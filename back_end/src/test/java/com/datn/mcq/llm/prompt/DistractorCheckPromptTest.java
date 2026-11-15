package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.DistractorCheck;
import com.datn.mcq.enums.Option;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Solver v7 (PA1 sau hotfix hai lượt, 15/11): ghép mù lỗi sai với phương án (H-1) ở LƯỢT 2, dùng chung với kiểm yêu cầu.
 * Lượt 1 giữ đúng v4 từng byte. Không gọi mạng: LLM giả trả lần lượt các phản hồi đã đặt.
 */
class DistractorCheckPromptTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String V7 = "solver-v7-distractor-check";

    /** LLM giả: ghi prompt, schema; trả lần lượt {@code bodies} (phần tử "THROW" là ném lỗi). */
    static final class SeqLlm extends LlmClient {
        final List<String> prompts = new ArrayList<>();
        final List<String> schemas = new ArrayList<>();
        final List<String> bodies;

        SeqLlm(String... bodies) {
            super("FAKE_KEY_FOR_TESTS", "fake");
            this.bodies = new ArrayList<>(Arrays.asList(bodies));
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

    /** Lượt 1: Solver kết luận các phương án {@code correct} là đúng. */
    private static String solve(String correct) {
        StringBuilder items = new StringBuilder();
        List<String> c = List.of(correct.split(","));
        for (String o : List.of("A", "B", "C", "D")) {
            items.append(items.length() > 0 ? ", " : "").append("{\"option\": \"").append(o).append("\", \"correct\": ")
                    .append(c.contains(o)).append(", \"reason\": \"kiểm ").append(o).append("\"}");
        }
        return "{\"reasoning\": \"LỜI GIẢI LƯỢT MỘT\", \"options_verdict\": [" + items + "], \"correct_count\": " + c.size()
                + ", \"judge_answer\": \"" + (c.size() == 1 ? c.get(0) : "multiple") + "\"}";
    }

    private static final String Q = "Khai triển $(x+1)^2$";
    private static final String CHECKS = "[{\"error\": 1, \"result\": \"A\", \"reason\": \"ra $x^2+1$\"},"
            + " {\"error\": 2, \"result\": \"none\", \"reason\": \"không ra\"},"
            + " {\"error\": 3, \"result\": \"B\", \"reason\": \"ra đáp án\"}]";

    /** Lỗi theo phương án; đáp án đúng B có chữ để kiểm không bị gửi. */
    private static Map<String, String> errors() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("A", "Nhầm $(a+b)^2 = a^2 + b^2$");
        m.put("B", "CHỮ Ở PHƯƠNG ÁN ĐÚNG");
        m.put("C", "Sai dấu hạng tử giữa");
        m.put("D", "Quên nhân đôi tích");
        return m;
    }

    private static AnswerSolverChecker.SolverCheckOutcome check(SeqLlm llm, Map<String, String> errors,
                                                                List<String> codes) {
        return new AnswerSolverChecker(llm, new PromptBuilder(), "m", V7).check(Q, "$x^2+1$", "$x^2+2x+1$", "$x^2-2x+1$",
                "$x^2+x+1$", Option.B, codes, null, errors);
    }

    private static String v4() {
        return new PromptBuilder().buildSolverPrompt(PromptBuilder.SOLVER_PROMPT_VERSION_V4, Q, "$x^2+1$", "$x^2+2x+1$",
                "$x^2-2x+1$", "$x^2+x+1$");
    }

    @Test
    void v7LaMacDinh() {
        assertEquals(V7, PromptBuilder.SOLVER_PROMPT_VERSION);
        assertTrue(PromptBuilder.SOLVER_PROMPT_VERSIONS.containsAll(List.of("solver-v5-request-check",
                "solver-v6-two-pass", V7)), "v5, v6 giữ để đo lại");
    }

    @Test
    void luotMotGiongV4LuotHaiGhepMuKhongLoChuCai() throws Exception {
        SeqLlm llm = new SeqLlm(solve("B"), "{\"error_checks\": " + CHECKS + "}");
        AnswerSolverChecker.SolverCheckOutcome out = check(llm, errors(), null);
        assertEquals(2, llm.prompts.size(), "lỗi phương án kiểm ở lượt 2");
        assertEquals(v4(), llm.prompts.get(0), "lượt 1: đúng prompt v4 từng byte");
        assertEquals(AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON, llm.schemas.get(0));
        String p2 = llm.prompts.get(1);
        assertTrue(p2.contains("<<<LỖI_HỌC_SINH\n1. Nhầm $(a+b)^2 = a^2 + b^2$\n2. Sai dấu hạng tử giữa\n3. Quên nhân đôi tích\n"
                + "HẾT_LỖI>>>"), p2);
        assertFalse(p2.contains("CHỮ Ở PHƯƠNG ÁN ĐÚNG"), "lỗi ở đáp án đúng không gửi");
        assertTrue(p2.contains("LỜI GIẢI LƯỢT MỘT") && p2.contains("A. $x^2+1$"), "đề, phương án, lời giải lượt 1");
        assertTrue(p2.contains("\"none\" nếu áp lỗi không ra phương án nào"));
        assertFalse(p2.contains(TeacherRequestPrompt.SOLVER_HEADER), "không có yêu cầu thì không có khối yêu cầu");
        assertTrue(p2.contains("có 1 trường: \"error_checks\""), p2);
        assertEquals(List.of("error_checks"), props(llm.schemas.get(1)));
        assertTrue(out.passed());
        assertNull(out.requestCheck(), "câu không có yêu cầu");
        assertEquals(Map.of("A", DistractorCheck.MATCH, "C", DistractorCheck.MISMATCH, "D", DistractorCheck.MISMATCH),
                out.distractorChecks());
        assertTrue(out.reasoning().startsWith(AnswerSolverChecker.OPTIONS_VERDICT_PREFIX));
        assertTrue(out.reasoning().contains(DistractorCheckPrompt.NOTES_PREFIX + "\nA (lỗi 1): match (Solver: A) – ra $x^2+1$"),
                out.reasoning());
        assertEquals(200, out.usage().tokenCount(), "token cộng hai lượt");
    }

    @Test
    void coCaYeuCauThiMotLuotHaiDungChungErrorChecksTruocRequestCheck() throws Exception {
        SeqLlm llm = new SeqLlm(solve("B"), "{\"error_checks\": " + CHECKS + ", \"request_check\": {\"verdict\": \"met\","
                + " \"reason\": \"có bẫy\"}}");
        AnswerSolverChecker.SolverCheckOutcome out = check(llm, errors(), List.of("common_trap"));
        assertEquals(2, llm.prompts.size(), "một lượt 2 dùng chung");
        assertEquals(v4(), llm.prompts.get(0));
        String p2 = llm.prompts.get(1);
        assertTrue(p2.indexOf("HẾT_LỖI>>>") < p2.indexOf(TeacherRequestPrompt.SOLVER_HEADER), "khối lỗi trước khối yêu cầu");
        assertTrue(p2.contains("có 2 trường theo thứ tự: \"error_checks\", \"request_check\"."), p2);
        JsonNode s = JSON.readTree(llm.schemas.get(1));
        assertEquals(List.of("error_checks", "request_check"), props(llm.schemas.get(1)));
        assertEquals(List.of("error_checks", "request_check"), texts(s.get("propertyOrdering")));
        assertEquals(List.of("error_checks", "request_check"), texts(s.get("required")));
        assertEquals(List.of("A", "B", "C", "D", "none", "unclear"), texts(s.get("properties").get("error_checks")
                .get("items").get("properties").get("result").get("enum")));
        assertEquals("met", out.requestCheck().verdict().getDbValue());
        assertEquals(DistractorCheck.MATCH, out.distractorChecks().get("A"));
    }

    @Test
    void chiCoYeuCauThiLuotHaiNhuV6() {
        SeqLlm llm = new SeqLlm(solve("B"), "{\"request_check\": {\"verdict\": \"partial\", \"reason\": \"x\"}}");
        AnswerSolverChecker.SolverCheckOutcome out = check(llm, null, List.of("common_trap"));
        assertEquals(TeacherRequestPrompt.SECOND_PASS_SCHEMA, llm.schemas.get(1), "schema lượt 2 như v6");
        assertTrue(llm.prompts.get(1).contains("có 1 trường: \"request_check\"."));
        assertFalse(llm.prompts.get(1).contains("LỖI_HỌC_SINH"));
        assertNull(out.distractorChecks());
        assertEquals("partial", out.requestCheck().verdict().getDbValue());
    }

    @Test
    void khongYeuCauKhongLoiThiMotLuotNhuV4() {
        Map<String, String> onlyCorrect = new LinkedHashMap<>();
        onlyCorrect.put("B", "chỉ ở đáp án đúng");
        onlyCorrect.put("C", "   ");
        for (Map<String, String> none : Arrays.asList(null, Map.<String, String>of(), onlyCorrect)) {
            SeqLlm llm = new SeqLlm(solve("B"));
            AnswerSolverChecker.SolverCheckOutcome out = check(llm, none, List.of());
            assertEquals(1, llm.prompts.size());
            assertEquals(v4(), llm.prompts.get(0));
            assertNull(out.distractorChecks());
            assertNull(out.requestCheck());
        }
    }

    @Test
    void luotMotCoCoThiKhongGoiLuotHaiLoiChuaKiem() {
        SeqLlm llm = new SeqLlm(solve("A,B"));
        AnswerSolverChecker.SolverCheckOutcome out = check(llm, errors(), List.of("common_trap"));
        assertEquals(1, llm.prompts.size());
        assertFalse(out.passed());
        assertNull(out.distractorChecks(), "lỗi giữ chưa kiểm");
        assertEquals("unchecked", out.requestCheck().verdict().getDbValue());
        // chỉ có lỗi, không có yêu cầu: request_check vẫn null
        SeqLlm only = new SeqLlm(solve("A,B"));
        AnswerSolverChecker.SolverCheckOutcome o2 = check(only, errors(), null);
        assertNull(o2.requestCheck());
        assertNull(o2.distractorChecks());
    }

    @Test
    void luotHaiLoiThiLoiChuaKiemVaYeuCauUnchecked() {
        for (String second : List.of("THROW", "không phải JSON")) {
            SeqLlm llm = new SeqLlm(solve("B"), second);
            AnswerSolverChecker.SolverCheckOutcome out = check(llm, errors(), List.of("common_trap"));
            assertTrue(out.passed(), second);
            assertNull(out.distractorChecks(), "lượt 2 lỗi: lỗi giữ chưa kiểm (" + second + ")");
            assertEquals("unchecked", out.requestCheck().verdict().getDbValue());
        }
    }

    @Test
    void errorChecksThieuHoacHongTrongPhanHoiHopLeThiUnknown() {
        for (String body : List.of("{\"request_check\": {\"verdict\": \"met\", \"reason\": \"x\"}}",
                "{\"error_checks\": \"x\", \"request_check\": {\"verdict\": \"met\", \"reason\": \"x\"}}",
                "{\"error_checks\": [{\"error\": 1, \"result\": \"unclear\", \"reason\": \"\"}, {\"error\": 9,"
                        + " \"result\": \"A\"}, {\"error\": \"2\", \"result\": \"E\"}, 5],"
                        + " \"request_check\": {\"verdict\": \"met\", \"reason\": \"x\"}}")) {
            SeqLlm llm = new SeqLlm(solve("B"), body);
            AnswerSolverChecker.SolverCheckOutcome out = check(llm, errors(), List.of("common_trap"));
            assertEquals(Map.of("A", DistractorCheck.UNKNOWN, "C", DistractorCheck.UNKNOWN, "D", DistractorCheck.UNKNOWN),
                    out.distractorChecks(), body);
            assertEquals("met", out.requestCheck().verdict().getDbValue(), "request_check vẫn đọc được");
        }
        SeqLlm dup = new SeqLlm(solve("B"), "{\"error_checks\": [{\"error\": 1, \"result\": \"A\", \"reason\": \"r\"},"
                + " {\"error\": 1, \"result\": \"C\", \"reason\": \"r\"}]}");
        assertEquals(DistractorCheck.MATCH, check(dup, errors(), null).distractorChecks().get("A"), "trùng số: lấy phần tử đầu");
    }

    @Test
    void solverCuKhongGuiLoi() {
        for (String old : List.of(PromptBuilder.SOLVER_PROMPT_VERSION_V5, PromptBuilder.SOLVER_PROMPT_VERSION_V6)) {
            SeqLlm llm = new SeqLlm(solve("B"));
            AnswerSolverChecker.SolverCheckOutcome out = new AnswerSolverChecker(llm, new PromptBuilder(), "m", old)
                    .check(Q, "a", "b", "c", "d", Option.B, null, null, errors());
            assertEquals(1, llm.prompts.size(), old);
            assertNull(out.distractorChecks());
        }
    }

    // ---------- tiện ích ----------

    private static List<String> props(String schema) throws Exception {
        List<String> out = new ArrayList<>();
        JSON.readTree(schema).get("properties").fieldNames().forEachRemaining(out::add);
        return out;
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
