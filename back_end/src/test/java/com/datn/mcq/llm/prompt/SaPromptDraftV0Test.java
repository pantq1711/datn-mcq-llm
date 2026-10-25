package com.datn.mcq.llm.prompt;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Strategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prompt nháp SA v0 (bản sửa 1 mục 10): schema Gemini có propertyOrdering (lời giải trước đáp số),
 * đáp số là chuỗi; prompt nêu quy tắc đáp số; prompt MCQ không đổi.
 */
class SaPromptDraftV0Test {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(String json) {
        return SimpleJson.parseObject(json);
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaSinhCauCoThuTuLoiGiaiTruocDapSo() {
        Map<String, Object> s = schema(SaPromptDraftV0.GENERATION_RESPONSE_SCHEMA_JSON);
        assertEquals("OBJECT", s.get("type"));
        assertEquals(List.of("question_text", "explanation", "answer"), s.get("propertyOrdering"));
        assertEquals(List.of("question_text", "explanation", "answer"), s.get("required"));
        Map<String, Object> props = (Map<String, Object>) s.get("properties");
        assertEquals("STRING", ((Map<String, Object>) props.get("answer")).get("type"),
                "đáp số là chuỗi để giữ dấu phẩy, phân số và căn");
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaSolverCoThuTuLapLuanTruocDapSo() {
        Map<String, Object> s = schema(SaPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON);
        assertEquals(List.of("reasoning", "judge_answer"), s.get("propertyOrdering"));
        assertEquals(List.of("reasoning", "judge_answer"), s.get("required"));
        Map<String, Object> props = (Map<String, Object>) s.get("properties");
        assertEquals("STRING", ((Map<String, Object>) props.get("judge_answer")).get("type"));
    }

    @Test
    void promptSinhCauNeuQuyTacDapSo() {
        String p = SaPromptDraftV0.buildGenerationPrompt(
                "Toán", 8, "Phương trình bậc nhất", "Giải được phương trình bậc nhất.", "Giải $2x = 6$.",
                Difficulty.MEDIUM);
        for (String part : List.of("TRẢ LỜI NGẮN", "Toán", "Khối lớp: 8", "Phương trình bậc nhất",
                "Giải được phương trình bậc nhất.", "Giải $2x = 6$.", "Hiểu", "phân số", "\\sqrt{n}",
                "làm tròn", "chữ số 0 thừa", "\"question_text\", \"explanation\", \"answer\"",
                PromptBuilder.ESCAPE_HINT.strip())) {
            assertTrue(p.contains(part), "Prompt thiếu: " + part);
        }
        assertFalse(p.contains("correct_option"));
        assertFalse(p.contains("BẢN NHÁP"), "nhãn bản nháp chỉ nằm ở Javadoc, không gửi cho model");
    }

    @Test
    void cauNguonChiLaDinhHuongBatBuocTaoBaiMoi() {
        String p = SaPromptDraftV0.buildGenerationPrompt("Toán", 8, null, null, "Giải $2x - 6 = 0$.", Difficulty.EASY);
        for (String part : List.of("chỉ để định hướng dạng bài và kiến thức", "BẮT BUỘC tạo một bài toán MỚI",
                "số liệu, biểu thức hoặc ngữ cảnh khác câu hỏi nguồn",
                "không chép lại phương trình, biểu thức hay số liệu của câu hỏi nguồn")) {
            assertTrue(p.contains(part), "Prompt thiếu: " + part);
        }
        assertTrue(p.indexOf("Giải $2x - 6 = 0$.") < p.indexOf("BẮT BUỘC tạo một bài toán MỚI"));
        String noSource = SaPromptDraftV0.buildGenerationPrompt("Toán", 8, null, null, null, Difficulty.EASY);
        assertFalse(noSource.contains("câu hỏi nguồn"), "không có câu nguồn thì không nhắc câu nguồn");
    }

    @Test
    void promptSolverKhongKemDapSoNguoiSoan() {
        String p = SaPromptDraftV0.buildSolverPrompt("Tính $1 + 1$.");
        for (String part : List.of("Tính $1 + 1$.", "\"reasoning\"", "\"judge_answer\"", "ambiguous", "làm tròn")) {
            assertTrue(p.contains(part), "Prompt Solver thiếu: " + part);
        }
    }

    @Test
    void phienBanPromptVaPromptMcqKhongDoi() {
        assertEquals("sa-v1-draft", SaPromptDraftV0.PROMPT_VERSION);
        String mcq = new PromptBuilder().buildGenerationPrompt("Toán", 8, null, null, null, Strategy.ZERO_SHOT,
                Difficulty.EASY);
        assertTrue(mcq.startsWith("Bạn là giáo viên THCS biên soạn câu hỏi trắc nghiệm 4 lựa chọn."));
        assertTrue(mcq.contains("đủ 7 trường"));
    }
}
