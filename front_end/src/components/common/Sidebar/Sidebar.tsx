import { Menu } from "antd";
import { FileTextOutlined, FolderOpenOutlined, HomeOutlined, ReadOutlined, TableOutlined, TeamOutlined } from "@ant-design/icons";
import { useLocation, useNavigate } from "react-router-dom";
import type { MenuProps } from "antd";
import { useAuth } from "../../../context/AuthContext";
import { ROLE_ADMIN } from "../../../utils/auth.utils";
import "./Sidebar.css";

type MenuItem = Required<MenuProps>["items"][number];

export default function Sidebar() {
    const navigate = useNavigate();
    const location = useLocation();
    const { user } = useAuth();

    const isAdmin = user?.role === ROLE_ADMIN;

    const menuItems: MenuItem[] = [
        {
            key: "/",
            icon: <HomeOutlined />,
            label: "Tổng quan",
        },
        {
            key: "/question-bank",
            icon: <ReadOutlined />,
            label: "Ngân hàng câu hỏi",
        },
        {
            key: "/exam-matrices",
            icon: <TableOutlined />,
            label: "Quản lý ma trận",
        },
        {
            key: "/exams",
            icon: <FileTextOutlined />,
            label: "Đề thi",
        },
        {
            key: "/materials",
            icon: <FolderOpenOutlined />,
            label: "Tài liệu bài học",
        },
        ...(isAdmin
            ? [
                {
                    key: "/admin/users",
                    icon: <TeamOutlined />,
                    label: "Quản trị tài khoản",
                },
            ]
            : []),
    ];

    const handleMenuClick: MenuProps["onClick"] = ({ key }) => {
        navigate(key);
    };

    const selectedKey = menuItems
        .filter((item): item is NonNullable<MenuItem> => item !== null && typeof item === "object" && "key" in item)
        .map((item) => item.key)
        .find((key) => {
            if (typeof key !== "string") return false;
            return location.pathname === key || location.pathname.startsWith(`${key}/`);
        }) ?? "/";

    return (
        <aside className="app-sidebar">
            <Menu
                mode="inline"
                selectedKeys={[selectedKey as string]}
                items={menuItems}
                onClick={handleMenuClick}
                className="app-sidebar-menu"
            />
        </aside>
    );
}