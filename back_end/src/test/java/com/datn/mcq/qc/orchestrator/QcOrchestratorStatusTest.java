package com.datn.mcq.qc.orchestrator;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupScope;
import com.datn.mcq.qc.dto.DedupStatus;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import com.datn.mcq.qc.service.DedupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hợp đồng Q-2 (chốt 2026-10-21): QC_PASSED chỉ khi format hợp lệ, dedup đã chạy và không trùng,
 * answer_check chạy thành công và judge_answer khớp correct_option. Dedup báo trùng -> DUPLICATE.
 * Mọi trường hợp khác (trừ format lỗi) phải ở trạng thái GENERATED.
 *
 * <p>Sửa 2026-10-21 theo quyết định 1: dedup phải kết luận NO_DUPLICATE trước khi gọi Solver.
 * DedupService thật chạy trên repository mock (mặc định không có ứng viên -> NO_DUPLICATE);
 * các nhánh answer_check vì vậy được kiểm sau khi dedup đã kết luận không trùng.
 *
 * Test không gọi mạng: LlmClient được thay bằng bản giả trả JSON dựng sẵn hoặc ném lỗi,
 * repository được mock.
 */
class QcOrchestratorStatusTest {

    private static final String SOLVER_MODEL = "solver-test-model";

    /** Phạm vi production hợp lệ: run 7 là run duy nhất cùng môn. */
    private static final DedupScope SCOPE = DedupScope.forProduction(7, List.of(7));

    private QualityCheckResultRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(QualityCheckResultRepository.class);
        when(repository.findByQuestionIdAndCheckType(anyInt(), any())).thenReturn(Optional.empty());
        when(repository.save(any(QualityCheckResult.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- Nhánh format ----------

    @Test
    void formatFailureMustBeFormatFailedAndNeverCallSolver() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(solverJson("A"));
        QuestionQcInput input = new QuestionQcInput(
                1, true, "Câu hỏi $x = 2$", "$x = 2$", "$x = 2$", "$x = 4$", "$x = 0$",
                Option.A, "Giải thích $x = 2$.", true, true);

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(input, SCOPE);

        assertEquals(AnswerCheckStatus.NOT_RUN, report.answerCheckStatus());
        assertEquals(DedupStatus.NOT_RUN, report.dedupStatus(), "Format lỗi thì dừng trước dedup");

        assertEquals(QuestionStatus.FORMAT_FAILED, report.resultingStatus());
        assertEquals(0, llm.solverCalls, "Format lỗi thì không được gọi Solver");
        verify(repository, never()).findDedupCandidateRows(any(), any(), any(), any());
    }

    // ---------- Dedup chưa kết luận: thiếu phạm vi, lỗi, hoặc trùng ----------
    // Sửa 2026-10-21 (quyết định 1): trước đây test này kỳ vọng Solver vẫn được gọi khi dedup chưa chạy
    // (solverCalls = 1, answer_check CONSISTENT). Hợp đồng mới: dedup chưa kết luận thì KHÔNG gọi Solver.

    @Test
    void missingDedupScopeMustStayGeneratedAndNeverCallSolver() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(solverJson("A"));

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), null);

        assertEquals(DedupStatus.ERROR, report.dedupStatus());
        assertEquals(AnswerCheckStatus.NOT_RUN, report.answerCheckStatus());
        assertEquals(QuestionStatus.GENERATED, report.resultingStatus(),
                "Thiếu phạm vi dedup thì không được QC_PASSED dù Solver sẽ khớp đáp án (Q-2)");
        assertEquals(0, llm.solverCalls, "Dedup chưa kết luận thì không được gọi Solver");
        assertNoAnswerCheckRowSaved();
        assertNoDedupRowSaved();
        verify(repository, never()).findDedupCandidateRows(any(), any(), any(), any());
    }

    @Test
    void dedupFailureMustStayGeneratedAndNeverCallSolver() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(solverJson("A"));
        when(repository.findDedupCandidateRows(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("mất kết nối DB"));

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertEquals(DedupStatus.ERROR, report.dedupStatus());
        assertEquals(AnswerCheckStatus.NOT_RUN, report.answerCheckStatus());
        assertEquals(QuestionStatus.GENERATED, report.resultingStatus());
        assertEquals(0, llm.solverCalls);
        assertNoAnswerCheckRowSaved();
        assertNoDedupRowSaved();
    }

    @Test
    void duplicateMustBeDuplicateAndNeverCallSolver() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(solverJson("A"));
        when(repository.findDedupCandidateRows(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(duplicateCandidateRow(5)));

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertEquals(DedupStatus.DUPLICATE, report.dedupStatus());
        assertEquals(AnswerCheckStatus.NOT_RUN, report.answerCheckStatus());
        assertEquals(QuestionStatus.DUPLICATE, report.resultingStatus());
        assertEquals(0, llm.solverCalls, "Câu trùng không bao giờ gọi Solver");
        assertNoAnswerCheckRowSaved();
        QualityCheckResult dedupRow = savedDedupRow();
        assertFalse(dedupRow.getPassed());
        assertEquals(5, dedupRow.getComparedToQuestionId());
        assertEquals(5, report.dedup().comparedToQuestionId());
    }

    // ---------- Đường duy nhất tới QC_PASSED ----------

    @Test
    void noDuplicateThenConsistentAnswerCheckReachesQcPassed() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(solverJson("A"));
        boolean[] dedupRanBeforeSolver = {false};
        when(repository.findDedupCandidateRows(any(), any(), any(), any())).thenAnswer(inv -> {
            dedupRanBeforeSolver[0] = llm.solverCalls == 0;
            return List.of();
        });

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertTrue(dedupRanBeforeSolver[0], "Dedup phải chạy trước Solver");
        assertEquals(DedupStatus.NO_DUPLICATE, report.dedupStatus());
        assertEquals(AnswerCheckStatus.CONSISTENT, report.answerCheckStatus());
        assertEquals(QuestionStatus.QC_PASSED, report.resultingStatus());
        assertEquals(1, llm.solverCalls);
        assertTrue(savedDedupRow().getPassed());
        QualityCheckResult row = savedAnswerCheckRow();
        assertTrue(row.getPassed());
        assertEquals(AnswerChoice.A, row.getJudgeAnswer());
        assertEquals(SOLVER_MODEL, row.getJudgeModel());
    }

    // ---------- Nhánh answer_check sai (INCONSISTENT) ----------

    @Test
    void solverDisagreeingWithKeyMustStayGeneratedAndRecordFailedRow() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(solverJson("B"));

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertEquals(AnswerCheckStatus.INCONSISTENT, report.answerCheckStatus());
        assertEquals(DedupStatus.NO_DUPLICATE, report.dedupStatus());

        assertEquals(QuestionStatus.GENERATED, report.resultingStatus());
        QualityCheckResult row = savedAnswerCheckRow();
        assertFalse(row.getPassed());
        assertEquals(AnswerChoice.B, row.getJudgeAnswer());
    }

    @Test
    void solverAnsweringNoneMustStayGeneratedAndRecordFailedRow() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(solverJson("none"));

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertEquals(AnswerCheckStatus.INCONSISTENT, report.answerCheckStatus());
        assertEquals(DedupStatus.NO_DUPLICATE, report.dedupStatus());

        assertEquals(QuestionStatus.GENERATED, report.resultingStatus());
        QualityCheckResult row = savedAnswerCheckRow();
        assertFalse(row.getPassed());
        assertEquals(AnswerChoice.NONE, row.getJudgeAnswer());
    }

    @Test
    void solverAnsweringMultipleMustStayGeneratedAndRecordFailedRow() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(solverJson("multiple"));

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertEquals(AnswerCheckStatus.INCONSISTENT, report.answerCheckStatus());
        assertEquals(DedupStatus.NO_DUPLICATE, report.dedupStatus());

        assertEquals(QuestionStatus.GENERATED, report.resultingStatus());
        QualityCheckResult row = savedAnswerCheckRow();
        assertFalse(row.getPassed());
        assertEquals(AnswerChoice.MULTIPLE, row.getJudgeAnswer());
    }

    // ---------- Nhánh lỗi (ERROR): Solver ném lỗi / trả kết quả không hợp lệ ----------

    @Test
    void solverNetworkErrorMustStayGeneratedWithoutAnswerCheckRow() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.throwing(
                () -> new RuntimeException("HTTP 503 từ Gemini API: UNAVAILABLE"));

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertEquals(AnswerCheckStatus.ERROR, report.answerCheckStatus());
        assertEquals(DedupStatus.NO_DUPLICATE, report.dedupStatus());

        assertEquals(QuestionStatus.GENERATED, report.resultingStatus(),
                "Solver lỗi không được coi là đã kiểm tra thành công");
        assertNoAnswerCheckRowSaved();
    }

    @Test
    void solverInvalidJsonEscapeMustStayGeneratedWithoutAnswerCheckRow() {
        // Ca thật từ llm-probe: LaTeX trong reasoning làm hỏng JSON (Invalid \escape).
        // Dùng \sqrt vì \s không phải escape JSON hợp lệ (còn \f trong \frac lại hợp lệ = form feed).
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(
                "{\"reasoning\": \"Ta có \\sqrt{4} = 2\", \"judge_answer\": \"A\"}");

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertEquals(AnswerCheckStatus.ERROR, report.answerCheckStatus());
        assertEquals(DedupStatus.NO_DUPLICATE, report.dedupStatus());

        assertEquals(QuestionStatus.GENERATED, report.resultingStatus());
        assertNoAnswerCheckRowSaved();
    }

    @Test
    void solverMissingJudgeAnswerMustStayGeneratedWithoutAnswerCheckRow() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning("{\"reasoning\": \"Không kết luận được\"}");

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertEquals(AnswerCheckStatus.ERROR, report.answerCheckStatus());
        assertEquals(DedupStatus.NO_DUPLICATE, report.dedupStatus());

        assertEquals(QuestionStatus.GENERATED, report.resultingStatus());
        assertNoAnswerCheckRowSaved();
    }

    @Test
    void solverUnknownJudgeAnswerValueMustStayGeneratedWithoutAnswerCheckRow() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(solverJson("E"));

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertEquals(AnswerCheckStatus.ERROR, report.answerCheckStatus());
        assertEquals(DedupStatus.NO_DUPLICATE, report.dedupStatus());

        assertEquals(QuestionStatus.GENERATED, report.resultingStatus());
        assertNoAnswerCheckRowSaved();
    }

    // ---------- Nhánh không chạy (NOT_RUN): thiếu cấu hình / bị tắt ----------

    @Test
    void missingApiKeyMustStayGeneratedAndNeverCallSolver() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.notConfigured();

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertEquals(AnswerCheckStatus.NOT_RUN, report.answerCheckStatus());
        assertEquals(DedupStatus.NO_DUPLICATE, report.dedupStatus());

        assertEquals(QuestionStatus.GENERATED, report.resultingStatus(),
                "Thiếu API key không được coi là một lần kiểm tra thành công");
        assertEquals(0, llm.solverCalls);
        assertNoAnswerCheckRowSaved();
    }

    @Test
    void answerCheckDisabledByCallerMustStayGeneratedAndNeverCallSolver() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(solverJson("A"));

        QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, false), SCOPE);

        assertEquals(AnswerCheckStatus.NOT_RUN, report.answerCheckStatus());
        assertEquals(DedupStatus.NO_DUPLICATE, report.dedupStatus());

        assertEquals(QuestionStatus.GENERATED, report.resultingStatus());
        assertEquals(0, llm.solverCalls);
        assertNoAnswerCheckRowSaved();
    }

    // ---------- Bất biến chung ----------

    // Sửa 2026-10-21 (quyết định 1): trước đây vòng lặp chỉ kiểm "không QC_PASSED" khi dedup chưa chạy.
    // Nay kiểm mọi kết luận dedup khác NO_DUPLICATE (thiếu phạm vi, lỗi, trùng) với mọi loại phản hồi Solver:
    // không QC_PASSED và Solver không bao giờ được gọi.
    @Test
    void noDedupOutcomeOtherThanNoDuplicateMayReachQcPassedOrCallSolver() {
        List<Supplier<FakeSolverLlmClient>> clients = List.of(
                () -> FakeSolverLlmClient.returning(solverJson("A")),
                () -> FakeSolverLlmClient.returning(solverJson("C")),
                () -> FakeSolverLlmClient.returning("không phải JSON"),
                () -> FakeSolverLlmClient.throwing(() -> new IllegalStateException("lỗi bất kỳ")),
                FakeSolverLlmClient::notConfigured);
        for (Supplier<FakeSolverLlmClient> client : clients) {
            for (String dedupCase : List.of("missing-scope", "dedup-error", "duplicate")) {
                setUp();
                FakeSolverLlmClient llm = client.get();
                DedupScope scope = SCOPE;
                if (dedupCase.equals("missing-scope")) {
                    scope = null;
                } else if (dedupCase.equals("dedup-error")) {
                    when(repository.findDedupCandidateRows(any(), any(), any(), any()))
                            .thenThrow(new IllegalStateException("lỗi đọc ứng viên"));
                } else {
                    when(repository.findDedupCandidateRows(any(), any(), any(), any()))
                            .thenReturn(List.<Object[]>of(duplicateCandidateRow(5)));
                }
                QcPipelineReport report = orchestrator(llm).evaluateAndSave(validInput(Option.A, true), scope);
                assertNotEquals(QuestionStatus.QC_PASSED, report.resultingStatus(), dedupCase);
                assertEquals(0, llm.solverCalls, "Solver bị gọi khi dedup = " + dedupCase);
            }
        }
    }

    // ---------- responseSchema ----------

    @Test
    void solverMustSendResponseSchemaThroughSchemaAwareCall() {
        FakeSolverLlmClient llm = FakeSolverLlmClient.returning(solverJson("A"));

        orchestrator(llm).evaluateAndSave(validInput(Option.A, true), SCOPE);

        assertEquals(1, llm.solverCalls);
        assertEquals(AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON, llm.lastSchema,
                "Solver phải gửi responseSchema qua callJsonModeWithSchema");
        assertEquals(SOLVER_MODEL, llm.lastModel);
        assertTrue(llm.lastSchema.contains("\"judge_answer\""));
        for (String value : List.of("\"A\"", "\"B\"", "\"C\"", "\"D\"", "\"multiple\"", "\"none\"")) {
            assertTrue(llm.lastSchema.contains(value), "Schema phải giới hạn judge_answer gồm " + value);
        }
    }

    /**
     * Chặn đường gọi mạng: mọi method public của LlmClient trả LlmCallResult (đều đi tới HTTP thật)
     * phải được bản giả override. Nếu sau này thêm method mới mà quên override, test này đỏ.
     */
    @Test
    void fakeLlmClientMustOverrideEveryNetworkMethod() {
        List<Method> networkMethods = Arrays.stream(LlmClient.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getReturnType() == LlmCallResult.class)
                .toList();
        assertFalse(networkMethods.isEmpty());
        for (Method m : networkMethods) {
            try {
                Method override = FakeSolverLlmClient.class.getDeclaredMethod(m.getName(), m.getParameterTypes());
                assertEquals(FakeSolverLlmClient.class, override.getDeclaringClass());
            } catch (NoSuchMethodException e) {
                throw new AssertionError("FakeSolverLlmClient chưa override " + m + " -> có thể gọi HTTP thật", e);
            }
        }
        // isConfigured cũng phải bị override để không đọc biến môi trường thật
        try {
            FakeSolverLlmClient.class.getDeclaredMethod("isConfigured");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("FakeSolverLlmClient phải override isConfigured()", e);
        }
    }

    // ---------- Hỗ trợ ----------

    private QcOrchestrator orchestrator(FakeSolverLlmClient llm) {
        AnswerSolverChecker solver = new AnswerSolverChecker(llm, new PromptBuilder(), SOLVER_MODEL);
        return new QcOrchestrator(new FormatChecker(), solver, repository, new DedupService(repository));
    }

    /** Một dòng ứng viên trùng nội dung với validInput (thứ tự phương án khác). */
    private static Object[] duplicateCandidateRow(int id) {
        return new Object[]{id, "Nghiệm của phương trình $\\frac{x}{2} = 1$ là:",
                "$x = 0$", "$x = 4$", "$x = 1$", "$x = 2$"};
    }

    private void assertNoDedupRowSaved() {
        verify(repository, never()).saveAndFlush(any(QualityCheckResult.class));
    }

    private QualityCheckResult savedDedupRow() {
        ArgumentCaptor<QualityCheckResult> captor = ArgumentCaptor.forClass(QualityCheckResult.class);
        verify(repository, atLeastOnce()).saveAndFlush(captor.capture());
        List<QualityCheckResult> rows = captor.getAllValues().stream()
                .filter(r -> r.getCheckType() == CheckType.DEDUP)
                .toList();
        assertEquals(1, rows.size(), "Phải lưu đúng 1 dòng dedup");
        return rows.get(0);
    }

    private static QuestionQcInput validInput(Option correctOption, boolean runAnswerCheck) {
        return new QuestionQcInput(
                42,
                true,
                "Nghiệm của phương trình $\\frac{x}{2} = 1$ là:",
                "$x = 2$",
                "$x = 1$",
                "$x = 4$",
                "$x = 0$",
                correctOption,
                "Nhân hai vế với $2$ được $x = 2$.",
                true,
                runAnswerCheck);
    }

    private static String solverJson(String judgeAnswer) {
        return "{\"reasoning\": \"Nhân hai vế với 2 được x = 2\", \"judge_answer\": \"" + judgeAnswer + "\"}";
    }

    private List<QualityCheckResult> savedRows() {
        ArgumentCaptor<QualityCheckResult> captor = ArgumentCaptor.forClass(QualityCheckResult.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private QualityCheckResult savedAnswerCheckRow() {
        List<QualityCheckResult> rows = savedRows().stream()
                .filter(r -> r.getCheckType() == CheckType.ANSWER_CHECK)
                .toList();
        assertEquals(1, rows.size(), "Phải lưu đúng 1 dòng answer_check");
        return rows.get(0);
    }

    private void assertNoAnswerCheckRowSaved() {
        boolean anyAnswerCheck = savedRows().stream()
                .anyMatch(r -> r.getCheckType() == CheckType.ANSWER_CHECK);
        assertFalse(anyAnswerCheck, "Không được lưu dòng answer_check khi Solver không có kết quả hợp lệ");
    }

    /** LlmClient giả: không bao giờ gọi mạng. */
    static final class FakeSolverLlmClient extends LlmClient {
        private final boolean configured;
        private final String solverResponse;
        private final Supplier<RuntimeException> failure;
        int solverCalls;

        private FakeSolverLlmClient(boolean configured, String solverResponse, Supplier<RuntimeException> failure) {
            super("FAKE_KEY_FOR_TESTS", "fake-generator-model");
            this.configured = configured;
            this.solverResponse = solverResponse;
            this.failure = failure;
        }

        static FakeSolverLlmClient returning(String json) {
            return new FakeSolverLlmClient(true, json, null);
        }

        static FakeSolverLlmClient throwing(Supplier<RuntimeException> failure) {
            return new FakeSolverLlmClient(true, null, failure);
        }

        static FakeSolverLlmClient notConfigured() {
            return new FakeSolverLlmClient(false, null, null);
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }


        @Override
        public LlmCallResult callJsonMode(String prompt, String modelName, Double temperature) {
            // Solver phải dùng đường có schema; gọi vào đây coi như lỗi.
            throw new UnsupportedOperationException("Solver phải gọi callJsonModeWithSchema");
        }

        String lastSchema;
        String lastModel;

        @Override
        public LlmCallResult callJsonModeWithSchema(
                String prompt, String modelName, Double temperature, String responseSchemaJson) {
            solverCalls++;
            lastSchema = responseSchemaJson;
            lastModel = modelName;
            if (failure != null) {
                throw failure.get();
            }
            return new LlmCallResult(solverResponse, 10, 1.0, 0.0, modelName);
        }
    }
}
