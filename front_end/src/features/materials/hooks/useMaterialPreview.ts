import { useRef, useState } from "react";
import { message } from "antd";
import type { MaterialDto } from "../types/material.types";
import { getMaterial } from "../services/materialApi";

/** Mở modal xem trước: hiện ngay bản trong danh sách rồi nạp chi tiết (textPreview). Bỏ kết quả về muộn. */
export function useMaterialPreview() {
    const [previewMaterial, setPreviewMaterial] = useState<MaterialDto | null>(null);
    const [previewLoading, setPreviewLoading] = useState(false);
    // Mỗi lần mở/đóng tăng số này; kết quả của lần gọi cũ (đã đóng hoặc đã chuyển tài liệu khác) bị bỏ qua
    const requestIdRef = useRef(0);

    const openPreview = async (item: MaterialDto) => {
        const requestId = ++requestIdRef.current;
        setPreviewMaterial(item);
        setPreviewLoading(true);
        try {
            const detail = await getMaterial(item.id);
            if (requestId === requestIdRef.current) setPreviewMaterial(detail);
        } catch (err: any) {
            if (requestId !== requestIdRef.current) return;
            message.error(err?.response?.data?.error || "Không tải được chi tiết tài liệu");
            setPreviewMaterial(null); // không để modal báo "Không có đoạn xem trước" khi thực ra là lỗi tải
        } finally {
            if (requestId === requestIdRef.current) setPreviewLoading(false);
        }
    };

    const closePreview = () => {
        requestIdRef.current++;
        setPreviewMaterial(null);
        setPreviewLoading(false);
    };

    return { previewMaterial, previewLoading, openPreview, closePreview };
}