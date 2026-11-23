/**
 * Giao bài online chưa hỗ trợ câu có hình (A1, docs/design/cau-co-hinh.md mục 4.5): học sinh chưa có đường xem ảnh nên
 * backend trả 409 ASSIGN_HAS_FIGURE khi đề có chỗ ngân hàng nào có ảnh. Giao diện làm mờ nút Giao bài trước, kèm lý do.
 * Gỡ cùng điều kiện ở backend ở PR A2.
 */

/** Một chỗ của đề; chỉ cần `state` (để không trùng kiểu với nhánh giao diện hình) và `images` do backend trả khi có hình. */
interface ItemLike {
    state: string;
}

/**
 * Số chỗ của đề có hình (backend trả `images` không rỗng ở chỗ lấy từ ngân hàng mà câu nguồn có ảnh, G0 H3).
 * `images` đọc không qua kiểu: trường này được khai kiểu ở nhánh giao diện hiển thị hình, không phải ở đây.
 */
export function countFigureItems(parts: ReadonlyArray<{ items: ReadonlyArray<ItemLike> }> | undefined): number {
    let count = 0;
    for (const part of parts ?? []) {
        for (const item of part.items) {
            const images = (item as { images?: unknown }).images;
            if (Array.isArray(images) && images.length > 0) {
                count++;
            }
        }
    }
    return count;
}

/** Lý do làm mờ nút Giao bài khi đề có hình; chuỗi rỗng khi không có. */
export function figureAssignBlockReason(figureCount: number): string {
    if (figureCount <= 0) return "";
    return `Đề có ${figureCount} câu có hình. Giao bài online chưa hỗ trợ câu có hình; bỏ hoặc Đổi các câu đó rồi mới giao bài được.`;
}
