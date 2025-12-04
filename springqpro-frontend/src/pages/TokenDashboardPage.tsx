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
// src/pages/TokenDashboardPage.tsx

import { useEffect, useState } from "react";
import { useAuth } from "../utility/auth/AuthContext";
import { refreshAccessToken, getRedisTokenStatus } from "../api/api";
import NavBar from "../components/NavBar";

interface DecodedJWT {
  sub: string;
  iat: number;
  exp: number;
  iss?: string;
  [key: string]: any;
}

export default function TokenDashboardPage() {
  const { accessToken, refreshToken, login } = useAuth();
  const [decoded, setDecoded] = useState<DecodedJWT | null>(null);
  const [expiryCountdown, setExpiryCountdown] = useState<string>("");
  const [redisStatus, setRedisStatus] = useState<string>("Checking...");
  const [refreshing, setRefreshing] = useState<boolean>(false);

  // Decoding JWT:
  const decodeJwt = (token: string): DecodedJWT | null => {
    try {
      const payload = token.split(".")[1];
      return JSON.parse(atob(payload));
    } catch {
      return null;
    }
  };
  useEffect(() => {
    if (accessToken) {
      setDecoded(decodeJwt(accessToken));
    }
  }, [accessToken]);

  // Expiry Countdown for Tokens (Quality of Life stuff):
  useEffect(() => {
    if (!decoded?.exp) return;

    const timer = setInterval(() => {
      const now = Math.floor(Date.now() / 1000);
      const diff = decoded.exp - now;

      if (diff <= 0) {
        setExpiryCountdown("Expired");
        clearInterval(timer);
        return;
      }

      const mins = Math.floor(diff / 60);
      const secs = diff % 60;
      setExpiryCountdown(`${mins}m ${secs}s`);
    }, 1000);

    return () => clearInterval(timer);
  }, [decoded]);

  // Redis fetch:
  useEffect(() => {
    if (!accessToken || !refreshToken) return;
    const fetchStatus = async () => {
      try {
        const res = await getRedisTokenStatus(accessToken, refreshToken);
        setRedisStatus(res.active ? "Active" : "Not Found");
      } catch {
        setRedisStatus("Error");
      }
    };
    fetchStatus();
  }, [accessToken, refreshToken]);

  // Manual refresh:
  const handleRefresh = async () => {
    if (!refreshToken) return;
    setRefreshing(true);

    const res = await refreshAccessToken(refreshToken);
    if (res.accessToken && res.refreshToken) {
      login(res.accessToken, res.refreshToken);
      setDecoded(decodeJwt(res.accessToken));
    } else {
      alert("Failed to refresh token.");
    }
    setRefreshing(false);
  };

  const mask = (t: string) => t.slice(0, 16) + "..." + t.slice(-8);

  // The code:
  return (
    <div
      style={{
        minHeight: "100vh",
        backgroundColor: "#f6f8fa",
        fontFamily: "monospace",
      }}
    >
      <NavBar />

      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          paddingTop: "40px",
        }}
      >
        {/* PAGE TITLE */}
        <h1 style={{ color: "#2d2d2d", marginBottom: "25px" }}>
          Authentication Overview
        </h1>

        {/* MAIN CARD */}
        <div
          style={{
            width: "480px",
            padding: "25px",
            backgroundColor: "#ffffff",
            border: "2px solid #6db33f",
            borderRadius: "12px",
            boxShadow: "0 0 10px rgba(109,179,63,0.25)",
            display: "flex",
            flexDirection: "column",
            gap: "16px",
          }}
        >
          <h2 style={{ color: "#6db33f", marginBottom: "6px" }}>
            Your Tokens
          </h2>

          <div>
            <b>Access Token:</b>
            <div style={{ marginTop: "3px", color: "#2d2d2d" }}>
              {accessToken ? mask(accessToken) : "None"}
            </div>
          </div>

          <div>
            <b>Refresh Token:</b>
            <div style={{ marginTop: "3px", color: "#2d2d2d" }}>
              {refreshToken ? mask(refreshToken) : "None"}
            </div>
          </div>

          {/* EXPIRY SECTION */}
          {decoded && (
            <>
              <hr style={{ borderColor: "#6db33f" }} />

              <div>
                <b>Issued At:</b>{" "}
                {new Date(decoded.iat * 1000).toLocaleString()}
              </div>

              <div>
                <b>Expires At:</b>{" "}
                {new Date(decoded.exp * 1000).toLocaleString()}
              </div>

              <div>
                <b>Time Remaining:</b>{" "}
                <span
                  style={{
                    color: expiryCountdown === "Expired" ? "red" : "#2d2d2d",
                  }}
                >
                  {expiryCountdown}
                </span>
              </div>
            </>
          )}

          {/* REDIS STATUS */}
          <hr style={{ borderColor: "#6db33f" }} />
          <div>
            <b>Redis Refresh Token Entry:</b>{" "}
            <span
              style={{
                color:
                  redisStatus === "Active"
                    ? "#6db33f"
                    : redisStatus === "Error"
                    ? "red"
                    : "#cc0000",
              }}
            >
              {redisStatus}
            </span>
          </div>

          {/* REFRESH BUTTON */}
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            style={{
              marginTop: "10px",
              padding: "10px",
              backgroundColor: refreshing ? "#85b577" : "#6db33f",
              border: "none",
              borderRadius: "6px",
              color: "#ffffff",
              fontWeight: "bold",
              cursor: "pointer",
              fontSize: "15px",
              boxShadow: "0 0 8px rgba(109,179,63,0.4)",
            }}
          >
            {refreshing ? "Refreshing..." : "Refresh Access Token"}
          </button>
        </div>

        {/* NAVIGATION OVERVIEW BOX */}
        <div
          style={{
            width: "520x",
            marginTop: "35px",
            padding: "20px",
            backgroundColor: "#ffffff",
            border: "2px solid #6db33f",
            borderRadius: "12px",
            boxShadow: "0 0 10px rgba(109,179,63,0.25)",
            color: "#2d2d2d",
          }}
        >
          <h2 style={{ marginBottom: "10px", color: "#6db33f" }}>
            Navigation Overview
          </h2>
          <ul style={{ lineHeight: "1.6" }}>
            <li>
              <b>Auth:</b> View tokens, expiry, Redis status, refresh flow. <b><i>You are here.</i></b>
            </li>
            <li>
              <b>Tasks:</b> Create tasks & query DB via GraphQL.
            </li>
            <li>
              <b>Processing:</b> Observe queue activity & task lifecycle.
            </li>
            <li>
              <b>System Health:</b> View Actuator health & metrics summary.
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}