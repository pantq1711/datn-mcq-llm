import { createElement } from "react";
import { FilePdfOutlined, FileWordOutlined, FileTextOutlined } from "@ant-design/icons";
import type { MaterialWarningCode } from "../features/materials/types/material.types";

// Nhãn và mức độ cảnh báo trích xuất (docs/api mục 9); chỉ FORMULA_LOSS_HIGH là nghiêm trọng
export const materialWarningConfig: Record<MaterialWarningCode, { label: string; severity: "error" | "warning" }> = {
    MATHTYPE_FORMULAS_DROPPED: { label: "Thiếu công thức MathType", severity: "warning" },
    FORMULA_LOSS_HIGH: { label: "Mất nhiều công thức", severity: "error" },
    FORMULA_IMAGES_NOT_READ: { label: "Có ảnh chưa đọc được", severity: "warning" },
    PDF_TEXT_QUALITY_LOW: { label: "Chất lượng chữ PDF thấp", severity: "warning" },
};

export function describeMaterialWarning(code?: string) {
    return materialWarningConfig[code as MaterialWarningCode] ?? { label: "Lưu ý tài liệu", severity: "warning" as const };
}

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