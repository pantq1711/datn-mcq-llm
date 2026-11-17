// Đường dẫn trang học sinh làm bài (An chốt: /lam-bai/:code, khớp docs/design/lam-bai-truc-tuyen.md và docs/api mục 11).
// Route trong App, link trong hộp giao bài và mã QR cùng lấy từ đây để không lệch nhau.

export const STUDENT_ROUTE = "/lam-bai/:code";

/** Đường dẫn (không kèm origin) tới bài có mã `publicCode`. */
export function studentPath(publicCode: string): string {
    return `/lam-bai/${encodeURIComponent(publicCode)}`;
}
