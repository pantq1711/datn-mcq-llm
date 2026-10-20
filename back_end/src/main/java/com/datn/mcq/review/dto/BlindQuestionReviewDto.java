package com.datn.mcq.review.dto;

import com.datn.mcq.enums.QuestionStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO trả về cho form chấm của giáo viên (Blind Review Contract).
 * Tuân thủ Quy tắc 9 trong docs/architecture/backend-structure.md và Mục 3 docs/contract/enums.md:
 * KHÔNG chứa correct_option, KHÔNG chứa llm_difficulty, KHÔNG chứa kết quả QC (judge_answer, similarity_score...).
 */
public record BlindQuestionReviewDto(
        Integer id,
        Integer templateId,
        String subjectName,
        Integer gradeLevel,
        String topic,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String explanation,
        QuestionStatus status,
        boolean latexValid,
        List<String> latexErrors,
        LocalDateTime createdAt
) {
}

