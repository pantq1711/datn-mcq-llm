package com.datn.mcq.generation.dto;

import java.util.List;

/**
 * Kết quả giải mã JSON câu Đúng/Sai (V4, GĐ3): đề dẫn, 4 ý a-d, lời giải, 4 nhãn.
 *
 * @param statements 4 ý theo thứ tự a, b, c, d (phần tử null khi thiếu); rỗng khi giải mã lỗi
 * @param labels     4 nhãn đúng như LLM viết, đã trim, theo thứ tự a-d (phần tử null khi thiếu); rỗng khi giải mã lỗi.
 *                   Kiểm tra giá trị T/F làm ở FormatChecker.
 * @param parseError thông báo lỗi giải mã (ví dụ khóa trùng); null khi giải mã được
 */
public record ParsedTfDto(
        boolean parseSuccess,
        String questionText,
        List<String> statements,
        String explanation,
        List<String> labels,
        String parseError
) {
    public ParsedTfDto {
        statements = statements == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(statements));
        labels = labels == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(labels));
    }
}
