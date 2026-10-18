package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: question_template.source_type. */
public enum SourceType implements DbEnum {
    TEXT("text"),
    PDF("pdf"),
    IMAGE("image");

    private final String dbValue;

    SourceType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<SourceType> {
        public Conv() {
            super(SourceType.class);
        }
    }
}
