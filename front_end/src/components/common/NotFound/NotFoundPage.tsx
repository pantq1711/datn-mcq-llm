import { Button, Result } from "antd";
import { useNavigate } from "react-router-dom";
import Header from "../Header/Header";
import { useAuth } from "../../../context/AuthContext";
import { getHomePath } from "../../../utils/auth.utils";
import "./NotFoundPage.css";

/** Chữ nút quay về theo trang chủ của từng vai trò (cùng đích với logo trên thanh đầu trang). */
const HOME_LABELS: Record<string, string> = {
    "/": "Về trang Tổng quan",
    "/admin/users": "Về trang Quản trị tài khoản",
    "/auth/login": "Về trang Đăng nhập",
};

/**
 * Trang 404 (rà soát M1): có thanh đầu trang tên ứng dụng (dạng của trang đăng nhập, không có thanh bên và menu tài
 * khoản vì trang nằm ngoài bố cục chính); nút quay về trang chủ đúng vai trò.
 */
export const NotFoundPage = () => {
    const navigate = useNavigate();
    const { user } = useAuth();
    const homePath = getHomePath(user?.role);

    return (
        <div className="not-found-page">
            <Header variant="auth" />
            <div className="not-found-page-content">
                <Result
                    status="404"
                    title="Không tìm thấy trang"
                    subTitle="Trang này không có hoặc đã được chuyển chỗ. Thầy/cô kiểm tra lại đường dẫn."
                    extra={
                        <Button type="primary" onClick={() => navigate(homePath)}>
                            {HOME_LABELS[homePath] ?? "Về trang chủ"}
                        </Button>
                    }
                />
            </div>
        </div>
    );
};

export default NotFoundPage;
