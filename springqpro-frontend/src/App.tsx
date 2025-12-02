import { BrowserRouter, Routes, Route } from "react-router-dom";
import { AuthProvider } from "./utility/auth/AuthContext";
import ProtectedRoute from "./utility/auth/ProtectedRoute";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import TokenDashboardPage from "./pages/TokenDashboardPage";

export default function App() {
  return(
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/token-dashboard" element={<ProtectedRoute><TokenDashboardPage /></ProtectedRoute>}/>
          {/* Default root redirect: */}
          <Route path="*" element={<LoginPage />}/>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}