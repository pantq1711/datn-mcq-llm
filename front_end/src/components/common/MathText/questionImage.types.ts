/**
 * Một ảnh của câu hỏi, khớp QuestionImageDto của backend (câu ngân hàng và câu trong đề lấy từ ngân hàng).
 * `url` là đường dẫn có chữ ký ngắn hạn cho đúng người xem ("/api/question-images/{id}?exp&u&sig"); cần ghép gốc API.
 * Token ⟦img:{id}⟧ trong chữ của câu trỏ tới `id` ở đây.
 */
export interface QuestionImageInfo {
    id: number;
    /** stem, option_a..option_d, statement_a..statement_d, solution */
    role: string;
    ordinal?: number;
    mime?: string;
    widthPx?: number;
    heightPx?: number;
    /** Bề rộng hiển thị trong Word gốc (mm). */
    displayWidthMm?: number;
    url: string;
}
