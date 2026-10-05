import { useState } from "react";
import { Layout } from "antd";
import { Outlet } from "react-router-dom";

import MainLayout from "./MainLayout";
import Header from "../components/common/Header/Header";
import Sidebar from "../components/common/Sidebar/Sidebar";

import "./TeacherLayout.css";

const { Sider } = Layout;

export default function TeacherLayout() {
    const [collapsed, setCollapsed] = useState(true);

    const handleToggleSidebar = () => {
        setCollapsed((previous) => !previous);
    };

    return (
        <MainLayout>
            <div className="teacher-layout">
                <Header
                    collapsed={collapsed}
                    onToggleSidebar={handleToggleSidebar}
                />

                <Layout className="teacher-layout-body">
                    <Sider
                        width={240}
                        collapsedWidth={80}
                        collapsed={collapsed}
                        theme="light"
                        className="teacher-layout-sidebar"
                    >
                        <Sidebar />
                    </Sider>

                    <main className="teacher-layout-content">
                        <Outlet />
                    </main>
                </Layout>
            </div>
        </MainLayout>
    );
}