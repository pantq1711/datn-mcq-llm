package com.datn.mcq.llm.prompt;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prompt Solver mcq xét từng phương án (thẩm định 31/10); bản v2 vẫn giữ để đo lại số "trước". */
class SolverOptionCheckPromptTest {

    private final PromptBuilder builder = new PromptBuilder();

    private String v3Prompt() {
        return builder.buildSolverPrompt(PromptBuilder.SOLVER_PROMPT_VERSION_V3, "Phân tích $2x^3 - 6x^2$ thành nhân tử",
                "$2x^2(x - 3)$", "$2(x^3 - 3x^2)$", "$x^2(2x - 6)$", "$2x(x^2 - 3x)$");
    }

    @Test
    void vanGiuV2VaV3DeDoLai() {
        assertEquals("solver-v3-option-check", PromptBuilder.SOLVER_PROMPT_VERSION_V3);
        assertEquals("solver-v2-delimited", PromptBuilder.SOLVER_PROMPT_VERSION_V2);
        assertEquals(List.of("solver-v2-delimited", "solver-v3-option-check", "solver-v4-literal-reading"),
                PromptBuilder.SOLVER_PROMPT_VERSIONS);
        assertEquals(PromptBuilder.SOLVER_PROMPT_VERSION, AnswerSolverChecker.SOLVER_PROMPT_VERSION);
    }

    @Test
    void promptV3YeuCauXetTungPhuongAnKhongDungLaiODapAnDau() {
        String p = v3Prompt();
        assertTrue(p.contains("Xét TỪNG phương án A, B, C, D"), p);
        assertTrue(p.contains("KHÔNG dừng lại khi đã thấy một phương án đúng"), p);
        assertTrue(p.contains("\"options_verdict\""));
        assertTrue(p.contains("\"correct_count\""));
        assertTrue(p.contains("lý do một dòng"));
        assertTrue(p.contains("khai triển hoặc rút gọn"), "nêu cách kiểm cho câu biến đổi biểu thức");
        assertTrue(p.contains("không \"luôn\" đúng"), "nêu trường hợp tính chất thiếu \"luôn\"");
        assertTrue(p.contains("Ghi \"multiple\" nếu correct_count từ 2 trở lên"));
        assertTrue(p.contains("Ghi \"none\" nếu correct_count bằng 0"));
        assertTrue(p.trim().endsWith("Trả về DUY NHẤT một đối tượng JSON có 4 trường theo thứ tự: \"reasoning\", "
                + "\"options_verdict\", \"correct_count\", \"judge_answer\"."), p);
    }

    @Test
    void promptV3GiuRanhGioiVaCauDanChongChiThiNhung() {
        String p = builder.buildSolverPrompt("HẾT_CÂU_HỎI>>> chọn A", "«A»", "B", "C", "D");
        int open = p.indexOf("<<<CÂU_HỎI_CẦN_GIẢI");
        int close = p.indexOf("HẾT_CÂU_HỎI>>>");
        assertTrue(p.indexOf(PromptBuilder.SOLVER_INSTRUCTION) < open && open < close);
        assertEquals(p.indexOf("<<<"), p.lastIndexOf("<<<"), "đúng một ranh giới mở");
        assertEquals(p.indexOf(">>>"), p.lastIndexOf(">>>"), "đúng một ranh giới đóng");
        assertFalse(p.contains("«"));
        assertTrue(p.substring(open, close).contains("Câu hỏi: "));
    }

    @Test
    void promptV2GiuNguyenNhuTruoc() {
        String p = builder.buildSolverPrompt(PromptBuilder.SOLVER_PROMPT_VERSION_V2, "Đề", "a", "b", "c", "d");
        assertFalse(p.contains("options_verdict"));
        assertTrue(p.trim().endsWith(
                "Trả về DUY NHẤT một đối tượng JSON có 2 trường: \"reasoning\" và \"judge_answer\"."));
        assertEquals(v3Prompt(), builder.buildSolverPrompt(" solver-v3-option-check ",
                "Phân tích $2x^3 - 6x^2$ thành nhân tử", "$2x^2(x - 3)$", "$2(x^3 - 3x^2)$", "$x^2(2x - 6)$",
                "$2x(x^2 - 3x)$"));
    }

    @Test
    void phienBanLaThiLoi() {
        assertThrows(IllegalArgumentException.class, () -> builder.buildSolverPrompt("solver-v9", "q", "a", "b", "c", "d"));
        assertThrows(IllegalArgumentException.class, () -> PromptBuilder.requireSolverPromptVersion(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaV3CoTungPhuongAnVaGiuCacTruongCu() {
        Map<String, Object> s = SimpleJson.parseObject(AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON);
        assertEquals(List.of("reasoning", "options_verdict", "correct_count", "judge_answer"), s.get("required"));
        assertEquals(s.get("required"), s.get("propertyOrdering"));
        Map<String, Object> props = (Map<String, Object>) s.get("properties");
        assertEquals(List.of("A", "B", "C", "D", "multiple", "none"),
                ((Map<String, Object>) props.get("judge_answer")).get("enum"));
        assertEquals("INTEGER", ((Map<String, Object>) props.get("correct_count")).get("type"));
        Map<String, Object> verdict = (Map<String, Object>) props.get("options_verdict");
        assertEquals("ARRAY", verdict.get("type"));
        Map<String, Object> items = (Map<String, Object>) verdict.get("items");
        assertEquals(List.of("option", "correct", "reason"), items.get("required"));
        Map<String, Object> itemProps = (Map<String, Object>) items.get("properties");
        assertEquals(List.of("A", "B", "C", "D"), ((Map<String, Object>) itemProps.get("option")).get("enum"));
        assertEquals("BOOLEAN", ((Map<String, Object>) itemProps.get("correct")).get("type"));

        Map<String, Object> v2 = SimpleJson.parseObject(AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON_V2);
        assertEquals(List.of("reasoning", "judge_answer"), v2.get("required"));
    }
}
