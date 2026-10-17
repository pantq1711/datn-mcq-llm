package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: quality_check_result.check_type. */
public enum CheckType implements DbEnum {
    FORMAT("format"),
    DEDUP("dedup"),
    ANSWER_CHECK("answer_check");

    private final String dbValue;

    CheckType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<CheckType> {
        public Conv() {
            super(CheckType.class);
        }
    }
}
