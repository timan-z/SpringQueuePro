import { useEffect, useState } from "react";
import NavBar from "../components/NavBar";
import TaskDetailDrawer from "../components/TaskDetailDrawer";
import { API_BASE, getEnumLists } from "../api/api";
import { useAuth } from "../utility/auth/AuthContext";

/* -------------------- Types -------------------- */

type TaskType = string;
type Status = string;

interface Task {
  id: string;
  payload: string;
  type: TaskType;
  status: Status;
  attempts: number;
  maxRetries: number;
  createdAt: string;
}

/* ============================================================
                      TASKS DASHBOARD PAGE
   ============================================================ */
export default function TasksDashboardPage() {
  const { accessToken } = useAuth();

  /* ---------- Dynamic Enums from Schema ---------- */
  const [taskTypes, setTaskTypes] = useState<TaskType[]>([]);
  const [taskStatuses, setTaskStatuses] = useState<Status[]>([]);
  const [enumsLoaded, setEnumsLoaded] = useState(false);

  /* ---------- Data ---------- */
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(false);

  /* ---------- Filters ---------- */
  const [statusFilter, setStatusFilter] = useState<Status | "ALL">("ALL");
  const [typeFilter, setTypeFilter] = useState<TaskType | "ALL">("ALL");
  const [searchId, setSearchId] = useState("");

  /* ---------- Sorting ---------- */
  const [sortKey, setSortKey] = useState<keyof Task>("createdAt");
  const [sortAsc, setSortAsc] = useState(false);

  /* ---------- GraphQL Explorer ---------- */
  const [gqlQuery, setGqlQuery] = useState(`
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
  `.trim());
  const [gqlResult, setGqlResult] = useState("");

  /* ---------- Drawer ---------- */
  const [drawerTask, setDrawerTask] = useState<Task | null>(null);
  const [drawerQuery, setDrawerQuery] = useState("");

  /* ---------- Auto-refresh ---------- */
  const [autoRefresh, setAutoRefresh] = useState(true);

  /* -------------------- Generic GQL Caller -------------------- */
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

  /* ============================================================
                      Load Enum Lists (Dynamic)
     ============================================================ */
  const loadEnumLists = async () => {
    if (!accessToken) return;
    try {
      const enums = await getEnumLists(accessToken);
      setTaskTypes(enums.taskTypes);
      setTaskStatuses(enums.taskStatuses);
      setEnumsLoaded(true);
    } catch (err) {
      console.error("Failed to fetch GraphQL enum lists:", err);
    }
  };

  useEffect(() => {
    loadEnumLists();
  }, [accessToken]);

  /* ============================================================
                          Fetch ALL tasks
     ============================================================ */
  const loadTasks = async () => {
    if (!accessToken) return;

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

  /* Auto-refresh toggle */
  useEffect(() => {
    if (!autoRefresh) return;

    const interval = setInterval(loadTasks, 3500);
    return () => clearInterval(interval);
  }, [autoRefresh]);

  /* ============================================================
                      Retry / Delete Tasks
     ============================================================ */
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

  const deleteTask = async (task: Task) => {
    const mutation = `
      mutation DeleteTask($id: ID!) {
        deleteTask(id: $id)
      }
    `;
    await gql(mutation, { id: task.id });
    loadTasks();
  };

  /* ============================================================
                      GraphQL Explorer: Run Query
     ============================================================ */
  const runExplorer = async () => {
    const json = await gql(gqlQuery);
    setGqlResult(JSON.stringify(json, null, 2));
  };

  /* ============================================================
                      Apply Filters + Sorting + Search
     ============================================================ */
  const tasksView = [...tasks]
    .filter((t) => (statusFilter === "ALL" ? true : t.status === statusFilter))
    .filter((t) => (typeFilter === "ALL" ? true : t.type === typeFilter))
    .filter((t) =>
      searchId.trim() === "" ? true : t.id.includes(searchId.trim())
    )
    .sort((a, b) => {
      const aVal = (a as any)[sortKey];
      const bVal = (b as any)[sortKey];
      if (sortAsc) return aVal > bVal ? 1 : -1;
      return aVal < bVal ? 1 : -1;
    });

  /* ============================================================
                      Status Color
     ============================================================ */
  const statusColor = (s: Status) =>
    s === "COMPLETED"
      ? "#2f9e44"
      : s === "FAILED"
      ? "#d64545"
      : s === "INPROGRESS"
      ? "#2f8faf"
      : "#d9a441";

  /* ============================================================
                           RENDER
     ============================================================ */
  return (
    <div style={{ backgroundColor: "#f6f8fa", minHeight: "100vh" }}>
      <NavBar />

      <div style={{ padding: "30px", maxWidth: "1200px", margin: "auto" }}>
        <h1 style={{ color: "#6db33f", fontFamily: "monospace" }}>
          Tasks Dashboard
        </h1>

        <p style={{ marginTop: "-10px", color: "#444" }}>
          Deep system introspection: GraphQL, analytics, filtering, retries,
          locking, and full task lifecycle.
        </p>

        {/* ======================================================
                         MAIN GRID LAYOUT
         ====================================================== */}
        <div
          style={{
            marginTop: "20px",
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: "24px",
          }}
        >

          {/* ==============================================================  
                           LEFT CARD — TASK TABLE
            ============================================================== */}
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

            {/* ----- FILTERS ----- */}
            <div
              style={{
                display: "flex",
                gap: "10px",
                marginBottom: "12px",
                alignItems: "center",
              }}
            >
              {/* Status Filter */}
              <select
                value={statusFilter}
                onChange={(e) =>
                  setStatusFilter(e.target.value as Status | "ALL")
                }
                style={{
                  padding: "6px",
                  border: "1px solid #6db33f",
                  borderRadius: "4px",
                  fontFamily: "monospace",
                }}
              >
                <option value="ALL">All Statuses</option>
                {taskStatuses.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>

              {/* Type Filter */}
              <select
                value={typeFilter}
                onChange={(e) =>
                  setTypeFilter(e.target.value as TaskType | "ALL")
                }
                style={{
                  padding: "6px",
                  border: "1px solid #6db33f",
                  borderRadius: "4px",
                  fontFamily: "monospace",
                }}
              >
                <option value="ALL">All Types</option>
                {taskTypes.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>

              {/* Search by ID */}
              <input
                placeholder="Search by ID…"
                value={searchId}
                onChange={(e) => setSearchId(e.target.value)}
                style={{
                  padding: "6px",
                  border: "1px solid #6db33f",
                  borderRadius: "4px",
                  fontFamily: "monospace",
                  flex: 1,
                }}
              />

              {/* Auto-refresh Toggle */}
              <button
                onClick={() => setAutoRefresh((v) => !v)}
                style={{
                  padding: "6px 10px",
                  fontSize: "12px",
                  backgroundColor: autoRefresh ? "#6db33f" : "#c2c2c2",
                  color: autoRefresh ? "white" : "#333",
                  border: "none",
                  borderRadius: "4px",
                  cursor: "pointer",
                }}
              >
                {autoRefresh ? "Auto: ON" : "Auto: OFF"}
              </button>
            </div>

            {/* ----- TABLE SCROLL WRAPPER ----- */}
            <div
              style={{
                maxHeight: "650px",
                overflowY: "auto",
                borderTop: "1px solid #ddd",
              }}
            >
              {loading && <div>Loading…</div>}

              {tasksView.map((t) => (
                <div
                  key={t.id}
                  style={{
                    padding: "10px 6px",
                    borderBottom: "1px solid #eee",
                  }}
                >
                  {/* Row header */}
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      fontSize: "13px",
                      cursor: "pointer",
                    }}
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
                  >
                    <span style={{ color: "#666", textDecoration: "underline" }}>
                      {t.id}
                    </span>

                    <span style={{ color: statusColor(t.status) }}>
                      {t.status}
                    </span>
                  </div>

                  {/* Detail */}
                  <div style={{ fontSize: "12px", color: "#444" }}>
                    {t.type} — attempts {t.attempts}/{t.maxRetries}
                  </div>

                  {/* Row Actions */}
                  <div
                    style={{
                      marginTop: "6px",
                      display: "flex",
                      gap: "6px",
                    }}
                  >
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
                          cursor: "pointer",
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
                        cursor: "pointer",
                      }}
                    >
                      Delete
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* ==============================================================  
                      RIGHT CARD — SAFE GRAPHQL EXPLORER
            ============================================================== */}
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

      {/* ==============================================================  
                        TASK DETAIL DRAWER
         ============================================================== */}
      <TaskDetailDrawer
        task={drawerTask}
        graphqlQuery={drawerQuery}
        onClose={() => setDrawerTask(null)}
      />
    </div>
  );
}
