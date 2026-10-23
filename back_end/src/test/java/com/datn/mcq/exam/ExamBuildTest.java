package com.datn.mcq.exam;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.McqProductionSchema;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
import com.datn.mcq.llm.prompt.TfPromptDraftV0;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tạo đề theo ma trận một lần bấm (V7, phần C mục 2) qua HTTP thật trên datn_mcq_test: lấy câu ngân hàng khớp ô trước,
 * phần thiếu xếp hàng sinh câu bằng MatrixPromptDraftV0; xử lý job bằng LLM giả (không gọi mạng); đối soát khi xem
 * tiến độ: lệch Solver thử lại 1 lần rồi dùng câu kèm cờ (quyết định 2), câu trùng/lỗi định dạng thử tối đa 3 lần.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamBuildTest {

    /** Solver giả: trả theo schema; mcq luôn B, sa theo {@code saJudge}, tf T,F,T,F. */
    static final class FakeSolver extends LlmClient {
        String saJudge = "5";
        String tfJudges = "T,F,T,F";
        int calls;

        FakeSolver() {
            super("FAKE_KEY_FOR_TESTS", "fake-solver-model");
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            calls++;
            String body;
            if (TfPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON.equals(schema)) {
                String[] j = tfJudges.split(",");
                body = "{\"reasoning_a\": \"r\", \"judge_a\": \"" + j[0] + "\", \"reasoning_b\": \"r\", \"judge_b\": \""
                        + j[1] + "\", \"reasoning_c\": \"r\", \"judge_c\": \"" + j[2] + "\", \"reasoning_d\": \"r\","
                        + " \"judge_d\": \"" + j[3] + "\"}";
            } else if (SaPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON.equals(schema)) {
                body = "{\"reasoning\": \"r\", \"judge_answer\": \"" + saJudge + "\"}";
            } else {
                body = "{\"reasoning\": \"r\", \"judge_answer\": \"B\"}";
            }
            return new LlmCallResult(body, 10, 1.0, 0.0, modelName);
        }
    }

    /** LLM sinh câu giả: mỗi lần một câu khác số liệu; {@code sameMcq} thì mọi câu mcq giống hệt nhau. */
    static final class FakeGenerator extends LlmClient {
        final List<String> prompts = new ArrayList<>();
        final List<String> mcqPrompts = new ArrayList<>();
        boolean sameMcq;
        /** Số câu mcq đầu tiên trả về có "$" lẻ ở giữa câu (format_failed). */
        int brokenMcq;
        String[] tfLabels = {"T", "F", "T", "F"};
        int n;

        FakeGenerator() {
            super("FAKE_KEY_FOR_TESTS", "fake-generator-model");
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public LlmCallResult generateMcq(String prompt, String modelName, Strategy strategy, Double temperature) {
            throw new UnsupportedOperationException("MCQ production phải gọi kèm schema (ngày 23/10)");
        }

        private LlmCallResult mcq(String prompt, String modelName) {
            prompts.add(prompt);
            mcqPrompts.add(prompt);
            int k = sameMcq ? 1 : ++n;
            if (brokenMcq > 0) {
                brokenMcq--;
                return new LlmCallResult("{\"question_text\": \"Biết $x = " + k + " thì bậc của đơn thức $2x^3$ là:\","
                        + " \"option_a\": \"$1$\", \"option_b\": \"$3$\", \"option_c\": \"$0$\", \"option_d\": \"$2$\","
                        + " \"correct_option\": \"B\", \"explanation\": \"Số mũ của x.\"}", 10, 1.0, 0.0, modelName);
            }
            return new LlmCallResult("{\"question_text\": \"Bậc của đơn thức $" + (k + 1) + "x^" + k + "$ là:\","
                    + " \"option_a\": \"$" + (k + 1) + "$\", \"option_b\": \"$" + k + "$\", \"option_c\": \"$0$\","
                    + " \"option_d\": \"$" + (k + 2) + "$\", \"correct_option\": \"B\", \"explanation\": \"Số mũ của x.\"}",
                    10, 1.0, 0.0, modelName);
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            if (McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON.equals(schema)) {
                return mcq(prompt, modelName);
            }
            prompts.add(prompt);
            int k = ++n;
            if (SaPromptDraftV0.GENERATION_RESPONSE_SCHEMA_JSON.equals(schema)) {
                return new LlmCallResult("{\"question_text\": " + SimpleJson.quote("Tứ giác $ABCD$ có ba góc lần lượt là $"
                        + (80 + k) + "^\\circ$, $90^\\circ$, $100^\\circ$. Góc còn lại bằng bao nhiêu độ?")
                        + ", \"explanation\": \"Tổng bốn góc là 360 độ.\", \"answer\": \"5\"}", 10, 1.0, 0.0, modelName);
            }
            return new LlmCallResult("{\"question_text\": " + SimpleJson.quote("Cho biểu thức $P = (x + " + k + ")^2$.")
                    + ", \"statement_a\": \"Ý một số " + k + ".\", \"statement_b\": \"Ý hai số " + k + ".\","
                    + " \"statement_c\": \"Ý ba số " + k + ".\", \"statement_d\": \"Ý bốn số " + k + ".\","
                    + " \"explanation\": \"Lời giải.\", \"label_a\": \"" + tfLabels[0] + "\", \"label_b\": \""
                    + tfLabels[1] + "\", \"label_c\": \"" + tfLabels[2] + "\", \"label_d\": \"" + tfLabels[3] + "\"}",
                    10, 1.0, 0.0, modelName);
        }
    }

    private static final FakeSolver SOLVER = new FakeSolver();

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private FakeGenerator generator;
    private int subject;
    private int teacher;
    private int matrixId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "ExamBuildTest.setUp");
        SOLVER.saJudge = "5";
        SOLVER.tfJudges = "T,F,T,F";
        SOLVER.calls = 0;
        generator = new FakeGenerator();
        subject = insert("INSERT INTO subject (name) VALUES ('Toán')");
        teacher = insert("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.exam@test', 'teacher')");
        matrixId = json(postJson("/api/exam-matrices", "{\"name\": \"Đề thử ma trận\", \"subjectId\": " + subject
                + ", \"gradeLevel\": 8, \"cells\": ["
                + "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 2,"
                + " \"topic\": \"Đa thức\", \"content\": \"Đơn thức, đa thức\","
                + " \"learningOutcomes\": {\"easy\": \"Nhận biết được đơn thức, đa thức.\"}},"
                + "{\"part\": \"II\", \"questionType\": \"tf\", \"statementDifficulties\": [\"easy\", \"easy\","
                + " \"medium\", \"hard\"], \"questionCount\": 1, \"topic\": \"Hằng đẳng thức\","
                + " \"learningOutcomes\": {\"easy\": \"Nhận biết.\", \"medium\": \"Khai triển.\", \"hard\": \"Vận dụng.\"}},"
                + "{\"part\": \"III\", \"questionType\": \"sa\", \"difficulty\": \"medium\", \"questionCount\": 1,"
                + " \"topic\": \"Tứ giác\", \"content\": \"Tứ giác, hình thang cân\","
                + " \"learningOutcomes\": {\"medium\": \"Tính được góc trong tứ giác.\"}}]}")
                .andExpect(status().isOk())).get("id").asInt();
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Câu mcq trong ngân hàng, sinh từ một template có chủ đề {@code topic}. */
    private int bankMcq(String topic, String difficulty, String text) {
        int tpl = insert("INSERT INTO question_template (subject_id, source_question, is_active, topic)"
                + " VALUES (?, 'Mẫu', TRUE, ?)", subject, topic);
        int run = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, final_difficulty)"
                + " VALUES (?, ?, ?, '$1$', '$2$', '$3$', '$4$', 'B', '{}', TRUE, 'in_bank', ?)", tpl, run, text,
                difficulty);
        int review = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production',"
                + " 'B', TRUE, TRUE, ?, 'approved_as_is', 'B')", q, teacher, difficulty);
        return insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, difficulty) VALUES (?, ?, ?, '$1$', '$2$', '$3$', '$4$', 'B', ?)",
                q, review, text, difficulty);
    }

    private JsonNode build(String body) throws Exception {
        return json(postJson("/api/exams/build", body).andExpect(status().isOk()));
    }

    private JsonNode progress(int examId) throws Exception {
        return json(mockMvc.perform(get("/api/exams/" + examId + "/progress")).andExpect(status().isOk()));
    }

    /** Xử lý mọi job đến hạn (đặt lại next_run_at về quá khứ trước mỗi vòng). */
    private void processAll() {
        GenerationJobPoller poller = new GenerationJobPoller(jobProcessor, generator, qcOrchestrator,
                dedupScopeResolver, 3);
        for (int i = 0; i < 50; i++) {
            jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'");
            if (!poller.processNextJob(true).processed()) {
                return;
            }
        }
    }

    private int cellId(int position) {
        return jdbc.queryForObject("SELECT id FROM exam_matrix_cell WHERE matrix_id = ? AND position = ?",
                Integer.class, matrixId, position);
    }

    // ---------- tạo đề ----------

    @Test
    void layNganHangTruocRoiXepHangSinhPhanThieu() throws Exception {
        int match = bankMcq("Đa thức", "easy", "Đơn thức nào có bậc 2?");
        bankMcq("Đa thức", "medium", "Câu khác mức");
        bankMcq("Tứ giác", "easy", "Câu khác chủ đề");

        JsonNode built = build("{\"matrixId\": " + matrixId + "}");
        int examId = built.get("examId").asInt();
        assertEquals("Đề thử ma trận", built.get("title").asText(), "bỏ trống title thì lấy tên ma trận");
        assertEquals(4, built.get("slots").asInt());
        assertEquals(1, built.get("fromBank").asInt());
        assertEquals(3, built.get("jobsQueued").asInt());

        Map<String, Object> exam = jdbc.queryForMap("SELECT * FROM exam WHERE id = ?", examId);
        assertEquals("teacher", exam.get("mode"));
        assertEquals(matrixId, exam.get("matrix_id"));
        assertEquals("draft", exam.get("status"));
        assertEquals(built.get("examCode").asText(), exam.get("exam_code"));
        assertEquals(List.of(match), jdbc.queryForList("SELECT bank_item_id FROM exam_slot WHERE exam_id = ?"
                + " AND bank_item_id IS NOT NULL", Integer.class, examId));
        List<Map<String, Object>> runs = jdbc.queryForList("SELECT * FROM experiment_run WHERE prompt_version LIKE"
                + " 'matrix-%' ORDER BY id");
        assertEquals(List.of("matrix-mcq-v1-draft", "matrix-tf-v1-draft", "matrix-sa-v1-draft"),
                runs.stream().map(r -> r.get("prompt_version")).toList());
        for (Map<String, Object> run : runs) {
            assertEquals("production", run.get("purpose"));
            assertEquals("zero-shot", run.get("strategy"));
            assertNotNull(run.get("hyperparams"));
        }
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM generation_job WHERE exam_slot_id IS NOT NULL",
                Integer.class));

        JsonNode before = progress(examId);
        assertEquals("generating", before.get("status").asText());
        assertEquals(1, before.get("ready").asInt());
        assertEquals(3, before.get("generating").asInt());
        assertEquals(1, before.get("cells").get(0).get("fromBank").asInt());

        processAll();
        JsonNode after = progress(examId);
        assertEquals("ready", after.get("status").asText());
        assertEquals(4, after.get("ready").asInt());
        assertEquals(0, after.get("failed").asInt());
        JsonNode mcqSlots = json(mockMvc.perform(get("/api/exams/" + examId + "/cells/" + cellId(1)))
                .andExpect(status().isOk()));
        assertEquals("bank", mcqSlots.get(0).get("source").asText());
        assertEquals("generated", mcqSlots.get(1).get("source").asText());
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, mcqSlots.get(1).get("questionId").asInt()));

        String mcqPrompt = generator.mcqPrompts.get(0);
        assertTrue(mcqPrompt.contains("Chủ đề: Đa thức") && mcqPrompt.contains("Nội dung: Đơn thức, đa thức")
                && mcqPrompt.contains("Nhận biết được đơn thức, đa thức."), mcqPrompt);
        assertFalse(mcqPrompt.contains("Câu hỏi nguồn"));
        String saPrompt = generator.prompts.stream().filter(p -> p.contains("TRẢ LỜI NGẮN")).findFirst().orElseThrow();
        assertTrue(saPrompt.contains("KHÔNG cần hình vẽ"), "chủ đề Tứ giác dặn không cần hình vẽ");
        String tfPrompt = generator.prompts.stream().filter(p -> p.contains("ĐÚNG/SAI")).findFirst().orElseThrow();
        assertTrue(tfPrompt.contains("ý a: Biết; ý b: Biết; ý c: Hiểu; ý d: Vận dụng"));
    }

    @Test
    void uuTienCauNganHangChuaNamTrongDeNao() throws Exception {
        int first = bankMcq("Đa thức", "easy", "Câu ngân hàng một");
        int second = bankMcq("Đa thức", "easy", "Câu ngân hàng hai");
        int third = bankMcq("Đa thức", "easy", "Câu ngân hàng ba");
        int e1 = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        int e2 = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        assertEquals(List.of(first, second), jdbc.queryForList("SELECT bank_item_id FROM exam_slot WHERE exam_id = ?"
                + " AND bank_item_id IS NOT NULL ORDER BY slot_no", Integer.class, e1));
        assertEquals(List.of(third, first), jdbc.queryForList("SELECT bank_item_id FROM exam_slot WHERE exam_id = ?"
                + " AND bank_item_id IS NOT NULL ORDER BY slot_no", Integer.class, e2),
                "câu chưa nằm trong đề nào trước; hết thì mới dùng lại câu đã nằm trong đề khác");
    }

    @Test
    void lechSolverThuLaiMotLanRoiDungCauKemCo() throws Exception {
        SOLVER.saJudge = "7";
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        processAll();
        progress(examId);  // đối soát: câu sa lệch Solver lần đầu thì xếp hàng sinh lại
        Map<String, Object> slot = jdbc.queryForMap("SELECT * FROM exam_slot WHERE exam_id = ? AND question_type = 'sa'",
                examId);
        assertEquals("generating", slot.get("state"));
        assertEquals(2, slot.get("attempts"));
        assertEquals(1, slot.get("solver_retries"));
        assertNotNull(slot.get("fallback_question_id"));

        processAll();
        JsonNode done = progress(examId);
        slot = jdbc.queryForMap("SELECT * FROM exam_slot WHERE exam_id = ? AND question_type = 'sa'", examId);
        assertEquals("ready", slot.get("state"), "vẫn lệch sau 1 lần thử lại thì dùng câu đó (kèm cờ)");
        assertEquals("generated", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, slot.get("generated_question_id")));
        assertEquals("ready", done.get("status").asText());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM generation_job WHERE exam_slot_id = ?",
                Integer.class, slot.get("id")));
    }

    @Test
    void cauTrungThuToiDa3LanRoiBaoHong() throws Exception {
        generator.sameMcq = true;
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        for (int round = 0; round < 4; round++) {
            processAll();
            progress(examId);
        }
        JsonNode p = progress(examId);
        assertEquals("partial", p.get("status").asText());
        assertEquals(1, p.get("failed").asInt());
        assertEquals(1, p.get("cells").get(0).get("failed").asInt());
        Map<String, Object> failed = jdbc.queryForMap("SELECT * FROM exam_slot WHERE exam_id = ? AND state = 'failed'",
                examId);
        assertEquals(3, failed.get("attempts"));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM generation_job WHERE exam_slot_id = ?",
                Integer.class, failed.get("id")));
    }

    @Test
    void duLieuSaiTra404() throws Exception {
        postJson("/api/exams/build", "{\"matrixId\": 999999}").andExpect(status().isNotFound());
        postJson("/api/exams/build", "{\"matrixId\": " + matrixId + ", \"teacherId\": 999999}")
                .andExpect(status().isNotFound());
        postJson("/api/exams/build", "{}").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/exams/999999/progress")).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM exam", Integer.class));
    }

    // ---------- xem đề ở chế độ giáo viên (phần C mục 3) ----------

    @Test
    void xemDeCoDapAnLoiGiaiVaCoCanXemKy() throws Exception {
        int bank = bankMcq("Đa thức", "easy", "Đơn thức nào có bậc 2?");
        SOLVER.saJudge = "7";
        generator.tfLabels = new String[]{"T", "T", "T", "T"};
        SOLVER.tfJudges = "T,T,T,T";
        int examId = build("{\"matrixId\": " + matrixId + ", \"title\": \"Đề giữa kì\"}").get("examId").asInt();
        for (int round = 0; round < 3; round++) {
            processAll();
            progress(examId);
        }
        JsonNode view = json(mockMvc.perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals("Đề giữa kì", view.get("title").asText());
        assertEquals("teacher", view.get("mode").asText());
        assertEquals("ready", view.get("buildStatus").asText());
        assertEquals(List.of("I", "II", "III"), List.of(view.get("parts").get(0).get("part").asText(),
                view.get("parts").get(1).get("part").asText(), view.get("parts").get(2).get("part").asText()));
        JsonNode part1 = view.get("parts").get(0);
        assertEquals(2, part1.get("required").asInt());
        assertEquals(2, part1.get("ready").asInt());

        JsonNode fromBank = part1.get("items").get(0);
        assertEquals("bank", fromBank.get("source").asText());
        assertEquals(bank, fromBank.get("bankItemId").asInt());
        assertEquals("Đơn thức nào có bậc 2?", fromBank.get("questionText").asText());
        assertEquals("B", fromBank.get("correctOption").asText());
        assertFalse(fromBank.get("needsReview").asBoolean());

        JsonNode generatedMcq = part1.get("items").get(1);
        assertEquals("generated", generatedMcq.get("source").asText());
        assertEquals("B", generatedMcq.get("correctOption").asText(), "chế độ giáo viên có đáp án");
        assertEquals("Số mũ của x.", generatedMcq.get("explanation").asText(), "và lời giải");
        assertEquals("easy", generatedMcq.get("difficulty").asText());
        assertFalse(generatedMcq.get("needsReview").asBoolean());
        assertEquals("[]", generatedMcq.get("reviewReasons").toString());

        JsonNode tf = view.get("parts").get(1).get("items").get(0);
        assertEquals(4, tf.get("statements").size());
        assertEquals("T", tf.get("statements").get(0).get("label").asText());
        assertEquals("hard", tf.get("statements").get(3).get("difficulty").asText());
        assertTrue(tf.get("needsReview").asBoolean());
        assertEquals("[\"warning: label: 4 ý cùng nhãn T\"]", tf.get("reviewReasons").toString());

        JsonNode sa = view.get("parts").get(2).get("items").get(0);
        assertEquals("5", sa.get("saAnswer").asText());
        assertTrue(sa.get("needsReview").asBoolean());
        assertEquals("[\"solver_inconsistent\"]", sa.get("reviewReasons").toString());
        assertEquals(2, view.get("needsReviewCount").asInt());
    }

    @Test
    void xemDeDangSinhChiCoDinhDanh() throws Exception {
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        JsonNode view = json(mockMvc.perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals("generating", view.get("buildStatus").asText());
        JsonNode item = view.get("parts").get(0).get("items").get(0);
        assertEquals("generating", item.get("state").asText());
        assertFalse(item.has("questionText"));
        assertFalse(item.has("correctOption"));
        mockMvc.perform(get("/api/exams/999999")).andExpect(status().isNotFound());
    }

    // ---------- Giữ / Đổi câu khác (phần C mục 4) ----------

    private int readyExam() throws Exception {
        SOLVER.saJudge = "7";
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        for (int round = 0; round < 3; round++) {
            processAll();
            progress(examId);
        }
        return examId;
    }

    private int slotId(int examId, String type, int slotNo) {
        return jdbc.queryForObject("SELECT id FROM exam_slot WHERE exam_id = ? AND question_type = ? AND slot_no = ?",
                Integer.class, examId, type, slotNo);
    }

    private ResultActions action(int examId, int slotId, String action) throws Exception {
        return postJson("/api/exams/" + examId + "/slots/" + slotId + "/" + action, "{\"teacherId\": " + teacher + "}");
    }

    @Test
    void giuCauSinhRaTaoReviewAssistedVaNganHang() throws Exception {
        int examId = readyExam();
        int mcqSlot = slotId(examId, "mcq", 1);
        int questionId = jdbc.queryForObject("SELECT generated_question_id FROM exam_slot WHERE id = ?", Integer.class,
                mcqSlot);
        JsonNode kept = json(action(examId, mcqSlot, "keep").andExpect(status().isOk()));
        assertEquals("kept", kept.get("state").asText());
        int bankId = kept.get("bankItemId").asInt();
        assertEquals("B", kept.get("correctOption").asText());
        assertFalse(kept.get("needsReview").asBoolean(), "câu đã Giữ thì không còn cờ");

        Map<String, Object> review = jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?", questionId);
        assertEquals("assisted", review.get("review_mode"));
        assertEquals("production", review.get("review_purpose"));
        assertEquals("approved_as_is", review.get("decision"));
        assertEquals("B", review.get("reviewer_answer"));
        assertEquals("B", review.get("final_option"));
        assertEquals(teacher, review.get("reviewer_id"));
        Map<String, Object> item = jdbc.queryForMap("SELECT * FROM bank_item WHERE id = ?", bankId);
        assertEquals(questionId, item.get("source_question_id"));
        assertEquals(review.get("id"), item.get("review_id"));
        assertEquals("in_bank", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?", String.class,
                questionId));
        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM exam_slot_action WHERE slot_id = ?", mcqSlot);
        assertEquals("keep", log.get("action"));
        assertEquals(questionId, log.get("generated_question_id"));
        assertEquals(bankId, log.get("bank_item_id"));
        assertEquals(teacher, log.get("actor_id"));

        action(examId, mcqSlot, "keep").andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM exam_slot_action WHERE slot_id = ?", Integer.class,
                mcqSlot));
    }

    @Test
    void giuCauLechSolverVaCauTf() throws Exception {
        int examId = readyExam();
        JsonNode sa = json(action(examId, slotId(examId, "sa", 1), "keep").andExpect(status().isOk()));
        assertEquals("kept", sa.get("state").asText());
        assertEquals("5", jdbc.queryForObject("SELECT sa_answer FROM bank_item WHERE id = ?", String.class,
                sa.get("bankItemId").asInt()), "câu lệch Solver được Giữ sau khi giáo viên thấy cờ");
        Map<String, Object> saReview = jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_type = 'sa'");
        assertEquals("assisted", saReview.get("review_mode"));
        assertEquals("number", saReview.get("reviewer_sa_kind"));
        assertEquals("5", saReview.get("final_sa_answer"));

        JsonNode tf = json(action(examId, slotId(examId, "tf", 1), "keep").andExpect(status().isOk()));
        assertEquals(List.of("T", "F", "T", "F"), jdbc.queryForList("SELECT label FROM bank_item_statement"
                + " WHERE bank_item_id = ? ORDER BY position", String.class, tf.get("bankItemId").asInt()));
        assertEquals(List.of("easy", "easy", "medium", "hard"), jdbc.queryForList("SELECT difficulty FROM"
                + " bank_item_statement WHERE bank_item_id = ? ORDER BY position", String.class,
                tf.get("bankItemId").asInt()));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM teacher_review_statement", Integer.class));
    }

    @Test
    void giuCauLayTuNganHangKhongTaoReviewMoi() throws Exception {
        int bank = bankMcq("Đa thức", "easy", "Đơn thức nào có bậc 2?");
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        int reviews = jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class);
        JsonNode kept = json(action(examId, slotId(examId, "mcq", 1), "keep").andExpect(status().isOk()));
        assertEquals("kept", kept.get("state").asText());
        assertEquals(bank, kept.get("bankItemId").asInt());
        assertEquals(reviews, jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class));
        assertEquals("keep", jdbc.queryForObject("SELECT action FROM exam_slot_action", String.class));
    }

    @Test
    void doiCauKhacSinhLaiDungO() throws Exception {
        int examId = readyExam();
        int mcqSlot = slotId(examId, "mcq", 2);
        int oldQuestion = jdbc.queryForObject("SELECT generated_question_id FROM exam_slot WHERE id = ?", Integer.class,
                mcqSlot);
        String oldText = jdbc.queryForObject("SELECT question_text FROM generated_question WHERE id = ?", String.class,
                oldQuestion);
        JsonNode replaced = json(action(examId, mcqSlot, "replace").andExpect(status().isOk()));
        assertEquals("generating", replaced.get("state").asText());
        assertFalse(replaced.has("questionText"));
        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM exam_slot_action WHERE slot_id = ?", mcqSlot);
        assertEquals("replace", log.get("action"));
        assertEquals(oldQuestion, log.get("generated_question_id"));
        action(examId, mcqSlot, "replace").andExpect(status().isConflict());

        int prompts = generator.prompts.size();
        processAll();
        assertTrue(generator.prompts.get(prompts).contains(oldText), "prompt sinh lại liệt kê câu cũ của ô");
        JsonNode view = json(mockMvc.perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        JsonNode item = view.get("parts").get(0).get("items").get(1);
        assertEquals("ready", item.get("state").asText());
        assertNotEquals(oldQuestion, item.get("questionId").asInt());
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, oldQuestion), "câu cũ giữ nguyên trạng thái để truy vết");
    }

    @Test
    void thaoTacSaiChoTra404() throws Exception {
        int examId = readyExam();
        int other = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        action(other, slotId(examId, "mcq", 1), "keep").andExpect(status().isNotFound());
        action(examId, 999999, "replace").andExpect(status().isNotFound());
    }

    // ---------- ngày 23/10: đa dạng trong ô, lý do lỗi format, sinh lại chỗ hỏng ----------

    @Test
    void moiChoTrongOBietCauThuKVaCauDaSinhCuaO() throws Exception {
        build("{\"matrixId\": " + matrixId + "}");
        processAll();
        assertEquals(2, generator.mcqPrompts.size());
        String first = generator.mcqPrompts.get(0);
        String second = generator.mcqPrompts.get(1);
        assertTrue(first.contains("Đây là câu thứ 1/2 của ô này"), first);
        assertTrue(second.contains("Đây là câu thứ 2/2 của ô này"), second);
        String firstQuestion = jdbc.queryForObject("SELECT question_text FROM generated_question WHERE question_type ="
                + " 'mcq' ORDER BY id LIMIT 1", String.class);
        assertTrue(second.contains("1. " + firstQuestion), "prompt dựng lúc worker nhận job, có câu của chỗ trước");
        assertFalse(first.contains(com.datn.mcq.llm.prompt.PreviousQuestion.HEADER), "chỗ đầu chưa có câu nào");
        String saPrompt = generator.prompts.stream().filter(p -> p.contains("TRẢ LỜI NGẮN")).findFirst().orElseThrow();
        assertFalse(saPrompt.contains("Đây là câu thứ"), "ô chỉ 1 câu thì không có dòng k/n");
    }

    @Test
    void sinhLaiSauFormatFailedCoLyDoTrongPromptVaCotErrors() throws Exception {
        generator.brokenMcq = 1;
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        processAll();
        int broken = jdbc.queryForObject("SELECT id FROM generated_question WHERE status = 'format_failed'",
                Integer.class);
        String errors = jdbc.queryForObject("SELECT errors::text FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'format'", String.class, broken);
        assertNotNull(errors, "lý do format_failed được lưu ở cột errors (V8)");
        assertTrue(errors.contains("question_text"), errors);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM quality_check_result WHERE passed = TRUE"
                + " AND errors IS NOT NULL", Integer.class), "dòng passed thì errors NULL");

        int before = generator.mcqPrompts.size();
        progress(examId);
        processAll();
        String retry = generator.mcqPrompts.get(before);
        int at = retry.indexOf("Lần sinh trước bị loại vì: ");
        assertTrue(at >= 0, retry);
        String line = retry.substring(at, retry.indexOf('\n', at));
        assertTrue(line.endsWith("Tránh lặp lại lỗi này."), line);
        assertTrue(line.length() <= 300, "tối đa 300 ký tự: " + line.length());
        assertTrue(line.contains("question_text"), line);
    }

    @Test
    void doiCauKhacDungDuocChoChoHong() throws Exception {
        generator.sameMcq = true;
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        for (int round = 0; round < 4; round++) {
            processAll();
            progress(examId);
        }
        int failed = jdbc.queryForObject("SELECT id FROM exam_slot WHERE exam_id = ? AND state = 'failed'",
                Integer.class, examId);
        generator.sameMcq = false;
        JsonNode replaced = json(action(examId, failed, "replace").andExpect(status().isOk()));
        assertEquals("generating", replaced.get("state").asText());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM exam_slot WHERE id = ?", Integer.class, failed));
        processAll();
        assertEquals("ready", progress(examId).get("status").asText());
    }
}
