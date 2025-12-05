import { useEffect, useState } from "react";
import NavBar from "../components/NavBar";
import { API_BASE } from "../api/api";
import { useAuth } from "../utility/auth/AuthContext";

// --- Types matching your schema.graphqls ---
type TaskStatus = "QUEUED" | "INPROGRESS" | "COMPLETED" | "FAILED";

type TaskType =
  | "EMAIL"
  | "REPORT"
  | "DATACLEANUP"
  | "SMS"
  | "NEWSLETTER"
  | "TAKESLONG"
  | "FAIL"
  | "FAILABS"
  | "TEST";

interface Task {
  id: string;
  payload: string;
  type: TaskType;
  status: TaskStatus;
  attempts: number;
  maxRetries: number;
  createdAt: string;
}

// --- Metric response shape from /actuator/metrics ---
interface MetricResponse {
  name: string;
  measurements: { statistic: string; value: number }[];
  availableTags: any[];
}

export default function TasksPage() {
  const { accessToken } = useAuth();

  // ---- Create Task form state ----
  const [payload, setPayload] = useState("");
  const [taskType, setTaskType] = useState<TaskType>("EMAIL");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // ---- GraphQL preview + response ----
  const [lastMutationPreview, setLastMutationPreview] = useState<string>("");
  const [lastResponseJson, setLastResponseJson] = useState<string>("");

  // ---- Recent tasks ----
  const [recentTasks, setRecentTasks] = useState<Task[]>([]);
  const [loadingTasks, setLoadingTasks] = useState(false);

  // ---- Metrics snapshot ----
  const [submittedCount, setSubmittedCount] = useState<number | null>(null);
  const [completedCount, setCompletedCount] = useState<number | null>(null);
  const [failedCount, setFailedCount] = useState<number | null>(null);
  const [queueSize, setQueueSize] = useState<number | null>(null);

  // ---------- Helper: Generic GraphQL caller ----------
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

  // ---------- Create Task handler ----------
  const handleCreateTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!payload.trim()) {
      setCreateError("Please enter a payload.");
      return;
    }
    setCreateError(null);
    setCreating(true);

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

    // Pretty preview of the mutation (for the “GraphQL preview” card)
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
      setLastResponseJson(JSON.stringify(json, null, 2));
      // Clear the payload field for quick follow-up submits
      setPayload("");
      // Refresh recent tasks immediately
      fetchRecentTasks();
    } catch (err: any) {
      setLastResponseJson(`Error: ${err.message ?? String(err)}`);
    } finally {
      setCreating(false);
    }
  };

  // ---------- Fetch Recent Tasks ----------
  const fetchRecentTasks = async () => {
    if (!accessToken) return;
    setLoadingTasks(true);

    const query = `
      query RecentTasks {
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

      // Sort by createdAt desc and take last 8
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

  // Auto-refresh recent tasks every 3 seconds
  useEffect(() => {
    fetchRecentTasks();
    const interval = setInterval(fetchRecentTasks, 3000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken]);

  // ---------- Retry (for FAILED tasks) ----------
  const handleRetryTask = async (task: Task) => {
    if (!accessToken) return;
    const mutation = `
      mutation RetryTask($input: StdUpdateTaskInput!) {
        updateTask(input: $input) {
          id
          status
          attempts
        }
      }
    `;
    const variables = {
      input: {
        id: task.id,
        status: "QUEUED",
      },
    };
    try {
      await callGraphQL(mutation, variables);
      // after setting QUEUED in DB, ProcessingService / queue path can re-enqueue it
      fetchRecentTasks();
    } catch (err) {
      console.error("Failed to retry task:", err);
    }
  };

  // ---------- Metrics Snapshot ----------
  const fetchMetric = async (key: string): Promise<number | null> => {
    try {
      const res = await fetch(`${API_BASE}/actuator/metrics/${key}`, {
        headers: {
          Authorization: accessToken ? `Bearer ${accessToken}` : "",
        },
      });
      if (!res.ok) return null;
      const json: MetricResponse = await res.json();
      return json.measurements?.[0]?.value ?? null;
    } catch {
      return null;
    }
  };

  const fetchMetrics = async () => {
    if (!accessToken) return;
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken]);

  // ---------- Status color helper ----------
  const statusColor = (status: TaskStatus): string => {
    switch (status) {
      case "QUEUED":
        return "#d9a441"; // yellow-ish
      case "INPROGRESS":
        return "#2f8faf"; // cyan/blue
      case "COMPLETED":
        return "#2f9e44"; // green
      case "FAILED":
        return "#d64545"; // red
      default:
        return "#2d2d2d";
    }
  };

  // ------------------ RENDER ------------------
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
          {/* LEFT COLUMN: Create + Preview */}
          <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
            {/* Quick Create Card */}
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
                Enqueue a new task into SpringQueuePro via GraphQL.
              </p>

              {createError && (
                <div style={{ color: "#cc0000", fontSize: "13px", marginBottom: "8px" }}>
                  ⚠ {createError}
                </div>
              )}

              <form onSubmit={handleCreateTask}>
                <div style={{ marginBottom: "10px" }}>
                  <label
                    style={{
                      display: "block",
                      marginBottom: "4px",
                      fontSize: "14px",
                      color: "#2d2d2d",
                    }}
                  >
                    Payload
                  </label>
                  <input
                    value={payload}
                    onChange={(e) => setPayload(e.target.value)}
                    placeholder="e.g., Send email to user@example.com"
                    style={{
                      width: "100%",
                      padding: "8px",
                      border: "1px solid #6db33f",
                      borderRadius: "4px",
                      fontFamily: "monospace",
                    }}
                  />
                </div>

                <div style={{ marginBottom: "15px" }}>
                  <label
                    style={{
                      display: "block",
                      marginBottom: "4px",
                      fontSize: "14px",
                      color: "#2d2d2d",
                    }}
                  >
                    Task Type
                  </label>
                  <select
                    value={taskType}
                    onChange={(e) => setTaskType(e.target.value as TaskType)}
                    style={{
                      width: "100%",
                      padding: "8px",
                      border: "1px solid #6db33f",
                      borderRadius: "4px",
                      fontFamily: "monospace",
                      backgroundColor: "#ffffff",
                    }}
                  >
                    <option value="EMAIL">EMAIL</option>
                    <option value="REPORT">REPORT</option>
                    <option value="DATACLEANUP">DATACLEANUP</option>
                    <option value="SMS">SMS</option>
                    <option value="NEWSLETTER">NEWSLETTER</option>
                    <option value="TAKESLONG">TAKESLONG</option>
                    <option value="FAIL">FAIL (retry demo)</option>
                    <option value="FAILABS">FAILABS</option>
                    <option value="TEST">TEST</option>
                  </select>
                </div>

                <button
                  type="submit"
                  disabled={creating}
                  style={{
                    width: "100%",
                    padding: "10px",
                    backgroundColor: creating ? "#85b577" : "#6db33f",
                    border: "none",
                    borderRadius: "6px",
                    color: "#ffffff",
                    fontWeight: "bold",
                    cursor: "pointer",
                    fontSize: "15px",
                    boxShadow: "0 0 8px rgba(109,179,63,0.4)",
                  }}
                >
                  {creating ? "Creating..." : "Create Task"}
                </button>
              </form>
            </div>

            {/* GraphQL Preview + Response */}
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
{lastMutationPreview || "// Fill the form and click Create Task to see the GraphQL mutation here."}
              </pre>

              <h3 style={{ marginTop: "14px", color: "#6db33f" }}>Last Response</h3>
              <pre
                style={{
                  backgroundColor: "#f0f4f0",
                  borderRadius: "6px",
                  padding: "10px",
                  fontSize: "12px",
                  overflowX: "auto",
                }}
              >
{lastResponseJson || "// Response from backend will appear here."}
              </pre>
            </div>
          </div>

          {/* RIGHT COLUMN: Recent tasks + Metrics */}
          <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
            {/* Recent Tasks */}
            <div
              style={{
                backgroundColor: "#ffffff",
                border: "2px solid #6db33f",
                borderRadius: "10px",
                boxShadow: "0 0 10px rgba(109,179,63,0.25)",
                padding: "16px",
              }}
            >
              <h2 style={{ marginTop: 0, color: "#6db33f" }}>Recent Tasks</h2>
              <p style={{ fontSize: "13px", color: "#555" }}>
                Auto-refreshing every 3 seconds. Shows latest 8 tasks.
              </p>

              {loadingTasks && (
                <div style={{ fontSize: "13px", color: "#777" }}>Loading...</div>
              )}

              {recentTasks.length === 0 && !loadingTasks && (
                <div style={{ fontSize: "13px", color: "#777" }}>
                  No tasks found yet. Create one on the left.
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
                      alignItems: "center",
                    }}
                  >
                    <span style={{ fontSize: "13px", color: "#999" }}>{t.id}</span>
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
                    <b>Type:</b> {t.type} &nbsp; | &nbsp; <b>Attempts:</b>{" "}
                    {t.attempts}/{t.maxRetries}
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
                        borderRadius: "4px",
                        border: "1px solid #d64545",
                        backgroundColor: "#fff5f5",
                        color: "#d64545",
                        fontSize: "12px",
                        cursor: "pointer",
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
                Pulled from Spring Boot Actuator / Micrometer.
              </p>

              <div style={{ fontSize: "13px", lineHeight: "1.7", color: "#333" }}>
                <div>
                  <b>Tasks Submitted:</b>{" "}
                  {submittedCount !== null ? submittedCount : "—"}
                </div>
                <div>
                  <b>Tasks Completed:</b>{" "}
                  {completedCount !== null ? completedCount : "—"}
                </div>
                <div>
                  <b>Tasks Failed:</b>{" "}
                  {failedCount !== null ? failedCount : "—"}
                </div>
                <div>
                  <b>Queue Memory Size:</b>{" "}
                  {queueSize !== null ? queueSize : "—"}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
