package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;

import java.util.List;

/**
 * Nội dung một câu đưa vào pipeline QC.
 *
 * @param parseError       thông báo lỗi parse JSON (ví dụ khóa trùng); FormatChecker đưa vào lỗi format khi
 *                         {@code parseSuccess = false}. Null khi parse thành công hoặc nơi gọi không có thông tin này.
 * @param questionType     dạng câu (V4); null được coi là mcq
 * @param saAnswer         đáp số câu Trả lời ngắn đúng như LLM viết (chỉ với sa)
 * @param saAnswerIsString trường answer trong JSON là chuỗi (chỉ có nghĩa với sa)
 * @param statements       (tf) 4 ý theo thứ tự a-d; rỗng với mcq/sa
 * @param statementLabels  (tf) 4 nhãn đúng như LLM viết (đã trim) theo thứ tự a-d; rỗng với mcq/sa
 * @param parseWarnings    (tf) cảnh báo từ bước giải mã, FormatChecker chép vào answerWarnings; rỗng khi không có
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
        boolean saAnswerIsString,
        List<String> statements,
        List<String> statementLabels,
        List<String> parseWarnings
) {
    public QuestionQcInput {
        questionType = questionType != null ? questionType : QuestionType.MCQ;
        statements = statements == null ? List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(statements));
        statementLabels = statementLabels == null ? List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(statementLabels));
        parseWarnings = parseWarnings == null ? List.of() : List.copyOf(parseWarnings);
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
                explanation, validateLatex, runAnswerCheck, parseError, QuestionType.MCQ, null, true, null, null, null);
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
                explanation, validateLatex, runAnswerCheck, parseError, QuestionType.SA, saAnswer, saAnswerIsString,
                null, null, null);
    }

    /** Câu Đúng/Sai (V4, GĐ3): đề dẫn, 4 ý và 4 nhãn theo thứ tự a-d. */
    public static QuestionQcInput trueFalse(
            Integer questionId,
            boolean parseSuccess,
            String questionText,
            List<String> statements,
            List<String> statementLabels,
            String explanation,
            boolean validateLatex,
            boolean runAnswerCheck,
            String parseError) {
        return trueFalse(questionId, parseSuccess, questionText, statements, statementLabels, explanation,
                validateLatex, runAnswerCheck, parseError, null);
    }

    /** Câu Đúng/Sai kèm cảnh báo từ bước giải mã (GĐ4). */
    public static QuestionQcInput trueFalse(
            Integer questionId,
            boolean parseSuccess,
            String questionText,
            List<String> statements,
            List<String> statementLabels,
            String explanation,
            boolean validateLatex,
            boolean runAnswerCheck,
            String parseError,
            List<String> parseWarnings) {
        return new QuestionQcInput(questionId, parseSuccess, questionText, null, null, null, null, null,
                explanation, validateLatex, runAnswerCheck, parseError, QuestionType.TF, null, true,
                statements, statementLabels, parseWarnings);
    }
}
