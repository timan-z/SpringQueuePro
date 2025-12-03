import { useState, useEffect, useRef } from "react";
import { loginUser } from "../api/api.ts";
import { useAuth } from "../utility/auth/AuthContext";
import { useNavigate } from "react-router-dom";

/* 2025-12-02-NOTE: To be frank, I'm on a time crunch so I'm just going to be copying
my Login Page design from my "Hack MD Clone" CMDE Project and adjusting it slightly aesthetically.
TO-DO: Maybe replace this with a more original design later - make a new branch and overhaul the frontend
whenever I've got the time to work on my atrocious frontend HTML/CSS skills (make this look nice).
*/
export default function LoginPage() {
    const emailRef = useRef<HTMLInputElement>(null);
    const passRef = useRef<HTMLInputElement>(null);
    const signInBtnRef = useRef<HTMLButtonElement>(null);
    const [emailError, setEmailError] = useState(false);
    const [passwordError, setPasswordError] = useState(false);
    const [serverError, setServerError] = useState<string | null>(null);
    const { login } = useAuth();
    const navigate = useNavigate();

    // Validate fields are filled:
    const checkFormsFilled = () => {
        const email = emailRef.current?.value ?? "";
        const pass = passRef.current?.value ?? "";
        if(email.trim() === "") {
            setEmailError(true);
            setTimeout(() => setEmailError(false), 2200);
            return false;
        }
        if(pass.trim() === "") {
            setPasswordError(true);
            setTimeout(() => setPasswordError(false), 2200);
            return false;
        }
        return true;
    };

    // ENTER key submits login:
    useEffect(() => {
        const handleEnterKey = (e: KeyboardEvent) => {
            if (e.key === "Enter") signInBtnRef.current?.click();
        };

        const emailInput = emailRef.current;
        const passInput = passRef.current;
        if (emailInput) emailInput.addEventListener("keydown", handleEnterKey);
        if (passInput) passInput.addEventListener("keydown", handleEnterKey);

        return () => {
            if (emailInput) emailInput.removeEventListener("keydown", handleEnterKey);
            if (passInput) passInput.removeEventListener("keydown", handleEnterKey);
        };
    }, []);

    const handleLogin = async(e: React.FormEvent) => {
        e.preventDefault();
        setServerError(null);
        if(!checkFormsFilled()) return;
        const email = emailRef.current!.value;
        const password = passRef.current!.value;

        try {
            const result = await loginUser(email, password);
            if (result.accessToken) {
                login(result.accessToken, result.refreshToken);
                navigate("/token-dashboard");
            } else {
                setServerError("Invalid email or password.");
            }
        } catch {
            setServerError("[Server/Network Error] Login failed unexpectedly. Try again.");
        }
    }
    
    return(
        <div style={{
            height: "100vh",
            width: "100%",
            backgroundColor: "#f6f8fa",
            fontFamily: "monospace",
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            paddingTop: "40px"
        }}>
            {/* Outer-most <div> element containing the "Sign in to SoringQueuePro" box. Should be centered in the middle of the screen: */} 
            <div style={{
                width: "380px",
                padding: "30px",
                backgroundColor: "#ffffff",
                border: "2px solid #6db33f",
                borderRadius: "10px",
                boxShadow: "0 0 10px rgba(109,179,63,0.4)",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                gap: "20px"
            }}>
                <h1 style={{ marginBottom: "0px", fontSize: "1.4rem", color: "#2d2d2d" }}>
                    Sign in to <span style={{ color: "#6db33f" }}>SpringQueuePro</span>
                </h1>
    
                {serverError && (
                    <div style={{ color: "#cc0000", fontSize: "14px" }}>{serverError}</div>
                )}

                <form
                    onSubmit={handleLogin}
                    style={{
                        width: "100%",
                        display: "flex",
                        flexDirection: "column",
                        alignItems: "center"
                }}>
                    {/* Email field */}
                    <div style={{ width: "100%", marginBottom: "10px" }}>
                        <label style={{ fontSize: "16px", marginBottom: "5px", display: "block" }}>Email</label>

                        {emailError && (
                            <div style={{ color: "#cc0000", marginBottom: "5px", fontSize: "13px" }}>
                                ⚠ Please enter an email.
                            </div>
                        )}

                        <input ref={emailRef} type="email" placeholder="email@example.com"
                        style={{
                            width: "100%",
                            padding: "8px",
                            border: "1px solid #6db33f",
                            borderRadius: "4px",
                            color: "#2d2d2d"
                        }}/>
                    </div>

                    {/* Password field */}
                    <div style={{ width: "100%", marginBottom: "15px" }}>
                        <label style={{ fontSize: "16px", marginBottom: "5px", display: "block" }}>Password</label>

                        {passwordError && (
                            <div style={{ color: "#cc0000", marginBottom: "5px", fontSize: "13px" }}>
                                ⚠ Please enter a password.
                            </div>
                        )}

                        <input ref={passRef} type="password" placeholder="Password"
                        style={{
                            width: "100%",
                            padding: "8px",
                            border: "1px solid #6db33f",
                            borderRadius: "4px",
                            color: "#2d2d2d"
                        }}/>
                    </div>

                    {/* Sign in button */}
                    <button
                        ref={signInBtnRef}
                        type="submit"
                        style={{
                        width: "100%",
                        padding: "10px",
                        backgroundColor: "#6db33f",
                        border: "none",
                        borderRadius: "6px",
                        color: "#ffffff",
                        fontWeight: "bold",
                        cursor: "pointer",
                        fontSize: "15px",
                        boxShadow: "0 0 8px rgba(109,179,63,0.5)"
                    }}>
                        Sign In
                    </button>
                </form>

                {/* Create account */}
                <div style={{ marginTop: "10px", fontSize: "14px" }}>
                    New to SpringQueuePro?{" "}
                    <a href="/register"
                    style={{
                        color: "#6db33f",
                        fontWeight: "bold",
                        textDecoration: "none"
                    }}>
                        Create an account →
                    </a>
                </div>
            </div>
        </div>
    );
}
