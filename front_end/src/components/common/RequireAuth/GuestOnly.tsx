import { Navigate, Outlet } from "react-router-dom";
import { Spin } from "antd";
import { useAuth } from "../../../context/AuthContext";
import { getHomePath } from "../../../utils/auth.utils";

/** Trang chỉ dành cho người chưa đăng nhập (đăng nhập, đăng ký, quên mật khẩu): đã đăng nhập thì về trang chủ theo vai trò. */
export const GuestOnly = () => {
    const { token, user, initializing } = useAuth();

    // Chưa có token và không còn thử refresh: là khách
    if (!token && !initializing) {
        return <Outlet />;
    }

    // Đang refresh lúc mở app hoặc đang tải /me (lỗi thì AuthContext xóa token và form hiện lại)
    if (!user) {
        return (
            <div style={{ display: "flex", justifyContent: "center", padding: "48px 0" }}>
                <Spin size="large" />
            </div>
        );
    }

    const homePath = getHomePath(user.role);
    // Vai trò không có trang web (homePath là trang đăng nhập): để nguyên, tránh chuyển hướng về chính nó
    if (homePath === "/auth/login") {
        return <Outlet />;
    }

    return <Navigate to={homePath} replace />;
};
