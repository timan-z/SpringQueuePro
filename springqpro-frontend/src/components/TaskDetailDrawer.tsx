// Just a modular panel that'll contain Task detail when you click a specific Task on the Tasks/Task Dashboard Page.
/* This wil slide in from the right-hand side of the screen, show the full task JSON, show the GraphQL query
used to fetch it, and includes a close button.
*/
import React from "react";

interface TaskDetailDrawerProps {
  task: any | null;
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
