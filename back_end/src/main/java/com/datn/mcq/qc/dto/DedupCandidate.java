package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.QuestionType;

import java.util.List;

/**
 * Nội dung một câu dùng để so trùng.
 * <ul>
 *   <li>mcq: thân câu và 4 phương án. Không gồm đáp án đúng và lời giải.</li>
 *   <li>sa (V4): thân câu và đáp số (so theo khóa chuẩn của giá trị). Không gồm lời giải.</li>
 *   <li>tf (V4): đề dẫn và 4 ý. Không gồm nhãn Đúng/Sai và lời giải.</li>
 * </ul>
 *
 * @param questionType dạng câu; null được coi là mcq. Chỉ so trùng giữa các câu cùng dạng.
 * @param saAnswer     đáp số câu sa đúng như đã lưu; null với mcq và tf
 * @param statements   4 ý của câu tf theo thứ tự a-d; rỗng với mcq và sa
 */
public record DedupCandidate(
        Integer questionId,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        QuestionType questionType,
        String saAnswer,
        List<String> statements
) {
    public DedupCandidate {
        questionType = questionType != null ? questionType : QuestionType.MCQ;
        statements = statements == null ? List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(statements));
    }

    /** Câu trắc nghiệm 4 phương án (chữ ký trước V4). */
    public DedupCandidate(
            Integer questionId,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD) {
        this(questionId, questionText, optionA, optionB, optionC, optionD, QuestionType.MCQ, null, null);
    }

    /** Câu Trả lời ngắn (V4). */
    public static DedupCandidate shortAnswer(Integer questionId, String questionText, String saAnswer) {
        return new DedupCandidate(questionId, questionText, null, null, null, null, QuestionType.SA, saAnswer, null);
    }

    /** Câu Đúng/Sai (V4, GĐ3). */
    public static DedupCandidate trueFalse(Integer questionId, String questionText, List<String> statements) {
        return new DedupCandidate(questionId, questionText, null, null, null, null, QuestionType.TF, null, statements);
    }

    public boolean hasContent() {
        if (questionType == QuestionType.SA) {
            return notBlank(questionText) && notBlank(saAnswer);
        }
        if (questionType == QuestionType.TF) {
            return notBlank(questionText) && statements.size() == 4 && statements.stream().allMatch(DedupCandidate::notBlank);
        }
        return notBlank(questionText) && notBlank(optionA) && notBlank(optionB)
                && notBlank(optionC) && notBlank(optionD);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
