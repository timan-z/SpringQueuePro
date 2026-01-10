# API Layer (REST + GraphQL) (Deep Dive)

## Introduction

This document provides a focused deep dive into how the **API Layer (REST + GraphQL)** is implemented in my SpringQueuePro system.

SpringQueuePro intentionally uses **both REST and GraphQL**, but they serve **different, well-defined roles**:

- **REST** is used for **authentication, identity, and session-oriented flows**, where request/response semantics and explicit endpoints are the natural fit.
- **GraphQL** is used for **task-domain interaction**, including task creation, inspection, filtering, retry orchestration, and runtime visibility.

This separation is deliberate and mirrors real-world systems, where authentication is often handled via REST endpoints, while domain interaction—especially read-heavy and exploratory workloads—is better served by GraphQL.

This document explains and highlights:
- Why SpringQueuePro uses both REST and GraphQL
- How responsibilities are cleanly divided between the two
- How security and ownership are enforced consistently across both surfaces
- How API design reflects system evolution and production constraints

**NOTE**: This document intentionally does **not** re-explain JWT structure, refresh token rotation mechanics, or RBAC rules in detail. Those topics are covered in the *Authentication & Identity Management* and *Security Configuration & RBAC* deep dives. The focus here is on **API shape, controller responsibilities, and client-facing interaction patterns**.

---

## Table of Contents

- [Why SpringQueuePro Uses Both REST and GraphQL](#why-springqueuepro-uses-both-rest-and-graphql)
- [REST for Authentication & Identity Management](#rest-for-authentication--identity-management)
- [GraphQL as the Primary Task-Domain API](#graphql-as-the-primary-task-domain-api)
- [Ownership & Multi-Tenant Scoping at the API Layer](#ownership--multi-tenant-scoping-at-the-api-layer)
- [Relevant Project Files](#relevant-project-files)
- [GraphQL Schema Design](#graphql-schema-design)
- [REST APIs Beyond Authentication](#rest-apis-beyond-authentication)
- [Steps Taken to Mimic Production Quality](#steps-taken-to-mimic-production-quality)

## Why SpringQueuePro Uses Both REST and GraphQL

SpringQueuePro is not a single-purpose API. It supports two fundamentally different interaction models:

1. **Authentication and session lifecycle management**
2. **Task-domain querying, inspection, and orchestration**

### Why REST for Authentication

Authentication flows:
- are command-oriented
- operate on identity and session artifacts
- require explicit request/response semantics
- often integrate with non-GraphQL clients

REST is the industry-standard fit for:
- login
- registration
- refresh token rotation
- logout
- session validation

### Why GraphQL for Task Interaction

Once authenticated, users primarily:
- explore task state
- filter and inspect data
- issue domain mutations (retry, update, delete)
- interact through a dashboard UI

GraphQL excels here because:
- it enables read-heavy exploratory queries
- it avoids endpoint explosion
- it allows clients to request exactly what they need
- it acts as a *query interface*, not just a CRUD API

---

## REST for Authentication & Identity Management

Authentication in SpringQueuePro is handled exclusively via **REST endpoints** exposed by `AuthenticationController`.

### Responsibilities of the Authentication REST API

The authentication API is responsible for:
- user registration
- credential verification
- issuing access and refresh tokens
- refresh token rotation
- logout and token invalidation
- refresh-token session introspection

These operations are intentionally **not part of GraphQL**, because:
- they are not part of the task domain
- they operate on identity, not business entities
- they benefit from clear, explicit endpoints
- they are easier to secure and reason about independently

### Key Authentication Endpoints

The `/auth` REST surface includes:
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /auth/refresh-status`

Each endpoint:
- performs a narrowly scoped operation
- relies on JWT for cryptographic validity
- relies on Redis for refresh token coordination
- returns simple DTOs rather than domain entities

This design mirrors real-world identity services, where authentication is treated as a **separate subsystem** from domain APIs.

---

## GraphQL as the Primary Task-Domain API

Once authentication is complete, all **task-related interaction** flows through GraphQL.

The GraphQL API is implemented via `TaskGraphQLController` using Spring for GraphQL. Unlike REST controllers, GraphQL controllers use `@Controller` because request handling is delegated to the GraphQL execution engine rather than Spring MVC routing.

### Responsibilities of the GraphQL API

The GraphQL layer is responsible for:
- querying persisted tasks
- filtering by status or type
- inspecting individual task state
- creating new tasks
- retrying failed tasks
- updating and deleting tasks
- exposing enums and schema-driven metadata

All task-domain mutations delegate to:
- `TaskService` for persistence operations
- `ProcessingService` for retry orchestration

This keeps the GraphQL controller thin and free of business logic.

---

## Ownership & Multi-Tenant Scoping at the API Layer

SpringQueuePro is a **multi-tenant system**, meaning:
- tasks are owned by specific users
- users must not see or mutate tasks they do not own

Ownership scoping begins at the API layer.

### How Ownership Is Enforced

GraphQL resolvers:
- extract the authenticated user via `Authentication`
- pass the resolved identity into service-layer methods
- never trust client-provided IDs without scoping

For example:
- queries return only tasks owned by the caller
- mutations verify ownership before allowing updates
- retry operations validate ownership before requeueing

REST authentication endpoints do not operate on tasks at all, which further reduces the risk of cross-tenant leakage.

This layered enforcement mirrors real production systems, where:
- security gates entry at the framework level
- ownership is enforced at the service and persistence layers
- APIs never assume client intent is trustworthy

## Relevant Project Files

### REST (Authentication & Operational)

- `controller/auth/AuthenticationController`
  **Authoritative REST API for identity and session management.**
  Handles registration, login, refresh token rotation, logout, and refresh-token validation.

- `controller/rest/ProcessingEventsController`
  REST endpoints for runtime observability (processing events and worker status).

- `controller/rest/TaskRestController`
  REST mirror of task CRUD operations, primarily for debugging and regression testing.

### GraphQL (Task Domain)

- `controller/graphql/TaskGraphQLController`
  Primary GraphQL API for querying and mutating task state.

- `resources/graphql/schema.graphqls`
  Defines the task schema, enums, queries, and mutations that form the contract between frontend and backend.

- `controller/GraphiQLRedirectController`
  Convenience controller for accessing the GraphiQL UI during development and demos.

### Legacy / Transparency Artifacts

- `controller/rest/ProducerController` *(Deprecated)*
  Historical REST controller from the in-memory queue phase.
  Preserved intentionally to document architectural evolution and design trade-offs.

## GraphQL Schema Design

The GraphQL schema is intentionally:
- strongly typed
- enum-driven
- aligned with backend domain models

### Key Design Choices

- **Enums for `TaskType` and `TaskStatus`**
  Prevent invalid values at the API boundary and eliminate stringly-typed inputs.

- **Typed Input Objects**
  Mutations use input objects (`CreateTaskInput`, `StdUpdateTaskInput`) to allow schema evolution without breaking clients.

- **Schema-Assisted Frontend Behavior**
  The `taskEnums` query allows clients to dynamically load supported values rather than hardcoding them.

This reinforces the idea that the API is a **contract**, not just a transport.

## REST APIs Beyond Authentication

REST still plays a supporting role outside authentication:
- Debugging and regression testing
- Operational visibility (processing events, worker status)
- Educational comparison between REST and GraphQL
- Historical documentation of system evolution

However, REST is **not** treated as an alternate privileged path for task operations. Security rules apply consistently, and GraphQL remains the primary domain interface.

---

## Steps Taken to Mimic Production Quality

### 1. Clear API Responsibility Boundaries

- REST owns identity and session flows
- GraphQL owns task-domain interaction

This avoids overloading a single API style with incompatible concerns.

---

### 2. GraphQL Treated as a First-Class Query Interface

GraphQL is not “just for the UI.” It is:

- introspectable
- schema-driven
- secured at the resolver level
- usable independently of the frontend

---

### 3. No Hidden Side Doors

Even though multiple API styles exist:

- authentication rules apply consistently
- ownership checks are enforced uniformly
- deprecated controllers are clearly marked
- no API bypasses orchestration or persistence rules

---

### 4. Evolution Is Documented, Not Hidden

Legacy controllers are preserved intentionally to show:

- how the system started
- why certain decisions were made
- how the architecture matured

This transparency mirrors real systems with long-lived codebases.

---

## Closing Summary

SpringQueuePro’s API layer reflects **real-world system boundaries**.

- REST handles **who you are and how you authenticate**
- GraphQL handles **what you can see and do with tasks**
- Services remain authoritative
- Persistence remains the source of truth

In short:

> **REST establishes identity. GraphQL expresses intent. The backend enforces correctness.**

This division keeps the API layer clean, scalable, and aligned with production-grade design practices.

---