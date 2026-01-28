import NavBar from "../components/NavBar";

/* 2026-01-28-NOTE: Adding authorization context to About Page to alter how this page is loaded
depending on if the user is logged in or not. I want the About Page to be viewable from the Login/Register page
(meaning the user hasn't yet logged in or doesn't yet have an account). The About Page certainly should not be gated! 
---
- I'm going to hide the Navigation Bar behind Access Token check.
- I'm going to replace it with a "Return to Login/Registration Page" bar or button instead!
*/
import { useAuth } from "../utility/auth/AuthContext";
import { Link } from "react-router-dom";

export default function AboutPage() {
  const { accessToken } = useAuth();

  // In-line styling is probably not the best practice, but I'll have this for now.
  const cardStyle: React.CSSProperties = {
    background: "#ffffff",
    border: "2px solid #6db33f",
    borderRadius: "10px",
    padding: "18px 20px",
    boxShadow: "0 0 10px rgba(109,179,63,0.20)",
  };

  const pillStyle: React.CSSProperties = {
    display: "inline-block",
    padding: "4px 10px",
    borderRadius: "999px",
    border: "1px solid #6db33f",
    background: "#f0f6ef",
    fontSize: "12px",
    marginRight: "8px",
    marginBottom: "8px",
  };

  const sectionTitle: React.CSSProperties = {
    marginTop: "0px",
    marginBottom: "10px",
    color: "#2d2d2d",
    fontSize: "1.1rem",
  };

  return (
    <div>
      {/* If the user is viewing the About Page in an authorized context, navigation bar appears. */}
      {accessToken && (<NavBar />)}
      {/* If the user is not authenticated, navigation bar would be replaced with a similar bar re-directing to the Auth pages: */}
      {!accessToken && (
        <div className="navBar">
          <nav className="navContainer">
            <div className="navLeft">
              <Link to="/login" className="navLink">
                Login
              </Link>
              <Link to="/register" className="navLink">
                Register
              </Link>
            </div>
          </nav>
        </div>
      )}

      <div
        style={{
          fontFamily: "monospace",
          padding: "40px",
          maxWidth: "950px",
          margin: "0 auto",
          lineHeight: 1.65,
          color: "#2d2d2d",
        }}
      >
        <h1 style={{ fontSize: "2rem", marginBottom: "6px", color: "#6db33f" }}>
          SpringQueuePro
        </h1>

        <div style={{ color: "#555", fontSize: "13px", marginBottom: "18px" }}>
          A production-style distributed task queue built to demonstrate correctness, reliability,
          observability, and modern backend architecture.
        </div>

        {/* Elevator pitch */}
        <div style={{ ...cardStyle, marginBottom: "18px" }}>
          <p style={{ marginTop: 0 }}>
            <strong>SpringQueuePro</strong> is a distributed, fault-tolerant task processing platform
            inspired by systems like Celery / BullMQ / AWS SQS. It supports durable task persistence,
            safe concurrent execution, centralized retries with exponential backoff, JWT auth with
            refresh token rotation, and an observability surface (Actuator + Micrometer/Prometheus).
          </p>

          <p style={{ marginBottom: 0 }}>
            This dashboard exists to make those backend behaviors visible in a clean, recruiter-friendly way:
            creating tasks, monitoring processing, inspecting task lifecycle, and checking system health.
          </p>
        </div>

        {/* What this project demonstrates */}
        <div style={{ ...cardStyle, marginBottom: "18px" }}>
          <h2 style={sectionTitle}>What this project demonstrates</h2>

          <div style={pillStyle}>PostgreSQL durability</div>
          <div style={pillStyle}>Redis distributed locks</div>
          <div style={pillStyle}>Atomic state transitions</div>
          <div style={pillStyle}>Retries + backoff</div>
          <div style={pillStyle}>JWT access/refresh</div>
          <div style={pillStyle}>GraphQL + REST</div>
          <div style={pillStyle}>Metrics + health</div>
          <div style={pillStyle}>React + TypeScript UI</div>

          <ul style={{ marginTop: "10px" }}>
            <li>
              Durable tasks persisted in PostgreSQL with a strict lifecycle
              (<code>QUEUED → INPROGRESS → COMPLETED/FAILED</code>).
            </li>
            <li>
              Redis-backed distributed coordination to prevent double execution under concurrency.
            </li>
            <li>
              Centralized retry orchestration with exponential backoff (handlers stay “pure”).
            </li>
            <li>
              Secured APIs via JWT access tokens + rotating refresh tokens (RBAC-ready).
            </li>
            <li>
              Observability via Spring Boot Actuator + Micrometer/Prometheus metrics.
            </li>
          </ul>
        </div>

        {/* How to use the dashboard */}
        <div style={{ ...cardStyle, marginBottom: "18px" }}>
          <h2 style={sectionTitle}>Dashboard walkthrough</h2>
          <ul style={{ marginTop: 0 }}>
            <li>
              <b>Tasks</b>: enqueue work via GraphQL + view a small “recent tasks” feed.
            </li>
            <li>
              <b>Tasks Dashboard</b>: filter/search across tasks and inspect a task in a detail drawer
              (shows the exact GraphQL query + returned JSON).
            </li>
            <li>
              <b>Processing Monitor</b>: worker pool status + processing events + queue metrics.
            </li>
            <li>
              <b>System Health</b>: Spring Boot health components + a simple metrics snapshot.
            </li>
            <li>
              <b>Token Dashboard</b>: high-level token/session visibility (details documented in the repo).
            </li>
          </ul>
        </div>

        {/* Architecture snapshot */}
        <div style={{ ...cardStyle, marginBottom: "18px" }}>
          <h2 style={sectionTitle}>High-level architecture</h2>

          <pre
            style={{
              backgroundColor: "#f5f5f5",
              padding: "14px",
              borderRadius: "8px",
              overflowX: "auto",
              fontSize: "12px",
              marginBottom: "10px",
            }}
          >
            {`React (TypeScript UI)
              -> GraphQL/REST (JWT)
            Spring Boot 3 backend
              -> PostgreSQL (durable task store)
              -> Redis (locks + refresh token store + coordination)
              -> Actuator/Micrometer (metrics + health)`}
          </pre>

          <div style={{ fontSize: "13px", color: "#555" }}>
            The long-form architecture diagrams and implementation details live in the README.
          </div>
        </div>

        {/* Links */}
        <div style={cardStyle}>
          <h2 style={sectionTitle}>Links</h2>

          <ul style={{ marginTop: 0, marginBottom: 0 }}>
            <li>
              <a href="https://github.com/timan-z/SpringQueuePro" target="_blank" rel="noreferrer">
                GitHub Repository (full README + diagrams + design notes)
              </a>
            </li>
          </ul>

          <div style={{ marginTop: "14px", fontSize: "12px", color: "#666" }}>
            Note: this page is intentionally concise—think “demo overview”, not full documentation.
          </div>
        </div>
      </div>
    </div>
  );
}