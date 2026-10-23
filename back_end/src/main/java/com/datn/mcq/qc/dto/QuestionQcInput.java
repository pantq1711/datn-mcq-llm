package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.Option;

/**
 * @param parseError thông báo lỗi parse JSON (ví dụ khóa trùng); FormatChecker đưa vào lỗi format khi
 *                   {@code parseSuccess = false}. Null khi parse thành công hoặc nơi gọi không có thông tin này.
 */
public record QuestionQcInput(
        Integer questionId,
        boolean parseSuccess,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        Option correctOption,
        String explanation,
        boolean validateLatex,
        boolean runAnswerCheck,
        String parseError
) {
    public QuestionQcInput(
            Integer questionId,
            boolean parseSuccess,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            Option correctOption,
            String explanation,
            boolean validateLatex,
            boolean runAnswerCheck) {
        this(questionId, parseSuccess, questionText, optionA, optionB, optionC, optionD, correctOption,
                explanation, validateLatex, runAnswerCheck, null);
    }
}
