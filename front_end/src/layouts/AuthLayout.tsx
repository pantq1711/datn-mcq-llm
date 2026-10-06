import { Layout } from "antd";
import { Outlet } from "react-router-dom";
import "./AuthLayout.css";

const { Content } = Layout;

export default function AuthLayout() {
    return (
        <Layout className="auth-layout">
            <Content className="auth-layout-content">
                <div className="auth-layout-container">
                    <div className="auth-layout-card">
                        <Outlet />
                    </div>
                </div>
            </Content>
        </Layout>
    );
}