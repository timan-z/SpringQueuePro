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

/* TO-DO:
- Can maybe map token rotation history to a User / tie to the backend at some point.
- This can also maybe appear in one of the Metrics pages too?
- Add a clear history button for my rotation history stuff.
*/

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

  // Access Token Metadata, etc:
  const [decodedAccess, setDecodedAccess] = useState<DecodedJWT | null>(null);
  const [accessCountdown, setAccessCountdown] = useState<string>("");
  const [redisStatus, setRedisStatus] = useState<string>("Checking...");
  const [refreshing, setRefreshing] = useState<boolean>(false);

  // Refresh Token Metadata, etc:
  const [decodedRefresh, setDecodedRefresh] = useState<DecodedJWT | null>(null);
  const [refreshCountdown, setRefreshCountdown] = useState("");
  const [integrityOk, setIntegrityOk] = useState<boolean | null>(null); // Token chain integrity check.
  const [rotationHistory, setRotationHistory] = useState<any[]>([]);  // Local rotation history.

  // Decoding JWT:
  const decodeJwt = (token: string): DecodedJWT | null => {
    try {
      const payload = token.split(".")[1];
      return JSON.parse(atob(payload));
    } catch {
      return null;
    }
  }

  // UseEffect hook that'll decode both Access + Refresh Tokens on mount/refresh:
  useEffect(() => {
    if (accessToken) setDecodedAccess(decodeJwt(accessToken));
    if (refreshToken) setDecodedRefresh(decodeJwt(refreshToken));
  }, [accessToken, refreshToken]);

  // Expiry Countdown for Access Tokens (Quality of Life stuff):
  useEffect(() => {
    if (!decodedAccess?.exp) return;

    const timer = setInterval(() => {
      const now = Math.floor(Date.now() / 1000);
      const diff = decodedAccess.exp - now;

      if (diff <= 0) {
        setAccessCountdown("Expired");
        clearInterval(timer);
        return;
      }

      const mins = Math.floor(diff / 60);
      const secs = diff % 60;
      setAccessCountdown(`${mins}m ${secs}s`);
    }, 1000);

    return () => clearInterval(timer);
  }, [decodedAccess]);

  // Expiry countdown for Refresh Tokens (Quality of Life stuff):
  useEffect(() => {
    if (!decodedRefresh?.exp) return;

    const timer = setInterval(() => {
      const now = Math.floor(Date.now() / 1000);
      const diff = decodedRefresh.exp - now;

      if (diff <= 0) {
        setRefreshCountdown("Expired");
        clearInterval(timer);
        return;
      }

      const mins = Math.floor(diff / 60);
      const secs = diff % 60;
      setRefreshCountdown(`${mins}m ${secs}s`);
    }, 1000);

    return () => clearInterval(timer);
  }, [decodedRefresh]);

  // Redis Status Fetch:
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

  // Token Chain Integrity Check:
  useEffect(() => {
    if (!decodedAccess || !decodedRefresh) return;

    const ok = decodedAccess.sub === decodedRefresh.sub;
    setIntegrityOk(ok);
  }, [decodedAccess, decodedRefresh]);

  // Manual refresh AND rotation history:
  const handleRefresh = async () => {
    if (!refreshToken) return;
    setRefreshing(true);

    const res = await refreshAccessToken(refreshToken);
    if (res.accessToken && res.refreshToken) {
      // 2025-12-05-NOTE:+ADDITION: Now saving rotation history:
      const historyEntry = {
        time: new Date().toLocaleString(),
        oldRefresh: refreshToken,
        newRefresh: res.refreshToken,
      };
      const newHistory = [...rotationHistory, historyEntry];
      if(newHistory.length > 6) {
        newHistory.shift();
      }
      setRotationHistory(newHistory);
      localStorage.setItem("rotationHistory", JSON.stringify(newHistory));

      // login update
      login(res.accessToken, res.refreshToken);
      // Decode new tokens:
      setDecodedAccess(decodeJwt(res.accessToken));
      setDecodedRefresh(decodeJwt(res.refreshToken));
    } else {
      alert("Failed to refresh token.");
    }
    setRefreshing(false);
  };

  // ROTATION HISTORY ADDITION: Load history from localStorage on mount
  useEffect(() => {
    const stored = localStorage.getItem("rotationHistory");
    if (stored) setRotationHistory(JSON.parse(stored));
  }, []);

  // For masking both tokens:
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
            width: "520px",
            padding: "25px",
            backgroundColor: "#ffffff",
            border: "2px solid #6db33f",
            borderRadius: "12px",
            boxShadow: "0 0 10px rgba(109,179,63,0.3)",
            marginBottom:"40px"
            /*display: "flex",
            flexDirection: "column",
            gap: "16px",*/
          }}
        >
          <h2 style={{ color: "#6db33f" /*, marginBottom: "6px"*/}}>Your Tokens</h2>

          {/*<div>*/}
          <b>Access Token:</b>
          {/*<div style={{ marginTop: "3px", color: "#2d2d2d" }}>*/}
          <div style={{ marginBottom: "10px" }}>
            {accessToken ? mask(accessToken) : "None"}
          </div>
          {/*</div>*/}

          {/*<div>*/}
          <b>Refresh Token:</b>
          {/*<div style={{ marginTop: "3px", color: "#2d2d2d" }}>*/}
          <div style={{ marginBottom: "10px" }}>
            {refreshToken ? mask(refreshToken) : "None"}
          </div>
          {/*</div>*/}

          {/* EXPIRY SECTION */}
          {decodedAccess && (
            <>
              <hr style={{ borderColor: "#6db33f" }} />

              <b>Access Issued At:</b>{" "}
              {new Date(decodedAccess.iat * 1000).toLocaleString()}
              <br/>

              <b>Access Expires At:</b>{" "}
              {new Date(decodedAccess.exp * 1000).toLocaleString()}
              <br/>

              {/*<div>*/}
              <b>Access Time Remaining:</b>{" "}
              <span style={{ color: accessCountdown === "Expired" ? "red" : "#2d2d2d"}}>
                {accessCountdown}
              </span>
              {/*</div>*/}
            </>
          )}

          {decodedRefresh && (
            <>
              <hr style={{ borderColor: "#6db33f" }} />

              <h3 style={{ color: "#6db33f" }}>Refresh Token Details</h3>

              <b>Refresh Issued At:</b>{" "}
              {new Date(decodedRefresh.iat * 1000).toLocaleString()}
              <br />

              <b>Refresh Expires At:</b>{" "}
              {new Date(decodedRefresh.exp * 1000).toLocaleString()}
              <br />

              <b>Refresh Time Remaining:</b>{" "}
              <span style={{ color: refreshCountdown === "Expired" ? "red" : "#2d2d2d" }}>
                {refreshCountdown}
              </span>
            </>
          )}

          {/* REDIS STATUS */}
          <hr style={{ borderColor: "#6db33f" }} />
          {/*<div>*/}
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
          {/*</div>*/}
          
          {/* TOKEN CHAIN INTEGRITY: */}
          {decodedAccess && decodedRefresh && (
            <>
              <hr style={{ borderColor: "#6db33f" }} />
              <b>Token Chain Integrity:</b>{" "}
              {integrityOk ? (
                <span style={{ color: "#6db33f" }}>Valid (Subjects Match)</span>
              ) : (
                <span style={{ color: "red" }}>INVALID! Subjects Differ</span>
              )}
            </>
          )}

          {/* REFRESH BUTTON */}
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            style={{
              marginTop: "14px",
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

        {/* ROTATION HISTORY CARD: */}
        {rotationHistory.length > 0 && (
          <div
            style={{
              width: "520px",
              backgroundColor: "#ffffff",
              border: "2px solid #6db33f",
              borderRadius: "12px",
              padding: "20px",
              marginBottom: "40px",
              boxShadow: "0 0 10px rgba(109,179,63,0.25)",
            }}
          >
            <h2 style={{ color: "#6db33f" }}>Refresh Token Rotation History</h2>
            <p>(Last 6 Most Recent Token Rotations)</p>

            <div style={{
              maxHeight: "180px",
              overflowY: "auto",
              paddingRight: "10px",
              marginTop: "12px",
            }}>
              <ul style={{ paddingLeft:"20px" }}>
                {rotationHistory.map((entry, i) => (
                  <li key={i} style={{ marginBottom: "10px" }}>
                    <b>{entry.time}</b>  
                    <br />
                    old: {mask(entry.oldRefresh)}  
                    <br />
                    new: {mask(entry.newRefresh)}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}

        {/* NAVIGATION OVERVIEW BOX */}
        <div
          style={{
            width: "520x",
            /*marginTop: "35px",*/
            padding: "20px",
            backgroundColor: "#ffffff",
            border: "2px solid #6db33f",
            borderRadius: "12px",
            boxShadow: "0 0 10px rgba(109,179,63,0.25)",
            color: "#2d2d2d",
          }}
        >
          <h2 style={{ color: "#6db33f" }}>Navigation Overview</h2>
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