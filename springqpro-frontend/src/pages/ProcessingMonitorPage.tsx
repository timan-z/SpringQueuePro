// Formerly ProcessingMonitorPage.tsx
import { useEffect, useState } from "react";
import NavBar from "../components/NavBar";
import { useAuth } from "../utility/auth/AuthContext";
/*
import {
  getMetric,
  getProcessingEvents,
  createTestTask,
  getWorkerStatus
} from "../api/api";
*/

export default function ProcessingMonitorPage() {
  const { accessToken } = useAuth();

  // -------------------------------
  //   STATE
  // -------------------------------
  const [queueDepth, setQueueDepth] = useState<number | null>(null);
  const [tasksCompleted, setTasksCompleted] = useState<number | null>(null);
  const [tasksFailed, setTasksFailed] = useState<number | null>(null);
  const [processingDuration, setProcessingDuration] = useState<number | null>(
    null
  );

  const [events, setEvents] = useState<string[]>([]);
  const [workers, setWorkers] = useState<{
    active: number;
    idle: number;
    inFlight: number;
  } | null>(null);

  // Loading flags
  const [loadingMetrics, setLoadingMetrics] = useState(true);
  const [loadingEvents, setLoadingEvents] = useState(true);

  // -------------------------------
  //   FETCH METRICS (PROMETHEUS)
  // -------------------------------
  /*const fetchMetrics = async () => {
    setLoadingMetrics(true);

    try {
      const depth = await getMetric("springqpro_queue_memory_size");
      const completed = await getMetric("springqpro_tasks_completed_total");
      const failed = await getMetric("springqpro_tasks_failed_total");
      const duration = await getMetric("springqpro_task_processing_duration_seconds_max");

      setQueueDepth(depth ?? null);
      setTasksCompleted(completed ?? null);
      setTasksFailed(failed ?? null);
      setProcessingDuration(duration ?? null);
    } catch (err) {
      console.error("Error fetching metrics", err);
    }

    setLoadingMetrics(false);
  };*/

  // -------------------------------
  //   FETCH WORKER STATUS
  // -------------------------------
  /*const fetchWorkerStatus = async () => {
    try {
      const info = await getWorkerStatus();
      setWorkers(info);
    } catch (err) {
      console.error("Error fetching worker status", err);
    }
  };*/

  // -------------------------------
  //   FETCH EVENT LOG
  // -------------------------------
  /*const fetchEventLog = async () =>:
    try {
      const list = await getProcessingEvents();
      setEvents(list);
    } catch (err) {
      console.error("Error fetching events", err);
    } finally {
      setLoadingEvents(false);
    }
  };*/

  // -------------------------------
  //   LOAD ON MOUNT
  // -------------------------------
  /*useEffect(() => {
    fetchMetrics();
    fetchWorkerStatus();
    fetchEventLog();

    const interval = setInterval(() => {
      fetchMetrics();
      fetchEventLog();
      fetchWorkerStatus();
    }, 3000);

    return () => clearInterval(interval);
  }, []);*/

  // -------------------------------
  //   TEST TASK SPAWNERS
  // -------------------------------
  /*const spawnFailTask = async () => {
    await createTestTask("FAIL");
  };

  const spawnLongTask = async () => {
    await createTestTask("TAKESLONG");
  };

  const spawnBurst = async () => {
    for (let i = 0; i < 20; i++) await createTestTask("QUICK");
  };*/

  // -------------------------------
  //   STYLES (Spring Boot aesthetic)
  // -------------------------------
  const containerStyle: React.CSSProperties = {
    minHeight: "100vh",
    backgroundColor: "#f6f8fa",
    fontFamily: "monospace",
    color: "#2d2d2d",
    paddingBottom: "40px"
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

  const button: React.CSSProperties = {
    padding: "8px 14px",
    backgroundColor: "#6db33f",
    color: "white",
    border: "none",
    borderRadius: "6px",
    cursor: "pointer",
    fontWeight: "bold",
    marginRight: "10px",
    boxShadow: "0 0 5px rgba(109,179,63,0.4)"
  };

  return (
    <div style={containerStyle}>
      <NavBar />

      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", paddingTop: "30px" }}>
        <h1 style={{ marginBottom: "25px", fontSize: "1.8rem" }}>Processing Overview</h1>

        {/* --------------------------------------------------- */}
        {/* WORKER POOL STATUS */}
        {/* --------------------------------------------------- */}
        <div style={card}>
          <h2 style={title}>Worker ThreadPool Status</h2>

          {workers ? (
            <>
              <div><b>Active Workers:</b> {workers.active}</div>
              <div><b>Idle Workers:</b> {workers.idle}</div>
              <div><b>Tasks In-Flight:</b> {workers.inFlight}</div>
            </>
          ) : (
            <div>Loading...</div>
          )}
        </div>

        {/* --------------------------------------------------- */}
        {/* METRICS CARD */}
        {/* --------------------------------------------------- */}
        <div style={card}>
          <h2 style={title}>Processing Metrics</h2>

          {loadingMetrics ? (
            <div>Loading metrics…</div>
          ) : (
            <>
              {/*<div><b>Queue Depth:</b> {queueDepth ?? "N/A"}</div>
              <div><b>Total Completed:</b> {tasksCompleted ?? "N/A"}</div>
              <div><b>Total Failed:</b> {tasksFailed ?? "N/A"}</div>
              <div><b>Max Processing Duration:</b> {processingDuration ? `${processingDuration.toFixed(2)}s` : "N/A"}</div>*/}
              <div><b>Queue Depth:</b></div>
              <div><b>Total Completed:</b></div>
              <div><b>Total Failed:</b></div>
              <div><b>Max Processing Duration:</b></div>
            </>
          )}
        </div>

        {/* --------------------------------------------------- */}
        {/* EVENT LOG */}
        {/* --------------------------------------------------- */}
        <div style={card}>
          <h2 style={title}>Live Event Log</h2>

          <div style={{
            backgroundColor: "#f0f6ef",
            border: "1px solid #6db33f",
            padding: "10px",
            height: "200px",
            overflowY: "auto",
            fontSize: "14px"
          }}>
            {loadingEvents
              ? "Loading event log…"
              : events.length > 0
                ? events.map((e, i) => (
                  <div key={i} style={{ marginBottom: "4px" }}>
                    {e}
                  </div>
                ))
                : "No events yet."
            }
          </div>
        </div>

        {/* --------------------------------------------------- */}
        {/* TEST TASK GENERATOR */}
        {/* --------------------------------------------------- */}
        <div style={card}>
          <h2 style={title}>Test Task Generator</h2>

          {/*<button style={button} onClick={spawnFailTask}>*/}
          <button style={button}>
            Spawn FAIL Task
          </button>
          <button style={button}>
          {/*<button style={button} onClick={spawnLongTask}>*/}
            Spawn Long Task
          </button>
          {/*<button style={button} onClick={spawnBurst}>*/}
          <button style={button}>
            Spawn 20 Quick Tasks
          </button>

          <p style={{ fontSize: "13px", marginTop: "10px", color: "#444" }}>
            Use these buttons to observe retries, queue depth changes, and worker pool behavior.
          </p>
        </div>
      </div>
    </div>
  );
}
