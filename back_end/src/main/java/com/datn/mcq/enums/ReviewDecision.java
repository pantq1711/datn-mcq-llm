package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: teacher_review.decision. */
public enum ReviewDecision implements DbEnum {
    APPROVED_AS_IS("approved_as_is"),
    APPROVED_WITH_EDIT("approved_with_edit"),
    REJECTED("rejected");

    private final String dbValue;

    ReviewDecision(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<ReviewDecision> {
        public Conv() {
            super(ReviewDecision.class);
        }
    }
}
