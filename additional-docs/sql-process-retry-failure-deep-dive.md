# Processing, Retry & Failure Orchestration (Deep Dive)

## Introduction

This document provides a focused deep dive into how **Processing, Retry & Failure Orchestration** is implemented in the SpringQueuePro system.

While the **Task Domain & Persistence** deep dive explains how PostgreSQL enforces correctness and atomic state transitions, and the **Distributed Coordination & Redis Integration** deep dive explains how Redis provides safe mutual exclusion under concurrency, this document focuses on **what happens after a task has been claimed**:

- How task execution is orchestrated end-to-end
- How failures are handled deterministically
- How retries are scheduled safely (without handler-level side effects)
- Why retry policy lives in one place rather than being scattered across handlers

In SpringQueuePro, retries are treated as a **runtime policy**, not a handler responsibility. Handlers simply perform work and throw exceptions when they fail. The orchestration layer is responsible for:

- Persisting failure
- Deciding if the task is eligible for retry
- Applying backoff
- Scheduling re-execution safely
- Maintaining observability throughout the process

This document explains and highlights:

- Why retry orchestration must be centralized in real systems
- How `ProcessingService` acts as the execution policy engine
- How failure states are persisted deterministically
- How exponential backoff is computed and applied
- How manual requeueing is supported safely
- Steps taken to mirror production-grade retry semantics

**NOTE**: This document intentionally does **not** re-explain database claim semantics (`QUEUED → INPROGRESS`) or Redis lock mechanics. Those topics are covered in the *Task Domain & Persistence* and *Distributed Coordination & Redis Integration* deep dives. The focus here is strictly on **execution flow after claim, failure classification, and retry orchestration**.

---

## Table of Contents

- [Why Retry Orchestration Is Critical in SpringQueuePro](#why-retry-orchestration-is-critical-in-springqueuepro)
- [Handlers vs Orchestration: Clear Responsibility Boundaries](#handlers-vs-orchestration-clear-responsibility-boundaries)
- [ProcessingService as the Policy Engine](#processingservice-as-the-policy-engine)
- [Failure Handling & Deterministic Persistence](#failure-handling--deterministic-persistence)
- [Automatic Retries with Exponential Backoff](#automatic-retries-with-exponential-backoff)
- [Manual Requeueing (Operator-Controlled Retry)](#manual-requeueing-operator-controlled-retry)
- [Observability of Processing & Retry Behavior](#observability-of-processing--retry-behavior)
- [Steps Taken to Mimic Production Quality](#steps-taken-to-mimic-production-quality)

---

## Why Retry Orchestration Is Critical in SpringQueuePro

SpringQueuePro is designed as a concurrent, multi-worker task system where **tasks may legitimately fail** due to:

- transient errors (simulated via probabilistic failure)
- deterministic failures (simulated via “fail absolute” handlers)
- runtime exceptions during execution
- timeouts or resource contention
- infrastructure instability (Redis/network/database issues)

In a system like this, retry behavior cannot be an afterthought. If retries are poorly designed:

- tasks can retry indefinitely
- failures can generate retry storms
- multiple workers can re-execute tasks concurrently
- state transitions can become inconsistent
- failure behavior becomes impossible to debug

For these reasons, SpringQueuePro treats retries as a **first-class orchestration concern** and places retry logic in a single authoritative location: `ProcessingService`.

---

## Handlers vs Orchestration: Clear Responsibility Boundaries

SpringQueuePro intentionally distinguishes between:

### Handlers (Units of Work)

Handlers implement the `TaskHandler` interface:

```java
public interface TaskHandler {
    void handle(Task task) throws InterruptedException;
}
```

Handlers are intentionally simple:

- they perform task-specific work (or simulate task-distinct work)
- they may sleep to simulate real processing latency
- they throw exceptions to signal failure

Handlers do **not**:

- mutate database state
- schedule retries
- re-enqueue tasks
- acquire locks
- decide eligibility for retries

This separation mirrors real production systems where business logic is isolated from execution policy.

---

### Orchestration Layer (Execution Policy)

The orchestration layer (`ProcessingService`) is responsible for:

- persisting outcomes (`COMPLETED` / `FAILED`)
- determining retry eligibility
- computing backoff
- scheduling retries safely
- maintaining observability and metrics

This keeps all retry semantics:

- centralized
- testable
- predictable
- auditable

---

## ProcessingService as the Policy Engine

`ProcessingService` is the runtime component that converts **a claimed task** into a **deterministic lifecycle outcome**.

Although `claimAndProcess` contains validation, claim, and locking logic, its most important responsibility begins **after execution starts**:

- wrapping handler execution in measurement
- treating exceptions as control-flow signals
- persisting failure deterministically
- enforcing retry policy centrally

A key design decision is that handler execution occurs inside a tightly controlled boundary:

- execution is wrapped in a try/catch
- orchestration logic exclusively owns failure handling and retries
- all lifecycle mutations occur via repository-backed persistence

This structure closely resembles execution loops found in:

- job queues
- message consumers
- worker schedulers
- cloud-native retry systems (e.g., SQS/Kafka-style consumers)

---

## Failure Handling & Deterministic Persistence

When handler execution throws an exception (including `TaskProcessingException`), `ProcessingService` handles it deterministically:

1. The task is marked `FAILED` in the domain model.
2. The persistence entity is updated via `TaskMapper`.
3. The failed task is persisted immediately (`taskRepository.save(...)`).
4. The Redis cache is synchronized (`cache.put(...)`).
5. Failure counters and runtime events are recorded.

This ensures that failures are:

- explicit
- durable
- observable
- never silent

Crucially, failure persistence occurs **before any retry scheduling**. This guarantees that:

- retries are never scheduled without recorded failure
- dashboards and APIs reflect real system state
- the system remains debuggable during heavy retry activity

---

## Automatic Retries with Exponential Backoff

After persisting a failure, `ProcessingService` evaluates retry eligibility:

```java
if (claimed.getAttempts() < claimed.getMaxRetries()) { ... }
```

If the task is eligible for retry:

1. A delay is computed using `computeBackoffMs(attempts)`
2. The task is transitioned from `FAILED → QUEUED`
3. Retry execution is scheduled via the scheduler
4. Retry metrics and events are recorded

---

### Why FAILED → QUEUED Is Explicit

This transition is not cosmetic—it is required for correctness.

The claim mechanism only operates on `QUEUED` tasks. A task that remains `FAILED` cannot be reclaimed or retried.

SpringQueuePro therefore treats retries as **real lifecycle transitions**, not invisible runtime shortcuts.

---

### Exponential Backoff Policy

The backoff computation:

```java
return (long) (1000 * Math.pow(2, Math.max(0, attempts - 1)));
```

This mirrors production retry semantics because it:

- reduces pressure on downstream systems
- avoids synchronized retry waves
- prevents infrastructure hammering
- stabilizes recovery under failure spikes

The objective is not merely to retry, but to retry **safely and predictably**.

---

### Why Scheduling Lives in ProcessingService

Retry scheduling is performed via:

```java
scheduler.schedule(() -> queueService.enqueueById(taskId), delayMs, TimeUnit.MILLISECONDS);
```

This design is intentional:

- delays are controlled by the scheduler
- execution re-enters via `QueueService`
- retries follow the exact same execution path as initial runs

This avoids hidden or bypassed execution paths.

---

## Manual Requeueing (Operator-Controlled Retry)

SpringQueuePro supports explicit, operator-style retry through:

```java
public boolean manuallyRequeue(String taskId)
```

Manual requeueing is deliberately strict:

- only `FAILED` tasks are eligible
- state transition (`FAILED → QUEUED`) is enforced in the database
- attempts are reset to zero
- Redis cache is synchronized
- a `TaskCreatedEvent` is published

Publishing the event—rather than calling `enqueueById` directly—is deliberate. It ensures execution occurs only **after the transaction commits**, preventing execution of tasks whose state is not yet durable.

This mirrors production systems where operational actions reuse the same lifecycle pathways as normal execution.

---

## Observability of Processing & Retry Behavior

SpringQueuePro exposes processing and retry behavior explicitly via:

### 1. Metrics

- submitted, claimed, completed, failed, retried counters
- execution timing via `processingTimer.recordCallable(...)`

These enable:

- throughput analysis
- failure rate inspection
- latency distribution tracking
- retry volume monitoring

---

### 2. Runtime Event Log

A bounded in-memory event log captures execution transitions:

- CLAIM_START / CLAIM_SUCCESS
- PROCESSING
- FAILED / RETRY_SCHEDULED
- COMPLETED
- LOCK_RELEASE

This supports:

- dashboard visibility
- debugging without backend logs
- replaying recent runtime behavior during demos or incidents

---

## Steps Taken to Mimic Production Quality

### 1. Centralized Retry Policy

Retries are enforced in one location, not scattered across handlers. This prevents:

- inconsistent semantics
- handler complexity
- hidden execution behavior

---

### 2. Failure Is Always Persisted First

Failures are durably recorded before retries are scheduled, ensuring:

- correctness under crash scenarios
- consistent observability
- debuggable behavior under load

---

### 3. Backoff Is Applied Deliberately

Exponential backoff stabilizes the system and prevents retry storms, mirroring cloud-native patterns.

---

### 4. Handlers Are Pure Units of Work

Handlers:

- execute work
- throw exceptions

They do not coordinate retries or persistence.

---

### 5. Retry Scheduling Uses Controlled Runtime Paths

Retries re-enter through the same submission pipeline (`enqueueById`), ensuring:

- consistent execution boundaries
- uniform observability
- no hidden bypasses

---

## Closing Summary

SpringQueuePro treats task processing as an orchestration problem, not a handler problem.

Handlers execute work. Failures are persisted. Retries are policy-driven. Backoff is deliberate. Execution re-enters through controlled scheduling.

This yields processing behavior that is:

- deterministic
- scalable
- observable
- production-adjacent in operational semantics

In short:

> **Handlers execute logic. ProcessingService enforces policy. Retries are scheduled, not improvised.**

---
