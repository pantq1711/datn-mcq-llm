package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: experiment_run.strategy. */
public enum Strategy implements DbEnum {
    ZERO_SHOT("zero-shot"),
    FEW_SHOT("few-shot"),
    FEW_SHOT_STRUCTURED("few-shot-structured");

    private final String dbValue;

    Strategy(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<Strategy> {
        public Conv() {
            super(Strategy.class);
        }
    }
}
