# Authentication & Identity Management (Deep Dive)

## Introduction

This document provides a focused deep dive into how **Authentication & Identity Management** is handled in the SpringQueuePro system. It will touch on how user identity is represented and persisted, how authentication is performed in a **stateless manner using JWTs**, and how session trust is maintained through **access tokens**, **refresh tokens**, and **Redis-backed refresh token rotation**. 

- The goal of this section is to explain who a `User` is, how they prove their identity to the system, and how that trust is safely maintained over time, even in a distributed, horizontally scalable environment. (*It will also touch on the **deliberate architectural choices made to mimic real-world system design practices***).

**NOTE**: This document will **not** cover authorization concerns relating to role-based access control (RBAC), endpoint protection, nor method-level security annotations. (Those topics are addressed separately in the Security Configuration & RBAC "deep dive" documentation).

---

## Table of Contents
- [Why a `User` Exists in My Task/Job Queue System](#why-a-user-exists-in-my-taskjob-queue-system)
- [Authentication Model](#authentication-model)
- [Relevant Project Files](#relevant-project-files)
- [Steps Taken to Mimic Production Quality](#steps-taken-to-mimic-production-quality)

## Why a `User` Exists in My Task/Job Queue System

SpringQueuePro is a distributed task/job processing engine, and in its most minimal form (*see SpringQueue*) can operate without any notion of `User`(s) at all. (*That is, tasks could be submitted and processed globally*). However, SpringQueuePro is intentionally designed to resemble real-world task queue services, where a queue is **not a single global resource but a multi-tenant platform consumed by multiple independent clients**.

Having the concept of a `User` allows the system to model ownership, isolation, and accountability in the realm of task execution. **Each authenticated user represents a distinct consumer of the queue service**, with their own logical task-processing domain. Tasks are created on behalf of a user, persisted with ownership metadata, and later queried, managed, and observed within the same user boundary.

### This guarantees:
1. Tasks are scoped to an owning user, preventing accidental or unauthorized cross-visibility.
2. The system can safely support multiple concurrent users submitting and inspecting tasks simultaneously.
3. The queue behaves like a shared infrastructure service rather than a single-purpose job runner.

While SpringQueuePro is not a hosted production service (*for commercial use*), this multi-user model mirrors how real-world systems (such as Celery-backed platforms, managed queues, or internal job orchestration services) separate concerns between infrastructure and clients. The queue engine itself remains generic and reusable, while users provide the contextual boundary that makes the system realistic, secure, and extensible.

The introduction of the `User` entity is orthogonal to task execution mechanics: workers, retries, locking, and persistence behave identically regardless of who owns a task. User identity exists to define who may submit, view, and manage tasks, not how tasks are processed. **This separation keeps the core queue engine clean while enabling SpringQueuePro to function as a true multi-tenant service rather than a single-user demonstration**.

## Authentication Model

SpringQueuePro uses a **dual-token authentication model** inspired by OAuth-style systems:
- **Access Tokens**
    - Short-lived JWTs
    - Used to authenticate API requests
    - Never stored server-side
    - Expire quickly to reduce "blast radius" if compromised
- **Refresh Tokens**
    - Longer-lived JWTs
    - Stored server-side in Redis
    - Used only to obtain new access tokens
    - Rotated on every refresh to prevent replay attacks

This separation allows the system to **balance security and usability in a way that mirrors real production systems**.

## Relevant Project Files
`security/`:
- `JwtAuthenticationFilter`

    - **Enforces stateless authentication** by validating access tokens on every request 
    
    - Establishes the authenticated user in Spring Security's `SecurityContext`.

    - Explicitly rejects refresh tokens at the filter layer to prevent misuse as API credentials.

    - Ensures that *only* short-lived tokens (access tokens) can authenticate requests.

- `JwtUtil`
    - **Centralizes JWT creation and validation logic**, ensuring consistent token structure, cryptographic verification, expiration enforcement, and explicit token typing across the system.

- `RefreshTokenService`
    - **Provides Redis-backed storage** for refresh tokens, enabling rotation, revocation, and replay protection at the authentication flow level.

- `CustomUserDetailsService`
    - **Bridges persisted user identity (UserEntity) into Spring Security’s authentication model**, allowing JWT-authenticated requests to resolve a fully populated security principal.

- `dto/*`
    - Just a collection of data transfer objects for readability, for example:<br>
    `public record LoginRequest(String email, String password) { }`

`controller/auth/AuthenticationController`
- **Defines the authentication lifecycle endpoints** (register, login, refresh, logout) and orchestrates token issuance, rotation, and revocation.

- This controller is intentionally separate from the task-related controllers and APIs (clean separation of concerns).

`redis/RedisTokenStore`
- **Provides Redis-backed persistence** for issued refresh tokens, enabling rotation, revocation, and replay prevention across the cluster.

`domain/entity/UserEntity`
- `UserEntity` is the persistent user identity role metadata. It represents a user's account information—which, for the sake of this project, is just an email and password hash.

- PostgreSQL remains the system of record for identity, independent of authentication tokens or session state. (*This data is stored in the database and doesn't depend on either tokens or if the user is currently logged in*).

## Steps Taken to Mimic Production Quality

I like to consider my SpringQueuePro project production-grade (or at the very least production-adjacent) because I designed the system around **operational realities**, and so this section here is dedicated to the design choices—relating to SQP's authentication system—that reflect this decision.

Here are some key characteristics that stand out about SQP's authentication setup:

### 1. Stateless Runtime Authentication

Every request is authenticated independently using an access token. There are:

- No HTTP sessions
- No server-side authentication state
- No reliance on sticky sessions

This allows the system to scale horizontally and survive restarts without breaking authentication.

---

### 2. Short-Lived Access Tokens for Safety

Access tokens are intentionally short-lived. If an access token is compromised, its usefulness is limited in time, reducing the impact of credential leakage.

This reflects real-world security practices used in OAuth2, cloud APIs, and large-scale distributed systems.

---

### 3. Refresh Tokens for Session Continuity

Rather than extending access token lifetimes indefinitely, SpringQueuePro uses refresh tokens to maintain session continuity. This allows:
- Frequent access token rotation
- Reduced exposure window
- Better control over long-lived sessions

This mirrors how real systems balance usability with security.

---

### 4. Server-Side Refresh Token Storage (Redis)

Although JWTs are stateless, refresh tokens are **intentionally tracked server-side**. This enables capabilities that purely stateless JWT systems cannot provide:

- Explicit logout
- Forced session invalidation
- Replay attack prevention
- Cluster-wide session control

Redis serves as a lightweight, fast coordination layer that supports these guarantees without reintroducing heavyweight session management.

---

### 5. Clear Separation of Concerns

Authentication logic is cleanly separated into:
- Identity persistence (`UserEntity`)
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
- Ambiguous Redis vs JWT expiration scenarios

---

### 7. Alignment with Real Operational Systems

While SpringQueuePro is a learning project, its authentication model aligns closely with:

- OAuth-style token systems
- Cloud service authentication models
- Real background job platforms with multi-tenant access (of course e.g., Celery)
