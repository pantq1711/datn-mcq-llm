package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: generated_question.status. */
public enum QuestionStatus implements DbEnum {
    GENERATED("generated"),
    FORMAT_FAILED("format_failed"),
    DUPLICATE("duplicate"),
    QC_PASSED("qc_passed"),
    REJECTED("rejected"),
    IN_BANK("in_bank");

    private final String dbValue;

    QuestionStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<QuestionStatus> {
        public Conv() {
            super(QuestionStatus.class);
        }
    }
}
