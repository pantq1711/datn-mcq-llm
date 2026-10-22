package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/**
 * Nhãn Đúng/Sai của một ý (nhãn LLM, nhãn cuối). Dùng "T"/"F" thay vì true/false để chuỗi "false"
 * không bị hiểu là giá trị đúng ở frontend.
 * Cột dùng: generated_question_statement.llm_label, teacher_review_statement.final_label, bank_item_statement.label.
 */
public enum TruthValue implements DbEnum {
    TRUE("T"),
    FALSE("F");

    private final String dbValue;

    TruthValue(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    /** Nhãn LLM viết trong JSON: sau trim, "T"/"F" không phân biệt hoa thường; giá trị khác hoặc null trả null. */
    public static TruthValue fromLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.strip();
        return v.equalsIgnoreCase("T") ? TRUE : v.equalsIgnoreCase("F") ? FALSE : null;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<TruthValue> {
        public Conv() {
            super(TruthValue.class);
        }
    }
}
