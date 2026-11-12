package com.datn.mcq.exam;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.exam.export.PandocRunner;
import com.datn.mcq.exam.export.PandocTestSupport;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.MatrixPromptDraftV0;
import com.datn.mcq.llm.prompt.McqAnswerPlan;
import com.datn.mcq.llm.prompt.McqProductionSchema;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
import com.datn.mcq.llm.prompt.TfLabelPattern;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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

    /** Solver giả: trả theo schema; mcq luôn B, sa theo {@code saJudge}, tf theo {@code tfJudges} hoặc nhãn câu tf vừa sinh. */
    static final class FakeSolver extends LlmClient {
        String saJudge = "5";
        /** null: đồng ý với nhãn câu tf vừa sinh ({@link FakeGenerator#lastTfLabels}). */
        String tfJudges;
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
                String[] j = (tfJudges != null ? tfJudges : FakeGenerator.lastTfLabels).split(",");
                body = "{\"reasoning_a\": \"r\", \"judge_a\": \"" + j[0] + "\", \"reasoning_b\": \"r\", \"judge_b\": \""
                        + j[1] + "\", \"reasoning_c\": \"r\", \"judge_c\": \"" + j[2] + "\", \"reasoning_d\": \"r\","
                        + " \"judge_d\": \"" + j[3] + "\"}";
            } else if (SaPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON.equals(schema)) {
                body = "{\"reasoning\": \"r\", \"judge_answer\": \"" + saJudge + "\"}";
            } else {
                body = "{\"reasoning\": \"r\", \"judge_answer\": \"" + FakeGenerator.lastMcqLetter + "\"}";
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
        /** Số câu tf tiếp theo có lời giải tự thú nhận gán nhãn theo mẫu (bị loại ở bước kiểm định dạng). */
        int admissionTf;
        /** Số câu mcq tiếp theo có lệnh LaTeX một dấu gạch trong JSON ({@code \circ}, được sửa escape). */
        int escapeMcq;
        /** Số câu mcq tiếp theo quên đóng "$" ở cuối option_a (được tự đóng). */
        int unclosedMcq;
        /** Số câu mcq tiếp theo có lệnh LaTeX nằm ngoài $...$ trong lời giải (lỗi LaTeX của lời giải). */
        int rawLatexExplanationMcq;
        /** Số câu mcq tiếp theo có lệnh LaTeX lạ ({@code \foo}) ngoài $...$ trong lời giải: bước giải mã không bọc được. */
        int unwrappableExplanationMcq;
        /** Nếu khác null: đề dẫn (đã thoát JSON) của câu mcq kế tiếp, dùng một lần. */
        String nextMcqQuestion;
        /** Nếu khác null: lời giải (đã thoát JSON) của câu mcq kế tiếp, dùng một lần. */
        String nextMcqExplanation;
        /** null: theo mẫu nhãn bắt buộc trong prompt (không có mẫu thì T,F,T,F). */
        String[] tfLabels;
        static String lastTfLabels = "T,F,T,F";
        /** Chữ đúng của câu mcq vừa sinh (FakeSolver đồng ý với chữ này): B, hoặc chữ đã giao khi {@link #followMcqLetter}. */
        static String lastMcqLetter = "B";
        /** true: đặt đáp án đúng ở chữ đã giao trong prompt (McqAnswerPlan); false: luôn B như trước. */
        boolean followMcqLetter;
        int n;

        FakeGenerator() {
            super("FAKE_KEY_FOR_TESTS", "fake-generator-model");
        }

        @Override
        public boolean isConfigured() {
            return true;
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
            String explanation = "Số mũ của x.";
            if (rawLatexExplanationMcq > 0) {
                rawLatexExplanationMcq--;
                // JSON thoát đúng; sau giải mã là \frac{5x + 3}{y} nằm ngoài $...$.
                explanation = "Ta có \\\\frac{5x + 3}{y} khi $y \\\\neq 0$.";
            } else if (unwrappableExplanationMcq > 0) {
                unwrappableExplanationMcq--;
                explanation = "Ta có \\\\foo{5x + 3} khi $y$.";
            } else if (escapeMcq > 0) {
                escapeMcq--;
                explanation = "Số mũ của x, góc $90^\\circ$.";
            }
            if (nextMcqExplanation != null) {
                explanation = nextMcqExplanation;
                nextMcqExplanation = null;
            }
            String optionA = "$" + (k + 1) + "$";
            if (unclosedMcq > 0 && explanation.equals("Số mũ của x.")) {
                unclosedMcq--;
                optionA = "$" + (k + 1);
            }
            String questionText = "Bậc của đơn thức $" + (k + 1) + "x^" + k + "$ là:";
            if (nextMcqQuestion != null) {
                questionText = nextMcqQuestion;
                nextMcqQuestion = null;
            }
            String assigned = followMcqLetter ? McqAnswerPlan.fromNote(prompt) : null;
            if (assigned != null) {
                // Đáp án đúng "$k$" đặt ở chữ đã giao; ba phương án nhiễu điền vào ba chỗ còn lại theo thứ tự.
                List<String> distractors = new ArrayList<>(List.of(optionA, "$0$", "$" + (k + 2) + "$"));
                String[] options = new String[4];
                for (int i = 0; i < 4; i++) {
                    options[i] = McqAnswerPlan.LETTERS.get(i).equals(assigned) ? "$" + k + "$" : distractors.remove(0);
                }
                lastMcqLetter = assigned;
                return new LlmCallResult("{\"question_text\": \"" + questionText + "\","
                        + " \"option_a\": \"" + options[0] + "\", \"option_b\": \"" + options[1] + "\", \"option_c\": \""
                        + options[2] + "\", \"option_d\": \"" + options[3] + "\", \"correct_option\": \"" + assigned
                        + "\", \"explanation\": \"" + explanation + "\"}", 10, 1.0, 0.0, modelName);
            }
            lastMcqLetter = "B";
            return new LlmCallResult("{\"question_text\": \"" + questionText + "\","
                    + " \"option_a\": \"" + optionA + "\", \"option_b\": \"$" + k + "$\", \"option_c\": \"$0$\","
                    + " \"option_d\": \"$" + (k + 2) + "$\", \"correct_option\": \"B\", \"explanation\": \""
                    + explanation + "\"}", 10, 1.0, 0.0, modelName);
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
            String[] labels = tfLabels;
            if (labels == null) {
                String pattern = TfLabelPattern.fromNote(prompt);
                labels = (pattern != null ? pattern : "TFTF").split("");
            }
            lastTfLabels = String.join(",", labels);
            String tfExplanation = "Lời giải.";
            if (admissionTf > 0) {
                admissionTf--;
                tfExplanation = "Ý b sai nhưng theo mẫu nhãn yêu cầu gán nhãn F.";
            }
            return new LlmCallResult("{\"question_text\": " + SimpleJson.quote("Cho biểu thức $P = (x + " + k + ")^2$.")
                    + ", \"statement_a\": \"Ý một số " + k + ".\", \"statement_b\": \"Ý hai số " + k + ".\","
                    + " \"statement_c\": \"Ý ba số " + k + ".\", \"statement_d\": \"Ý bốn số " + k + ".\","
                    + " \"explanation\": \"" + tfExplanation + "\", \"label_a\": \"" + labels[0] + "\", \"label_b\": \""
                    + labels[1] + "\", \"label_c\": \"" + labels[2] + "\", \"label_d\": \"" + labels[3] + "\"}",
                    10, 1.0, 0.0, modelName);
        }
    }

    private static final FakeSolver SOLVER = new FakeSolver();

    @TestBean
    private AnswerSolverChecker answerSolverChecker;

    static AnswerSolverChecker answerSolverChecker() {
        return new AnswerSolverChecker(SOLVER, new PromptBuilder(), "fake-solver-model");
    }

    /** pandoc thật nếu máy có (PandocTestSupport), không thì đường dẫn không tồn tại để kiểm tra 503. */
    @TestBean
    private PandocRunner pandocRunner;

    static PandocRunner pandocRunner() {
        return new PandocRunner(PandocTestSupport.find().orElse("pandoc-khong-co-tren-may-nay"));
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
        // W1-1: đi qua filter chain thật; mọi request mang JWT của giáo viên {@code teacher} (xem perform).
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "ExamBuildTest.setUp");
        SOLVER.saJudge = "5";
        SOLVER.tfJudges = null;
        SOLVER.calls = 0;
        FakeGenerator.lastTfLabels = "T,F,T,F";
        FakeGenerator.lastMcqLetter = "B";
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

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** Gọi API với tư cách giáo viên {@code teacher} (chủ sở hữu ma trận và đề trong test này). */
    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(AuthTestSupport.teacher(teacher)));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
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
        return json(perform(get("/api/exams/" + examId + "/progress")).andExpect(status().isOk()));
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

    /** Mẫu nhãn bắt buộc của job tf mới nhất trong đề, đọc lại từ generation_job.prompt_note. */
    private String tfPattern(int examId) {
        return TfLabelPattern.fromNote(jdbc.queryForObject("SELECT j.prompt_note FROM generation_job j JOIN exam_slot s"
                + " ON s.id = j.exam_slot_id WHERE s.exam_id = ? AND j.question_type = 'tf' ORDER BY j.id DESC LIMIT 1",
                String.class, examId));
    }

    /** Nhãn LLM trả về (generated_question_statement.llm_label) của câu tf, theo thứ tự a-d. */
    private List<String> llmLabels(int questionId) {
        return jdbc.queryForList("SELECT llm_label FROM generated_question_statement WHERE question_id = ?"
                + " ORDER BY position", String.class, questionId);
    }

    private static String comma(String pattern) {
        return String.join(",", pattern.split(""));
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
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
        assertEquals(List.of("matrix-mcq-v9-draft", "matrix-tf-v11-draft", "matrix-sa-v11-draft"),
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
        JsonNode mcqSlots = json(perform(get("/api/exams/" + examId + "/cells/" + cellId(1)))
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
        postJson("/api/exams/build", "{}").andExpect(status().isBadRequest());
        perform(get("/api/exams/999999/progress")).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM exam", Integer.class));
    }

    /**
     * W1-1: worker (poller, xử lý job, QC) không đi qua HTTP nên chạy được khi không có ai đăng nhập; chỉ API xem tiến
     * độ mới cần token của chủ đề.
     */
    @Test
    void workerChayKhongCanNguoiDangNhap() throws Exception {
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        SecurityContextHolder.clearContext();
        processAll();
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "worker không cần SecurityContext");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM generation_job WHERE exam_slot_id IS NOT NULL"
                + " AND status IN ('pending', 'running')", Integer.class), "mọi job đã được xử lý");
        assertEquals("ready", progress(examId).get("status").asText());
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
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
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
        assertEquals(List.of("warning: label: 4 ý cùng nhãn T"), texts(tf.get("reviewReasons")),
                "lệch mẫu mục tiêu không còn bật cờ; 4 ý cùng nhãn vẫn bật");

        JsonNode sa = view.get("parts").get(2).get("items").get(0);
        assertEquals("5", sa.get("saAnswer").asText());
        assertTrue(sa.get("needsReview").asBoolean());
        assertEquals("[\"solver_inconsistent\"]", sa.get("reviewReasons").toString());
        assertEquals(2, view.get("needsReviewCount").asInt());
    }

    @Test
    void xemDeDangSinhChiCoDinhDanh() throws Exception {
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals("generating", view.get("buildStatus").asText());
        JsonNode item = view.get("parts").get(0).get("items").get(0);
        assertEquals("generating", item.get("state").asText());
        assertFalse(item.has("questionText"));
        assertFalse(item.has("correctOption"));
        perform(get("/api/exams/999999")).andExpect(status().isNotFound());
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
        return postJson("/api/exams/" + examId + "/slots/" + slotId + "/" + action, "{}");
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
        assertEquals(llmLabels(tf.get("questionId").asInt()), jdbc.queryForList("SELECT label FROM bank_item_statement"
                + " WHERE bank_item_id = ? ORDER BY position", String.class, tf.get("bankItemId").asInt()),
                "ngân hàng mang nhãn LLM của câu");
        assertEquals(List.of("easy", "easy", "medium", "hard"), jdbc.queryForList("SELECT difficulty FROM"
                + " bank_item_statement WHERE bank_item_id = ? ORDER BY position", String.class,
                tf.get("bankItemId").asInt()));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM teacher_review_statement", Integer.class));
    }

    private JsonNode itemOf(int examId, String type) throws Exception {
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        for (JsonNode part : view.get("parts")) {
            for (JsonNode item : part.get("items")) {
                if (type.equals(item.get("questionType").asText())) {
                    return item;
                }
            }
        }
        throw new AssertionError("không có câu " + type);
    }

    private Object visibleTo(JsonNode kept) {
        return jdbc.queryForObject("SELECT visible_to_user_id FROM bank_item WHERE id = ?", Object.class,
                kept.get("bankItemId").asInt());
    }

    /**
     * L5 (10/11): Giữ câu có cờ "cần xem kỹ" VẪN được (200, slot kept, có nhật ký keep để đo), nhưng bank_item riêng tư của
     * người Giữ; câu không cờ vào ngân hàng dùng chung như cũ.
     */
    @Test
    void giuCauCoCoVanDuocNhungNganHangRiengTuConCauKhongCoThiDungChung() throws Exception {
        int examId = readyExam(); // sa lệch Solver (7 so với 5) nên có cờ; mcq sạch cờ
        assertTrue(itemOf(examId, "sa").get("needsReview").asBoolean());
        assertFalse(itemOf(examId, "mcq").get("needsReview").asBoolean());

        int saSlot = slotId(examId, "sa", 1);
        JsonNode sa = json(action(examId, saSlot, "keep").andExpect(status().isOk()));
        assertEquals("kept", sa.get("state").asText());
        assertEquals(teacher, visibleTo(sa), "câu có cờ vào ngân hàng riêng của người Giữ");
        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM exam_slot_action WHERE slot_id = ?", saSlot);
        assertEquals("keep", log.get("action"));
        assertEquals(sa.get("bankItemId").asInt(), log.get("bank_item_id"));

        JsonNode mcq = json(action(examId, slotId(examId, "mcq", 1), "keep").andExpect(status().isOk()));
        assertNull(visibleTo(mcq), "câu không cờ vào ngân hàng dùng chung");
    }

    @Test
    void cauRiengTuCuaGv1KhongVaoDeCuaGv2NhungVaoDeCuaChinhGv1() throws Exception {
        int examId = readyExam();
        JsonNode mcq = json(action(examId, slotId(examId, "mcq", 1), "keep").andExpect(status().isOk()));
        JsonNode sa = json(action(examId, slotId(examId, "sa", 1), "keep").andExpect(status().isOk())); // có cờ: riêng tư
        assertNull(visibleTo(mcq));
        assertEquals(teacher, visibleTo(sa));

        int other = insert("INSERT INTO users (name, email, role) VALUES ('GV2', 'gv2.keep@test', 'teacher')");
        String body = "{\"name\": \"Ma trận GV2\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"cells\": ["
                + "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 1,"
                + " \"topic\": \"Đa thức\", \"content\": \"Đơn thức, đa thức\","
                + " \"learningOutcomes\": {\"easy\": \"Nhận biết được đơn thức, đa thức.\"}},"
                + "{\"part\": \"III\", \"questionType\": \"sa\", \"difficulty\": \"medium\", \"questionCount\": 1,"
                + " \"topic\": \"Tứ giác\", \"content\": \"Tứ giác, hình thang cân\","
                + " \"learningOutcomes\": {\"medium\": \"Tính được góc trong tứ giác.\"}}]}";
        int otherMatrix = json(mockMvc.perform(post("/api/exam-matrices").contentType(MediaType.APPLICATION_JSON)
                .content(body).with(AuthTestSupport.teacher(other))).andExpect(status().isOk())).get("id").asInt();
        int otherExam = json(mockMvc.perform(post("/api/exams/build").contentType(MediaType.APPLICATION_JSON)
                .content("{\"matrixId\": " + otherMatrix + "}").with(AuthTestSupport.teacher(other)))
                .andExpect(status().isOk())).get("examId").asInt();
        assertEquals(mcq.get("bankItemId").asInt(), jdbc.queryForObject("SELECT bank_item_id FROM exam_slot"
                + " WHERE exam_id = ? AND question_type = 'mcq'", Integer.class, otherExam), "câu dùng chung: GV2 lấy được");
        assertNull(jdbc.queryForObject("SELECT bank_item_id FROM exam_slot WHERE exam_id = ? AND question_type = 'sa'",
                Integer.class, otherExam), "câu riêng tư của GV1: GV2 không lấy được (phải sinh mới)");

        int mine = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        assertEquals(sa.get("bankItemId").asInt(), jdbc.queryForObject("SELECT bank_item_id FROM exam_slot"
                + " WHERE exam_id = ? AND question_type = 'sa'", Integer.class, mine), "chính GV1 dùng lại được");
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

    /**
     * M-9, M-17: lỗi khi ghi bank_item (trigger tạm ép lỗi ở bước cuối của Giữ) thì Giữ rollback toàn bộ cho cả ba dạng:
     * teacher_review, ý tf của lần duyệt, trạng thái và mức của câu, nhật ký thao tác và chỗ trong đề không đổi.
     */
    @Test
    void loiKhiGhiNganHangThiGiuCauRollbackToanBo() throws Exception {
        int examId = readyExam();
        dropBankItemBlocker();
        jdbc.execute("CREATE FUNCTION test_block_bank_item() RETURNS trigger AS $$ BEGIN"
                + " RAISE EXCEPTION 'chan ghi bank_item' USING ERRCODE = '23514'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER trg_test_block_bank_item BEFORE INSERT ON bank_item FOR EACH ROW"
                + " EXECUTE FUNCTION test_block_bank_item()");
        try {
            for (String type : List.of("mcq", "sa", "tf")) {
                int slot = slotId(examId, type, 1);
                int questionId = jdbc.queryForObject("SELECT generated_question_id FROM exam_slot WHERE id = ?",
                        Integer.class, slot);
                String statusBefore = jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                        String.class, questionId);
                Object finalDifficultyBefore = jdbc.queryForObject("SELECT final_difficulty FROM generated_question"
                        + " WHERE id = ?", Object.class, questionId);
                List<String> levelsBefore = jdbc.queryForList("SELECT final_difficulty FROM generated_question_statement"
                        + " WHERE question_id = ? ORDER BY position", String.class, questionId);

                try {
                    action(examId, slot, "keep");
                } catch (Exception expected) {
                    // lỗi từ trigger có thể ném ra qua MockMvc hoặc thành phản hồi 500; cả hai đều được
                }

                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class), type);
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review_statement", Integer.class), type);
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM bank_item", Integer.class), type);
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM bank_item_statement", Integer.class), type);
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM exam_slot_action WHERE slot_id = ?",
                        Integer.class, slot), type);
                assertEquals(statusBefore, jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                        String.class, questionId), type);
                assertEquals(finalDifficultyBefore, jdbc.queryForObject("SELECT final_difficulty FROM generated_question"
                        + " WHERE id = ?", Object.class, questionId), type);
                assertEquals(levelsBefore, jdbc.queryForList("SELECT final_difficulty FROM generated_question_statement"
                        + " WHERE question_id = ? ORDER BY position", String.class, questionId), type);
                assertEquals("ready", jdbc.queryForObject("SELECT state FROM exam_slot WHERE id = ?", String.class, slot),
                        type);
                assertNull(jdbc.queryForObject("SELECT bank_item_id FROM exam_slot WHERE id = ?", Integer.class, slot),
                        type);
            }
        } finally {
            dropBankItemBlocker();
        }
        // sau khi gỡ trigger, Giữ lại bình thường (lỗi trước đó không để lại trạng thái dở dang)
        action(examId, slotId(examId, "mcq", 1), "keep").andExpect(status().isOk());
    }

    private void dropBankItemBlocker() {
        jdbc.execute("DROP TRIGGER IF EXISTS trg_test_block_bank_item ON bank_item");
        jdbc.execute("DROP FUNCTION IF EXISTS test_block_bank_item()");
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
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
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

    /**
     * Đo baseline 26/10: 6/27 lần sinh tf có lời giải tự thú nhận gán nhãn theo mẫu. Lần sinh đó bị loại ở bước định dạng
     * (không tốn lượt Solver), lý do vào ghi chú của lần sinh lại.
     */
    @Test
    void loiGiaiTfTuThuNhanBiLoaiFormatFailedVaSinhLaiVoiLyDo() throws Exception {
        generator.admissionTf = 1;
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        processAll();
        int failed = jdbc.queryForObject("SELECT id FROM generated_question WHERE status = 'format_failed'", Integer.class);
        assertEquals("tf", jdbc.queryForObject("SELECT question_type FROM generated_question WHERE id = ?", String.class, failed));
        String errors = jdbc.queryForObject("SELECT errors::text FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'format'", String.class, failed);
        assertTrue(errors.contains("explanation: lời giải nhắc tới mẫu nhãn"), errors);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'answer_check'", Integer.class, failed), "dừng trước Solver, không tốn lượt gọi");

        progress(examId);
        processAll();
        List<String> tfPrompts = generator.prompts.stream().filter(p -> p.contains("ĐÚNG/SAI")).toList();
        assertEquals(2, tfPrompts.size());
        assertTrue(tfPrompts.get(1).contains("Lần sinh trước bị loại vì: explanation: lời giải nhắc tới mẫu nhãn"),
                tfPrompts.get(1));
        progress(examId);
        assertEquals("ready", jdbc.queryForObject("SELECT state FROM exam_slot WHERE exam_id = ? AND question_type = 'tf'",
                String.class, examId));
        assertEquals("qc_passed", jdbc.queryForObject("SELECT q.status FROM exam_slot s JOIN generated_question q ON"
                + " q.id = s.generated_question_id WHERE s.exam_id = ? AND s.question_type = 'tf'", String.class, examId));
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

    // ---------- 3/11 (góp ý giáo viên): chữ đáp án đúng của câu mcq giao trước, chia đều A/B/C/D ----------

    private static final String MCQ_CELL = "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\","
            + " \"questionCount\": %d, \"topic\": \"%s\", \"learningOutcomes\": {\"easy\": \"Nhận biết.\"}}";

    /** Đề chỉ có hai ô mcq ({@code first} + {@code second} câu), đã tạo (job xếp hàng, chưa xử lý). */
    private int mcqExam(int first, int second) throws Exception {
        int mcqMatrix = json(postJson("/api/exam-matrices", "{\"name\": \"Ma trận trắc nghiệm\", \"subjectId\": " + subject
                + ", \"gradeLevel\": 8, \"cells\": [" + MCQ_CELL.formatted(first, "Đa thức") + ", "
                + MCQ_CELL.formatted(second, "Hằng đẳng thức") + "]}").andExpect(status().isOk())).get("id").asInt();
        return build("{\"matrixId\": " + mcqMatrix + "}").get("examId").asInt();
    }

    /** Chữ đáp án đúng đã giao (generation_job.prompt_note của job mới nhất mỗi chỗ mcq), theo thứ tự trong đề. */
    private List<String> assignedLetters(int examId) {
        return jdbc.queryForList("SELECT j.prompt_note FROM generation_job j JOIN exam_slot s ON s.id = j.exam_slot_id"
                + " WHERE s.exam_id = ? AND s.question_type = 'mcq' AND j.id = (SELECT max(id) FROM generation_job"
                + " WHERE exam_slot_id = s.id) ORDER BY s.cell_id, s.slot_no", String.class, examId).stream()
                .map(McqAnswerPlan::fromNote).toList();
    }

    /** Chữ đúng của câu đang dùng ở mỗi chỗ mcq (generated_question.correct_option), theo thứ tự trong đề. */
    private List<String> actualLetters(int examId) {
        return jdbc.queryForList("SELECT g.correct_option FROM exam_slot s JOIN generated_question g"
                + " ON g.id = s.generated_question_id WHERE s.exam_id = ? AND s.question_type = 'mcq'"
                + " ORDER BY s.cell_id, s.slot_no", String.class, examId);
    }

    private static boolean hasRunOfThree(List<String> letters) {
        for (int i = 2; i < letters.size(); i++) {
            if (letters.get(i).equals(letters.get(i - 1)) && letters.get(i).equals(letters.get(i - 2))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void moiChoMcqCuaDeDuocGiaoChuDapAnChiaDeuTheoSeedCuaDe() throws Exception {
        int examId = mcqExam(7, 5);
        List<String> letters = assignedLetters(examId);
        assertEquals(12, letters.size());
        assertEquals(McqAnswerPlan.assign(12, examId), letters, "thứ tự theo ô rồi số chỗ, seed là id đề");
        for (String letter : McqAnswerPlan.LETTERS) {
            assertEquals(3, letters.stream().filter(letter::equals).count(), letter + " trong " + letters);
        }
        assertFalse(hasRunOfThree(letters), letters.toString());

        generator.followMcqLetter = true;
        for (int round = 0; round < 3; round++) {
            processAll();
            progress(examId);
        }
        assertEquals("ready", progress(examId).get("status").asText());
        assertEquals(12, generator.mcqPrompts.size());
        for (String prompt : generator.mcqPrompts) {
            String letter = McqAnswerPlan.fromNote(prompt);
            assertNotNull(letter, prompt);
            assertTrue(prompt.contains("Phương án đúng phải là " + letter + ": đặt đáp án đúng vào phương án " + letter), prompt);
            assertTrue(prompt.contains("Lời giải không được nhắc tới chữ cái của phương án"), prompt);
        }
        assertEquals(letters, actualLetters(examId), "câu sinh ra theo đúng chữ đã giao");
        assertEquals(0, count("SELECT count(*) FROM quality_check_result WHERE check_type = 'format'"
                + " AND warnings::text LIKE '%khác chữ đã giao%'"), "đúng chữ thì không có cảnh báo");
    }

    @Test
    void cauNganHangDeuADuocDemTruocChoSinhChiaDeuPhanConLai() throws Exception {
        for (int i = 0; i < 5; i++) {
            int bank = bankMcq("Đa thức", "easy", "Câu ngân hàng số " + i);
            jdbc.update("UPDATE bank_item SET correct_option = 'A' WHERE id = ?", bank);
        }
        int examId = mcqExam(5, 7);
        assertEquals(5, count("SELECT count(*) FROM exam_slot WHERE exam_id = ? AND bank_item_id IS NOT NULL", examId));
        List<String> letters = assignedLetters(examId); // chỉ 7 chỗ sinh có job
        assertEquals(7, letters.size());
        Map<String, Long> counts = new java.util.HashMap<>();
        letters.forEach(l -> counts.merge(l, 1L, Long::sum));
        assertFalse(counts.containsKey("A"), "5 câu ngân hàng đã đủ A: " + letters);
        long min = Math.min(counts.getOrDefault("B", 0L), Math.min(counts.getOrDefault("C", 0L), counts.getOrDefault("D", 0L)));
        long max = Math.max(counts.getOrDefault("B", 0L), Math.max(counts.getOrDefault("C", 0L), counts.getOrDefault("D", 0L)));
        assertTrue(max - min <= 1, "B/C/D chia đều nhất có thể (3,2,2): " + letters);
        // cùng kết quả với tính trực tiếp: thứ tự đề = 5 câu ngân hàng (A) rồi 7 chỗ sinh
        List<String> fixed = new ArrayList<>(java.util.Arrays.asList("A", "A", "A", "A", "A"));
        for (int i = 0; i < 7; i++) {
            fixed.add(null);
        }
        assertEquals(McqAnswerPlan.assignAround(fixed, examId).subList(5, 12), letters);

        // Đổi một chỗ sinh: chữ mới là chữ đang thiếu so với 4 chữ còn lại (giữ cân bằng toàn đề, tính cả câu ngân hàng)
        generator.followMcqLetter = true;
        for (int round = 0; round < 3; round++) {
            processAll();
            progress(examId);
        }
        int slot = jdbc.queryForObject("SELECT s.id FROM exam_slot s WHERE s.exam_id = ? AND s.bank_item_id IS NULL"
                + " ORDER BY s.cell_id, s.slot_no LIMIT 1", Integer.class, examId);
        List<String> others = actualLetters(examId).subList(1, 7); // 6 chỗ sinh còn lại; 5 câu ngân hàng đều A
        json(action(examId, slot, "replace").andExpect(status().isOk()));
        String fresh = McqAnswerPlan.fromNote(latestNote(slot));
        assertNotEquals("A", fresh, "A đã nhiều nhất toàn đề");
        long freshCount = others.stream().filter(fresh::equals).count();
        for (String letter : List.of("B", "C", "D")) {
            assertTrue(freshCount <= others.stream().filter(letter::equals).count(),
                    "chọn chữ đang ít nhất trong B/C/D: " + fresh + " so với " + letter + " trong " + others);
        }
    }

    @Test
    void chuDungKhacChuDaGiaoLaCanhBaoTrongVetQcKhongBatCoKhongLoaiCau() throws Exception {
        int examId = mcqExam(4, 4);
        List<String> letters = assignedLetters(examId);
        // FakeGenerator mặc định luôn trả B: mọi chỗ được giao chữ khác B đều lệch chữ.
        processAll();
        progress(examId);
        assertEquals("ready", progress(examId).get("status").asText(), "lệch chữ không loại câu");
        long deviated = letters.stream().filter(l -> !l.equals("B")).count();
        assertEquals(deviated, count("SELECT count(*) FROM quality_check_result WHERE check_type = 'format'"
                + " AND warnings::text LIKE '%khác chữ đã giao%'"));
        int slot = jdbc.queryForObject("SELECT s.id FROM exam_slot s WHERE s.exam_id = ? AND s.question_type = 'mcq'"
                + " ORDER BY s.cell_id, s.slot_no LIMIT 1", Integer.class, examId);
        String firstAssigned = letters.get(0);
        JsonNode trace = json(perform(get("/api/exams/" + examId + "/slots/" + slot + "/qc-trace"))
                .andExpect(status().isOk())).get("lineages").get(0).get("versions").get(0);
        List<String> warnings = texts(trace.get("qc").get("format").get("warnings"));
        List<String> reasons = texts(trace.get("reviewReasons"));
        if (firstAssigned.equals("B")) {
            assertTrue(warnings.stream().noneMatch(w -> w.contains("khác chữ đã giao")), warnings.toString());
        } else {
            assertTrue(warnings.contains("correct_option: khác chữ đã giao " + firstAssigned + " (nhận B)"),
                    "vết QC cho thấy mức tuân thủ chữ: " + warnings);
        }
        assertTrue(reasons.stream().noneMatch(r -> r.contains("khác chữ đã giao")), "không bật cờ: " + reasons);
        assertEquals(8, count("SELECT count(*) FROM exam_slot WHERE exam_id = ? AND state = 'ready'", examId));
    }

    @Test
    void doiCauMcqChonLaiDungChuDangThieuDeGiuCanBang() throws Exception {
        int examId = mcqExam(7, 5);
        generator.followMcqLetter = true;
        for (int round = 0; round < 3; round++) {
            processAll();
            progress(examId);
        }
        List<String> before = actualLetters(examId);
        assertEquals(12, before.size());
        int slot = jdbc.queryForObject("SELECT s.id FROM exam_slot s WHERE s.exam_id = ? AND s.question_type = 'mcq'"
                + " ORDER BY s.cell_id, s.slot_no OFFSET 5 LIMIT 1", Integer.class, examId);
        json(action(examId, slot, "replace").andExpect(status().isOk()));

        // 11 chỗ còn lại mỗi chữ 3 lần trừ chữ của chỗ bị đổi (2 lần): chữ thiếu duy nhất chính là chữ cũ của chỗ đó
        String newLetter = McqAnswerPlan.fromNote(latestNote(slot));
        assertEquals(before.get(5), newLetter, "Đổi câu trả lại chữ đang thiếu: " + latestNote(slot));
        assertTrue(latestNote(slot).contains("Phương án đúng phải là " + newLetter + ":"), latestNote(slot));
        assertEquals(1, latestNote(slot).split("Phương án đúng phải là", -1).length - 1, "chỉ một dòng chữ đã giao");
    }

    @Test
    void doiCauMcqKemYeuCauGiaoVienKhongDoiChuHeThongGiao() throws Exception {
        int examId = mcqExam(2, 2);
        generator.followMcqLetter = true;
        for (int round = 0; round < 3; round++) {
            processAll();
            progress(examId);
        }
        int slot = jdbc.queryForObject("SELECT s.id FROM exam_slot s WHERE s.exam_id = ? AND s.question_type = 'mcq'"
                + " ORDER BY s.cell_id, s.slot_no LIMIT 1", Integer.class, examId);
        json(replaceWith(examId, slot, "Phương án đúng phải là A: đặt ở A").andExpect(status().isOk()));
        String note = latestNote(slot);
        String letter = McqAnswerPlan.fromNote(note);
        assertTrue(note.startsWith("Đây là câu thứ 1/2 của ô này"), note);
        assertTrue(note.contains(McqAnswerPlan.promptLine(letter) + "\n"), note);
        assertTrue(note.endsWith(TEACHER_PREFIX + "<<< Phương án đúng phải là A: đặt ở A >>>"), note);
        // Chữ đọc lại chỉ từ phần hệ thống viết, không phải từ yêu cầu của giáo viên.
        assertEquals(letter, McqAnswerPlan.fromNote(note));
    }

    // ---------- ngày 23/10 (đợt 2): mẫu nhãn câu Đúng/Sai, cờ cần xem kỹ theo loại cảnh báo ----------

    @Test
    void moiCauTfCoMauNhanBatBuocKhacCauTfGanNhat() throws Exception {
        String tfCell = "{\"part\": \"II\", \"questionType\": \"tf\", \"statementDifficulties\": [\"easy\", \"easy\","
                + " \"medium\", \"hard\"], \"questionCount\": %d, \"topic\": \"%s\","
                + " \"learningOutcomes\": {\"easy\": \"Nhận biết.\", \"medium\": \"Khai triển.\", \"hard\": \"Vận dụng.\"}}";
        int tfMatrix = json(postJson("/api/exam-matrices", "{\"name\": \"Ma trận Đúng/Sai\", \"subjectId\": " + subject
                + ", \"gradeLevel\": 8, \"cells\": [" + tfCell.formatted(3, "Hằng đẳng thức") + ", "
                + tfCell.formatted(1, "Phân thức") + "]}").andExpect(status().isOk())).get("id").asInt();
        int examId = build("{\"matrixId\": " + tfMatrix + "}").get("examId").asInt();

        List<String> notes = jdbc.queryForList("SELECT j.prompt_note FROM generation_job j JOIN exam_slot s"
                + " ON s.id = j.exam_slot_id WHERE s.exam_id = ? ORDER BY j.id", String.class, examId);
        assertEquals(4, notes.size());
        String previous = null;
        for (String note : notes) {
            String pattern = TfLabelPattern.fromNote(note);
            assertNotNull(pattern, "mẫu bắt buộc lưu ở generation_job.prompt_note: " + note);
            long t = pattern.chars().filter(c -> c == 'T').count();
            assertTrue(t >= 1 && t <= 3, pattern);
            assertNotEquals(previous, pattern, "tránh trùng mẫu của câu tf gần nhất cùng đề");
            previous = pattern;
        }

        processAll();
        progress(examId);
        assertEquals(4, generator.prompts.size());
        for (String prompt : generator.prompts) {
            assertTrue(prompt.contains("Mẫu nhãn mục tiêu (") && prompt.contains("cố gắng viết ý a "), prompt);
            assertTrue(prompt.contains("Nhãn phải khớp tính đúng/sai THẬT của khẳng định"), prompt);
            assertFalse(prompt.contains("Mẫu nhãn bắt buộc"), prompt);
            assertFalse(prompt.contains("Không bắt buộc số ý Đúng hay số ý Sai"), prompt);
            assertTrue(prompt.contains("ý d (Vận dụng) phải cần suy luận thêm"), prompt);
        }
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals("ready", view.get("buildStatus").asText());
        assertEquals(0, view.get("needsReviewCount").asInt(), "nhãn khớp mẫu thì không gắn cờ");
    }

    /**
     * Từ 26/10 mẫu nhãn là mục tiêu, lệch mẫu vì tính đúng là hợp lệ: câu không bị loại, KHÔNG bật "cần xem kỹ", nhưng cảnh báo
     * vẫn lưu ở warnings của dòng format và hiện trong vết QC để thống kê mức tuân thủ mẫu.
     */
    @Test
    void nhanKhacMauMucTieuChiCanhBaoTrongVetQcKhongBatCanXemKyVaKhongLoaiCau() throws Exception {
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        String required = tfPattern(examId);
        String other = TfLabelPattern.ALL.stream().filter(p -> !p.equals(required)).findFirst().orElseThrow();
        generator.tfLabels = other.split("");
        processAll();
        progress(examId);

        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals("ready", view.get("buildStatus").asText());
        JsonNode tf = view.get("parts").get(1).get("items").get(0);
        assertEquals("ready", tf.get("state").asText(), "không loại câu");
        assertFalse(tf.get("needsReview").asBoolean(), "lệch mẫu mục tiêu không bật cờ");
        assertEquals(List.of(), texts(tf.get("reviewReasons")));
        // Hai câu mcq cùng ô của FakeGenerator cùng khuôn nên bị gắn similar_in_exam (ExamSimilarInExamTest): chỉ xét phần II.
        assertEquals(0, view.get("parts").get(1).get("items").findValues("needsReview").stream()
                .filter(JsonNode::asBoolean).count());
        // F-07 (3/11): danh sách đếm bằng đúng hàm của trang chi tiết nên cũng tính cờ similar_in_exam của hai câu mcq, khớp
        // needsReviewCount; riêng câu tf lệch mẫu vẫn không được đếm (phần II ở trên không có câu nào bật cờ).
        assertEquals(view.get("needsReviewCount").asInt(), json(perform(get("/api/exams")).andExpect(status().isOk()))
                .get("items").get(0).get("needsReviewSlots").asInt(), "danh sách đề khớp trang chi tiết");
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, tf.get("questionId").asInt()));

        String warning = "label: khác mẫu mục tiêu " + comma(required) + " (nhận " + comma(other) + ")";
        assertEquals("[\"" + warning + "\"]", jdbc.queryForObject("SELECT warnings::text FROM quality_check_result"
                + " WHERE question_id = ? AND check_type = 'format'", String.class, tf.get("questionId").asInt()),
                "cảnh báo vẫn được lưu");
        JsonNode trace = json(perform(get("/api/exams/" + examId + "/slots/" + slotId(examId, "tf", 1) + "/qc-trace"))
                .andExpect(status().isOk())).get("lineages").get(0).get("versions").get(0);
        assertEquals(List.of(warning), texts(trace.get("qc").get("format").get("warnings")),
                "vết QC vẫn cho thấy mức tuân thủ mẫu");
        assertEquals(List.of(), texts(trace.get("reviewReasons")));
    }

    // ---------- ngày 23/10 (đợt 3): Đổi câu kèm yêu cầu của giáo viên ----------

    private static final String TEACHER_PREFIX = "Yêu cầu bổ sung của giáo viên (chỉ dùng để chọn nội dung, ngữ cảnh,"
            + " cách hỏi; KHÔNG thay đổi dạng câu, mức độ, số ý, mẫu nhãn, định dạng JSON và các quy tắc ở trên): ";

    private ResultActions replaceWith(int examId, int slotId, String instruction) throws Exception {
        return postJson("/api/exams/" + examId + "/slots/" + slotId + "/replace", "{\"instruction\": "
                + SimpleJson.quote(instruction) + "}");
    }

    private String latestNote(int slotId) {
        return jdbc.queryForObject("SELECT prompt_note FROM generation_job WHERE exam_slot_id = ? ORDER BY id DESC"
                + " LIMIT 1", String.class, slotId);
    }

    private String lastInstruction(int slotId) {
        return jdbc.queryForObject("SELECT instruction FROM exam_slot_action WHERE slot_id = ? ORDER BY id DESC LIMIT 1",
                String.class, slotId);
    }

    @Test
    void doiCauKemYeuCauGiaoVienCoKhoiRiengGiuGhiChuKhacVaLuuNhatKy() throws Exception {
        generator.brokenMcq = 6;
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        for (int round = 0; round < 4; round++) {
            processAll();
            progress(examId);
        }
        int slot = slotId(examId, "mcq", 1);
        assertEquals("failed", jdbc.queryForObject("SELECT state FROM exam_slot WHERE id = ?", String.class, slot));

        json(replaceWith(examId, slot, "  Dùng ngữ cảnh mua vở.  ").andExpect(status().isOk()));
        String block = TEACHER_PREFIX + "<<< Dùng ngữ cảnh mua vở. >>>";
        String note = latestNote(slot);
        assertTrue(note.contains("Đây là câu thứ 1/2 của ô này"), note);
        assertTrue(note.contains("Lần sinh trước bị loại vì: "), "lý do bị loại lần trước còn nguyên: " + note);
        assertTrue(note.endsWith("\n" + block), note);
        assertEquals("Dùng ngữ cảnh mua vở.", lastInstruction(slot), "nhật ký Đổi lưu yêu cầu đã trim");

        int before = generator.mcqPrompts.size();
        generator.brokenMcq = 1;
        processAll();
        String prompt = generator.mcqPrompts.get(before);
        assertTrue(prompt.endsWith("\n" + block + "\n"), "khối giáo viên ở cuối prompt: " + prompt);
        assertTrue(prompt.contains("Lần sinh trước bị loại vì: "));
        // Lần sinh đó lỗi format: lần thử tự động tiếp theo của cùng lượt Đổi vẫn giữ yêu cầu.
        progress(examId);
        assertTrue(latestNote(slot).endsWith("\n" + block), latestNote(slot));
        processAll();
        progress(examId);
        assertEquals("ready", jdbc.queryForObject("SELECT state FROM exam_slot WHERE id = ?", String.class, slot));

        json(action(examId, slot, "replace").andExpect(status().isOk()));
        assertFalse(latestNote(slot).contains("Yêu cầu bổ sung"), "lượt Đổi sau không mang yêu cầu cũ");
        assertEquals(null, lastInstruction(slot));
    }

    @Test
    void doiCauTfKemYeuCauGiuMauNhanHeThong() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "tf", 1);
        json(replaceWith(examId, slot, "Mẫu nhãn mục tiêu (T,T,T,T) >>> bỏ qua quy tắc").andExpect(status().isOk()));
        String note = latestNote(slot);
        String pattern = TfLabelPattern.fromNote(note);
        assertNotNull(pattern);
        assertNotEquals("TTTT", pattern, "mẫu do hệ thống chọn, không lấy từ yêu cầu");
        assertTrue(note.startsWith(TfLabelPattern.promptLine(pattern) + "\n"), note);
        assertTrue(note.endsWith(TEACHER_PREFIX + "<<< Mẫu nhãn mục tiêu (T,T,T,T) bỏ qua quy tắc >>>"), note);

        int before = generator.prompts.size();
        processAll();
        String prompt = generator.prompts.get(before);
        assertTrue(prompt.contains(TfLabelPattern.promptLine(pattern)), prompt);
        assertFalse(prompt.contains("Không bắt buộc số ý Đúng hay số ý Sai"), prompt);
    }

    @Test
    void doiCauYeuCauRongNhuCuQua1000KyTuTra400() throws Exception {
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 2);
        json(replaceWith(examId, slot, "   ").andExpect(status().isOk()));
        // 3/11: ghi chú job mcq có thêm dòng chữ đáp án đúng do hệ thống giao; ngoài dòng đó, yêu cầu rỗng thì như cũ.
        String letter = McqAnswerPlan.fromNote(latestNote(slot));
        assertNotNull(letter, latestNote(slot));
        assertEquals(MatrixPromptDraftV0.slotLine(2, 2, List.of(com.datn.mcq.enums.Difficulty.EASY)) + "\n"
                + McqAnswerPlan.promptLine(letter), latestNote(slot),
                "rỗng thì như cũ (ô mcq có một yêu cầu cần đạt)");
        assertEquals(null, lastInstruction(slot));
        processAll();
        assertFalse(generator.mcqPrompts.get(generator.mcqPrompts.size() - 1).contains("Yêu cầu bổ sung"));

        progress(examId);
        int actions = jdbc.queryForObject("SELECT count(*) FROM exam_slot_action", Integer.class);
        int jobs = jdbc.queryForObject("SELECT count(*) FROM generation_job", Integer.class);
        Map<String, Object> slotBefore = jdbc.queryForMap("SELECT state, generated_question_id, attempts FROM exam_slot"
                + " WHERE id = ?", slot);
        // PR B (12/11): giới hạn Đổi câu tăng 500 -> 1000, bằng giới hạn của đề và ô.
        JsonNode error = json(replaceWith(examId, slot, " " + "x".repeat(1001) + " ").andExpect(status().isBadRequest()));
        assertTrue(error.get("error").asText().contains("1000"), error.toString());
        assertEquals(actions, jdbc.queryForObject("SELECT count(*) FROM exam_slot_action", Integer.class));
        assertEquals(jobs, jdbc.queryForObject("SELECT count(*) FROM generation_job", Integer.class));
        assertEquals(slotBefore, jdbc.queryForMap("SELECT state, generated_question_id, attempts FROM exam_slot"
                + " WHERE id = ?", slot), "chỗ giữ nguyên");
        json(replaceWith(examId, slot, " " + "y".repeat(1000) + " ").andExpect(status().isOk()));
        assertEquals("y".repeat(1000), lastInstruction(slot), "đúng 1000 ký tự sau trim thì nhận và lưu đủ");

        // V9, nâng lên 1000 ở V28: cột instruction chỉ cho dòng 'replace', 1-1000 ký tự.
        jdbc.update("INSERT INTO exam_slot_action (slot_id, action, instruction) VALUES (?, 'replace', repeat('z', 1000))",
                slot);
        for (String bad : List.of("INSERT INTO exam_slot_action (slot_id, action, instruction) VALUES (?, 'keep', 'x')",
                "INSERT INTO exam_slot_action (slot_id, action, instruction) VALUES (?, 'replace', '')",
                "INSERT INTO exam_slot_action (slot_id, action, instruction) VALUES (?, 'replace', repeat('z', 1001))")) {
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(bad, slot), bad);
        }
    }

    // ---------- ngày 23/10 (đợt 3): xuất đề ra Word ----------

    @Test
    void xuatDeRaWordTenFileTheoMaDe() throws Exception {
        generator.brokenMcq = 6;
        int examId = readyExam();
        progress(examId);
        String code = jdbc.queryForObject("SELECT exam_code FROM exam WHERE id = ?", String.class, examId);
        var response = perform(get("/api/exams/" + examId + "/export/docx")).andReturn().getResponse();
        if (PandocTestSupport.find().isPresent()) {
            assertEquals(200, response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
            assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    response.getContentType());
            String disposition = response.getHeader("Content-Disposition");
            assertTrue(disposition.startsWith("attachment") && disposition.contains("filename=\"" + code + ".docx\""),
                    disposition);
            String xml = PandocTestSupport.documentXml(response.getContentAsByteArray());
            assertTrue(xml.contains("m:oMath"), "công thức là Equation gốc của Word");
            assertFalse(xml.contains("Equation.DSMT4"), "mặc định không có đối tượng MathType");
            assertNull(response.getHeader("X-Equation-Kept-Omml"), "header chỉ có khi xuất MathType");
            assertNull(response.getHeader("X-Equation-Scaled"), "header chỉ có khi xuất MathType");
            assertNull(response.getHeader("X-Equation-Failed"), "header chỉ có khi xuất MathType");
            for (String part : List.of("PHẦN I. TRẮC NGHIỆM NHIỀU LỰA CHỌN", "ĐÁP ÁN", "LỜI GIẢI",
                    "Chưa có câu – cần sinh lại")) {
                assertTrue(xml.contains(part), "docx thiếu: " + part);
            }
            // 2/11: đầu đề (bảng 2 cột không viền) và mã đề hiển thị 101; mã hệ thống MT... chỉ ở tên file, không in ra file.
            for (String part : List.of("UBND PHƯỜNG ……………", "TRƯỜNG THCS ……………", "ĐỀ KIỂM TRA ĐỀ THỬ MA TRẬN",
                    "Môn: Toán, Lớp: 8.", "(Thời gian làm bài …… phút không kể thời gian giao đề)", "Mã đề",
                    "Điểm bài thi", "Mã phách", "<w:tbl>",
                    "ĐÁP ÁN VÀ HƯỚNG DẪN CHẤM – MÃ ĐỀ 101", "LỜI GIẢI CHI TIẾT", "------ HẾT ------")) {
                assertTrue(xml.contains(part), "đầu đề docx thiếu: " + part);
            }
            assertTrue(xml.contains("<w:top w:val=\"nil\"/>"), "bảng đầu đề không viền");
            assertFalse(xml.contains("MT2"), "mã hệ thống không in ra file");
            assertFalse(xml.contains(code), "mã hệ thống không in ra file");
        } else {
            assertEquals(503, response.getStatus());
            assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("pandoc"));
        }
        perform(get("/api/exams/999999/export/docx")).andExpect(status().isNotFound());
    }

    /** durationMinutes (5–180) điền dòng "Thời gian làm bài"; không có thì "……"; ngoài khoảng hoặc không phải số thì 400. */
    @Test
    void xuatDeRaWordThamSoThoiGianLamBai() throws Exception {
        int examId = readyExam();
        progress(examId);
        String base = "/api/exams/" + examId + "/export/docx";
        if (PandocTestSupport.find().isPresent()) {
            var ok = perform(get(base + "?durationMinutes=45")).andReturn().getResponse();
            assertEquals(200, ok.getStatus(), ok.getContentAsString(StandardCharsets.UTF_8));
            assertTrue(PandocTestSupport.documentXml(ok.getContentAsByteArray())
                    .contains("(Thời gian làm bài 45 phút không kể thời gian giao đề)"));
            var both = perform(get(base + "?equation=mathtype&durationMinutes=180")).andReturn().getResponse();
            assertEquals(200, both.getStatus());
            assertTrue(PandocTestSupport.documentXml(both.getContentAsByteArray())
                    .contains("(Thời gian làm bài 180 phút không kể thời gian giao đề)"));
        }
        for (String bad : List.of("4", "181", "0", "-5", "abc", "45.5")) {
            var response = perform(get(base + "?durationMinutes=" + bad)).andReturn().getResponse();
            assertEquals(400, response.getStatus(), bad);
            assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("180"), "thông báo nêu khoảng hợp lệ");
        }
    }

    /** ?equation=mathtype: công thức thành đối tượng MathType OLE; header báo số công thức giữ Equation của Word. */
    @Test
    void xuatDeRaWordCongThucMathType() throws Exception {
        int examId = readyExam();
        progress(examId);
        var response = perform(get("/api/exams/" + examId + "/export/docx?equation=mathtype")).andReturn()
                .getResponse();
        if (PandocTestSupport.find().isPresent()) {
            assertEquals(200, response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
            String xml = PandocTestSupport.documentXml(response.getContentAsByteArray());
            assertTrue(xml.contains("ProgID=\"Equation.DSMT4\""), "công thức là đối tượng MathType");
            String kept = response.getHeader("X-Equation-Kept-Omml");
            assertNotNull(kept, "header số công thức giữ Equation");
            assertEquals(xml.split("<m:oMath[ >]", -1).length - 1, Integer.parseInt(kept),
                    "header khớp số Equation còn lại trong file");
            assertNotNull(response.getHeader("X-Equation-Scaled"), "header số công thức bị thu nhỏ cho vừa lề");
            assertEquals(0, Integer.parseInt(response.getHeader("X-Equation-Scaled")),
                    "đề thử toàn công thức ngắn: không công thức nào bị thu nhỏ");
            assertEquals("0", response.getHeader("X-Equation-Failed"), "11/11: header số công thức pandoc không đọc được");
            assertNull(response.getHeader("X-Equation-Failed-At"), "không có công thức hỏng thì không có vị trí");
            assertTrue(xml.contains("PHẦN I. TRẮC NGHIỆM NHIỀU LỰA CHỌN") && xml.contains("ĐÁP ÁN"));
        } else {
            assertEquals(503, response.getStatus());
        }
        // không phân biệt hoa thường
        perform(get("/api/exams/" + examId + "/export/docx?equation=OMML"))
                .andExpect(status().is(PandocTestSupport.find().isPresent() ? 200 : 503));
        var bad = perform(get("/api/exams/" + examId + "/export/docx?equation=abc")).andReturn().getResponse();
        assertEquals(400, bad.getStatus());
        assertTrue(bad.getContentAsString(StandardCharsets.UTF_8).contains("omml"), "thông báo nêu giá trị hợp lệ");
        perform(get("/api/exams/999999/export/docx?equation=mathtype")).andExpect(status().isNotFound());
    }

    /** Giữ câu tf có nhãn LLM khác mẫu bắt buộc: ngân hàng lưu nhãn LLM (khớp lời giải), không lấy từ mẫu. */
    @Test
    void giuCauTfNhanLechMauLuuNhanLlmVaoNganHang() throws Exception {
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        String required = tfPattern(examId);
        String llm = TfLabelPattern.ALL.stream().filter(p -> !p.equals(required)).findFirst().orElseThrow();
        generator.tfLabels = llm.split("");
        SOLVER.saJudge = "7";
        for (int round = 0; round < 3; round++) {
            processAll();
            progress(examId);
        }
        JsonNode tf = json(action(examId, slotId(examId, "tf", 1), "keep").andExpect(status().isOk()));
        assertEquals("kept", tf.get("state").asText());
        List<String> expected = List.of(llm.split(""));
        assertEquals(expected, llmLabels(tf.get("questionId").asInt()));
        assertEquals(expected, jdbc.queryForList("SELECT label FROM bank_item_statement WHERE bank_item_id = ?"
                + " ORDER BY position", String.class, tf.get("bankItemId").asInt()), "nhãn LLM, không phải mẫu");
        assertEquals(expected, jdbc.queryForList("SELECT final_label FROM teacher_review_statement ORDER BY position",
                String.class));
        assertNotEquals(List.of(required.split("")), expected);
    }

    /**
     * Đợt A bổ sung 4 (đề 2, chỗ 20), cập nhật 2/11 (đề 10 Phần I câu 2): lời giải có biểu thức LaTeX ngoài $...$ nay được
     * tự bọc $...$ khi giải mã (cảnh báo "đã tự bọc $", bật cờ "cần xem kỹ"); câu giữ nguyên qua Giữ và xem đề.
     */
    @Test
    void loiGiaiCoLatexNgoaiCongThucDuocBocVaGiuNguyenSauKhiGiu() throws Exception {
        generator.rawLatexExplanationMcq = 1;
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        String expected = "Ta có $\\frac{5x + 3}{y}$ khi $y \\neq 0$.";
        int questionId = jdbc.queryForObject("SELECT generated_question_id FROM exam_slot WHERE id = ?", Integer.class,
                slot);
        assertEquals(expected, jdbc.queryForObject("SELECT explanation FROM generated_question WHERE id = ?",
                String.class, questionId));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT passed FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'format'", Boolean.class, questionId), "bọc $ xong thì không còn lỗi format");
        assertTrue(jdbc.queryForObject("SELECT warnings::text FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'format'", String.class, questionId)
                .contains("explanation: đã tự bọc $ quanh công thức LaTeX nằm ngoài $"), "ghi cảnh báo mã mới");

        JsonNode kept = json(action(examId, slot, "keep").andExpect(status().isOk()));
        assertEquals(expected, kept.get("explanation").asText(), "Giữ trả lời giải");
        assertEquals(expected, jdbc.queryForObject("SELECT explanation FROM bank_item WHERE id = ?", String.class,
                kept.get("bankItemId").asInt()), "bank_item giữ lời giải");
        JsonNode item = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()))
                .get("parts").get(0).get("items").get(0);
        assertEquals(kept.get("bankItemId").asInt(), item.get("bankItemId").asInt(), "câu đã Giữ hiển thị bản ngân hàng");
        assertEquals(expected, item.get("explanation").asText(), "xem đề trả lời giải của câu đã Giữ");
    }

    /**
     * F-12 phần 1: vết QC xếp cảnh báo tự sửa bằng đúng hàm của QcSummaryService (một nguồn duy nhất): "đã tự bọc $" là
     * việc tự sửa nên nằm ở autoRepairs, không lẫn vào warnings (cảnh báo khác).
     */
    @Test
    void vetQcXepCanhBaoTuBocVaoAutoRepairsCungNguonVoiLyDoCanXemKy() throws Exception {
        generator.followMcqLetter = true; // đúng chữ đã giao thì không có cảnh báo lệch chữ trong nhóm cảnh báo khác
        generator.rawLatexExplanationMcq = 1;
        int examId = readyExam();
        int slot = slotId(examId, "mcq", 1);
        JsonNode format = json(perform(get("/api/exams/" + examId + "/slots/" + slot + "/qc-trace"))
                .andExpect(status().isOk())).get("lineages").get(0).get("versions").get(0).get("qc").get("format");
        List<String> repairs = texts(format.get("autoRepairs"));
        assertTrue(repairs.stream().anyMatch(r -> r.startsWith("explanation: đã tự bọc $ quanh")), repairs.toString());
        assertEquals(List.of(), texts(format.get("warnings")),
                "cảnh báo tự bọc $ không bật cờ nên không được xếp vào nhóm cảnh báo khác");
    }

    /** 2/11: lời giải LaTeX ngoài $ mà không bọc được (lệnh lạ) là lỗi định dạng của đầu ra LLM: câu bị loại và sinh lại. */
    @Test
    void loiGiaiLatexNgoaiDolaKhongBocDuocThiSinhLai() throws Exception {
        generator.unwrappableExplanationMcq = 1;
        int examId = readyExam();
        assertEquals("ready", progress(examId).get("status").asText());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM generated_question WHERE status = 'format_failed'",
                Integer.class), "đúng một câu bị loại");
        String errors = jdbc.queryForObject("SELECT r.errors::text FROM quality_check_result r JOIN generated_question g"
                + " ON g.id = r.question_id WHERE g.status = 'format_failed' AND r.check_type = 'format'", String.class);
        assertTrue(errors.contains("explanation: Có lệnh LaTeX nằm ngoài"), errors);
    }

    /** Lý do "cần xem kỹ" của câu mcq đầu tiên của đề có lý do chứa {@code part}. */
    private List<String> reasonsContaining(int examId, String part) throws Exception {
        JsonNode items = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()))
                .get("parts").get(0).get("items");
        for (JsonNode item : items) {
            List<String> reasons = new ArrayList<>();
            item.get("reviewReasons").forEach(r -> reasons.add(r.asText()));
            if (reasons.stream().anyMatch(r -> r.contains(part))) {
                assertTrue(item.get("needsReview").asBoolean());
                return reasons;
            }
        }
        throw new AssertionError("không câu mcq nào có lý do chứa \"" + part + "\"");
    }

    /**
     * 2/11 (đề 11, câu 190 và 192 qua QC không cờ): cảnh báo của bước format ngoài nhóm answerWarnings (lệnh LaTeX không hỗ
     * trợ trong $...$, lời giải có thể còn nháp, từ tiếng Anh) trước đây KHÔNG được lưu (QcOrchestrator chỉ lưu answerWarnings)
     * nên không bao giờ thành lý do "cần xem kỹ". Nay lưu cả hai nhóm.
     */
    @Test
    void lenhLatexKhongHoTroTrongCongThucBatCanXemKy() throws Exception {
        generator.nextMcqQuestion = "Cho $x^\\\\toRadians$ thì bậc là:";
        int examId = readyExam();
        List<String> reasons = reasonsContaining(examId, "toRadians");
        assertTrue(reasons.stream().anyMatch(r -> r.startsWith("warning: question_text: Lệnh \\toRadians không hỗ trợ")),
                reasons.toString());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM quality_check_result WHERE check_type = 'format'"
                + " AND warnings::text LIKE '%toRadians%'", Integer.class), "cảnh báo được lưu ở cột warnings");
    }

    @Test
    void loiGiaiCoTinhLaiVaTuTiengAnhBatCanXemKy() throws Exception {
        generator.nextMcqExplanation = "Thử tính lại bằng cách khác: $1 + 1 = 2$.";
        int examId = readyExam();
        assertTrue(reasonsContaining(examId, "explanation_draft_suspect").contains("explanation_draft_suspect"));

        generator.nextMcqExplanation = "Kết quả is đúng.";
        int second = readyExam();
        assertTrue(reasonsContaining(second, "tiếng Anh")
                .contains("warning: explanation: có từ tiếng Anh ngoài công thức (is)"));
    }

    @Test
    void canhBaoTuSuaChacChanKhongBatCanXemKyNhungVanDuocLuu() throws Exception {
        generator.escapeMcq = 1;
        generator.unclosedMcq = 1;
        int examId = build("{\"matrixId\": " + matrixId + "}").get("examId").asInt();
        processAll();
        progress(examId);

        JsonNode items = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()))
                .get("parts").get(0).get("items");
        int checked = 0;
        for (JsonNode item : items) {
            String warnings = jdbc.queryForObject("SELECT warnings::text FROM quality_check_result WHERE question_id = ?"
                    + " AND check_type = 'format'", String.class, item.get("questionId").asInt());
            // Hai câu mcq của FakeGenerator trong cùng một ô cùng khuôn sau khi che số (lớp 2 chống câu cùng khuôn gắn
            // similar_in_exam, ExamSimilarInExamTest); test này chỉ xét lý do của bước format.
            List<String> reasons = texts(item.get("reviewReasons")).stream()
                    .filter(r -> !r.startsWith("similar_in_exam: ")).toList();
            if (warnings != null && warnings.contains("json: đã sửa 1 escape LaTeX (")) {
                assertTrue(item.get("explanation").asText().contains("$90^\\circ$"), "giữ nghĩa LaTeX");
                assertEquals(List.of(), reasons, "sửa escape không bật cần xem kỹ, vẫn lưu cảnh báo nhưng không là lý do");
                checked++;
            } else if (warnings != null && warnings.contains("option_a: đã tự đóng $")) {
                assertEquals("$" + item.get("optionB").asText().replace("$", "").transform(s -> Integer.parseInt(s) + 1)
                        + "$", item.get("optionA").asText());
                assertEquals(List.of(), reasons, "tự đóng $ là hệ thống tự sửa chắc chắn: lưu cảnh báo, không bật cờ (2/11)");
                checked++;
            }
        }
        assertEquals(2, checked, "mỗi loại cảnh báo một câu");
    }
}
