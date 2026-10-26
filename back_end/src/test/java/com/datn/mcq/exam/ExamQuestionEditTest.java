package com.datn.mcq.exam;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.generation.worker.StaleJobRecovery;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
import com.datn.mcq.llm.prompt.TfPromptDraftV0;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-2: giáo viên sửa câu trong ô đề chưa Giữ ({@code PUT /api/exams/{examId}/slots/{slotId}/question}) qua HTTP thật
 * trên datn_mcq_test, LLM giả (không gọi mạng).
 *
 * <p>Mỗi lần sửa tạo một dòng generated_question mới (edited_from_question_id trỏ bản trước), bản gốc LLM và kết quả QC
 * gốc giữ nguyên. QC không cần LLM (format, sửa/kiểm LaTeX, dedup loại trừ chính chuỗi bản) chạy trong request; Solver
 * chạy qua hàng đợi generation_job (job_type = answer_check) khi đề, phương án/ý hoặc đáp án đổi.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamQuestionEditTest {

    /** Solver giả: mcq trả {@code mcqJudge}, sa {@code saJudge}, tf {@code tfJudges}; có thể lỗi hoặc chạy một móc. */
    static final class EditSolver extends LlmClient {
        String mcqJudge = "B";
        String saJudge = "5";
        /** Lời giải thích Solver trả cho mcq và sa (W1-3: được lưu ở quality_check_result.solver_reasoning). */
        String reasoning = "r";
        /** null: đồng ý với nhãn câu tf vừa sinh. */
        String tfJudges;
        /** true: mọi lần gọi đều lỗi (giả lập hết thời gian chờ / lỗi mạng). */
        boolean fail;
        /** Chạy một lần ở lần gọi tới, trong lúc Solver "đang chạy" (ngoài transaction của worker). */
        ThrowingRunnable onCall;
        int calls;

        EditSolver() {
            super("FAKE_KEY_FOR_TESTS", "fake-solver-model");
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            calls++;
            if (onCall != null) {
                ThrowingRunnable hook = onCall;
                onCall = null;
                try {
                    hook.run();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }
            if (fail) {
                throw new IllegalStateException("Solver giả: hết thời gian chờ");
            }
            String body;
            if (TfPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON.equals(schema)) {
                String[] j = (tfJudges != null ? tfJudges : ExamBuildTest.FakeGenerator.lastTfLabels).split(",");
                body = "{\"reasoning_a\": \"r\", \"judge_a\": \"" + j[0] + "\", \"reasoning_b\": \"r\", \"judge_b\": \""
                        + j[1] + "\", \"reasoning_c\": \"r\", \"judge_c\": \"" + j[2] + "\", \"reasoning_d\": \"r\","
                        + " \"judge_d\": \"" + j[3] + "\"}";
            } else if (SaPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON.equals(schema)) {
                body = "{\"reasoning\": \"" + reasoning + "\", \"judge_answer\": \"" + saJudge + "\"}";
            } else {
                body = "{\"reasoning\": \"" + reasoning + "\", \"judge_answer\": \"" + mcqJudge + "\"}";
            }
            return new LlmCallResult(body, 10, 1.0, 0.0, modelName);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final EditSolver SOLVER = new EditSolver();

    @TestBean
    private AnswerSolverChecker answerSolverChecker;

    static AnswerSolverChecker answerSolverChecker() {
        return new AnswerSolverChecker(SOLVER, new PromptBuilder(), "fake-solver-model");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private GenerationJobProcessor jobProcessor;
    @Autowired
    private QcOrchestrator qcOrchestrator;
    @Autowired
    private DedupScopeResolver dedupScopeResolver;
    @Autowired
    private StaleJobRecovery staleJobRecovery;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private ExamBuildTest.FakeGenerator generator;
    private int subject;
    private int teacher;
    private int otherTeacher;
    private int matrixId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "ExamQuestionEditTest.setUp");
        SOLVER.mcqJudge = "B";
        SOLVER.saJudge = "5";
        SOLVER.tfJudges = null;
        SOLVER.fail = false;
        SOLVER.onCall = null;
        SOLVER.calls = 0;
        ExamBuildTest.FakeGenerator.lastTfLabels = "T,F,T,F";
        generator = new ExamBuildTest.FakeGenerator();
        subject = insert("INSERT INTO subject (name) VALUES ('Toán')");
        teacher = insert("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.edit@test', 'teacher')");
        otherTeacher = insert("INSERT INTO users (name, email, role) VALUES ('GV khác', 'gv.khac@test', 'teacher')");
        matrixId = json(perform(post("/api/exam-matrices").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Đề sửa câu\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"cells\": ["
                        + "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 2,"
                        + " \"topic\": \"Đa thức\", \"content\": \"Đơn thức, đa thức\","
                        + " \"learningOutcomes\": {\"easy\": \"Nhận biết được đơn thức, đa thức.\"}},"
                        + "{\"part\": \"II\", \"questionType\": \"tf\", \"statementDifficulties\": [\"easy\", \"easy\","
                        + " \"medium\", \"hard\"], \"questionCount\": 1, \"topic\": \"Hằng đẳng thức\","
                        + " \"learningOutcomes\": {\"easy\": \"Nhận biết.\", \"medium\": \"Khai triển.\","
                        + " \"hard\": \"Vận dụng.\"}},"
                        + "{\"part\": \"III\", \"questionType\": \"sa\", \"difficulty\": \"medium\", \"questionCount\": 1,"
                        + " \"topic\": \"Tứ giác\", \"content\": \"Tứ giác, hình thang cân\","
                        + " \"learningOutcomes\": {\"medium\": \"Tính được góc trong tứ giác.\"}}]}"))
                .andExpect(status().isOk())).get("id").asInt();
    }

    // ---------- tiện ích ----------

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(AuthTestSupport.teacher(teacher)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions putQuestion(int examId, int slotId, JsonNode body) throws Exception {
        return perform(put("/api/exams/" + examId + "/slots/" + slotId + "/question")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private ResultActions keep(int examId, int slotId) throws Exception {
        return perform(post("/api/exams/" + examId + "/slots/" + slotId + "/keep")
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private GenerationJobPoller poller() {
        return new GenerationJobPoller(jobProcessor, generator, qcOrchestrator, dedupScopeResolver, 3);
    }

    /** Xử lý mọi job đến hạn (đặt lại next_run_at về quá khứ trước mỗi vòng). */
    private void processAll() {
        GenerationJobPoller poller = poller();
        for (int i = 0; i < 50; i++) {
            jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'");
            if (!poller.processNextJob(true).processed()) {
                return;
            }
        }
    }

    /** Đề có đủ 4 câu sinh ra đã qua QC (mcq x2, tf, sa). */
    private int readyExam() throws Exception {
        int examId = json(perform(post("/api/exams/build").contentType(MediaType.APPLICATION_JSON)
                .content("{\"matrixId\": " + matrixId + "}")).andExpect(status().isOk())).get("examId").asInt();
        for (int round = 0; round < 3; round++) {
            processAll();
            perform(get("/api/exams/" + examId + "/progress")).andExpect(status().isOk());
        }
        assertEquals(4, count("SELECT count(*) FROM exam_slot WHERE exam_id = ? AND state = 'ready'", examId));
        return examId;
    }

    private int slotId(int examId, String type, int slotNo) {
        return count("SELECT id FROM exam_slot WHERE exam_id = ? AND question_type = ? AND slot_no = ?",
                examId, type, slotNo);
    }

    private int questionOf(int slotId) {
        return count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slotId);
    }

    private Map<String, Object> question(int id) {
        return jdbc.queryForMap("SELECT * FROM generated_question WHERE id = ?", id);
    }

    private String statusOf(int questionId) {
        return jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?", String.class, questionId);
    }

    /** Body sửa câu mcq chép nguyên nội dung hiện tại của câu {@code base}. */
    private ObjectNode mcqBody(int base) {
        Map<String, Object> q = question(base);
        ObjectNode n = objectMapper.createObjectNode();
        n.put("baseQuestionId", base);
        n.put("questionText", (String) q.get("question_text"));
        n.put("optionA", (String) q.get("option_a"));
        n.put("optionB", (String) q.get("option_b"));
        n.put("optionC", (String) q.get("option_c"));
        n.put("optionD", (String) q.get("option_d"));
        n.put("correctOption", (String) q.get("correct_option"));
        n.put("explanation", (String) q.get("explanation"));
        return n;
    }

    private ObjectNode saBody(int base) {
        Map<String, Object> q = question(base);
        ObjectNode n = objectMapper.createObjectNode();
        n.put("baseQuestionId", base);
        n.put("questionText", (String) q.get("question_text"));
        n.put("saAnswer", (String) q.get("sa_answer"));
        n.put("explanation", (String) q.get("explanation"));
        return n;
    }

    private ObjectNode tfBody(int base) {
        Map<String, Object> q = question(base);
        ObjectNode n = objectMapper.createObjectNode();
        n.put("baseQuestionId", base);
        n.put("questionText", (String) q.get("question_text"));
        n.put("explanation", (String) q.get("explanation"));
        ArrayNode statements = n.putArray("statements");
        for (Map<String, Object> s : jdbc.queryForList("SELECT position, statement_text, llm_label FROM"
                + " generated_question_statement WHERE question_id = ? ORDER BY position", base)) {
            ObjectNode item = statements.addObject();
            item.put("key", (String) s.get("position"));
            item.put("text", (String) s.get("statement_text"));
            item.put("label", (String) s.get("llm_label"));
        }
        return n;
    }

    /** Câu của chỗ {@code slotId} trong GET /api/exams/{examId}. */
    private JsonNode viewItem(int examId, int slotId) throws Exception {
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        for (JsonNode part : view.get("parts")) {
            for (JsonNode item : part.get("items")) {
                if (item.get("slotId").asInt() == slotId) {
                    return item;
                }
            }
        }
        throw new AssertionError("Không thấy chỗ " + slotId);
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    /** Dấu vết ghi DB của luồng sửa: số câu, số job, số dòng nhật ký, số dòng QC. */
    private List<Integer> footprint() {
        return List.of(count("SELECT count(*) FROM generated_question"), count("SELECT count(*) FROM generation_job"),
                count("SELECT count(*) FROM exam_slot_action"), count("SELECT count(*) FROM quality_check_result"));
    }

    private int pendingAnswerCheckJobs(int questionId) {
        return count("SELECT count(*) FROM generation_job WHERE question_id = ? AND job_type = 'answer_check'"
                + " AND status IN ('pending', 'running')", questionId);
    }

    // ---------- sửa hợp lệ ----------

    @Test
    void suaMcqTaoBanMoiGiuNguyenBanGocVaXepJobSolver() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int base = questionOf(slot);
        Map<String, Object> original = question(base);
        int originalQc = count("SELECT count(*) FROM quality_check_result WHERE question_id = ?", base);
        String newText = "Đơn thức $2x^1$ có bậc là:";
        int calls = SOLVER.calls;

        JsonNode item = json(putQuestion(examId, slot, mcqBody(base).put("questionText", newText))
                .andExpect(status().isOk()));
        assertEquals(calls, SOLVER.calls, "request không gọi và không chờ Solver");
        int version = item.get("questionId").asInt();
        assertNotEquals(base, version);
        assertEquals("ready", item.get("state").asText());
        assertEquals(newText, item.get("questionText").asText());
        assertTrue(item.get("edited").asBoolean());
        assertEquals(base, item.get("originalQuestionId").asInt());
        assertEquals("checking", item.get("qcState").asText());
        assertTrue(texts(item.get("reviewReasons")).contains("solver_pending"), item.toString());

        Map<String, Object> v = question(version);
        assertEquals(base, v.get("edited_from_question_id"));
        assertEquals(original.get("template_id"), v.get("template_id"));
        assertEquals(original.get("run_id"), v.get("run_id"));
        assertEquals(original.get("llm_difficulty"), v.get("llm_difficulty"));
        assertEquals("", v.get("raw_llm_output"), "bản giáo viên sửa không có đầu ra LLM");
        assertEquals("generated", v.get("status"), "chờ Solver nên chưa qc_passed");
        assertEquals(1, count("SELECT count(*) FROM quality_check_result WHERE question_id = ? AND check_type = 'dedup'"
                + " AND passed = TRUE", version), "dedup chạy trong request");

        assertEquals(original, question(base), "bản gốc LLM không đổi");
        assertEquals(originalQc, count("SELECT count(*) FROM quality_check_result WHERE question_id = ?", base));
        assertEquals(version, questionOf(slot));
        assertEquals("ready", jdbc.queryForObject("SELECT state FROM exam_slot WHERE id = ?", String.class, slot));

        Map<String, Object> log = jdbc.queryForMap("SELECT action, generated_question_id, edit_distance, actor_id,"
                + " changed_fields::text AS changed FROM exam_slot_action WHERE slot_id = ?", slot);
        assertEquals("edit", log.get("action"));
        assertEquals(version, log.get("generated_question_id"));
        assertEquals(teacher, log.get("actor_id"));
        assertTrue((Integer) log.get("edit_distance") > 0);
        assertEquals("[\"question_text\"]", ((String) log.get("changed")).replace(" ", ""));

        Map<String, Object> job = jdbc.queryForMap("SELECT * FROM generation_job WHERE question_id = ?"
                + " AND job_type = 'answer_check'", version);
        assertEquals("pending", job.get("status"));
        assertNull(job.get("exam_slot_id"), "job Solver không gắn chỗ (đối soát không nhầm là job sinh câu)");
    }

    /** Chữ của giáo viên là thẩm quyền: quy tắc loại lời giải tự thú nhận chỉ áp cho đầu ra của LLM. */
    @Test
    void suaTfCoCumGiongTuThuNhanVanLuuDuocVaKhongBiChanFormat() throws Exception {
        int examId = readyExam();
        int tfSlot = slotId(examId, "tf", 1);
        ObjectNode tf = tfBody(questionOf(tfSlot));
        String note = "Ghi chú: theo mẫu nhãn yêu cầu của tổ, ý b là Sai. Xin lỗi vì lần trước nhầm.";
        tf.put("explanation", note);
        int version = json(putQuestion(examId, tfSlot, tf).andExpect(status().isOk())).get("questionId").asInt();
        assertEquals(note, jdbc.queryForObject("SELECT explanation FROM generated_question WHERE id = ?", String.class,
                version));
    }

    @Test
    void suaTfVaSaHopLe() throws Exception {
        int examId = readyExam();
        int tfSlot = slotId(examId, "tf", 1);
        int tfBase = questionOf(tfSlot);
        List<String> levels = jdbc.queryForList("SELECT llm_difficulty FROM generated_question_statement"
                + " WHERE question_id = ? ORDER BY position", String.class, tfBase);
        ObjectNode tf = tfBody(tfBase);
        ArrayNode statements = (ArrayNode) tf.get("statements");
        ((ObjectNode) statements.get(0)).put("text", "Ý một đã sửa.").put("label", "F");
        ((ObjectNode) statements.get(1)).put("label", "T");
        ((ObjectNode) statements.get(2)).put("label", "T");
        ((ObjectNode) statements.get(3)).put("label", "F");
        int tfVersion = json(putQuestion(examId, tfSlot, tf).andExpect(status().isOk())).get("questionId").asInt();
        assertEquals(List.of("Ý một đã sửa.", "F", levels.get(0)), List.of(
                jdbc.queryForObject("SELECT statement_text FROM generated_question_statement WHERE question_id = ?"
                        + " AND position = 'a'", String.class, tfVersion),
                jdbc.queryForObject("SELECT llm_label FROM generated_question_statement WHERE question_id = ?"
                        + " AND position = 'a'", String.class, tfVersion),
                jdbc.queryForObject("SELECT llm_difficulty FROM generated_question_statement WHERE question_id = ?"
                        + " AND position = 'a'", String.class, tfVersion)));
        assertEquals(levels, jdbc.queryForList("SELECT llm_difficulty FROM generated_question_statement"
                + " WHERE question_id = ? ORDER BY position", String.class, tfVersion), "mức từng ý giữ nguyên");

        int saSlot = slotId(examId, "sa", 1);
        int saVersion = json(putQuestion(examId, saSlot, saBody(questionOf(saSlot)).put("saAnswer", "6"))
                .andExpect(status().isOk())).get("questionId").asInt();
        assertEquals("6", question(saVersion).get("sa_answer"));
        assertEquals(1, pendingAnswerCheckJobs(tfVersion));
        assertEquals(1, pendingAnswerCheckJobs(saVersion));

        SOLVER.tfJudges = "F,T,T,F";
        SOLVER.saJudge = "6";
        processAll();
        assertEquals("qc_passed", statusOf(tfVersion));
        assertEquals("qc_passed", statusOf(saVersion));
        assertEquals("passed", viewItem(examId, tfSlot).get("qcState").asText());
    }

    @Test
    void khongDoiGiThiKhongTaoBanMoi() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int base = questionOf(slot);
        List<Integer> before = footprint();
        JsonNode item = json(putQuestion(examId, slot, mcqBody(base).put("questionText",
                question(base).get("question_text") + "  ")).andExpect(status().isOk()));
        assertEquals(base, item.get("questionId").asInt());
        assertEquals(before, footprint());
    }

    // ---------- lỗi schema / đáp án / LaTeX ----------

    @Test
    void loiSchemaDapAnVaLatexTra400KhongGhiGi() throws Exception {
        int examId = readyExam();
        int mcq = slotId(examId, "mcq", 1);
        int mcqBase = questionOf(mcq);
        int tfSlot = slotId(examId, "tf", 1);
        int saSlot = slotId(examId, "sa", 1);
        List<Integer> before = footprint();

        assertEquals("INVALID_REQUEST", json(putQuestion(examId, mcq, mcqBody(mcqBase).put("correctOption", "E"))
                .andExpect(status().isBadRequest())).get("code").asText());
        assertEquals("INVALID_REQUEST", json(putQuestion(examId, mcq, mcqBody(mcqBase).put("saAnswer", "5"))
                .andExpect(status().isBadRequest())).get("code").asText(), "trường không áp dụng cho dạng câu");
        ObjectNode missing = mcqBody(mcqBase);
        missing.remove("optionD");
        assertEquals("INVALID_REQUEST", json(putQuestion(examId, mcq, missing)
                .andExpect(status().isBadRequest())).get("code").asText());
        ObjectNode noBase = mcqBody(mcqBase);
        noBase.remove("baseQuestionId");
        assertEquals("INVALID_REQUEST", json(putQuestion(examId, mcq, noBase)
                .andExpect(status().isBadRequest())).get("code").asText());

        ObjectNode threeStatements = tfBody(questionOf(tfSlot));
        ((ArrayNode) threeStatements.get("statements")).remove(3);
        putQuestion(examId, tfSlot, threeStatements).andExpect(status().isBadRequest());
        ObjectNode badLabel = tfBody(questionOf(tfSlot));
        ((ObjectNode) badLabel.get("statements").get(0)).put("label", "X");
        putQuestion(examId, tfSlot, badLabel).andExpect(status().isBadRequest());

        JsonNode badAnswer = json(putQuestion(examId, saSlot, saBody(questionOf(saSlot)).put("saAnswer", "abc"))
                .andExpect(status().isBadRequest()));
        assertEquals("FORMAT_INVALID", badAnswer.get("code").asText());
        assertTrue(texts(badAnswer.get("errors")).stream().anyMatch(e -> e.startsWith("answer:")), badAnswer.toString());

        JsonNode latex = json(putQuestion(examId, mcq, mcqBody(mcqBase)
                .put("questionText", "Giá trị $x của biểu thức là:")).andExpect(status().isBadRequest()));
        assertEquals("FORMAT_INVALID", latex.get("code").asText());
        assertTrue(texts(latex.get("errors")).stream().anyMatch(e -> e.startsWith("question_text")), latex.toString());

        JsonNode blank = json(putQuestion(examId, mcq, mcqBody(mcqBase).put("optionA", " "))
                .andExpect(status().isBadRequest()));
        assertTrue(texts(blank.get("errors")).contains("option_a: nội dung trống"), blank.toString());

        assertEquals(before, footprint(), "lỗi thì không ghi gì");
        assertEquals(mcqBase, questionOf(mcq));
    }

    @Test
    void tuDongDongDoLaCoGhiNhanCanhBao() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int base = questionOf(slot);
        JsonNode item = json(putQuestion(examId, slot, mcqBody(base).put("optionA", "$5"))
                .andExpect(status().isOk()));
        int version = item.get("questionId").asInt();
        assertEquals("$5$", question(version).get("option_a"));
        assertEquals("$5$", item.get("optionA").asText());
        assertTrue(texts(item.get("reviewReasons")).contains("warning: option_a: đã tự đóng $"), item.toString());
        String warnings = jdbc.queryForObject("SELECT warnings::text FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'format'", String.class, version);
        assertTrue(warnings.contains("option_a: đã tự đóng $"), warnings);
    }

    // ---------- dedup ----------

    @Test
    void dedupKhongTuKhopChinhNoVaCacBanTruoc() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int base = questionOf(slot);

        // Đổi đáp án: thân câu và 4 phương án giống hệt bản gốc LLM (cùng dấu vân tay).
        SOLVER.mcqJudge = "C";
        int v1 = json(putQuestion(examId, slot, mcqBody(base).put("correctOption", "C"))
                .andExpect(status().isOk())).get("questionId").asInt();
        assertEquals("generated", statusOf(v1), "không bị đánh dấu trùng với chính bản gốc");
        processAll();
        assertEquals("qc_passed", statusOf(v1));

        SOLVER.mcqJudge = "B";
        int v2 = json(putQuestion(examId, slot, mcqBody(v1).put("correctOption", "B"))
                .andExpect(status().isOk())).get("questionId").asInt();
        assertEquals("generated", statusOf(v2), "không trùng với bản gốc lẫn bản trước (v1 đã qc_passed)");
        processAll();
        assertEquals("qc_passed", statusOf(v2));
    }

    @Test
    void banSuaTrungCauKhacThiDuplicateKhongXepSolverVaChanGiu() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int other = questionOf(slotId(examId, "mcq", 2));
        ObjectNode copy = mcqBody(other).put("baseQuestionId", questionOf(slot));
        JsonNode item = json(putQuestion(examId, slot, copy).andExpect(status().isOk()));
        int version = item.get("questionId").asInt();
        assertEquals("duplicate", statusOf(version));
        assertEquals("needs_review", item.get("qcState").asText());
        assertEquals(List.of("duplicate: " + other), texts(item.get("reviewReasons")));
        assertEquals(0, count("SELECT count(*) FROM generation_job WHERE question_id = ?", version));
        keep(examId, slot).andExpect(status().isBadRequest());
        assertEquals(0, count("SELECT count(*) FROM bank_item WHERE source_question_id = ?", version));
    }

    // ---------- Solver ----------

    @Test
    void chiSuaLoiGiaiKhongGoiSolverVaGiuKetQuaSolver() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int base = questionOf(slot);
        int tfSlot = slotId(examId, "tf", 1);
        int tfBase = questionOf(tfSlot);
        int jobs = count("SELECT count(*) FROM generation_job");
        int calls = SOLVER.calls;

        JsonNode item = json(putQuestion(examId, slot, mcqBody(base).put("explanation", "Lời giải mới: số mũ của x."))
                .andExpect(status().isOk()));
        int version = item.get("questionId").asInt();
        JsonNode tfItem = json(putQuestion(examId, tfSlot, tfBody(tfBase).put("explanation", "Lời giải tf mới."))
                .andExpect(status().isOk()));
        int tfVersion = tfItem.get("questionId").asInt();
        processAll();

        assertEquals(jobs, count("SELECT count(*) FROM generation_job"), "không xếp job Solver");
        assertEquals(calls, SOLVER.calls, "không gọi LLM");
        assertEquals("qc_passed", statusOf(version));
        assertEquals("passed", item.get("qcState").asText());
        assertEquals("[]", item.get("reviewReasons").toString());
        assertEquals(1, count("SELECT count(*) FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'answer_check' AND passed = TRUE", version), "kết quả Solver được chép sang");
        assertEquals("qc_passed", statusOf(tfVersion));
        assertEquals(4, count("SELECT count(*) FROM answer_check_statement s JOIN quality_check_result r"
                + " ON r.id = s.qc_result_id WHERE r.question_id = ?", tfVersion));
        Map<String, Object> log = jdbc.queryForMap("SELECT edit_distance, changed_fields::text AS changed"
                + " FROM exam_slot_action WHERE slot_id = ?", slot);
        assertEquals("[\"explanation\"]", ((String) log.get("changed")).replace(" ", ""));
        assertEquals(0, log.get("edit_distance"), "edit_distance không tính lời giải");
    }

    @Test
    void solverDongYThiQcPassed() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int version = json(putQuestion(examId, slot, mcqBody(questionOf(slot))
                .put("questionText", "Đơn thức $2x^1$ có bậc là:")).andExpect(status().isOk())).get("questionId").asInt();
        int calls = SOLVER.calls;
        processAll();
        assertEquals(calls + 1, SOLVER.calls);
        assertEquals("qc_passed", statusOf(version));
        JsonNode item = viewItem(examId, slot);
        assertEquals("passed", item.get("qcState").asText());
        assertEquals("[]", item.get("reviewReasons").toString());
        assertEquals("done", jdbc.queryForObject("SELECT status FROM generation_job WHERE question_id = ?"
                + " AND job_type = 'answer_check'", String.class, version));
    }

    @Test
    void solverBatDongChiCanhBaoGiuDapAnGiaoVien() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int version = json(putQuestion(examId, slot, mcqBody(questionOf(slot)).put("correctOption", "C"))
                .andExpect(status().isOk())).get("questionId").asInt();
        processAll();
        assertEquals("generated", statusOf(version));
        assertEquals("C", question(version).get("correct_option"), "không ghi đè đáp án giáo viên");
        JsonNode item = viewItem(examId, slot);
        assertEquals("C", item.get("correctOption").asText());
        assertEquals("needs_review", item.get("qcState").asText());
        assertEquals(List.of("solver_inconsistent"), texts(item.get("reviewReasons")));
        assertTrue(item.get("needsReview").asBoolean());
        assertEquals("edited", json(keep(examId, slot).andExpect(status().isOk())).get("state").asText(),
                "giáo viên vẫn Giữ được sau khi thấy cờ");
    }

    @Test
    void solverLoiHetLuotThuThiFailedChoGiuKemCo() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int version = json(putQuestion(examId, slot, mcqBody(questionOf(slot))
                .put("questionText", "Đơn thức $2x^1$ có bậc là:")).andExpect(status().isOk())).get("questionId").asInt();
        assertEquals("QC_CHECKING", json(keep(examId, slot).andExpect(status().isConflict())).get("code").asText());

        SOLVER.fail = true;
        int calls = SOLVER.calls;
        processAll();
        assertEquals(calls + 3, SOLVER.calls, "thử tối đa max-attempts lần");
        Map<String, Object> job = jdbc.queryForMap("SELECT * FROM generation_job WHERE question_id = ?"
                + " AND job_type = 'answer_check'", version);
        assertEquals("failed", job.get("status"));
        assertEquals("generated", statusOf(version), "Solver lỗi không được coi là đạt");
        assertEquals(0, count("SELECT count(*) FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'answer_check'", version));
        JsonNode item = viewItem(examId, slot);
        assertEquals("needs_review", item.get("qcState").asText());
        assertEquals(List.of("solver_failed"), texts(item.get("reviewReasons")));

        JsonNode kept = json(keep(examId, slot).andExpect(status().isOk()));
        assertEquals("edited", kept.get("state").asText());
        assertEquals("in_bank", statusOf(version));
    }

    @Test
    void jobBiTraVeViQuaHanRoiFailThiKhongKetOChecking() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int version = json(putQuestion(examId, slot, mcqBody(questionOf(slot))
                .put("questionText", "Đơn thức $2x^1$ có bậc là:")).andExpect(status().isOk())).get("questionId").asInt();
        // Worker nhận job rồi chết giữa chừng: job kẹt ở running.
        jdbc.update("UPDATE generation_job SET status = 'running', attempt = 1, started_at = now() - interval '10 minutes'"
                + " WHERE question_id = ? AND job_type = 'answer_check'", version);
        assertEquals("checking", viewItem(examId, slot).get("qcState").asText());
        keep(examId, slot).andExpect(status().isConflict());

        assertTrue(staleJobRecovery.resetStaleJobs() >= 1);
        assertEquals("pending", jdbc.queryForObject("SELECT status FROM generation_job WHERE question_id = ?"
                + " AND job_type = 'answer_check'", String.class, version));
        SOLVER.fail = true;
        processAll();
        assertEquals("failed", jdbc.queryForObject("SELECT status FROM generation_job WHERE question_id = ?"
                + " AND job_type = 'answer_check'", String.class, version));
        JsonNode item = viewItem(examId, slot);
        assertEquals("needs_review", item.get("qcState").asText());
        assertTrue(texts(item.get("reviewReasons")).contains("solver_failed"));
        assertEquals("edited", json(keep(examId, slot).andExpect(status().isOk())).get("state").asText());
    }

    @Test
    void solverCuaBanCuVeMuonKhongLamBanMoiThanhDat() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int v1 = json(putQuestion(examId, slot, mcqBody(questionOf(slot))
                .put("questionText", "Đơn thức $2x^1$ có bậc là:")).andExpect(status().isOk())).get("questionId").asInt();
        int[] v2 = new int[1];
        // Trong lúc Solver của v1 đang chạy (job running), giáo viên sửa tiếp thành v2 với đáp án C.
        SOLVER.onCall = () -> v2[0] = json(putQuestion(examId, slot, mcqBody(v1).put("correctOption", "C"))
                .andExpect(status().isOk())).get("questionId").asInt();
        assertTrue(poller().processNextJob(true).processed());

        assertNotEquals(0, v2[0]);
        assertEquals("generated", statusOf(v1));
        assertEquals(0, count("SELECT count(*) FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'answer_check'", v1), "kết quả về muộn của v1 không được ghi");
        Map<String, Object> job1 = jdbc.queryForMap("SELECT * FROM generation_job WHERE question_id = ?"
                + " AND job_type = 'answer_check'", v1);
        assertEquals("done", job1.get("status"));
        assertTrue(String.valueOf(job1.get("last_error")).contains("superseded"), String.valueOf(job1.get("last_error")));
        assertEquals("generated", statusOf(v2[0]), "v2 không thành đạt nhờ kết quả của v1");
        assertEquals("checking", viewItem(examId, slot).get("qcState").asText());

        processAll();
        assertEquals("generated", statusOf(v2[0]));
        assertEquals(List.of("solver_inconsistent"), texts(viewItem(examId, slot).get("reviewReasons")));
    }

    @Test
    void suaLan2KhiSolverLan1ChuaXongThiHuyJobCu() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int base = questionOf(slot);
        int v1 = json(putQuestion(examId, slot, mcqBody(base)
                .put("questionText", "Đơn thức $2x^1$ có bậc là:")).andExpect(status().isOk())).get("questionId").asInt();
        int v2 = json(putQuestion(examId, slot, mcqBody(v1).put("questionText", "Bậc của đơn thức $2x^1$ bằng:"))
                .andExpect(status().isOk())).get("questionId").asInt();
        assertEquals(v1, question(v2).get("edited_from_question_id"));
        Map<String, Object> job1 = jdbc.queryForMap("SELECT * FROM generation_job WHERE question_id = ?", v1);
        assertEquals("failed", job1.get("status"));
        assertEquals("superseded", job1.get("last_error"));
        assertEquals(1, pendingAnswerCheckJobs(v2));

        int calls = SOLVER.calls;
        processAll();
        assertEquals(calls + 1, SOLVER.calls, "chỉ Solver của bản mới chạy");
        assertEquals("qc_passed", statusOf(v2));
        assertEquals(v2, questionOf(slot));
        assertEquals(base, viewItem(examId, slot).get("originalQuestionId").asInt(), "bản gốc là câu LLM, không phải v1");
    }

    // ---------- đồng thời ----------

    @Test
    void haiRequestDongThoiKhongGhiDeNhau() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int base = questionOf(slot);
        ObjectNode a = mcqBody(base).put("questionText", "Đơn thức $2x^1$ có bậc là:");
        ObjectNode b = mcqBody(base).put("questionText", "Bậc của đơn thức $2x^1$ bằng:");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> fa = pool.submit(() -> {
                start.await();
                return putQuestion(examId, slot, a).andReturn().getResponse().getStatus();
            });
            Future<Integer> fb = pool.submit(() -> {
                start.await();
                return putQuestion(examId, slot, b).andReturn().getResponse().getStatus();
            });
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(fa.get(30, TimeUnit.SECONDS), fb.get(30, TimeUnit.SECONDS)));
            statuses.sort(null);
            assertEquals(List.of(200, 409), statuses);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, count("SELECT count(*) FROM generated_question WHERE edited_from_question_id = ?", base));
        assertEquals(1, count("SELECT count(*) FROM exam_slot_action WHERE slot_id = ? AND action = 'edit'", slot));
    }

    @Test
    void sauKhiDoiDeKhacBaseThiXungDotPhienBan() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int base = questionOf(slot);
        putQuestion(examId, slot, mcqBody(base).put("questionText", "Đơn thức $2x^1$ có bậc là:"))
                .andExpect(status().isOk());
        List<Integer> before = footprint();
        assertEquals("VERSION_CONFLICT", json(putQuestion(examId, slot, mcqBody(base).put("correctOption", "C"))
                .andExpect(status().isConflict())).get("code").asText(), "base cũ (trang chưa tải lại)");
        assertEquals(before, footprint());
    }

    // ---------- trạng thái chỗ ----------

    @Test
    void choDaGiuDangSinhHoacLayTuNganHangThi409() throws Exception {
        int examId = readyExam();
        int kept = slotId(examId, "mcq", 1);
        int keptBase = questionOf(kept);
        keep(examId, kept).andExpect(status().isOk());
        List<Integer> before = footprint();
        assertEquals("SLOT_KEPT", json(putQuestion(examId, kept, mcqBody(keptBase).put("correctOption", "C"))
                .andExpect(status().isConflict())).get("code").asText());

        int generating = slotId(examId, "mcq", 2);
        int genBase = questionOf(generating);
        perform(post("/api/exams/" + examId + "/slots/" + generating + "/replace")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        before = footprint();
        assertEquals("SLOT_NOT_READY", json(putQuestion(examId, generating, mcqBody(genBase).put("correctOption", "C"))
                .andExpect(status().isConflict())).get("code").asText());
        assertEquals(before, footprint());
        assertEquals(1, count("SELECT count(*) FROM exam_slot_action WHERE slot_id = ?", kept));
    }

    @Test
    void choLayTuNganHangKhongSuaDuoc() throws Exception {
        int tpl = insert("INSERT INTO question_template (subject_id, source_question, is_active, topic)"
                + " VALUES (?, 'Mẫu', TRUE, 'Đa thức')", subject);
        int run = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, explanation, raw_llm_output, parse_success, status,"
                + " final_difficulty, llm_difficulty) VALUES (?, ?, 'Câu ngân hàng', '$1$', '$2$', '$3$', '$4$', 'B',"
                + " 'Lời giải', '{}', TRUE, 'in_bank', 'easy', 'easy')", tpl, run);
        int review = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production',"
                + " 'B', TRUE, TRUE, 'easy', 'approved_as_is', 'B')", q, teacher);
        insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b, option_c,"
                + " option_d, correct_option, difficulty) VALUES (?, ?, 'Câu ngân hàng', '$1$', '$2$', '$3$', '$4$', 'B',"
                + " 'easy')", q, review);
        int examId = json(perform(post("/api/exams/build").contentType(MediaType.APPLICATION_JSON)
                .content("{\"matrixId\": " + matrixId + "}")).andExpect(status().isOk())).get("examId").asInt();
        int bankSlot = count("SELECT id FROM exam_slot WHERE exam_id = ? AND bank_item_id IS NOT NULL", examId);
        List<Integer> before = footprint();
        assertEquals("SLOT_FROM_BANK", json(putQuestion(examId, bankSlot, mcqBody(q).put("correctOption", "C"))
                .andExpect(status().isConflict())).get("code").asText());
        assertEquals(before, footprint());
    }

    // ---------- Giữ sau khi sửa ----------

    @Test
    void giuSauKhiSuaChepBanSuaVaoNganHangVaTruyDuocBanGoc() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int base = questionOf(slot);
        int baseQc = count("SELECT count(*) FROM quality_check_result WHERE question_id = ?", base);
        String newText = "Đơn thức $2x^1$ có bậc là:";
        int version = json(putQuestion(examId, slot, mcqBody(base).put("questionText", newText)
                .put("explanation", "Lời giải đã sửa.")).andExpect(status().isOk())).get("questionId").asInt();
        processAll();

        JsonNode kept = json(keep(examId, slot).andExpect(status().isOk()));
        assertEquals("edited", kept.get("state").asText(), "Giữ bản đã sửa thì chỗ ở trạng thái edited (V7)");
        assertTrue(kept.get("edited").asBoolean());
        assertEquals(base, kept.get("originalQuestionId").asInt());
        int bankId = kept.get("bankItemId").asInt();
        Map<String, Object> item = jdbc.queryForMap("SELECT * FROM bank_item WHERE id = ?", bankId);
        assertEquals(version, item.get("source_question_id"));
        assertEquals(newText, item.get("question_text"));
        assertEquals("Lời giải đã sửa.", item.get("explanation"));
        assertEquals(base, question(version).get("edited_from_question_id"), "truy ngược được bản gốc LLM");
        assertEquals("qc_passed", statusOf(base), "bản gốc LLM giữ trạng thái và kết quả QC");
        assertEquals(baseQc, count("SELECT count(*) FROM quality_check_result WHERE question_id = ?", base));
        assertEquals("in_bank", statusOf(version));
        Map<String, Object> review = jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?", version);
        assertEquals("assisted", review.get("review_mode"));
        assertEquals(List.of("edit", "keep"), jdbc.queryForList("SELECT action FROM exam_slot_action WHERE slot_id = ?"
                + " ORDER BY id", String.class, slot));
        assertEquals(version, count("SELECT generated_question_id FROM exam_slot_action WHERE slot_id = ?"
                + " AND action = 'keep'", slot));
        assertEquals("SLOT_KEPT", json(putQuestion(examId, slot, mcqBody(version).put("correctOption", "C"))
                .andExpect(status().isConflict())).get("code").asText());
    }

    // ---------- quyền ----------

    @Test
    void giaoVienKhacSuaThi404VaKhongGhiGi() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int base = questionOf(slot);
        Object updatedAt = jdbc.queryForObject("SELECT updated_at FROM exam_slot WHERE id = ?", Object.class, slot);
        List<Integer> before = footprint();
        mockMvc.perform(put("/api/exams/" + examId + "/slots/" + slot + "/question")
                        .with(AuthTestSupport.teacher(otherTeacher)).contentType(MediaType.APPLICATION_JSON)
                        .content(mcqBody(base).put("correctOption", "C").toString()))
                .andExpect(status().isNotFound());
        assertEquals(before, footprint());
        assertEquals(base, questionOf(slot));
        assertEquals(updatedAt, jdbc.queryForObject("SELECT updated_at FROM exam_slot WHERE id = ?", Object.class, slot));
        putQuestion(examId, 999999, mcqBody(base)).andExpect(status().isNotFound());
    }

    @Test
    void workerChayJobSolverKhongCanDangNhap() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int version = json(putQuestion(examId, slot, mcqBody(questionOf(slot))
                .put("questionText", "Đơn thức $2x^1$ có bậc là:")).andExpect(status().isOk())).get("questionId").asInt();
        SecurityContextHolder.clearContext();
        processAll();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("qc_passed", statusOf(version));
        assertFalse(pendingAnswerCheckJobs(version) > 0);
    }

    @Test
    void processNextKhongChaySolverThiBoQuaJobAnswerCheck() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        int version = json(putQuestion(examId, slot, mcqBody(questionOf(slot))
                .put("questionText", "Đơn thức $2x^1$ có bậc là:")).andExpect(status().isOk())).get("questionId").asInt();
        jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'");
        assertFalse(poller().processNextJob(false).processed(), "runAnswerCheck=false không nhận job Solver");
        assertEquals(1, pendingAnswerCheckJobs(version));
        assertNotNull(jdbc.queryForObject("SELECT job_type FROM generation_job WHERE question_id = ?", String.class,
                version));
    }
}
