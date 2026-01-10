# Processing, Retry & Failure Orchestration (Deep Dive)

## Introduction

This document provides a focused deep dive into how **Processing, Retry & Failure Orchestration** is implemented in my SpringQueuePro system.

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

**NOTE**: This document intentionally does **not** re-explain database claim semantics (`QUEUED → IN_PROGRESS`) or Redis lock mechanics. Those topics are covered in the *Task Domain & Persistence* and *Distributed Coordination & Redis Integration* deep dives. The focus here is strictly on **execution flow after claim, failure classification, and retry orchestration**.

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

## Why Retry Orchestration Is Critical in SpringQueuePro

SpringQueuePro is designed as a concurrent, multi-worker task system where **tasks may legitimately fail** due to:

- transient errors (simulated via probabilistic failure)
- deterministic failures (simulated via “fail absolute” handlers)
- runtime exceptions during execution
- timeouts or resource contention
- infrastructure instability (Redis/network/db issues)

In a system like this, retry behavior cannot be an afterthought. If retries are poorly designed:

- tasks can retry indefinitely
- failures can generate retry storms
- multiple workers can re-execute tasks concurrently
- state transitions can become inconsistent
- failure behavior becomes impossible to debug

For these reasons, SpringQueuePro treats retries as a **first-class orchestration concern** and places retry logic in a single authoritative location: `ProcessingService`.

## Handlers vs Orchestration: Clear Responsibility Boundaries

SpringQueuePro intentionally distinguishes between:

### Handlers (Units of Work)

Handlers implement the `TaskHandler` interface:

```java
public interface TaskHandler {
    void handle(Task task) throws InterruptedException;
}
```

Handlers are intentionally “dumb”:
- they perform task-specific work (*or at least simulate supposed task-distinct work*)
- they may sleep to simulate real processing latency
- they throw exceptions to signal failure

Handlers do **not**:
- mutate database state
- schedule retries
- re-enqueue tasks
- acquire locks
- decide eligibility for retries

This separation mirrors real production systems where business logic is isolated from runtime policy.

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

## ProcessingService as the Policy Engine

`ProcessingService` is the runtime component that converts **a claimed task** into a **deterministic lifecycle outcome**.

Even though `claimAndProcess` contains claim and locking logic, its most important role is what happens *after execution begins*:
- wrap handler execution in measurement
- treat exceptions as control flow signals
- persist failure explicitly
- enforce retry policy centrally

A key design decision is that task handlers are executed inside a controlled boundary:
- execution happens inside a try/catch
- the orchestration layer is the only place where failure transitions and retries are triggered
- all state mutations occur via persistence-driven updates and repository methods

This resembles the execution loop found in:
- job queues
- message consumer frameworks
- worker schedulers
- cloud-native retry systems (SQS/Kafka + backoff policies)

## Failure Handling & Deterministic Persistence

When handler execution throws an exception (including `TaskProcessingException`), `ProcessingService` handles it deterministically:

1. The task is marked `FAILED` in the domain model.
2. The entity is updated from the domain model (`taskMapper.updateEntity(...)`).
3. The failed task is persisted immediately (`taskRepository.save(...)`).
4. The cache is synchronized (`cache.put(...)`).
5. Failure counters and events are recorded.

This means failures are:
- explicit
- durable
- observable
- never “silent”

Importantly, failure persistence happens **before retry scheduling**. This ensures that:
- retries are never scheduled without first recording the failure state
- dashboards and inspection APIs always reflect real system behavior
- the system remains debuggable during heavy retry activity

---

## Automatic Retries with Exponential Backoff

After persisting a failure, SpringQueuePro evaluates retry eligibility:

```java
if (claimed.getAttempts() < claimed.getMaxRetries()) { ... }
```

If eligible:

1. The retry delay is computed via `computeBackoffMs(attempts)
2. The task is transitioned from `FAILED → QUEUED`
3. Retry is scheduled using the internal scheduler
4. Retry metrics and events are recorded

### Why FAILED → QUEUED Is Explicit

This is not cosmetic. It is a **correctness requirement**.

The claim step only succeeds for `QUEUED` tasks. If a failed task remained `FAILED`, it would never be eligible for re-claim.

SpringQueuePro therefore treats “re-queued retry” as a **real state transition**, not as an invisible runtime trick.

### Exponential Backoff Policy

The backoff function:

```java
return (long) (1000 * Math.pow(2, Math.max(0, attempts - 1)));
```

This mirrors real-world retry semantics because it:

- reduces pressure on downstream systems
- prevents lockstep retry waves
- avoids hammering infrastructure during failure spikes
- stabilizes recovery under load

The goal is not just to “retry”—it is to retry **safely and predictably**.

### Why Scheduling Lives in ProcessingService

Retry scheduling is performed via:

```java
scheduler.schedule(() -> queueService.enqueueById(taskId), delayMs, TimeUnit.MILLISECONDS);
```

This is intentionally routed through:

- the scheduler for delay control
- the queue service for controlled submission
- the same orchestration entry point used for normal execution

This avoids creating hidden “alternate execution paths” for retries.

---

## Manual Requeueing (Operator-Controlled Retry)

SpringQueuePro supports explicit operator-style retry through:

```java
public boolean manuallyRequeue(String taskId)
```

Manual requeueing is intentionally strict:

- Only `FAILED` tasks may be manually requeued
- The state transition is enforced in the database (`FAILED → QUEUED`)
- Attempts are reset to 0 (to re-enable retry semantics cleanly)
- Cache is synchronized
- A `TaskCreatedEvent` is published

Publishing the event (rather than directly calling `enqueueById`) is deliberate: it ensures runtime submission happens only after the transaction commits, preventing “phantom execution” of tasks whose state hasn’t actually been persisted yet.

This mirrors real production systems where operational commands (manual retry) are handled through the same lifecycle mechanisms as normal execution.

---

## Observability of Processing & Retry Behavior

SpringQueuePro makes processing and retry behavior explicitly visible through:

### 1. Metrics

- submitted, claimed, completed, failed, retried counters
- execution timing via `processingTimer.recordCallable(...)`

This enables:

- throughput analysis
- failure rate analysis
- latency distribution inspection
- retry volume monitoring

### 2. Runtime Event Log

A bounded event log captures key execution transitions:

- CLAIM_START / CLAIM_SUCCESS
- PROCESSING
- FAILED / RETRY_SCHEDULED
- COMPLETED
- LOCK_RELEASE

This log exists to support:

- dashboard visibility
- debugging without logs
- replaying recent runtime history during demos or incidents

---

## Steps Taken to Mimic Production Quality

### 1. Centralized Retry Policy

Retries are enforced in one place, not scattered across handlers. This prevents:

- inconsistent retry semantics
- handler complexity
- hidden execution paths

---

### 2. Failure Is Always Persisted First

Failures are recorded durably before retries are scheduled. This ensures:

- correctness under crash scenarios
- consistent observability
- debuggable behavior under load

---

### 3. Backoff Is Applied Deliberately

Exponential backoff prevents retry storms and stabilizes the system under repeated failure. This mirrors real cloud-native patterns.

---

### 4. Handlers Are Pure Units of Work

Handlers do not coordinate retries or persistence. They either:

- complete successfully
- throw exceptions

This is a clean and scalable separation of concerns.

---

### 5. Retry Scheduling Uses Controlled Runtime Paths

Retries re-enter the system through the same submission pipeline (`enqueueById`), ensuring:

- consistent execution boundaries
- uniform observability
- no hidden bypasses

---

## Closing Summary

SpringQueuePro treats task processing as an orchestration problem, not a handler problem.

Handlers do work. Failures are persisted. Retries are policy-driven. Backoff is deliberate. Execution re-enters the pipeline through controlled scheduling.

This design yields task execution behavior that is:

- deterministic
- scalable
- observable
- production-adjacent in its operational semantics

In short:

> **Handlers execute logic. ProcessingService enforces policy. Retries are scheduled, not improvised.**

---
