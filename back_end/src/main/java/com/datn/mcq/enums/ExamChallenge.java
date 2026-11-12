package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * Mức thử thách của đề (12/11, cột exam.challenge, V27): {@code standard} như trước; {@code advanced} thêm khối "Mức
 * NÂNG CAO" vào prompt của các ô (hoặc ý tf) mức Vận dụng. Đổi câu trong đề nâng cao giữ mức.
 */
public enum ExamChallenge implements DbEnum {
    STANDARD("standard"),
    ADVANCED("advanced");

    private final String dbValue;

    ExamChallenge(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    /**
     * Giá trị client gửi: bỏ trống là {@link #STANDARD}; không phân biệt hoa thường, bỏ khoảng trắng hai đầu; giá trị khác
     * thì null (nơi gọi trả 400).
     */
    public static ExamChallenge parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return STANDARD;
        }
        String v = raw.strip().toLowerCase(Locale.ROOT);
        for (ExamChallenge c : values()) {
            if (c.dbValue.equals(v)) {
                return c;
            }
        }
        return null;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<ExamChallenge> {
        public Conv() {
            super(ExamChallenge.class);
        }
    }
}
