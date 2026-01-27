/* TokenDashboardPage.tsx:
--------------------------
This page is basically a read-only + light-interaction diagnostic page that visualizes the
current authentication state (owned by AuthProvider) for this current user.
- This page does not "own" authentication, it simply observes its state with some light interaction (invoke refresh).
- It derives UI state directly from tokens.
TokenDashboardPage is a data consumer, not an owner.
***
This is the post-login landing page that exists to give the user a peek "under the hood" as to how the 
authentication and authorization setup works for this system (a glimpse into the backend).
*/

import { useEffect, useState } from "react";
import { useAuth } from "../utility/auth/AuthContext";
import { refreshAccessToken, getRedisTokenStatus } from "../api/api";
import NavBar from "../components/NavBar";

/* EVENTUAL TO-DO:
- Write a JWT Debugger file that parses JWT Payload locally and displays internal info.
- Can maybe map token rotation history to a User / tie to the backend at some point.
- This can also maybe appear in one of the Metrics pages too?
- Add a clear history button for my rotation history stuff.
***
2026-01-27-NOTE: I've made the token rotation history user-scoped on the client-side only (no PostgreSQL persistence, etc).
The feature is entirely cosmetic, extremely misc, and purely for demo visualization purposes. In production, it would be
backed by a user-scoped audit table, but remember that the point of this page is observability, not correctness.
*/

// Manually parsing the JWT payload instead of trusting jwt-decode:
interface DecodedJWT {
  sub: string;
  iat: number;
  exp: number;
  iss?: string;
  [key: string]: any;
}

export default function TokenDashboardPage() {
  const { accessToken, refreshToken, login } = useAuth(); // TokenDashboardPage doens't know how login works, but needed for successful Refresh.

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

  // User-scoped localStorage key for rotation history (cosmetic and purely for frontend/demo purposes):
  const rotationStorageKey = decodedAccess?.sub ? `rotationHistory:${decodedAccess.sub}` : null;

  // Manual JWT decoding helper:
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

  // Expiry Countdown for Access Tokens (cosmetic frontend stuff):
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

  // Expiry Countdown for Refresh Tokens (cosmetic frontend stuff):
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

  // UseEffect hook for Redis Token Status Fetch:
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

  // UseEffect hook for Token Chain Integrity Check:
  /* NOTE: Impressive feature to highlight in interviews, etc.
  - Demonstrates access & refresh tokens are linked.
  - Shows rotation preserves identity.
  - Illustrates that authentication here is a chain, not isolated tokens. (Link this to production-grade stuff).
  */
  useEffect(() => {
    if (!decodedAccess || !decodedRefresh) return;

    const ok = decodedAccess.sub === decodedRefresh.sub;
    setIntegrityOk(ok);
  }, [decodedAccess, decodedRefresh]);

  // Manual Token Refresh Handler Method - This is the method that directly invokes the backend on this observability-focused page:
  const handleRefresh = async () => {
    if (!refreshToken) return;
    setRefreshing(true);

    const res = await refreshAccessToken(refreshToken);
    if (res.accessToken && res.refreshToken) {
      // Steps below are relevant to the Rotation History section:
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
      if (rotationStorageKey) {
        localStorage.setItem(rotationStorageKey, JSON.stringify(newHistory));
      }

      // Steps below are relevant to this method's main refresh functionality:

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
    if (!rotationStorageKey) return;

    const stored = localStorage.getItem(rotationStorageKey);
    if (stored) {
      setRotationHistory(JSON.parse(stored));
    } else {
      setRotationHistory([]);
    }
  }, [rotationStorageKey]);

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

              <h3 style={{ color: "#6db33f" }}>Access Token Details</h3>

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
          <div style={{display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px",}}>
            <h2 style={{ color: "#6db33f", margin: 0 }}>
              Refresh Token Rotation History
            </h2>
            {/* Button to manually clear the Refrehs Token Rotation History contents: */}
            <button
              onClick={() => {
                if (rotationStorageKey) {
                  localStorage.removeItem(rotationStorageKey);
                  setRotationHistory([]);
                }
              }}
              style={{
                width: "75px",
                padding: "6px 10px",
                fontSize: "12px",
                backgroundColor: "#f3f4f6",
                border: "1px solid #6db33f",
                borderRadius: "6px",
                cursor: "pointer",
                fontFamily: "monospace",
              }}
            >
              Clear
            </button>
          </div>

          <p>(Last 6 Most Recent Token Rotations)</p>

          {rotationHistory.length > 0 && (

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
          )}
        </div>

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