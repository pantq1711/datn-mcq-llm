import { useState } from "react";
import { Layout } from "antd";
import { Outlet } from "react-router-dom";

import MainLayout from "./MainLayout";
import Header from "../components/common/Header/Header";
import Sidebar from "../components/common/Sidebar/Sidebar";

import "./AppLayout.css";

const { Sider } = Layout;

export default function AppLayout() {
    // Mặc định thu gọn thành dải biểu tượng; bấm nút ☰ để mở rộng có chữ
    const [collapsed, setCollapsed] = useState(true);
    // Màn hẹp hơn 768px: thanh bên ẩn hẳn, mở ra thì nằm đè lên nội dung như ngăn trượt
    const [narrow, setNarrow] = useState(false);

    const handleToggleSidebar = () => {
        setCollapsed((previous) => !previous);
    };

    const handleBreakpoint = (broken: boolean) => {
        setNarrow(broken);
        if (broken) setCollapsed(true);
    };

    const closeOnNarrow = () => {
        if (narrow) setCollapsed(true);
    };

    const overlayOpen = narrow && !collapsed;

    return (
        <MainLayout>
            <div className="app-layout">
                <Header
                    onToggleSidebar={handleToggleSidebar}
                />

                <Layout className="app-layout-body">
                    <Sider
                        width={240}
                        collapsedWidth={narrow ? 0 : 80}
                        collapsed={collapsed}
                        breakpoint="md"
                        onBreakpoint={handleBreakpoint}
                        trigger={null}
                        theme="light"
                        className={`app-layout-sidebar${overlayOpen ? " app-layout-sidebar-overlay" : ""}`}
                    >
                        <Sidebar onNavigate={closeOnNarrow} />
                    </Sider>

                    {overlayOpen && (
                        <div className="app-layout-mask" onClick={closeOnNarrow} aria-hidden="true" />
                    )}

                    <main className="app-layout-content">
                        <Outlet />
                    </main>
                </Layout>
            </div>
        </MainLayout>
    );
}
