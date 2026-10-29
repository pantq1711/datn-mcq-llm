import { createElement } from "react";
import { FilePdfOutlined, FileWordOutlined, FileTextOutlined } from "@ant-design/icons";

export const renderFileIcon = (kind: string, size: number = 20) => {
    const lowerKind = (kind || "").toLowerCase();
    if (lowerKind === "pdf") {
        return createElement(FilePdfOutlined, { style: { color: "#ff4d4f", fontSize: size } });
    }
    if (lowerKind === "docx" || lowerKind === "doc") {
        return createElement(FileWordOutlined, { style: { color: "#1890ff", fontSize: size } });
    }
    return createElement(FileTextOutlined, { style: { color: "#52c41a", fontSize: size } });
};

export const formatBytes = (bytes?: number) => {
    if (!bytes) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
};