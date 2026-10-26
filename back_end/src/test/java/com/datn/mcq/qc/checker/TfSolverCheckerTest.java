package com.datn.mcq.qc.checker;

import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.llm.prompt.TfPromptDraftV0;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Solver câu Đúng/Sai (GĐ3): CONSISTENT chỉ khi đủ 4 ý khớp; có ý lệch hoặc undecidable là INCONSISTENT;
 * thiếu hoặc sai judge_x, JSON hỏng, khóa trùng là ERROR (ném lỗi). Không gọi mạng.
 */
class TfSolverCheckerTest {

    private static final List<String> LLM_LABELS = List.of("T", "T", "F", "F");

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

    static String judge(String a, String b, String c, String d) {
        return "{\"reasoning_a\": \"ra\", \"judge_a\": \"" + a + "\", \"reasoning_b\": \"rb\", \"judge_b\": \"" + b
                + "\", \"reasoning_c\": \"rc\", \"judge_c\": \"" + c + "\", \"reasoning_d\": \"rd\", \"judge_d\": \""
                + d + "\"}";
    }

    private AnswerSolverChecker.TfSolverOutcome compare(String json) {
        return checker.parseAndCompareTrueFalse(json, "solver-x", LLM_LABELS);
    }

    @Test
    void duBonYKhopLaConsistent() {
        AnswerSolverChecker.TfSolverOutcome out = compare(judge("T", " t ", "F", "f"));
        assertTrue(out.passed());
        assertEquals(List.of(TruthChoice.TRUE, TruthChoice.TRUE, TruthChoice.FALSE, TruthChoice.FALSE),
                out.judgeLabels());
        assertEquals("solver-x", out.judgeModel());
        assertTrue(out.reasoning().contains("a) ra") && out.reasoning().contains("d) rd"), out.reasoning());
    }

    @Test
    void lechMotYLaInconsistent() {
        AnswerSolverChecker.TfSolverOutcome out = compare(judge("T", "T", "T", "F"));
        assertFalse(out.passed());
        assertEquals(TruthChoice.TRUE, out.judgeLabels().get(2));
    }

    @Test
    void undecidableLaInconsistentKeCaKhiKhongLech() {
        AnswerSolverChecker.TfSolverOutcome out = compare(judge("T", "T", "F", "UNDECIDABLE"));
        assertFalse(out.passed());
        assertEquals(TruthChoice.UNDECIDABLE, out.judgeLabels().get(3));
    }

    @Test
    void thieuHongHoacKhoaTrungLaError() {
        assertThrows(IllegalArgumentException.class, () -> compare(judge("T", "T", "F", "  ")));
        assertThrows(IllegalArgumentException.class, () -> compare(judge("T", "Đúng", "F", "F")));
        assertThrows(IllegalArgumentException.class,
                () -> compare("{\"judge_a\": \"T\", \"judge_b\": \"T\", \"judge_c\": \"F\"}"));
        assertThrows(IllegalArgumentException.class, () -> compare("không phải JSON"));
        assertThrows(IllegalArgumentException.class,
                () -> compare(judge("T", "T", "F", "F").replace("}", ", \"judge_d\": \"T\"}")));
    }

    @Test
    void nhanLlmKhongHopLeLaError() {
        assertThrows(IllegalArgumentException.class,
                () -> checker.parseAndCompareTrueFalse(judge("T", "T", "F", "F"), "m", List.of("T", "T", "F")));
        assertThrows(IllegalArgumentException.class,
                () -> checker.parseAndCompareTrueFalse(judge("T", "T", "F", "F"), "m", List.of("T", "T", "F", "X")));
    }

    @Test
    void checkTrueFalseDungPromptVaSchemaTf() {
        client.response = judge("T", "T", "F", "F");
        AnswerSolverChecker.TfSolverOutcome out = checker.checkTrueFalse("Cho $P$.",
                List.of("Ý một", "Ý hai", "Ý ba", "Ý bốn"), LLM_LABELS);
        assertTrue(out.passed());
        assertEquals(TfPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON, client.lastSchema);
        assertTrue(client.lastPrompt.contains("a) Ý một"));
        assertFalse(client.lastPrompt.contains("label_"), "Solver không thấy nhãn người soạn");
    }

    @Test
    void checkTrueFalseMangSoLieuLuotGoiLlmCuaChinNoCoParseThuanThiKhong() {
        client.response = judge("T", "T", "F", "F");
        AnswerSolverChecker.TfSolverOutcome out = checker.checkTrueFalse("Cho $P$.",
                List.of("Ý một", "Ý hai", "Ý ba", "Ý bốn"), LLM_LABELS);
        assertEquals(1, out.usage().tokenCount());
        assertEquals(1.0, out.usage().latencyMs());
        assertNull(compare(judge("T", "T", "F", "F")).usage(), "phân tích thuần không có lượt gọi LLM nào");
    }
}
