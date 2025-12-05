import { useEffect, useState } from "react";
import NavBar from "../components/NavBar";
import TaskDetailDrawer from "../components/TaskDetailDrawer";
import { API_BASE } from "../api/api";
import { useAuth } from "../utility/auth/AuthContext";

type Status = "QUEUED" | "INPROGRESS" | "COMPLETED" | "FAILED";
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
  status: Status;
  attempts: number;
  maxRetries: number;
  createdAt: string;
}

export default function TasksDashboardPage() {
  const { accessToken } = useAuth();

  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(false);

  // Filters
  const [statusFilter, setStatusFilter] = useState<Status | "ALL">("ALL");
  const [typeFilter, setTypeFilter] = useState<TaskType | "ALL">("ALL");

  // Sorting
  const [sortKey, setSortKey] = useState<keyof Task>("createdAt");
  const [sortAsc, setSortAsc] = useState(false);

  // GraphQL Explorer
  const [gqlQuery, setGqlQuery] = useState<string>(`
{
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
  `);
  const [gqlResult, setGqlResult] = useState<string>("");

  // Drawer
  const [drawerTask, setDrawerTask] = useState<Task | null>(null);
  const [drawerQuery, setDrawerQuery] = useState<string>("");

  // ----- Helper -----
  const gql = async (query: string, variables?: any) => {
    const res = await fetch(`${API_BASE}/graphql`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: accessToken ? `Bearer ${accessToken}` : "",
      },
      body: JSON.stringify({ query, variables }),
    });
    return res.json();
  };

  // ----- Load tasks -----
  const loadTasks = async () => {
    setLoading(true);

    const query = `
      query AllTasks {
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

    const json = await gql(query);
    setTasks(json.data.tasks ?? []);
    setLoading(false);
  };

  useEffect(() => {
    loadTasks();
  }, []);

  // ----- Retry task -----
  const retryTask = async (task: Task) => {
    const mutation = `
      mutation Retry($input: StdUpdateTaskInput!) {
        updateTask(input: $input) {
          id
          status
          attempts
        }
      }
    `;

    await gql(mutation, {
      input: { id: task.id, status: "QUEUED" },
    });

    loadTasks();
  };

  // ----- Delete task -----
  const deleteTask = async (task: Task) => {
    const mutation = `
      mutation DeleteTask($id: ID!) {
        deleteTask(id: $id)
      }
    `;

    await gql(mutation, { id: task.id });
    loadTasks();
  };

  // ----- Run GraphQL Explorer -----
  const runExplorer = async () => {
    const json = await gql(gqlQuery);
    setGqlResult(JSON.stringify(json, null, 2));
  };

  // ----- Apply filters + sorting -----
  const tasksView = [...tasks]
    .filter((t) => (statusFilter === "ALL" ? true : t.status === statusFilter))
    .filter((t) => (typeFilter === "ALL" ? true : t.type === typeFilter))
    .sort((a, b) => {
      const aVal = (a as any)[sortKey];
      const bVal = (b as any)[sortKey];
      if (sortAsc) return aVal > bVal ? 1 : -1;
      return aVal < bVal ? 1 : -1;
    });

  // ----- Status color -----
  const statusColor = (s: Status) =>
    s === "COMPLETED"
      ? "#2f9e44"
      : s === "FAILED"
      ? "#d64545"
      : s === "INPROGRESS"
      ? "#2f8faf"
      : "#d9a441";

  return (
    <div style={{ backgroundColor: "#f6f8fa", minHeight: "100vh" }}>
      <NavBar />

      <div style={{ padding: "30px", maxWidth: "1200px", margin: "auto" }}>
        <h1 style={{ color: "#6db33f", fontFamily: "monospace" }}>
          Tasks Dashboard
        </h1>

        <p style={{ marginTop: "-10px", color: "#444" }}>
          Full system introspection: GraphQL, metrics, filters, task lifecycle.
        </p>

        {/* ========== LAYOUT GRID ========== */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: "24px",
            marginTop: "20px",
          }}
        >
          {/* LEFT: TABLE */}
          <div
            style={{
              background: "white",
              borderRadius: "8px",
              border: "2px solid #6db33f",
              padding: "20px",
              boxShadow: "0 0 10px rgba(109,179,63,0.2)",
            }}
          >
            <h2 style={{ marginTop: 0, color: "#6db33f" }}>All Tasks</h2>

            {/* Filters */}
            <div style={{ display: "flex", gap: "10px", marginBottom: "15px" }}>
              <select
                value={statusFilter}
                onChange={(e) =>
                  setStatusFilter(e.target.value as Status | "ALL")
                }
                style={{
                  padding: "6px",
                  border: "1px solid #6db33f",
                  borderRadius: "4px",
                }}
              >
                <option value="ALL">All Statuses</option>
                <option value="QUEUED">Queued</option>
                <option value="INPROGRESS">In-Progress</option>
                <option value="COMPLETED">Completed</option>
                <option value="FAILED">Failed</option>
              </select>

              <select
                value={typeFilter}
                onChange={(e) =>
                  setTypeFilter(e.target.value as TaskType | "ALL")
                }
                style={{
                  padding: "6px",
                  border: "1px solid #6db33f",
                  borderRadius: "4px",
                }}
              >
                <option value="ALL">All Types</option>
                <option value="EMAIL">EMAIL</option>
                <option value="REPORT">REPORT</option>
                <option value="DATACLEANUP">DATACLEANUP</option>
                <option value="SMS">SMS</option>
                <option value="NEWSLETTER">NEWSLETTER</option>
                <option value="TAKESLONG">TAKESLONG</option>
                <option value="FAIL">FAIL</option>
                <option value="FAILABS">FAILABS</option>
                <option value="TEST">TEST</option>
              </select>
            </div>

            {/* Table */}
            <div
              style={{
                maxHeight: "650px",
                overflowY: "auto",
                borderTop: "1px solid #ddd",
              }}
            >
              {loading && <div>Loading...</div>}

              {tasksView.map((t) => (
                <div
                  key={t.id}
                  style={{
                    padding: "10px 6px",
                    borderBottom: "1px solid #eee",
                    cursor: "pointer",
                  }}
                >
                  {/* Row Top */}
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      fontSize: "13px",
                    }}
                  >
                    <span
                      onClick={async () => {
                        const q = `
                          query TaskById {
                            task(id: "${t.id}") {
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
                        const json = await gql(q);
                        setDrawerTask(json.data.task);
                        setDrawerQuery(q);
                      }}
                      style={{ color: "#666", textDecoration: "underline" }}
                    >
                      {t.id}
                    </span>

                    <span style={{ color: statusColor(t.status) }}>
                      {t.status}
                    </span>
                  </div>

                  <div style={{ fontSize: "12px", color: "#444" }}>
                    {t.type} • attempts {t.attempts}/{t.maxRetries}
                  </div>

                  {/* Row Actions */}
                  <div style={{ marginTop: "6px", display: "flex", gap: "6px" }}>
                    {t.status === "FAILED" && (
                      <button
                        onClick={() => retryTask(t)}
                        style={{
                          padding: "4px 6px",
                          fontSize: "11px",
                          backgroundColor: "#fff5f5",
                          color: "#d64545",
                          border: "1px solid #d64545",
                          borderRadius: "4px",
                        }}
                      >
                        Retry
                      </button>
                    )}

                    <button
                      onClick={() => deleteTask(t)}
                      style={{
                        padding: "4px 6px",
                        fontSize: "11px",
                        backgroundColor: "#f8f9fa",
                        color: "#666",
                        border: "1px solid #aaa",
                        borderRadius: "4px",
                      }}
                    >
                      Delete
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* RIGHT: GraphQL Explorer */}
          <div
            style={{
              background: "white",
              borderRadius: "8px",
              border: "2px solid #6db33f",
              padding: "20px",
              boxShadow: "0 0 10px rgba(109,179,63,0.2)",
            }}
          >
            <h2 style={{ marginTop: 0, color: "#6db33f" }}>
              Safe GraphQL Explorer
            </h2>

            <textarea
              value={gqlQuery}
              onChange={(e) => setGqlQuery(e.target.value)}
              style={{
                width: "100%",
                height: "220px",
                borderRadius: "6px",
                border: "1px solid #6db33f",
                padding: "10px",
                fontFamily: "monospace",
                fontSize: "13px",
              }}
            />

            <button
              onClick={runExplorer}
              style={{
                marginTop: "10px",
                width: "100%",
                padding: "10px",
                backgroundColor: "#6db33f",
                color: "white",
                fontWeight: "bold",
                border: "none",
                borderRadius: "6px",
                cursor: "pointer",
              }}
            >
              Run Query
            </button>

            <pre
              style={{
                backgroundColor: "#f3f5f2",
                padding: "10px",
                marginTop: "12px",
                height: "200px",
                overflowY: "auto",
                fontSize: "12px",
                borderRadius: "6px",
              }}
            >
{gqlResult || "// Results appear here"}
            </pre>
          </div>
        </div>
      </div>

      {/* Drawer */}
      <TaskDetailDrawer
        task={drawerTask}
        graphqlQuery={drawerQuery}
        onClose={() => setDrawerTask(null)}
      />
    </div>
  );
}
