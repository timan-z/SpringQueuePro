/* [INFORMATION]:
I'm going to have a LoginPage.tsx and RegisterPage.tsx page. RegisterPage, upon success, redirects the user to the LoginPage.
I want it so that if the LoginPage succeeds, it'll remap to this TokenDashboardPage.tsx page. It'll be this page that showcases:
- Refresh Token (w/ first 16 chars... masked)
- Refresh Token (w/ first 16 chars... masked)
- Expiry (read from JWT Payload)
"Token will auto-refresh when expired" <-- DEBUG: I can't remember if I have this.
This page basically flexes the whole authentication pipeline.

Have buttons for:
- Refresh Access Token manually.
- Logout (for invalidating refresh token via Redis). [will redirect to the LoginPage]
- Show Redis token store status (e.g., "Active refresh token stored")

TO-DO: Write a JWT Debugger file that parses JWT Payload locally and displays internal info.
*/
import { useAuth } from "../utility/auth/AuthContext";
import { jwtDecode } from "jwt-decode";
import { useEffect, useState } from "react";

export default function TokenDashboardPage() {
    const { accessToken, refreshToken, decoded, refresh, logout } = useAuth();
    const [redisStatus, setRedisStatus] = useState<string>("checking...");

    // Fetch Redis token store status:
    useEffect(() => {
        (async () => {
            if (!refreshToken) return setRedisStatus("no token");
            const res = await fetch(`/actuator/health`);
            const json = await res.json();
            setRedisStatus(json?.components?.redis?.status || "unknown");
        })();
    }, [refreshToken]);

    const masked = (t: string | null) => t ? t.substring(0, 16) + "..." : "N/A";

    return (
        <div style={{ padding: 40, color: "#00FF41", fontFamily: "monospace" }}>
        <h1>Authentication Dashboard</h1>
        <p>Logged in as: <b>{decoded?.sub}</b></p>

        <h2>Access Token</h2>
        <p>{masked(accessToken)}</p>
        <p>Expires: {decoded?.exp ? new Date(decoded.exp * 1000).toString() : "N/A"}</p>

        <h2>Refresh Token</h2>
        <p>{masked(refreshToken)}</p>
        <p>Redis status: {redisStatus}</p>

        <button onClick={refresh} style={{ marginRight: 10 }}>Refresh Access Token</button>
        <button onClick={logout}>Logout</button>

        <hr/>

        <h3>Test Protected API</h3>
        <button
            onClick={async () => {
            const res = await fetch("/graphql", {
                method: "POST",
                headers: {
                "Content-Type": "application/json",
                Authorization: `Bearer ${accessToken}`,
                },
                body: JSON.stringify({ query: "{ tasks { id } }" }),
            });
            alert("GraphQL response: " + (await res.text()));
            }}
        >
            Run Test Query
        </button>
        </div>
    );
}
