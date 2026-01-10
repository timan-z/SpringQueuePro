# Security Configuration & RBAC (Deep Dive)

## Introduction

This document provides a focused deep dive into how **Security Configuration & Role-Based Access Control (RBAC)** is implemented in my SpringQueuePro system. While the **Authentication & Identity Management** documentation explains *who* a user is and *how* they prove their identity, this document focuses on **what an authenticated user is allowed to do once trust has been established**. (*It's nice to separate authentication and authorization concerns into different documents since they're easy to conflate and mix together*).

This section will explain and highlight:
- How **Spring Security** is configured to protect both REST and GraphQL APIs.
- How **Role-Based Access Control (RBAC)** is enforced consistently across the system (applied at the API and resolver levels).
- How **authorization boundaries** are enforced consistently across the system.
- Architectural decisions made to mirror real-world production systems.

**NOTE**: This document intentionally does **not** re-explain authentication mechanics such as JWT structure, access vs refresh tokens, or Redis-backed token rotation. Those topics are covered in the *Authentication & Identity Management* deep dive.

---

## Table of Contents

- [Why Authorization Is Important in SpringQueuePro](#why-authorization-is-important-in-springqueuepro)
- [Authorization vs Resource Ownership](#authorization-vs-resource-ownership)
- [Security Model Overview](#security-model-overview)
- [Relevant Project Files](#relevant-project-files)
- [RBAC Enforcement Strategy](#rbac-enforcement-strategy)
- [Steps Taken to Mimic Production Quality](#steps-taken-to-mimic-production-quality)

## Why Authorization Is Important in SpringQueuePro

SpringQueuePro is designed as a multi-tenant task queue system, not a single-user job runner. Even though tasks are processed by shared infrastructure (workers, executors, Redis locks), ownership and visibility must remain strictly isolated per user. This is critical because:

1. Tasks are owned by specific users and must not be visible or mutable across tenants.

2. Different API surfaces expose different operational capabilities (task submission, querying, system health, processing metrics).

3. The system must be extensible to support additional roles (e.g., admin, operator, read-only) without refactoring core services. (***I dont actually have this as of 01/09/2026 but the door is left open for scalability***).

4. Without a clear authorization strategy, the system would quickly devolve into a globally shared queue with no meaningful security boundaries—a design that would be unacceptable in any real production environment.

## Authorization vs Resource Ownership

SpringQueuePro distinguishes between **access authorization** and **resource ownership enforcement**.

Spring Security (via endpoint rules and `@PreAuthorize`) ensures that only authenticated users can invoke protected APIs and resolvers. However, authentication alone is insufficient in a multi-tenant system where users operate on distinct, user-owned resources.

For this reason, **ownership is enforced at the service and persistence layers**, not solely at the controller or resolver level. Repository queries and service methods are scoped to the authenticated user, ensuring that even authorized requests cannot operate on resources they do not own.

This layered approach mirrors real-world systems, where framework-level authorization gates access to APIs, and domain-level checks enforce correctness and tenant isolation.

(For more on this, see "**Ownership Enforcement at the Persistence Layer**" section in my **Task Domain Persistence "deep dive" document**).

## Security Model Overview

SpringQueuePro uses **Spring Security** as the central enforcement mechanism for authorization. Once a request is authenticated via JWT, Spring Security applies a **stateless authorization model** based on:
- The authenticated `Principal` resolved from the JWT.
- The requested API surface (REST vs GraphQL).
- Explicitly defined rules at the configuration level.

The system enforces Authorization at the **API boundary**, ensuring that:
- Unauthorized requests are rejected at the framework boundary.
- Domain services can assume valid authentication context.
- Security rules remain centralized, explicit, and auditable.

SpringQueuePro does **not** rely on HTTP sessions, server-side login state, or implicit trust propagation. Every request is evaluated independently, mirroring how production APIs behave in distributed and horizontally scaled environments.

## Relevant Project Files
- `config/SecurityConfig`—serves as the **authoritative security policy definition for the entire backend**. It defines how requests are authenticated, *which* endpoints are protected, and *when* authorization checks are applied. <br>**Some key responsibilities**:
   - Defines the **Spring Security filter chain**.
   - Enforces **stateless session management**.
   - Attaches the custom `JwtAuthenticationFilter`.
   - Explicitly declares which endpoints require authentication.
   - Configuring CORS for browser-based clients.

- `security/JwtAuthenticationFilter`—used mainly in authentication but also plays a role in authorization. Without this filter, method and endpoint-level authorization rules would have no trusted subject to evaluate.
<br>**Some key responsibilities include**:
   - Establishing the authenticated `Principal`.
   - Populating the `SecurityContext` with resolved authorities.
   - Ensuring downstream authorization checks have a reliable identity context.

## RBAC Enforcement Strategy

SpringQueuePro enforces authorization using **a layered approach**, combining:
1. **Endpoint-level security** (via `SecurityFilterChain`)
2. **Method-level security** (via `@PreAuthorize`)
3. **Domain-level ownership checks** (inside services)

### 1. Endpoint-Level Enforcement

At the HTTP layer, the system distinguishes between:

- **Public endpoints**

  - `/auth/**`
  - `/actuator/health`
- **Protected endpoints**

  - `POST /graphql`
  - `/api/tasks/**`
  - `/api/internal/**`
  - `/api/processing/**`

GraphQL is explicitly secured **only for POST requests**, reflecting the fact that GraphQL operations are executed via POST. This avoids unnecessarily protecting static or browser-facing resources while still securing actual data access.

### 2. Method-Level Enforcement (GraphQL)

GraphQL resolvers are secured using `@PreAuthorize("isAuthenticated()")`. This ensures that:

- Every resolver invocation runs under an authenticated security context
- Authorization is enforced *inside* the GraphQL execution layer, not just at the HTTP boundary
- Future role-based restrictions can be applied per resolver without restructuring the API

Because GraphQL exposes a single endpoint, **resolver-level authorization is essential**. Securing only the `/graphql` endpoint would be insufficient in a real system.

### 3. Domain-Level Ownership Checks

Beyond framework-level authorization, SpringQueuePro enforces **ownership constraints** explicitly inside service methods. For example:

- A user cannot update or delete tasks they do not own
- Retry operations validate task ownership before requeueing

This protects against both accidental misuse and future changes that might expose additional execution paths.

## REST vs GraphQL Authorization Boundaries

SpringQueuePro intentionally distinguishes between REST and GraphQL responsibilities:

- **GraphQL**

  - Read-heavy querying
  - Task inspection and exploration
  - Fine-grained resolver-level authorization
  - Dashboard-facing API

- **REST**

  - Command-style or operational endpoints
  - System health, processing metrics, internal views
  - Clear URL-based access boundaries

Both API styles are protected uniformly by Spring Security and JWT authentication. This ensures that:

- No “side door” exists through REST or GraphQL
- Authorization behavior remains consistent regardless of transport
- The backend remains API-agnostic from a security perspective

---

## Steps Taken to Mimic Production Quality

SpringQueuePro’s authorization model reflects real operational systems in several important ways:

### 1. Stateless Authorization

Every request is authorized independently. There is:
- No session affinity
- No cached authorization state
- No reliance on previous requests

This all ensures predictable behavior under load and across restarts.

---

### 2. Explicit Security Boundaries

Authorization rules are **declared explicitly**:
- At the filter chain
- At the resolver level
- At the service level

There are no implicit “trusted paths” or magic defaults.

---

### 3. Defense-in-Depth

Authorization is not enforced in a single place. Instead, the system layers:
- Framework-level checks
- Method-level annotations
- Domain ownership validation

This reduces the risk of accidental privilege escalation.

---

### 4. GraphQL-Aware Security Design

Rather than treating GraphQL as a special case, SpringQueuePro embraces its execution model:

- Resolver-level security
- Ownership checks per operation
- Explicit authentication requirements

This mirrors how production GraphQL services are secured.

---

### 5. Future Role Expansion

This is touched on a little earlier in the documentation, but although the current implementation primarily enforces authentication rather than multiple roles, the system is **structurally prepared** for:
- Admin-only resolvers
- Read-only roles
- Operator-level system access

Adding these would require policy changes—not architectural rewrites—another way that my SpringQueuePro system emphasizes scalability.

---