package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: teacher_review_reason.reason_code. */
public enum ReasonCode implements DbEnum {
    CONTENT_INACCURATE("content_inaccurate"),
    CONTENT_OFF_CURRICULUM("content_off_curriculum"),
    CONTENT_INSUFFICIENT_INFO("content_insufficient_info"),
    CONTENT_UNCLEAR("content_unclear"),
    OPTIONS_OUT_OF_SCOPE("options_out_of_scope"),
    OPTIONS_INDISTINCT("options_indistinct"),
    PRESENTATION_QUESTION_HARD_TO_READ("presentation_question_hard_to_read"),
    PRESENTATION_OPTIONS_HARD_TO_READ("presentation_options_hard_to_read"),
    PRESENTATION_MISSING_QUESTION("presentation_missing_question"),
    PRESENTATION_GRAMMAR("presentation_grammar"),
    PRESENTATION_FORMULA_SYMBOL_ERROR("presentation_formula_symbol_error"),
    ANSWER_MULTIPLE_CORRECT("answer_multiple_correct"),
    ANSWER_NONE_CORRECT("answer_none_correct"),
    OTHER("other");

    private final String dbValue;

    ReasonCode(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<ReasonCode> {
        public Conv() {
            super(ReasonCode.class);
        }
    }
}
