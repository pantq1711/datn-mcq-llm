package com.datn.mcq.generation.dto;

import java.util.List;

/**
 * Kết quả giải mã JSON câu Trả lời ngắn (3 trường: question_text, explanation, answer).
 *
 * @param answer         đáp số đúng như LLM viết, đã trim (không đổi \n theo nghĩa đen); null khi thiếu
 * @param answerIsString true khi trường answer là chuỗi JSON (hợp đồng yêu cầu chuỗi); false khi là số, đối tượng...
 * @param parseError     thông báo lỗi giải mã (ví dụ khóa trùng); null khi giải mã được
 * @param parseWarnings  cảnh báo khi giải mã ở chế độ sửa (đã sửa escape LaTeX, đã tự đóng $); rỗng khi không có
 */
public record ParsedSaDto(
        boolean parseSuccess,
        String questionText,
        String explanation,
        String answer,
        boolean answerIsString,
        String parseError,
        List<String> parseWarnings
) {
    public ParsedSaDto {
        parseWarnings = parseWarnings == null ? List.of() : List.copyOf(parseWarnings);
    }

    /** Không có cảnh báo giải mã. */
    public ParsedSaDto(boolean parseSuccess, String questionText, String explanation, String answer,
                       boolean answerIsString, String parseError) {
        this(parseSuccess, questionText, explanation, answer, answerIsString, parseError, List.of());
    }
}
