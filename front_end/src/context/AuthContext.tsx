import { createContext, useContext, useState, type ReactNode, useEffect } from "react";
import { refresh } from "../features/auth/services/authApi";
import { getMe } from "../features/auth/services/authApi";
import type { UserInfo } from "../features/auth/types/auth.types";

interface AuthContextProps {
    token: string | null;
    setToken: (token: string | null) => void;
    user: UserInfo | null;
    setUser: (user: UserInfo | null) => void;
}

const AuthContext = createContext<AuthContextProps | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
    const [token, setToken] = useState<string | null>(null);
    const [user, setUser] = useState<UserInfo | null>(null);

    useEffect(() => {
        const stored = localStorage.getItem("accessToken");
        if (stored) {
            setToken(stored);
        } else {
            // Không có token → cố gắng refresh
            (async () => {
                try {
                    const { accessToken } = await refresh();
                    setToken(accessToken);
                } catch (_) { }
            })();
        }
    }, []);

    useEffect(() => {
        if (!token) {
            setUser(null);
            return;
        }
        (async () => {
            try {
                const data = await getMe();
                setUser(data);
            } catch (e) {
                console.error("Failed to fetch user info", e);
                // Nếu 401 → logout tự động (có thể gọi logout())
            }
        })();
    }, [token]);

    return (
        <AuthContext.Provider value={{ token, setToken, user, setUser }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error("useAuth must be used within AuthProvider");
    return ctx;
};
