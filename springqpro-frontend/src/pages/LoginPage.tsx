import { useRef, useState } from "react";
import { loginUser } from "../api/api.ts";
import { useAuth } from "../utility/auth/AuthContext";
import { useNavigate } from "react-router-dom";

export default function LoginPage() {
    const emailRef = useRef<HTMLInputElement>(null);
    const passRef = useRef<HTMLInputElement>(null);
    const [error, setError] = useState<string | null>(null);
    const { login } = useAuth();
    const navigate = useNavigate();

    // Login function:
    const handleLogin = async(e: React.FormEvent) => {
        e.preventDefault();
        const email = emailRef.current!.value;
        const password = passRef.current!.value;
        const result = await loginUser(email, password);
        if(result.accessToken) {
            login(result.accessToken, result.refreshToken);
            navigate("/token-dashboard");
        } else {
            setError("Invalid login credentials.");
        }
    };

    // DEBUG:+TO-DO:[BELOW] FLESH THIS OUT LATER!!!
    return(
        <div style={{ padding: 40, color: "#00FF41", fontFamily: "monospace" }}>
            <h2>Login to SpringQueuePro</h2>
            {error && <div style={{ color: "red" }}>{error}</div>}

            <form onSubmit={handleLogin}>
                <input ref={emailRef} type="email" placeholder="Email" style={{ margin: 8 }} />
                <input ref={passRef} type="password" placeholder="Password" style={{ margin: 8 }} />
                <button type="submit">Login</button>
            </form>

            <a href="/register" style={{ color: "#00FF41" }}>Create account →</a>
        </div>
    );
}
