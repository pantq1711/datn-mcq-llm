package com.datn.mcq.generation;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.repository.QuestionTemplateRepository;
import com.datn.mcq.catalog.repository.SubjectRepository;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.SourceType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.repository.ExperimentRunRepository;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.generation.service.QcRerunService;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupStatus;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import com.datn.mcq.qc.service.DedupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quy tắc QC-RERUN (quyết định 6 ngày 22/10): chạy lại QC cho một câu ở trạng thái generated, đi qua đúng pipeline
 * format -> dedup -> Solver, không ép trạng thái. Kết quả answer_check/dedup cũ được xóa trước khi chạy lại,
 * để lần chạy lỗi không bị hiểu nhầm là thành công. Solver là LlmClient giả, không gọi mạng.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class QcRerunServiceTest {

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
    private QualityCheckResultRepository qcrRepository;
    @Autowired
    private GenerationService generationService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private DedupService dedupService;
    @Autowired
    private DedupScopeResolver dedupScopeResolver;

    private QuestionTemplate template;
    private ExperimentRun productionRun;

    /** Solver giả: đếm số lần gọi; trả JSON dựng sẵn hoặc ném lỗi. Sinh câu và JSON mode thường bị chặn. */
    static final class FakeSolver extends LlmClient {
        private final String response;
        private final Supplier<RuntimeException> failure;
        int calls;

        FakeSolver(String response, Supplier<RuntimeException> failure) {
            super("FAKE_KEY_FOR_TESTS", "fake-model");
            this.response = response;
            this.failure = failure;
        }

        static FakeSolver answering(String judge) {
            return new FakeSolver("{\"reasoning\": \"Tính toán\", \"judge_answer\": \"" + judge + "\"}", null);
        }

        static FakeSolver failing() {
            return new FakeSolver(null, () -> new RuntimeException("HTTP 503 từ Gemini API"));
        }

        @Override
        public boolean isConfigured() {
            return true;
        }


        @Override
        public LlmCallResult callJsonMode(String prompt, String modelName, Double temperature) {
            throw new UnsupportedOperationException("Solver phải dùng callJsonModeWithSchema");
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            calls++;
            if (failure != null) {
                throw failure.get();
            }
            return new LlmCallResult(response, 10, 1.0, 0.0, modelName);
        }
    }

    @BeforeEach
    void setUp() {
        TestDataCleaner.cleanAll(dataSource, jdbcTemplate, "QcRerunServiceTest.setUp");
        Subject subject = subjectRepository.save(new Subject("Toán Chạy Lại QC"));
        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(subject.getId());
        tpl.setGradeLevel(8);
        tpl.setTopic("Chạy lại QC");
        tpl.setSourceType(SourceType.TEXT);
        tpl.setSourceQuestion("Câu mẫu?");
        tpl.setIsActive(true);
        template = templateRepository.save(tpl);
        productionRun = run(Purpose.PRODUCTION);
    }

    private ExperimentRun run(Purpose purpose) {
        ExperimentRun r = new ExperimentRun();
        r.setSubjectId(template.getSubjectId());
        r.setPurpose(purpose);
        r.setStrategy(Strategy.FEW_SHOT_STRUCTURED);
        r.setModelName("gemini-test");
        r.setTemperature(0.7);
        r.setPromptText("Test prompt");
        r.setStatus("completed");
        r.setStartedAt(LocalDateTime.now());
        return runRepository.save(r);
    }

    private GeneratedQuestion question(ExperimentRun r, QuestionStatus status, String stem) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setTemplateId(template.getId());
        gq.setRunId(r.getId());
        gq.setQuestionText(stem);
        gq.setOptionA("$x = 2$");
        gq.setOptionB("$x = 1$");
        gq.setOptionC("$x = 4$");
        gq.setOptionD("$x = 0$");
        gq.setCorrectOption(Option.A);
        gq.setExplanation("Nhân hai vế với $2$ được $x = 2$.");
        gq.setRawLlmOutput("{}");
        gq.setLlmDifficulty(Difficulty.EASY);
        gq.setParseSuccess(true);
        gq.setStatus(status);
        if (status == QuestionStatus.IN_BANK) {
            gq.setFinalDifficulty(Difficulty.EASY);
        }
        gq.setCreatedAt(LocalDateTime.now());
        return questionRepository.save(gq);
    }

    private void row(GeneratedQuestion q, CheckType type, boolean passed) {
        QualityCheckResult r = new QualityCheckResult();
        r.setQuestionId(q.getId());
        r.setCheckType(type);
        r.setPassed(passed);
        if (type == CheckType.ANSWER_CHECK) {
            r.setJudgeAnswer(AnswerChoice.A);
            r.setJudgeModel("old-solver");
        }
        r.setCheckedAt(LocalDateTime.now().minusDays(1));
        qcrRepository.save(r);
    }

    private Optional<QualityCheckResult> find(GeneratedQuestion q, CheckType type) {
        return qcrRepository.findByQuestionIdAndCheckType(q.getId(), type);
    }

    private QcRerunService service(FakeSolver solver) {
        QcOrchestrator orchestrator = new QcOrchestrator(new FormatChecker(),
                new AnswerSolverChecker(solver, new PromptBuilder(), "solver-test"), qcrRepository, dedupService);
        return new QcRerunService(generationService, catalogService, orchestrator, dedupScopeResolver);
    }

    @Test
    void rerunGoesThroughFormatDedupAndSolverAndCanReachQcPassed() {
        GeneratedQuestion q = question(productionRun, QuestionStatus.GENERATED, "Nghiệm của $\\frac{x}{2} = 1$ là:");
        FakeSolver solver = FakeSolver.answering("A");

        QcRerunService.QcRerunResult result = service(solver).rerun(q.getId());

        assertEquals(QuestionStatus.GENERATED, result.previousStatus());
        assertEquals(QuestionStatus.QC_PASSED, result.resultingStatus());
        assertEquals(DedupStatus.NO_DUPLICATE, result.qcReport().dedupStatus());
        assertEquals(AnswerCheckStatus.CONSISTENT, result.qcReport().answerCheckStatus());
        assertEquals(1, solver.calls);
        assertEquals(QuestionStatus.QC_PASSED, questionRepository.findById(q.getId()).orElseThrow().getStatus());
        assertTrue(find(q, CheckType.FORMAT).orElseThrow().getPassed());
        assertTrue(find(q, CheckType.DEDUP).orElseThrow().getPassed());
        assertTrue(find(q, CheckType.ANSWER_CHECK).orElseThrow().getPassed());
    }

    // Sửa 2026-10-22 (quyết định mục 2): chỉ chạy lại sau lỗi hạ tầng. Trước đây test này dựng sẵn một dòng
    // answer_check passed; nay ca đó bị từ chối, nên dựng ca "dedup đã qua nhưng Solver lỗi" (không có dòng answer_check).
    @Test
    void rerunAfterSolverErrorReplacesDedupRowAndAFailedRerunLeavesNoAnswerCheckRow() {
        GeneratedQuestion q = question(productionRun, QuestionStatus.GENERATED, "Nghiệm của $\\frac{x}{2} = 1$ là:");
        row(q, CheckType.DEDUP, true);
        FakeSolver solver = FakeSolver.failing();

        QcRerunService.QcRerunResult result = service(solver).rerun(q.getId());

        assertEquals(QuestionStatus.GENERATED, result.resultingStatus());
        assertEquals(AnswerCheckStatus.ERROR, result.qcReport().answerCheckStatus());
        assertEquals(1, solver.calls);
        assertTrue(find(q, CheckType.ANSWER_CHECK).isEmpty(), "Lần chạy lỗi không được để lại dòng answer_check");
        QualityCheckResult dedup = find(q, CheckType.DEDUP).orElseThrow();
        assertTrue(dedup.getCheckedAt().isAfter(LocalDateTime.now().minusHours(1)), "Dòng dedup phải là của lần chạy lại");
    }

    @Test
    void inconsistentAnswerCheckIsRefusedAndNothingChanges() {
        GeneratedQuestion q = question(productionRun, QuestionStatus.GENERATED, "Nghiệm của $\\frac{x}{2} = 1$ là:");
        row(q, CheckType.DEDUP, true);
        row(q, CheckType.ANSWER_CHECK, false); // Solver lệch đáp án: kết luận hợp lệ, không phải lỗi hạ tầng
        FakeSolver solver = FakeSolver.answering("A");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service(solver).rerun(q.getId()));

        assertTrue(ex.getMessage().contains("lệch"), ex.getMessage());
        assertEquals(0, solver.calls);
        assertEquals(QuestionStatus.GENERATED, questionRepository.findById(q.getId()).orElseThrow().getStatus());
        assertFalse(find(q, CheckType.ANSWER_CHECK).orElseThrow().getPassed(), "Kết luận cũ phải được giữ nguyên");
        assertTrue(find(q, CheckType.DEDUP).isPresent());
    }

    @Test
    void generatedQuestionWithConsistentAnswerCheckIsAlsoRefused() {
        // Chỉ có ở dữ liệu cũ (trước khi nối dedup): answer_check đã chạy thành công, không phải lỗi hạ tầng.
        GeneratedQuestion q = question(productionRun, QuestionStatus.GENERATED, "Nghiệm của $\\frac{x}{2} = 1$ là:");
        row(q, CheckType.ANSWER_CHECK, true);
        FakeSolver solver = FakeSolver.answering("A");

        assertThrows(IllegalStateException.class, () -> service(solver).rerun(q.getId()));
        assertEquals(0, solver.calls);
        assertTrue(find(q, CheckType.ANSWER_CHECK).orElseThrow().getPassed());
    }

    @Test
    void rerunDetectsDuplicateAndNeverCallsSolver() {
        String stem = "Nghiệm của $\\frac{x}{2} = 1$ là:";
        GeneratedQuestion original = question(run(Purpose.PRODUCTION), QuestionStatus.QC_PASSED, stem);
        GeneratedQuestion q = question(productionRun, QuestionStatus.GENERATED, stem);
        FakeSolver solver = FakeSolver.answering("A");

        QcRerunService.QcRerunResult result = service(solver).rerun(q.getId());

        assertEquals(QuestionStatus.DUPLICATE, result.resultingStatus());
        assertEquals(0, solver.calls, "Câu trùng không bao giờ gọi Solver");
        assertEquals(original.getId(), find(q, CheckType.DEDUP).orElseThrow().getComparedToQuestionId());
        assertTrue(find(q, CheckType.ANSWER_CHECK).isEmpty());
    }

    @Test
    void onlyGeneratedQuestionsCanBeRerunAndNothingChangesOtherwise() {
        for (QuestionStatus status : List.of(QuestionStatus.DUPLICATE, QuestionStatus.QC_PASSED,
                QuestionStatus.IN_BANK, QuestionStatus.REJECTED, QuestionStatus.FORMAT_FAILED)) {
            GeneratedQuestion q = question(productionRun, status, "Câu trạng thái " + status.getDbValue() + " $x$:");
            row(q, CheckType.ANSWER_CHECK, true);
            FakeSolver solver = FakeSolver.answering("A");

            assertThrows(IllegalStateException.class, () -> service(solver).rerun(q.getId()), status.getDbValue());

            assertEquals(0, solver.calls);
            assertEquals(status, questionRepository.findById(q.getId()).orElseThrow().getStatus());
            assertTrue(find(q, CheckType.ANSWER_CHECK).isPresent(), "Không được xóa kết quả QC của câu " + status);
        }
    }

    @Test
    void missingQuestionIsNotFound() {
        assertThrows(NoSuchElementException.class, () -> service(FakeSolver.answering("A")).rerun(Integer.MAX_VALUE));
    }

    @Test
    void experimentRerunComparesOnlyWithinItsOwnRun() {
        String stem = "Nghiệm của $\\frac{x}{2} = 1$ là:";
        question(run(Purpose.EXPERIMENT), QuestionStatus.QC_PASSED, stem);
        GeneratedQuestion q = question(run(Purpose.EXPERIMENT), QuestionStatus.GENERATED, stem);
        FakeSolver solver = FakeSolver.answering("A");

        QcRerunService.QcRerunResult result = service(solver).rerun(q.getId());

        assertFalse(result.resultingStatus() == QuestionStatus.DUPLICATE, "Không so giữa các run experiment");
        assertEquals(QuestionStatus.QC_PASSED, result.resultingStatus());
        assertEquals(1, solver.calls);
    }
}
