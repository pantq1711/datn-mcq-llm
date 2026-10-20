package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Option;
import com.datn.mcq.generation.dto.ParsedMcqDto;

import java.util.Map;

/**
 * Cắt rào ```json (nếu có) và giải mã 7 trường chuẩn của câu hỏi trắc nghiệm từ đầu ra LLM.
 */
public final class McqJsonParser {

    private McqJsonParser() {}

    public static String stripMarkdownFence(String raw) {
        return SimpleJson.stripMarkdownFence(raw);
    }

    public static ParsedMcqDto parse(String rawLlmOutput) {
        if (rawLlmOutput == null || rawLlmOutput.isBlank()) {
            return new ParsedMcqDto(false, null, null, null, null, null, null, null, "Phản hồi LLM rỗng");
        }
        String cleaned = stripMarkdownFence(rawLlmOutput);
        Map<String, Object> obj;
        try {
            obj = SimpleJson.parseObject(cleaned);
        } catch (IllegalArgumentException ex) {
            return new ParsedMcqDto(false, null, null, null, null, null, null, null, ex.getMessage());
        }

        String questionText = asString(obj.get("question_text"));
        String optionA = asString(obj.get("option_a"));
        String optionB = asString(obj.get("option_b"));
        String optionC = asString(obj.get("option_c"));
        String optionD = asString(obj.get("option_d"));
        String explanation = asString(obj.get("explanation"));
        String correctOptRaw = asString(obj.get("correct_option"));

        Option correctOption = null;
        if (correctOptRaw != null && !correctOptRaw.isBlank()) {
            try {
                correctOption = DbEnum.fromDbValue(Option.class, correctOptRaw.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                correctOption = null;
            }
        }

        return new ParsedMcqDto(
                true,
                questionText,
                optionA,
                optionB,
                optionC,
                optionD,
                correctOption,
                explanation,
                null
        );
    }

    private static String asString(Object val) {
        if (val == null) {
            return null;
        }
        return String.valueOf(val).trim();
    }
}

