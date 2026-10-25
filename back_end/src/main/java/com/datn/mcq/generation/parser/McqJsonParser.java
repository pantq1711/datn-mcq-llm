package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Option;
import com.datn.mcq.generation.dto.ParsedMcqDto;

import java.util.ArrayList;
import java.util.List;
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

    /** Giải mã chặt (không sửa gì): dùng cho run experiment C1/C2/C3 và dữ liệu demo. */
    public static ParsedMcqDto parse(String rawLlmOutput) {
        return parse(rawLlmOutput, false);
    }

    /**
     * @param repair true (run production, ngày 23/10): sửa escape LaTeX chưa nhân đôi và tự đóng "$" ở cuối trường
     *               ({@link LlmOutputRepair}), mỗi lần sửa ghi một cảnh báo vào parseWarnings
     */
    public static ParsedMcqDto parse(String rawLlmOutput, boolean repair) {
        if (rawLlmOutput == null || rawLlmOutput.isBlank()) {
            return new ParsedMcqDto(false, null, null, null, null, null, null, null, "Phản hồi LLM rỗng");
        }
        List<String> warnings = new ArrayList<>();
        String cleaned = LlmOutputRepair.repairJson(stripMarkdownFence(rawLlmOutput), repair, warnings);
        Map<String, Object> obj;
        try {
            obj = SimpleJson.parseObject(cleaned);
        } catch (IllegalArgumentException ex) {
            return new ParsedMcqDto(false, null, null, null, null, null, null, null, ex.getMessage());
        }

        // raw_llm_output được lưu nguyên bản ở nơi gọi; chỉ các trường văn bản đã giải mã được chuẩn hóa.
        String questionText = LlmOutputRepair.textField("question_text", obj.get("question_text"), repair, true, warnings);
        String optionA = LlmOutputRepair.textField("option_a", obj.get("option_a"), repair, true, warnings);
        String optionB = LlmOutputRepair.textField("option_b", obj.get("option_b"), repair, true, warnings);
        String optionC = LlmOutputRepair.textField("option_c", obj.get("option_c"), repair, true, warnings);
        String optionD = LlmOutputRepair.textField("option_d", obj.get("option_d"), repair, true, warnings);
        String explanation = LlmOutputRepair.textField("explanation", obj.get("explanation"), repair, true, warnings);
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
                null,
                warnings
        );
    }

    private static String asString(Object val) {
        if (val == null) {
            return null;
        }
        return String.valueOf(val).trim();
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

    /**
     * So cả cụm chữ sau "\" với tên lệnh: chữ cái Unicode (Character.isLetter, gồm "ô", "ế"...) và cả dấu kết hợp,
     * để "nếu" viết dạng tổ hợp NFD (n, e, dấu mũ, dấu sắc, u) không bị đọc thành lệnh \ne.
     */
    private static boolean startsKnownNCommand(String text, int letterStart) {
        int end = letterStart;
        while (end < text.length() && isWordChar(text.charAt(end))) {
            end++;
        }
        return COMMANDS_STARTING_WITH_N.contains(text.substring(letterStart, end));
    }

    private static boolean isWordChar(char c) {
        if (Character.isLetter(c)) {
            return true;
        }
        int type = Character.getType(c);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }
}

