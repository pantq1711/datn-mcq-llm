package com.datn.mcq.review.dto;

import com.datn.mcq.enums.QuestionStatus;

import java.time.LocalDateTime;

/**
 * DTO danh sách câu hỏi chờ duyệt.
 * Tuyệt đối không chứa correct_option, llm_difficulty hay kết quả QC chi tiết.
 */
public record ReviewQuestionListItemDto(
        Integer id,
        Integer templateId,
        String subjectName,
        Integer gradeLevel,
        String topic,
        String questionText,
        QuestionStatus status,
        boolean latexValid,
        LocalDateTime createdAt
) {
}

