package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.LatexValidationRules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sửa các lỗi lặp lại của đầu ra LLM trước/sau khi giải mã JSON (ngày 23/10, dùng chung cho parser mcq/sa/tf, chỉ ở chế
 * độ sửa của parser, tức run production):
 * <ol>
 *   <li>{@link #repairLatexEscapes}: dấu "\" của lệnh LaTeX chưa nhân đôi trong chuỗi JSON (ví dụ "$75^\circ$").</li>
 *   <li>{@link #closeTrailingDollar}: công thức mở bằng "$" ở cuối trường mà quên "$" đóng.</li>
 *   <li>{@link #stripTextOutsideMath} (27/10): vỏ {@code \text{...}} của đơn vị đo viết ngoài $ ("$40$ \text{km/h}").</li>
 *   <li>{@link #fixDegreeSymbol} (31/10): ký hiệu độ viết {@code ^\bullet}/{@code ^\triangle} thay cho {@code ^{\circ}}.</li>
 *   <li>{@link #wrapLatexOutsideMath} (2/11): biểu thức LaTeX liền mạch nằm ngoài $...$ (không áp cho trường {@code answer}).</li>
 *   <li>{@link #wrapFormulaLines} (4/11): dòng công thức của lời giải viết ngoài $ được bọc NGUYÊN dòng; dòng bị chia bởi $
 *       giữa biểu thức ({@link #hasBrokenFormulaLine}) không tự sửa mà bị FormatChecker báo lỗi (câu được sinh lại).</li>
 * </ol>
 */
public final class LlmOutputRepair {

    private LlmOutputRepair() {}

    /**
     * Tiền tố cảnh báo đã sửa escape LaTeX. Cảnh báo này vẫn lưu ở warnings (V5) nhưng không bật "cần xem kỹ"
     * (ngày 23/10, đợt 2): sửa escape không đổi nghĩa công thức.
     */
    public static final String ESCAPE_WARNING_PREFIX = "json: đã sửa ";

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
            return ESCAPE_WARNING_PREFIX + commands.size() + " escape LaTeX ("
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

    /** Số dấu "$" chưa thoát (không tính "\$") trong {@code text}. */
    static int unescapedDollars(String text) {
        int count = 0;
        for (int i = 0; text != null && i < text.length(); i++) {
            if (text.charAt(i) == '$' && (i == 0 || text.charAt(i - 1) != '\\')) {
                count++;
            }
        }
        return count;
    }

    /** Tiền tố chung của mọi cảnh báo tự đóng "$" (sau tên trường). */
    static final String DOLLAR_WARNING_SUFFIX = ": đã tự đóng $";

    /**
     * Cảnh báo khi đã tự đóng "$". F-02: chuỗi gốc {@code original} có ĐÚNG 1 dấu "$" chưa thoát (quên đóng một công thức) thì
     * là {@code statement_b: đã tự đóng $}, thuộc nhóm tự sửa chắc chắn, KHÔNG bật cờ "cần xem kỹ". Từ 3 dấu trở lên, vị trí
     * đóng có thể sai nên cảnh báo mang tên khác ({@code statement_b: đã tự đóng $ khi chuỗi có 3 dấu $ (có thể đóng sai
     * chỗ)}) và bật cờ.
     */
    public static String dollarWarning(String field, String original) {
        int count = unescapedDollars(original);
        return count <= 1 ? field + DOLLAR_WARNING_SUFFIX
                : field + DOLLAR_WARNING_SUFFIX + " khi chuỗi có " + count + " dấu $ (có thể đóng sai chỗ)";
    }

    /** @param text văn bản sau khi sửa; {@code removed} là số {@code \text{...}} đã gỡ vỏ */
    public record TextRepair(String text, int removed) {}

    private static final String TEXT_COMMAND = "\\text{";

    /**
     * Gỡ vỏ {@code \text{X}} nằm NGOÀI cặp $...$ thành X đã trim (model hay viết đơn vị đo như "$40$ \text{km/h}"). Cách
     * xác định trong/ngoài $ theo {@code LatexValidationRules}: "$" mở/đóng, "$$" là công thức riêng dòng, "\$" và mọi ký
     * hiệu "\" khác được bỏ qua nguyên khối. Chỉ xử lý đúng lệnh \text ngay sau là "{" (không đụng \textbf, \textit...).
     * Giữ nguyên khi ngoặc nhọn không cân, hoặc X chứa "\" hoặc "$" để bộ kiểm tra báo lỗi như cũ. Nếu ký tự đứng trước
     * không phải khoảng trắng hay ngoặc mở và X không rỗng thì chèn đúng một dấu cách ("$6$\text{km}" thành "$6$ km").
     */
    public static TextRepair stripTextOutsideMath(String text) {
        if (text == null || !text.contains(TEXT_COMMAND)) {
            return new TextRepair(text, 0);
        }
        StringBuilder sb = new StringBuilder(text.length());
        boolean inMath = false;
        boolean display = false;
        int removed = 0;
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\') {
                int close = inMath || !text.startsWith(TEXT_COMMAND, i)
                        ? -1
                        : findTextClose(text, i + TEXT_COMMAND.length());
                if (close < 0) {
                    sb.append(c);
                    if (i + 1 < n) {
                        sb.append(text.charAt(i + 1));
                    }
                    i += 2;
                    continue;
                }
                String inner = text.substring(i + TEXT_COMMAND.length(), close).trim();
                if (!inner.isEmpty() && sb.length() > 0) {
                    char prev = sb.charAt(sb.length() - 1);
                    if (!Character.isWhitespace(prev) && "([{".indexOf(prev) < 0) {
                        sb.append(' ');
                    }
                }
                sb.append(inner);
                removed++;
                i = close + 1;
                continue;
            }
            if (c == '$') {
                boolean doubleDollar = i + 1 < n && text.charAt(i + 1) == '$';
                if (!inMath) {
                    display = doubleDollar;
                    inMath = true;
                    sb.append(display ? "$$" : "$");
                    i += display ? 2 : 1;
                } else {
                    inMath = false;
                    boolean two = display && doubleDollar;
                    sb.append(two ? "$$" : "$");
                    i += two ? 2 : 1;
                }
                continue;
            }
            sb.append(c);
            i++;
        }
        return removed == 0 ? new TextRepair(text, 0) : new TextRepair(sb.toString(), removed);
    }

    /** Vị trí "}" đóng của lệnh \text mở ngay trước {@code from}; -1 nếu ngoặc không cân hoặc nội dung có "\" hoặc "$". */
    private static int findTextClose(String text, int from) {
        int depth = 1;
        for (int k = from; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == '\\' || c == '$') {
                return -1;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return k;
            }
        }
        return -1;
    }

    /**
     * Cảnh báo khi đã gỡ \text ngoài $. Cùng tiền tố với cảnh báo sửa escape nên lưu ở warnings, hiện trong vết QC như
     * một lần tự sửa và không bật "cần xem kỹ" (gỡ vỏ không đổi nghĩa).
     */
    public static String textWarning(String field, int removed) {
        return ESCAPE_WARNING_PREFIX + removed + " \\text{} ngoài $ (đã gỡ vỏ) ở " + field;
    }

    /** @param text văn bản sau khi sửa; {@code fixed} là số ký hiệu độ đã đổi thành {@code ^{\circ}} */
    public record DegreeRepair(String text, int fixed) {}

    private static final String DEGREE = "^{\\circ}";

    /**
     * Lệnh model hay viết thay cho {@code \circ} (31/10: bullet, triangle; 2/11, đề 11 câu 190 và 192: toRadians, rceil, và
     * các biến thể lceil, degree, deg, circle, o). Không gồm {@code prime}, {@code circ} và {@code cdot} (F-02: "\cdot" là
     * dấu nhân, "30^\cdot" có thể là lũy thừa chưa viết xong; đổi thành độ sẽ đổi nghĩa mà không bật cờ).
     */
    private static final String WRONG_DEGREE_COMMANDS = "(?:bullet|triangle|toRadians|rceil|lceil|degree|deg|circle|o)";

    /** Chữ số hoặc "}" rồi "^" rồi một lệnh trong {@link #WRONG_DEGREE_COMMANDS}, có hoặc không có {...} và khoảng trắng. */
    private static final Pattern WRONG_DEGREE = Pattern.compile(
            "([0-9}])\\s*\\^\\s*(?:\\\\" + WRONG_DEGREE_COMMANDS + "(?![A-Za-z])|\\{\\s*\\\\" + WRONG_DEGREE_COMMANDS
                    + "(?![A-Za-z])\\s*\\})");

    /**
     * Đổi ký hiệu độ viết sai thành {@code ^{\circ}} (31/10, thẩm định nội dung: "75•", "85△" ở 6 câu). CHỈ bên trong cặp
     * $...$ và chỉ khi ngay sau một chữ số hoặc "}" là "^" rồi một lệnh trong {@link #WRONG_DEGREE_COMMANDS} (bullet,
     * triangle, từ 2/11 thêm toRadians, rceil, lceil, degree, deg, circle, o; dạng {@code ^\X} hoặc {@code ^{\X}},
     * cho phép khoảng trắng quanh "^"): "$75^\bullet$" thành "$75^{\circ}$". Ngoài ngữ cảnh đó giữ nguyên
     * ({@code \triangle ABC}, {@code a^\bullet}, {@code x^{\prime}}, {@code \bullet} ngoài công thức). Cách xác định trong/ngoài $ như
     * {@link #stripTextOutsideMath}; công thức không có "$" đóng thì không đụng tới.
     */
    public static DegreeRepair fixDegreeSymbol(String text) {
        if (text == null || text.indexOf('^') < 0 || text.indexOf('\\') < 0) {
            return new DegreeRepair(text, 0);
        }
        StringBuilder sb = new StringBuilder(text.length());
        int fixed = 0;
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\') {
                sb.append(c);
                if (i + 1 < n) {
                    sb.append(text.charAt(i + 1));
                }
                i += 2;
                continue;
            }
            if (c != '$') {
                sb.append(c);
                i++;
                continue;
            }
            boolean display = i + 1 < n && text.charAt(i + 1) == '$';
            int open = display ? 2 : 1;
            int close = findMathClose(text, i + open);
            if (close < 0) {
                sb.append(text, i, n);
                break;
            }
            String inner = text.substring(i + open, close);
            Matcher m = WRONG_DEGREE.matcher(inner);
            StringBuilder math = new StringBuilder();
            int last = 0;
            while (m.find()) {
                math.append(inner, last, m.start()).append(m.group(1)).append(DEGREE);
                last = m.end();
                fixed++;
            }
            math.append(inner, last, inner.length());
            boolean two = display && close + 1 < n && text.charAt(close + 1) == '$';
            sb.append(display ? "$$" : "$").append(math).append(two ? "$$" : "$");
            i = close + (two ? 2 : 1);
        }
        return fixed == 0 ? new DegreeRepair(text, 0) : new DegreeRepair(sb.toString(), fixed);
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

    /**
     * Cảnh báo khi đã đổi ký hiệu độ. Cùng tiền tố với cảnh báo sửa escape nên lưu ở warnings, hiện trong vết QC như một
     * lần tự sửa và không bật "cần xem kỹ" (ý định luôn là độ, không phải khối lượng hay hình tam giác).
     */
    public static String degreeWarning(String field, int fixed) {
        return ESCAPE_WARNING_PREFIX + fixed + " ký hiệu độ (^\\bullet, ^\\triangle thành ^{\\circ}) ở " + field;
    }

    /** @param text văn bản sau khi sửa; {@code wrapped} là số biểu thức đã được bọc $...$ */
    public record WrapRepair(String text, int wrapped) {}

    /** Lệnh có trong danh sách cho phép nhưng không là mỏ neo: \text do {@link #stripTextOutsideMath} lo, \begin/\end cần cả khối. */
    private static final Set<String> NOT_ANCHOR = Set.of("text", "begin", "end");
    private static final String SENTENCE_END = ".!?;";
    private static final String TRAILING_PUNCTUATION = ".,;:!?";

    /**
     * Bọc $...$ quanh biểu thức LaTeX nằm NGOÀI cặp $...$ (2/11, thẩm định UX đề 10: lời giải câu 2 Phần I có
     * "\frac{5x + y}{xy}" không nằm trong $). Mỏ neo là lệnh trong danh sách cho phép của {@link LatexValidationRules} (trừ
     * \text, \begin, \end) hoặc mũ/chỉ số viết bằng ngoặc nhọn ("^" hay "_" liền sau là "{"). Từ mỏ neo mở rộng sang hai phía qua các "từ" (tách theo khoảng trắng
     * ở ngoài ngoặc nhọn) trông như công thức: không có chữ ngoài ASCII (tiếng Việt), không phải từ chỉ gồm từ 2 chữ cái ASCII
     * trở lên (từ tiếng Anh, "Ta", "Cho"), không có lệnh lạ, ngoặc nhọn cân; không đi qua từ kết thúc câu (. ! ? ;). Dấu câu
     * cuối biểu thức nằm ngoài $; nếu sát một cặp $ khác thì chèn dấu cách để không thành "$$".
     *
     * <p>Không chắc thì giữ nguyên (bộ kiểm tra LaTeX báo lỗi, câu được sinh lại): số dấu $ (không tính "\$") lẻ; ngoặc nhọn
     * lệch; lệnh lạ; "\n" (gạch rồi n) không phải lệnh. Mỗi biểu thức liền mạch chỉ bọc một lần dù có nhiều mỏ neo.
     */
    public static WrapRepair wrapLatexOutsideMath(String text) {
        if (text == null || (text.indexOf('\\') < 0 && !text.contains("^{") && !text.contains("_{"))) {
            return new WrapRepair(text, 0);
        }
        List<int[]> outside = outsideMathSpans(text);
        if (outside == null) {
            return new WrapRepair(text, 0);
        }
        StringBuilder sb = new StringBuilder(text.length() + 8);
        int wrapped = 0;
        int copied = 0;
        for (int[] span : outside) {
            int start = span[0];
            int end = span[1];
            sb.append(text, copied, start);
            boolean afterDollar = start > 0;
            boolean beforeDollar = end < text.length();
            int[] count = new int[1];
            sb.append(wrapSegment(text.substring(start, end), afterDollar, beforeDollar, count));
            wrapped += count[0];
            copied = end;
        }
        sb.append(text, copied, text.length());
        return wrapped == 0 ? new WrapRepair(text, 0) : new WrapRepair(sb.toString(), wrapped);
    }

    /** Cảnh báo khi đã bọc $, ví dụ {@code explanation: đã tự bọc $ quanh công thức LaTeX nằm ngoài $}. */
    public static String wrapWarning(String field, int wrapped) {
        return field + ": đã tự bọc $ quanh " + (wrapped > 1 ? wrapped + " công thức" : "công thức") + " LaTeX nằm ngoài $";
    }

    /** @param text lời giải sau khi sửa; {@code wrapped} là số dòng đã được bọc nguyên dòng bằng $...$ */
    public record FormulaLineRepair(String text, int wrapped) {}

    /**
     * Thông báo lỗi khi một dòng công thức của lời giải bị chia bởi "$" giữa biểu thức ({@code P $= … \cdot$ (-3)}): không
     * biết chỗ đóng đúng nằm đâu nên không tự sửa; câu được sinh lại. Công khai để FormatChecker báo lỗi chặn cho đầu ra LLM.
     */
    public static final String MSG_FORMULA_LINE_BROKEN =
            "Có dòng công thức bị chia bởi dấu $ giữa biểu thức (thiếu hoặc thừa $)";

    /** Dấu hết câu đứng cuối dòng công thức: nằm ngoài $ ("$= 4(x - 2)(x + 2)$."). */
    private static final String LINE_TAIL_PUNCTUATION = ".,;:!?";
    /** Ký hiệu cho biết dòng là công thức: dấu quan hệ, phép cộng, mũ, chỉ số, so sánh (không tính chữ số trần). */
    private static final String FORMULA_SYMBOLS = "=+^_<>";
    private static final Pattern LATEX_COMMAND = Pattern.compile("\\\\([A-Za-z]+)");
    /** Đầu dòng liệt kê: "- ", "1. ", "a) ", "(b) ": không phải công thức dù theo sau có ký hiệu toán. */
    private static final Pattern LIST_MARKER = Pattern.compile("^\\s*(?:[-*•+]\\s+|\\(?\\d+[.)]\\s+|\\(?[a-dA-D][.)]\\s*)");

    /**
     * Bọc NGUYÊN dòng bằng $...$ cho dòng công thức viết ngoài $ trong lời giải (4/11, đo đề 16 ô 37: quy tắc "mỗi bước một
     * dòng" làm model viết "P = -5x^2(x - 3)" không có $). Tách theo "\n". Dòng được bọc khi: không có "$" nào; không có chữ
     * tiếng Việt (chữ ngoài ASCII) và không có từ chữ cái ASCII dài từ 2 ký tự (trừ tên in hoa 2-4 chữ như "AB", "ABC" và
     * tên lệnh LaTeX); mọi lệnh LaTeX nằm trong danh sách cho phép (không tính \text, \begin, \end); có ký hiệu toán ({@code =
     * + ^ _ < >}) hoặc lệnh LaTeX; ngoặc nhọn cân; không mở đầu bằng nhãn liệt kê ("a)", "1.", "- "). Dấu hết câu ở cuối dòng
     * ({@code . , ; : ! ?}) ở lại ngoài $. Dòng bắt đầu bằng "=" cũng được bọc nguyên dòng ({@code $= 4(x - 2)$}). Không chắc
     * thì giữ nguyên.
     */
    public static FormulaLineRepair wrapFormulaLines(String text) {
        // "$$" (công thức riêng dòng) không chắc đâu là trong/ngoài: giữ nguyên.
        if (text == null || text.isEmpty() || text.contains("$$")) {
            return new FormulaLineRepair(text, 0);
        }
        String[] lines = text.split("\n", -1);
        int wrapped = 0;
        boolean open = false; // đang ở TRONG một cặp $...$ mở ở dòng trước (công thức trải qua nhiều dòng)
        for (int i = 0; i < lines.length; i++) {
            if (!open) {
                String w = wrapFormulaLine(lines[i]);
                if (w != null) {
                    lines[i] = w;
                    wrapped++;
                }
            }
            if (unescapedDollars(lines[i]) % 2 != 0) {
                open = !open;
            }
        }
        // Số dấu $ lẻ cả đoạn: không biết đâu là trong/ngoài, giữ nguyên để bộ kiểm tra báo lỗi như cũ.
        return wrapped == 0 || open ? new FormulaLineRepair(text, 0) : new FormulaLineRepair(String.join("\n", lines), wrapped);
    }

    /** Dòng đã bọc, hoặc null khi dòng không phải dòng công thức viết ngoài $. */
    private static String wrapFormulaLine(String line) {
        if (line.indexOf('$') >= 0) {
            return null;
        }
        String stripped = line.strip();
        if (stripped.isEmpty() || LIST_MARKER.matcher(stripped).find()) {
            return null;
        }
        int end = stripped.length();
        while (end > 0 && LINE_TAIL_PUNCTUATION.indexOf(stripped.charAt(end - 1)) >= 0) {
            end--;
        }
        String body = stripped.substring(0, end).strip();
        if (body.isEmpty() || !formulaLike(body)) {
            return null;
        }
        return "$" + body + "$" + stripped.substring(end);
    }

    /** True khi {@code body} chỉ gồm ký hiệu toán, số, biến và lệnh LaTeX cho phép (xem {@link #wrapFormulaLines}). */
    private static boolean formulaLike(String body) {
        int braces = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c > 127 && Character.isLetter(c)) {
                return false;
            }
            if (c == '{') {
                braces++;
            } else if (c == '}' && --braces < 0) {
                return false;
            }
        }
        if (braces != 0) {
            return false;
        }
        boolean hasCommand = false;
        Matcher m = LATEX_COMMAND.matcher(body);
        StringBuilder rest = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String name = m.group(1);
            if (!LatexValidationRules.isAllowedCommand(name) || NOT_ANCHOR.contains(name)) {
                return false;
            }
            hasCommand = true;
            rest.append(body, last, m.start()).append(' ');
            last = m.end();
        }
        rest.append(body, last, body.length());
        for (String token : rest.toString().split("[^A-Za-z]+")) {
            if (token.length() >= 2 && !token.matches("[A-Z]{2,4}")) {
                return false;
            }
        }
        if (hasCommand) {
            return true;
        }
        for (int i = 0; i < body.length(); i++) {
            if (FORMULA_SYMBOLS.indexOf(body.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Cảnh báo khi đã bọc dòng công thức; cùng tiền tố "đã tự bọc $ quanh" nên không bật cờ "cần xem kỹ". */
    public static String formulaLineWarning(String field, int wrapped) {
        return field + ": đã tự bọc $ quanh " + wrapped + " dòng công thức nằm ngoài $";
    }

    /**
     * True khi có một DÒNG công thức bị chia bởi "$" giữa biểu thức ({@code P $= (-5x^2) \cdot x + (-5x^2) \cdot$ (-3)}, 4/11,
     * đề 16 ô 37). Dòng công thức là dòng có ít nhất một cặp $...$ mà phần ngoài $ không có chữ nào (không chữ tiếng Việt,
     * không từ ASCII dài từ 2 ký tự trừ tên in hoa); dòng đó bị chia khi phần ngoài $ còn có số hoặc ký hiệu toán
     * ({@code = + ^ _ < > * /}) mà không phải dấu câu hay ngoặc đóng/mở. Số dấu $ lẻ không xét (bộ kiểm tra LaTeX lo). Đầu dòng liệt kê
     * ("1.", "a)", "- ") được bỏ qua. Không tự sửa vì không biết chỗ đóng đúng.
     */
    public static boolean hasBrokenFormulaLine(String text) {
        if (text == null || text.indexOf('$') < 0) {
            return false;
        }
        for (String line : text.split("\n", -1)) {
            if (line.indexOf('$') >= 0 && isBrokenFormulaLine(line)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBrokenFormulaLine(String line) {
        String rest = line;
        Matcher marker = LIST_MARKER.matcher(rest);
        if (marker.find()) {
            rest = rest.substring(marker.end());
        }
        int dollars = unescapedDollars(rest);
        if (dollars == 0) {
            return false;
        }
        if (dollars % 2 != 0) {
            // Dòng chỉ gồm công thức mà số $ lẻ: công thức trải qua nhiều dòng ("$= a\n= b$"), Word tách theo dòng nên mỗi
            // dòng hiện chữ "$". Dòng có chữ thì giữ cho bộ kiểm tra LaTeX hiện có lo.
            return wordless(rest.replace("$", " "));
        }
        List<int[]> outside = outsideMathSpans(rest);
        if (outside == null) {
            return false;
        }
        StringBuilder outsideText = new StringBuilder();
        for (int[] span : outside) {
            outsideText.append(rest, span[0], span[1]).append(' ');
        }
        String o = outsideText.toString();
        if (!wordless(o)) {
            return false;
        }
        for (String token : o.trim().split("\\s+")) {
            String bare = token.replaceAll("^[(\\[{.,;:!?]+", "").replaceAll("[)\\]}.,;:!?]+$", "");
            if (!bare.isEmpty() && bare.matches(".*[0-9=+^_<>*/].*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * True khi {@code s} không có chữ: không chữ tiếng Việt (chữ ngoài ASCII) và không từ ASCII dài từ 2 chữ cái, trừ tên in
     * hoa 2-4 chữ ("AB", "ABC") và tên lệnh LaTeX ("\cdot", "\frac"...).
     */
    private static boolean wordless(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127 && Character.isLetter(s.charAt(i))) {
                return false;
            }
        }
        for (String token : LATEX_COMMAND.matcher(s).replaceAll(" ").split("[^A-Za-z]+")) {
            if (token.length() >= 2 && !token.matches("[A-Z]{2,4}")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Các đoạn [start, end) nằm ngoài cặp $...$ ("\$" và mọi ký hiệu "\" khác bỏ qua nguyên khối; "$$" là công thức riêng
     * dòng); null khi số dấu $ lẻ (không biết đâu là trong/ngoài).
     */
    private static List<int[]> outsideMathSpans(String text) {
        List<int[]> spans = new ArrayList<>();
        int n = text.length();
        int spanStart = 0;
        boolean inMath = false;
        boolean display = false;
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '$') {
                boolean doubleDollar = i + 1 < n && text.charAt(i + 1) == '$';
                if (!inMath) {
                    if (i > spanStart) {
                        spans.add(new int[] {spanStart, i});
                    }
                    display = doubleDollar;
                    inMath = true;
                    i += display ? 2 : 1;
                } else {
                    inMath = false;
                    i += (display && doubleDollar) ? 2 : 1;
                    spanStart = Math.min(i, n);
                }
                continue;
            }
            i++;
        }
        if (inMath) {
            return null;
        }
        if (spanStart < n) {
            spans.add(new int[] {spanStart, n});
        }
        return spans;
    }

    /** Một "từ" của đoạn ngoài $: [start, end) trong đoạn. */
    private record Token(int start, int end, boolean anchor, boolean mathLike) {}

    private static String wrapSegment(String seg, boolean afterDollar, boolean beforeDollar, int[] count) {
        List<Token> tokens = tokenize(seg);
        boolean anyAnchor = false;
        for (Token t : tokens) {
            anyAnchor |= t.anchor();
        }
        if (!anyAnchor) {
            return seg;
        }
        StringBuilder out = new StringBuilder(seg.length() + 8);
        int copied = 0;
        int k = 0;
        while (k < tokens.size()) {
            if (!tokens.get(k).anchor()) {
                k++;
                continue;
            }
            int first = k;
            while (first > 0 && tokens.get(first - 1).mathLike() && !endsSentence(seg, tokens.get(first - 1))) {
                first--;
            }
            int last = k;
            while (last + 1 < tokens.size() && tokens.get(last + 1).mathLike() && !endsSentence(seg, tokens.get(last))) {
                last++;
            }
            int start = tokens.get(first).start();
            int end = tokens.get(last).end();
            while (end > start && TRAILING_PUNCTUATION.indexOf(seg.charAt(end - 1)) >= 0) {
                end--;
            }
            out.append(seg, copied, start);
            if (start == 0 && afterDollar) {
                out.append(' ');
            }
            out.append('$').append(seg, start, end).append('$');
            if (end == seg.length() && beforeDollar) {
                out.append(' ');
            }
            copied = end;
            count[0]++;
            k = last + 1;
        }
        out.append(seg, copied, seg.length());
        return out.toString();
    }

    /** True khi từ kết thúc bằng dấu hết câu: biểu thức không đi tiếp sang từ sau (hoặc lùi sang từ trước). */
    private static boolean endsSentence(String seg, Token t) {
        return t.end() > t.start() && SENTENCE_END.indexOf(seg.charAt(t.end() - 1)) >= 0;
    }

    /** Tách theo khoảng trắng ở ngoài ngoặc nhọn ("\frac{5x + y}{xy}" là một từ). */
    private static List<Token> tokenize(String seg) {
        List<Token> tokens = new ArrayList<>();
        int n = seg.length();
        int i = 0;
        while (i < n) {
            while (i < n && Character.isWhitespace(seg.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            int depth = 0;
            while (i < n && (depth > 0 || !Character.isWhitespace(seg.charAt(i)))) {
                char c = seg.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
                i++;
            }
            int end = Math.min(i, n);
            tokens.add(classify(seg.substring(start, end), start, end));
        }
        return tokens;
    }

    private static Token classify(String token, int start, int end) {
        boolean anchor = false;
        boolean ok = true;
        int depth = 0;
        int n = token.length();
        for (int i = 0; i < n; i++) {
            char c = token.charAt(i);
            if (c > 127 && Character.isLetter(c)) {
                ok = false;
            } else if (c == '\\') {
                if (i + 1 >= n) {
                    ok = false;
                } else if (isAsciiLetter(token.charAt(i + 1))) {
                    String name = asciiWord(token, i + 1);
                    if (LatexValidationRules.isAllowedCommand(name) && !NOT_ANCHOR.contains(name)) {
                        anchor = true;
                    } else {
                        ok = false;
                    }
                    i += name.length();
                } else if (token.charAt(i + 1) == '$') {
                    ok = false;
                    i++;
                } else {
                    i++;
                }
            } else if ((c == '^' || c == '_') && i + 1 < n && token.charAt(i + 1) == '{') {
                anchor = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth < 0) {
                ok = false;
            }
        }
        if (depth != 0) {
            ok = false;
        }
        if (ok) {
            String bare = token.replaceAll("^[(\\[{]+", "").replaceAll("[)\\]},.;:!?]+$", "");
            if (bare.matches("[A-Za-z]{2,}")) {
                ok = false;
            }
        }
        return new Token(start, end, anchor && ok, ok);
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
            TextRepair t = stripTextOutsideMath(s);
            if (t.removed() > 0) {
                warnings.add(textWarning(field, t.removed()));
                s = t.text();
            }
            DollarRepair d = closeTrailingDollar(s);
            if (d.closed()) {
                warnings.add(dollarWarning(field, s));
                s = d.text();
            }
            DegreeRepair g = fixDegreeSymbol(s);
            if (g.fixed() > 0) {
                warnings.add(degreeWarning(field, g.fixed()));
                s = g.text();
            }
            // 4/11: dòng công thức của lời giải viết ngoài $ ("mỗi bước một dòng") được bọc nguyên dòng, TRƯỚC bước bọc theo
            // đoạn liền mạch (bước đó đi qua cả ký tự xuống dòng và có thể ghép hai dòng, hoặc bọc dở một dòng).
            if ("explanation".equals(field)) {
                FormulaLineRepair f = wrapFormulaLines(McqJsonParser.normalizeLiteralNewlines(s));
                if (f.wrapped() > 0) {
                    warnings.add(formulaLineWarning(field, f.wrapped()));
                }
                s = f.text();
            }
            // Đáp án câu trả lời ngắn là số viết trần: LaTeX ở đó để bước kiểm định dạng báo đúng lỗi dạng đáp án.
            if (!"answer".equals(field)) {
                WrapRepair w = wrapLatexOutsideMath(s);
                if (w.wrapped() > 0) {
                    warnings.add(wrapWarning(field, w.wrapped()));
                    s = w.text();
                }
            }
        }
        return (newlines ? McqJsonParser.normalizeLiteralNewlines(s) : s).trim();
    }
}
