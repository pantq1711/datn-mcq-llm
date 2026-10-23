package com.datn.mcq.qc.service;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.repository.QuestionTemplateRepository;
import com.datn.mcq.catalog.repository.SubjectRepository;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
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
import com.datn.mcq.qc.checker.DedupChecker;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupOutcome;
import com.datn.mcq.qc.dto.DedupScope;
import com.datn.mcq.qc.dto.DedupStatus;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DedupService trên database test: phạm vi ứng viên theo quyết định 2, ghi dòng dedup đúng ràng buộc,
 * thiếu phạm vi hoặc lỗi thì không ghi dòng dedup. Không gọi LLM.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class DedupServiceIntegrationTest {

    private static final String STEM = "Kết quả của phép tính $\\sqrt{(-5)^2}$ là gì?";

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
    private DedupService dedupService;

    private QuestionTemplate mathTemplate;
    private QuestionTemplate otherTemplate;
    private ExperimentRun currentRun;
    private ExperimentRun olderRunSameSubject;
    private ExperimentRun runOtherSubject;

    @BeforeEach
    void setUp() {
        TestDataCleaner.cleanAll(dataSource, jdbcTemplate, "DedupServiceIntegrationTest.setUp");
        Subject math = subjectRepository.save(new Subject("Toán Dedup"));
        Subject other = subjectRepository.save(new Subject("Toán Khác"));
        mathTemplate = template(math);
        otherTemplate = template(other);
        currentRun = run(math);
        olderRunSameSubject = run(math);
        runOtherSubject = run(other);
    }

    private QuestionTemplate template(Subject subject) {
        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(subject.getId());
        tpl.setGradeLevel(8);
        tpl.setTopic("Căn bậc hai");
        tpl.setSourceType(SourceType.TEXT);
        tpl.setSourceQuestion("Câu mẫu?");
        tpl.setIsActive(true);
        return templateRepository.save(tpl);
    }

    private ExperimentRun run(Subject subject) {
        ExperimentRun r = new ExperimentRun();
        r.setSubjectId(subject.getId());
        r.setPurpose(Purpose.PRODUCTION);
        r.setStrategy(Strategy.FEW_SHOT_STRUCTURED);
        r.setModelName("gemini-test");
        r.setTemperature(0.7);
        r.setPromptText("Test prompt");
        r.setStatus("completed");
        r.setStartedAt(LocalDateTime.now());
        return runRepository.save(r);
    }

    private GeneratedQuestion question(ExperimentRun run, QuestionStatus status, String stem) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setTemplateId(run.getSubjectId().equals(mathTemplate.getSubjectId())
                ? mathTemplate.getId() : otherTemplate.getId());
        gq.setRunId(run.getId());
        gq.setQuestionText(stem);
        gq.setOptionA("$-5$");
        gq.setOptionB("$5$");
        gq.setOptionC("$-25$");
        gq.setOptionD("$25$");
        gq.setCorrectOption(Option.B);
        gq.setExplanation("Ta có $\\sqrt{A^2} = |A|$.");
        gq.setRawLlmOutput("{}");
        gq.setLlmDifficulty(Difficulty.EASY);
        gq.setParseSuccess(true);
        gq.setStatus(status);
        if (status == QuestionStatus.IN_BANK) {
            gq.setFinalDifficulty(Difficulty.EASY); // chk_gq_in_bank_complete
        }
        gq.setCreatedAt(LocalDateTime.now());
        return questionRepository.save(gq);
    }

    private void markDedupPassed(GeneratedQuestion q) {
        QualityCheckResult row = new QualityCheckResult();
        row.setQuestionId(q.getId());
        row.setCheckType(CheckType.DEDUP);
        row.setPassed(true);
        row.setThresholdUsed(DedupChecker.PROVISIONAL_THRESHOLD);
        row.setCheckedAt(LocalDateTime.now());
        qcrRepository.save(row);
    }

    private DedupCandidate subjectOf(GeneratedQuestion q) {
        return new DedupCandidate(q.getId(), q.getQuestionText(), q.getOptionA(), q.getOptionB(),
                q.getOptionC(), q.getOptionD());
    }

    private DedupScope scopeOfCurrentRun() {
        return DedupScope.forProduction(currentRun.getId(), List.of(currentRun.getId(), olderRunSameSubject.getId()));
    }

    private Optional<QualityCheckResult> dedupRow(GeneratedQuestion q) {
        return qcrRepository.findByQuestionIdAndCheckType(q.getId(), CheckType.DEDUP);
    }

    @Test
    void duplicateOfQcPassedQuestionInAnotherRunOfSameSubjectIsRecorded() {
        GeneratedQuestion existing = question(olderRunSameSubject, QuestionStatus.QC_PASSED, STEM);
        GeneratedQuestion fresh = question(currentRun, QuestionStatus.GENERATED, STEM);

        DedupOutcome outcome = dedupService.checkAndRecord(subjectOf(fresh), scopeOfCurrentRun());

        assertEquals(DedupStatus.DUPLICATE, outcome.status());
        QualityCheckResult row = dedupRow(fresh).orElseThrow();
        assertFalse(row.getPassed());
        assertEquals(existing.getId(), row.getComparedToQuestionId());
        assertEquals(1.0, row.getSimilarityScore(), 1e-9);
        assertEquals(DedupChecker.PROVISIONAL_THRESHOLD, row.getThresholdUsed(), 1e-12);
        assertNull(row.getJudgeAnswer());
        assertNull(row.getJudgeModel());
    }

    @Test
    void duplicateOfInBankQuestionIsDetected() {
        GeneratedQuestion inBank = question(olderRunSameSubject, QuestionStatus.IN_BANK, STEM);
        GeneratedQuestion fresh = question(currentRun, QuestionStatus.GENERATED, STEM);

        DedupOutcome outcome = dedupService.checkAndRecord(subjectOf(fresh), scopeOfCurrentRun());

        assertEquals(DedupStatus.DUPLICATE, outcome.status());
        assertEquals(inBank.getId(), outcome.comparedToQuestionId());
    }

    @Test
    void sameContentInAnotherSubjectIsOutOfScope() {
        question(runOtherSubject, QuestionStatus.QC_PASSED, STEM);
        GeneratedQuestion fresh = question(currentRun, QuestionStatus.GENERATED, STEM);

        DedupOutcome outcome = dedupService.checkAndRecord(subjectOf(fresh), scopeOfCurrentRun());

        assertEquals(DedupStatus.NO_DUPLICATE, outcome.status());
        assertTrue(dedupRow(fresh).orElseThrow().getPassed());
    }

    @Test
    void sameRunGeneratedQuestionCountsOnlyWhenItAlreadyHasPassedDedupRow() {
        GeneratedQuestion earlierWithoutDedup = question(currentRun, QuestionStatus.GENERATED, STEM);
        GeneratedQuestion fresh = question(currentRun, QuestionStatus.GENERATED, STEM);
        assertEquals(DedupStatus.NO_DUPLICATE,
                dedupService.checkAndRecord(subjectOf(fresh), scopeOfCurrentRun()).status());

        markDedupPassed(earlierWithoutDedup);
        DedupOutcome outcome = dedupService.checkAndRecord(subjectOf(fresh), scopeOfCurrentRun());
        assertEquals(DedupStatus.DUPLICATE, outcome.status());
        assertEquals(earlierWithoutDedup.getId(), outcome.comparedToQuestionId());
    }

    @Test
    void generatedQuestionOfAnotherRunIsNotACandidateEvenWithPassedDedupRow() {
        GeneratedQuestion otherRunGenerated = question(olderRunSameSubject, QuestionStatus.GENERATED, STEM);
        markDedupPassed(otherRunGenerated);
        GeneratedQuestion fresh = question(currentRun, QuestionStatus.GENERATED, STEM);

        assertEquals(DedupStatus.NO_DUPLICATE,
                dedupService.checkAndRecord(subjectOf(fresh), scopeOfCurrentRun()).status());
    }

    @Test
    void rejectedFormatFailedAndDuplicateQuestionsAreNeverCandidates() {
        for (QuestionStatus excluded : List.of(
                QuestionStatus.REJECTED, QuestionStatus.FORMAT_FAILED, QuestionStatus.DUPLICATE)) {
            GeneratedQuestion q = question(currentRun, excluded, STEM);
            markDedupPassed(q);
        }
        GeneratedQuestion fresh = question(currentRun, QuestionStatus.GENERATED, STEM);

        assertEquals(DedupStatus.NO_DUPLICATE,
                dedupService.checkAndRecord(subjectOf(fresh), scopeOfCurrentRun()).status());
    }

    @Test
    void sameSkillWithDifferentNumbersInBankIsNotDuplicate() {
        question(olderRunSameSubject, QuestionStatus.QC_PASSED, "Kết quả của phép tính $\\sqrt{(-7)^2}$ là gì?");
        GeneratedQuestion fresh = question(currentRun, QuestionStatus.GENERATED, STEM);

        DedupOutcome outcome = dedupService.checkAndRecord(subjectOf(fresh), scopeOfCurrentRun());

        assertEquals(DedupStatus.NO_DUPLICATE, outcome.status());
        QualityCheckResult row = dedupRow(fresh).orElseThrow();
        assertTrue(row.getPassed());
        assertTrue(row.getSimilarityScore() == null || row.getSimilarityScore() < row.getThresholdUsed());
    }

    @Test
    void missingScopeIsErrorAndWritesNoDedupRow() {
        GeneratedQuestion fresh = question(currentRun, QuestionStatus.GENERATED, STEM);

        assertEquals(DedupStatus.ERROR, dedupService.checkAndRecord(subjectOf(fresh), null).status());
        assertEquals(DedupStatus.ERROR,
                dedupService.checkAndRecord(subjectOf(fresh), DedupScope.forProduction(currentRun.getId(), null)).status());
        assertEquals(DedupStatus.ERROR, dedupService.checkAndRecord(subjectOf(fresh),
                DedupScope.forProduction(currentRun.getId(), List.of(olderRunSameSubject.getId()))).status());
        assertEquals(DedupStatus.ERROR, dedupService.checkAndRecord(subjectOf(fresh),
                new DedupScope(Purpose.EXPERIMENT, currentRun.getId(),
                        List.of(currentRun.getId(), olderRunSameSubject.getId()))).status());
        assertTrue(dedupRow(fresh).isEmpty());
    }

    @Test
    void rerunUpdatesTheSingleDedupRow() {
        GeneratedQuestion fresh = question(currentRun, QuestionStatus.GENERATED, STEM);
        dedupService.checkAndRecord(subjectOf(fresh), scopeOfCurrentRun());
        dedupService.checkAndRecord(subjectOf(fresh), scopeOfCurrentRun());

        long rows = qcrRepository.findByQuestionIdOrderByIdAsc(fresh.getId()).stream()
                .filter(r -> r.getCheckType() == CheckType.DEDUP).count();
        assertEquals(1, rows);
    }

    @Test
    void databaseFailureRollsBackAndLeavesNoDedupRow() {
        // Câu không tồn tại: ghi dòng dedup vi phạm khóa ngoại, transaction phải rollback toàn bộ.
        DedupCandidate ghost = new DedupCandidate(Integer.MAX_VALUE, STEM, "$-5$", "$5$", "$-25$", "$25$");

        assertThrows(RuntimeException.class, () -> dedupService.checkAndRecord(ghost, scopeOfCurrentRun()));
        assertTrue(qcrRepository.findByQuestionIdAndCheckType(Integer.MAX_VALUE, CheckType.DEDUP).isEmpty());
    }
}
