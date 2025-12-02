import { useEffect, useState } from "react";
import { useAuth } from "../utility/auth/AuthContext";
import NavBar from "../components/NavBar";

interface ProcessingEvent {
  timestamp: string;
  taskId: string;
  eventType: string;
  details: string;
}

interface ProcessingMetrics {
  queueSize: number;
  processed: number;
  failed: number;
  retried: number;
}

interface MetricValue {
  value: number;
}

export default function ProcessingMonitorPage() {
  const { accessToken } = useAuth();

  const [events, setEvents] = useState<ProcessingEvent[]>([]);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [loading, setLoading] = useState(false);
  const [metrics, setMetrics] = useState({
    queueSize: 0,
    processed: 0,
    failed: 0,
    retried: 0
  });

  // Fetch Processing Events
  const fetchEvents = async () => {
    setLoading(true);

    const res = await fetch("/api/processing/events/recent", {
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    const json = await res.json();
    setEvents(json);
    setLoading(false);
  };

  // Fetch minimal actuator metrics
  const fetchMetrics = async () => {
    const metricKeys: Record<keyof ProcessingMetrics, string> = {
        processed: "springqpro_tasks_completed_total",
        failed: "springqpro_tasks_failed_total",
        retried: "springqpro_tasks_retried_total",
        queueSize: "springqpro_queue_memory_size"
    };

    const m: Partial<ProcessingMetrics> = {};

    for (const [key, metric] of Object.entries(metricKeys)) {
        const res = await fetch(`/actuator/metrics/${metric}`);
        const json = await res.json();

        m[key as keyof ProcessingMetrics] = json.measurements?.[0]?.value ?? 0;
    }
    setMetrics(m as ProcessingMetrics);
  };

  // Auto-refresh cycle
  useEffect(() => {
    fetchEvents();
    fetchMetrics();

    if (!autoRefresh) return;

    const id = setInterval(() => {
      fetchEvents();
      fetchMetrics();
    }, 5000);

    return () => clearInterval(id);
  }, [autoRefresh]);

  const colorFor = (e: string) => {
    switch (e) {
      case "CLAIMED": return "#0066ff";
      case "COMPLETED": return "#009933";
      case "FAILED": return "#cc0000";
      case "RETRY_SCHEDULED": return "#ff9900";
      case "LOCK_ACQUIRED": return "#9933ff";
      case "LOCK_FAILED": return "#666666";
      default: return "black";
    }
  };

  return (
    <div>
      <NavBar />

      <div style={{ padding: 30, fontFamily: "monospace" }}>
        <h1>Processing Monitor</h1>

        {/* Mini Metrics Row */}
        <div
          style={{
            display: "flex",
            gap: 20,
            marginBottom: 25,
            flexWrap: "wrap"
          }}
        >
          <MetricBox label="Queue Size" value={metrics.queueSize} />
          <MetricBox label="Processed" value={metrics.processed} />
          <MetricBox label="Failed" value={metrics.failed} />
          <MetricBox label="Retried" value={metrics.retried} />
        </div>

        {/* Auto-refresh toggle */}
        <label style={{ marginBottom: 20, display: "inline-block" }}>
          <input
            type="checkbox"
            checked={autoRefresh}
            onChange={() => setAutoRefresh(!autoRefresh)}
            style={{ marginRight: 8 }}
          />
          Auto-refresh (5s)
        </label>

        <h2 style={{ marginTop: 10 }}>Event Log</h2>

        {loading && <div>Loading…</div>}

        <div style={{ marginTop: 20 }}>
          {events.map((ev, idx) => (
            <div
              key={idx}
              style={{
                padding: 14,
                border: "1px solid #ddd",
                marginBottom: 12,
                borderRadius: 8,
                backgroundColor: "#fafafa"
              }}
            >
              <div style={{ fontSize: 14, color: "#666" }}>
                {new Date(ev.timestamp).toLocaleString()}
              </div>

              <div style={{ display: "flex", gap: 10, marginTop: 5 }}>
                <span style={{ fontWeight: "bold" }}>Task:</span>
                <span>{ev.taskId}</span>
              </div>

              <div style={{ marginTop: 5 }}>
                <span
                  style={{
                    padding: "3px 8px",
                    borderRadius: 6,
                    color: "white",
                    backgroundColor: colorFor(ev.eventType),
                    fontSize: 12,
                    fontWeight: "bold"
                  }}
                >
                  {ev.eventType}
                </span>
              </div>

              <div style={{ marginTop: 8, whiteSpace: "pre-wrap" }}>
                {ev.details}
              </div>
            </div>
          ))}
        </div>

      </div>
    </div>
  );
}

// Mini Box Component
function MetricBox({ label, value }: { label: string; value: number }) {
  return (
    <div
      style={{
        border: "2px solid black",
        padding: "10px 20px",
        borderRadius: 10,
        minWidth: 140,
        textAlign: "center",
        background: "white"
      }}
    >
      <div style={{ fontSize: 13, opacity: 0.7 }}>{label}</div>
      <div style={{ fontSize: 24, fontWeight: "bold" }}>{value}</div>
    </div>
  );
}
