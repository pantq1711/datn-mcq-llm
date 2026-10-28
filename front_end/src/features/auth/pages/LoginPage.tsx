import { Typography } from "antd";
import LoginForm from "../components/LoginForm";
import "./LoginPage.css";

const { Title } = Typography;

export default function LoginPage() {
    return (
        <div className="login-page">
            <div className="login-page-header">
                <Title level={2} className="login-page-title">
                    Đăng nhập
                </Title>
            </div>

            <div className="login-page-form">
                <LoginForm />
            </div>
        </div>
    );
}