import type { ReactNode } from "react";
import { Layout } from "antd";
import "./MainLayout.css";

interface MainLayoutProps {
    children: ReactNode;
}

const { Content } = Layout;

export default function MainLayout({
    children,
}: MainLayoutProps) {
    return (
        <Layout className="main-layout">
            <Content className="main-layout-content">
                {children}
            </Content>
        </Layout>
    );
}