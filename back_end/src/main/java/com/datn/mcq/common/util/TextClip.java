package com.datn.mcq.common.util;

/** Cắt chuỗi theo giới hạn cột DB (W1-3: lời giải thích Solver, lỗi Solver). */
public final class TextClip {

    private TextClip() {}

    /**
     * Cắt {@code text} còn tối đa {@code max} ký tự (không cắt đôi một cặp surrogate); trống hoặc null thì null.
     */
    public static String clip(String text, int max) {
        // PostgreSQL không nhận ký tự NUL (0x00) trong TEXT; văn bản từ LLM có thể chứa.
        text = text == null ? null : text.replace("\u0000", "");
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.length() <= max) {
            return text;
        }
        int end = max;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }
}
