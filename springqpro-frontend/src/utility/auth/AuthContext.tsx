import React, { createContext, useContext, useEffect, useState} from "react";
import { jwtDecode } from "jwt-decode";
import { refreshAccessToken, logoutUser } from "../../api/api";

/* NOTE(S)-TO-SELF (worth noting down on in my notes):
- React createContext allows you to create a "context" object which lets you share data (e.g., state, functions,
and other values) across other components w/o needing to manually pass props down every level of the component tree.
*/

interface DecodedToken {
    sub: string;
    exp: number;
    iat: number;
}

interface AuthContextType {
    accessToken: string | null;
    refreshToken: string | null;
    decoded: DecodedToken | null;
    login: (access: string, refresh: string) => void;
    logout: () => Promise<void>;
    refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>(null!);
export const useAuth = () => useContext(AuthContext);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [accessToken, setAccessToken] = useState<string | null>(localStorage.getItem("accessToken"));
    const [refreshToken, setRefreshToken] = useState<string | null>(localStorage.getItem("refreshToken"));
    const [decoded, setDecoded] = useState<DecodedToken | null>(accessToken ? jwtDecode(accessToken) : null);

    const login = (access: string, refresh: string) => {
        setAccessToken(access);
        setRefreshToken(refresh);
        setDecoded(jwtDecode(access));
        localStorage.setItem("accessToken", access);
        localStorage.setItem("refreshToken", refresh); 
    };
    const logout = async() => {
        try {
            if(refreshToken) await logoutUser(refreshToken);
        } catch { }
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
        setAccessToken(null);
        setRefreshToken(null);
        setDecoded(null);
    }
    const refresh = async() => {
        if(!refreshToken) return;
        const result = await refreshAccessToken(refreshToken);
        if(result?.accessToken) {
            login(result.accessToken, result.refreshToken);
        } else {
            await logout();
        }
    };
    // Automatic refresh if access token expires:
    useEffect(() => {
        if(!decoded) return;
        const now = Date.now() / 1000;
        if(decoded.exp < now) {
            refresh();
        }
    }, [decoded]);

    return(
        <AuthContext.Provider
            value={{accessToken, refreshToken, decoded, login, logout, refresh}}
        >
            {children}
        </AuthContext.Provider>
    );
}
