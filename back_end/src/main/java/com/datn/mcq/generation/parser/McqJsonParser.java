package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Option;
import com.datn.mcq.generation.dto.ParsedMcqDto;

import java.util.Map;
import java.util.Set;

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

        // raw_llm_output được lưu nguyên bản ở nơi gọi; chỉ các trường văn bản đã giải mã được chuẩn hóa.
        String questionText = asText(obj.get("question_text"));
        String optionA = asText(obj.get("option_a"));
        String optionB = asText(obj.get("option_b"));
        String optionC = asText(obj.get("option_c"));
        String optionD = asText(obj.get("option_d"));
        String explanation = asText(obj.get("explanation"));
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

    /** Trường văn bản: đổi "\n" theo nghĩa đen ngoài công thức thành xuống dòng, rồi trim. */
    private static String asText(Object val) {
        if (val == null) {
            return null;
        }
        return normalizeLiteralNewlines(String.valueOf(val)).trim();
    }

    /** Lệnh LaTeX bắt đầu bằng chữ n: nếu gặp ngoài công thức thì giữ nguyên để bộ kiểm tra báo "lệnh nằm ngoài $...$". */
    private static final Set<String> COMMANDS_STARTING_WITH_N =
            Set.of("neq", "ne", "notin", "not", "nu", "nabla", "neg", "ni");

    /**
     * Đổi chuỗi "\n" theo nghĩa đen (dấu \ rồi chữ n) thành ký tự xuống dòng khi nằm NGOÀI $...$ và $$...$$.
     * Model hay viết \\n trong JSON vì prompt dặn nhân đôi mọi dấu \, nên sau khi giải mã còn lại "\n".
     * Bên trong công thức giữ nguyên (\neq, \ne, \nu, \not, \nabla...). "\$" và "\\" được giữ nguyên khối,
     * "\$" không mở công thức. Ngoài công thức, nếu sau "\" là đúng tên một lệnh bắt đầu bằng n (ví dụ \neq)
     * thì giữ nguyên, để lỗi "lệnh nằm ngoài $...$" vẫn được báo thay vì bị che thành xuống dòng.
     */
    public static String normalizeLiteralNewlines(String text) {
        if (text == null || text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        boolean inMath = false;
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char next = text.charAt(i + 1);
                if (!inMath && next == 'n' && !startsKnownNCommand(text, i + 1)) {
                    sb.append('\n');
                } else {
                    sb.append(c).append(next);
                }
                i += 2;
                continue;
            }
            if (c == '$') {
                boolean doubleDollar = i + 1 < n && text.charAt(i + 1) == '$';
                sb.append(doubleDollar ? "$$" : "$");
                inMath = !inMath;
                i += doubleDollar ? 2 : 1;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static boolean startsKnownNCommand(String text, int letterStart) {
        int end = letterStart;
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++;
        }
        return COMMANDS_STARTING_WITH_N.contains(text.substring(letterStart, end));
    }
}

