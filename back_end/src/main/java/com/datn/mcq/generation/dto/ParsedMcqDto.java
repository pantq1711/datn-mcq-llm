package com.datn.mcq.generation.dto;

import com.datn.mcq.enums.Option;

import java.util.List;
import java.util.Map;

/**
 * Kết quả giải mã JSON câu trắc nghiệm (7 trường; từ 13/11 thêm trường thứ 8 {@code distractor_errors} của prompt ma trận).
 *
 * @param parseWarnings    cảnh báo khi giải mã ở chế độ sửa (ngày 23/10: đã sửa escape LaTeX, đã tự đóng $); FormatChecker
 *                         chép vào answerWarnings. Rỗng khi không có.
 * @param distractorErrors (13/11, PA1) lỗi sai của học sinh theo phương án A-D (đủ 4 khóa, phương án đúng và phương án không có
 *                         lỗi là null); null khi đầu ra không có trường này hoặc không có lỗi nào dùng được
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
        List<String> parseWarnings,
        Map<String, String> distractorErrors
) {
    public ParsedMcqDto {
        parseWarnings = parseWarnings == null ? List.of() : List.copyOf(parseWarnings);
        distractorErrors = distractorErrors == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(distractorErrors));
    }

    /** Không có lỗi sai kèm phương án (chữ ký trước 13/11). */
    public ParsedMcqDto(boolean parseSuccess, String questionText, String optionA, String optionB, String optionC,
                        String optionD, Option correctOption, String explanation, String parseError,
                        List<String> parseWarnings) {
        this(parseSuccess, questionText, optionA, optionB, optionC, optionD, correctOption, explanation, parseError,
                parseWarnings, null);
    }

    /** Bản sao có thêm một cảnh báo ở cuối {@code parseWarnings}. */
    public ParsedMcqDto withParseWarning(String warning) {
        List<String> all = new java.util.ArrayList<>(parseWarnings);
        all.add(warning);
        return new ParsedMcqDto(parseSuccess, questionText, optionA, optionB, optionC, optionD, correctOption, explanation,
                parseError, all, distractorErrors);
    }

    /** Không có cảnh báo giải mã. */
    public ParsedMcqDto(boolean parseSuccess, String questionText, String optionA, String optionB, String optionC,
                        String optionD, Option correctOption, String explanation, String parseError) {
        this(parseSuccess, questionText, optionA, optionB, optionC, optionD, correctOption, explanation, parseError,
                List.of());
    }
}
