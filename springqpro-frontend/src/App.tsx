import { BrowserRouter, Routes, Route, Navigate} from "react-router-dom";
import { AuthProvider } from "./utility/auth/AuthContext";
import { useAuth } from "./utility/auth/AuthContext";
import ProtectedRoute from "./utility/auth/ProtectedRoute";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import TokenDashboardPage from "./pages/TokenDashboardPage";
import TasksPage from "./pages/TasksPage";
import SystemHealthPage from "./pages/SystemHealthPage";
import ProcessingMonitorPage from "./pages/ProcessingMonitorPage";
import AboutPage from "./pages/AboutPage";

// Function that'll check for Access Token and Refresh Token validity for determining default site page (/login or /token-dashboard): 
function DefaultRoute() {
  const { accessToken } = useAuth();
  return accessToken ? <Navigate to="/token-dashboard" replace /> : <Navigate to="/login" replace />;
}

export default function App() {
  return(
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Default home-page (will switch between /login and /token-dashboard depending on if the user is logged in or not). Login defined in DefaultRoute(): */}
          <Route path="/" element={<DefaultRoute/>} />
          <Route path="*" element={<DefaultRoute/>}/> {/* Any unrecognized URL will also map to either /login or /token-dashboard depending on auth status. */}

          {/* LOGIN PAGE: */}
          <Route path="/login" element={<LoginPage />} />

          {/* REGISTER PAGE: */}
          <Route path="/register" element={<RegisterPage />} />

          {/* TOKEN DASHBOARD: */}
          <Route path="/token-dashboard" element={<ProtectedRoute><TokenDashboardPage /></ProtectedRoute>}/>

          {/* TASKS PAGE: */}
          <Route path="/tasks" element={<ProtectedRoute><TasksPage /></ProtectedRoute>}/>

          {/* PROCESSING MONITOR PAGE: */}
          <Route path="/processing-monitor" element={<ProtectedRoute><ProcessingMonitorPage/></ProtectedRoute>}/>

          {/* METRICS PAGE: */}
          <Route path="/system-health" element={<ProtectedRoute><SystemHealthPage /></ProtectedRoute>}/>

          {/* ABOUT PAGE: */}
          <Route path="/about" element={<AboutPage/>}/> 
          {/* ^ Shouldn't need to be guarded to be honest. I should have like a version of the About Page that
          lacks the Navigation Bar or maybe just replaces it with one that only has the Login/Register buttons...
          (Like a different About Page that you can view depending on if you're logged in or not). */}

        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
