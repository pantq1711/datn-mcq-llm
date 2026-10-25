package com.datn.mcq.qc.orchestrator;

import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupScope;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import com.datn.mcq.qc.service.AnswerCheckRecorder;
import com.datn.mcq.qc.service.DedupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Constructor 4 tham số của QcOrchestrator (GĐ4): bị đánh dấu @Deprecated, chỉ để test MCQ dựng tay. Không có bộ ghi
 * kết quả Solver câu đúng/sai nên câu tf đi qua nó ra ERROR mà không gọi Solver. Spring dùng constructor 5 tham số.
 */
class QcOrchestratorLegacyConstructorTest {

    private static final DedupScope SCOPE = DedupScope.forProduction(7, List.of(7));

    @Test
    void constructor4ThamSoBiDanhDauDeprecated() throws Exception {
        Constructor<QcOrchestrator> legacy = QcOrchestrator.class.getConstructor(FormatChecker.class,
                AnswerSolverChecker.class, QualityCheckResultRepository.class, DedupService.class);
        Constructor<QcOrchestrator> full = QcOrchestrator.class.getConstructor(FormatChecker.class,
                AnswerSolverChecker.class, QualityCheckResultRepository.class, DedupService.class,
                AnswerCheckRecorder.class);
        assertTrue(legacy.isAnnotationPresent(Deprecated.class));
        assertFalse(full.isAnnotationPresent(Deprecated.class));
        assertTrue(full.isAnnotationPresent(Autowired.class));
    }

    @Test
    @SuppressWarnings("deprecation")
    void cauTfQuaConstructor4ThamSoLaErrorVaKhongGoiSolver() {
        QualityCheckResultRepository repository = mock(QualityCheckResultRepository.class);
        when(repository.findByQuestionIdAndCheckType(anyInt(), any())).thenReturn(Optional.empty());
        when(repository.save(any(QualityCheckResult.class))).thenAnswer(inv -> inv.getArgument(0));
        QcOrchestratorStatusTest.FakeSolverLlmClient llm = QcOrchestratorStatusTest.FakeSolverLlmClient.returning(
                "{\"reasoning_a\": \"r\", \"judge_a\": \"T\", \"reasoning_b\": \"r\", \"judge_b\": \"F\","
                        + " \"reasoning_c\": \"r\", \"judge_c\": \"T\", \"reasoning_d\": \"r\", \"judge_d\": \"F\"}");
        QcOrchestrator orchestrator = new QcOrchestrator(new FormatChecker(),
                new AnswerSolverChecker(llm, new PromptBuilder(), "solver-test-model"), repository,
                new DedupService(repository));

        QcPipelineReport report = orchestrator.evaluateAndSave(QuestionQcInput.trueFalse(1, true, "Đề dẫn",
                List.of("Ý một", "Ý hai", "Ý ba", "Ý bốn"), List.of("T", "F", "T", "F"), "Lời giải", false, true,
                null), SCOPE);

        assertEquals(AnswerCheckStatus.ERROR, report.answerCheckStatus());
        assertEquals(0, llm.solverCalls);
    }
}
