// ProtectedRoute.tsx - This is the authentication gatekeeper! Checks auth at render time -> render or redirect.
import React from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function ProtectedRoute({ children }: { children: React.JSX.Element }) {
    const { accessToken } = useAuth();
    return accessToken ? children : <Navigate to="/login" replace />;
}
