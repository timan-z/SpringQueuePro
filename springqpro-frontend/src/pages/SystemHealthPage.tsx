import { useEffect, useState } from "react";
import NavBar from "../components/NavBar";
import { useAuth } from "../utility/auth/AuthContext";

interface HealthComponent {
  status: string;
  details?: any;
}

interface SystemMetrics {
  submitted: number | null;
  processed: number | null;
  failed: number | null;
  retried: number | null;
  queueSize: number | null;
}

interface HealthResponse {
  status: string;
  components: Record<string, HealthComponent>;
}

export default function SystemHealthPage() {
  const { accessToken } = useAuth();

  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [metrics, setMetrics] = useState<any>({});
  const [autoRefresh, setAutoRefresh] = useState(true);

  const fetchHealth = async () => {
    const res = await fetch("/actuator/health");
    const json = await res.json();
    setHealth(json);
  };

  const fetchMetric = async (key: string) => {
    const res = await fetch(`/actuator/metrics/${key}`);
    const json = await res.json();
    return json.measurements?.[0]?.value ?? null;
  };

  const metricKeys: Record<keyof SystemMetrics, string> = {
    submitted: "springqpro_tasks_submitted_total",
    processed: "springqpro_tasks_completed_total",
    failed: "springqpro_tasks_failed_total",
    retried: "springqpro_tasks_retried_total",
    queueSize: "springqpro_queue_memory_size"
  };

  const fetchMetrics = async () => {
    const result: Partial<SystemMetrics> = {};

    for (const [k, key] of Object.entries(metricKeys)) {
        result[k as keyof SystemMetrics] = await fetchMetric(key);
    }

    setMetrics(result as SystemMetrics);
  };

  useEffect(() => {
    fetchHealth();
    fetchMetrics();

    if (!autoRefresh) return;

    const id = setInterval(() => {
      fetchHealth();
      fetchMetrics();
    }, 5000);

    return () => clearInterval(id);
  }, [autoRefresh]);

  const colorFor = (status: string) => {
    switch (status.toUpperCase()) {
      case "UP":
        return "#009933";
      case "DOWN":
        return "#cc0000";
      default:
        return "#ff9900"; // unknown, degraded
    }
  };

  return (
    <div>
      <NavBar />

      <div style={{ padding: 30, fontFamily: "monospace" }}>
        <h1>🩺 System Health Dashboard</h1>

        {/* Auto-refresh toggle */}
        <label style={{ marginBottom: 25, display: "inline-block" }}>
          <input
            type="checkbox"
            checked={autoRefresh}
            onChange={() => setAutoRefresh(!autoRefresh)}
            style={{ marginRight: 8 }}
          />
          Auto-refresh every 5 seconds
        </label>

        {/* Overall Health */}
        {health && (
          <div
            style={{
              border: "3px solid black",
              borderRadius: 10,
              padding: 20,
              marginBottom: 30,
              backgroundColor: "white"
            }}
          >
            <h2>Overall Status</h2>
            <div
              style={{
                fontSize: 28,
                fontWeight: "bold",
                color: colorFor(health.status)
              }}
            >
              {health.status}
            </div>
          </div>
        )}

        {/* Component Health Grid */}
        {health && (
          <>
            <h2>Component Status</h2>
            <div
              style={{
                display: "flex",
                flexWrap: "wrap",
                gap: 20,
                marginBottom: 40
              }}
            >
              {Object.entries(health.components).map(([name, comp]) => (
                <div
                  key={name}
                  style={{
                    padding: 15,
                    minWidth: 200,
                    border: "2px solid black",
                    borderRadius: 10,
                    backgroundColor: "white"
                  }}
                >
                  <div style={{ fontSize: 16, marginBottom: 5 }}>
                    {name.toUpperCase()}
                  </div>
                  <div
                    style={{
                      fontWeight: "bold",
                      color: colorFor(comp.status),
                      fontSize: 20
                    }}
                  >
                    {comp.status}
                  </div>
                </div>
              ))}
            </div>
          </>
        )}

        {/* Metrics Section */}
        <h2>Key System Metrics</h2>

        <div
          style={{
            display: "flex",
            gap: 20,
            flexWrap: "wrap",
            marginTop: 20
          }}
        >
          <MetricBox label="Tasks Submitted" value={metrics.submitted} />
          <MetricBox label="Tasks Processed" value={metrics.processed} />
          <MetricBox label="Tasks Failed" value={metrics.failed} />
          <MetricBox label="Retries" value={metrics.retried} />
          <MetricBox label="Queue Size (Memory)" value={metrics.queueSize} />
        </div>
      </div>
    </div>
  );
}

function MetricBox({ label, value }: { label: string; value: number }) {
  return (
    <div
      style={{
        border: "2px solid black",
        padding: "12px 18px",
        borderRadius: 10,
        backgroundColor: "white",
        minWidth: 160,
        textAlign: "center"
      }}
    >
      <div style={{ fontSize: 13, opacity: 0.7 }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: "bold", marginTop: 4 }}>
        {value ?? 0}
      </div>
    </div>
  );
}
