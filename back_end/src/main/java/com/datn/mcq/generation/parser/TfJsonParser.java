package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.generation.dto.ParsedTfDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cắt rào ```json (nếu có) và giải mã câu Đúng/Sai (V4, GĐ3): question_text, statement_a..d, explanation,
 * label_a..d. Dùng chung {@link SimpleJson} nên khóa trùng là lỗi giải mã. Chuỗi "\n" theo nghĩa đen ngoài công thức
 * được đổi ở đề dẫn, 4 ý và lời giải; nhãn chỉ trim. Kiểm tra định dạng làm ở FormatChecker.
 *
 * <p>GĐ4: nếu một ý mở đầu bằng tiền tố nhãn "a)", "a.", "(a)" hoặc "a:" (không phân biệt hoa thường, cho phép
 * khoảng trắng) và chữ cái khớp đúng vị trí của ý, tiền tố được bỏ (một lần) và ghi cảnh báo vào
 * {@link ParsedTfDto#parseWarnings()}. Chữ cái không khớp vị trí thì giữ nguyên. Riêng dạng "a." chỉ bỏ khi ký tự
 * ngay sau dấu chấm không phải chữ cái ("a.b = 0" giữ nguyên, "a. Điều kiện" bỏ).
 */
public final class TfJsonParser {

    public static final List<String> KEYS = List.of("a", "b", "c", "d");

    /** Tiền tố nhãn ở đầu ý: "(a)" hoặc "a)", "a.", "a:"; nhóm 1 hoặc 2 là chữ cái. */
    private static final Pattern LABEL_PREFIX =
            Pattern.compile("^(?:\\(\\s*([A-Da-d])\\s*\\)|([A-Da-d])\\s*[).:])");

    private TfJsonParser() {}

    public static ParsedTfDto parse(String rawLlmOutput) {
        if (rawLlmOutput == null || rawLlmOutput.isBlank()) {
            return new ParsedTfDto(false, null, null, null, null, "Phản hồi LLM rỗng");
        }
        Map<String, Object> obj;
        try {
            obj = SimpleJson.parseObject(SimpleJson.stripMarkdownFence(rawLlmOutput));
        } catch (IllegalArgumentException ex) {
            return new ParsedTfDto(false, null, null, null, null, ex.getMessage());
        }
        List<String> statements = new ArrayList<>(4);
        List<String> labels = new ArrayList<>(4);
        List<String> warnings = new ArrayList<>();
        for (String key : KEYS) {
            statements.add(stripLabelPrefix(key, asText(obj.get("statement_" + key)), warnings));
            Object label = obj.get("label_" + key);
            labels.add(label != null ? String.valueOf(label).trim() : null);
        }
        return new ParsedTfDto(
                true,
                asText(obj.get("question_text")),
                statements,
                asText(obj.get("explanation")),
                labels,
                null,
                warnings);
    }

    /** Bỏ tiền tố nhãn khớp vị trí {@code key} ở đầu ý (một lần), ghi cảnh báo; không khớp thì trả nguyên văn. */
    static String stripLabelPrefix(String key, String statement, List<String> warnings) {
        if (statement == null) {
            return null;
        }
        Matcher m = LABEL_PREFIX.matcher(statement);
        if (!m.find()) {
            return statement;
        }
        String letter = m.group(1) != null ? m.group(1) : m.group(2);
        if (!letter.equalsIgnoreCase(key)) {
            return statement;
        }
        // Dạng "a.": chỉ bỏ khi ký tự ngay sau dấu chấm không phải chữ cái, để "a.b = 0" giữ nguyên.
        if (m.group().endsWith(".") && m.end() < statement.length()
                && Character.isLetter(statement.charAt(m.end()))) {
            return statement;
        }
        warnings.add(prefixWarning(key, m.group()));
        return statement.substring(m.end()).trim();
    }

    /** Cảnh báo khi đã bỏ tiền tố, ví dụ {@code statement_a: đã bỏ tiền tố "a)" ở đầu ý}. */
    public static String prefixWarning(String key, String prefix) {
        return "statement_" + key + ": đã bỏ tiền tố \"" + prefix.trim() + "\" ở đầu ý";
    }

    private static String asText(Object val) {
        if (val == null) {
            return null;
        }
        return McqJsonParser.normalizeLiteralNewlines(String.valueOf(val)).trim();
    }
}
