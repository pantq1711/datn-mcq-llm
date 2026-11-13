package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;

import java.util.List;
import java.util.Map;

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
 * @param llmOutput        câu là đầu ra của LLM (luồng sinh), không phải chữ giáo viên viết hay sửa; FormatChecker chỉ áp cho
 *                         đầu ra LLM quy tắc loại lời giải tf tự thú nhận gán nhãn theo mẫu. Mặc định false.
 * @param requestCodes     (12/11, PR B) mã chip yêu cầu của giáo viên của câu (generated_question.request_codes); rỗng khi
 *                         không có. Solver kiểm yêu cầu trong cùng lượt gọi
 * @param requestText      (PR B) văn bản tự do của yêu cầu (generated_question.request_text); null khi không có
 * @param distractorErrors (13/11, PA1) lỗi sai của học sinh theo phương án A-D (generated_question.distractor_errors, chỉ mcq);
 *                         null khi không có. Solver ghép mù trong cùng lượt gọi, không thấy chữ cái gắn với lỗi
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
        List<String> parseWarnings,
        boolean llmOutput,
        List<String> requestCodes,
        String requestText,
        Map<String, String> distractorErrors
) {
    public QuestionQcInput {
        questionType = questionType != null ? questionType : QuestionType.MCQ;
        statements = statements == null ? List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(statements));
        statementLabels = statementLabels == null ? List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(statementLabels));
        parseWarnings = parseWarnings == null ? List.of() : List.copyOf(parseWarnings);
        requestCodes = requestCodes == null ? List.of() : List.copyOf(requestCodes);
        requestText = requestText == null || requestText.isBlank() ? null : requestText;
        distractorErrors = distractorErrors == null || questionType != QuestionType.MCQ ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(distractorErrors));
    }

    /** Chữ ký trước 13/11 (không có lỗi sai kèm phương án). */
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
            String parseError,
            QuestionType questionType,
            String saAnswer,
            boolean saAnswerIsString,
            List<String> statements,
            List<String> statementLabels,
            List<String> parseWarnings,
            boolean llmOutput,
            List<String> requestCodes,
            String requestText) {
        this(questionId, parseSuccess, questionText, optionA, optionB, optionC, optionD, correctOption, explanation,
                validateLatex, runAnswerCheck, parseError, questionType, saAnswer, saAnswerIsString, statements,
                statementLabels, parseWarnings, llmOutput, requestCodes, requestText, null);
    }

    /** Bản sao kèm lỗi sai theo phương án (13/11, PA1, đọc từ generated_question); null là không có. */
    public QuestionQcInput withDistractorErrors(Map<String, String> errors) {
        return new QuestionQcInput(questionId, parseSuccess, questionText, optionA, optionB, optionC, optionD,
                correctOption, explanation, validateLatex, runAnswerCheck, parseError, questionType, saAnswer,
                saAnswerIsString, statements, statementLabels, parseWarnings, llmOutput, requestCodes, requestText,
                errors);
    }

    /** Chữ ký trước PR B (câu không có yêu cầu của giáo viên). */
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
            String parseError,
            QuestionType questionType,
            String saAnswer,
            boolean saAnswerIsString,
            List<String> statements,
            List<String> statementLabels,
            List<String> parseWarnings,
            boolean llmOutput) {
        this(questionId, parseSuccess, questionText, optionA, optionB, optionC, optionD, correctOption, explanation,
                validateLatex, runAnswerCheck, parseError, questionType, saAnswer, saAnswerIsString, statements,
                statementLabels, parseWarnings, llmOutput, null, null);
    }

    /** Bản sao kèm yêu cầu của giáo viên (PR B, đọc từ generated_question). */
    public QuestionQcInput withRequest(List<String> codes, String text) {
        return new QuestionQcInput(questionId, parseSuccess, questionText, optionA, optionB, optionC, optionD,
                correctOption, explanation, validateLatex, runAnswerCheck, parseError, questionType, saAnswer,
                saAnswerIsString, statements, statementLabels, parseWarnings, llmOutput, codes, text, distractorErrors);
    }

    /** Không phải đầu ra của LLM (chữ ký trước khi có {@code llmOutput}). */
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
            String parseError,
            QuestionType questionType,
            String saAnswer,
            boolean saAnswerIsString,
            List<String> statements,
            List<String> statementLabels,
            List<String> parseWarnings) {
        this(questionId, parseSuccess, questionText, optionA, optionB, optionC, optionD, correctOption, explanation,
                validateLatex, runAnswerCheck, parseError, questionType, saAnswer, saAnswerIsString, statements,
                statementLabels, parseWarnings, false);
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

    /** Bản sao kèm cảnh báo từ bước giải mã (ngày 23/10: áp cho cả mcq và sa). */
    public QuestionQcInput withParseWarnings(List<String> warnings) {
        return new QuestionQcInput(questionId, parseSuccess, questionText, optionA, optionB, optionC, optionD,
                correctOption, explanation, validateLatex, runAnswerCheck, parseError, questionType, saAnswer,
                saAnswerIsString, statements, statementLabels, warnings, llmOutput, requestCodes, requestText,
                distractorErrors);
    }

    /** Bản sao đánh dấu câu là đầu ra của LLM (luồng sinh) hay không. */
    public QuestionQcInput withLlmOutput(boolean fromLlm) {
        return new QuestionQcInput(questionId, parseSuccess, questionText, optionA, optionB, optionC, optionD,
                correctOption, explanation, validateLatex, runAnswerCheck, parseError, questionType, saAnswer,
                saAnswerIsString, statements, statementLabels, parseWarnings, fromLlm, requestCodes, requestText,
                distractorErrors);
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
