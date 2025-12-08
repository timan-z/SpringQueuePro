import { useEffect, useState } from "react";
import NavBar from "../components/NavBar";
import { useAuth } from "../utility/auth/AuthContext";
import {
  getMetric,
  getProcessingEvents,
  getWorkerStatus
} from "../api/api";

export default function ProcessingMonitorPage() {
  const { accessToken } = useAuth();

  const [workers, setWorkers] = useState<{ active: number; idle: number; inFlight: number } | null>(null);
  const [queueDepth, setQueueDepth] = useState<number | null>(null);
  const [completed, setCompleted] = useState<number | null>(null);
  const [failed, setFailed] = useState<number | null>(null);

  const [events, setEvents] = useState<string[]>([]);
  const [loadingEvents, setLoadingEvents] = useState(true);

  const containerStyle: React.CSSProperties = {
    minHeight: "100vh",
    backgroundColor: "#f6f8fa",
    fontFamily: "monospace",
    paddingBottom: "50px"
  };

  const card: React.CSSProperties = {
    backgroundColor: "#ffffff",
    border: "2px solid #6db33f",
    borderRadius: "10px",
    padding: "22px",
    width: "600px",
    boxShadow: "0 0 10px rgba(109,179,63,0.3)",
    marginBottom: "25px"
  };

  const title: React.CSSProperties = {
    fontSize: "1.3rem",
    marginBottom: "12px",
    color: "#2d2d2d"
  };

  // ---- Fetch Worker + Metrics ----
  const refreshAll = async () => {
    if (!accessToken) return;

    const status = await getWorkerStatus(accessToken);
    const qd = await getMetric("springqpro_queue_memory_size", accessToken);
    const comp = await getMetric("springqpro_tasks_completed_total", accessToken);
    const fail = await getMetric("springqpro_tasks_failed_total", accessToken);

    setWorkers(status);
    setQueueDepth(qd ?? null);
    setCompleted(comp ?? null);
    setFailed(fail ?? null);

    const ev = await getProcessingEvents(accessToken);
    setEvents(ev);
    setLoadingEvents(false);
  };

  useEffect(() => {
    refreshAll();
    const interval = setInterval(refreshAll, 3000);
    return () => clearInterval(interval);
  }, [accessToken]);

  return (
    <div style={containerStyle}>
      <NavBar />

      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", paddingTop: "30px" }}>
        <h1 style={{ marginBottom: "25px", fontSize: "1.8rem" }}>Processing Overview</h1>

        {/* Worker Status */}
        <div style={card}>
          <h2 style={title}>Worker ThreadPool</h2>
          {workers ? (
            <>
              <div><b>Active Workers:</b> {workers.active}</div>
              <div><b>Idle Workers:</b> {workers.idle}</div>
              <div><b>Tasks In Flight:</b> {workers.inFlight}</div>
            </>
          ) : (
            <div>Loading...</div>
          )}
        </div>

        {/* Metrics */}
        <div style={card}>
          <h2 style={title}>Queue Metrics</h2>
          <div><b>Queue Depth:</b> {queueDepth ?? "—"}</div>
          <div><b>Total Completed:</b> {completed ?? "—"}</div>
          <div><b>Total Failed:</b> {failed ?? "—"}</div>
        </div>

        {/* Log */}
        <div style={card}>
          <h2 style={title}>Processing Events</h2>

          <div
            style={{
              backgroundColor: "#f0f6ef",
              border: "1px solid #6db33f",
              padding: "10px",
              height: "200px",
              overflowY: "auto",
              fontSize: "14px"
            }}
          >
            {loadingEvents
              ? "Loading..."
              : events.length > 0
                ? events.map((e, i) => <div key={i}>{e}</div>)
                : "No recent events."}
          </div>
        </div>
      </div>
    </div>
  );
}
