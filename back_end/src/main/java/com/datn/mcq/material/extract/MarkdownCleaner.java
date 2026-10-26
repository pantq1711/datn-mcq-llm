package com.datn.mcq.material.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Làm sạch markdown của pandoc thành văn bản thường, ở khâu trích (W2-3 bước 5): bỏ dấu in đậm, in nghiêng, gạch, chỉ số
 * trên/dưới; bỏ dấu {@code \} thoát ký tự (trả lại đúng ký tự); bỏ {@code #} của tiêu đề, {@code >} của trích dẫn, các
 * dòng kẻ bảng/đường ngang; liên kết chỉ giữ chữ hiển thị; danh sách gọn. Chỉ số trên/dưới của Word (pandoc: {@code x^2^},
 * {@code H~2~O}) giữ nghĩa bằng ký hiệu {@code x^2}, {@code H_2O} (bỏ dấu đóng của cặp); {@code ~~gạch~~} chỉ bỏ dấu. Nhờ vậy {@code extracted_text} lưu trong DB đã
 * sạch và các offset (from, to) của đoạn trích khớp đúng văn bản đã lưu.
 *
 * <p>Công thức {@code $...$} và {@code $$...$$} được GIỮ NGUYÊN từng ký tự (kể cả {@code *}, {@code _}, {@code \}
 * bên trong). Pandoc thoát mọi dấu {@code $} chữ thật thành {@code \$}, nên cặp {@code $} không thoát là công thức; một
 * {@code $} lẻ không có cặp là chữ thường. Công thức được tách ra thành ký hiệu giữ chỗ trước khi làm sạch phần còn lại và
 * trả về nguyên vẹn sau đó.
 */
final class MarkdownCleaner {

    private static final char HOLD_OPEN = '';
    private static final char HOLD_CLOSE = '';
    private static final String PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    private static final Pattern RULE_LINE = Pattern.compile("^\\s*(?:-{3,}[\\s-]*|={3,}\\s*|\\*{3,}\\s*)$");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern QUOTE = Pattern.compile("^\\s{0,3}>\\s?");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*+]\\s+");
    private static final Pattern ORDERED = Pattern.compile("^(\\s*)(\\d+)[.)]\\s+");
    private static final Pattern LINK = Pattern.compile("(?<!\\\\)\\[([^\\]\\n]*)]\\([^)\\s]*(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern AUTOLINK = Pattern.compile("<(https?://[^>\\s]+)>");

    private MarkdownCleaner() {}

    static String clean(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        List<String> math = new ArrayList<>();
        String text = holdMath(markdown, math);
        text = lineRules(text);
        text = AUTOLINK.matcher(text).replaceAll("$1");
        text = LINK.matcher(text).replaceAll("$1");
        text = inline(text);
        text = restoreMath(text, math);
        return text.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").strip();
    }

    // ---------- công thức ----------

    /** Thay mỗi công thức bằng ký hiệu giữ chỗ (HOLD_OPEN số HOLD_CLOSE); công thức được thêm vào {@code math}. */
    private static String holdMath(String s, List<String> math) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                out.append(c).append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '$') {
                int end = mathEnd(s, i);
                if (end > i) {
                    out.append(HOLD_OPEN).append(math.size()).append(HOLD_CLOSE);
                    math.add(s.substring(i, end));
                    i = end;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Vị trí ngay sau công thức bắt đầu ở {@code start}; {@code start} nếu đó không phải công thức. */
    private static int mathEnd(String s, int start) {
        if (start + 1 < s.length() && s.charAt(start + 1) == '$') {
            int close = s.indexOf("$$", start + 2);
            return close < 0 ? start : close + 2;
        }
        if (start + 1 >= s.length() || Character.isWhitespace(s.charAt(start + 1))) {
            return start;
        }
        for (int j = start + 1; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '\\') {
                j++;
            } else if (c == '\n' && j + 1 < s.length() && s.charAt(j + 1) == '\n') {
                return start;
            } else if (c == '$') {
                return Character.isWhitespace(s.charAt(j - 1)) ? start : j + 1;
            }
        }
        return start;
    }

    private static String restoreMath(String s, List<String> math) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == HOLD_OPEN) {
                int close = s.indexOf(HOLD_CLOSE, i);
                out.append(math.get(Integer.parseInt(s.substring(i + 1, close))));
                i = close + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    // ---------- theo dòng ----------

    private static String lineRules(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            if (RULE_LINE.matcher(line).matches()) {
                continue;
            }
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                line = heading.group(1);
            } else {
                Matcher quote = QUOTE.matcher(line);
                while (quote.find()) {
                    line = line.substring(quote.end());
                    quote = QUOTE.matcher(line);
                }
                line = BULLET.matcher(line).replaceFirst("$1- ");
                line = ORDERED.matcher(line).replaceFirst("$1$2. ");
            }
            out.append(line).append('\n');
        }
        out.setLength(Math.max(0, out.length() - 1));
        return out.toString();
    }

    // ---------- trong dòng ----------

    /**
     * Vị trí dấu đóng của chỉ số trên/dưới bắt đầu ở {@code open} (pandoc: {@code ^2^}, {@code ~2~}): nội dung không rỗng,
     * không có khoảng trắng, dấu đóng không bị thoát. -1 khi không có cặp.
     */
    private static int scriptEnd(String s, int open, char marker) {
        for (int j = open + 1; j < s.length(); j++) {
            char c = s.charAt(j);
            if (Character.isWhitespace(c)) {
                return -1;
            }
            if (c == '\\') {
                j++;
            } else if (c == marker) {
                return j > open + 1 ? j : -1;
            }
        }
        return -1;
    }

    private static String inline(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == '\n') {
                    // dấu \ cuối dòng của pandoc là ngắt dòng cứng: giữ xuống dòng, bỏ dấu \
                    continue;
                }
                if (PUNCTUATION.indexOf(next) >= 0) {
                    out.append(next);
                    i++;
                    continue;
                }
                out.append(c);
            } else if (c == '*') {
                // dấu in đậm/nghiêng: chữ thật đã được pandoc thoát nên dấu không thoát là dấu định dạng
                continue;
            } else if (c == '~' && i + 1 < s.length() && s.charAt(i + 1) == '~') {
                // ~~gạch ngang~~: bỏ cả hai dấu của mỗi cặp, giữ chữ
                i++;
            } else if (c == '~' || c == '^') {
                // x^2^ (chỉ số trên) và H~2~O (chỉ số dưới): giữ nghĩa bằng ký hiệu x^2 và H_2O, bỏ dấu đóng của cặp.
                // Dấu lẻ không có cặp (không khoảng trắng bên trong) là chữ thường.
                int close = scriptEnd(s, i, c);
                if (close < 0) {
                    out.append(c);
                } else {
                    out.append(c == '^' ? '^' : '_').append(inline(s.substring(i + 1, close)));
                    i = close;
                }
            } else if (c == '_') {
                boolean inWord = i > 0 && i + 1 < s.length() && Character.isLetterOrDigit(s.charAt(i - 1))
                        && Character.isLetterOrDigit(s.charAt(i + 1));
                if (inWord) {
                    out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
