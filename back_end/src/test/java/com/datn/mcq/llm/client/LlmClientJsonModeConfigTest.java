package com.datn.mcq.llm.client;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm tra phần generationConfig gửi tới Gemini có kèm responseSchema (chỉ dựng chuỗi, không gọi mạng).
 */
class LlmClientJsonModeConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void jsonModeConfigMustContainMimeTypeAndResponseSchema() {
        String fragment = LlmClient.jsonModeGenerationConfig(0.0, AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON);

        // Bọc thành đối tượng JSON để parse và kiểm tra cấu trúc thật thay vì so chuỗi
        Map<String, Object> root = SimpleJson.parseObject("{" + fragment + "}");
        Map<String, Object> config = (Map<String, Object>) root.get("generationConfig");

        assertEquals("application/json", config.get("responseMimeType"));
        assertEquals(0.0, ((Number) config.get("temperature")).doubleValue());

        Map<String, Object> schema = (Map<String, Object>) config.get("responseSchema");
        assertTrue(schema != null, "generationConfig phải có responseSchema");
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        Map<String, Object> judge = (Map<String, Object>) props.get("judge_answer");
        assertEquals(List.of("A", "B", "C", "D", "multiple", "none"), judge.get("enum"));
        assertEquals(List.of("reasoning", "options_verdict", "correct_count", "judge_answer"), schema.get("required"));
    }
}
