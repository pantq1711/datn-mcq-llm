import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../../../context/AuthContext";
import { Spin } from "antd";
import { getHomePath } from "../../../utils/auth.utils";

interface RequireAuthProps {
    allowedRoles?: string[];
}

export const RequireAuth = ({ allowedRoles }: RequireAuthProps) => {
    const { token, user, initializing } = useAuth();

    const loading = (
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh" }}>
            <Spin size="large" />
        </div>
    );

    // Đang thử refresh lúc mở app: chờ kết quả rồi mới quyết định
    if (!token && initializing) {
        return loading;
    }

    if (!token) {
        return <Navigate to="/auth/login" replace />;
    }

    // Nếu route yêu cầu vai trò cụ thể
    if (allowedRoles && allowedRoles.length > 0) {
        // Đợi tải xong thông tin user
        if (!user) {
            return loading;
        }

        const userRole = user.role?.toLowerCase();
        const isAllowed = allowedRoles.some((role) => role.toLowerCase() === userRole);

        if (!isAllowed) {
            // Không đủ quyền -> về trang chủ của vai trò đó (luôn là trang vai trò này được vào, nên không lặp)
            return <Navigate to={getHomePath(user.role)} replace />;
        }
    }

    return <Outlet />;
};