# Distributed Coordination & Redis Integration (Deep Dive)

## Introduction

This document provides a focused deep dive into how **Redis is used as a distributed coordination layer** in my SpringQueuePro system.

While the **Task Domain & Persistence** documentation explains how PostgreSQL acts as the *system of record* for task state, this document focuses on **how Redis is used to safely coordinate concurrent access to that state in a distributed environment**.

Redis plays a **deliberately constrained but critical role** in SpringQueuePro. It is **not** used as a primary datastore, and it does **not** own authoritative task state. Instead, it provides:
- Distributed mutual exclusion (locking)
- Ephemeral coordination primitives
- Short-lived caching for performance and observability
- Safety guarantees under concurrency and failure

This document explains:
- Why Redis is necessary in a multi-worker task system
- How distributed locks prevent double execution
- How Redis caching is intentionally scoped and bounded
- How Redis is configured to behave predictably in production environments
- What steps were taken to mirror real-world production designs

**NOTE**: This document intentionally does **not** re-explain PostgreSQL persistence, JPA transactions, or retry semantics. Those topics are covered in the *Task Domain & Persistence* deep dive. (There will be independent, isolated "deep dive" documents on each of these topics).

---

## Table of Contents

- [Why Redis Is Required in SpringQueuePro](#why-redis-is-required-in-springqueuepro)
- [Redis vs PostgreSQL: Clear Responsibility Boundaries](#redis-vs-postgresql-clear-responsibility-boundaries)
- [Distributed Locking Model](#distributed-locking-model)
- [Ephemeral Task Caching Strategy](#ephemeral-task-caching-strategy)
- [Redis and Refresh Token Coordination (Brief)](#redis-and-refresh-token-coordination-brief)
- [Relevant Project Files](#relevant-project-files)
- [Failure Scenarios & Safety Guarantees](#failure-scenarios--safety-guarantees)
- [Steps Taken to Mimic Production Quality](#steps-taken-to-mimic-production-quality)

## Why Redis Is Required in SpringQueuePro

SpringQueuePro is designed as a **concurrent, multi-worker task execution system**. Even in a single-node deployment, multiple worker threads may attempt to process tasks simultaneously. In a distributed or horizontally scaled deployment, this risk increases significantly.

Without a coordination layer:

- Two workers could claim and execute the same task (corrupting data).
- Race conditions could lead to inconsistent state transitions
- Failures during execution could leave tasks in ambiguous states

Traditional database locking alone is insufficient for this use case because:

- Long-lived locks reduce throughput
- Workers may crash while holding locks
- Database locks do not naturally express *execution ownership*

Redis fills this gap by providing **fast, ephemeral coordination primitives** that:
- Exist independently of worker lifetimes
- Automatically expire
- Are cheap to acquire and release
- Work consistently across nodes

## Redis vs PostgreSQL: Clear Responsibility Boundaries

SpringQueuePro enforces a **strict separation of concerns** between Redis and PostgreSQL.

### PostgreSQL (System of Record)

- Owns task lifecycle state
- Enforces atomic transitions:

  - `QUEUED → IN_PROGRESS → COMPLETED / FAILED`
- Provides durability and transactional guarantees
- Remains authoritative under all circumstances

### Redis (Coordination Layer)

- Owns *nothing permanently*
- Provides:

  - Distributed locks
  - Temporary task metadata
  - Fast lookup paths
- All Redis state is:

  - Ephemeral
  - TTL-bound
  - Reconstructable from PostgreSQL

This separation ensures that:
- Redis failures never corrupt system state
- Redis restarts do not require data recovery
- PostgreSQL remains the single source of truth

This mirrors real production systems where Redis is treated as an **optimization and coordination tool**, not a datastore.

## Distributed Locking Model

SpringQueuePro uses Redis to implement **token-based distributed locks** to ensure **effectively-once task execution under cooperative correctness semantics**.
- DB conditional updates (see `TaskRepository`) are the true guard.
- Redis locks are secondary but play an important role in ensuring that no two workers execute the same task simultaneously.

Important point of emphasis is that Redis locks do not replace DataBase state transitions.
- Redis locks exist *after* DataBase ownership is established.
- Redis is about **execution coordination**, not claim correctness.

### Lock Acquisition

Locks are acquired using Redis’ atomic `SET NX PX` semantics:

- `NX` ensures the lock is only set if absent
- `PX` applies a time-to-live (TTL)
- Each lock stores a **unique token** identifying the owner

This guarantees:

- Mutual exclusion
- Automatic recovery if a worker crashes
- No reliance on manual cleanup

If a lock cannot be acquired, the worker simply does not proceed.

---

### Lock Ownership & Safe Release

Lock release is guarded by a **Lua script** that ensures:

- Only the worker that acquired the lock may release it
- Accidental unlocks are impossible
- Race conditions during expiration are handled safely

The unlock logic performs:

```
if redis.get(key) == token then
    redis.del(key)
end
```

This prevents:
- One worker releasing another worker’s lock
- Stale tokens invalidating active executions

This pattern is widely used in production systems and is a **non-negotiable requirement** for safe distributed locking.

## Ephemeral Task Caching Strategy

In addition to locking, Redis is used as a **short-lived task metadata cache**.

### Purpose of Task Caching

The cache exists to:
- Reduce repetitive database lookups
- Improve dashboard responsiveness
- Support inspection and coordination flows

Importantly:
- Cached tasks are **never authoritative**
- Cached entries have strict TTLs
- Cache misses are always safe

If Redis is unavailable or an entry expires:
- The system simply falls back to PostgreSQL
- No correctness guarantees are violated

This ensures Redis improves performance **without becoming a dependency for correctness**.


## Redis and Refresh Token Coordination (Brief)

In addition to task coordination and distributed locking, Redis is also used in SpringQueuePro as a **lightweight session coordination layer** for authentication.

While **access tokens** are fully stateless JWTs and never stored server-side, **refresh tokens are intentionally persisted in Redis** with explicit TTLs. This enables capabilities that purely stateless JWT systems cannot provide, including:
- Refresh token rotation
- Explicit logout and token revocation
- Replay attack prevention
- Cluster-wide session consistency

Redis is well-suited for this role because refresh tokens are:
- Ephemeral by nature
- Short-lived relative to identity data
- Required to be fast-access and centrally visible
- Safe to expire or be lost without compromising system correctness

Importantly, Redis **does not own identity** in SpringQueuePro:
- PostgreSQL remains the system of record for users
- Redis only tracks active session artifacts
- Redis failure results in re-authentication, not data loss

This mirrors real-world production systems, where session state is treated as **recoverable coordination data**, not durable truth.

For a full discussion of authentication flows, refresh token rotation, and identity management, see **Authentication & Identity Management (Deep Dive)**.

## Relevant Project Files

- `redis/RedisDistributedLock`—the core distributed locking mechanism (embodies the system's concurrency safety guarantees).
<br>**Responsibilities include**:
    - Lock acquisition using Redis atomic primitives
    - Token-based ownership tracking
    - Safe unlock via Lua scripting
    - Automatic recovery via TTL expiration

- `redis/TaskRedisRepository`—a bounded Redis-backed cache for task metadata. (Simple and avoids Redis features for predictable behavior).
<br>**Responsibilities include**:
    - TTL-enforced task storage
    - Explicit cache invalidation
    - Defensive deserialization
    - Clear logging for visibility

- `config/RedisConfig`—centralized Redis wiring and configuration.
<br>**Responsibilities include**:
    - Environment-aware connection setup
    - SSL support for managed Redis providers
    - Explicit serializer configuration
    - Avoidance of Java native serialization pitfalls
    - Makes sure Redis behaves consistently across local development, CI environments, and cloud deployments.

## Failure Scenarios & Safety Guarantees

SpringQueuePro’s Redis design explicitly accounts for failure.

### Worker Crash During Execution
- Lock TTL expires automatically
- Another worker may safely retry
- PostgreSQL state remains consistent

### Redis Restart or Data Loss
- Locks are ephemeral by design
- Cached tasks are reconstructable
- System resumes without manual intervention

### Network Partitions
- Lock acquisition failures are treated as non-fatal
- Workers fail fast rather than risking double execution
- Correctness is preserved over availability

This bias toward correctness mirrors production task processing systems.

---

## Steps Taken to Mimic Production Quality

### 1. Redis Is Not Trusted with State

Redis is intentionally treated as:
- Volatile
- Replaceable
- Non-authoritative

This avoids a common anti-pattern where Redis becomes an accidental primary datastore.

---

### 2. Token-Based Distributed Locking

Locks are:
- Explicitly owned
- Time-bounded
- Safely released

This prevents subtle race conditions that only appear under load.

---

### 3. TTL-Driven Recovery

All Redis entries:
- Expire automatically
- Require no cleanup jobs
- Support crash recovery by default

---

### 4. Environment-Aware Configuration

Redis connectivity supports:
- SSL for managed providers
- Externalized credentials
- Consistent serialization across environments

This mirrors real operational constraints.

---

### 5. Defensive Design Philosophy

Redis failures:
- Degrade performance
- Do **not** break correctness

This is a key hallmark of production-grade system design.

---

## Closing Summary

Redis in SpringQueuePro exists to **coordinate, not persist**.

By:
- Enforcing strict responsibility boundaries
- Using token-based distributed locks
- Treating Redis as ephemeral infrastructure
- Biasing toward correctness under failure

SpringQueuePro achieves safe, deterministic task execution under concurrency — a requirement for any real-world distributed task processing system.

---
