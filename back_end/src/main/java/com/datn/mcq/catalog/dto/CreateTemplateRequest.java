package com.datn.mcq.catalog.dto;

import com.datn.mcq.enums.SourceType;

/**
 * Yêu cầu tạo question_template cho một subject đã tồn tại.
 * sourceType bỏ trống thì mặc định "text"; gradeLevel bỏ trống hoặc trong khoảng 6 đến 9.
 */
public record CreateTemplateRequest(
        Integer subjectId,
        Integer gradeLevel,
        String topic,
        String learningOutcome,
        String sourceQuestion,
        SourceType sourceType
) {
}
