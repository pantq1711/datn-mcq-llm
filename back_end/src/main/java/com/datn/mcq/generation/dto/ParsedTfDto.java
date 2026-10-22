package com.datn.mcq.generation.dto;

import java.util.List;

/**
 * Kết quả giải mã JSON câu Đúng/Sai (V4, GĐ3): đề dẫn, 4 ý a-d, lời giải, 4 nhãn.
 *
 * @param statements 4 ý theo thứ tự a, b, c, d (phần tử null khi thiếu); rỗng khi giải mã lỗi
 * @param labels     4 nhãn đúng như LLM viết, đã trim, theo thứ tự a-d (phần tử null khi thiếu); rỗng khi giải mã lỗi.
 *                   Kiểm tra giá trị T/F làm ở FormatChecker.
 * @param parseError thông báo lỗi giải mã (ví dụ khóa trùng); null khi giải mã được
 * @param parseWarnings cảnh báo khi giải mã (GĐ4: đã bỏ tiền tố nhãn "a)" ở đầu ý); FormatChecker chép vào
 *                      answerWarnings. Rỗng khi không có.
 */
public record ParsedTfDto(
        boolean parseSuccess,
        String questionText,
        List<String> statements,
        String explanation,
        List<String> labels,
        String parseError,
        List<String> parseWarnings
) {
    public ParsedTfDto {
        statements = statements == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(statements));
        labels = labels == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(labels));
        parseWarnings = parseWarnings == null ? List.of() : List.copyOf(parseWarnings);
    }

    /** Không có cảnh báo giải mã. */
    public ParsedTfDto(boolean parseSuccess, String questionText, List<String> statements, String explanation,
                       List<String> labels, String parseError) {
        this(parseSuccess, questionText, statements, explanation, labels, parseError, List.of());
    }
}
