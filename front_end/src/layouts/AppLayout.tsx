import { useState } from "react";
import { Layout } from "antd";
import { Outlet } from "react-router-dom";

import MainLayout from "./MainLayout";
import Header from "../components/common/Header/Header";
import Sidebar from "../components/common/Sidebar/Sidebar";

import "./AppLayout.css";

const { Sider } = Layout;

export default function AppLayout() {
    const [collapsed, setCollapsed] = useState(true);

    const handleToggleSidebar = () => {
        setCollapsed((previous) => !previous);
    };

    return (
        <MainLayout>
            <div className="app-layout">
                <Header
                    onToggleSidebar={handleToggleSidebar}
                />

                <Layout className="app-layout-body">
                    <Sider
                        width={240}
                        collapsedWidth={80}
                        collapsed={collapsed}
                        theme="light"
                        className="app-layout-sidebar"
                    >
                        <Sidebar />
                    </Sider>

                    <main className="app-layout-content">
                        <Outlet />
                    </main>
                </Layout>
            </div>
        </MainLayout>
    );
}