import { Menu } from "antd";
import {
    ExperimentOutlined,
    FileTextOutlined,
    FormOutlined,
    HomeOutlined,
    ReadOutlined,
    SafetyCertificateOutlined,
} from "@ant-design/icons";
import { useLocation, useNavigate } from "react-router-dom";
import type { MenuProps } from "antd";
import "./Sidebar.css";

type MenuItem = Required<MenuProps>["items"][number];

const menuItems: MenuItem[] = [
    {
        key: "/teacher/dashboard",
        icon: <HomeOutlined />,
        label: "Tổng quan",
    },
    {
        key: "/teacher/generate",
        icon: <FormOutlined />,
        label: "Sinh câu hỏi",
    },
    {
        key: "/teacher/review",
        icon: <SafetyCertificateOutlined />,
        label: "Duyệt câu hỏi",
    },
    {
        key: "/teacher/question-bank",
        icon: <ReadOutlined />,
        label: "Ngân hàng câu hỏi",
    },
    {
        key: "/teacher/exams",
        icon: <FileTextOutlined />,
        label: "Đề thi",
    },
    {
        key: "/teacher/experiments",
        icon: <ExperimentOutlined />,
        label: "Thực nghiệm",
    },
];

export default function Sidebar() {
    const navigate = useNavigate();
    const location = useLocation();

    const handleMenuClick: MenuProps["onClick"] = ({ key }) => {
        navigate(key);
    };

    const selectedKey =
        menuItems
            .filter((item) => item && "key" in item)
            .map((item) => ("key" in item ? item.key : undefined))
            .find(
                (key) =>
                    typeof key === "string" &&
                    (location.pathname === key ||
                        location.pathname.startsWith(`${key}/`)),
            ) ?? "/teacher/dashboard";

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