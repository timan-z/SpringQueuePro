import NavBar from "../components/NavBar";

export default function AboutPage() {
  return (
    <div>
      <NavBar />
      <div
        style={{
          fontFamily: "monospace",
          padding: "40px",
          maxWidth: "900px",
          margin: "0 auto",
          lineHeight: 1.6
        }}
      >
        <h1 style={{ fontSize: "2rem", marginBottom: "10px" }}>
          About SpringQueuePro
        </h1>

        <p>
          <strong>SpringQueuePro</strong> is a production-grade, distributed
          task-processing engine built with modern cloud-ready fundamentals:
          JWT-based authentication, Redis-backed distributed locking, PostgreSQL
          persistence, retry policies, Prometheus metrics, and Spring Boot 3.
        </p>

        <p>
          This project began as a "GoQueue" prototype, evolved into
          "SpringQueue", and is now a fully instrumented, observable,
          distributed system capable of reliably processing asynchronous work
          with cloud-level behaviors.
        </p>

        <hr style={{ margin: "30px 0" }} />

        <h2>Core Features</h2>

        <ul>
          <li>JWT Access + Refresh Tokens (Rotation + Logout via Redis)</li>
          <li>PostgreSQL persistence with optimistic locking</li>
          <li>Redis distributed locks for safe task claims</li>
          <li>Retry backoff scheduling via ScheduledExecutor</li>
          <li>Full Prometheus metrics via Micrometer</li>
          <li>Actuator health checks (DB, Redis, Disk, SSL)</li>
          <li>GraphQL API for safe, typed access</li>
          <li>REST API mirrors for reference and testing</li>
          <li>Containerized & ready for cloud deployment</li>
        </ul>

        <hr style={{ margin: "30px 0" }} />

        <h2>High-Level Architecture</h2>

        <pre
          style={{
            backgroundColor: "#f5f5f5",
            padding: "20px",
            borderRadius: "8px",
            overflowX: "auto"
          }}
        >
        {`
            ┌───────────────────────┐
            │      Frontend         │
            │ (React + TypeScript)  │
            └─────────▲─────────────┘
                        │ GraphQL / REST (JWT)
            ┌─────────┴─────────────┐
            │     SpringQueuePro     │
            │  (Spring Boot Backend) │
            └─────────▲─────────────┘
                        │
        ┌────────────┴────────────┬────────────┐
        │                          │            │
        ┌──────┐                 ┌────────┐   ┌──────────┐
        │Redis │                 │Postgres│   │Prometheus│
        │Lock/ │<--distributed-->| Task   │   │ Metrics  │
        │Store │                 │ Store  │   └──────────┘
        └──────┘                 └────────┘
                        │
                ┌────┴─────┐
                │ Grafana   │ (Monitoring dashboard)
                └───────────┘
        `}
        </pre>

        <hr style={{ margin: "30px 0" }} />

        <h2>Developer Notes</h2>

        <p>
          This frontend exists to showcase the internal behavior of the system
          in a clean, recruiter-friendly way: task flow, queue state,
          processing, retries, system health, and authentication.
        </p>

        <p>
          The underlying backend has been intentionally designed with
          production-first patterns: explicit retries, telemetry, observability,
          testcontainers-based integration tests, clean domain separation, and
          ready-to-deploy Docker images.
        </p>

        <hr style={{ margin: "30px 0" }} />

        <h2>Useful Links</h2>

        <ul>
          <li>
            <a href="https://github.com/timan-z/SpringQueuePro" target="_blank">
              GitHub Repository
            </a>
          </li>
          {/* DEBUG: Edit the below later. */}
          {/*<li>
            <a href="/docs" target="_blank">API Documentation (Coming Soon)</a>
          </li>
          <li>
            <a href="/graphql" target="_blank">
              GraphQL Playground / Schema
            </a>
          </li>*/}
        </ul>

        <hr style={{ margin: "30px 0" }} />

        <p style={{ opacity: 0.6 }}>
          This page will expand later with more system details, diagrams, and
          project motivations.
        </p>
      </div>
    </div>
  );
}