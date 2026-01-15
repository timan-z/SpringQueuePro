# Authentication & Identity Management (Deep Dive)

## Introduction

This document provides a focused deep dive into how **Authentication & Identity Management** is implemented in the SpringQueuePro system. It explains how user identity is represented and persisted, how authentication is performed using **stateless JWT access tokens**, and how session trust is maintained through **Redis-backed refresh token persistence and rotation**.

The goal of this section is to clearly articulate:
- Who a `User` is in the context of SpringQueuePro
- How users prove their identity to the system
- How that trust is safely maintained over time in a distributed, horizontally scalable environment

Special emphasis is placed on the **deliberate architectural decisions made to mirror real-world production systems**, rather than relying on simplified or purely academic authentication patterns.

> **NOTE**: This document focuses strictly on authentication and identity management.  
> Authorization concerns such as RBAC, endpoint protection rules, and method-level security annotations are covered separately in the *Security Configuration & RBAC* deep dive.

---

## Table of Contents
- [Why a `User` Exists in My Task/Job Queue System](#why-a-user-exists-in-my-taskjob-queue-system)
- [Authentication Model](#authentication-model)
- [Relevant Project Files](#relevant-project-files)
- [Steps Taken to Mimic Production Quality](#steps-taken-to-mimic-production-quality)

---

## Why a `User` Exists in My Task/Job Queue System

At its core, SpringQueuePro is a distributed task/job processing engine. In its most minimal form (as demonstrated in *SpringQueue*), such a system can operate without any notion of `User`s at all — tasks could be submitted and processed globally without ownership or isolation.

SpringQueuePro intentionally moves beyond this model to resemble **real-world queue platforms**, where a queue is not a single global resource but a **multi-tenant service consumed by multiple independent clients**.

Introducing the concept of a `User` enables the system to model:
- Ownership
- Isolation
- Accountability

Each authenticated user represents a **distinct consumer of the queue service**, operating within their own logical boundary. Tasks are created on behalf of a user, persisted with ownership metadata, and later queried, managed, and observed within that same boundary.

### This guarantees:
1. Tasks are scoped to an owning user, preventing accidental or unauthorized cross-visibility.
2. The system can safely support multiple concurrent users submitting and inspecting tasks.
3. The queue behaves like shared infrastructure rather than a single-purpose job runner.

Importantly, **user identity is orthogonal to task execution mechanics**. Workers, retries, locking, and persistence behave identically regardless of task ownership. Identity exists to define *who may submit, view, and manage tasks*, not *how tasks are processed*.

This separation keeps the core queue engine clean while allowing SpringQueuePro to function as a realistic, secure, multi-tenant system rather than a single-user demonstration.

---

## Authentication Model

SpringQueuePro uses a **hybrid authentication model** inspired by OAuth-style systems, combining stateless and stateful components:

### Access Tokens
- Short-lived JWTs
- Used to authenticate API requests
- Never stored server-side
- Cryptographically validated on every request
- Explicitly typed (`type=access`) to prevent misuse

### Refresh Tokens
- Longer-lived JWTs
- **Persisted server-side in Redis**
- Used only to obtain new access tokens
- Rotated on every refresh
- Explicitly revocable (logout, expiry, rotation)

This separation allows the system to balance **scalability, security, and usability**, mirroring how production-grade APIs and cloud platforms handle authentication.

---

## Relevant Project Files

### `security/`

#### `JwtAuthenticationFilter`
- Enforces stateless authentication by validating access tokens on every protected request.
- Extracts identity from JWTs and populates Spring Security’s `SecurityContext`.
- Explicitly rejects refresh tokens at the filter layer to prevent misuse as API credentials.
- Ensures only short-lived access tokens may authenticate API requests.

#### `JwtUtil`
- Centralizes JWT creation and validation logic.
- Handles cryptographic verification, expiration enforcement, token typing, and safe subject extraction.
- Explicitly distinguishes expired tokens from malformed or tampered tokens.

#### `CustomUserDetailsService`
- Bridges persisted user identity (`UserEntity`) into Spring Security’s authentication model.
- Loads users from PostgreSQL and resolves roles/authorities dynamically at request time.
- Allows authorization decisions to remain data-driven even with stateless JWTs.

#### `dto/*`
- Simple request/response DTOs used by authentication endpoints for clarity and boundary enforcement.
  - e.g. `LoginRequest`, `RegisterRequest`, `RefreshRequest`, `AuthResponse`

---

### `controller/auth/AuthenticationController`

- Defines the authentication lifecycle endpoints:
  - `POST /auth/register`
  - `POST /auth/login`
  - `POST /auth/refresh`
  - `POST /auth/logout`
  - `GET /auth/refresh-status`
- Orchestrates token issuance, rotation, and revocation.
- Intentionally isolated from task-related controllers to preserve separation of concerns.

---

### `redis/RedisTokenStore`

- Acts as the **source of truth for refresh token lifecycle**.
- Persists issued refresh tokens with TTL.
- Resolves refresh tokens to owning users.
- Enables:
  - Token rotation
  - Explicit logout
  - Session invalidation
  - Replay protection
  - Cluster-wide session coordination

> **NOTE**: Earlier iterations of the project included a `RefreshTokenService`.  
> The current architecture standardizes refresh token persistence exclusively through `RedisTokenStore`, which supersedes and effectively deprecates that older abstraction.

---

### `domain/entity/UserEntity`

- Persistent representation of a user account.
- Stores:
  - Email (primary key)
  - BCrypt-hashed password
  - Role metadata
- PostgreSQL serves as the **system of record for identity**, independent of session state or token lifetime.

---

## Steps Taken to Mimic Production Quality

SpringQueuePro’s authentication system was designed around **operational realities**, not just theoretical correctness. The following characteristics reflect that intent:

---

### 1. Stateless Runtime Authentication

Every protected request is authenticated independently using an access token.

- No HTTP sessions
- No server-side authentication state
- No sticky sessions

This allows the system to scale horizontally and tolerate restarts without breaking authentication.

---

### 2. Short-Lived Access Tokens

Access tokens are intentionally short-lived to reduce the blast radius of credential leakage.

This mirrors best practices used in OAuth2, cloud APIs, and large-scale distributed systems.

---

### 3. Refresh Tokens for Session Continuity

Session continuity is maintained via refresh tokens rather than long-lived access tokens.

This enables:
- Frequent access token rotation
- Reduced exposure windows
- Better control over long-lived sessions

---

### 4. Server-Side Refresh Token Tracking (Redis)

Although access tokens are stateless, refresh tokens are **intentionally tracked server-side**.

This enables capabilities that purely stateless JWT systems cannot provide:
- Explicit logout
- Forced session invalidation
- Replay attack prevention
- Concurrency-safe rotation

Redis serves as a lightweight coordination layer without reintroducing heavyweight session management.

---

### 5. Clear Separation of Concerns

Authentication responsibilities are cleanly separated into:
- Identity persistence (`UserEntity`, `UserRepository`)
- Token cryptography (`JwtUtil`)
- Request authentication (`JwtAuthenticationFilter`)
- Session coordination (`RedisTokenStore`)
- Flow orchestration (`AuthenticationController`)

This modularity improves testability, debuggability, and long-term maintainability.

---

### 6. Failure-Aware Design

Authentication flows explicitly handle:
- Expired tokens
- Invalid or tampered tokens
- Missing tokens
- Revoked refresh tokens
- Redis vs JWT expiration mismatches

These cases are tested and handled deliberately rather than implicitly.

---

### 7. Alignment with Real Operational Systems

While SpringQueuePro is a learning project, its authentication model closely aligns with:

- OAuth-style token systems
- Cloud service authentication patterns
- Multi-tenant job processing platforms (e.g., Celery-backed services)

This makes the system both realistic and extensible, without compromising clarity or correctness.

---

## SUMMARY

SpringQueuePro uses short-lived JWT access tokens for stateless authentication and Redis-backed refresh tokens for server-side session control. Access tokens cryptographically assert identity and are validated per request, while refresh tokens are persisted, rotated, and revoked centrally. Spring Security enforces endpoint and method-level authorization, resolving authenticated users and roles dynamically via a PostgreSQL-backed `UserDetailsService`. This hybrid design balances scalability, security, and revocability while keeping authorization decisions consistent and extensible.