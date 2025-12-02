import React from "react";
import { Link } from "react-router-dom";

export default function NavBar() {
  return (
    <div className="navBar">
      <nav>
        <Link to="/token-dashboard" style={{ textDecoration: "none" }}>
          Auth
        </Link>
        <Link to="/tasks" style={{ textDecoration: "none" }}>
          Tasks
        </Link>
        <Link to="/processing" style={{ textDecoration: "none" }}>
          Processing
        </Link>
        <Link to="/system-health" style={{ textDecoration: "none" }}>
          System Health
        </Link>
      </nav>
    </div>
  );
}
