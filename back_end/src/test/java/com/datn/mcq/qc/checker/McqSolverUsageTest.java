package com.datn.mcq.qc.checker;

import com.datn.mcq.enums.Option;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Đo chi phí (W2-3): kết quả Solver mcq mang token và độ trễ của đúng lượt gọi LLM đã cho ra nó. */
class McqSolverUsageTest {

    private static final class FakeClient extends LlmClient {
        FakeClient() {
            super("FAKE", "fake-model");
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String model, Double temperature, String schema) {
            return new LlmCallResult("{\"reasoning\": \"r\", \"judge_answer\": \"B\"}", 321, 456.5, 0.0, model);
        }
    }

    private final AnswerSolverChecker checker = new AnswerSolverChecker(new FakeClient(), new PromptBuilder(), "solver-x");

    @Test
    void checkMangSoLieuLuotGoiLlm() {
        AnswerSolverChecker.SolverCheckOutcome out = checker.check("Đề", "$1$", "$2$", "$3$", "$4$", Option.B);
        assertEquals(321, out.usage().tokenCount());
        assertEquals(456.5, out.usage().latencyMs());
    }

    @Test
    void phanTichThuanKhongCoSoLieu() {
        assertNull(checker.parseAndCompare("{\"reasoning\": \"r\", \"judge_answer\": \"B\"}", "m", Option.B).usage());
    }
}
