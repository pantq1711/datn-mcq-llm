package com.datn.mcq.generation.parser;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Dấu câu cuối câu dẫn và ý Đúng/Sai ở bước giải mã (góp ý giáo viên 4/11, đo đề 16: quy tắc "?" viết trong prompt không
 * được model tuân thủ, 0/4 câu). Làm ở parser (chế độ production) để web và Word thấy cùng một chữ; prompt không còn quy tắc "?".
 * <ul>
 *   <li>Câu dẫn kết thúc bằng "là:" hoặc "là :" thành "là" (câu nối kết thúc bằng "là", không dấu câu).</li>
 *   <li>Câu dẫn mcq/sa có từ hỏi ("nào", "bao nhiêu", "gì", "mấy") ở câu cuối mà không kết thúc bằng dấu câu thì thêm "?".
 *       Câu kết thúc bằng "là" giữ nguyên. Câu dẫn tf KHÔNG BAO GIỜ thêm "?" (thường là câu mệnh lệnh "Xét tính đúng hay
 *       sai…").</li>
 *   <li>Câu dẫn tf thiếu dấu kết câu (kể cả khi có từ hỏi) thì thêm "."; kết thúc bằng ":" thì giữ nguyên.</li>
 *   <li>Mỗi ý Đúng/Sai thiếu dấu kết câu ({@code . ? ! …}) thì thêm "." (cùng quy tắc của bản xuất Word).</li>
 * </ul>
 * Chữ nằm trong $...$ không tính khi tìm từ hỏi. Chỉ thêm ký tự cuối, không đổi chữ ở giữa.
 */
public final class StemPunctuation {

    private StemPunctuation() {}

    /** Từ hỏi, so theo từ nguyên (không phải chuỗi con: "gì" không khớp "gìn"). */
    private static final Pattern QUESTION_WORD = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(?:nào|bao\\s+nhiêu|gì|mấy)(?![\\p{L}\\p{N}_])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ENDS_WITH_LA_COLON = Pattern.compile("(?<![\\p{L}\\p{N}_])là\\s*:\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ENDS_WITH_LA = Pattern.compile("(?<![\\p{L}\\p{N}_])là$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MATH_SPAN = Pattern.compile("\\$[^$]*\\$");

    /** Dấu mà câu dẫn đã có thì giữ nguyên (kể cả ":" mở danh sách, ";" và ","). */
    private static final String STEM_PUNCTUATION = ".?!…:;,";
    /** Dấu kết câu của ý Đúng/Sai (như bản xuất Word). */
    private static final String STATEMENT_END = ".?!…";
    /** Ký tự kết thúc câu trong phần chữ để tìm câu cuối. */
    private static final String SENTENCE_BREAKS = ".?!;:\n";

    /**
     * @param text câu dẫn đã trim; null hoặc rỗng giữ nguyên
     * @param tf   true với câu dẫn của câu Đúng/Sai (thiếu dấu kết câu thì thêm ".")
     */
    public static String normalizeStem(String text, boolean tf) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String s = ENDS_WITH_LA_COLON.matcher(text.strip()).replaceFirst("là");
        if (ENDS_WITH_LA.matcher(s).find() || s.endsWith("$$")) {
            return s;
        }
        char last = s.charAt(s.length() - 1);
        if (STEM_PUNCTUATION.indexOf(last) >= 0) {
            return s;
        }
        // tf KHÔNG bao giờ thêm "?" (câu dẫn tf thường là câu mệnh lệnh "Xét tính đúng hay sai…"): chỉ thêm ".".
        if (tf) {
            return s + ".";
        }
        return hasQuestionWordInLastSentence(s) ? s + "?" : s;
    }

    /** Ý Đúng/Sai: thêm "." nếu chưa có dấu kết câu ({@code . ? ! …}), kể cả khi kết thúc bằng công thức. */
    public static String endStatement(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String s = text.strip();
        return STATEMENT_END.indexOf(s.charAt(s.length() - 1)) >= 0 ? s : s + ".";
    }

    private static boolean hasQuestionWordInLastSentence(String s) {
        String outside = Normalizer.normalize(MATH_SPAN.matcher(s).replaceAll(" "), Normalizer.Form.NFC);
        int from = 0;
        for (int i = outside.length() - 1; i >= 0; i--) {
            if (SENTENCE_BREAKS.indexOf(outside.charAt(i)) >= 0) {
                from = i + 1;
                break;
            }
        }
        return QUESTION_WORD.matcher(outside.substring(from)).find();
    }

    /** Cảnh báo khi đã đổi dấu câu cuối. Cùng tiền tố với cảnh báo sửa escape nên không bật "cần xem kỹ". */
    public static String warning(String field, String before, String after) {
        String what;
        if (after.endsWith("?") && !before.endsWith("?")) {
            what = "thêm ?";
        } else if (after.endsWith(".") && !before.endsWith(".")) {
            what = "thêm .";
        } else {
            what = "bỏ : sau là";
        }
        return LlmOutputRepair.ESCAPE_WARNING_PREFIX + "dấu câu cuối " + field + " (" + what + ")";
    }

    /** Chuẩn hóa câu dẫn ở chế độ sửa của parser; ghi cảnh báo vào {@code warnings} nếu có đổi. */
    static String stem(String field, String text, boolean tf, boolean repair, List<String> warnings) {
        if (!repair) {
            return text;
        }
        String out = normalizeStem(text, tf);
        if (out != null && !out.equals(text)) {
            warnings.add(warning(field, text, out));
        }
        return out;
    }

    /** Chuẩn hóa một ý Đúng/Sai ở chế độ sửa của parser; ghi cảnh báo vào {@code warnings} nếu có đổi. */
    static String statement(String field, String text, boolean repair, List<String> warnings) {
        if (!repair) {
            return text;
        }
        String out = endStatement(text);
        if (out != null && !out.equals(text)) {
            warnings.add(warning(field, text, out));
        }
        return out;
    }
}
