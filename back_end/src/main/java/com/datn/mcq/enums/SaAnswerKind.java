package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/**
 * Loại đáp số giáo viên nhập khi chấm mù câu Trả lời ngắn. Cột dùng: teacher_review.reviewer_sa_kind.
 */
public enum SaAnswerKind implements DbEnum {
    /** Giáo viên nhập một đáp số. */
    NUMBER("number"),
    /** Đề có nhiều cách hiểu cho ra đáp số khác nhau. */
    AMBIGUOUS("ambiguous");

    private final String dbValue;

    SaAnswerKind(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<SaAnswerKind> {
        public Conv() {
            super(SaAnswerKind.class);
        }
    }
}
