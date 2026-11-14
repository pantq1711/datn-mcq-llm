package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.TeacherRequest;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Solver hai lượt ({@code solver-v6-two-pass}, hotfix 14/11): đo TN1 với chip {@code common_trap} cho thấy khối yêu cầu trong
 * CÙNG lượt gọi làm Solver bắt câu nhiều đáp án đúng kém đi (4/9 so với 7/9 và 5/9 của v4). Lượt 1 giữ đúng prompt và schema
 * của v4 (mcq) hoặc bản cũ (tf, sa) cho MỌI câu; lượt 2 chỉ kiểm yêu cầu, chỉ khi câu có yêu cầu và lượt 1 không gắn cờ đáp án.
 * Không gọi mạng: LLM giả trả lần lượt các phản hồi đã đặt.
 */
class SolverTwoPassTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String V6 = "solver-v6-two-pass";

    /** LLM giả: ghi prompt, schema; trả lần lượt {@code bodies} (phần tử "THROW" là ném lỗi). */
    static final class SeqLlm extends LlmClient {
        final List<String> prompts = new ArrayList<>();
        final List<String> schemas = new ArrayList<>();
        final List<String> bodies;

        SeqLlm(String... bodies) {
            super("FAKE_KEY_FOR_TESTS", "fake");
            this.bodies = new ArrayList<>(List.of(bodies));
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            prompts.add(prompt);
            schemas.add(schema);
            String body = bodies.remove(0);
            if ("THROW".equals(body)) {
                throw new IllegalStateException("Gemini giả: HTTP 503");
            }
            return new LlmCallResult(body, 100, 10.0, 0.0, modelName);
        }
    }

    private static String mcqBody(String correctLetters) {
        StringBuilder items = new StringBuilder();
        List<String> correct = List.of(correctLetters.split(","));
        for (String o : List.of("A", "B", "C", "D")) {
            items.append(items.length() > 0 ? ", " : "").append("{\"option\": \"").append(o)
                    .append("\", \"correct\": ").append(correct.contains(o)).append(", \"reason\": \"kiểm ").append(o)
                    .append("\"}");
        }
        String judge = correct.size() == 1 ? correct.get(0) : "multiple";
        return "{\"reasoning\": \"LỜI GIẢI LƯỢT MỘT\", \"options_verdict\": [" + items + "], \"correct_count\": "
                + correct.size() + ", \"judge_answer\": \"" + judge + "\"}";
    }

    private static final String MET = "{\"request_check\": {\"verdict\": \"met\", \"reason\": \"có bẫy\"}}";
    private static final List<String> TRAP = List.of("common_trap");
    private static final List<String> STATEMENTS = List.of("$1 + 1 = 2$", "$2 > 3$", "$x^2 \\ge 0$", "$0 = 1$");
    private static final String TF_BODY = "{\"reasoning_a\": \"r\", \"judge_a\": \"T\", \"reasoning_b\": \"r\", \"judge_b\": \"F\","
            + " \"reasoning_c\": \"r\", \"judge_c\": \"T\", \"reasoning_d\": \"r\", \"judge_d\": \"F\"}";

    private static AnswerSolverChecker checker(SeqLlm llm) {
        return new AnswerSolverChecker(llm, new PromptBuilder(), "m", V6);
    }

    @Test
    void v6LaMacDinhVaCoTrongDanhSachPhienBan() {
        assertEquals(V6, PromptBuilder.SOLVER_PROMPT_VERSION);
        assertTrue(PromptBuilder.SOLVER_PROMPT_VERSIONS.contains(V6));
        assertTrue(PromptBuilder.SOLVER_PROMPT_VERSIONS.contains("solver-v5-request-check"), "v5 giữ để đo lại");
    }

    @Test
    void luotMotGiongHetV4TungByteKeCaKhiCoChipVaLuotHaiChiKiemYeuCau() throws Exception {
        PromptBuilder builder = new PromptBuilder();
        String v4 = builder.buildSolverPrompt(PromptBuilder.SOLVER_PROMPT_VERSION_V4, "Phân tích $2x^2 - 4x$", "$2x(x - 2)$",
                "$x(2x - 4)$", "$2(x^2 - 2x)$", "$x^2 - 2$");
        SeqLlm llm = new SeqLlm(mcqBody("A"), MET);
        AnswerSolverChecker.SolverCheckOutcome out = checker(llm).check("Phân tích $2x^2 - 4x$", "$2x(x - 2)$",
                "$x(2x - 4)$", "$2(x^2 - 2x)$", "$x^2 - 2$", Option.A, TRAP, "Dùng số nhỏ");
        assertEquals(2, llm.prompts.size(), "hai lượt");
        assertEquals(v4, llm.prompts.get(0), "lượt 1: đúng prompt v4");
        assertEquals(AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON, llm.schemas.get(0), "lượt 1: đúng schema v4");
        assertTrue(out.passed());
        assertEquals("met", out.requestCheck().verdict().getDbValue());
        assertEquals("có bẫy", out.requestCheck().reason());

        String p2 = llm.prompts.get(1);
        assertTrue(p2.contains("Phân tích $2x^2 - 4x$") && p2.contains("A. $2x(x - 2)$") && p2.contains("D. $x^2 - 2$"),
                "lượt 2 có đề và phương án");
        assertTrue(p2.contains("LỜI GIẢI LƯỢT MỘT"), "lượt 2 có lời giải của lượt 1");
        assertTrue(p2.contains(TeacherRequest.COMMON_TRAP.promptLine()) && p2.contains("<<< Dùng số nhỏ >>>"));
        assertFalse(p2.contains("options_verdict") || p2.contains("judge_answer"), "lượt 2 không giải lại, không chọn đáp án");
        assertFalse(p2.contains("Đáp án của người soạn"), p2);
        JsonNode s2 = JSON.readTree(llm.schemas.get(1));
        List<String> props = new ArrayList<>();
        s2.get("properties").fieldNames().forEachRemaining(props::add);
        assertEquals(List.of("request_check"), props, "lượt 2 chỉ trả request_check");
        assertTrue(p2.contains("có 1 trường: \"request_check\""), p2);
        assertEquals(200, out.usage().tokenCount(), "token cộng cả hai lượt");
    }

    @Test
    void cauKhongYeuCauMotLuotNhuV4() {
        SeqLlm llm = new SeqLlm(mcqBody("B"));
        AnswerSolverChecker.SolverCheckOutcome out = checker(llm).check("Đề", "a", "b", "c", "d", Option.B, List.of(), " ");
        assertEquals(1, llm.prompts.size());
        assertEquals(new PromptBuilder().buildSolverPrompt(PromptBuilder.SOLVER_PROMPT_VERSION_V4, "Đề", "a", "b", "c", "d"),
                llm.prompts.get(0));
        assertNull(out.requestCheck());
    }

    @Test
    void luotMotCoCoThiKhongGoiLuotHaiVaUnchecked() {
        SeqLlm llm = new SeqLlm(mcqBody("A,B"));
        AnswerSolverChecker.SolverCheckOutcome out = checker(llm).check("Đề", "a", "b", "c", "d", Option.A, TRAP, null);
        assertEquals(1, llm.prompts.size(), "lượt 1 có cờ: không gọi lượt 2");
        assertFalse(out.passed());
        assertEquals("unchecked", out.requestCheck().verdict().getDbValue());
    }

    @Test
    void luotHaiLoiHoacHongThiUncheckedKhongDoiKetLuanLuotMot() {
        for (String second : List.of("THROW", "{\"request_check\": \"met\"}", "không phải JSON",
                "{\"request_check\": {\"verdict\": \"unchecked\"}}")) {
            SeqLlm llm = new SeqLlm(mcqBody("B"), second);
            AnswerSolverChecker.SolverCheckOutcome out = checker(llm).check("Đề", "a", "b", "c", "d", Option.B, TRAP, null);
            assertEquals(2, llm.prompts.size(), second);
            assertTrue(out.passed(), "kết luận lượt 1 giữ nguyên: " + second);
            assertEquals("B", out.judgeAnswer().getDbValue());
            assertEquals("unchecked", out.requestCheck().verdict().getDbValue(), second);
            assertTrue(out.requestCheck().reason() != null && !out.requestCheck().reason().isBlank(), "có lý do");
        }
    }

    @Test
    void tfVaSaHaiLuotLuotMotNhuBanCu() {
        SeqLlm llm = new SeqLlm(TF_BODY, MET, "{\"reasoning\": \"r\", \"judge_answer\": \"5\"}", MET);
        AnswerSolverChecker c = checker(llm);
        AnswerSolverChecker.TfSolverOutcome tf = c.checkTrueFalse("Cho $x$.", STATEMENTS, List.of("T", "F", "T", "F"),
                List.of("tf_shared_context"), null);
        assertEquals(TfPromptDraftV0.buildSolverPrompt("Cho $x$.", STATEMENTS), llm.prompts.get(0));
        assertEquals(TfPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON, llm.schemas.get(0));
        assertTrue(llm.prompts.get(1).contains("a) $1 + 1 = 2$"), llm.prompts.get(1));
        assertEquals("met", tf.requestCheck().verdict().getDbValue());
        AnswerSolverChecker.SaSolverOutcome sa = c.checkShortAnswer("Tính $2 + 3$.", "5", List.of("geometry_link"), null);
        assertEquals(SaPromptDraftV0.buildSolverPrompt("Tính $2 + 3$."), llm.prompts.get(2));
        assertEquals(SaPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON, llm.schemas.get(2));
        assertTrue(sa.passed());
        assertEquals("met", sa.requestCheck().verdict().getDbValue());
        assertEquals(4, llm.prompts.size());
    }

    @Test
    void solverV5VanMotLuotNhuPrB() {
        String body = mcqBody("B");
        SeqLlm llm = new SeqLlm(body.substring(0, body.length() - 1)
                + ", \"request_check\": {\"verdict\": \"partial\", \"reason\": \"x\"}}");
        AnswerSolverChecker v5 = new AnswerSolverChecker(llm, new PromptBuilder(), "m", "solver-v5-request-check");
        AnswerSolverChecker.SolverCheckOutcome out = v5.check("Đề", "a", "b", "c", "d", Option.B, TRAP, null);
        assertEquals(1, llm.prompts.size());
        assertTrue(llm.prompts.get(0).contains(TeacherRequestPrompt.SOLVER_HEADER));
        assertEquals(AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON_REQUEST, llm.schemas.get(0));
        assertEquals("partial", out.requestCheck().verdict().getDbValue());
    }
}
