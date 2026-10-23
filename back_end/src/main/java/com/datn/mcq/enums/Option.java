package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: generated_question.correct_option, teacher_review.final_option, bank_item.correct_option. */
public enum Option implements DbEnum {
    A("A"),
    B("B"),
    C("C"),
    D("D");

    private final String dbValue;

    Option(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<Option> {
        public Conv() {
            super(Option.class);
        }
    }
}
