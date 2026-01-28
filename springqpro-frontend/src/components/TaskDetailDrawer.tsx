/* TaskDetailDrawer.tsx:
------------------------
This is just an external modular panel in which specific Task details will be displayed when you click on a specific
Task in the Task Dashboard page (TaskDashboardPage.tsx). It'll slide in from the right-hand side of the screen, show
the full task JSON, show the GraphQL query used to fetch it, and include a close button.
------------------------
TL;DR: It is a stateless, presentational UI overlay. This is just a cosmetic "drawer" component.
*/

interface TaskDetailDrawerProps {
  task: any | null; // <-- NOTE: I can maybe tighten this to be an explicit Task interface eventually. This is probably bad TypeScript practice.
  graphqlQuery: string;
  onClose: () => void;
}

export default function TaskDetailDrawer({
  task,
  graphqlQuery,
  onClose,
}: TaskDetailDrawerProps) {
  if (!task) return null;

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        right: 0,
        width: "420px",
        height: "100vh",
        backgroundColor: "#ffffff",
        borderLeft: "3px solid #6db33f",
        boxShadow: "-4px 0 12px rgba(0,0,0,0.15)",
        padding: "20px",
        overflowY: "auto",
        fontFamily: "monospace",
        zIndex: 999,
      }}
    >
      {/* Close Button */}
      <button
        onClick={onClose}
        style={{
          backgroundColor: "#6db33f",
          color: "white",
          border: "none",
          borderRadius: "4px",
          padding: "6px 12px",
          fontWeight: "bold",
          cursor: "pointer",
          marginBottom: "15px",
        }}
      >
        Close
      </button>

      <h2 style={{ color: "#6db33f", marginTop: 0 }}>Task Details</h2>

      <h4>GraphQL Query Used</h4>
      <pre
        style={{
          backgroundColor: "#f3f5f2",
          padding: "10px",
          borderRadius: "6px",
          fontSize: "12px",
          overflowX: "auto",
        }}
      >
        {graphqlQuery}
      </pre>

      <h4 style={{ marginTop: "20px" }}>Task JSON</h4>
      <pre
        style={{
          backgroundColor: "#f3f5f2",
          padding: "10px",
          borderRadius: "6px",
          fontSize: "12px",
          overflowX: "auto",
        }}
      >
        {JSON.stringify(task, null, 2)}
      </pre>
    </div>
  );
}
