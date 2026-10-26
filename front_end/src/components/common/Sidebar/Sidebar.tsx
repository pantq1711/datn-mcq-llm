import { Menu } from "antd";
import { FileTextOutlined, HomeOutlined, ReadOutlined, TableOutlined } from "@ant-design/icons";
import { useLocation, useNavigate } from "react-router-dom";
import type { MenuProps } from "antd";
import "./Sidebar.css";

type MenuItem = Required<MenuProps>["items"][number];

const menuItems: MenuItem[] = [
    {
        key: "/",
        icon: <HomeOutlined />,
        label: "Tổng quan",
    },
    // {
    //     key: "/generate",
    //     icon: <FormOutlined />,
    //     label: "Sinh câu hỏi",
    // },
    // {
    //     key: "/job-processor",
    //     icon: <OrderedListOutlined />,
    //     label: "Quản lý job sinh câu hỏi",
    // },
    // {
    //     key: "/review",
    //     icon: <SafetyCertificateOutlined />,
    //     label: "Duyệt câu hỏi",
    // },
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
    // {
    //     key: "/experiments",
    //     icon: <ExperimentOutlined />,
    //     label: "Thực nghiệm",
    // },
];

export default function Sidebar() {
    const navigate = useNavigate();
    const location = useLocation();

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