package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.generation.dto.ParsedTfDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cắt rào ```json (nếu có) và giải mã câu Đúng/Sai (V4, GĐ3): question_text, statement_a..d, explanation,
 * label_a..d. Dùng chung {@link SimpleJson} nên khóa trùng là lỗi giải mã. Chuỗi "\n" theo nghĩa đen ngoài công thức
 * được đổi ở đề dẫn, 4 ý và lời giải; nhãn chỉ trim. Kiểm tra định dạng làm ở FormatChecker.
 */
public final class TfJsonParser {

    public static final List<String> KEYS = List.of("a", "b", "c", "d");

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
        for (String key : KEYS) {
            statements.add(asText(obj.get("statement_" + key)));
            Object label = obj.get("label_" + key);
            labels.add(label != null ? String.valueOf(label).trim() : null);
        }
        return new ParsedTfDto(
                true,
                asText(obj.get("question_text")),
                statements,
                asText(obj.get("explanation")),
                labels,
                null);
    }

    private static String asText(Object val) {
        if (val == null) {
            return null;
        }
        return McqJsonParser.normalizeLiteralNewlines(String.valueOf(val)).trim();
    }
}
