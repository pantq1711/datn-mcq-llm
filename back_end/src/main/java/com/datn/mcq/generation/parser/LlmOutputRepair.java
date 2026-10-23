package com.datn.mcq.generation.parser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Sửa hai lỗi lặp lại của đầu ra LLM trước/sau khi giải mã JSON (ngày 23/10, dùng chung cho parser mcq/sa/tf, chỉ ở chế
 * độ sửa của parser, tức run production):
 * <ol>
 *   <li>{@link #repairLatexEscapes}: dấu "\" của lệnh LaTeX chưa nhân đôi trong chuỗi JSON (ví dụ "$75^\circ$").</li>
 *   <li>{@link #closeTrailingDollar}: công thức mở bằng "$" ở cuối trường mà quên "$" đóng.</li>
 * </ol>
 */
public final class LlmOutputRepair {

    private LlmOutputRepair() {}

    /** Lệnh LaTeX bắt đầu bằng chữ n được coi là lệnh (không phải xuống dòng "\n"). */
    public static final List<String> N_COMMANDS =
            List.of("neq", "ne", "neg", "nu", "not", "ni", "nabla", "nleq", "ngeq", "nparallel");

    /**
     * @param json     chuỗi JSON sau khi sửa
     * @param commands các lệnh đã sửa theo thứ tự gặp (có lặp), ví dụ "\circ"
     */
    public record EscapeRepair(String json, List<String> commands) {
        public EscapeRepair {
            commands = List.copyOf(commands);
        }

        public boolean changed() {
            return !commands.isEmpty();
        }

        /** Cảnh báo format, ví dụ {@code json: đã sửa 1 escape LaTeX (\circ)}; null khi không sửa gì. */
        public String warning() {
            if (commands.isEmpty()) {
                return null;
            }
            return "json: đã sửa " + commands.size() + " escape LaTeX ("
                    + String.join(", ", new LinkedHashSet<>(commands)) + ")";
        }
    }

    /**
     * Trong các chuỗi JSON, nhân đôi dấu "\" của lệnh LaTeX chưa được nhân đôi. Ngoài chuỗi không đụng tới.
     * <ul>
     *   <li>"\" trước ký tự không phải escape JSON hợp lệ (khác {@code " \ / b f n r t u}) → "\\".</li>
     *   <li>"\\u" (một dấu gạch rồi chữ u) không theo sau bởi 4 chữ số hex → nhân đôi dấu gạch.</li>
     *   <li>"\b", "\f", "\r", "\t" ngay trước một chữ cái ASCII → lệnh LaTeX (\beta, \frac, \right, \times...) → "\\".</li>
     *   <li>"\n" chỉ là lệnh LaTeX khi cụm chữ ASCII sau "\" đúng là một lệnh trong {@link #N_COMMANDS} và ký tự kế tiếp
     *       không phải chữ cái; còn lại giữ là xuống dòng (đã có normalizeLiteralNewlines xử lý sau).</li>
     * </ul>
     * Các escape hợp lệ khác giữ nguyên.
     */
    public static EscapeRepair repairLatexEscapes(String json) {
        if (json == null || json.indexOf('\\') < 0) {
            return new EscapeRepair(json, List.of());
        }
        StringBuilder sb = new StringBuilder(json.length() + 16);
        List<String> commands = new ArrayList<>();
        boolean inString = false;
        int n = json.length();
        int i = 0;
        while (i < n) {
            char c = json.charAt(i);
            if (!inString) {
                if (c == '"') {
                    inString = true;
                }
                sb.append(c);
                i++;
                continue;
            }
            if (c == '"') {
                inString = false;
                sb.append(c);
                i++;
                continue;
            }
            if (c != '\\' || i + 1 >= n) {
                sb.append(c);
                i++;
                continue;
            }
            char next = json.charAt(i + 1);
            String word = asciiWord(json, i + 1);
            boolean latex = switch (next) {
                case '"', '\\', '/' -> false;
                case 'u' -> !(i + 6 <= n && json.substring(i + 2, i + 6).matches("[0-9A-Fa-f]{4}"));
                case 'b', 'f', 'r', 't' -> i + 2 < n && isAsciiLetter(json.charAt(i + 2));
                case 'n' -> N_COMMANDS.contains(word)
                        && !(i + 1 + word.length() < n && Character.isLetter(json.charAt(i + 1 + word.length())));
                default -> true;
            };
            if (latex) {
                commands.add("\\" + (word.isEmpty() ? String.valueOf(next) : word));
                sb.append("\\\\").append(next);
            } else {
                sb.append(c).append(next);
            }
            i += 2;
        }
        return new EscapeRepair(sb.toString(), commands);
    }

    private static String asciiWord(String s, int start) {
        int end = start;
        while (end < s.length() && isAsciiLetter(s.charAt(end))) {
            end++;
        }
        return s.substring(start, end);
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** @param text văn bản sau khi sửa; {@code closed} true khi đã chèn "$" */
    public record DollarRepair(String text, boolean closed) {}

    private static final String END_PUNCTUATION = ".,;:!?";

    /**
     * Tự đóng "$" quên ở cuối trường: chỉ khi số dấu "$" (không tính "\$") lẻ, đoạn sau dấu "$" cuối còn nội dung và nội
     * dung đó trông như công thức (không có chữ có dấu tiếng Việt, không có từ chỉ gồm từ 2 chữ cái ASCII trở lên mà không
     * mở đầu bằng "\"). Khi đó chèn "$" ở cuối trường, trước dấu câu kết thúc (. , ; : ! ?) nếu có. Mọi trường hợp lẻ "$"
     * khác giữ nguyên để bộ kiểm tra LaTeX báo lỗi như cũ (ví dụ "$" lẻ ở giữa câu, theo sau là chữ tiếng Việt).
     */
    public static DollarRepair closeTrailingDollar(String text) {
        if (text == null) {
            return new DollarRepair(null, false);
        }
        int count = 0;
        int last = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '$' && (i == 0 || text.charAt(i - 1) != '\\')) {
                count++;
                last = i;
            }
        }
        if (count % 2 == 0) {
            return new DollarRepair(text, false);
        }
        int end = text.length();
        while (end > last + 1 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        int insert = end;
        while (insert > last + 1 && END_PUNCTUATION.indexOf(text.charAt(insert - 1)) >= 0) {
            insert--;
        }
        while (insert > last + 1 && Character.isWhitespace(text.charAt(insert - 1))) {
            insert--;
        }
        String core = text.substring(last + 1, insert);
        if (core.isBlank() || !looksLikeMath(core)) {
            return new DollarRepair(text, false);
        }
        return new DollarRepair(text.substring(0, insert) + "$" + text.substring(insert), true);
    }

    private static boolean looksLikeMath(String core) {
        for (int i = 0; i < core.length(); i++) {
            char c = core.charAt(i);
            if (c > 127 && Character.isLetter(c)) {
                return false;
            }
        }
        for (String token : core.trim().split("\\s+")) {
            String bare = token.replaceAll("^[(\\[{]+", "").replaceAll("[)\\]},.;:!?]+$", "");
            if (bare.matches("[A-Za-z]{2,}")) {
                return false;
            }
        }
        return true;
    }

    /** Cảnh báo khi đã tự đóng "$", ví dụ {@code statement_b: đã tự đóng $}. */
    public static String dollarWarning(String field) {
        return field + ": đã tự đóng $";
    }

    /** Chế độ sửa: sửa escape LaTeX trong chuỗi JSON trước khi giải mã, ghi cảnh báo nếu có sửa. */
    static String repairJson(String json, boolean repair, List<String> warnings) {
        if (!repair) {
            return json;
        }
        EscapeRepair r = repairLatexEscapes(json);
        if (r.changed()) {
            warnings.add(r.warning());
        }
        return r.json();
    }

    /**
     * Một trường văn bản đã giải mã: ở chế độ sửa thì tự đóng "$" ở cuối trường (ghi cảnh báo), rồi đổi "\n" theo nghĩa
     * đen ngoài công thức thành xuống dòng (nếu {@code newlines}) và trim.
     */
    static String textField(String field, Object val, boolean repair, boolean newlines, List<String> warnings) {
        if (val == null) {
            return null;
        }
        String s = String.valueOf(val);
        if (repair) {
            DollarRepair d = closeTrailingDollar(s);
            if (d.closed()) {
                warnings.add(dollarWarning(field));
                s = d.text();
            }
        }
        return (newlines ? McqJsonParser.normalizeLiteralNewlines(s) : s).trim();
    }
}
