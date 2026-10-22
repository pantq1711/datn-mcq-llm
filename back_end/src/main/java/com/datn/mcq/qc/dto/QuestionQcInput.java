package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;

/**
 * Nội dung một câu đưa vào pipeline QC.
 *
 * @param parseError       thông báo lỗi parse JSON (ví dụ khóa trùng); FormatChecker đưa vào lỗi format khi
 *                         {@code parseSuccess = false}. Null khi parse thành công hoặc nơi gọi không có thông tin này.
 * @param questionType     dạng câu (V4); null được coi là mcq
 * @param saAnswer         đáp số câu Trả lời ngắn đúng như LLM viết (chỉ với sa)
 * @param saAnswerIsString trường answer trong JSON là chuỗi (chỉ có nghĩa với sa)
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
        String parseError,
        QuestionType questionType,
        String saAnswer,
        boolean saAnswerIsString
) {
    public QuestionQcInput {
        questionType = questionType != null ? questionType : QuestionType.MCQ;
    }

    /** Câu trắc nghiệm 4 phương án (chữ ký trước V4). */
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
            boolean runAnswerCheck,
            String parseError) {
        this(questionId, parseSuccess, questionText, optionA, optionB, optionC, optionD, correctOption,
                explanation, validateLatex, runAnswerCheck, parseError, QuestionType.MCQ, null, true);
    }

    /** Câu trắc nghiệm 4 phương án, không có thông tin lỗi parse (chữ ký trước V4). */
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

    /** Câu Trả lời ngắn (V4): không có phương án và correct_option. */
    public static QuestionQcInput shortAnswer(
            Integer questionId,
            boolean parseSuccess,
            String questionText,
            String explanation,
            String saAnswer,
            boolean saAnswerIsString,
            boolean validateLatex,
            boolean runAnswerCheck,
            String parseError) {
        return new QuestionQcInput(questionId, parseSuccess, questionText, null, null, null, null, null,
                explanation, validateLatex, runAnswerCheck, parseError, QuestionType.SA, saAnswer, saAnswerIsString);
    }
}
