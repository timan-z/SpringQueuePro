import { useRef, useState } from "react";
import { registerUser } from "../api/api.ts";
import { useNavigate } from "react-router-dom";

export default function RegisterPage() {
    const emailRef = useRef<HTMLInputElement>(null);
    const passRef = useRef<HTMLInputElement>(null);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate();

    const handleRegister = async(e: React.FormEvent) => {
        e.preventDefault();
        const email = emailRef.current!.value;
        const password = passRef.current!.value;
        const result = await registerUser(email, password);
        if(result.status === "registered") {
            navigate("/login");
        } else {
            setError(result.error || "Registration failed");
        }
    };

    return (
        <div style={{ padding: 40, color: "#00FF41", fontFamily: "monospace" }}>
            <h2>Create SpringQueuePro Account</h2>
            {error && <div style={{ color: "red" }}>{error}</div>}

            <form onSubmit={handleRegister}>
                <input ref={emailRef} type="email" placeholder="Email" style={{ margin: 8 }} />
                <input ref={passRef} type="password" placeholder="Password" style={{ margin: 8 }} />
                <button type="submit">Register</button>
            </form>

            <a href="/login" style={{ color: "#00FF41" }}>Already have an account? Log in →</a>
        </div>
    );
}
