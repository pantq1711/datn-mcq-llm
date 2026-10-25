package com.datn.mcq.generation;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.repository.QuestionTemplateRepository;
import com.datn.mcq.catalog.repository.SubjectRepository;
import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.SourceType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.repository.ExperimentRunRepository;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.generation.repository.GenerationJobRepository;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.qc.dto.DedupScope;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nối dedup vào pipeline thật (GenerationService, GenerationJobPoller) trên datn_mcq_test.
 * Phạm vi theo quyết định 9: production so với run production cùng môn; experiment chỉ trong cùng run;
 * không bao giờ so chéo purpose. Không gọi Gemini: generate-sync dùng rawLlmOutputOverride,
 * worker dùng LlmClient giả, Solver không được cấu hình (BlockingTestLlmClient).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class DedupPipelineWiringTest {

    private static final String CONTENT_X = "Kết quả của phép tính $\\sqrt{(-5)^2}$ là gì?";
    private static final String CONTENT_Y = "Nghiệm của phương trình $2x - 4 = 0$ là:";
    private static final String CONTENT_Z = "Khai triển biểu thức $(x + 2)^2$ ta được:";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private QuestionTemplateRepository templateRepository;
    @Autowired
    private ExperimentRunRepository runRepository;
    @Autowired
    private GeneratedQuestionRepository questionRepository;
    @Autowired
    private GenerationJobRepository jobRepository;
    @Autowired
    private QualityCheckResultRepository qcrRepository;
    @Autowired
    private GenerationJobProcessor jobProcessor;
    @Autowired
    private QcOrchestrator qcOrchestrator;
    @Autowired
    private DedupScopeResolver dedupScopeResolver;

    private MockMvc mockMvc;
    private Subject subject;
    private QuestionTemplate template;
    private ExperimentRun productionRunOld;
    private ExperimentRun experimentRun1;
    private ExperimentRun experimentRun2;
    private GeneratedQuestion productionQcPassed;
    private GeneratedQuestion experimentQcPassed;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbcTemplate, "DedupPipelineWiringTest.setUp");
        subject = subjectRepository.save(new Subject("Toán Nối Dedup"));
        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(subject.getId());
        tpl.setGradeLevel(9);
        tpl.setTopic("Đại số");
        tpl.setSourceType(SourceType.TEXT);
        tpl.setSourceQuestion("Câu mẫu?");
        tpl.setIsActive(true);
        template = templateRepository.save(tpl);

        productionRunOld = run(subject, Purpose.PRODUCTION);
        experimentRun1 = run(subject, Purpose.EXPERIMENT);
        experimentRun2 = run(subject, Purpose.EXPERIMENT);
        productionQcPassed = qcPassedQuestion(productionRunOld, CONTENT_X);
        experimentQcPassed = qcPassedQuestion(experimentRun1, CONTENT_Y);
    }

    private ExperimentRun run(Subject s, Purpose purpose) {
        ExperimentRun r = new ExperimentRun();
        r.setSubjectId(s.getId());
        r.setPurpose(purpose);
        r.setStrategy(Strategy.FEW_SHOT_STRUCTURED);
        r.setModelName("gemini-test");
        r.setTemperature(0.7);
        r.setPromptText("Test prompt");
        r.setStatus("running");
        r.setStartedAt(LocalDateTime.now());
        return runRepository.save(r);
    }

    /** Dữ liệu cố định của test (không phải seed demo): một câu đã qua QC trong run cho trước. */
    private GeneratedQuestion qcPassedQuestion(ExperimentRun r, String stem) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setTemplateId(template.getId());
        gq.setRunId(r.getId());
        gq.setQuestionText(stem);
        gq.setOptionA("$-5$");
        gq.setOptionB("$5$");
        gq.setOptionC("$2$");
        gq.setOptionD("$25$");
        gq.setCorrectOption(Option.B);
        gq.setExplanation("Giải thích $x = 1$.");
        gq.setRawLlmOutput("{}");
        gq.setLlmDifficulty(Difficulty.EASY);
        gq.setParseSuccess(true);
        gq.setStatus(QuestionStatus.QC_PASSED);
        gq.setCreatedAt(LocalDateTime.now());
        return questionRepository.save(gq);
    }

    private static String rawJson(String stem) {
        return "{\"question_text\": " + SimpleJson.quote(stem) + ", \"option_a\": \"$-5$\", \"option_b\": \"$5$\","
                + " \"option_c\": \"$2$\", \"option_d\": \"$25$\", \"correct_option\": \"B\","
                + " \"explanation\": \"Giải thích $x = 1$.\"}";
    }

    private String syncBody(String stem) {
        return "{\"templateId\": " + template.getId() + ", \"runAnswerCheck\": true, \"rawLlmOutputOverride\": "
                + SimpleJson.quote(rawJson(stem)) + "}";
    }

    private Optional<QualityCheckResult> row(Integer questionId, CheckType type) {
        return qcrRepository.findByQuestionIdAndCheckType(questionId, type);
    }

    // ---------- GenerationService (generate-sync) ----------

    @Test
    void syncGenerationDuplicatingProductionQcPassedQuestionIsDuplicateAndSkipsSolver() throws Exception {
        mockMvc.perform(post("/api/generation/generate-sync")
                        .contentType(MediaType.APPLICATION_JSON).content(syncBody(CONTENT_X)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.status").value("duplicate"))
                .andExpect(jsonPath("$.qcReport.dedupStatus").value("DUPLICATE"))
                .andExpect(jsonPath("$.qcReport.answerCheckStatus").value("NOT_RUN"))
                .andExpect(jsonPath("$.qcReport.solverReasoning").value(containsString("trùng")))
                .andExpect(jsonPath("$.qcReport.dedup.comparedToQuestionId").value(productionQcPassed.getId()));

        GeneratedQuestion created = questionRepository.findAllByOrderByIdDesc().get(0);
        Integer id = created.getId();
        assertEquals(CONTENT_X, created.getQuestionText());
        assertEquals(QuestionStatus.DUPLICATE, created.getStatus());
        assertFalse(row(id, CheckType.DEDUP).orElseThrow().getPassed());
        assertTrue(row(id, CheckType.ANSWER_CHECK).isEmpty());
    }

    @Test
    void syncGenerationOfNewContentPassesDedupButStaysGeneratedWithoutSolver() throws Exception {
        mockMvc.perform(post("/api/generation/generate-sync")
                        .contentType(MediaType.APPLICATION_JSON).content(syncBody(CONTENT_Z)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.status").value("generated"))
                .andExpect(jsonPath("$.qcReport.dedupStatus").value("NO_DUPLICATE"))
                .andExpect(jsonPath("$.qcReport.answerCheckStatus").value("NOT_RUN"));

        GeneratedQuestion created = questionRepository.findAllByOrderByIdDesc().get(0);
        assertEquals(CONTENT_Z, created.getQuestionText());
        assertTrue(row(created.getId(), CheckType.DEDUP).orElseThrow().getPassed());
        assertTrue(row(created.getId(), CheckType.ANSWER_CHECK).isEmpty());
    }

    @Test
    void productionGenerationNeverComparesWithExperimentQuestions() throws Exception {
        mockMvc.perform(post("/api/generation/generate-sync")
                        .contentType(MediaType.APPLICATION_JSON).content(syncBody(CONTENT_Y)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qcReport.dedupStatus").value("NO_DUPLICATE"));
    }

    // ---------- DedupScopeResolver ----------

    @Test
    void resolverBuildsScopeAccordingToPurpose() {
        Subject other = subjectRepository.save(new Subject("Môn khác"));
        ExperimentRun productionOtherSubject = run(other, Purpose.PRODUCTION);
        ExperimentRun productionNew = run(subject, Purpose.PRODUCTION);

        DedupScope production = dedupScopeResolver.resolve(productionNew.getId());
        assertEquals(Purpose.PRODUCTION, production.purpose());
        assertEquals(List.of(productionRunOld.getId(), productionNew.getId()), production.comparableRunIds());
        assertFalse(production.comparableRunIds().contains(productionOtherSubject.getId()));
        assertFalse(production.comparableRunIds().contains(experimentRun1.getId()));

        DedupScope experiment = dedupScopeResolver.resolve(experimentRun2.getId());
        assertEquals(Purpose.EXPERIMENT, experiment.purpose());
        assertEquals(List.of(experimentRun2.getId()), experiment.comparableRunIds());
        assertTrue(experiment.isComplete());
    }

    // ---------- GenerationJobPoller (worker) ----------

    /** LlmClient giả cho worker: trả JSON dựng sẵn khi sinh câu; mọi đường gọi mạng khác bị chặn. */
    static final class CannedGeneratorLlmClient extends LlmClient {
        private final String json;

        CannedGeneratorLlmClient(String json) {
            super("FAKE_KEY_FOR_TESTS", "fake-generator-model");
            this.json = json;
        }

        @Override
        public boolean isConfigured() {
            return false;
        }

        @Override
        public LlmCallResult generateMcq(String prompt, String modelName, Strategy strategy, Double temperature) {
            return new LlmCallResult(json, 10, 1.0, 0.0, "fake-generator-model");
        }

        @Override
        public LlmCallResult callJsonMode(String prompt, String modelName, Double temperature) {
            throw new UnsupportedOperationException("Không được gọi mạng trong test");
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            throw new UnsupportedOperationException("Không được gọi Solver trong test này");
        }
    }

    private GenerationJobPoller.ProcessedJobSummary processOneJob(ExperimentRun r, String stem) {
        GenerationJob job = new GenerationJob();
        job.setRunId(r.getId());
        job.setTemplateId(template.getId());
        job.setTargetDifficulty(Difficulty.EASY);
        job.setStatus(JobStatus.PENDING);
        job.setAttempt(0);
        job.setNextRunAt(LocalDateTime.now().minusSeconds(1));
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);
        GenerationJobPoller poller = new GenerationJobPoller(
                jobProcessor, new CannedGeneratorLlmClient(rawJson(stem)), qcOrchestrator, dedupScopeResolver, 3);
        return poller.processNextJob(true);
    }

    @Test
    void experimentJobComparesOnlyWithinItsOwnRun() {
        GenerationJobPoller.ProcessedJobSummary sameRun = processOneJob(experimentRun1, CONTENT_Y);
        assertEquals("duplicate", sameRun.questionStatus(), "Cùng run experiment phải bị phát hiện trùng");
        assertEquals(experimentQcPassed.getId(),
                row(sameRun.questionId(), CheckType.DEDUP).orElseThrow().getComparedToQuestionId());

        GenerationJobPoller.ProcessedJobSummary otherRun = processOneJob(experimentRun2, CONTENT_Y);
        assertNotEquals("duplicate", otherRun.questionStatus(), "Không so giữa các run experiment");

        GenerationJobPoller.ProcessedJobSummary crossPurpose = processOneJob(experimentRun2, CONTENT_X);
        assertNotEquals("duplicate", crossPurpose.questionStatus(), "Không so experiment với production");

        for (Integer qid : List.of(sameRun.questionId(), otherRun.questionId(), crossPurpose.questionId())) {
            assertTrue(row(qid, CheckType.ANSWER_CHECK).isEmpty(), "Không có answer_check thật trong test");
            assertTrue(row(qid, CheckType.DEDUP).isPresent());
        }
    }
}
