import { useEffect, useState } from "react";
import NavBar from "../components/NavBar";
import { useAuth } from "../utility/auth/AuthContext";
import {
  getEnumLists,
  /*getMetric,
  getWorkerStatus,
  getProcessingEvents,
  createTestTask*/
} from "../api/api";

/* ============================= MAIN COMPONENT ============================= */

export default function ProcessingMonitorPage() {
  const { accessToken } = useAuth();

  /* -------------------- Dynamic Enums (avoid drift) -------------------- */
  const [taskTypes, setTaskTypes] = useState<string[]>([]);
  const [enumsLoaded, setEnumsLoaded] = useState(false);

  /* -------------------- Metrics State -------------------- */
  const [queueDepth, setQueueDepth] = useState<number | null>(null);
  const [tasksCompleted, setTasksCompleted] = useState<number | null>(null);
  const [tasksFailed, setTasksFailed] = useState<number | null>(null);
  const [maxProcessingDuration, setMaxProcessingDuration] = useState<number | null>(null);

  /* -------------------- Worker Pool -------------------- */
  const [workers, setWorkers] = useState<{
    active: number;
    idle: number;
    inFlight: number;
  } | null>(null);

  /* -------------------- Event Log -------------------- */
  const [events, setEvents] = useState<string[]>([]);
  const [loadingEvents, setLoadingEvents] = useState(true);

  /* -------------------- Load GraphQL Enum Lists -------------------- */
  useEffect(() => {
    const load = async () => {
      if (!accessToken) return;
      try {
        const enums = await getEnumLists(accessToken);
        setTaskTypes(enums.taskTypes);
        setEnumsLoaded(true);
      } catch {
        setEnumsLoaded(false);
      }
    };
    load();
  }, [accessToken]);

  /* -------------------- Fetch Metrics -------------------- */
  const fetchMetrics = async () => {
    try {
      /*setQueueDepth(await getMetric("springqpro_queue_memory_size"));
      setTasksCompleted(await getMetric("springqpro_tasks_completed_total"));
      setTasksFailed(await getMetric("springqpro_tasks_failed_total"));
      setMaxProcessingDuration(
        await getMetric("springqpro_task_processing_duration_seconds_max")
      );*/
    } catch (err) {
      console.error("Error fetching metrics:", err);
    }
  };

  /* -------------------- Fetch Worker Status -------------------- */
  const fetchWorkers = async () => {
    try {
      //const w = await getWorkerStatus();
      //setWorkers(w);
    } catch (err) {
      console.error("Error fetching worker status", err);
    }
  };

  /* -------------------- Fetch Event Log -------------------- */
  const fetchEvents = async () => {
    setLoadingEvents(true);
    try {
      //const list = await getProcessingEvents();
      //setEvents(list);
    } catch (err) {
      console.error("Error fetching processing events", err);
    }
    setLoadingEvents(false);
  };

  /* -------------------- Poll everything every 3 seconds -------------------- */
  useEffect(() => {
    fetchMetrics();
    fetchWorkers();
    fetchEvents();

    const interval = setInterval(() => {
      fetchMetrics();
      fetchWorkers();
      fetchEvents();
    }, 3000);

    return () => clearInterval(interval);
  }, []);

  /* -------------------- Spawn Test Tasks -------------------- */
  const spawnFailTask = async () => {
    //await createTestTask("FAIL");
  };

  const spawnLongTask = async () => {
    //await createTestTask("TAKESLONG");
  };

  const spawnBurst = async () => {
    for (let i = 0; i < 20; i++) {
      //await createTestTask("QUICK");
    }
  };

  /* -------------------- Styles (SpringQueuePro aesthetic) -------------------- */

  const container: React.CSSProperties = {
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

  /* ============================= RENDER ============================= */

  return (
    <div style={container}>
      <NavBar />

      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          paddingTop: "30px"
        }}
      >
        <h1 style={{ marginBottom: "25px", fontSize: "1.8rem" }}>
          Processing Overview
        </h1>

        {/* -------------------- WORKER STATUS -------------------- */}
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

        {/* -------------------- METRICS -------------------- */}
        <div style={card}>
          <h2 style={title}>Processing Metrics</h2>

          <div><b>Queue Depth:</b> {queueDepth ?? "—"}</div>
          <div><b>Total Completed:</b> {tasksCompleted ?? "—"}</div>
          <div><b>Total Failed:</b> {tasksFailed ?? "—"}</div>
          <div>
            <b>Max Processing Duration:</b>{" "}
            {maxProcessingDuration ? `${maxProcessingDuration.toFixed(2)}s` : "—"}
          </div>
        </div>

        {/* -------------------- EVENT LOG -------------------- */}
        <div style={card}>
          <h2 style={title}>Live Event Log</h2>

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
              ? "Loading event log…"
              : events.length > 0
              ? events.map((e, i) => (
                  <div key={i} style={{ marginBottom: "4px" }}>{e}</div>
                ))
              : "No events yet."}
          </div>
        </div>

        {/* -------------------- TEST TASK GENERATOR -------------------- */}
        <div style={card}>
          <h2 style={title}>Test Task Generator</h2>

          {/* Show configured taskTypes for debugging */}
          {enumsLoaded && (
            <p style={{ fontSize: "13px", color: "#6db33f" }}>
              Available types (from schema): {taskTypes.join(", ")}
            </p>
          )}

          <button style={button} onClick={spawnFailTask}>
            Spawn FAIL Task
          </button>

          <button style={button} onClick={spawnLongTask}>
            Spawn Long Task
          </button>

          <button style={button} onClick={spawnBurst}>
            Spawn 20 Quick Tasks
          </button>

          <p style={{ fontSize: "13px", marginTop: "10px", color: "#444" }}>
            These spawn controlled workloads to visualize retries, worker activity,
            and ProcessingService behavior.
          </p>
        </div>
      </div>
    </div>
  );
}
