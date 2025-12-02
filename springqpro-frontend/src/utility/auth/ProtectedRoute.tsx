import React from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function ProtectedRoute({ children }: { children: React.JSX.Element }) {
    const { accessToken } = useAuth();
    return accessToken ? children : <Navigate to="/login" replace />;
}
