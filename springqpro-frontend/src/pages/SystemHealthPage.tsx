import { useEffect, useState } from "react";
import NavBar from "../components/NavBar";
import { useAuth } from "../utility/auth/AuthContext";
import { getSystemHealth, getSystemMetric } from "../api/api";

interface HealthComponent {
  status: string;
}

interface HealthResponse {
  status: string;
  components: Record<string, HealthComponent>;
}

export default function SystemHealthPage() {
  const { accessToken } = useAuth();

  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const [queueDepth, setQueueDepth] = useState<number | null>(null);
  const [completed, setCompleted] = useState<number | null>(null);
  const [failed, setFailed] = useState<number | null>(null);

  const fetchAll = async () => {
    if (!accessToken) return;

    setLoading(true);

    const h = await getSystemHealth(accessToken);
    setHealth(h);

    const q = await getSystemMetric("springqpro_queue_memory_size", accessToken);
    setQueueDepth(q.value ?? q.totalMs ?? null);

    const c = await getSystemMetric("springqpro_tasks_completed_total", accessToken);
    setCompleted(c.value ?? c.totalMs ?? null);

    const f = await getSystemMetric("springqpro_tasks_failed_total", accessToken);
    setFailed(f.value ?? f.totalMs ?? null);

    setLoading(false);
  };

  useEffect(() => {
    fetchAll();
    const id = setInterval(fetchAll, 5000);
    return () => clearInterval(id);
  }, [accessToken]);

  // -------------------------------------------------
  // Styles
  // -------------------------------------------------
  const card: React.CSSProperties = {
    background: "#fff",
    border: "2px solid #6db33f",
    borderRadius: "10px",
    padding: "22px",
    width: "600px",
    marginBottom: "25px",
    boxShadow: "0 0 10px rgba(109,179,63,0.3)",
  };

  const title: React.CSSProperties = {
    fontSize: "1.3rem",
    marginBottom: "12px",
  };

  return (
    <div style={{ minHeight: "100vh", background: "#f6f8fa", fontFamily: "monospace" }}>
      <NavBar />

      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", paddingTop: "30px" }}>
        <h1 style={{ fontSize: "1.8rem", marginBottom: "25px" }}>System Health Overview</h1>

        {/* Health summary */}
        <div style={card}>
          <h2 style={title}>Spring Boot Health</h2>

          {loading || !health ? (
            "Loading…"
          ) : (
            <>
              <div>
                <b>Overall Status:</b>{" "}
                <span style={{ color: health.status === "UP" ? "#6db33f" : "red" }}>
                  {health.status}
                </span>
              </div>

              <hr style={{ margin: "12px 0", borderColor: "#6db33f" }} />

              {health.components && Object.keys(health.components).length > 0 && (
              Object.entries(health.components).map(([k, v]) => (
                  <div key={k} style={{ marginBottom: "10px" }}>
                    <b>{k}: </b>
                    <span style={{ color: v.status === "UP" ? "#6db33f" : "red" }}>
                      {v.status}
                    </span>
                  </div>
                ))
              )}
            </>
          )}

          <button
            onClick={fetchAll}
            style={{
              marginTop: "10px",
              padding: "8px 12px",
              background: "#6db33f",
              border: "none",
              color: "white",
              fontWeight: "bold",
              borderRadius: "6px",
              cursor: "pointer",
            }}
          >
            Refresh Now
          </button>
        </div>

        {/* Metrics snapshot */}
        <div style={card}>
          <h2 style={title}>Metrics Snapshot</h2>

          {loading ? (
            "Loading…"
          ) : (
            <>
              <div><b>Queue Depth:</b> {queueDepth ?? "N/A"}</div>
              <div><b>Tasks Completed:</b> {completed ?? "N/A"}</div>
              <div><b>Tasks Failed:</b> {failed ?? "N/A"}</div>
              <div style={{ fontSize: "13px", color: "#555", marginTop: "10px" }}>
                Updated every 5 seconds.
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
