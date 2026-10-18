package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: exam.status. */
public enum ExamStatus implements DbEnum {
    DRAFT("draft"),
    CONFIRMED("confirmed");

    private final String dbValue;

    ExamStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<ExamStatus> {
        public Conv() {
            super(ExamStatus.class);
        }
    }
}
