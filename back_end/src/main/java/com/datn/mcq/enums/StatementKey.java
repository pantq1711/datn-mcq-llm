package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/**
 * Vị trí ý của câu Đúng/Sai. Cột dùng: position của generated_question_statement, teacher_review_statement,
 * answer_check_statement, bank_item_statement.
 */
public enum StatementKey implements DbEnum {
    A("a"),
    B("b"),
    C("c"),
    D("d");

    private final String dbValue;

    StatementKey(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<StatementKey> {
        public Conv() {
            super(StatementKey.class);
        }
    }
}
