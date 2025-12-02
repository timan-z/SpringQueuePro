import { BrowserRouter, Routes, Route } from "react-router-dom";
import { AuthProvider } from "./utility/auth/AuthContext";
import ProtectedRoute from "./utility/auth/ProtectedRoute";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import TokenDashboardPage from "./pages/TokenDashboardPage";
import TasksPage from "./pages/TasksPage";
import SystemHealthPage from "./pages/SystemHealthPage";

export default function App() {
  return(
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* LOGIN PAGE: */}
          <Route path="/login" element={<LoginPage />} />
          {/* REGISTER PAGE: */}
          <Route path="/register" element={<RegisterPage />} />
          {/* TOKEN DASHBOARD: */}
          <Route path="/token-dashboard" element={<ProtectedRoute><TokenDashboardPage /></ProtectedRoute>}/>
          {/* TASKS PAGE: */}
          <Route path="/tasks" element={<ProtectedRoute><TasksPage /></ProtectedRoute>}/>

          {/* METRICS PAGE: */}
          <Route path="/system-health" element={<ProtectedRoute><SystemHealthPage /></ProtectedRoute>}/>

          {/* Default root redirect: */}
          <Route path="*" element={<LoginPage />}/>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}