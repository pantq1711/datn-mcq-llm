import { Alert, Button, Typography } from "antd";
import { Link } from "react-router-dom";
import "./ForgotPasswordPage.css";

const { Title } = Typography;

/**
 * Backend không có API quên mật khẩu hay gửi email: chỉ admin đặt lại mật khẩu (docs/api mục 8, 8.1).
 * Trang này chỉ hướng dẫn liên hệ quản trị viên.
 */
export default function ForgotPasswordPage() {
    return (
        <div className="forgot-password">
            <div className="forgot-password-header">
                <Title level={2} className="forgot-password-title">
                    Quên mật khẩu
                </Title>
            </div>

            <Alert
                type="info"
                showIcon
                className="forgot-password-info"
                message="Hệ thống không tự đặt lại mật khẩu qua email."
                description="Hãy liên hệ quản trị viên của trường để được đặt lại mật khẩu. Sau khi đăng nhập, bạn có thể tự đổi mật khẩu ở trang Hồ sơ cá nhân."
            />

            <Link to="/auth/login">
                <Button type="primary" size="large" block className="forgot-password-submit">
                    Quay lại đăng nhập
                </Button>
            </Link>
        </div>
    );
}
