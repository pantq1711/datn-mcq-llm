package com.datn.mcq.common.util;

import java.util.Set;

/**
 * Chuẩn hóa chuỗi "\n" theo nghĩa đen (dấu "\" rồi chữ n) do Gemini trả trong JSON (prompt dặn nhân đôi mọi dấu "\" nên sau
 * khi giải mã còn "\n" chữ). Dùng chung cho bước định dạng khi lưu câu (parser, chế độ sửa) và khi xuất Word (câu cũ đã lưu
 * cũng được sửa), để pandoc luôn nhận công thức đọc được: "$ .\n$" làm pandoc bỏ cả công thức, bộ chuyển MathType thấy lệch số
 * công thức và trả cả file ở dạng Equation.
 *
 * <ul>
 *   <li>"\" cùng cả từ liền sau ("\" + n + chữ cái/dấu kết hợp) là một lệnh LaTeX trong {@link #N_COMMANDS} (\neq, \notin,
 *       \nabla...): giữ nguyên.</li>
 *   <li>Còn lại là xuống dòng bị lọt: ngoài $...$ thành xuống dòng thật; trong $...$ thành một dấu cách (chữ đi sau giữ nguyên:
 *       "\nTa" thành " Ta").</li>
 *   <li>Đoạn $...$ vừa có "\n" bị thay mà rỗng hoặc chỉ còn dấu câu/khoảng trắng ("$ .$", "$ $") thì bỏ dấu $ thừa, giữ dấu
 *       câu. Chỉ xét đúng đoạn đó (đếm mở/đóng $ từng ký tự, "\$" bỏ qua), không đụng tới đoạn $...$ khác của chuỗi.</li>
 * </ul>
 * Chuỗi không có "\n" chữ cần sửa được trả về đúng đối tượng ban đầu. "\$" và "\\" được giữ nguyên khối.
 */
public final class LiteralNewlines {

    /** Lệnh LaTeX bắt đầu bằng chữ n: không phải xuống dòng. */
    public static final Set<String> N_COMMANDS = Set.of(
            "ne", "neq", "neg", "nu", "nabla", "not", "notin", "ni", "nmid", "nparallel", "nleq", "ngeq", "nless", "ngtr",
            "nexists", "natural", "newline", "nolimits", "nsubseteq", "nsupseteq", "ncong", "nsim", "nleftarrow",
            "nrightarrow", "nLeftarrow", "nRightarrow");

    /**
     * @param text         văn bản sau khi sửa
     * @param insideMath   số "\n" nằm trong $...$ đã đổi thành dấu cách
     * @param outsideMath  số "\n" nằm ngoài $...$ đã đổi thành xuống dòng
     * @param emptyPairs   số đoạn $...$ (có "\n" vừa bị thay) rỗng hoặc chỉ có dấu câu đã bỏ dấu $
     */
    public record Result(String text, int insideMath, int outsideMath, int emptyPairs) {
        /**
         * True khi có sửa đáng báo: "\n" nằm trong công thức hoặc cặp $ thừa. "\n" ngoài công thức đổi thành xuống dòng là
         * việc bình thường, không báo.
         */
        public boolean notable() {
            return insideMath > 0 || emptyPairs > 0;
        }
    }

    private LiteralNewlines() {
    }

    /** Cảnh báo tự sửa (cùng tiền tố "json: đã sửa" nên không bật "cần xem kỹ"); null khi không có gì đáng báo. */
    public static String warning(String field, Result r) {
        if (!r.notable()) {
            return null;
        }
        int total = r.insideMath() + r.emptyPairs();
        return "json: đã sửa " + total + " ký tự \\n lọt vào công thức hoặc cặp $ thừa ở " + field;
    }

    public static Result repair(String text) {
        if (text == null || !text.contains("\\n")) {
            return new Result(text, 0, 0, 0);
        }
        StringBuilder sb = new StringBuilder(text.length());
        boolean inMath = false;
        boolean display = false;     // đang ở trong $$...$$
        boolean touched = false;     // đoạn công thức hiện tại có "\n" vừa bị thay
        int spanStart = -1;          // vị trí (trong sb) của dấu $ mở đoạn hiện tại
        int inside = 0;
        int outside = 0;
        int pairs = 0;
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char next = text.charAt(i + 1);
                if (next == 'n') {
                    int end = i + 1;
                    while (end < n && isWordChar(text.charAt(end))) {
                        end++;
                    }
                    if (N_COMMANDS.contains(text.substring(i + 1, end))) {
                        sb.append(text, i, end);
                        i = end;
                        continue;
                    }
                    if (inMath) {
                        sb.append(' ');
                        inside++;
                        touched = true;
                    } else {
                        sb.append('\n');
                        outside++;
                    }
                    i += 2;
                    continue;
                }
                sb.append(c).append(next);
                i += 2;
                continue;
            }
            if (c == '$') {
                boolean doubleDollar = i + 1 < n && text.charAt(i + 1) == '$';
                if (!inMath) {
                    inMath = true;
                    display = doubleDollar;
                    touched = false;
                    spanStart = sb.length();
                    sb.append(doubleDollar ? "$$" : "$");
                    i += doubleDollar ? 2 : 1;
                } else {
                    boolean closesDisplay = display && doubleDollar;
                    if (!display && touched && isBlankOrPunctuation(sb, spanStart + 1)) {
                        // "$ .$" thành ".": bỏ dấu $ mở, không thêm dấu $ đóng
                        String inner = sb.substring(spanStart + 1).strip();
                        sb.setLength(spanStart);
                        sb.append(inner);
                        pairs++;
                    } else {
                        sb.append(closesDisplay ? "$$" : "$");
                    }
                    inMath = false;
                    display = false;
                    touched = false;
                    i += closesDisplay ? 2 : 1;
                }
                continue;
            }
            sb.append(c);
            i++;
        }
        if (inside == 0 && outside == 0) {
            return new Result(text, 0, 0, 0); // chỉ có lệnh \neq, \nabla...: giữ nguyên chuỗi gốc
        }
        return new Result(sb.toString(), inside, outside, pairs);
    }

    /** Chữ cái Unicode hoặc dấu kết hợp: "nếu" viết dạng NFD (n, e, dấu mũ, dấu sắc, u) không bị đọc thành lệnh \ne. */
    private static boolean isWordChar(char c) {
        if (Character.isLetter(c)) {
            return true;
        }
        int type = Character.getType(c);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    /** True khi sb từ {@code from} đến hết chỉ gồm khoảng trắng và dấu câu (hoặc rỗng). */
    private static boolean isBlankOrPunctuation(CharSequence sb, int from) {
        for (int k = from; k < sb.length(); k++) {
            char c = sb.charAt(k);
            if (!Character.isWhitespace(c) && ".,;:!?".indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}
