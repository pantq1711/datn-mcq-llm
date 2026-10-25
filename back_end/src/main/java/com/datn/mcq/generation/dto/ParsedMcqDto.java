package com.datn.mcq.generation.dto;

import com.datn.mcq.enums.Option;

import java.util.List;

/**
 * Kết quả giải mã JSON câu trắc nghiệm (7 trường).
 *
 * @param parseWarnings cảnh báo khi giải mã ở chế độ sửa (ngày 23/10: đã sửa escape LaTeX, đã tự đóng $); FormatChecker
 *                      chép vào answerWarnings. Rỗng khi không có.
 */
public record ParsedMcqDto(
        boolean parseSuccess,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        Option correctOption,
        String explanation,
        String parseError,
        List<String> parseWarnings
) {
    public ParsedMcqDto {
        parseWarnings = parseWarnings == null ? List.of() : List.copyOf(parseWarnings);
    }

    /** Không có cảnh báo giải mã. */
    public ParsedMcqDto(boolean parseSuccess, String questionText, String optionA, String optionB, String optionC,
                        String optionD, Option correctOption, String explanation, String parseError) {
        this(parseSuccess, questionText, optionA, optionB, optionC, optionD, correctOption, explanation, parseError,
                List.of());
    }
}
