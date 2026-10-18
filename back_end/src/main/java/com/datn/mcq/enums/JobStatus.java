package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: generation_job.status. */
public enum JobStatus implements DbEnum {
    PENDING("pending"),
    RUNNING("running"),
    DONE("done"),
    FAILED("failed");

    private final String dbValue;

    JobStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<JobStatus> {
        public Conv() {
            super(JobStatus.class);
        }
    }
}
