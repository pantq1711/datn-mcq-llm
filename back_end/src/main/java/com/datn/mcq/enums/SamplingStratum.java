package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: dedup_gold_pair.sampling_stratum. */
public enum SamplingStratum implements DbEnum {
    SAME_RUN("same_run"),
    CROSS_RUN("cross_run"),
    CROSS_TEMPLATE("cross_template"),
    RANDOM("random");

    private final String dbValue;

    SamplingStratum(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<SamplingStratum> {
        public Conv() {
            super(SamplingStratum.class);
        }
    }
}
