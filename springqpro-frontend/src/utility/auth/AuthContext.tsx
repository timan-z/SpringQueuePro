/* AuthContext.tsx:
-------------------
This file is the heart of the authentication system. It stores information about who is logged in,
what tokens exist, if they are expired, how we restore sessions, refresh safely, and how the rest of
the application consumes authentication.
- Without this file every page would need to re-implement authentication logic.
- Also, token refresh and routing would be brittle.
***
NOTE(S)-TO-SELF (worth noting down on in my notes):
- React createContext allows you to create a "context" object which lets you share data (e.g., state, functions,
and other values) across other components w/o needing to manually pass props down every level of the component tree.
*/

import React, { createContext, useContext, useEffect, useState} from "react";
import { jwtDecode } from "jwt-decode";
import { refreshAccessToken, logoutUser } from "../../api/api";

// DecodedToken interface defines what a JWT is expected to contain:
interface DecodedToken {
    sub: string;
    exp: number;
    iat: number;
}

// AuthContextType is the public contract the rest of the SpringQueuePro frontend needs to comply with:
interface AuthContextType {
    accessToken: string | null;
    refreshToken: string | null;
    decoded: DecodedToken | null;
    login: (access: string, refresh: string) => void;
    logout: () => Promise<void>;
    refresh: () => Promise<void>;
}

// Context Creation:
const AuthContext = createContext<AuthContextType>(null!);  // null! implies *I* promise that this context will always be provided. (I do w/ AuthProvider below).
export const useAuth = () => useContext(AuthContext);

// Token expiry helper method:
function isTokenExpired(token: string): boolean {
    try {
        // decodes JWT and compares expiration time to current time:
        const decoded = jwtDecode<DecodedToken>(token);
        const now = Math.floor(Date.now() / 1000);
        return decoded.exp < now;
    } catch {
        return true;
    }
}

/* This AuthProvider const will be what defines how data is created, stored, and kept up to date over time,
whereas AuthContext defines *what* the authentication data looks like, and how it can be consumed.
- AuthContext = communication channel (definition of shared auth data).
- AuthProvider = owner of authentication state and lifecycle (makes sure auth system exists at runtime/provides said data).
AuthProvider is the provider for the AuthContext (every page delegates to AuthProvider).
*/
export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    // State ownership:
    const [accessToken, setAccessToken] = useState<string | null>(localStorage.getItem("accessToken"));
    const [refreshToken, setRefreshToken] = useState<string | null>(localStorage.getItem("refreshToken"));
    const [decoded, setDecoded] = useState<DecodedToken | null>(accessToken ? jwtDecode(accessToken) : null);

    // Functions for Auth interaction and mutability (state alteration via Login, Register, Refresh, and so on).
    // [1] - Login:
    const login = (access: string, refresh: string) => {
        // LoginPage, Refresh flow, and Session restore all funnel through here.
        setAccessToken(access);
        setRefreshToken(refresh);
        setDecoded(jwtDecode(access));
        localStorage.setItem("accessToken", access);
        localStorage.setItem("refreshToken", refresh); 
    };
    // [2] - Logout:
    const logout = async() => {
        // Server logout is best effort:
        try {
            if(refreshToken) await logoutUser(refreshToken);
        } catch { }
        // Local logout is authoritative and the app never stays half-logged-in.
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
        setAccessToken(null);
        setRefreshToken(null);
        setDecoded(null);
        // Clear any user-scoped diagnostic data:
        Object.keys(localStorage).filter(key => key.startsWith("rotationHistory:")).forEach(key => localStorage.removeItem(key));
    }
    // [3] - Refresh:
    const refresh = async() => {
        // Refresh success -> login again | refresh failure -> logout:
        if(!refreshToken) return;
        const result = await refreshAccessToken(refreshToken);
        if(result?.accessToken) {
            login(result.accessToken, result.refreshToken);
        } else {
            await logout();
        }
    };

    // Session Restore UseEffect hook - runs once on app startup before any protected route renders:
    useEffect(() => {
        const storedAccess = localStorage.getItem("accessToken");
        const storedRefresh = localStorage.getItem("refreshToken");
        // Not logged in.
        if (!storedAccess || !storedRefresh) {
            logout();
            return;
        }
        // Access token still valid so restore session.
        if (!isTokenExpired(storedAccess)) {
            login(storedAccess, storedRefresh);
            return;
        }
        // Access expired so attempt refresh if refreshToken valid.
        refreshAccessToken(storedRefresh)
        .then((res) => {
            if (res.accessToken) {
                login(res.accessToken, res.refreshToken);
            } else {
                logout();
            }
        })
        .catch(() => logout());
    }, []);

    // Automatic Refresh UseEffect hook - for when access token expires:
    useEffect(() => {
        if(!decoded) return;
        const now = Date.now() / 1000;
        if(decoded.exp < now) {
            refresh();
        }
    }, [decoded]);

    // Injecting current Auth context into the tree.
    // In App.tsx, <AuthProvider> tags will wrap the project route tree and serve as a global authentication boundary.
    return(
        <AuthContext.Provider
            value={{accessToken, refreshToken, decoded, login, logout, refresh}}
        >
            {children}
        </AuthContext.Provider>
    );
}
