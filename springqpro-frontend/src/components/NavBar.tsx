import React from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../utility/auth/AuthContext.tsx";

export default function NavBar() {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async(e: React.FormEvent) => {
      e.preventDefault();
      try {
        await logout();
        navigate("/login");
      } catch(err) {
        // NOTE:+TO-DO:(?) console.warn("Logout request failed - proceeding with local logout only.");
        // NOTE:+TO-DO: ^ Could add something in my AuthContext to set all the states to null/remove localStorage stuff (if needed).
        console.error("Logout failed", err);
      }
  }

  return (
    <div className="navBar">
      <nav className="navContainer">
        <div className="navLeft">
          <Link to="/token-dashboard" className="navLink">
            Auth
          </Link>
          <Link to="/tasks" className="navLink">
            Tasks
          </Link>
          <Link to="/tasks-dashboard" className="navLink">
            Tasks Dashboard
          </Link>
          <Link to="/processing-monitor" className="navLink">
            Processing
          </Link>
          <Link to="/system-health" className="navLink">
            System Health
          </Link>
          <Link to="/about" className="navLink">
            About
          </Link>
        </div>

        <div className="navRight">
          <button className="logoutBtn" onClick={handleLogout}>
            Logout
          </button>
        </div>
      </nav>
    </div>
  );
}
