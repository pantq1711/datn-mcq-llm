package com.datn.mcq.review.dto;

import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO trả về cho form chấm của giáo viên (Blind Review Contract - Q-1 đã chốt).
 * Tuân thủ Quy tắc 9 trong docs/architecture/backend-structure.md và Mục 3 docs/contract/enums.md:
 * KHÔNG chứa correct_option, KHÔNG chứa llm_difficulty, KHÔNG chứa kết quả QC (judge_answer, similarity_score...).
 * Tuyệt đối KHÔNG chứa explanation cho cả PRODUCTION và EXPERIMENT trong form chấm ban đầu.
 * Với reviewPurpose = EXPERIMENT: status, latexValid, latexErrors đều là null và bị loại bỏ khỏi JSON payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlindQuestionReviewDto(
        Integer id,
        Integer templateId,
        Purpose reviewPurpose,
        String subjectName,
        Integer gradeLevel,
        String topic,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        QuestionStatus status,
        Boolean latexValid,
        List<String> latexErrors,
        LocalDateTime createdAt
) {
}

