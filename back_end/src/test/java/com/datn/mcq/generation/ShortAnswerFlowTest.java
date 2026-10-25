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
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.service.QcRerunService;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Luồng Trả lời ngắn trọn vẹn qua HTTP thật trên datn_mcq_test (GĐ2): generate-sync với rawLlmOutputOverride,
 * format, dedup, Solver giả (không gọi mạng); job bất đồng bộ; chạy lại QC.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ShortAnswerFlowTest {

    private static final String STEM = "Nghiệm của phương trình $4x + 3 = 0$ là bao nhiêu?";
    private static final String SECRET_EXPLANATION = "LỜI GIẢI BÍ MẬT: $4x = -3$ nên $x = -\\frac{3}{4}$.";

    /** Solver giả: chỉ trả lời qua callJsonModeWithSchema, ghi lại prompt và schema. */
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
        public LlmCallResult callJsonMode(String prompt, String modelName, Double temperature) {
            throw new UnsupportedOperationException("Solver phải dùng callJsonModeWithSchema");
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Subject subject;
    private QuestionTemplate template;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "ShortAnswerFlowTest.setUp");
        SOLVER.calls = 0;
        SOLVER.prompts.clear();
        SOLVER.schemas.clear();
        SOLVER.response = solverSays("-0,75");
        subject = subjectRepository.save(new Subject("Toán Trả lời ngắn"));
        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(subject.getId());
        tpl.setGradeLevel(8);
        tpl.setTopic("Phương trình bậc nhất");
        tpl.setLearningOutcome("Giải được phương trình bậc nhất một ẩn.");
        tpl.setSourceType(SourceType.TEXT);
        tpl.setSourceQuestion("Giải $2x = 6$.");
        tpl.setIsActive(true);
        template = templateRepository.save(tpl);
    }

    private static String solverSays(String judgeAnswer) {
        return "{\"reasoning\": \"Tự giải được kết quả.\", \"judge_answer\": " + SimpleJson.quote(judgeAnswer) + "}";
    }

    private static String saJson(String stem, String answer) {
        return "{\"question_text\": " + SimpleJson.quote(stem) + ", \"explanation\": "
                + SimpleJson.quote(SECRET_EXPLANATION) + ", \"answer\": " + SimpleJson.quote(answer) + "}";
    }

    private ResultActions postGenerate(String extraFields) throws Exception {
        String body = "{\"templateId\": " + template.getId() + ", " + extraFields + "}";
        return mockMvc.perform(post("/api/generation/generate-sync")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode generateSa(String llmJson, boolean runAnswerCheck) throws Exception {
        String body = mockMvcBody(postGenerate("\"questionType\": \"sa\", \"targetDifficulty\": \"medium\","
                + " \"runAnswerCheck\": " + runAnswerCheck + ", \"rawLlmOutputOverride\": " + SimpleJson.quote(llmJson))
                .andExpect(status().isOk()));
        return objectMapper.readTree(body);
    }

    private static String mockMvcBody(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode getJson(String url) throws Exception {
        return objectMapper.readTree(mockMvcBody(mockMvc.perform(get(url)).andExpect(status().isOk())));
    }

    // ---------- trọn luồng ----------

    @Test
    void saDiQuaQc() throws Exception {
        JsonNode resp = generateSa(saJson(STEM, "$-\\frac{3}{4}$"), true);
        JsonNode question = resp.get("question");
        JsonNode qc = resp.get("qcReport");
        assertEquals("sa", question.get("questionType").asText());
        assertEquals("qc_passed", question.get("status").asText());
        assertEquals("CONSISTENT", qc.get("answerCheckStatus").asText());
        assertEquals("NO_DUPLICATE", qc.get("dedupStatus").asText());
        assertEquals("number", qc.get("saJudgeKind").asText());
        assertEquals("-0,75", qc.get("saJudgeAnswer").asText());
        assertTrue(qc.get("judgeAnswer").isNull(), "judge_answer MCQ để trống với câu sa");
        int questionId = question.get("id").asInt();

        // Solver chỉ thấy đề, dùng schema SA.
        assertEquals(1, SOLVER.calls);
        assertEquals(SaPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON, SOLVER.schemas.get(0));
        assertTrue(SOLVER.prompts.get(0).contains(STEM));
        assertFalse(SOLVER.prompts.get(0).contains("LỜI GIẢI BÍ MẬT"));

        Map<String, Object> gq = jdbc.queryForMap("SELECT * FROM generated_question WHERE id = ?", questionId);
        assertEquals("sa", gq.get("question_type"));
        assertEquals("$-\\frac{3}{4}$", gq.get("sa_answer"));
        assertNull(gq.get("option_a"));
        assertNull(gq.get("correct_option"));
        assertEquals("medium", gq.get("llm_difficulty"));
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM experiment_run WHERE id = ?", gq.get("run_id"));
        assertEquals("sa", run.get("question_type"));
        assertEquals("production", run.get("purpose"));
        assertEquals("zero-shot", run.get("strategy"));
        assertEquals("sa-v1-draft", run.get("prompt_version"));
        assertEquals(List.of("sa", "sa", "sa"), jdbc.queryForList(
                "SELECT question_type FROM quality_check_result WHERE question_id = ? ORDER BY check_type",
                String.class, questionId));
        Map<String, Object> answerCheck = jdbc.queryForMap("SELECT * FROM quality_check_result"
                + " WHERE question_id = ? AND check_type = 'answer_check'", questionId);
        assertEquals("number", answerCheck.get("judge_sa_kind"));
        assertEquals("-0,75", answerCheck.get("judge_sa_answer"));
        assertNull(answerCheck.get("judge_answer"));
        assertEquals(Boolean.TRUE, answerCheck.get("passed"));

        // Chưa có thao tác của giáo viên nên chưa có review hay ngân hàng (Giữ vào ngân hàng: ExamBuildTest).
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM bank_item", Integer.class));
    }

    /** Hồi quy SA (GĐ3): bộ key của API câu sa giữ như cuối GĐ2, không có trường TF. */
    @Test
    void snapshotKeyApiSa() throws Exception {
        JsonNode resp = generateSa(saJson(STEM, "-0,75"), true);
        assertEquals(Set.of("id", "templateId", "runId", "questionText", "optionA", "optionB", "optionC", "optionD",
                "parseSuccess", "status", "tokenCount", "latencyMs", "createdAt", "questionType"),
                fieldNames(resp.get("question")));
        assertEquals(Set.of("resultingStatus", "formatPassed", "formatErrors", "answerCheckPassed", "judgeAnswer",
                "judgeModel", "solverReasoning", "answerCheckStatus", "dedupStatus", "dedup", "saJudgeKind",
                "saJudgeAnswer"), fieldNames(resp.get("qcReport")));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    // ---------- Solver theo bảng mục 7 ----------

    @Test
    void solverBaoMoHoLaInconsistentGiuGenerated() throws Exception {
        SOLVER.response = solverSays("ambiguous");
        JsonNode resp = generateSa(saJson(STEM, "-0,75"), true);
        assertEquals("generated", resp.get("question").get("status").asText());
        assertEquals("INCONSISTENT", resp.get("qcReport").get("answerCheckStatus").asText());
        assertEquals("ambiguous", resp.get("qcReport").get("saJudgeKind").asText());
        int id = resp.get("question").get("id").asInt();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'answer_check'", id);
        assertEquals("ambiguous", row.get("judge_sa_kind"));
        assertNull(row.get("judge_sa_answer"));
    }

    @Test
    void solverTraChuoiKhongSoDuocLaInconsistent() throws Exception {
        SOLVER.response = solverSays("2 hoặc 3");
        JsonNode resp = generateSa(saJson(STEM, "-0,75"), true);
        assertEquals("INCONSISTENT", resp.get("qcReport").get("answerCheckStatus").asText());
        assertEquals("unparseable", resp.get("qcReport").get("saJudgeKind").asText());
        assertEquals("2 hoặc 3", resp.get("qcReport").get("saJudgeAnswer").asText());
        assertTrue(resp.get("qcReport").get("solverReasoning").asText().startsWith("không so tự động được"));
    }

    @Test
    void solverKhacGiaTriVaSolverKhoaTrung() throws Exception {
        SOLVER.response = solverSays("0,33");
        JsonNode differ = generateSa(saJson("Tính $1 : 3$ (làm tròn đến hàng phần trăm).", "$\\frac{1}{3}$"), true);
        assertEquals("INCONSISTENT", differ.get("qcReport").get("answerCheckStatus").asText());

        SOLVER.response = "{\"reasoning\": \"r\", \"judge_answer\": \"1\", \"judge_answer\": \"2\"}";
        JsonNode dup = generateSa(saJson("Tính $2 + 3$.", "5"), true);
        assertEquals("ERROR", dup.get("qcReport").get("answerCheckStatus").asText());
        assertEquals("generated", dup.get("question").get("status").asText());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'answer_check'", Integer.class, dup.get("question").get("id").asInt()));
    }

    // ---------- format ----------

    @Test
    void dapSoSaiDinhDangLaFormatFailedKhongGoiSolver() throws Exception {
        JsonNode resp = generateSa(saJson(STEM, "05"), true);
        assertEquals("format_failed", resp.get("question").get("status").asText());
        assertTrue(resp.get("qcReport").get("formatErrors").toString().contains("có chữ số 0 thừa ở đầu"));
        assertEquals(0, SOLVER.calls);
    }

    @Test
    void phanSoChuaToiGianChiCanhBao() throws Exception {
        SOLVER.response = solverSays("0,5");
        JsonNode resp = generateSa(saJson("Tính $1 : 2$.", "2/4"), true);
        assertEquals("qc_passed", resp.get("question").get("status").asText());
        assertEquals("[\"answer: phân số chưa tối giản\"]", resp.get("qcReport").get("answerWarnings").toString());
        // V5 (Q8): cảnh báo được lưu ở dòng format; các dòng khác để NULL.
        int id = resp.get("question").get("id").asInt();
        assertEquals(objectMapper.readTree("[\"answer: phân số chưa tối giản\"]"), objectMapper.readTree(
                jdbc.queryForObject("SELECT warnings::text FROM quality_check_result WHERE question_id = ?"
                        + " AND check_type = 'format'", String.class, id)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM quality_check_result WHERE question_id = ?"
                + " AND check_type <> 'format' AND warnings IS NOT NULL", Integer.class, id));
    }

    // ---------- dedup ----------

    @Test
    void trungTheoGiaTriDapSoVaKhongSoVoiMcq() throws Exception {
        String longStem = "Một bể nước hình hộp chữ nhật được bơm đầy trong $4$ giờ, mỗi giờ bơm thêm cùng một lượng."
                + " Hỏi sau một giờ bể đầy được bao nhiêu phần?";
        // Câu MCQ qc_passed cùng thân câu trong run production cùng môn: không được so với câu sa.
        int mcqRun = jdbc.queryForObject("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature,"
                + " prompt_text, purpose) VALUES (?, 'few-shot-structured', 'm', 0.7, 'p', 'production') RETURNING id",
                Integer.class, subject.getId());
        jdbc.update("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c,"
                + " option_d, correct_option, raw_llm_output, parse_success, status) VALUES (?, ?, ?, '$\\frac{1}{4}$',"
                + " '$\\frac{1}{2}$', '$\\frac{1}{3}$', '$4$', 'A', '{}', TRUE, 'qc_passed')", template.getId(), mcqRun,
                longStem);

        SOLVER.response = solverSays("0,25");
        JsonNode first = generateSa(saJson(longStem, "$\\frac{1}{4}$"), true);
        assertEquals("NO_DUPLICATE", first.get("qcReport").get("dedupStatus").asText());
        assertEquals("qc_passed", first.get("question").get("status").asText());

        JsonNode second = generateSa(saJson(longStem, "0,25"), true);
        assertEquals("DUPLICATE", second.get("qcReport").get("dedupStatus").asText());
        assertEquals("FINGERPRINT", second.get("qcReport").get("dedup").get("layer").asText());
        assertEquals(first.get("question").get("id").asInt(),
                second.get("qcReport").get("dedup").get("comparedToQuestionId").asInt());
        assertEquals("duplicate", second.get("question").get("status").asText());
        assertEquals("sa", jdbc.queryForObject("SELECT question_type FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'dedup'", String.class, second.get("question").get("id").asInt()));
    }

    // ---------- kiểm tra request ----------

    @Test
    void requestSaiDangCau() throws Exception {
        postGenerate("\"questionType\": \"sa\", \"strategy\": \"few-shot\", \"runAnswerCheck\": false,"
                + " \"rawLlmOutputOverride\": " + SimpleJson.quote(saJson(STEM, "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Câu trả lời ngắn luôn sinh với strategy zero-shot."));
        // GĐ3: tf đã được hỗ trợ; thiếu mức của 4 ý thì 400 (xem TrueFalseFlowTest.requestSaiChoCauTf).
        postGenerate("\"questionType\": \"tf\", \"runAnswerCheck\": false")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Câu đúng/sai phải có targetStatementDifficulties gồm đúng 4 mức (ý a, b, c, d)."));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM experiment_run", Integer.class));
    }

    // ---------- job bất đồng bộ ----------

    /** LlmClient sinh câu giả cho worker: trả JSON SA qua callJsonModeWithSchema. */
    static final class FakeGeneratorClient extends LlmClient {
        String lastSchema;

        FakeGeneratorClient() {
            super("FAKE_KEY_FOR_TESTS", "fake-generator-model");
        }

        @Override
        public LlmCallResult generateMcq(String prompt, String modelName, Strategy strategy, Double temperature) {
            throw new UnsupportedOperationException("Câu sa không được sinh qua generateMcq");
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            lastSchema = schema;
            return new LlmCallResult(saJson(STEM, "-0,75"), 20, 2.0, 0.0, modelName);
        }
    }

    @Test
    void jobSaQuaHangDoi() throws Exception {
        String enqueue = "{\"templateId\": " + template.getId() + ", \"questionType\": \"sa\","
                + " \"targetDifficulty\": \"hard\", \"count\": 2}";
        JsonNode jobs = objectMapper.readTree(mockMvcBody(mockMvc.perform(post("/api/generation/jobs/enqueue")
                .contentType(MediaType.APPLICATION_JSON).content(enqueue)).andExpect(status().isOk())));
        assertEquals(2, jobs.size());
        assertEquals("sa", jobs.get(0).get("questionType").asText());
        assertFalse(jobs.get(0).has("targetStatementDifficulties"));
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM experiment_run");
        assertEquals("sa", run.get("question_type"));
        assertEquals("zero-shot", run.get("strategy"));
        assertEquals("sa-v1-draft", run.get("prompt_version"));
        assertEquals("sa", getJson("/api/generation/jobs").get(0).get("questionType").asText());

        FakeGeneratorClient generator = new FakeGeneratorClient();
        GenerationJobPoller poller = new GenerationJobPoller(jobProcessor, generator, qcOrchestrator,
                dedupScopeResolver, 3);
        GenerationJobPoller.ProcessedJobSummary summary = poller.processNextJob(false);
        assertTrue(summary.processed());
        assertEquals("generated", summary.questionStatus(), "answer_check tắt nên giữ generated");
        assertEquals(SaPromptDraftV0.GENERATION_RESPONSE_SCHEMA_JSON, generator.lastSchema);
        Map<String, Object> gq = jdbc.queryForMap("SELECT * FROM generated_question WHERE id = ?", summary.questionId());
        assertEquals("sa", gq.get("question_type"));
        assertEquals("-0,75", gq.get("sa_answer"));
        assertEquals("hard", gq.get("llm_difficulty"));
        assertEquals(List.of("sa", "sa"), jdbc.queryForList("SELECT question_type FROM quality_check_result"
                + " WHERE question_id = ? ORDER BY check_type", String.class, summary.questionId()));

        // Chạy lại QC (quy tắc QC-RERUN) cho câu generated: Solver giả xác nhận nên lên qc_passed.
        QcRerunService.QcRerunResult rerun = qcRerunService.rerun(summary.questionId());
        assertEquals("qc_passed", rerun.resultingStatus().getDbValue());
        assertEquals(1, SOLVER.calls);
    }
}
