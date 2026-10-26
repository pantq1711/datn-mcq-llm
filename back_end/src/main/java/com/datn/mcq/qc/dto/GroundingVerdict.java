package com.datn.mcq.qc.dto;

import java.util.Locale;

/**
 * Kết luận của bước QC "bám tài liệu" (W2-3): câu hỏi, đáp án và lời giải có dựa trên và nhất quán với các đoạn tài liệu bài
 * học đã đưa vào prompt hay không.
 */
public enum GroundingVerdict {
    /** Kiến thức câu hỏi kiểm tra có trong đoạn tài liệu và đáp án không mâu thuẫn; có trích dẫn đã kiểm chứng. */
    SUPPORTED("supported"),
    /** Đáp án hoặc lời giải mâu thuẫn với tài liệu, hoặc câu hỏi dựa trên khái niệm tài liệu nêu khác. */
    NOT_SUPPORTED("not_supported"),
    /** Đoạn tài liệu đã cung cấp không đủ để kết luận (hoặc bằng chứng của SUPPORTED không kiểm chứng được). */
    UNKNOWN("unknown");

    private final String dbValue;

    GroundingVerdict(String dbValue) {
        this.dbValue = dbValue;
    }

    /** Giá trị lưu ở question_grounding.verdict. */
    public String dbValue() {
        return dbValue;
    }

    /** Từ cột question_grounding.verdict. */
    public static GroundingVerdict fromDb(String value) {
        for (GroundingVerdict v : values()) {
            if (v.dbValue.equals(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("verdict không hợp lệ: " + value);
    }

    /** Từ phản hồi của judge (SUPPORTED, NOT_SUPPORTED, UNKNOWN; không phân biệt hoa thường); null nếu không nhận ra. */
    public static GroundingVerdict fromJudge(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip().toUpperCase(Locale.ROOT);
        for (GroundingVerdict g : values()) {
            if (g.name().equals(v)) {
                return g;
            }
        }
        return null;
    }
}
