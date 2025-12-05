import { useEffect, useState } from "react";
import NavBar from "../components/NavBar";
//import { getHealth, getMetric } from "../api/api";
import { useAuth } from "../utility/auth/AuthContext";

interface HealthComponent {
  status: string;
  details?: any;
}

interface HealthResponse {
  status: string;
  components: Record<string, HealthComponent>;
}

export default function SystemHealthPage() {
  const { accessToken } = useAuth();

  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [loading, setLoading] = useState(true);

  // Mini-metrics snapshot
  const [queueDepth, setQueueDepth] = useState<number | null>(null);
  const [completed, setCompleted] = useState<number | null>(null);
  const [failed, setFailed] = useState<number | null>(null);

  // -----------------------------------------------------
  // FETCH HEALTH INFO
  // -----------------------------------------------------
  /*const fetchHealth = async () => {
    try {
      const h = await getHealth();
      setHealth(h);
    } catch (err) {
      console.error("Failed to fetch health", err);
    }
  };*/

  // -----------------------------------------------------
  // FETCH SYSTEM METRICS SNAPSHOT
  // -----------------------------------------------------
  /*const fetchMetrics = async () => {
    try {
      const depth = await getMetric("springqpro_queue_memory_size");
      const comp = await getMetric("springqpro_tasks_completed_total");
      const fail = await getMetric("springqpro_tasks_failed_total");

      setQueueDepth(depth ?? null);
      setCompleted(comp ?? null);
      setFailed(fail ?? null);
    } catch (err) {
      console.error("Failed to fetch metrics", err);
    }
  };*/

  // -----------------------------------------------------
  // INITIAL LOAD + AUTO-REFRESH
  // -----------------------------------------------------
  /*useEffect(() => {
    const load = async () => {
      setLoading(true);
      await fetchHealth();
      await fetchMetrics();
      setLoading(false);
    };

    load();

    const interval = setInterval(() => {
      load();
    }, 5000);

    return () => clearInterval(interval);
  }, []);*/

  // -----------------------------------------------------
  // STYLE HELPERS (SpringBoot aesthetic)
  // -----------------------------------------------------
  const container: React.CSSProperties = {
    minHeight: "100vh",
    backgroundColor: "#f6f8fa",
    fontFamily: "monospace",
    color: "#2d2d2d",
    paddingBottom: "40px",
  };

  const card: React.CSSProperties = {
    backgroundColor: "#ffffff",
    border: "2px solid #6db33f",
    borderRadius: "10px",
    padding: "22px",
    width: "600px",
    boxShadow: "0 0 10px rgba(109,179,63,0.3)",
    marginBottom: "25px",
  };

  const title: React.CSSProperties = {
    fontSize: "1.3rem",
    marginBottom: "12px",
    color: "#2d2d2d",
  };

  const refreshBtn: React.CSSProperties = {
    padding: "8px 12px",
    backgroundColor: "#6db33f",
    color: "white",
    border: "none",
    borderRadius: "6px",
    cursor: "pointer",
    fontWeight: "bold",
    marginTop: "10px",
    boxShadow: "0 0 5px rgba(109,179,63,0.4)",
  };

  return (
    <div style={container}>
      <NavBar />

      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          paddingTop: "30px",
        }}
      >
        <h1 style={{ fontSize: "1.8rem", marginBottom: "25px" }}>
          System Health Overview
        </h1>

        {/* ------------------------------------------------ */}
        {/* HEALTH SUMMARY CARD */}
        {/* ------------------------------------------------ */}
        <div style={card}>
          <h2 style={title}>Spring Boot /actuator/health</h2>

          {loading || !health ? (
            <div>Loading system health…</div>
          ) : (
            <>
              <div>
                <b>Overall Status:</b>{" "}
                <span
                  style={{
                    color: health.status === "UP" ? "#6db33f" : "red",
                  }}
                >
                  {health.status}
                </span>
              </div>

              <hr style={{ margin: "12px 0", borderColor: "#6db33f" }} />

              {Object.entries(health.components).map(([name, comp]) => (
                <div
                  key={name}
                  style={{
                    marginBottom: "10px",
                    padding: "6px 0",
                    borderBottom: "1px solid #e0e0e0",
                  }}
                >
                  <b>{name}: </b>
                  <span
                    style={{
                      color: comp.status === "UP" ? "#6db33f" : "red",
                    }}
                  >
                    {comp.status}
                  </span>
                </div>
              ))}
            </>
          )}

          <button
            style={refreshBtn}
            onClick={() => {
              setLoading(true);
              /*fetchHealth();
              fetchMetrics();*/
              setLoading(false);
            }}
          >
            Refresh Now
          </button>
        </div>

        {/* ------------------------------------------------ */}
        {/* METRICS SNAPSHOT */}
        {/* ------------------------------------------------ */}
        <div style={card}>
          <h2 style={title}>Metrics Snapshot</h2>

          {loading ? (
            <div>Loading metrics…</div>
          ) : (
            <>
              <div style={{ marginBottom: "8px" }}>
                <b>Queue Depth:</b> {queueDepth ?? "N/A"}
              </div>

              <div style={{ marginBottom: "8px" }}>
                <b>Total Completed Tasks:</b> {completed ?? "N/A"}
              </div>

              <div style={{ marginBottom: "8px" }}>
                <b>Total Failed Tasks:</b> {failed ?? "N/A"}
              </div>

              <div style={{ fontSize: "13px", color: "#555", marginTop: "10px" }}>
                * Metrics updated every 5 seconds.
              </div>
            </>
          )}
        </div>

        {/* ------------------------------------------------ */}
        {/* FOOTNOTE */}
        {/* ------------------------------------------------ */}
        <div style={{ width: "600px", textAlign: "center", marginTop: "10px" }}>
          <p style={{ fontSize: "13px", color: "#555" }}>
            This information is pulled from{" "}
            <code style={{ color: "#6db33f" }}>/actuator/health</code> and{" "}
            <code style={{ color: "#6db33f" }}>/actuator/metrics</code>, offering
            a high-level snapshot of SpringQueuePro’s infrastructure health.
          </p>
        </div>
      </div>
    </div>
  );
}
