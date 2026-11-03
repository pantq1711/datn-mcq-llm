package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/**
 * Cột dùng: generated_question.origin (V26). {@code llm}: đầu ra LLM và bản giáo viên sửa (mọi dòng trước V26);
 * {@code import}: câu nhập từ Word. Thống kê đầu ra LLM và bộ đếm lượt gọi Gemini chỉ tính {@code llm}.
 */
public enum QuestionOrigin implements DbEnum {
    LLM("llm"),
    IMPORT("import");

    private final String dbValue;

    QuestionOrigin(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<QuestionOrigin> {
        public Conv() {
            super(QuestionOrigin.class);
        }
    }
}
