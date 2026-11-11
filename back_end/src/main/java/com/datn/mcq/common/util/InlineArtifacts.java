package com.datn.mcq.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ba lỗi định dạng lặp lại thấy trên máy chủ (11/11), dùng chung cho bước định dạng khi lưu câu
 * ({@code LlmOutputRepair.normalizeField}, nhóm tự sửa: cảnh báo mang tiền tố "json: đã sửa", không bật cờ "cần xem kỹ"), khi
 * giáo viên sửa câu và khi xuất Word (câu đã lưu từ trước cũng được sửa). Mỗi hàm idempotent; không có gì để sửa thì trả về
 * đúng đối tượng chuỗi ban đầu.
 *
 * <ul>
 *   <li>{@link #htmlBreaks}: {@code <br>}, {@code <br/>}, {@code <br />} (không phân biệt hoa thường) NGOÀI công thức thành
 *       xuống dòng thật; khoảng trắng hai bên bỏ đi, không sinh dòng trống thừa. Trong công thức giữ nguyên.</li>
 *   <li>{@link #arrows}: "->" và "=>" thành "⇒" ngoài công thức, thành {@code \Rightarrow} trong công thức (thêm một dấu cách
 *       nếu liền sau là ký tự khác khoảng trắng để lệnh không dính vào chữ). Không đụng "-->", "<-", "<->", "<=>", "==>",
 *       "->>", {@code \rightarrow}, và chữ trong {@code \text{...}} (và {@code \textbf}, {@code \textit}, {@code \mathrm},
 *       {@code \mbox}).</li>
 *   <li>{@link #splitMathLines}: "\\" ở mức ngoài cùng của một công thức KHÔNG có {@code \begin{...}} thành các công thức riêng
 *       trên các dòng riêng ("$a \\ b$" thành "$a$" xuống dòng "$b$"). Công thức có môi trường, "\\" nằm trong ngoặc nhọn
 *       ({@code \substack{a \\ b}}) hoặc cặp {@code \left}/{@code \right} bị chia lệch thì giữ nguyên.</li>
 * </ul>
 * Cách xác định trong/ngoài công thức: "$" mở một công thức inline, "$$" mở công thức riêng dòng (đóng bằng "$$"), "\" bỏ qua
 * ký tự đi sau ("\$" không mở công thức); "$" không có "$" đóng thì phần còn lại không bị đụng tới.
 */
public final class InlineArtifacts {

    /**
     * @param text  văn bản sau khi sửa
     * @param fixed số chỗ đã sửa (số thẻ br, số mũi tên, số "\\")
     */
    public record Result(String text, int fixed) { }

    private static final String PREFIX = "json: đã sửa ";
    private static final Pattern BR = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final String[] TEXT_COMMANDS = {"textbf", "textit", "text", "mathrm", "mbox"};

    private InlineArtifacts() {
    }

    // ---------------------------------------------------------------- cảnh báo

    /** Cảnh báo đã đổi thẻ br (tiền tố "json: đã sửa": không bật cờ); null khi không sửa gì. */
    public static String htmlBreaksWarning(String field, Result r) {
        return r.fixed() == 0 ? null : PREFIX + r.fixed() + " thẻ <br> thành xuống dòng ở " + field;
    }

    /** Cảnh báo đã đổi mũi tên viết bằng chữ; null khi không sửa gì. */
    public static String arrowsWarning(String field, Result r) {
        return r.fixed() == 0 ? null : PREFIX + r.fixed() + " mũi tên -> / => thành dấu suy ra ở " + field;
    }

    /** Cảnh báo đã tách "\\" trong công thức không có \begin; null khi không sửa gì. */
    public static String splitMathLinesWarning(String field, Result r) {
        return r.fixed() == 0 ? null
                : PREFIX + r.fixed() + " dấu \\\\ trong công thức không có \\begin (đã tách thành các công thức riêng) ở "
                + field;
    }

    // ---------------------------------------------------------------- khung chung

    /** Một công thức: {@code text[start, end)} gồm cả dấu $ mở ({@code open} ký tự) và đóng. */
    private record Span(int start, int end, int open) {
        int innerStart() {
            return start + open;
        }

        int innerEnd() {
            return end - open;
        }
    }

    /**
     * Các công thức của {@code text} theo thứ tự. {@code unclosed[0]} nhận vị trí "$" mở không có "$" đóng (phần từ đó trở đi
     * không được đụng tới), -1 khi không có.
     */
    private static List<Span> spans(String text, int[] unclosed) {
        unclosed[0] = -1;
        List<Span> out = new ArrayList<>();
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c != '$') {
                i++;
                continue;
            }
            boolean display = i + 1 < n && text.charAt(i + 1) == '$';
            int open = display ? 2 : 1;
            int close = -1;
            for (int k = i + open; k < n; k++) {
                char d = text.charAt(k);
                if (d == '\\') {
                    k++;
                } else if (d == '$' && (!display || (k + 1 < n && text.charAt(k + 1) == '$'))) {
                    close = k;
                    break;
                }
            }
            if (close < 0) {
                unclosed[0] = i;
                break;
            }
            out.add(new Span(i, close + open, open));
            i = close + open;
        }
        return out;
    }

    // ---------------------------------------------------------------- <br>

    public static Result htmlBreaks(String text) {
        if (text == null || text.indexOf('<') < 0) {
            return new Result(text, 0);
        }
        int[] unclosed = new int[1];
        List<Span> spans = spans(text, unclosed);
        int limit = unclosed[0] >= 0 ? unclosed[0] : text.length();
        StringBuilder sb = new StringBuilder(text.length());
        int fixed = 0;
        int pos = 0;
        for (Span span : spans) {
            fixed += breaksIn(text, pos, span.start(), sb);
            sb.append(text, span.start(), span.end());
            pos = span.end();
        }
        fixed += breaksIn(text, pos, limit, sb);
        sb.append(text, limit, text.length());
        return fixed == 0 ? new Result(text, 0) : new Result(sb.toString(), fixed);
    }

    /** Đoạn ngoài công thức {@code text[from, to)}: thẻ br thành xuống dòng. Trả số thẻ đã đổi. */
    private static int breaksIn(String text, int from, int to, StringBuilder sb) {
        int fixed = 0;
        int i = from;
        while (i < to) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < to) {
                sb.append(c).append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '<') {
                Matcher m = BR.matcher(text).region(i, to);
                if (m.lookingAt()) {
                    int end = m.end();
                    while (end < to && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
                        end++;
                    }
                    int keep = sb.length();
                    while (keep > 0 && (sb.charAt(keep - 1) == ' ' || sb.charAt(keep - 1) == '\t')) {
                        keep--;
                    }
                    sb.setLength(keep);
                    boolean newlineBefore = keep == 0 || sb.charAt(keep - 1) == '\n';
                    boolean newlineAfter = end < to && text.charAt(end) == '\n';
                    if (!newlineBefore && !newlineAfter) {
                        sb.append('\n');
                    }
                    fixed++;
                    i = end;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return fixed;
    }

    // ---------------------------------------------------------------- -> và =>

    public static Result arrows(String text) {
        if (text == null || (text.indexOf("->") < 0 && text.indexOf("=>") < 0)) {
            return new Result(text, 0);
        }
        int[] unclosed = new int[1];
        List<Span> spans = spans(text, unclosed);
        int limit = unclosed[0] >= 0 ? unclosed[0] : text.length();
        StringBuilder sb = new StringBuilder(text.length() + 16);
        int[] fixed = new int[1];
        int pos = 0;
        for (Span span : spans) {
            arrowsOutside(text, pos, span.start(), sb, fixed);
            sb.append(text, span.start(), span.innerStart());
            arrowsInside(text, span.innerStart(), span.innerEnd(), sb, fixed);
            sb.append(text, span.innerEnd(), span.end());
            pos = span.end();
        }
        arrowsOutside(text, pos, limit, sb, fixed);
        sb.append(text, limit, text.length());
        return fixed[0] == 0 ? new Result(text, 0) : new Result(sb.toString(), fixed[0]);
    }

    /** Độ dài (2) của mũi tên "->" hoặc "=>" bắt đầu tại {@code i} trong đoạn {@code [from, to)}, 0 nếu không phải. */
    private static int arrowAt(String text, int i, int from, int to) {
        if (i + 1 >= to) {
            return 0;
        }
        char c0 = text.charAt(i);
        char c1 = text.charAt(i + 1);
        char prev = i > from ? text.charAt(i - 1) : 0;
        char next = i + 2 < to ? text.charAt(i + 2) : 0;
        if (c0 == '-' && c1 == '>') {
            return prev != '-' && prev != '<' && prev != '|' && next != '>' ? 2 : 0;
        }
        if (c0 == '=' && c1 == '>') {
            return prev != '=' && prev != '<' && prev != '>' && next != '>' && next != '=' ? 2 : 0;
        }
        return 0;
    }

    private static void arrowsOutside(String text, int from, int to, StringBuilder sb, int[] fixed) {
        int i = from;
        while (i < to) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < to) {
                sb.append(c).append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (arrowAt(text, i, from, to) > 0) {
                sb.append('⇒');
                fixed[0]++;
                i += 2;
                continue;
            }
            sb.append(c);
            i++;
        }
    }

    private static void arrowsInside(String text, int from, int to, StringBuilder sb, int[] fixed) {
        int textDepth = 0; // > 0: đang trong đối số của \text{...} (đếm ngoặc), chữ thường nên không đụng tới
        int i = from;
        while (i < to) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < to) {
                String command = textDepth == 0 ? textCommandAt(text, i + 1, to) : null;
                if (command != null) {
                    int brace = i + 1 + command.length();
                    sb.append(text, i, brace + 1);
                    textDepth = 1;
                    i = brace + 1;
                    continue;
                }
                sb.append(c).append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (textDepth > 0) {
                if (c == '{') {
                    textDepth++;
                } else if (c == '}') {
                    textDepth--;
                }
                sb.append(c);
                i++;
                continue;
            }
            if (arrowAt(text, i, from, to) > 0) {
                sb.append("\\Rightarrow");
                if (i + 2 < to && !Character.isWhitespace(text.charAt(i + 2))) {
                    sb.append(' ');
                }
                fixed[0]++;
                i += 2;
                continue;
            }
            sb.append(c);
            i++;
        }
    }

    /** Tên lệnh chữ ({@code text}, {@code mathrm}...) có đối số "{" bắt đầu tại {@code from} (ngay sau "\"), hoặc null. */
    private static String textCommandAt(String text, int from, int to) {
        for (String name : TEXT_COMMANDS) {
            if (text.startsWith(name, from) && from + name.length() < to && text.charAt(from + name.length()) == '{') {
                return name;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- "\\" trong công thức không có \begin

    public static Result splitMathLines(String text) {
        if (text == null || !text.contains("\\\\") || !text.contains("$")) {
            return new Result(text, 0);
        }
        int[] unclosed = new int[1];
        List<Span> spans = spans(text, unclosed);
        StringBuilder sb = null;
        int copied = 0;
        int fixed = 0;
        for (Span span : spans) {
            String inner = text.substring(span.innerStart(), span.innerEnd());
            int[] breaks = topLevelBreaks(inner);
            if (breaks.length == 0) {
                continue;
            }
            List<String> parts = splitParts(inner, breaks);
            if (parts == null) {
                continue;
            }
            String delim = text.substring(span.start(), span.innerStart());
            StringBuilder replacement = new StringBuilder();
            for (String part : parts) {
                if (replacement.length() > 0) {
                    replacement.append('\n');
                }
                replacement.append(delim).append(part).append(delim);
            }
            if (sb == null) {
                sb = new StringBuilder(text.length() + 8);
            }
            sb.append(text, copied, span.start()).append(replacement);
            copied = span.end();
            fixed += breaks.length;
        }
        if (sb == null) {
            return new Result(text, 0);
        }
        return new Result(sb.append(text, copied, text.length()).toString(), fixed);
    }

    /**
     * Vị trí các "\\" ở mức ngoặc nhọn 0 của công thức {@code inner}; rỗng khi không có, hoặc khi công thức có
     * {@code \begin} (môi trường căn cột: "\\" là ngắt dòng của nó).
     */
    private static int[] topLevelBreaks(String inner) {
        if (!inner.contains("\\\\") || inner.contains("\\begin")) {
            return new int[0];
        }
        List<Integer> found = new ArrayList<>();
        int depth = 0;
        int n = inner.length();
        for (int i = 0; i < n; i++) {
            char c = inner.charAt(i);
            if (c == '\\' && i + 1 < n) {
                if (inner.charAt(i + 1) == '\\' && depth == 0) {
                    found.add(i);
                }
                i++;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        return found.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Các phần (đã trim, bỏ phần rỗng) của {@code inner} cắt tại {@code breaks}; null khi không nên tách: không còn phần
     * nào, hoặc một phần có {@code \left} và {@code \right} lệch nhau (cắt giữa cặp ngoặc co giãn).
     */
    private static List<String> splitParts(String inner, int[] breaks) {
        List<String> parts = new ArrayList<>();
        int from = 0;
        for (int b : breaks) {
            parts.add(inner.substring(from, b));
            from = b + 2;
        }
        parts.add(inner.substring(from));
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (count(part, "\\left") != count(part, "\\right")) {
                return null;
            }
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + what.length())) {
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- gộp (xuất Word)

    /** Cả ba quy tắc theo thứ tự {@link #htmlBreaks}, {@link #arrows}, {@link #splitMathLines}, không ghi cảnh báo. */
    public static String normalize(String text) {
        String s = htmlBreaks(text).text();
        s = arrows(s).text();
        return splitMathLines(s).text();
    }
}
