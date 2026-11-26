/** Chữ thay thế khi không có ảnh để hiện (token hỏng, ảnh không có trong `images`, URL hết hạn). Không bao giờ hiện chuỗi token. */
export const FIGURE_FALLBACK_TEXT = "[Hình không tải được]";
/** Nhãn gọn thay cho ảnh ở nơi chỉ có một dòng chữ (ô bảng ngân hàng). */
export const FIGURE_COMPACT_TEXT = "[Hình]";

const PX_PER_MM = 96 / 25.4;

/** Bề rộng hiển thị (px, ở 96 dpi) của ảnh có bề rộng gốc trong Word là `mm`. */
export function widthPxFromMm(mm?: number): number | undefined {
    return mm ? Math.round(mm * PX_PER_MM) : undefined;
}

/**
 * Ghép gốc API với đường dẫn ảnh. `<img>` không gửi header Bearer nên chữ ký nằm sẵn trong URL (docs/design/cau-co-hinh.md H3);
 * không tải blob. Đường dẫn của backend bắt đầu bằng "/api", giống các lời gọi của apiClient.
 */
export function resolveImageUrl(url: string): string {
    if (/^https?:\/\//i.test(url)) return url;
    const base = String(import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/+$/, "");
    return base + url;
}
