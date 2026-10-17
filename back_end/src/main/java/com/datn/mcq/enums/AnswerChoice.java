package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: teacher_review.reviewer_answer, quality_check_result.judge_answer. */
public enum AnswerChoice implements DbEnum {
    A("A"),
    B("B"),
    C("C"),
    D("D"),
    MULTIPLE("multiple"),
    NONE("none");

    private final String dbValue;

    AnswerChoice(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<AnswerChoice> {
        public Conv() {
            super(AnswerChoice.class);
        }
    }
}
