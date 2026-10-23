package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: users.role. */
public enum UserRole implements DbEnum {
    TEACHER("teacher"),
    RESEARCHER("researcher");

    private final String dbValue;

    UserRole(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<UserRole> {
        public Conv() {
            super(UserRole.class);
        }
    }
}
