# SpringQueuePro Evolution of Architecture

**SpringQueue → SpringQueuePro Architecture Evolution**

This document chronicles the architectural evolution of **SpringQueuePro**, tracing its growth from a minimal in-memory concurrency prototype (that being SpringQueue — the base edition) into a **production-grade, distributed task processing system**. Each phase reflects deliberate design tradeoffs, real-world system constraints, and progressively more robust engineering patterns.
- For each architectural stage, I will provide a link to a past commit that displays a snapshot of what this project looked like at that point in time.

---

## Phase 0 — Prototype & Code Audit (SpringQueue Base)

The initial phase of my project was basically a code audit of my base SpringQueue project. That is, recognizing practices and flaws in the codebase that — while acceptable in a minimalist producer-consumer prototype — were undesirable in a production-grade job queue system (*which is what was ultimately the goal with SpringQueuePro*).

### Goal

The first *true* step in moving SpringQueue towards a production-like system was to integrate persistence, new APIs (e.g., for JWT), and other distributed concerns. However, before any of that could be done, I had to establish a proper and idiomatically Java concurrency foundation before layering in any of the aforementioned.
- Yes, SpringQueue was a one-to-one translation of GoQueue with adjustments to make it more idiomatically Java. Granted, it was still evidently a project designed with another language's design principles in mind.

- This is **Phase 0** primarily because it involved laying groundwork for the "actually tangible" additions of subsequent phases.

### SpringQueue Initial State

The original **SpringQueue** was intentionally minimal:
- In-memory task storage using `ConcurrentHashMap`
- Worker execution via `ExecutorService`
- Task lifecycle managed directly inside worker threads
- Retry logic embedded inside task handlers
- Task routing implemented via a `switch` on task type
- No persistence, no external coordination

This version validated the **Producer–Consumer model** in Java and mirrored my earlier Go-based project **GoQueue** which used goroutines and channels.

### Audit Findings

While functional, the design surfaced several limitations:
- **Tight coupling** between workers, task logic, and retry policy
- No durability (all state lost on restart)
- Hard-coded concurrency configuration
- Task type routing brittle and non-extensible
- Retry logic invisible to any future persistence layer

### Key Refactors Introduced

- **TaskType enum** replacing raw strings
- **TaskHandler strategy pattern** (`TaskHandler` + per-type handlers)
- **TaskHandlerRegistry** for centralized dispatch
- Removal of large `switch` statements
- Externalized configuration (`QueueProperties`)
- Improved locking strategy (read/write locks)
- Structured logging (SLF4J)
- Early unit testing of handlers and queue semantics

> **Outcome:** A clean, idiomatic Java concurrency core ready to be lifted into a real backend service.

---

## Phase 1 — Spring Boot Service Layer & Persistence Foundation

With the code audit of Phase 0 completed, the SpringQueue prototype — now augmented and modularized for future extensibility — was ready to move towards production-like quality.

### Goal

Transform the post-audit prototype into a **real Spring Boot service** with persistence, clear layering, and API boundaries.

### Major Additions

#### Persistence (PostgreSQL + JPA)

- Introduced `TaskEntity` as the **system of record**
- Added `TaskRepository` (Spring Data JPA)
- Defined explicit task lifecycle states:

  ```
  QUEUED → IN_PROGRESS → COMPLETED / FAILED
  ```

#### Domain Separation (DDD-style)

- `TaskEntity` (persistence model)
- `Task` (domain model)
- `TaskMapper` to convert between them
- Handlers operate **only on domain objects**
- Persistence updates centralized in services

#### Service Layer

- `TaskService` for task creation & queries
- `ProcessingService` as the **orchestrator**

  - Atomic task claiming
  - Handler execution
  - Retry scheduling
  - State persistence
- `QueueService` demoted to a **dispatch bridge**
  - Submits work to executors
  - No longer owns business logic

#### Key Architectural Shift(s)

Retry logic was removed from handlers and centralized:
- Handlers now **throw exceptions** on failure
- `ProcessingService` catches failures
- Retry/backoff handled consistently and durably

> **Outcome:** Persistence-aware, restart-safe task execution with clean separation of concerns.

**Moreover**, by the end of this phase, my project had now visibly transitioned away from a **prototype queue** into an **event-driven, persistence-aware processing pipeline**.
- In the SpringQueue prototype, `QueueService` was:

    - [1] — A **Task Queue** (responsible for holding tasks in-memory).

    - [2] — A **Worker Pool Manager** (spinning up worker threads `ExecutorService` to process them concurrently).

    - [3] — A **Retry Scheduler** (re-enqueueing failed tasks after a delay, e.g., )

- In the prototype, `QueueService` effectively handled everything (persistence simulation, concurrency, retry logic, and orchestration). `QueueService` was the system's state.

- With the introduction of `TaskEntity`, `TaskRepository`, `ProcessingService`, and even just the event flow (`TaskCreatedEvent` -> listener -> `ProcessingService` claim and process), the responsibility boundaries have changed completely.
    - `TaskEntity` was now the persisted record
    - `TaskRepository` was the repository for durability
    - `ProcessingService` ensured transactional, version-safe updates

- `QueueService` now, instead of managing everything, was simply a **bridging and coordination layer** between the durable world (PostgreSQL) and the transient processing world (executor and worker threads). 
    - It no longer decided business outcomes (retry, fail, etc.) — now it just *dispatches* and *coordinates*.

Even the purpose of the `Worker` threads were altered. No longer were they "owners of business logic" (themselves executing custom code) but now runners of units of work (almost like carriage vessels).

---

## Phase 1.5 — GraphQL Integration & API Design

Now, this phase here was somewhat "personal" in that I really wanted to work with GraphQL in one of my projects and decided that my API for interacting with, querying and mutating Tasks would be defined through GraphQL. But it does have its perks:
- Clean mapping to domain queries
- Strong typing via schema `schema.graphqls`
- Fine-grained querying of task state
- Nice for observability UIs (like my React frontend)

### Goal

Expose a modern, flexible GraphQL API for interacting with persisted tasks.

### Implementation
- `schema.graphqls` defining `Task`, enums, and mutations

- `TaskGraphQLController` with `@QueryMapping` and `@MutationMapping`

- GraphQL secured alongside REST (later phases)

- GraphiQL used for manual verification

> **Outcome:** A modern API surface aligned with real production backends.

---

## Phase 2 — Distributed Coordination, Redis, and Security

I was fairly eager to work with Redis in the phase beyond simply using it for caching and fast data-access, instead focusing on it as a coordination tool and leveraging its distributed lock capabilities.
- In implementing `ProcessingService`, I leveraged Spring Data JPA's optimistic locking to prevent race conditions but as an additional layer of security, Redis-distributed locks that operated with embedded Lua scripts sounded like a lot of fun to work with.

(JWT, Spring Security, and so on were a given with this project).

### Goal

Make the system safe under concurrency, multi-instance deployment, and authenticated access.

---

### Redis Integration

Redis was introduced as **coordination infrastructure**, not just a cache:

- **Distributed locking** (`RedisDistributedLock`)

  - Prevents multiple workers claiming the same task
  - TTL-based safety against dead workers
- Redis-backed refresh token store (JWT)
- Task caching experiments (optional, non-authoritative)

Redis was explicitly *not* used as the system of record — PostgreSQL remained authoritative.

---

### Security & Authentication (JWT + RBAC)

A full **stateless security pipeline** was implemented:

- Spring Security filter chain
- Custom `JwtAuthenticationFilter`
- Access + refresh tokens
- Refresh token rotation
- Token revocation via Redis
- `/login`, `/register`, `/refresh`, `/logout`

#### Role-Based Access Control (RBAC)

- Users loaded via `CustomUserDetailsService`
- Roles attached to JWT claims
- Security enforced at:

  - REST endpoints
  - GraphQL resolvers
- Public endpoints limited to auth flows
- Internal queue operations strictly protected

> **Outcome:** Production-grade security comparable to real SaaS APIs.

---

## Phase 3 — Observability, Metrics, and Deployment

### Goal

Make the system **observable, debuggable, and deployable**.

---

### Metrics & Actuator

Using **Micrometer + Spring Actuator**, the system exposes:

- Task throughput counters
- Processing latency histograms
- Retry & failure metrics
- Queue depth
- Worker activity
- JVM, DB, Redis health

Metrics are Prometheus-scrapable via:

```
/actuator/prometheus
```

Custom metrics include:

- `springqpro_tasks_submitted_total`
- `springqpro_tasks_completed_total`
- `springqpro_task_processing_duration`
- `springqpro_tasks_failed_total`

---

### Testing Strategy

- Unit tests for:

  - Handlers
  - Mappers
  - Services
- Integration tests using **Testcontainers**

  - Real Postgres + Redis
  - JWT auth flows
  - Redis lock correctness
- CI via GitHub Actions

---

### Deployment

- Backend deployed to **Railway**
- Managed Postgres + Redis instances
- Frontend deployed to **Netlify**
- Docker used for:

  - Local development (`docker-compose`)
  - CI validation
  - Reproducible environments

> **Outcome:** A fully deployed, production-like distributed backend with real observability.

---

## Current State (SpringQueuePro v1.0)

SpringQueuePro is now:

- A **distributed, fault-tolerant task queue**
- Persistence-first and restart-safe
- Redis-coordinated for concurrency
- Secure via JWT + RBAC
- Observable via Prometheus metrics
- API-driven (GraphQL + REST)
- Deployed and demo-ready

---

## Looking Ahead — CloudQueue

The next evolution (**CloudQueue**) will:

- Replace internal queueing with **AWS SQS**
- Offload execution to **Lambda**
- Preserve existing API, auth, and observability layers
- Demonstrate cloud-native system design

---

## Why This History Matters

This project was not built by copy-pasting tutorials.
It evolved through **intentional architectural pressure**, mirroring how real systems grow:

> Prototype → Audit → Persistence → Distribution → Security → Observability → Cloud

This history exists to show *how* and *why* those decisions were made.

---