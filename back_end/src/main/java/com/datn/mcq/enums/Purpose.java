package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: experiment_run.purpose, teacher_review.review_purpose. */
public enum Purpose implements DbEnum {
    EXPERIMENT("experiment"),
    PRODUCTION("production");

    private final String dbValue;

    Purpose(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<Purpose> {
        public Conv() {
            super(Purpose.class);
        }
    }
}
