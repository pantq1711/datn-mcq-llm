package com.datn.mcq.generation.dto;

import java.util.List;

/**
 * Nội dung giáo viên sửa cho một câu (W1-2), gửi đủ nội dung theo dạng câu (thay thế toàn bộ, không sửa mức độ).
 * <ul>
 *   <li>mcq: questionText, optionA..optionD, correctOption (A-D), explanation;</li>
 *   <li>sa: questionText, saAnswer, explanation;</li>
 *   <li>tf: questionText, statements (đủ 4 ý a-d, mỗi ý text và label T/F), explanation.</li>
 * </ul>
 * Trường không áp dụng cho dạng câu phải để null.
 */
public record QuestionEditInput(
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String correctOption,
        String saAnswer,
        List<StatementEdit> statements,
        String explanation
) {
    /** Một ý câu Đúng/Sai: key a-d, nội dung, nhãn T/F. */
    public record StatementEdit(String key, String text, String label) {}
}
