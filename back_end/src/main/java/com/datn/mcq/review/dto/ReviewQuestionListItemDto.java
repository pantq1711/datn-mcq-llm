package com.datn.mcq.review.dto;

import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * DTO danh sách câu hỏi chờ chấm, cho CẢ production và experiment (quyết định 21/10 mục 2).
 * Không chứa correct_option, llm_difficulty, explanation, status, latexValid hay kết quả QC.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewQuestionListItemDto(
        Integer id,
        Integer templateId,
        Purpose reviewPurpose,
        String subjectName,
        Integer gradeLevel,
        String topic,
        String questionText,
        LocalDateTime createdAt,
        QuestionType questionType
) {
}
