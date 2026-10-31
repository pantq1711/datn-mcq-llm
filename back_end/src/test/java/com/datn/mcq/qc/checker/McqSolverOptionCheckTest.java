package com.datn.mcq.qc.checker;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Option;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Solver mcq xét từng phương án (thẩm định 31/10, prompt solver-v3-option-check): kết luận theo số phương án Solver cho
 * là đúng, không theo một đáp án duy nhất. Phân tích thuần, không gọi mạng.
 */
class McqSolverOptionCheckTest {

    private final AnswerSolverChecker checker = new AnswerSolverChecker(
            new LlmClient("", "gemini-3.5-flash-lite"), new PromptBuilder(), "gemini-3.5-flash-lite");

    private static String item(String option, Object correct) {
        return "{\"option\": \"" + option + "\", \"correct\": " + correct + ", \"reason\": \"nhân lại kiểm " + option
                + "\"}";
    }

    private static String v3(String judge, int count, boolean a, boolean b, boolean c, boolean d) {
        return "{\"reasoning\": \"Ta có $2x^3 - 6x^2 = 2x^2(x - 3)$.\", \"options_verdict\": [" + item("A", a) + ", "
                + item("B", b) + ", " + item("C", c) + ", " + item("D", d) + "], \"correct_count\": " + count
                + ", \"judge_answer\": \"" + judge + "\"}";
    }

    @Test
    void haiPhuongAnDungThiMultipleCorrectVaKhongQua() {
        AnswerSolverChecker.SolverCheckOutcome out =
                checker.parseAndCompare(v3("multiple", 2, true, false, true, false), "m", Option.A);
        assertFalse(out.passed(), "đáp án A nằm trong số phương án đúng nhưng câu vẫn hỏng");
        assertEquals(AnswerChoice.MULTIPLE, out.judgeAnswer());
        assertEquals(List.of(true, false, true, false), out.optionCorrect());
        assertEquals(2, out.correctCount());
        assertTrue(out.reasoning().startsWith(AnswerSolverChecker.OPTIONS_VERDICT_PREFIX + " 2/4 phương án đúng\n"),
                out.reasoning());
        assertTrue(out.reasoning().contains("\nA: đúng – nhân lại kiểm A\nB: sai – nhân lại kiểm B\n"), out.reasoning());
        assertTrue(out.reasoning().endsWith("\n\nTa có $2x^3 - 6x^2 = 2x^2(x - 3)$."), "giữ lời giải của Solver");
    }

    /** Câu 37 của thẩm định: cả 4 phương án đều bằng đa thức gốc. */
    @Test
    void bonPhuongAnDungThiMultipleCorrect() {
        AnswerSolverChecker.SolverCheckOutcome out =
                checker.parseAndCompare(v3("multiple", 4, true, true, true, true), "m", Option.B);
        assertFalse(out.passed());
        assertEquals(AnswerChoice.MULTIPLE, out.judgeAnswer());
        assertEquals(4, out.correctCount());
    }

    @Test
    void khongPhuongAnNaoDungThiNoCorrect() {
        AnswerSolverChecker.SolverCheckOutcome out =
                checker.parseAndCompare(v3("none", 0, false, false, false, false), "m", Option.B);
        assertFalse(out.passed());
        assertEquals(AnswerChoice.NONE, out.judgeAnswer());
        assertEquals(0, out.correctCount());
        assertTrue(out.reasoning().startsWith(AnswerSolverChecker.OPTIONS_VERDICT_PREFIX + " 0/4 phương án đúng"));
    }

    @Test
    void motPhuongAnDungKhopDapAnThiQua() {
        AnswerSolverChecker.SolverCheckOutcome out =
                checker.parseAndCompare(v3("B", 1, false, true, false, false), "m", Option.B);
        assertTrue(out.passed());
        assertEquals(AnswerChoice.B, out.judgeAnswer());
        assertEquals(1, out.correctCount());
    }

    @Test
    void motPhuongAnDungKhacDapAnThiInconsistentNhuCu() {
        AnswerSolverChecker.SolverCheckOutcome out =
                checker.parseAndCompare(v3("C", 1, false, false, true, false), "m", Option.B);
        assertFalse(out.passed());
        assertEquals(AnswerChoice.C, out.judgeAnswer());
    }

    @Test
    void judgeAnswerMauThuanVoiTungPhuongAnThiTheoTungPhuongAn() {
        // Solver ghi judge_answer = B (dừng ở phương án đầu khớp) nhưng chính nó xét B và D đều đúng.
        AnswerSolverChecker.SolverCheckOutcome out =
                checker.parseAndCompare(v3("B", 1, false, true, false, true), "m", Option.B);
        assertFalse(out.passed());
        assertEquals(AnswerChoice.MULTIPLE, out.judgeAnswer());
        assertEquals(2, out.correctCount());
    }

    @Test
    void correctCountLechThiDemLaiTuTungPhuongAn() {
        AnswerSolverChecker.SolverCheckOutcome out =
                checker.parseAndCompare(v3("B", 2, false, true, false, false), "m", Option.B);
        assertTrue(out.passed());
        assertEquals(1, out.correctCount());
    }

    @Test
    void thieuJudgeAnswerNhungCoTungPhuongAnThiVanKetLuan() {
        String raw = "{\"reasoning\": \"r\", \"options_verdict\": [" + item("A", true) + ", " + item("B", false) + ", "
                + item("C", false) + ", " + item("D", false) + "]}";
        AnswerSolverChecker.SolverCheckOutcome out = checker.parseAndCompare(raw, "m", Option.A);
        assertTrue(out.passed());
        assertEquals(AnswerChoice.A, out.judgeAnswer());
    }

    @Test
    void thieuOptionsVerdictThiTuongThichNguocNhuSolverCu() {
        AnswerSolverChecker.SolverCheckOutcome multiple =
                checker.parseAndCompare("{\"reasoning\": \"r\", \"judge_answer\": \"multiple\"}", "m", Option.A);
        assertFalse(multiple.passed());
        assertEquals(AnswerChoice.MULTIPLE, multiple.judgeAnswer());
        assertNull(multiple.optionCorrect(), "không có kết quả từng phương án");
        assertNull(multiple.correctCount());
        assertEquals("r", multiple.reasoning(), "không thêm khối xét từng phương án, nên không gắn lý do mới");

        AnswerSolverChecker.SolverCheckOutcome match =
                checker.parseAndCompare("{\"reasoning\": \"r\", \"judge_answer\": \"B\"}", "m", Option.B);
        assertTrue(match.passed());
    }

    @Test
    void optionsVerdictHongThiBoQuaVaDungJudgeAnswer() {
        String threeItems = "{\"reasoning\": \"r\", \"options_verdict\": [" + item("A", true) + ", " + item("B", true)
                + ", " + item("C", false) + "], \"correct_count\": 2, \"judge_answer\": \"A\"}";
        String duplicateLetter = "{\"reasoning\": \"r\", \"options_verdict\": [" + item("A", true) + ", "
                + item("A", true) + ", " + item("C", false) + ", " + item("D", false)
                + "], \"correct_count\": 2, \"judge_answer\": \"A\"}";
        String notBoolean = "{\"reasoning\": \"r\", \"options_verdict\": [" + item("A", "\"có\"") + ", "
                + item("B", true) + ", " + item("C", false) + ", " + item("D", false)
                + "], \"correct_count\": 2, \"judge_answer\": \"A\"}";
        String notArray = "{\"reasoning\": \"r\", \"options_verdict\": \"A đúng\", \"judge_answer\": \"A\"}";
        for (String raw : List.of(threeItems, duplicateLetter, notBoolean, notArray)) {
            AnswerSolverChecker.SolverCheckOutcome out = checker.parseAndCompare(raw, "m", Option.A);
            assertTrue(out.passed(), raw);
            assertEquals(AnswerChoice.A, out.judgeAnswer(), raw);
            assertNull(out.optionCorrect(), raw);
            assertEquals("r", out.reasoning(), raw);
        }
    }

    @Test
    void optionsVerdictKhongTheoThuTuVanDocDungTheoChuCai() {
        String raw = "{\"reasoning\": \"r\", \"options_verdict\": [" + item("d", false) + ", " + item("C", true) + ", "
                + item("B", false) + ", " + item("A", false) + "], \"correct_count\": 1, \"judge_answer\": \"C\"}";
        AnswerSolverChecker.SolverCheckOutcome out = checker.parseAndCompare(raw, "m", Option.C);
        assertTrue(out.passed());
        assertEquals(List.of(false, false, true, false), out.optionCorrect());
    }

    @Test
    void thieuCaHaiThiLoi() {
        assertThrows(IllegalArgumentException.class,
                () -> checker.parseAndCompare("{\"reasoning\": \"r\"}", "m", Option.A));
    }

    /** LlmClient giả ghi lại prompt và schema của lượt gọi. */
    private static final class CapturingClient extends LlmClient {
        String prompt;
        String schema;
        final String body;

        CapturingClient(String body) {
            super("FAKE_KEY_FOR_TESTS", "fake");
            this.body = body;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature,
                                                    String schema) {
            this.prompt = prompt;
            this.schema = schema;
            return new LlmCallResult(body, 10, 1.0, 0.0, modelName);
        }
    }

    @Test
    void macDinhGoiPromptVaSchemaXetTungPhuongAn() {
        CapturingClient client = new CapturingClient(v3("multiple", 2, true, true, false, false));
        AnswerSolverChecker v3 = new AnswerSolverChecker(client, new PromptBuilder(), "m");
        assertEquals(PromptBuilder.SOLVER_PROMPT_VERSION, v3.promptVersion());
        AnswerSolverChecker.SolverCheckOutcome out = v3.check("Đề", "a", "b", "c", "d", Option.A);
        assertEquals(AnswerChoice.MULTIPLE, out.judgeAnswer());
        assertEquals(AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON, client.schema);
        assertTrue(client.prompt.contains("options_verdict"));
    }

    @Test
    void cauHinhV2ThiGoiPromptVaSchemaCu() {
        CapturingClient client = new CapturingClient("{\"reasoning\": \"r\", \"judge_answer\": \"A\"}");
        AnswerSolverChecker v2 = new AnswerSolverChecker(client, new PromptBuilder(), "m",
                PromptBuilder.SOLVER_PROMPT_VERSION_V2);
        assertEquals("solver-v2-delimited", v2.promptVersion());
        assertTrue(v2.check("Đề", "a", "b", "c", "d", Option.A).passed());
        assertEquals(AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON_V2, client.schema);
        assertFalse(client.prompt.contains("options_verdict"));
    }

    @Test
    void phienBanPromptLaThiKhongKhoiTaoDuoc() {
        assertThrows(IllegalArgumentException.class, () -> new AnswerSolverChecker(
                new LlmClient("", "m"), new PromptBuilder(), "m", "solver-v9"));
        assertEquals(PromptBuilder.SOLVER_PROMPT_VERSION, new AnswerSolverChecker(
                new LlmClient("", "m"), new PromptBuilder(), "m", " ").promptVersion(), "để trống là mặc định");
    }
}
