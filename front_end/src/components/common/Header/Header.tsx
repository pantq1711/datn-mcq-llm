import { Avatar, Badge, Button, Dropdown, Layout, Space, Typography, } from "antd";
import { BellOutlined, LogoutOutlined, MenuOutlined, UserOutlined, } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import type { MenuProps } from "antd";
import { useAuth } from "../../../context/AuthContext";
import { logout } from "../../../features/auth/services/authApi";
import "./Header.css";

const { Header: AntHeader } = Layout;
const { Text } = Typography;

interface HeaderProps {

    onToggleSidebar?: () => void;
    variant?: "default" | "auth";
}

const userMenuItems: MenuProps["items"] = [
    {
        key: "profile",
        label: "Hồ sơ cá nhân",
        icon: <UserOutlined />,
    },
    {
        type: "divider",
    },
    {
        key: "logout",
        label: "Đăng xuất",
        icon: <LogoutOutlined />,
    },
];

export default function Header({
    onToggleSidebar,
    variant = "default",
}: HeaderProps) {
    const navigate = useNavigate();
    const { setToken } = useAuth();
    const { user } = useAuth();

    const handleMenuClick: MenuProps["onClick"] = async (e) => {
        if (e.key === "logout") {
            try {
                await logout();                     // gọi API
                setToken(null);                     // xóa token trong Context
                navigate("/auth/login");            // chuyển hướng sau logout
            } catch (err) {
                console.error("Logout failed", err);
                // Nếu muốn, hiển thị thông báo lỗi cho người dùng
            }
        }
        // Các menu khác (profile…) có thể xử lý ở đây
    };

    return (
        <AntHeader className={`app-header ${variant === "auth" ? "app-header-auth" : ""}`}>
            <div className="app-header-left">
                {variant === "default" && (
                    <Button
                        type="text"
                        icon={<MenuOutlined />}
                        onClick={onToggleSidebar}
                        className="app-header-sidebar-toggle"
                    />
                )}

                <div className="app-header-brand" onClick={() => navigate("/")}>
                    <img
                        src="/favicon.png"
                        alt="Logo"
                        className="app-header-brand-logo"
                    />

                    <Typography.Title
                        level={4}
                        className="app-header-brand-title"
                    >
                        Online learning
                    </Typography.Title>
                </div>
            </div>

            {variant === "default" && (
                <div className="app-header-right">
                    <Button
                        type="text"
                        shape="circle"
                        className="app-header-notification"
                    >
                        <Badge count={3} size="small">
                            <BellOutlined />
                        </Badge>
                    </Button>

                    <Dropdown menu={{ items: userMenuItems, onClick: handleMenuClick }}>
                        <Button type="text" className="app-header-user">
                            <Space size={10}>
                                <Avatar icon={<UserOutlined />} />
                                <div className="app-header-user-info">
                                    {/* Hiển thị tên thực tế nếu có, nếu không fallback */}
                                    <Text className="app-header-user-name">
                                        {user?.name ?? "Người dùng"}
                                    </Text>
                                    <Text type="secondary" className="app-header-user-role">
                                        {user?.role ?? "Không xác định"}
                                    </Text>
                                </div>
                            </Space>
                        </Button>
                    </Dropdown>
                </div>
            )}
        </AntHeader>
    );
}