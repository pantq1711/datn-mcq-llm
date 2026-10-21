package com.datn.mcq.review.dto;

import com.datn.mcq.enums.Purpose;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * DTO trả về cho form chấm của giáo viên (Blind Review Contract).
 * Tuân thủ Quy tắc 9 trong docs/architecture/backend-structure.md, mục 3 docs/contract/enums.md
 * và quyết định 21/10 mục 2 trong docs/ai-development-rules.md. Áp dụng cho CẢ production và experiment:
 * KHÔNG chứa correct_option, explanation, llm_difficulty, status, latexValid, latexErrors
 * hay bất kỳ kết quả QC nào (format, dedup, answer_check).
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
        LocalDateTime createdAt
) {
}
