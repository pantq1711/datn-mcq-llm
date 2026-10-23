package com.datn.mcq.qc.dto;

/**
 * Nội dung một câu dùng để so trùng: thân câu và 4 phương án.
 * Không gồm đáp án đúng và lời giải; hai trường này không thuộc dấu vân tay của câu.
 */
public record DedupCandidate(
        Integer questionId,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD
) {
    public boolean hasContent() {
        return notBlank(questionText) && notBlank(optionA) && notBlank(optionB)
                && notBlank(optionC) && notBlank(optionD);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
