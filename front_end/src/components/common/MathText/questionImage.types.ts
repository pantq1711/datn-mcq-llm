/**
 * Phần của một ảnh mà MathText cần để vẽ: dùng chung cho ảnh của giáo viên và ảnh của học sinh (S1 không trả `role`).
 * Token ⟦img:{id}⟧ trong chữ của câu trỏ tới `id` ở đây; `url` có chữ ký ngắn hạn, cần ghép gốc API.
 */
export interface QuestionImageView {
    id: number;
    mime?: string;
    widthPx?: number;
    heightPx?: number;
    /** Bề rộng hiển thị trong Word gốc (mm). */
    displayWidthMm?: number;
    url: string;
}

/**
 * Một ảnh của câu hỏi, khớp QuestionImageDto của backend (câu ngân hàng và câu trong đề lấy từ ngân hàng).
 * `url` là đường dẫn có chữ ký ngắn hạn cho đúng người xem ("/api/question-images/{id}?exp&u&sig").
 */
export interface QuestionImageInfo extends QuestionImageView {
    /** stem, option_a..option_d, statement_a..statement_d, solution */
    role: string;
    ordinal?: number;
}
