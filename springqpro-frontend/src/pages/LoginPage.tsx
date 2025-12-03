import { useRef, useState } from "react";
import { loginUser } from "../api/api.ts";
import { useAuth } from "../utility/auth/AuthContext";
import { useNavigate } from "react-router-dom";
import NavBar from "../components/NavBar";

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

    /* 2025-12-02-NOTE: To be frank, I'm on a time crunch so I'm just going to be copying
    my Login Page design from my "Hack MD Clone" CMDE Project and adjusting it slightly aesthetically.
    TO-DO: Maybe replace this with a more original design later - make a new branch and overhaul the frontend
    whenever I've got the time to work on my atrocious frontend HTML/CSS skills (make this look nice).
    */
    return(
        <div>
            {/* NOTE: Remove NavBar after I have it looking nice -- it's not supposed to be on the LoginPage!!! */}
            <NavBar />





        </div>
    );
}
