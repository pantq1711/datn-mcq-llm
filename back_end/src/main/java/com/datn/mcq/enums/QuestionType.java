package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/**
 * Cột dùng: question_type của experiment_run, generated_question, generation_job, teacher_review,
 * quality_check_result, bank_item và 4 bảng ý TF (V4).
 */
public enum QuestionType implements DbEnum {
    MCQ("mcq"),
    TF("tf"),
    SA("sa");

    private final String dbValue;

    QuestionType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<QuestionType> {
        public Conv() {
            super(QuestionType.class);
        }
    }
}
