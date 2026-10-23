package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: dedup_gold_pair.split. */
public enum GoldSplit implements DbEnum {
    DEV("dev"),
    TEST("test");

    private final String dbValue;

    GoldSplit(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<GoldSplit> {
        public Conv() {
            super(GoldSplit.class);
        }
    }
}
