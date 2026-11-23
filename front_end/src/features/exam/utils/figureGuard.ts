/**
 * Giao bài online chưa hỗ trợ câu có hình (A1, docs/design/cau-co-hinh.md mục 4.5): học sinh chưa có đường xem ảnh nên
 * backend trả 409 ASSIGN_HAS_FIGURE khi đề có chỗ ngân hàng nào có ảnh. Giao diện làm mờ nút Giao bài trước, kèm lý do.
 * Gỡ cùng điều kiện ở backend ở PR A2.
 */

import type { QuestionImageInfo } from "../types/exam.types";

/** Một chỗ của đề; chỉ cần `state` và `images` (backend trả khi có hình, vắng khi không). */
interface ItemLike {
    state: string;
    images?: QuestionImageInfo[] | null;
}

/**
 * Số chỗ của đề có hình (backend trả `images` không rỗng ở chỗ lấy từ ngân hàng mà câu nguồn có ảnh, G0 H3).
 * Vẫn kiểm `Array.isArray` để dữ liệu lỗi dạng không làm sai số đếm.
 */
export function countFigureItems(parts: ReadonlyArray<{ items: ReadonlyArray<ItemLike> }> | undefined): number {
    let count = 0;
    for (const part of parts ?? []) {
        for (const item of part.items) {
            const images = item.images;
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
