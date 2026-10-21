package com.datn.mcq.review.dto;

import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * DTO danh sách câu hỏi chờ duyệt.
 * Tuyệt đối không chứa correct_option, llm_difficulty, explanation hay kết quả QC chi tiết.
 * Với reviewPurpose = EXPERIMENT: status và latexValid đều là null và bị loại bỏ khỏi JSON serialization.
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
        QuestionStatus status,
        Boolean latexValid,
        LocalDateTime createdAt
) {
}

