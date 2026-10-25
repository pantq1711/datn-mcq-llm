package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/**
 * Loại đáp số Solver trả cho câu Trả lời ngắn. Cột dùng: quality_check_result.judge_sa_kind.
 */
public enum SaJudgeKind implements DbEnum {
    /** Solver trả một đáp số phân tích được. */
    NUMBER("number"),
    /** Solver báo đề có nhiều cách hiểu cho ra đáp số khác nhau. */
    AMBIGUOUS("ambiguous"),
    /** Solver trả một chuỗi không phân tích được thành số, nên không so tự động được. */
    UNPARSEABLE("unparseable");

    private final String dbValue;

    SaJudgeKind(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<SaJudgeKind> {
        public Conv() {
            super(SaJudgeKind.class);
        }
    }
}
