import { Layout } from "antd";
import { Outlet } from "react-router-dom";

import MainLayout from "./MainLayout";
import Header from "../components/common/Header/Header";

import "./AuthLayout.css";

const { Content } = Layout;

export default function AuthLayout() {
    return (
        <MainLayout>
            <div className="auth-layout-wrapper">
                <Header variant="auth" />

                <Layout className="auth-layout">
                    <Content className="auth-layout-content">
                        <div className="auth-layout-container">
                            <div className="auth-layout-card">
                                <Outlet />
                            </div>
                        </div>
                    </Content>
                </Layout>
            </div>
        </MainLayout>
    );
}