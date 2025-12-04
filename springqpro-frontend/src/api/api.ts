export const API_BASE = import.meta.env.VITE_API_BASE.replace(/\/+$/, "");

// Auth-related API calls to AuthenticationController.java in the backend:
// [1] - /auth/register
export async function registerUser(email: string, password: string) {
    const res = await fetch(`${API_BASE}/auth/register`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({ email, password})
    });
    return res.json();
}

// [2] - /auth/login
export async function loginUser(email: string, password: string) {
    const res = await fetch(`${API_BASE}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password })
    });
    return res.json();
}

// [3] - /auth/refresh
export async function refreshAccessToken(refreshToken: string) {
    const res = await fetch(`${API_BASE}/auth/refresh`, {
        method: "POST",
        headers: {"Content-Type":"application/json"},
        body: JSON.stringify({refreshToken})
    });
    return res.json();
}

// [4] - /auth/refresh-status
export async function getRedisTokenStatus(accessToken: string, refreshToken: string) {
    const url = `${API_BASE}/auth/refresh-status?refreshToken=${encodeURIComponent(refreshToken)}`;
    const res = await fetch(url, {
        method: "GET",
        headers: {
            "Authorization": `Bearer ${accessToken}`,
            "Content-Type": "application/json"
        }
    });
    return res.json();
}

// [5] - /auth/logout
export async function logoutUser(refreshToken: string) {
    const res = await fetch(`${API_BASE}/auth/logout`, {
        method: "POST",
        headers: {"Content-Type":"application/json"},
        body: JSON.stringify({refreshToken})
    });
    return res.json();
}
