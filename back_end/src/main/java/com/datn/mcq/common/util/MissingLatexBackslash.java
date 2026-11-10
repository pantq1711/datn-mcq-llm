package com.datn.mcq.common.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thêm lại dấu "\" cho lệnh LaTeX bị mất dấu, CHỈ BÊN TRONG $...$ và $$...$$ (9/11, đề 22 câu 458: "$70^circ$" hiện
 * "70circ"). Dùng chung cho bước định dạng khi lưu câu (LlmOutputRepair, nhóm tự sửa, không bật cờ "cần xem kỹ") và khi xuất
 * Word (câu đã lưu từ trước cũng được sửa; chạy trước {@link DegreeSymbols} để "70^circ" thành "70^\circ" rồi "70°").
 *
 * <ul>
 *   <li>Từ đứng riêng, trước nó không phải "\" hay chữ cái và sau nó không phải chữ cái: {@code circ, cdot, times, perp,
 *       parallel, leq, geq, neq, infty, angle, triangle, Delta}.</li>
 *   <li>Lệnh có đối số, chỉ khi ngay sau là "{" hoặc "[": {@code frac, dfrac, sqrt, widehat, overline}.</li>
 * </ul>
 * Cố ý KHÔNG xử lý từ ngắn (in, le, ge, ne, pi, pm, div, cap, cup) vì dễ là tích các biến ("pin", "a in B"). Nội dung của
 * {@code \text{...}}, {@code \textbf{...}}, {@code \textit{...}}, {@code \mathrm{...}}, {@code \mbox{...}} là chữ thường nên không
 * đụng tới. Cách xác định trong/ngoài $ như {@link DegreeSymbols} ("\" bỏ qua ký tự đi sau, "$" không đóng thì không đụng tới
 * phần còn lại). Không có gì để sửa thì trả về đúng đối tượng chuỗi ban đầu.
 */
public final class MissingLatexBackslash {

    /** Từ đứng riêng (không theo sau bởi chữ cái) hoặc lệnh có đối số (theo sau bởi "{" hay "["). */
    private static final Pattern MISSING = Pattern.compile(
            "(?<![\\\\A-Za-z])(?:(circ|cdot|times|perp|parallel|leq|geq|neq|infty|angle|triangle|Delta)(?![A-Za-z])"
                    + "|(frac|dfrac|sqrt|widehat|overline)(?=[{\\[]))");

    /** Lệnh mà đối số là chữ thường: nội dung trong {...} không được sửa. */
    private static final Pattern TEXT_GROUP = Pattern.compile("\\\\(?:textbf|textit|text|mathrm|mbox)\\{");

    /**
     * @param text     văn bản sau khi sửa
     * @param fixed    số lệnh đã thêm "\"
     * @param commands tên các lệnh đã sửa theo thứ tự gặp, không lặp ("circ", "times"...)
     */
    public record Result(String text, int fixed, List<String> commands) {
        public Result {
            commands = List.copyOf(commands);
        }
    }

    private MissingLatexBackslash() {
    }

    /** Cảnh báo tự sửa (cùng tiền tố "json: đã sửa" nên không bật "cần xem kỹ"); null khi không sửa gì. */
    public static String warning(String field, Result r) {
        if (r.fixed() == 0) {
            return null;
        }
        return "json: đã sửa " + r.fixed() + " lệnh LaTeX thiếu \\ (" + String.join(", ", r.commands()) + ") ở " + field;
    }

    public static Result repair(String text) {
        if (text == null || text.indexOf('$') < 0) {
            return new Result(text, 0, List.of());
        }
        StringBuilder sb = null; // chỉ tạo khi có chỗ đổi
        int copied = 0;          // text[0, copied) đã nằm trong sb
        int fixed = 0;
        LinkedHashSet<String> commands = new LinkedHashSet<>();
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
            int close = findMathClose(text, i + open);
            if (close < 0) {
                break;
            }
            String inner = text.substring(i + open, close);
            List<String> found = new ArrayList<>();
            String fixedInner = fix(inner, found);
            if (!found.isEmpty()) {
                if (sb == null) {
                    sb = new StringBuilder(n + 8);
                }
                sb.append(text, copied, i + open).append(fixedInner);
                copied = close;
                fixed += found.size();
                commands.addAll(found);
            }
            boolean two = display && close + 1 < n && text.charAt(close + 1) == '$';
            i = close + (two ? 2 : 1);
        }
        if (sb == null) {
            return new Result(text, 0, List.of());
        }
        return new Result(sb.append(text, copied, n).toString(), fixed, new ArrayList<>(commands));
    }

    /** Công thức {@code math} (không gồm dấu $) với "\" đã thêm; {@code found} nhận tên từng lệnh đã sửa. */
    private static String fix(String math, List<String> found) {
        if (math.length() < 3) { // lệnh ngắn nhất (leq, geq, neq) dài 3 chữ
            return math;
        }
        List<int[]> protectedRanges = textGroups(math);
        Matcher m = MISSING.matcher(math);
        StringBuilder out = null;
        int last = 0;
        while (m.find()) {
            if (isProtected(protectedRanges, m.start())) {
                continue;
            }
            if (out == null) {
                out = new StringBuilder(math.length() + 4);
            }
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            out.append(math, last, m.start()).append('\\').append(name);
            last = m.end();
            found.add(name);
        }
        if (out == null) {
            return math;
        }
        return out.append(math, last, math.length()).toString();
    }

    /** Các đoạn [start, end) là đối số {...} của \text, \mathrm...; ngoặc không cân thì tới hết công thức. */
    private static List<int[]> textGroups(String math) {
        List<int[]> ranges = new ArrayList<>();
        Matcher t = TEXT_GROUP.matcher(math);
        int from = 0;
        while (t.find(from)) {
            int depth = 1;
            int k = t.end();
            while (k < math.length() && depth > 0) {
                char ch = math.charAt(k);
                if (ch == '\\') {
                    k++;
                } else if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                }
                k++;
            }
            ranges.add(new int[] {t.end(), Math.min(k, math.length())});
            from = Math.min(k, math.length());
            if (from >= math.length()) {
                break;
            }
        }
        return ranges;
    }

    private static boolean isProtected(List<int[]> ranges, int pos) {
        for (int[] r : ranges) {
            if (pos >= r[0] && pos < r[1]) {
                return true;
            }
        }
        return false;
    }

    /** Vị trí "$" đóng đầu tiên từ {@code from} (bỏ qua "\" cùng ký tự đi sau nó); -1 khi không có. */
    private static int findMathClose(String text, int from) {
        for (int k = from; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == '\\') {
                k++;
            } else if (c == '$') {
                return k;
            }
        }
        return -1;
    }
}
