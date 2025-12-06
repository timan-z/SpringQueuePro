import React, { useRef, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { registerUser } from "../api/api";

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/* 2025-12-02-NOTE: To be frank, I'm on a time crunch so I'm just going to be copying
my Register Page design from my "Hack MD Clone" CMDE Project and adjusting it slightly aesthetically.
TO-DO: Maybe replace this with a more original design later - make a new branch and overhaul the frontend
whenever I've got the time to work on my atrocious frontend HTML/CSS skills (make this look nice).
*/
export default function RegisterPage() {
    const emailRef = useRef<HTMLInputElement | null>(null);
    const passwordRef = useRef<HTMLInputElement | null>(null);
    const [emailError, setEmailError] = useState<string | null>(null);
    const [passwordError, setPasswordError] = useState<string | null>(null);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const navigate = useNavigate();

    const showTransient = (
        setter: React.Dispatch<React.SetStateAction<string | null>>,
        message: string
    ) => {
        setter(message);
        setTimeout(() => setter(null), 2500);
    };

    // EDIT: I now realize that <input> w/ type="email" handles most of this itself, but carrying these over too anyways.
    const validateForm = (): boolean => {
        const email = emailRef.current?.value.trim() ?? "";
        const password = passwordRef.current?.value ?? "";
        if (!email) {
            showTransient(setEmailError, "Please enter an email.");
            return false;
        }
        if (!emailRegex.test(email)) {
            showTransient(setEmailError, "Please enter a valid email (must contain @ and a domain).");
            return false;
        }
        if (!password) {
            showTransient(setPasswordError, "Please enter a password.");
            return false;
        }
        if (password.length < 8) {
            showTransient(setPasswordError, "Password must be at least 8 characters long.");
            return false;
        }
        return true;
    };

    const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        setSubmitError(null);
        if (!validateForm()) return;

        const email = emailRef.current!.value.trim();
        const password = passwordRef.current!.value;

        try {
            setIsSubmitting(true);
            const result = await registerUser(email, password);

            // Adjust this check depending on how your API wrapper is implemented
            if (result?.status === "registered") {
                // NOTE:+TO-DO: When I have the time I want a pop-up box in the top-right or bottom-right corner that goes "You've successfully registered!" (disappears after).
                navigate("/login");
            } else {
                const msg = result?.error || result?.message || "Registration failed. Please try again.";
                showTransient(setSubmitError, msg);
            }
        } catch (err) {
            console.error("Registration error", err);
            showTransient(setSubmitError, "Unexpected error during registration. Please try again.");
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div style={{
            height: "100vh",
            width: "100%",
            backgroundColor: "#f6f8fa",
            fontFamily: "monospace",
            display: "flex",
            justifyContent: "center",
            alignItems: "center"
        }}>
            {/* The Register Card: */}
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
                <h1 style={{ 
                    marginBottom: "0px", 
                    fontSize: "1.3rem", 
                    color: "#2d2d2d" 
                }}>
                    Create a <span style={{ color: "#6db33f" }}>SpringQueuePro</span> account
                </h1>

                {submitError && (
                    <div
                    style={{
                        width: "100%",
                        marginTop: "4px",
                        padding: "8px 10px",
                        backgroundColor: "#450a0a",
                        borderRadius: "6px",
                        border: "1px solid #cc0000",
                        color: "#cc0000",
                        fontSize: "13px",
                    }}>
                        {submitError}
                    </div>
                )}

                <form
                    onSubmit={handleSubmit}
                    style={{
                    width: "100%",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "stretch",
                    gap: "16px",
                    marginTop: "8px",
                }}>
                    {/* Email field */}
                    <div style={{ width: "100%" }}>
                        <label
                            htmlFor="register-email"
                            style={{
                            display: "block",
                            marginBottom: "4px",
                            fontSize: "16px",
                            fontWeight: 600,
                            color: "#6db33f",
                        }}>
                            Email<span style={{ color: "#f97373" }}> *</span>
                        </label>

                        {emailError && (
                            <div
                            style={{
                                color: "#cc0000",
                                fontSize: "13px",
                                marginBottom: "4px",
                            }}
                            >
                            ⚠ {emailError}
                            </div>
                        )}

                        <input
                            id="register-email"
                            ref={emailRef}
                            type="email"
                            placeholder="sample_email@example.com"
                            style={{
                            width: "100%",
                            padding: "8px 10px",
                            borderRadius: "4px",
                            border: "1px solid #6db33f",
                            backgroundColor: "white",
                            color: "#2d2d2d",
                            fontFamily: "inherit",
                            fontSize: "14px",
                            outline: "none",
                        }}/>
                    </div>

                    {/* Password field */}
                    <div style={{ width: "100%" }}>
                        <label
                            htmlFor="register-password"
                            style={{
                            display: "block",
                            marginBottom: "4px",
                            fontSize: "16px",
                            fontWeight: 600,
                            color: "#6db33f",
                            }}
                        >
                            Password<span style={{ color: "#f97373" }}> *</span>
                        </label>

                        {passwordError && (
                            <div style={{
                                color: "#cc0000",
                                fontSize: "13px",
                                marginBottom: "4px",
                            }}>
                                ⚠ {passwordError}
                            </div>
                        )}

                        <input
                            id="register-password"
                            ref={passwordRef}
                            type="password"
                            placeholder="Make it at least 8 characters"
                            style={{
                            width: "100%",
                            padding: "8px 10px",
                            borderRadius: "4px",
                            border: "1px solid #6db33f",
                            backgroundColor: "white",
                            color: "#2d2d2d",
                            fontFamily: "inherit",
                            fontSize: "14px",
                            outline: "none",
                        }}/>

                        <div style={{
                            fontSize: "12px",
                            marginTop: "4px",
                            color: "#9ca3af",
                        }}>
                            Password must be at least 8 characters. No other rules.
                        </div>
                    </div>

                    {/* Submit button */}
                    <button
                    type="submit"
                    disabled={isSubmitting}
                    style={{
                        marginTop: "4px",
                        padding: "10px 12px",
                        width: "100%",
                        backgroundColor: isSubmitting ? "#568c2f" : "#6db33f",
                        color: "#ffffff",
                        border: "none",
                        borderRadius: "6px",
                        boxShadow: "0 0 8px rgba(109,179,63,0.5)",
                        cursor: isSubmitting ? "default" : "pointer",
                        fontWeight:"bold",
                        letterSpacing: "0.03em",
                        fontFamily: "monospace",
                        fontSize: "15px",
                        transition: "background-color 0.15s ease",
                    }}>
                    {isSubmitting ? "Registering..." : "REGISTER"}
                    </button>
                </form>

                <div style={{
                    width: "100%",
                    textAlign: "right",
                    fontSize: "14px",
                    marginBottom: "8px",
                }}>
                    Already have an account?{" "}
                    <Link
                        to="/login"
                        style={{
                        textDecoration: "none",
                        color: "#6db33f",
                        fontWeight: "bold",
                    }}>
                        Sign in →
                    </Link>
                </div>
            </div>
        </div>
    );
}
