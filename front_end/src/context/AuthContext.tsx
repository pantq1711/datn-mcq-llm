import { createContext, useContext, useState, type ReactNode, useEffect } from "react";
import { refresh } from "../features/auth/services/authApi";
import { getMe } from "../features/auth/services/authApi";
import type { UserInfo } from "../features/auth/types/auth.types";
import { AUTH_LOGOUT_EVENT } from "../services/apiClient";

interface AuthContextProps {
    token: string | null;
    setToken: (token: string | null) => void;
    user: UserInfo | null;
    setUser: (user: UserInfo | null) => void;
    /** Đang thử refresh lúc mở app (chưa có token lưu sẵn); RequireAuth chờ thay vì chuyển về đăng nhập. */
    initializing: boolean;
}

const AuthContext = createContext<AuthContextProps | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
    // Đọc token ngay khi khởi tạo: nếu đọc trong effect, lần render đầu RequireAuth thấy token null
    // và chuyển về trang đăng nhập trước khi kịp đặt token
    const [token, setToken] = useState<string | null>(() => localStorage.getItem("accessToken"));
    const [user, setUser] = useState<UserInfo | null>(null);
    const [initializing, setInitializing] = useState<boolean>(() => !localStorage.getItem("accessToken"));

    useEffect(() => {
        if (!initializing) return;
        // Không có token → cố gắng refresh bằng cookie
        (async () => {
            try {
                const { accessToken } = await refresh();
                setToken(accessToken);
            } catch (_) {
            } finally {
                setInitializing(false);
            }
        })();
    }, []);

    // apiClient báo refresh thất bại → xóa token, RequireAuth sẽ chuyển về trang đăng nhập
    useEffect(() => {
        const handleLogout = () => setToken(null);
        window.addEventListener(AUTH_LOGOUT_EVENT, handleLogout);
        return () => window.removeEventListener(AUTH_LOGOUT_EVENT, handleLogout);
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
                // Không lấy được thông tin người dùng (kể cả lỗi mạng) → đăng xuất, tránh Spin mãi ở RequireAuth
                console.error("Failed to fetch user info", e);
                localStorage.removeItem("accessToken");
                setToken(null);
            }
        })();
    }, [token]);

    return (
        <AuthContext.Provider value={{ token, setToken, user, setUser, initializing }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error("useAuth must be used within AuthProvider");
    return ctx;
};
