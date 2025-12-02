import { useEffect, useState } from "react";
import { useAuth } from "../utility/auth/AuthContext";
import NavBar from "../components/NavBar";

interface Task {
  id: string;
  payload: string;
  type: string;
  status: string;
  attempts: number;
  maxRetries: number;
  createdAt: string;
}

// NOTE:+TO-DO: Should have a search specific Task by ID thing too -- I know it's verbose and makes no sense but still... (showcase functionality).
// ^ It's in SpringQueue and CloudQueue so why not.

export default function TasksPage() {
  const { accessToken } = useAuth();

  const [tasks, setTasks] = useState<Task[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [autoRefresh, setAutoRefresh] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(false);
  // Task creation form
  const [newPayload, setNewPayload] = useState("");
  const [newType, setNewType] = useState("EMAIL");

  const fetchTasks = async () => {
    setLoading(true);

    const query = `
      query($s: TaskStatus) {
        tasks(status: $s) {
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

    const variables = statusFilter === "ALL" ? {} : { s: statusFilter };

    const res = await fetch("/graphql", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`
      },
      body: JSON.stringify({ query, variables })
    });

    const json = await res.json();
    setTasks(json.data.tasks);
    setLoading(false);
  };

  // Auto refresh every 5s
  useEffect(() => {
    fetchTasks();
    if (!autoRefresh) return;
    const id = setInterval(fetchTasks, 5000);
    return () => clearInterval(id);
  }, [statusFilter, autoRefresh]);

  const createTask = async () => {
    const mutation = `
      mutation($i: CreateTaskInput!) {
        createTask(input: $i) {
          id
          status
        }
      }
    `;

    const variables = {
      i: { payload: newPayload, type: newType }
    };

    const res = await fetch("/graphql", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ query: mutation, variables })
    });

    await res.json();
    setNewPayload("");
    fetchTasks();
  };

  const statusColor = (s: string) => {
    switch (s) {
      case "COMPLETED": return "#00b300";
      case "FAILED": return "#e60000";
      case "INPROGRESS": return "#ff9900";
      default: return "gray";
    }
  };

  return (
    <div>
      <NavBar />

      <div style={{ padding: 30, fontFamily: "monospace" }}>
        <h1>📊 SpringQueuePro Task Dashboard</h1>

        <div style={{ marginBottom: 20 }}>
          <label style={{ marginRight: 10 }}>Filter:</label>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="ALL">ALL</option>
            <option value="QUEUED">QUEUED</option>
            <option value="INPROGRESS">INPROGRESS</option>
            <option value="FAILED">FAILED</option>
            <option value="COMPLETED">COMPLETED</option>
          </select>

          <label style={{ marginLeft: 25 }}>
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={() => setAutoRefresh(!autoRefresh)}
              style={{ marginRight: 8 }}
            />
            Auto-refresh
          </label>
        </div>

        {/* Create Task Panel */}
        <div
          style={{
            padding: 18,
            border: "2px solid black",
            borderRadius: 10,
            marginBottom: 30,
            width: 420
          }}
        >
          <h2>Create Task</h2>
          <input
            placeholder="Payload"
            style={{ marginBottom: 10, width: "100%" }}
            value={newPayload}
            onChange={(e) => setNewPayload(e.target.value)}
          />
          <select
            style={{ marginBottom: 10, width: "100%" }}
            value={newType}
            onChange={(e) => setNewType(e.target.value)}
          >
            <option value="EMAIL">EMAIL</option>
            <option value="SMS">SMS</option>
            <option value="REPORT">REPORT</option>
            <option value="DATACLEANUP">DATACLEANUP</option>
            <option value="NEWSLETTER">NEWSLETTER</option>
            <option value="TAKESLONG">TAKESLONG</option>
            <option value="FAIL">FAIL</option>
            <option value="FAILABS">FAILABS</option>
            <option value="TEST">TEST</option>
          </select>

          <button onClick={createTask}>
            Submit Task
          </button>
        </div>

        <h2>Task List</h2>
        {loading && <div>Loading...</div>}

        <table
          style={{
            width: "100%",
            borderCollapse: "collapse",
            marginTop: 10
          }}
        >
          <thead>
            <tr style={{ borderBottom: "2px solid black" }}>
              <th>ID</th>
              <th>Type</th>
              <th>Status</th>
              <th>Attempts</th>
              <th>Payload</th>
              <th>Created</th>
            </tr>
          </thead>

          <tbody>
            {tasks.map((t) => (
              <tr key={t.id} style={{ borderBottom: "1px solid #ddd" }}>
                <td>{t.id}</td>
                <td>{t.type}</td>
                <td style={{ color: statusColor(t.status), fontWeight: "bold" }}>
                  {t.status}
                </td>
                <td>{t.attempts}/{t.maxRetries}</td>
                <td>
                  <pre style={{ whiteSpace: "pre-wrap" }}>
                    {t.payload}
                  </pre>
                </td>
                <td>{new Date(t.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>

      </div>
    </div>
  );
}
