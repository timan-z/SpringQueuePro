import { useRef, useEffect, useState } from "react";
import NavBar from "../components/NavBar";
import { API_BASE, getEnumLists } from "../api/api";
import { useAuth } from "../utility/auth/AuthContext";

/* NOTE:+TO-DO:
- I WANT TO ADD A "CLEAR" BUTTON FOR THE MOST RECENT TASKS BUT THE PROBLEM IS THAT EVERYTIME
IT RE-LOADS THE WHOLE THING COMES BACK ANYWAYS!!!
*/

/* -------------------- Types (auto-shaped dynamically) -------------------- */

type TaskType = string;
type TaskStatus = string;

interface Task {
  id: string;
  payload: string;
  type: TaskType;
  status: TaskStatus;
  attempts: number;
  maxRetries: number;
  createdAt: string;
}

interface MetricResponse {
  name: string;
  measurements: { statistic: string; value: number }[];
  availableTags: any[];
}

/* ============================= MAIN COMPONENT ============================= */

export default function TasksPage() {
  const { accessToken } = useAuth();

  /* -------------------- Dynamic Enum Lists -------------------- */
  const [taskTypes, setTaskTypes] = useState<TaskType[]>([]);
  const [taskStatuses, setTaskStatuses] = useState<TaskStatus[]>([]);
  const [enumsLoaded, setEnumsLoaded] = useState(false);

  /* -------------------- Create Form State -------------------- */
  const [payload, setPayload] = useState("");
  const [taskType, setTaskType] = useState<TaskType>("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  /* -------------------- GraphQL Output State -------------------- */
  const [lastMutationPreview, setLastMutationPreview] = useState("");
  const [lastResponseJson, setLastResponseJson] = useState("");

  /* -------------------- Recent Tasks -------------------- */
  const [recentTasks, setRecentTasks] = useState<Task[]>([]);
  const [loadingTasks, setLoadingTasks] = useState(false);

  // 2025-12-05-NOTE: New state to keep track of Tasks in the "Recent Tasks" tab that the current user actually made:
  const [myOwnedTaskIds, setMyOwnedTaskIds] = useState<Set<string>>(new Set());

  /* -------------------- Metrics -------------------- */
  const [submittedCount, setSubmittedCount] = useState<number | null>(null);
  const [completedCount, setCompletedCount] = useState<number | null>(null);
  const [failedCount, setFailedCount] = useState<number | null>(null);
  const [queueSize, setQueueSize] = useState<number | null>(null);

  const lastCreatedIdRef = useRef<string | null>(null);

  /* -------------------- Load Enum Lists (taskEnums) -------------------- */

  const loadEnumLists = async () => {
    if (!accessToken) return;

    try {
      const enums = await getEnumLists(accessToken);
      setTaskTypes(enums.taskTypes);
      setTaskStatuses(enums.taskStatuses);

      // preselect first available type
      if (enums.taskTypes.length > 0) {
        setTaskType(enums.taskTypes[0]);
      }

      setEnumsLoaded(true);
    } catch (err) {
      console.error("Failed to load GraphQL enum lists:", err);
      setEnumsLoaded(false);
    }
  };

  useEffect(() => {
    loadEnumLists();
  }, [accessToken]);

  /* -------------------- GraphQL Caller -------------------- */

  const callGraphQL = async (query: string, variables?: any) => {
    if (!accessToken) throw new Error("Missing access token");

    const res = await fetch(`${API_BASE}/graphql`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({ query, variables }),
    });

    if (!res.ok) {
      const text = await res.text();
      throw new Error(`GraphQL error: ${res.status} - ${text}`);
    }

    return res.json();
  };

  /* -------------------- Create Task -------------------- */

  const handleCreateTask = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!payload.trim()) {
      setCreateError("Please enter a payload.");
      return;
    }

    setCreating(true);
    setCreateError(null);

    const mutation = `
      mutation CreateTask($input: CreateTaskInput!) {
        createTask(input: $input) {
          id
          payload
          type
          status
          attempts
          maxRetries
          createdAt
        }
      }
    `;

    const variables = {
      input: {
        payload,
        type: taskType,
      },
    };

    // Pretty preview block
    const preview = `
    mutation {
      createTask(input: {
        payload: "${payload}",
        type: ${taskType}
      }) {
        id
        payload
        type
        status
        attempts
        maxRetries
        createdAt
      }
    }
    `.trim();

    setLastMutationPreview(preview);

    try {
      const json = await callGraphQL(mutation, variables);
      const created = json.data.createTask;

      lastCreatedIdRef.current = created.id;  // save id.

      setLastResponseJson(JSON.stringify(json, null, 2));
      setPayload("");

      // Immediately refresh recent tasks
      fetchRecentTasks();
    } catch (err: any) {
      setLastResponseJson(`Error: ${err.message ?? String(err)}`);
    } finally {
      setCreating(false);
    }
  };

  // Function to requery the backend and get "last response" for the GraphQL section:
  const refreshLastResponse = async () => {
    const id = lastCreatedIdRef.current;
    if (!id) {
      setLastResponseJson("// No task created yet to refresh");
      return;
    }

    const query = `
      query {
        task(id: "${id}") {
          id
          payload
          type
          status
          attempts
          maxRetries
          createdAt
        }
      }
    `;

    try {
      const json = await callGraphQL(query);
      setLastResponseJson(JSON.stringify(json, null, 2));
    } catch (err: any) {
      setLastResponseJson(`Error refreshing: ${err.message}`);
    }
  };

  /* -------------------- Recent Tasks Fetch -------------------- */

  const fetchRecentTasks = async () => {
    if (!accessToken) return;

    setLoadingTasks(true);

    const query = `
      query {
        tasks {
          id
          payload
          type
          status
          attempts
          maxRetries
          createdAt
        }
      }
    `;

    try {
      const json = await callGraphQL(query);
      const tasks: Task[] = json.data.tasks ?? [];

      const sorted = [...tasks].sort(
        (a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );

      setRecentTasks(sorted.slice(0, 8));
    } catch (err) {
      console.error("Failed to fetch recent tasks:", err);
    } finally {
      setLoadingTasks(false);
    }
  };

  useEffect(() => {
    fetchRecentTasks();
    const interval = setInterval(fetchRecentTasks, 3000);
    return () => clearInterval(interval);
  }, [accessToken]);

  /* -------------------- Retry Button -------------------- */

  const handleRetryTask = async (task: Task) => {

    console.log("DEBUG:[handleRetryTask] - ENTERED INSIDE OF METHOD!");

    const mutation = `
      mutation($id: ID!) {
        retryTask(id: $id)
      }
    `;
    const variables = { id: task.id };

    try {

      console.log("DEBUG:[handleRetryTask] - About to call callGraphQL(mutation, variables);");

      await callGraphQL(mutation, variables);
      // optimistic update (instant UI feedback)
      setRecentTasks(prev =>
        prev.map(t =>
          t.id === task.id ? { ...t, status: "QUEUED" } : t
        )
      );
      fetchRecentTasks();
    } catch (err) {
      console.error("Failed to retry task:", err);
    }
  };

  /* -------------------- Metrics -------------------- */

  const fetchMetric = async (key: string): Promise<number | null> => {
    try {
      const res = await fetch(`${API_BASE}/actuator/metrics/${key}`, {
        headers: { Authorization: accessToken ? `Bearer ${accessToken}` : "" },
      });
      if (!res.ok) return null;
      const json: MetricResponse = await res.json();
      return json.measurements?.[0]?.value ?? null;
    } catch {
      return null;
    }
  };

  const fetchMetrics = async () => {
    const [submitted, completed, failed, queue] = await Promise.all([
      fetchMetric("springqpro_tasks_submitted_total"),
      fetchMetric("springqpro_tasks_completed_total"),
      fetchMetric("springqpro_tasks_failed_total"),
      fetchMetric("springqpro_queue_memory_size"),
    ]);

    setSubmittedCount(submitted);
    setCompletedCount(completed);
    setFailedCount(failed);
    setQueueSize(queue);
  };

  useEffect(() => {
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 5000);
    return () => clearInterval(interval);
  }, [accessToken]);

  /* -------------------- Status Color Helper -------------------- */

  const statusColor = (status: TaskStatus): string => {
    switch (status) {
      case "QUEUED":
        return "#d9a441";
      case "INPROGRESS":
        return "#2f8faf";
      case "COMPLETED":
        return "#2f9e44";
      case "FAILED":
        return "#d64545";
      default:
        return "#2d2d2d";
    }
  };

  /* ============================= RENDER ============================= */

  return (
    <div
      style={{
        minHeight: "100vh",
        backgroundColor: "#f6f8fa",
        fontFamily: "monospace",
      }}
    >
      <NavBar />

      <div
        style={{
          padding: "30px",
          display: "flex",
          justifyContent: "center",
        }}
      >
        <div
          style={{
            maxWidth: "1100px",
            width: "100%",
            display: "grid",
            gridTemplateColumns: "1.2fr 1fr",
            gap: "24px",
          }}
        >

          {/* -------------------- LEFT COLUMN -------------------- */}
          <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>

            {/* CREATE TASK */}
            <div
              style={{
                backgroundColor: "#ffffff",
                border: "2px solid #6db33f",
                borderRadius: "10px",
                boxShadow: "0 0 10px rgba(109,179,63,0.25)",
                padding: "20px",
              }}
            >
              <h2 style={{ marginTop: 0, color: "#6db33f" }}>Create Task</h2>
              <p style={{ color: "#555", fontSize: "14px" }}>
                Enqueue a new task using GraphQL.
              </p>

              {!enumsLoaded && (
                <div style={{ color: "#777", marginBottom: "8px" }}>
                  Loading allowed task types...
                </div>
              )}

              <form onSubmit={handleCreateTask}>
                {/* Payload */}
                <div style={{ marginBottom: "10px" }}>
                  <label style={{ display: "block", marginBottom: "4px" }}>
                    Payload
                  </label>
                  <input
                    value={payload}
                    onChange={(e) => setPayload(e.target.value)}
                    placeholder="e.g. Send newsletter..."
                    disabled={!enumsLoaded}
                    style={{
                      width: "100%",
                      padding: "8px",
                      border: "1px solid #6db33f",
                      borderRadius: "4px",
                    }}
                  />
                </div>

                {/* Task Type */}
                <div style={{ marginBottom: "15px" }}>
                  <label style={{ display: "block", marginBottom: "4px" }}>
                    Task Type
                  </label>
                  <select
                    value={taskType}
                    onChange={(e) => setTaskType(e.target.value)}
                    disabled={!enumsLoaded}
                    style={{
                      width: "100%",
                      padding: "8px",
                      border: "1px solid #6db33f",
                      borderRadius: "4px",
                      backgroundColor: "#ffffff",
                    }}
                  >
                    {taskTypes.map((tt) => (
                      <option key={tt} value={tt}>
                        {tt}
                      </option>
                    ))}
                  </select>
                </div>

                <button
                  type="submit"
                  disabled={creating || !enumsLoaded}
                  style={{
                    width: "100%",
                    padding: "10px",
                    backgroundColor:
                      creating || !enumsLoaded ? "#85b577" : "#6db33f",
                    border: "none",
                    borderRadius: "6px",
                    color: "#ffffff",
                    fontWeight: "bold",
                    cursor: creating || !enumsLoaded ? "default" : "pointer",
                  }}
                >
                  {creating ? "Creating..." : "Create Task"}
                </button>
              </form>
            </div>

            {/* GraphQL Preview Panel */}
            <div
              style={{
                backgroundColor: "#ffffff",
                border: "2px solid #6db33f",
                borderRadius: "10px",
                boxShadow: "0 0 10px rgba(109,179,63,0.25)",
                padding: "16px",
              }}
            >
              <h3 style={{ marginTop: 0, color: "#6db33f" }}>GraphQL Mutation</h3>

              <pre
                style={{
                  backgroundColor: "#f0f4f0",
                  borderRadius: "6px",
                  padding: "10px",
                  fontSize: "12px",
                  overflowX: "auto",
                }}
              >
{lastMutationPreview || "// Fill the form and click Create Task to see your GraphQL mutation here"}
              </pre>

              <div style={{ 
                display: "flex", 
                justifyContent: "space-between", 
                alignItems: "center",
                marginTop: "14px"
              }}>
                <h3 style={{ margin: 0, color: "#6db33f" }}>Last Response</h3>

                <button
                  onClick={refreshLastResponse}
                  style={{
                    padding: "4px 8px",
                    fontSize: "11px",
                    borderRadius: "4px",
                    border: "1px solid #6db33f",
                    background: "#f0f4f0",
                    cursor: "pointer"
                  }}
                >
                  Refresh
                </button>
              </div>

              <pre
                style={{
                  backgroundColor: "#f0f4f0",
                  borderRadius: "6px",
                  padding: "10px",
                  fontSize: "12px",
                  overflowX: "auto",
                }}
              >
{lastResponseJson || "// Response from SpringQueuePro will appear here"}
              </pre>
            </div>
          </div>

          {/* -------------------- RIGHT COLUMN -------------------- */}

          <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>

            {/* Recent Tasks */}
            <div
              style={{
                backgroundColor: "#ffffff",
                border: "2px solid #6db33f",
                borderRadius: "10px",
                boxShadow: "0 0 10px rgba(109,179,63,0.25)",
                padding: "16px",
                maxHeight: "320px",
                overflowY: "auto",
              }}
            >
              <h2 style={{ marginTop: 0, color: "#6db33f" }}>Recent Tasks</h2>

              <div style={{ height: "18px", marginBottom: "4px" }}>
                {loadingTasks && (
                  <span style={{ fontSize: "13px", color: "#777" }}>Loading…</span>
                )}
              </div>

              {!loadingTasks && recentTasks.length === 0 && (
                <div style={{ fontSize: "13px", color: "#777" }}>
                  No tasks yet. Create one!
                </div>
              )}

              {recentTasks.map((t) => (
                <div
                  key={t.id}
                  style={{
                    borderBottom: "1px solid #e0e0e0",
                    padding: "8px 0",
                    display: "flex",
                    flexDirection: "column",
                    gap: "4px",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                    }}
                  >
                    <span style={{ fontSize: "13px", color: "#999" }}>
                      {t.id}
                    </span>
                    <span
                      style={{
                        fontSize: "13px",
                        fontWeight: "bold",
                        color: statusColor(t.status),
                      }}
                    >
                      {t.status}
                    </span>
                  </div>

                  <div style={{ fontSize: "13px", color: "#333" }}>
                    <b>{t.type}</b> — Attempts {t.attempts}/{t.maxRetries}
                  </div>

                  <div style={{ fontSize: "12px", color: "#777" }}>
                    {new Date(t.createdAt).toLocaleString()}
                  </div>

                  {t.status === "FAILED" && (
                    <button
                      onClick={() => handleRetryTask(t)}
                      style={{
                        marginTop: "4px",
                        alignSelf: "flex-start",
                        padding: "4px 8px",
                        backgroundColor: "#fff5f5",
                        border: "1px solid #d64545",
                        color: "#d64545",
                        borderRadius: "4px",
                        cursor: "pointer",
                        fontSize: "12px",
                      }}
                    >
                      Retry Task
                    </button>
                  )}
                </div>
              ))}
            </div>

            {/* Metrics Snapshot */}
            <div
              style={{
                backgroundColor: "#ffffff",
                border: "2px solid #6db33f",
                borderRadius: "10px",
                boxShadow: "0 0 10px rgba(109,179,63,0.25)",
                padding: "16px",
              }}
            >
              <h2 style={{ marginTop: 0, color: "#6db33f" }}>Queue Snapshot</h2>
              <p style={{ fontSize: "13px", color: "#555" }}>
                From Micrometer / Spring Boot Actuator.
              </p>

              <div style={{ fontSize: "13px", lineHeight: "1.7" }}>
                <div>
                  <b>Tasks Submitted:</b> {submittedCount ?? "—"}
                </div>
                <div>
                  <b>Tasks Completed:</b> {completedCount ?? "—"}
                </div>
                <div>
                  <b>Tasks Failed:</b> {failedCount ?? "—"}
                </div>
                <div>
                  <b>Queue Memory Size:</b> {queueSize ?? "—"}
                </div>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
