import {
    Avatar,
    Badge,
    Button,
    Dropdown,
    Layout,
    Space,
    Typography,
} from "antd";
import {
    BellOutlined,
    LogoutOutlined,
    MenuOutlined,
    UserOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import type { MenuProps } from "antd";
import "./Header.css";

const { Header: AntHeader } = Layout;
const { Text } = Typography;

interface HeaderProps {
    collapsed: boolean;
    onToggleSidebar: () => void;
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
}: HeaderProps) {
    const navigate = useNavigate();
    return (
        <AntHeader className="app-header">
            <div className="app-header-left">
                <Button
                    type="text"
                    icon={<MenuOutlined />}
                    onClick={onToggleSidebar}
                    className="app-header-sidebar-toggle"
                />

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

                <Dropdown
                    menu={{ items: userMenuItems }}
                    trigger={["click"]}
                    placement="bottomRight"
                >
                    <Button type="text" className="app-header-user">
                        <Space size={10}>
                            <Avatar icon={<UserOutlined />} />

                            <div className="app-header-user-info">
                                <Text className="app-header-user-name">
                                    Nguyễn Văn A
                                </Text>

                                <Text
                                    type="secondary"
                                    className="app-header-user-role"
                                >
                                    Giáo viên
                                </Text>
                            </div>
                        </Space>
                    </Button>
                </Dropdown>
            </div>
        </AntHeader>
    );
}