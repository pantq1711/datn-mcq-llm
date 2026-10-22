package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.generation.dto.ParsedSaDto;

import java.util.Map;

/**
 * Cắt rào ```json (nếu có) và giải mã 3 trường của câu Trả lời ngắn (V4): question_text, explanation, answer.
 * Dùng chung {@link SimpleJson} với MCQ nên khóa trùng là lỗi giải mã. Chuỗi "\n" theo nghĩa đen ngoài công thức chỉ
 * được đổi ở question_text và explanation; answer chỉ trim (đáp số không có lý do chứa xuống dòng).
 * Kiểm tra định dạng đáp số làm ở FormatChecker, không ở đây; raw_llm_output được lưu nguyên ở nơi gọi.
 */
public final class SaJsonParser {

    private SaJsonParser() {}

    public static ParsedSaDto parse(String rawLlmOutput) {
        if (rawLlmOutput == null || rawLlmOutput.isBlank()) {
            return new ParsedSaDto(false, null, null, null, false, "Phản hồi LLM rỗng");
        }
        Map<String, Object> obj;
        try {
            obj = SimpleJson.parseObject(SimpleJson.stripMarkdownFence(rawLlmOutput));
        } catch (IllegalArgumentException ex) {
            return new ParsedSaDto(false, null, null, null, false, ex.getMessage());
        }
        Object answer = obj.get("answer");
        return new ParsedSaDto(
                true,
                asText(obj.get("question_text")),
                asText(obj.get("explanation")),
                answer != null ? String.valueOf(answer).trim() : null,
                answer instanceof String,
                null);
    }

    private static String asText(Object val) {
        if (val == null) {
            return null;
        }
        return McqJsonParser.normalizeLiteralNewlines(String.valueOf(val)).trim();
    }
}
