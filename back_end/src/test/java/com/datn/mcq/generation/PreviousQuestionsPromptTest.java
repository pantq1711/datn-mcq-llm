package com.datn.mcq.generation;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.service.PreviousQuestionLookup;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.McqProductionSchema;
import com.datn.mcq.llm.prompt.PreviousQuestion;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
import com.datn.mcq.llm.prompt.TfPromptDraftV0;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Độ đa dạng (quyết định Q1-Q4 ngày 22/10): prompt sinh câu sa/tf gửi model kèm tối đa 10 câu gần nhất cùng template
 * và cùng dạng câu (mọi run, mọi trạng thái, chỉ câu giải mã được), dùng chung cho sync và job; experiment_run.prompt_text
 * giữ prompt gốc; mcq không đổi. LLM là bản giả (không gọi mạng).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class PreviousQuestionsPromptTest {

    static final class CapturingLlm extends LlmClient {
        final List<String> prompts = new ArrayList<>();
        final List<String> schemas = new ArrayList<>();
        int calls;

        CapturingLlm() {
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
            calls++;
            return new LlmCallResult("{\"question_text\": \"Tính $" + calls + " + 1$\", \"option_a\": \"$1$\","
                    + " \"option_b\": \"$2$\", \"option_c\": \"$3$\", \"option_d\": \"$4$\", \"correct_option\": \"B\","
                    + " \"explanation\": \"Lời giải\"}", 10, 1.0, 0.0, modelName);
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            schemas.add(schema);
            if (McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON.equals(schema)) {
                return mcq(prompt, modelName);
            }
            prompts.add(prompt);
            calls++;
            if (SaPromptDraftV0.GENERATION_RESPONSE_SCHEMA_JSON.equals(schema)) {
                return new LlmCallResult("{\"question_text\": " + SimpleJson.quote("Đề mới số " + calls
                        + ": giải $3x - " + (3 * calls) + " = 0$.") + ", \"explanation\": \"Lời giải\", \"answer\": \""
                        + calls + "\"}", 10, 1.0, 0.0, modelName);
            }
            if (TfPromptDraftV0.GENERATION_RESPONSE_SCHEMA_JSON.equals(schema)) {
                return new LlmCallResult("{\"question_text\": " + SimpleJson.quote("Đề dẫn mới số " + calls
                        + ": cho $y = " + calls + "x$.") + ", \"statement_a\": \"Ý một " + calls + "\", \"statement_b\":"
                        + " \"Ý hai " + calls + "\", \"statement_c\": \"Ý ba " + calls + "\", \"statement_d\": \"Ý bốn "
                        + calls + "\", \"explanation\": \"Lời giải\", \"label_a\": \"T\", \"label_b\": \"F\","
                        + " \"label_c\": \"T\", \"label_d\": \"F\"}", 10, 1.0, 0.0, modelName);
            }
            throw new UnsupportedOperationException("Schema lạ: " + schema);
        }
    }

    private static final CapturingLlm LLM = new CapturingLlm();

    @TestBean(name = "blockingTestLlmClient", methodName = "capturingLlm")
    private LlmClient llmClient;

    static LlmClient capturingLlm() {
        return LLM;
    }

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PreviousQuestionLookup lookup;
    @Autowired
    private GenerationJobProcessor jobProcessor;
    @Autowired
    private QcOrchestrator qcOrchestrator;
    @Autowired
    private DedupScopeResolver dedupScopeResolver;

    private MockMvc mockMvc;
    private int subject;
    private int template;
    private int otherTemplate;
    private int saRun;
    private int tfRun;
    private int mcqRun;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "PreviousQuestionsPromptTest.setUp");
        LLM.prompts.clear();
        LLM.schemas.clear();
        LLM.calls = 0;
        subject = insert("INSERT INTO subject (name) VALUES ('Toán')");
        template = insert("INSERT INTO question_template (subject_id, source_question, is_active, grade_level, topic)"
                + " VALUES (?, 'Giải $2x - 6 = 0$.', TRUE, 8, 'Phương trình bậc nhất')", subject);
        otherTemplate = insert("INSERT INTO question_template (subject_id, source_question, is_active, grade_level)"
                + " VALUES (?, 'Mẫu khác', TRUE, 8)", subject);
        saRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose,"
                + " prompt_version, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', 'sa-v0-draft',"
                + " 'sa')", subject);
        tfRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose,"
                + " prompt_version, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', 'tf-v0-draft',"
                + " 'tf')", subject);
        mcqRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    /** Câu sa cũ, tạo cách đây {@code minutesAgo} phút. */
    private int saQuestion(int tpl, String text, String status, boolean parsed, int minutesAgo) {
        return insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text, sa_answer,"
                + " explanation, raw_llm_output, parse_success, status, llm_difficulty, created_at) VALUES (?, ?, 'sa',"
                + " ?, ?, 'E', '{}', ?, ?, 'easy', now() - make_interval(mins => ?))", tpl, saRun,
                parsed ? text : null, parsed ? "3" : null, parsed, status, minutesAgo);
    }

    private int tfQuestion(String stem, List<String> statements, String status, int minutesAgo) {
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text, explanation,"
                + " raw_llm_output, parse_success, status, created_at) VALUES (?, ?, 'tf', ?, 'E', '{}', TRUE, ?,"
                + " now() - make_interval(mins => ?))", template, tfRun, stem, status, minutesAgo);
        for (int i = 0; i < 4; i++) {
            jdbc.update("INSERT INTO generated_question_statement (question_id, position, statement_text, llm_label,"
                    + " llm_difficulty) VALUES (?, ?, ?, 'T', 'easy')", q, "abcd".substring(i, i + 1), statements.get(i));
        }
        return q;
    }

    // ---------- tra cứu ----------

    @Test
    void layToiDa10CauSaGanNhatCungTemplateMoiTrangThai() {
        String[] statuses = {"qc_passed", "duplicate", "rejected", "generated"};
        for (int i = 1; i <= 12; i++) {
            saQuestion(template, "Đề SA " + i, statuses[i % 4], true, 100 - i);
        }
        saQuestion(template, null, "format_failed", false, 0);
        saQuestion(otherTemplate, "Đề template khác", "qc_passed", true, 0);
        jdbc.update("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c,"
                + " option_d, correct_option, raw_llm_output, parse_success, status, created_at) VALUES (?, ?, 'Đề MCQ',"
                + " '1', '2', '3', '4', 'A', '{}', TRUE, 'qc_passed', now())", template, mcqRun);

        List<PreviousQuestion> recent = lookup.recent(template, QuestionType.SA);
        assertEquals(10, recent.size());
        assertEquals("Đề SA 12", recent.get(0).questionText(), "mới nhất trước");
        assertEquals("Đề SA 3", recent.get(9).questionText());
        assertTrue(recent.stream().allMatch(p -> p.statements().isEmpty()));
        assertTrue(recent.stream().noneMatch(p -> p.questionText() == null || p.questionText().contains("khác")
                || p.questionText().contains("MCQ")));
        assertEquals(List.of(), lookup.recent(template, QuestionType.MCQ), "không áp cho mcq");
    }

    @Test
    void cauTfKemDeDanVa4YTheoThuTu() {
        tfQuestion("Đề dẫn cũ", List.of("Ý a cũ", "Ý b cũ", "Ý c cũ", "Ý d cũ"), "duplicate", 5);
        List<PreviousQuestion> recent = lookup.recent(template, QuestionType.TF);
        assertEquals(1, recent.size());
        assertEquals("Đề dẫn cũ", recent.get(0).questionText());
        assertEquals(List.of("Ý a cũ", "Ý b cũ", "Ý c cũ", "Ý d cũ"), recent.get(0).statements());
    }

    // ---------- sync ----------

    @Test
    void syncSaGuiDanhSachRunGiuPromptGoc() throws Exception {
        saQuestion(template, "Đề SA cũ", "duplicate", true, 5);
        mockMvc.perform(post("/api/generation/generate-sync").contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\": " + template + ", \"questionType\": \"sa\", \"runAnswerCheck\": false}"))
                .andExpect(status().isOk());
        String sent = LLM.prompts.get(0);
        assertTrue(sent.contains(PreviousQuestion.HEADER), sent);
        assertTrue(sent.contains("\n1. Đề SA cũ\n"), sent);
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM experiment_run WHERE id = (SELECT max(id)"
                + " FROM experiment_run)");
        assertEquals("sa-v1-draft", run.get("prompt_version"));
        assertFalse(((String) run.get("prompt_text")).contains(PreviousQuestion.HEADER), "run giữ prompt gốc");
    }

    @Test
    void syncTfGuiDeDanVa4Y() throws Exception {
        tfQuestion("Đề dẫn cũ", List.of("Ý a cũ", "Ý b cũ", "Ý c cũ", "Ý d cũ"), "qc_passed", 5);
        mockMvc.perform(post("/api/generation/generate-sync").contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\": " + template + ", \"questionType\": \"tf\", \"runAnswerCheck\": false,"
                        + " \"targetStatementDifficulties\": [\"easy\", \"easy\", \"medium\", \"hard\"]}"))
                .andExpect(status().isOk());
        String sent = LLM.prompts.get(0);
        assertTrue(sent.contains("\n1. Đề dẫn: Đề dẫn cũ\n   a) Ý a cũ\n   b) Ý b cũ\n   c) Ý c cũ\n   d) Ý d cũ\n"),
                sent);
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM experiment_run WHERE id = (SELECT max(id)"
                + " FROM experiment_run)");
        assertEquals("tf-v2-draft", run.get("prompt_version"));
        assertFalse(((String) run.get("prompt_text")).contains(PreviousQuestion.HEADER));
    }

    @Test
    void syncMcqKhongDoi() throws Exception {
        jdbc.update("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c,"
                + " option_d, correct_option, raw_llm_output, parse_success, status, created_at) VALUES (?, ?, 'Đề MCQ cũ',"
                + " '1', '2', '3', '4', 'A', '{}', TRUE, 'qc_passed', now())", template, mcqRun);
        mockMvc.perform(post("/api/generation/generate-sync").contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\": " + template + ", \"strategy\": \"zero-shot\", \"runAnswerCheck\": false}"))
                .andExpect(status().isOk());
        assertFalse(LLM.prompts.get(0).contains(PreviousQuestion.HEADER));
        assertFalse(LLM.prompts.get(0).contains("Đề MCQ cũ"));
        // Ngày 23/10: generate-sync MCQ (run production) gọi JSON mode kèm schema MCQ, như sa/tf.
        assertEquals(List.of(McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON), LLM.schemas);
    }

    // ---------- job ----------

    @Test
    void jobSaSauThayCauCuaJobTruoc() throws Exception {
        saQuestion(template, "Đề SA cũ", "qc_passed", true, 5);
        mockMvc.perform(post("/api/generation/jobs/enqueue").contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\": " + template + ", \"questionType\": \"sa\", \"count\": 2}"))
                .andExpect(status().isOk());
        jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute'");
        GenerationJobPoller poller = new GenerationJobPoller(jobProcessor, LLM, qcOrchestrator, dedupScopeResolver, 3);
        poller.processNextJob(false);
        poller.processNextJob(false);
        assertEquals(2, LLM.prompts.size());
        assertTrue(LLM.prompts.get(0).contains("\n1. Đề SA cũ\n"), LLM.prompts.get(0));
        assertTrue(LLM.prompts.get(1).contains("\n1. Đề mới số 1: giải $3x - 3 = 0$.\n2. Đề SA cũ\n"),
                LLM.prompts.get(1));
        String runPrompt = jdbc.queryForObject("SELECT prompt_text FROM experiment_run WHERE id = (SELECT max(id)"
                + " FROM experiment_run)", String.class);
        assertFalse(runPrompt.contains(PreviousQuestion.HEADER), "run giữ prompt gốc");
    }
}
