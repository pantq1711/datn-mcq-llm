import { Typography } from "antd";
import RegisterForm from "../components/RegisterForm";
import "./RegisterPage.css";

const { Title } = Typography;

export default function RegisterPage() {
    return (
        <div className="register-page">
            <div className="register-page-header">
                <Title level={2} className="register-page-title">
                    Đăng ký
                </Title>
            </div>

            <div className="register-page-form">
                <RegisterForm />
            </div>
        </div>
    );
}