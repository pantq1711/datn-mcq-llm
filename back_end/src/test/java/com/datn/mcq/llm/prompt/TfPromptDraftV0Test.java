package com.datn.mcq.llm.prompt;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prompt nháp TF v0: schema Gemini có propertyOrdering (đề dẫn, 4 ý, lời giải, 4 nhãn); schema Solver theo
 * reasoning_x rồi judge_x từ a đến d; prompt nêu mức mục tiêu từng ý; Solver không thấy nhãn người soạn.
 */
class TfPromptDraftV0Test {

    private static final List<String> GENERATION_ORDER = List.of("question_text", "statement_a", "statement_b",
            "statement_c", "statement_d", "explanation", "label_a", "label_b", "label_c", "label_d");
    private static final List<String> SOLVER_ORDER = List.of("reasoning_a", "judge_a", "reasoning_b", "judge_b",
            "reasoning_c", "judge_c", "reasoning_d", "judge_d");

    @Test
    @SuppressWarnings("unchecked")
    void schemaSinhCauDungThuTu() {
        Map<String, Object> s = SimpleJson.parseObject(TfPromptDraftV0.GENERATION_RESPONSE_SCHEMA_JSON);
        assertEquals(GENERATION_ORDER, s.get("propertyOrdering"));
        assertEquals(GENERATION_ORDER, s.get("required"));
        Map<String, Object> props = (Map<String, Object>) s.get("properties");
        for (String label : List.of("label_a", "label_b", "label_c", "label_d")) {
            assertEquals(List.of("T", "F"), ((Map<String, Object>) props.get(label)).get("enum"), label);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaSolverDungThuTu() {
        Map<String, Object> s = SimpleJson.parseObject(TfPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON);
        assertEquals(SOLVER_ORDER, s.get("propertyOrdering"));
        assertEquals(SOLVER_ORDER, s.get("required"));
        Map<String, Object> props = (Map<String, Object>) s.get("properties");
        assertEquals(List.of("T", "F", "undecidable"), ((Map<String, Object>) props.get("judge_c")).get("enum"));
    }

    @Test
    void promptSinhCauNeuMucTungY() {
        String p = TfPromptDraftV0.buildGenerationPrompt("Toán", 8, "Phân thức", "Rút gọn được phân thức.",
                "Rút gọn $\\frac{2x}{4x}$.", List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD));
        for (String part : List.of("ĐÚNG/SAI", "đúng 4 ý", "ý a: Biết; ý b: Biết; ý c: Hiểu; ý d: Vận dụng",
                "Phân thức", "Rút gọn được phân thức.", "Không bắt buộc số ý Đúng hay số ý Sai",
                "\"label_a\"", "\"T\" = Đúng", PromptBuilder.ESCAPE_HINT.strip())) {
            assertTrue(p.contains(part), "Prompt thiếu: " + part);
        }
        assertFalse(p.contains("BẢN NHÁP"));
        assertFalse(p.contains("correct_option"));
    }

    @Test
    void cauNguonChiLaDinhHuongBatBuocTaoBaiMoi() {
        List<Difficulty> levels = List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD);
        String p = TfPromptDraftV0.buildGenerationPrompt("Toán", 8, null, null, "Giải $2x - 6 = 0$.", levels);
        for (String part : List.of("chỉ để định hướng dạng bài và kiến thức", "BẮT BUỘC tạo một bài toán MỚI",
                "số liệu, biểu thức hoặc ngữ cảnh khác câu hỏi nguồn",
                "không chép lại phương trình, biểu thức hay số liệu của câu hỏi nguồn vào đề dẫn hay các ý")) {
            assertTrue(p.contains(part), "Prompt thiếu: " + part);
        }
        assertTrue(p.indexOf("Giải $2x - 6 = 0$.") < p.indexOf("BẮT BUỘC tạo một bài toán MỚI"));
        String noSource = TfPromptDraftV0.buildGenerationPrompt("Toán", 8, null, null, null, levels);
        assertFalse(noSource.contains("câu hỏi nguồn"), "không có câu nguồn thì không nhắc câu nguồn");
    }

    @Test
    void promptSolverKhongKemNhan() {
        String p = TfPromptDraftV0.buildSolverPrompt("Cho $P = x$.", List.of("Ý A1", "Ý B1", "Ý C1", "Ý D1"));
        for (String part : List.of("Cho $P = x$.", "a) Ý A1", "b) Ý B1", "c) Ý C1", "d) Ý D1", "undecidable",
                "\"reasoning_a\"", "\"judge_d\"")) {
            assertTrue(p.contains(part), "Prompt Solver thiếu: " + part);
        }
        assertEquals("tf-v2-draft", TfPromptDraftV0.PROMPT_VERSION);
    }

    /** Ngày 23/10 (mục 6): ý bám đối tượng trong đề dẫn; mọi "$" mở phải đóng trước dấu câu. */
    @Test
    void quyTacYBamDeDanVaDongDoLa() {
        String p = TfPromptDraftV0.buildGenerationPrompt("Toán", 8, null, null, null,
                List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD));
        for (String part : List.of(
                "Mỗi ý a–d phải nói về chính đối tượng trong đề dẫn (biểu thức, hình, số liệu đã cho), không nêu kiến"
                        + " thức chung tách rời đề dẫn",
                "Mọi công thức mở bằng $ phải đóng bằng $ trước dấu câu")) {
            assertTrue(p.contains(part), "Prompt thiếu: " + part);
        }
    }
}
