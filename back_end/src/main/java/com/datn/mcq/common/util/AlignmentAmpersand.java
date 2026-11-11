package com.datn.mcq.common.util;

/**
 * Bỏ dấu căn lề "&" nằm trong công thức KHÔNG có môi trường {@code \begin{...}}, CHỈ BÊN TRONG $...$ và $$...$$ (11/11, đề 1
 * câu 8: lời giải viết từng bước "&= 5x \cdot x - 5x \cdot 2y" như trong {@code aligned}, bước bọc dòng công thức khi lưu câu
 * thành "$&= 5x \cdot x - 5x \cdot 2y$"; texmath báo "unexpected '&'", pandoc để công thức ở dạng chữ và xuất MathType rơi về
 * cả file Equation). "&" chỉ có nghĩa trong môi trường căn cột ({@code aligned}, {@code cases}, {@code array}...), nên ở ngoài
 * môi trường bỏ đi không đổi nghĩa: "$&= 5x(x - 2y)$" thành "$= 5x(x - 2y)$", "$a &= b$" thành "$a = b$".
 *
 * <p>Dùng chung cho bước định dạng khi lưu câu (LlmOutputRepair, nhóm tự sửa, không bật cờ "cần xem kỹ") và khi xuất Word
 * (câu đã lưu từ trước cũng được sửa), cạnh {@link MissingLatexBackslash} và {@link DegreeSymbols}.
 *
 * <p>Giữ nguyên: công thức có {@code \begin} (mọi "&" trong đó), {@code \&} (dấu & thật), "&" trong đối số của
 * {@code \text{...}}, {@code \textbf}, {@code \textit}, {@code \mathrm}, {@code \mbox} (chữ thường), "&" ngoài công thức, và
 * công thức chỉ có "&" (bỏ đi thì thành công thức rỗng). Cách xác định trong/ngoài $ như {@link MissingLatexBackslash} ("\" bỏ
 * qua ký tự đi sau, "$" không đóng thì không đụng tới phần còn lại). Không có gì để sửa thì trả về đúng đối tượng chuỗi ban đầu.
 */
public final class AlignmentAmpersand {

    private static final String[] TEXT_COMMANDS = {"textbf", "textit", "text", "mathrm", "mbox"};

    /**
     * @param text  văn bản sau khi sửa
     * @param fixed số dấu "&" đã bỏ
     */
    public record Result(String text, int fixed) { }

    private AlignmentAmpersand() {
    }

    /** Cảnh báo tự sửa (cùng tiền tố "json: đã sửa" nên không bật "cần xem kỹ"); null khi không sửa gì. */
    public static String warning(String field, Result r) {
        if (r.fixed() == 0) {
            return null;
        }
        return "json: đã sửa " + r.fixed() + " dấu & căn lề ngoài môi trường \\begin ở " + field;
    }

    public static Result repair(String text) {
        if (text == null || text.indexOf('$') < 0 || text.indexOf('&') < 0) {
            return new Result(text, 0);
        }
        StringBuilder sb = null; // chỉ tạo khi có chỗ đổi
        int copied = 0;          // text[0, copied) đã nằm trong sb
        int fixed = 0;
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
            int[] removed = new int[1];
            String fixedInner = fix(inner, removed);
            if (removed[0] > 0) {
                if (sb == null) {
                    sb = new StringBuilder(n);
                }
                sb.append(text, copied, i + open).append(fixedInner);
                copied = close;
                fixed += removed[0];
            }
            boolean two = display && close + 1 < n && text.charAt(close + 1) == '$';
            i = close + (two ? 2 : 1);
        }
        if (sb == null) {
            return new Result(text, 0);
        }
        return new Result(sb.append(text, copied, n).toString(), fixed);
    }

    /** Công thức {@code math} (không gồm dấu $) đã bỏ "&" căn lề; {@code removed[0]} nhận số dấu đã bỏ. */
    private static String fix(String math, int[] removed) {
        if (math.indexOf('&') < 0 || math.contains("\\begin")) {
            return math;
        }
        StringBuilder out = new StringBuilder(math.length());
        int count = 0;
        int textDepth = 0; // > 0: đang trong đối số của \text{...} (đếm ngoặc)
        int n = math.length();
        for (int k = 0; k < n; k++) {
            char c = math.charAt(k);
            if (c == '\\') {
                String command = textCommandAt(math, k + 1);
                if (textDepth == 0 && command != null && k + 1 + command.length() < n
                        && math.charAt(k + 1 + command.length()) == '{') {
                    out.append(math, k, k + 2 + command.length());
                    k += 1 + command.length();
                    textDepth = 1;
                    continue;
                }
                out.append(c);
                if (k + 1 < n) {
                    out.append(math.charAt(++k));
                }
                continue;
            }
            if (textDepth > 0) {
                if (c == '{') {
                    textDepth++;
                } else if (c == '}') {
                    textDepth--;
                }
                out.append(c);
                continue;
            }
            if (c == '&') {
                count++;
                // "a & b": bỏ cả khoảng trắng đi sau để không còn hai dấu cách; "&= x" ở đầu công thức thành "= x"
                boolean spaceBefore = out.isEmpty() || Character.isWhitespace(out.charAt(out.length() - 1));
                while (spaceBefore && k + 1 < n && math.charAt(k + 1) == ' ') {
                    k++;
                }
                continue;
            }
            out.append(c);
        }
        if (count == 0 || out.toString().isBlank()) {
            return math;
        }
        removed[0] = count;
        return out.toString();
    }

    /** Tên lệnh chữ ({@code text}, {@code mathrm}...) bắt đầu tại {@code from} (ngay sau "\"), hoặc null. */
    private static String textCommandAt(String math, int from) {
        for (String name : TEXT_COMMANDS) {
            if (math.startsWith(name, from)) {
                return name;
            }
        }
        return null;
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
