package com.datn.mcq.qc.checker;

import com.datn.mcq.enums.SaJudgeKind;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Solver câu Trả lời ngắn theo bảng mục 7 (bản sửa 1): so theo giá trị ở chế độ LENIENT (nhận dấu chấm),
 * "ambiguous" và chuỗi không phân tích được là INCONSISTENT (có ghi dòng), còn thiếu hoặc hỏng là ERROR (ném lỗi).
 * Không gọi mạng.
 */
class SaSolverCheckerTest {

    private static final String LLM_ANSWER = "$-\\frac{3}{4}$";

    /** LlmClient giả, ghi lại prompt và schema Solver nhận được. */
    static final class FakeClient extends LlmClient {
        String response;
        String lastPrompt;
        String lastSchema;

        FakeClient() {
            super("FAKE", "fake-model");
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String model, Double temperature, String schema) {
            lastPrompt = prompt;
            lastSchema = schema;
            return new LlmCallResult(response, 1, 1.0, 0.0, model);
        }
    }

    private final FakeClient client = new FakeClient();
    private final AnswerSolverChecker checker = new AnswerSolverChecker(client, new PromptBuilder(), "solver-x");

    private AnswerSolverChecker.SaSolverOutcome compare(String json) {
        return checker.parseAndCompareShortAnswer(json, "solver-x", LLM_ANSWER);
    }

    private static String judge(String answer) {
        return "{\"reasoning\": \"Giải được $x = -0,75$.\", \"judge_answer\": \"" + answer + "\"}";
    }

    @Test
    void cungGiaTriLaConsistent() {
        for (String same : new String[] {"-0,75", "-0.75", "−3/4", "$-\\\\frac{3}{4}$", "-\\\\frac{6}{8}"}) {
            AnswerSolverChecker.SaSolverOutcome out = compare(judge(same));
            assertTrue(out.passed(), same);
            assertEquals(SaJudgeKind.NUMBER, out.judgeKind());
            assertEquals("solver-x", out.judgeModel());
        }
        assertEquals("-0.75", compare(judge("-0.75")).judgeAnswer(), "lưu chuỗi gốc Solver trả");
    }

    @Test
    void khacGiaTriLaInconsistent() {
        AnswerSolverChecker.SaSolverOutcome out = compare(judge("0,75"));
        assertFalse(out.passed());
        assertEquals(SaJudgeKind.NUMBER, out.judgeKind());
        assertEquals("0,75", out.judgeAnswer());
        assertFalse(checker.parseAndCompareShortAnswer(judge("0,33"), "m", "1/3").passed());
    }

    @Test
    void ambiguousLaInconsistentKhongCoDapSo() {
        AnswerSolverChecker.SaSolverOutcome out = compare(judge("ambiguous"));
        assertFalse(out.passed());
        assertEquals(SaJudgeKind.AMBIGUOUS, out.judgeKind());
        assertNull(out.judgeAnswer());
        // Quyết định 2 sau GĐ2: không phân biệt hoa thường, sau khi trim.
        for (String variant : new String[] {"Ambiguous", " AMBIGUOUS ", "aMbIgUoUs"}) {
            AnswerSolverChecker.SaSolverOutcome v = compare(judge(variant));
            assertEquals(SaJudgeKind.AMBIGUOUS, v.judgeKind(), variant);
            assertNull(v.judgeAnswer());
        }
    }

    @Test
    void khongPhanTichDuocLaInconsistentKemLyDo() {
        AnswerSolverChecker.SaSolverOutcome out = compare(judge("2 hoặc 3"));
        assertFalse(out.passed());
        assertEquals(SaJudgeKind.UNPARSEABLE, out.judgeKind());
        assertEquals("2 hoặc 3", out.judgeAnswer());
        assertTrue(out.reasoning().startsWith("không so tự động được"), out.reasoning());
        assertEquals(SaJudgeKind.UNPARSEABLE, compare(judge("1 + \\\\sqrt{2}")).judgeKind());
    }

    @Test
    void thieuHongHoacKhoaTrungLaError() {
        assertThrows(IllegalArgumentException.class, () -> compare("{\"reasoning\": \"r\"}"));
        assertThrows(IllegalArgumentException.class, () -> compare(judge("  ")));
        assertThrows(IllegalArgumentException.class, () -> compare("không phải JSON"));
        assertThrows(IllegalArgumentException.class,
                () -> compare("{\"reasoning\": \"r\", \"judge_answer\": \"1\", \"judge_answer\": \"2\"}"));
    }

    @Test
    void dapSoLlmKhongPhanTichDuocLaError() {
        assertThrows(IllegalArgumentException.class,
                () -> checker.parseAndCompareShortAnswer(judge("1"), "m", "x = 1"));
        assertThrows(IllegalArgumentException.class,
                () -> checker.parseAndCompareShortAnswer(judge("1"), "m", null));
    }

    @Test
    void checkShortAnswerDungPromptVaSchemaSa() {
        client.response = judge("-0,75");
        AnswerSolverChecker.SaSolverOutcome out = checker.checkShortAnswer("Nghiệm của $4x + 3 = 0$?", LLM_ANSWER);
        assertTrue(out.passed());
        assertEquals(SaPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON, client.lastSchema);
        assertTrue(client.lastPrompt.contains("Nghiệm của $4x + 3 = 0$?"));
        assertFalse(client.lastPrompt.contains(LLM_ANSWER), "Solver không được thấy đáp số người soạn");
    }

    @Test
    void checkShortAnswerMangSoLieuLuotGoiLlmCuaChinNoCoParseThuanThiKhong() {
        client.response = judge("-0,75");
        AnswerSolverChecker.SaSolverOutcome out = checker.checkShortAnswer("Nghiệm của $4x + 3 = 0$?", LLM_ANSWER);
        assertEquals(1, out.usage().tokenCount());
        assertEquals(1.0, out.usage().latencyMs());
        assertNull(compare(judge("-0,75")).usage(), "phân tích thuần không có lượt gọi LLM nào");
    }
}
