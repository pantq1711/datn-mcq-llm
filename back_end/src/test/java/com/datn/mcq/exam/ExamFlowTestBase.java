package com.datn.mcq.exam;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nền chung cho các test W1-3 về luồng tạo đề (chọn câu ngân hàng, dedup trong đề, vết QC): HTTP thật qua filter chain
 * trên datn_mcq_test, LLM sinh câu và Solver giả (không gọi mạng). Không phải test (tên không khớp mẫu của surefire).
 */
abstract class ExamFlowTestBase {

    static final ExamQuestionEditTest.EditSolver SOLVER = new ExamQuestionEditTest.EditSolver();

    @TestBean
    private AnswerSolverChecker answerSolverChecker;

    static AnswerSolverChecker answerSolverChecker() {
        return new AnswerSolverChecker(SOLVER, new PromptBuilder(), "fake-solver-model");
    }

    @Autowired
    WebApplicationContext webApplicationContext;
    @Autowired
    DataSource dataSource;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    GenerationJobProcessor jobProcessor;
    @Autowired
    QcOrchestrator qcOrchestrator;
    @Autowired
    DedupScopeResolver dedupScopeResolver;

    final ObjectMapper objectMapper = new ObjectMapper();
    MockMvc mockMvc;
    ExamBuildTest.FakeGenerator generator;
    int subject;
    int teacher;
    int otherTeacher;

    @BeforeEach
    void setUpBase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, getClass().getSimpleName() + ".setUp");
        SOLVER.mcqJudge = "B";
        SOLVER.saJudge = "5";
        SOLVER.reasoning = "r";
        SOLVER.tfJudges = null;
        SOLVER.fail = false;
        SOLVER.onCall = null;
        SOLVER.calls = 0;
        ExamBuildTest.FakeGenerator.lastTfLabels = "T,F,T,F";
        generator = new ExamBuildTest.FakeGenerator();
        subject = insert("INSERT INTO subject (name) VALUES ('Toán')");
        teacher = insert("INSERT INTO users (name, email, role) VALUES ('Cô An', 'an.flow@test', 'teacher')");
        otherTeacher = insert("INSERT INTO users (name, email, role) VALUES ('Thầy Bình', 'binh.flow@test',"
                + " 'teacher')");
    }

    // ---------- tiện ích ----------

    Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(AuthTestSupport.teacher(teacher)));
    }

    ResultActions performAs(int userId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(AuthTestSupport.teacher(userId)));
    }

    JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    ResultActions postJson(String url, String body) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Ô ma trận mcq mức dễ. {@code content} null thì bỏ trống. */
    static String mcqCell(String topic, String content, int count) {
        return "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": " + count
                + ", \"topic\": \"" + topic + "\"" + (content == null ? "" : ", \"content\": \"" + content + "\"")
                + ", \"learningOutcomes\": {\"easy\": \"Nhận biết.\"}}";
    }

    int createMatrix(String... cells) throws Exception {
        return json(postJson("/api/exam-matrices", "{\"name\": \"Ma trận thử\", \"subjectId\": " + subject
                + ", \"gradeLevel\": 8, \"cells\": [" + String.join(",", cells) + "]}")
                .andExpect(status().isOk())).get("id").asInt();
    }

    int cellId(int matrixId, int position) {
        return count("SELECT id FROM exam_matrix_cell WHERE matrix_id = ? AND position = ?", matrixId, position);
    }

    JsonNode build(int matrixId) throws Exception {
        return json(postJson("/api/exams/build", "{\"matrixId\": " + matrixId + "}").andExpect(status().isOk()));
    }

    JsonNode progress(int examId) throws Exception {
        return json(perform(get("/api/exams/" + examId + "/progress")).andExpect(status().isOk()));
    }

    GenerationJobPoller poller() {
        return new GenerationJobPoller(jobProcessor, generator, qcOrchestrator, dedupScopeResolver, 3);
    }

    /** Xử lý mọi job đến hạn (đặt lại next_run_at về quá khứ trước mỗi vòng). */
    void processAll() {
        GenerationJobPoller poller = poller();
        for (int i = 0; i < 50; i++) {
            jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'");
            if (!poller.processNextJob(true).processed()) {
                return;
            }
        }
    }

    /** Xử lý job rồi đối soát (xem tiến độ), lặp để đi hết các lần sinh lại. */
    void settle(int examId) throws Exception {
        for (int round = 0; round < 5; round++) {
            processAll();
            progress(examId);
        }
    }

    int slotId(int examId, int cellId, int slotNo) {
        return count("SELECT id FROM exam_slot WHERE exam_id = ? AND cell_id = ? AND slot_no = ?", examId, cellId,
                slotNo);
    }

    String slotState(int slotId) {
        return jdbc.queryForObject("SELECT state FROM exam_slot WHERE id = ?", String.class, slotId);
    }

    /**
     * Câu mcq trong ngân hàng. {@code templateId} null thì tạo template mới có chủ đề {@code topic}. Câu nguồn
     * (generated_question) có đề {@code sourceText}; bank_item có đề và phương án {@code bankText}/{@code bankOptions}
     * (khác câu nguồn khi giáo viên đã sửa lúc duyệt).
     */
    int bankMcq(Integer templateId, String topic, String difficulty, String sourceText, String bankText,
                String[] bankOptions) {
        int tpl = templateId != null ? templateId : insert("INSERT INTO question_template (subject_id, source_question,"
                + " is_active, topic) VALUES (?, 'Mẫu', TRUE, ?)", subject, topic);
        int run = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, final_difficulty)"
                + " VALUES (?, ?, ?, '$11$', '$12$', '$13$', '$14$', 'B', '{}', TRUE, 'in_bank', ?)", tpl, run,
                sourceText, difficulty);
        int review = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production',"
                + " 'B', TRUE, TRUE, ?, 'approved_as_is', 'B')", q, teacher, difficulty);
        return insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, difficulty) VALUES (?, ?, ?, ?, ?, ?, ?, 'B', ?)", q, review,
                bankText, bankOptions[0], bankOptions[1], bankOptions[2], bankOptions[3], difficulty);
    }

    int bankMcq(String topic, String difficulty, String text) {
        return bankMcq(null, topic, difficulty, text, text, new String[] {"$1$", "$2$", "$3$", "$4$"});
    }

    /** Một đề thủ công (exam_question) của {@code owner} dùng câu ngân hàng, tạo lúc {@code createdAt}. */
    int manualExamUsing(int owner, String code, String createdAt, int... bankItems) {
        int exam = insert("INSERT INTO exam (exam_code, title, subject_id, created_by, created_at) VALUES (?, ?, ?, ?,"
                + " ?::timestamp)", code, "Đề " + code, subject, owner, createdAt);
        int pos = 1;
        for (int item : bankItems) {
            jdbc.update("INSERT INTO exam_question (exam_id, bank_item_id, position) VALUES (?, ?, ?)", exam, item,
                    pos++);
        }
        return exam;
    }
}
