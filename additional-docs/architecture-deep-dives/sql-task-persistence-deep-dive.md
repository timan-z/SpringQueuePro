# Task Domain & Persistence (Deep Dive)

## Introduction

This document provides a focused deep dive into how **Task Domain Modeling & Persistence** is implemented in the SpringQueuePro system. It focuses on the **authoritative data model around which the entire system is built**.

In SpringQueuePro, **PostgreSQL is the system of record**. Every guarantee around correctness, concurrency safety, retries, ownership, and observability ultimately traces back to how tasks are represented, persisted, and transitioned in the database.

This document explains and highlights:
- How tasks are modeled as **durable domain entities**
- How **state transitions are enforced atomically**
- How **ownership is enforced at the persistence layer**
- Why repository-level design choices are critical for correctness under concurrency
- How these decisions mirror real-world production systems

**NOTE**: This document intentionally does **not** re-explain Redis distributed locking mechanics, worker execution details, or retry orchestration logic. Those topics are covered in their own deep dives. The focus here is strictly on **truth, durability, and correctness**.

---

## Table of Contents

- [Why Task Persistence Is Critical in SpringQueuePro](#why-task-persistence-is-critical-in-springqueuepro)
- [Task Domain Model Overview](#task-domain-model-overview)
- [Task Lifecycle & State Machine](#task-lifecycle--state-machine)
- [Atomic State Transitions & Concurrency Safety](#atomic-state-transitions--concurrency-safety)
- [Ownership Enforcement at the Persistence Layer](#ownership-enforcement-at-the-persistence-layer)
- [Repository Design & Query Strategy](#repository-design--query-strategy)
- [Steps Taken to Mimic Production Quality](#steps-taken-to-mimic-production-quality)

---

## Why Task Persistence Is Critical in SpringQueuePro

SpringQueuePro is not a simple in-memory job runner. It is a **durable, multi-tenant task queue** where correctness must hold under:

- Concurrent worker execution
- Retries and failures
- Restarts and crashes
- Multiple authenticated users
- Distributed deployment

If task persistence were weak or loosely defined:

- Two workers could execute the same task
- Tasks could disappear or be double-completed
- Ownership boundaries could be violated
- Retries could corrupt task state
- Observability would become unreliable

For these reasons, **every meaningful system invariant is anchored to the database**, not Redis, not workers, and not in-memory state.

---

## Task Domain Model Overview

The **TaskEntity** (`domain/entity/TaskEntity`) represents the canonical, authoritative representation of work in the system.

At a high level, each task captures:

- **Identity** — a globally unique task ID
- **Payload** — the data required to perform the task
- **Type** — the execution category (`EMAIL`, `REPORT`, `DATACLEANUP`, etc.)
- **Status** — the current lifecycle state
- **Retry metadata** — attempt count and maximum retries
- **Ownership** — which authenticated user created the task
- **Temporal data** — creation timestamp
- **Concurrency metadata** — a version field for optimistic locking

This model is intentionally **explicit and rigid**. Flexibility is handled at higher layers (handlers and services), while the persistence layer enforces correctness and invariants.

### Why This Matters

By treating the task entity as a **state machine rather than a passive record**, SpringQueuePro ensures that:
- Invalid states cannot be represented
- Transitions are auditable and predictable
- Failures do not silently corrupt system truth

---

## Task Lifecycle & State Machine

SpringQueuePro enforces a strict task lifecycle:
```
QUEUED → INPROGRESS → COMPLETED
→ FAILED
```

This lifecycle is modeled explicitly using the `TaskStatus` enum.

The enum is stored as a **string**, not an ordinal, which is a deliberate choice that prioritizes:
- Readability at the database level
- Safer schema evolution
- Easier debugging during production incidents

There are **no implicit or hidden states**. If a task is not in one of these states, it is invalid by definition.

### Why This Matters

Production systems benefit from **simple, explicit state machines**:
- Fewer edge cases
- Clear retry and failure semantics
- Easier reasoning during incident response

SpringQueuePro deliberately avoids transitional or ambiguous states that often lead to race conditions.

---

## Atomic State Transitions & Concurrency Safety

One of the most important design decisions in SpringQueuePro is that **task state transitions are enforced atomically at the database level**.

When a worker attempts to claim a task, it does **not**:
- Read the task
- Check its status in memory
- Update it later optimistically

Instead, it performs a **single conditional update**:

- Transition from `QUEUED → INPROGRESS`
- Increment the attempt counter
- Succeed only if the task is still in the expected prior state

If the update affects **exactly one row**, the claim succeeded.
If it affects **zero rows**, the task was already claimed or its state changed.

This pattern:
- Eliminates race conditions
- Prevents double execution
- Avoids JVM-level locking
- Remains correct across multiple nodes

### Relationship to Redis Locks

Redis distributed locks act as a **secondary coordination layer**, but the database transition is the **final authority**.

Even if Redis were misconfigured or temporarily unavailable, the database would still prevent double execution.

This layered approach mirrors production systems where **durability and correctness are enforced at the persistence layer**, not in memory.

---

## Ownership Enforcement at the Persistence Layer

SpringQueuePro enforces **task ownership explicitly in the data model**.

Each task stores:
- The authenticated user who created it (`createdBy`)

Ownership is not enforced solely at the controller layer. Instead:
- Repository queries include ownership constraints
- Services retrieve and mutate tasks **only within the authenticated user’s scope**

This ensures that:
- Users cannot view tasks they do not own
- Users cannot retry or mutate other users’ tasks
- New APIs cannot accidentally bypass ownership rules

### Why This Matters

Controller-level checks alone are fragile. By enforcing ownership at the **query and persistence layer**, SpringQueuePro adds defense in depth against:
- Developer error
- API expansion
- Refactors that accidentally widen access

---

## Repository Design & Query Strategy

SpringQueuePro intentionally mixes:
- **Derived query methods**
- **Explicit JPQL queries**

### Derived Queries

Common access patterns (e.g., filtering by status, type, or ownership) use Spring Data’s naming conventions. This keeps the repository:
- Readable
- Declarative
- Free from unnecessary SQL

### Explicit JPQL Queries

Correctness-critical operations—especially state transitions—use explicit JPQL updates. These queries:
- Encode invariants directly into the update
- Return row counts to indicate success or failure
- Act as synchronization primitives, not just data access

This distinction reflects real production systems where **some queries enforce policy, not just retrieval**.

---

## Steps Taken to Mimic Production Quality

SpringQueuePro’s task persistence layer reflects production-grade systems in several important ways:

### 1. Database as the Single Source of Truth

All authoritative state lives in PostgreSQL:
- Redis coordinates
- Workers execute
- Services orchestrate
- **The database decides what is real**

---

### 2. Explicit State Machines

Task states are:
- Finite
- Enumerated
- Enforced by transitions

This prevents silent corruption and undefined behavior.

---

### 3. Atomic, Conditional Updates

State transitions are guarded by:
- Expected prior state
- Single-statement updates
- Row-count validation

This ensures correctness under extreme concurrency.

---

### 4. Ownership Embedded in the Data Model

Multi-tenancy is not bolted on later. Ownership is:
- Stored explicitly
- Queried consistently
- Enforced centrally

This mirrors SaaS-grade data isolation patterns.

---

### 5. Pragmatic Use of Optimistic Locking

An optimistic locking field exists, but the system primarily relies on **explicit state transitions** rather than version-based conflict detection.

This reflects a pragmatic philosophy:
- Use the simplest mechanism that guarantees correctness
- Avoid overengineering while preserving extensibility

---

## Closing Summary

The Task Domain & Persistence layer is the **bedrock of SpringQueuePro**.

Everything else—workers, retries, Redis coordination, dashboards—exists to safely manipulate and observe **durable, authoritative task state**. By grounding correctness in the database and modeling tasks as explicit state machines, SpringQueuePro achieves predictability, safety, and scalability that mirrors real production queue systems.

In short:

> **If Redis coordinates and workers execute, PostgreSQL decides.**
