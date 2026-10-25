package com.datn.mcq.generation;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.repository.QuestionTemplateRepository;
import com.datn.mcq.catalog.repository.SubjectRepository;
import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.SourceType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.service.QcRerunService;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.llm.prompt.TfPromptDraftV0;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.datn.mcq.qc.service.AnswerCheckRecorder;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Luồng Đúng/Sai trọn vẹn qua HTTP thật trên datn_mcq_test (GĐ3): generate-sync với rawLlmOutputOverride, format,
 * dedup, Solver giả (không gọi mạng); job bất đồng bộ; chạy lại QC.
 */
@SpringBootTest(properties = "app.experiment.enabled=true")
@Import(TestSafetyConfiguration.class)
class TrueFalseFlowTest {

    private static final String STEM = "Cho biểu thức $P = \\frac{x^2 - 1}{x^2 - x}$.";
    private static final List<String> STATEMENTS = List.of(
            "Điều kiện xác định của $P$ là $x \\neq 0$ và $x \\neq 1$.",
            "Với điều kiện xác định, $P = \\frac{x+1}{x}$.",
            "Tại $x = 2$ thì $P = 2$.",
            "Có đúng hai giá trị nguyên của $x$ để $P$ nhận giá trị nguyên.");
    private static final String SECRET_EXPLANATION = "LỜI GIẢI BÍ MẬT TỪNG Ý";
    private static final String LEVELS = "[\"easy\", \"easy\", \"medium\", \"hard\"]";

    static final class FakeSolverClient extends LlmClient {
        String response;
        int calls;
        final List<String> prompts = new ArrayList<>();
        final List<String> schemas = new ArrayList<>();

        FakeSolverClient() {
            super("FAKE_KEY_FOR_TESTS", "fake-solver-model");
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public LlmCallResult generateMcq(String prompt, String modelName, Strategy strategy, Double temperature) {
            throw new UnsupportedOperationException("Không sinh câu MCQ trong test này");
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            calls++;
            prompts.add(prompt);
            schemas.add(schema);
            return new LlmCallResult(response, 10, 1.0, 0.0, modelName);
        }
    }

    private static final FakeSolverClient SOLVER = new FakeSolverClient();

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
    private SubjectRepository subjectRepository;
    @Autowired
    private QuestionTemplateRepository templateRepository;
    @Autowired
    private GenerationJobProcessor jobProcessor;
    @Autowired
    private QcOrchestrator qcOrchestrator;
    @Autowired
    private DedupScopeResolver dedupScopeResolver;
    @Autowired
    private QcRerunService qcRerunService;
    @Autowired
    private AnswerCheckRecorder answerCheckRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Subject subject;
    private QuestionTemplate template;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "TrueFalseFlowTest.setUp");
        SOLVER.calls = 0;
        SOLVER.prompts.clear();
        SOLVER.schemas.clear();
        SOLVER.response = solverSays("T", "T", "F", "F");
        subject = subjectRepository.save(new Subject("Toán Đúng Sai"));
        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(subject.getId());
        tpl.setGradeLevel(8);
        tpl.setTopic("Phân thức đại số");
        tpl.setSourceType(SourceType.TEXT);
        tpl.setSourceQuestion("Rút gọn phân thức.");
        tpl.setIsActive(true);
        template = templateRepository.save(tpl);
    }

    static String solverSays(String a, String b, String c, String d) {
        return "{\"reasoning_a\": \"ra\", \"judge_a\": \"" + a + "\", \"reasoning_b\": \"rb\", \"judge_b\": \"" + b
                + "\", \"reasoning_c\": \"rc\", \"judge_c\": \"" + c + "\", \"reasoning_d\": \"rd\", \"judge_d\": \""
                + d + "\"}";
    }

    static String tfJson(String stem, List<String> s, List<String> labels) {
        return "{\"question_text\": " + SimpleJson.quote(stem)
                + ", \"statement_a\": " + SimpleJson.quote(s.get(0)) + ", \"statement_b\": " + SimpleJson.quote(s.get(1))
                + ", \"statement_c\": " + SimpleJson.quote(s.get(2)) + ", \"statement_d\": " + SimpleJson.quote(s.get(3))
                + ", \"explanation\": " + SimpleJson.quote(SECRET_EXPLANATION)
                + ", \"label_a\": \"" + labels.get(0) + "\", \"label_b\": \"" + labels.get(1)
                + "\", \"label_c\": \"" + labels.get(2) + "\", \"label_d\": \"" + labels.get(3) + "\"}";
    }

    private ResultActions postGenerate(String fields) throws Exception {
        return mockMvc.perform(post("/api/generation/generate-sync").contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\": " + template.getId() + ", " + fields + "}"));
    }

    private JsonNode generateTf(String llmJson, boolean runAnswerCheck) throws Exception {
        return objectMapper.readTree(body(postGenerate("\"questionType\": \"tf\", \"targetStatementDifficulties\": "
                + LEVELS + ", \"runAnswerCheck\": " + runAnswerCheck + ", \"rawLlmOutputOverride\": "
                + SimpleJson.quote(llmJson)).andExpect(status().isOk())));
    }

    private static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    // ---------- trọn luồng ----------

    @Test
    void tfDiQuaQcVoiBonYDungThuTu() throws Exception {
        JsonNode resp = generateTf(tfJson(STEM, STATEMENTS, List.of("T", "T", "F", "F")), true);
        JsonNode question = resp.get("question");
        JsonNode qc = resp.get("qcReport");
        assertEquals("tf", question.get("questionType").asText());
        assertEquals("qc_passed", question.get("status").asText());
        assertEquals(4, question.get("statements").size());
        assertEquals("a", question.get("statements").get(0).get("key").asText());
        assertEquals(STATEMENTS.get(3), question.get("statements").get(3).get("text").asText());
        assertEquals(Set.of("key", "text"), Set.copyOf(fieldNames(question.get("statements").get(0))));
        assertEquals("CONSISTENT", qc.get("answerCheckStatus").asText());
        assertEquals("[{\"key\":\"a\",\"judgeLabel\":\"T\"},{\"key\":\"b\",\"judgeLabel\":\"T\"},"
                + "{\"key\":\"c\",\"judgeLabel\":\"F\"},{\"key\":\"d\",\"judgeLabel\":\"F\"}]",
                qc.get("tfJudgements").toString());
        assertFalse(qc.has("saJudgeKind"));
        int id = question.get("id").asInt();

        assertEquals(TfPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON, SOLVER.schemas.get(0));
        assertTrue(SOLVER.prompts.get(0).contains("d) " + STATEMENTS.get(3)));
        assertFalse(SOLVER.prompts.get(0).contains("BÍ MẬT"));

        Map<String, Object> gq = jdbc.queryForMap("SELECT * FROM generated_question WHERE id = ?", id);
        assertEquals("tf", gq.get("question_type"));
        assertNull(gq.get("llm_difficulty"));
        assertNull(gq.get("option_a"));
        assertNull(gq.get("correct_option"));
        List<Map<String, Object>> statements = rows("SELECT * FROM generated_question_statement WHERE question_id = ?"
                + " ORDER BY position", id);
        assertEquals(List.of("a", "b", "c", "d"), statements.stream().map(r -> r.get("position")).toList());
        assertEquals(List.of("T", "T", "F", "F"), statements.stream().map(r -> r.get("llm_label")).toList());
        assertEquals(List.of("easy", "easy", "medium", "hard"),
                statements.stream().map(r -> r.get("llm_difficulty")).toList());
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM experiment_run WHERE id = ?", gq.get("run_id"));
        assertEquals("tf", run.get("question_type"));
        assertEquals("production", run.get("purpose"));
        assertEquals("zero-shot", run.get("strategy"));
        assertEquals("tf-v2-draft", run.get("prompt_version"));
        assertEquals(List.of("tf", "tf", "tf"), jdbc.queryForList("SELECT question_type FROM quality_check_result"
                + " WHERE question_id = ? ORDER BY check_type", String.class, id));
        Map<String, Object> answerCheck = jdbc.queryForMap("SELECT * FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'answer_check'", id);
        assertNull(answerCheck.get("judge_answer"));
        assertEquals(List.of("T", "T", "F", "F"), jdbc.queryForList("SELECT judge_label FROM answer_check_statement"
                + " WHERE qc_result_id = ? ORDER BY position", String.class, answerCheck.get("id")));

        // Chưa có thao tác của giáo viên nên chưa có review hay ngân hàng (Giữ vào ngân hàng: ExamBuildTest).
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM bank_item", Integer.class));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    // ---------- Solver ----------

    @Test
    void undecidableLaInconsistentVanLuuNhanTungY() throws Exception {
        SOLVER.response = solverSays("T", "T", "F", "undecidable");
        JsonNode resp = generateTf(tfJson(STEM, STATEMENTS, List.of("T", "T", "F", "F")), true);
        assertEquals("INCONSISTENT", resp.get("qcReport").get("answerCheckStatus").asText());
        assertEquals("generated", resp.get("question").get("status").asText());
        int id = resp.get("question").get("id").asInt();
        assertEquals(List.of("T", "T", "F", "undecidable"), jdbc.queryForList("SELECT s.judge_label FROM"
                + " answer_check_statement s JOIN quality_check_result q ON q.id = s.qc_result_id"
                + " WHERE q.question_id = ? ORDER BY s.position", String.class, id));
    }

    @Test
    void solverThieuYLaErrorKhongGhiDong() throws Exception {
        SOLVER.response = "{\"judge_a\": \"T\", \"judge_b\": \"T\", \"judge_d\": \"F\"}";
        JsonNode resp = generateTf(tfJson(STEM, STATEMENTS, List.of("T", "T", "F", "F")), true);
        assertEquals("ERROR", resp.get("qcReport").get("answerCheckStatus").asText());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM quality_check_result WHERE check_type ="
                + " 'answer_check'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM answer_check_statement", Integer.class));
    }

    @Test
    void ghiKetQuaSolverTfLaMotTransaction() throws Exception {
        JsonNode resp = generateTf(tfJson(STEM, STATEMENTS, List.of("T", "T", "F", "F")), false);
        int id = resp.get("question").get("id").asInt();
        // Nhãn thứ 4 null vi phạm NOT NULL khi ghi dòng con: cả dòng answer_check cũng phải rollback.
        assertThrows(RuntimeException.class, () -> answerCheckRecorder.recordTrueFalse(id, true, "m",
                Arrays.asList(TruthChoice.TRUE, TruthChoice.TRUE, TruthChoice.FALSE, null)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'answer_check'", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM answer_check_statement", Integer.class));
    }

    // ---------- format ----------

    @Test
    void giaiMaLoiKhongLuuYNao() throws Exception {
        JsonNode resp = generateTf("{\"question_text\": \"S\", \"label_a\": \"T\", \"label_a\": \"F\"}", true);
        assertEquals("format_failed", resp.get("question").get("status").asText());
        assertFalse(resp.get("question").has("statements"), "parse lỗi thì không có ý nào");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM generated_question_statement", Integer.class));
        assertEquals(0, SOLVER.calls);
        // V8 (ngày 23/10): lỗi giải mã JSON được lưu ở cột errors của dòng format.
        String errors = jdbc.queryForObject("SELECT errors::text FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'format'", String.class, resp.get("question").get("id").asInt());
        assertTrue(errors != null && errors.contains("Không giải mã được JSON hợp lệ") && errors.contains("label_a"),
                String.valueOf(errors));
    }

    @Test
    void yTrungNhauLaFormatFailedNhanGiongNhauChiCanhBao() throws Exception {
        JsonNode dup = generateTf(tfJson(STEM, List.of("Ý một", "Ý hai", "Ý một", "Ý bốn"),
                List.of("T", "T", "F", "F")), true);
        assertEquals("format_failed", dup.get("question").get("status").asText());
        assertTrue(dup.get("qcReport").get("formatErrors").toString().contains("trùng lặp"));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM generated_question_statement", Integer.class),
                "giải mã được thì vẫn lưu 4 ý để truy vết");

        SOLVER.response = solverSays("T", "T", "T", "T");
        JsonNode same = generateTf(tfJson("Cho $Q = 1$.", STATEMENTS, List.of("T", "T", "T", "T")), true);
        assertEquals("qc_passed", same.get("question").get("status").asText());
        assertEquals("[\"label: 4 ý cùng nhãn T\"]", same.get("qcReport").get("answerWarnings").toString());
    }

    @Test
    void tienToNhanODauYDuocBoTruocKhiLuuVaCanhBao() throws Exception {
        List<String> prefixed = List.of("a) " + STATEMENTS.get(0), "(B) " + STATEMENTS.get(1),
                "c. " + STATEMENTS.get(2), "a: " + STATEMENTS.get(3));
        JsonNode resp = generateTf(tfJson(STEM, prefixed, List.of("T", "T", "F", "F")), true);
        assertEquals("qc_passed", resp.get("question").get("status").asText());
        assertEquals(STATEMENTS.get(0), resp.get("question").get("statements").get(0).get("text").asText());
        assertEquals("[\"statement_a: đã bỏ tiền tố \\\"a)\\\" ở đầu ý\","
                + "\"statement_b: đã bỏ tiền tố \\\"(B)\\\" ở đầu ý\","
                + "\"statement_c: đã bỏ tiền tố \\\"c.\\\" ở đầu ý\"]",
                resp.get("qcReport").get("answerWarnings").toString());
        List<Object> stored = rows("SELECT statement_text FROM generated_question_statement WHERE question_id = ?"
                + " ORDER BY position", resp.get("question").get("id").asInt()).stream()
                .map(r -> r.get("statement_text")).toList();
        assertEquals(List.of(STATEMENTS.get(0), STATEMENTS.get(1), STATEMENTS.get(2), "a: " + STATEMENTS.get(3)),
                stored, "ý d mang chữ a không khớp vị trí nên giữ nguyên");
        assertTrue(SOLVER.prompts.get(0).contains("a) " + STATEMENTS.get(0)));
        assertFalse(SOLVER.prompts.get(0).contains("a) a) "));
    }

    // ---------- dedup ----------

    @Test
    void daoThuTuYLaTrungVaKhongSoVoiDangKhac() throws Exception {
        int mcqRun = jdbc.queryForObject("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature,"
                + " prompt_text, purpose) VALUES (?, 'few-shot-structured', 'm', 0.7, 'p', 'production') RETURNING id",
                Integer.class, subject.getId());
        jdbc.update("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c,"
                + " option_d, correct_option, raw_llm_output, parse_success, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'A',"
                + " '{}', TRUE, 'qc_passed')", template.getId(), mcqRun, STEM, STATEMENTS.get(0), STATEMENTS.get(1),
                STATEMENTS.get(2), STATEMENTS.get(3));

        JsonNode first = generateTf(tfJson(STEM, STATEMENTS, List.of("T", "T", "F", "F")), true);
        assertEquals("NO_DUPLICATE", first.get("qcReport").get("dedupStatus").asText());

        List<String> permuted = List.of(STATEMENTS.get(2), STATEMENTS.get(0), STATEMENTS.get(3), STATEMENTS.get(1));
        JsonNode second = generateTf(tfJson(STEM, permuted, List.of("F", "T", "F", "T")), true);
        assertEquals("DUPLICATE", second.get("qcReport").get("dedupStatus").asText());
        assertEquals("FINGERPRINT", second.get("qcReport").get("dedup").get("layer").asText());
        assertEquals(first.get("question").get("id").asInt(),
                second.get("qcReport").get("dedup").get("comparedToQuestionId").asInt());
        assertEquals(1, SOLVER.calls, "câu trùng không gọi Solver");
    }

    // ---------- kiểm tra request ----------

    @Test
    void requestSaiChoCauTf() throws Exception {
        String override = ", \"rawLlmOutputOverride\": " + SimpleJson.quote(tfJson(STEM, STATEMENTS,
                List.of("T", "T", "F", "F"))) + ", \"runAnswerCheck\": false";
        String need4 = "Câu đúng/sai phải có targetStatementDifficulties gồm đúng 4 mức (ý a, b, c, d).";
        postGenerate("\"questionType\": \"tf\"" + override).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(need4));
        postGenerate("\"questionType\": \"tf\", \"targetStatementDifficulties\": [\"easy\", \"easy\", \"hard\"]"
                + override).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(need4));
        postGenerate("\"questionType\": \"tf\", \"targetDifficulty\": \"easy\", \"targetStatementDifficulties\": "
                + LEVELS + override).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "targetDifficulty không áp dụng cho câu đúng/sai; dùng targetStatementDifficulties."));
        postGenerate("\"questionType\": \"tf\", \"strategy\": \"few-shot\", \"targetStatementDifficulties\": "
                + LEVELS + override).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Câu đúng/sai luôn sinh với strategy zero-shot."));
        postGenerate("\"targetStatementDifficulties\": " + LEVELS + ", \"runAnswerCheck\": false")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("targetStatementDifficulties chỉ áp dụng cho câu đúng/sai."));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM experiment_run", Integer.class));
    }

    // ---------- job bất đồng bộ ----------

    static final class FakeGeneratorClient extends LlmClient {
        String lastSchema;
        List<String> labels = List.of("T", "T", "F", "F");

        FakeGeneratorClient() {
            super("FAKE_KEY_FOR_TESTS", "fake-generator-model");
        }

        @Override
        public LlmCallResult generateMcq(String prompt, String modelName, Strategy strategy, Double temperature) {
            throw new UnsupportedOperationException("Câu tf không được sinh qua generateMcq");
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            lastSchema = schema;
            return new LlmCallResult(tfJson(STEM, STATEMENTS, labels), 30, 2.0, 0.0, modelName);
        }
    }

    /** V5 (Q8): cảnh báo bước format được lưu ở dòng format, cả sync và job; API đọc lại đúng giá trị đó. */
    @Test
    void canhBaoDuocLuuODongFormatCaSyncVaJob() throws Exception {
        JsonNode same = generateTf(tfJson(STEM, STATEMENTS, List.of("T", "T", "T", "T")), false);
        int syncId = same.get("question").get("id").asInt();
        assertEquals(objectMapper.readTree("[\"label: 4 ý cùng nhãn T\"]"), formatWarnings(syncId));
        assertEquals(same.get("qcReport").get("answerWarnings"), formatWarnings(syncId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM quality_check_result WHERE question_id = ?"
                + " AND check_type <> 'format' AND warnings IS NOT NULL", Integer.class, syncId));

        JsonNode normal = generateTf(tfJson("Cho $Q = 2$.", STATEMENTS, List.of("T", "F", "T", "F")), false);
        assertNull(jdbc.queryForObject("SELECT warnings::text FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'format'", String.class, normal.get("question").get("id").asInt()),
                "không có cảnh báo thì NULL");

        mockMvc.perform(post("/api/generation/jobs/enqueue").contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\": " + template.getId() + ", \"questionType\": \"tf\","
                        + " \"targetStatementDifficulties\": [\"easy\", \"easy\", \"medium\", \"hard\"]}"))
                .andExpect(status().isOk());
        jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute'");
        FakeGeneratorClient generator = new FakeGeneratorClient();
        generator.labels = List.of("F", "F", "F", "F");
        GenerationJobPoller.ProcessedJobSummary summary = new GenerationJobPoller(jobProcessor, generator,
                qcOrchestrator, dedupScopeResolver, 3).processNextJob(false);
        assertEquals(objectMapper.readTree("[\"label: 4 ý cùng nhãn F\"]"), formatWarnings(summary.questionId()));
    }

    private JsonNode formatWarnings(int questionId) throws Exception {
        return objectMapper.readTree(jdbc.queryForObject("SELECT warnings::text FROM quality_check_result"
                + " WHERE question_id = ? AND check_type = 'format'", String.class, questionId));
    }

    @Test
    void jobTfQuaHangDoiVaChayLaiQc() throws Exception {
        String enqueue = "{\"templateId\": " + template.getId() + ", \"questionType\": \"tf\","
                + " \"targetStatementDifficulties\": [\"easy\", \"medium\", \"medium\", \"hard\"], \"count\": 1}";
        JsonNode jobs = objectMapper.readTree(body(mockMvc.perform(post("/api/generation/jobs/enqueue")
                .contentType(MediaType.APPLICATION_JSON).content(enqueue)).andExpect(status().isOk())));
        assertEquals("tf", jobs.get(0).get("questionType").asText());
        assertEquals("easy,medium,medium,hard", jobs.get(0).get("targetStatementDifficulties").asText());
        assertTrue(jobs.get(0).get("targetDifficulty").isNull());
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM experiment_run");
        assertEquals("tf", run.get("question_type"));
        assertEquals("tf-v2-draft", run.get("prompt_version"));

        FakeGeneratorClient generator = new FakeGeneratorClient();
        GenerationJobPoller poller = new GenerationJobPoller(jobProcessor, generator, qcOrchestrator,
                dedupScopeResolver, 3);
        GenerationJobPoller.ProcessedJobSummary summary = poller.processNextJob(false);
        assertEquals("generated", summary.questionStatus());
        assertEquals(TfPromptDraftV0.GENERATION_RESPONSE_SCHEMA_JSON, generator.lastSchema);
        assertEquals(List.of("easy", "medium", "medium", "hard"), jdbc.queryForList("SELECT llm_difficulty FROM"
                + " generated_question_statement WHERE question_id = ? ORDER BY position", String.class,
                summary.questionId()));
        assertEquals("done", jdbc.queryForObject("SELECT status FROM generation_job", String.class));

        QcRerunService.QcRerunResult rerun = qcRerunService.rerun(summary.questionId());
        assertEquals("qc_passed", rerun.resultingStatus().getDbValue());
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM answer_check_statement", Integer.class));
    }

    /** Ngày 23/10: lý do format_failed lưu ở errors (V8); sync chạy chế độ sửa (tự đóng $ ở cuối ý). */
    @Test
    void loiFormatLuuOErrorsVaSyncTuDongDoLa() throws Exception {
        JsonNode dup = generateTf(tfJson(STEM, List.of("Ý một", "Ý hai", "Ý một", "Ý bốn"),
                List.of("T", "T", "F", "F")), false);
        assertEquals("format_failed", dup.get("question").get("status").asText());
        assertEquals("[\"4 ý a, b, c, d có nội dung trùng lặp nhau\"]", objectMapper.readTree(jdbc.queryForObject(
                "SELECT errors::text FROM quality_check_result WHERE question_id = ? AND check_type = 'format'",
                String.class, dup.get("question").get("id").asInt())).toString());

        JsonNode fixed = generateTf(tfJson("Cho $R = x^2$.", List.of(STATEMENTS.get(0), STATEMENTS.get(1),
                STATEMENTS.get(2), "Giá trị của $R$ tại $x = 3$ bằng $9."), List.of("T", "T", "F", "F")), false);
        assertTrue(fixed.get("qcReport").get("formatPassed").asBoolean(), fixed.toString());
        assertEquals("[\"statement_d: đã tự đóng $\"]", fixed.get("qcReport").get("answerWarnings").toString());
        assertNull(jdbc.queryForObject("SELECT errors::text FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'format'", String.class, fixed.get("question").get("id").asInt()));
    }
}
