import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../../../context/AuthContext";
import { Spin } from "antd";

interface RequireAuthProps {
    allowedRoles?: string[];
}

export const RequireAuth = ({ allowedRoles }: RequireAuthProps) => {
    const { token, user } = useAuth();

    if (!token) {
        return <Navigate to="/auth/login" replace />;
    }

    // Nếu route yêu cầu vai trò cụ thể
    if (allowedRoles && allowedRoles.length > 0) {
        // Đợi tải xong thông tin user
        if (!user) {
            return (
                <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh" }}>
                    <Spin size="large" />
                </div>
            );
        }

        const userRole = user.role?.toLowerCase();
        const isAllowed = allowedRoles.some((role) => role.toLowerCase() === userRole);

        if (!isAllowed) {
            // Không đủ quyền -> chuyển về trang chủ
            return <Navigate to="/" replace />;
        }
    }

    return <Outlet />;
};
