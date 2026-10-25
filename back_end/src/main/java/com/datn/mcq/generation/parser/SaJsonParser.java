package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.generation.dto.ParsedSaDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cắt rào ```json (nếu có) và giải mã 3 trường của câu Trả lời ngắn (V4): question_text, explanation, answer.
 * Dùng chung {@link SimpleJson} với MCQ nên khóa trùng là lỗi giải mã. Chuỗi "\n" theo nghĩa đen ngoài công thức chỉ
 * được đổi ở question_text và explanation; answer chỉ trim (đáp số không có lý do chứa xuống dòng).
 * Kiểm tra định dạng đáp số làm ở FormatChecker, không ở đây; raw_llm_output được lưu nguyên ở nơi gọi.
 */
public final class SaJsonParser {

    private SaJsonParser() {}

    /** Giải mã chặt (không sửa gì). */
    public static ParsedSaDto parse(String rawLlmOutput) {
        return parse(rawLlmOutput, false);
    }

    /** @param repair true (run production): sửa escape LaTeX và tự đóng "$" ({@link LlmOutputRepair}) */
    public static ParsedSaDto parse(String rawLlmOutput, boolean repair) {
        if (rawLlmOutput == null || rawLlmOutput.isBlank()) {
            return new ParsedSaDto(false, null, null, null, false, "Phản hồi LLM rỗng");
        }
        List<String> warnings = new ArrayList<>();
        Map<String, Object> obj;
        try {
            obj = SimpleJson.parseObject(LlmOutputRepair.repairJson(SimpleJson.stripMarkdownFence(rawLlmOutput),
                    repair, warnings));
        } catch (IllegalArgumentException ex) {
            return new ParsedSaDto(false, null, null, null, false, ex.getMessage());
        }
        Object answer = obj.get("answer");
        String questionText = LlmOutputRepair.textField("question_text", obj.get("question_text"), repair, true,
                warnings);
        String explanation = LlmOutputRepair.textField("explanation", obj.get("explanation"), repair, true, warnings);
        // answer: chỉ trim, không đổi \n; chế độ sửa vẫn tự đóng "$" (ví dụ "$3\sqrt{2}").
        String answerText = answer instanceof String
                ? LlmOutputRepair.textField("answer", answer, repair, false, warnings)
                : answer != null ? String.valueOf(answer).trim() : null;
        return new ParsedSaDto(true, questionText, explanation, answerText, answer instanceof String, null, warnings);
    }
}
