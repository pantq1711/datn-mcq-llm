package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.QuestionType;

/**
 * Nội dung một câu dùng để so trùng.
 * <ul>
 *   <li>mcq: thân câu và 4 phương án. Không gồm đáp án đúng và lời giải.</li>
 *   <li>sa (V4): thân câu và đáp số (so theo khóa chuẩn của giá trị). Không gồm lời giải.</li>
 * </ul>
 *
 * @param questionType dạng câu; null được coi là mcq. Chỉ so trùng giữa các câu cùng dạng.
 * @param saAnswer     đáp số câu sa đúng như đã lưu; null với mcq
 */
public record DedupCandidate(
        Integer questionId,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        QuestionType questionType,
        String saAnswer
) {
    public DedupCandidate {
        questionType = questionType != null ? questionType : QuestionType.MCQ;
    }

    /** Câu trắc nghiệm 4 phương án (chữ ký trước V4). */
    public DedupCandidate(
            Integer questionId,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD) {
        this(questionId, questionText, optionA, optionB, optionC, optionD, QuestionType.MCQ, null);
    }

    /** Câu Trả lời ngắn (V4). */
    public static DedupCandidate shortAnswer(Integer questionId, String questionText, String saAnswer) {
        return new DedupCandidate(questionId, questionText, null, null, null, null, QuestionType.SA, saAnswer);
    }

    public boolean hasContent() {
        if (questionType == QuestionType.SA) {
            return notBlank(questionText) && notBlank(saAnswer);
        }
        return notBlank(questionText) && notBlank(optionA) && notBlank(optionB)
                && notBlank(optionC) && notBlank(optionD);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
