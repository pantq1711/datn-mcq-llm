package com.datn.mcq.qc.checker;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Từ tiếng Anh đứng riêng thường gặp (and, or, the, is, true, false) nằm ngoài $...$ trong câu do LLM sinh (2/11, thẩm
 * định UX). Không tốn lượt gọi LLM nào; FormatChecker chỉ ghi cảnh báo (bật cờ "cần xem kỹ"), không loại câu.
 *
 * <p>Chỉ khớp từ đứng riêng (không dính chữ cái, chữ số hay "_"): "orchestra", "this", "thesis" không khớp. Văn bản trong
 * $...$ bị bỏ qua (ví dụ {@code \text{ and }}); "\$" là dấu đô la thường. Không phân biệt hoa thường.
 */
public final class EnglishWordLeak {

    private static final Pattern WORD = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(and|or|the|is|true|false)(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE);

    private EnglishWordLeak() {}

    /** Các từ tiếng Anh tìm thấy ngoài công thức, chữ thường, mỗi từ một lần, theo thứ tự xuất hiện; rỗng khi không có. */
    public static List<String> find(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        Matcher m = WORD.matcher(outsideMath(text));
        while (m.find()) {
            found.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(found);
    }

    /** Thay phần trong $...$ bằng một dấu cách; dấu $ lẻ thì phần còn lại coi là công thức. */
    private static String outsideMath(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean inMath = false;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                if (!inMath) {
                    sb.append(' ');
                }
                i++;
            } else if (c == '$') {
                inMath = !inMath;
                sb.append(' ');
                if (i + 1 < n && text.charAt(i + 1) == '$') {
                    i++; // "$$" là một dấu mở hoặc đóng công thức riêng dòng
                }
            } else {
                sb.append(inMath ? ' ' : c);
            }
        }
        return sb.toString();
    }
}
