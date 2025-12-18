Things removed from the README that I maybe liked or something (that I'm just going to record here in the meantime):

---

### System Architecture Summary

- Like any professional distributed job queue system, SpringQueuePro is built around a fully event-driven, asynchronous task execution pipeline with durable persistence, coordinated concurrency, and robust reliability guarantees.

- At its core, SpringQueuePro models a distributed worker-thread architecture: tasks are persisted in PostgreSQL, coordinated via atomic state transitions, claimed safely using Redis-based distributed locks, and executed on a configurable ExecutorService worker pool. This design guarantees idempotent task processing, prevents race conditions when multiple workers (threads) compete for the same job, and ensures that each task is processed exactly once (or retired safely under controlled backoff).

- The platform supports automatic retries with exponential backoff, dead-task prevention, and stateful lifecycle management (**QUEUED → IN_PROGRESS → COMPLETED / FAILED**). Processing logic is fully decoupled using a TaskHandler registry, enabling clean extensibility and domain separation.

- All APIs — both GraphQL and REST — are protected through a modern JWT authentication system with access + refresh tokens, token rotation, revocation, and a Redis-backed refresh token store. The security pipeline uses a stateless Spring Security filter chain, custom authentication filters, and strict authorization boundaries around internal queue management endpoints.

- SpringQueuePro implements role-based access control (RBAC) using Spring Security’s stateless filter chain. Each request passes through a JWT authentication filter that validates tokens, loads user roles from the database, and attaches an authenticated UserDetails principal to the security context. Both GraphQL and REST routes enforce fine-grained authorization rules — public endpoints (login/register) are open, while all internal queue management APIs require authenticated users with the proper roles, ensuring strong separation between public interfaces and privileged system operations.

- SpringQueuePro is fully observability-ready. Using Micrometer, Spring Actuator, and Prometheus, the system records metrics for:
task throughput, retry rates, worker pool utilization, processing duration histograms, queue depth, API call counts, Postgres/Redis health, and JVM resource usage. These metrics are validated using Testcontainers-powered integration tests, ensuring correctness across real Postgres + Redis environments. 

- A lightweight React + TypeScript Dashboard provides a visual interface for interacting with the system — allowing user authentication, task creation, queue inspection, and health monitoring of the backend services. **The entire stack is containerized with Docker**.

For my own sake, SpringQueuePro — aside from the practical experience gained in building such a complex system using Java and Spring Boot — was meant to be an exercise to better my expertise in distributed systems, concurrency control, queue design, cloud-native observability (*this will be expanded on in the **Future Improvements** section*), security architecture, and full-stack application development, all packaged in a clean, maintainable, and extensible software engineering project.

---

SpringQueuePro is a distributed, fault-tolerant task processing platform designed around durable persistence, coordinated concurrency, and strict execution guarantees.

---