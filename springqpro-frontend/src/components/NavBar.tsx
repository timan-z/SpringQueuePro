import { Link } from "react-router-dom";

export default function NavBar() {
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
          <Link to="/processing" className="navLink">
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
          <button className="logoutBtn"> {/* SOON: onClick={handleLogout} */}
            Logout
          </button>
        </div>
      </nav>
    </div>
  );
}
