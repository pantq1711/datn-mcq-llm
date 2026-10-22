package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/**
 * Nhãn mà người chấm hoặc Solver đưa ra cho một ý Đúng/Sai: thêm "undecidable" (không kết luận được)
 * so với {@link TruthValue}, giống quan hệ giữa {@link Option} và {@link AnswerChoice}.
 * Cột dùng: teacher_review_statement.reviewer_label, answer_check_statement.judge_label.
 */
public enum TruthChoice implements DbEnum {
    TRUE("T"),
    FALSE("F"),
    UNDECIDABLE("undecidable");

    private final String dbValue;

    TruthChoice(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    /** Nhãn Solver viết: sau trim, không phân biệt hoa thường; giá trị khác hoặc null trả null. */
    public static TruthChoice fromJudge(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.strip();
        for (TruthChoice c : values()) {
            if (c.dbValue.equalsIgnoreCase(v)) {
                return c;
            }
        }
        return null;
    }

    /** Nhãn giáo viên hoặc Solver có khớp nhãn LLM không ("undecidable" không bao giờ khớp). */
    public boolean matches(TruthValue llmLabel) {
        return llmLabel != null && this != UNDECIDABLE && dbValue.equals(llmLabel.getDbValue());
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<TruthChoice> {
        public Conv() {
            super(TruthChoice.class);
        }
    }
}
