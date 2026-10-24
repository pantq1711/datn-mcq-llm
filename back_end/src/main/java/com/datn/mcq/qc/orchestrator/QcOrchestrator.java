package com.datn.mcq.qc.orchestrator;

import com.datn.mcq.common.util.JsonStringArray;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.SaJudgeKind;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupOutcome;
import com.datn.mcq.qc.dto.DedupScope;
import com.datn.mcq.qc.dto.DedupStatus;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.dto.SolverVerdict;
import com.datn.mcq.qc.dto.TfJudgement;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import com.datn.mcq.qc.service.AnswerCheckRecorder;
import com.datn.mcq.qc.service.DedupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Điều phối tuần tự pipeline QC: format -> dedup -> answer_check.
 * Tuân thủ quy tắc 4 trong docs/architecture/backend-structure.md:
 * qc không đọc entity generated_question; generation truyền nội dung câu hỏi và phạm vi dedup vào,
 * nhận báo cáo rồi tự cập nhật status.
 *
 * <p>Solver (gọi LLM, có thể tốn phí) chỉ được gọi khi dedup kết luận NO_DUPLICATE (quyết định 1, 4).
 */
@Service
public class QcOrchestrator {

    private final FormatChecker formatChecker;
    private final AnswerSolverChecker answerSolverChecker;
    private final QualityCheckResultRepository qualityCheckResultRepository;
    private final DedupService dedupService;
    private final AnswerCheckRecorder answerCheckRecorder;

    /**
     * Chữ ký trước GĐ3, giữ lại cho các test dựng tay QcOrchestrator với câu MCQ. Không dùng trong mã chính:
     * Spring dùng constructor 5 tham số.
     *
     * <p>Constructor này không có {@link AnswerCheckRecorder}, nên câu Đúng/Sai (tf) đi qua nó luôn có
     * answer_check ERROR mà không gọi Solver. Câu Trả lời ngắn (sa) không cần bộ ghi đó nên vẫn chạy Solver như
     * constructor đầy đủ, nhưng chỉ nên dùng constructor này cho test MCQ.
     *
     * @deprecated chỉ dùng cho test MCQ; dùng {@link #QcOrchestrator(FormatChecker, AnswerSolverChecker,
     *             QualityCheckResultRepository, DedupService, AnswerCheckRecorder)}.
     */
    @Deprecated
    public QcOrchestrator(
            FormatChecker formatChecker,
            AnswerSolverChecker answerSolverChecker,
            QualityCheckResultRepository qualityCheckResultRepository,
            DedupService dedupService) {
        this(formatChecker, answerSolverChecker, qualityCheckResultRepository, dedupService, null);
    }

    @Autowired
    public QcOrchestrator(
            FormatChecker formatChecker,
            AnswerSolverChecker answerSolverChecker,
            QualityCheckResultRepository qualityCheckResultRepository,
            DedupService dedupService,
            AnswerCheckRecorder answerCheckRecorder) {
        this.formatChecker = formatChecker;
        this.answerSolverChecker = answerSolverChecker;
        this.qualityCheckResultRepository = qualityCheckResultRepository;
        this.dedupService = dedupService;
        this.answerCheckRecorder = answerCheckRecorder;
    }

    /**
     * @param scope phạm vi dedup do nơi gọi tính (generation); null hoặc sai hình dạng thì dedup = ERROR,
     *              câu giữ generated và Solver không được gọi
     */
    public QcPipelineReport evaluateAndSave(QuestionQcInput input, DedupScope scope) {
        FormatChecker.FormatCheckOutcome formatOutcome = formatChecker.check(input);
        QualityCheckResult formatRow = upsertCheckResult(
                input.questionId(),
                input.questionType(),
                CheckType.FORMAT,
                formatOutcome.passed(),
                null,
                null,
                null,
                null,
                formatOutcome.answerWarnings(),
                // V8 (ngày 23/10): lý do format không qua (kể cả lỗi giải mã JSON) lưu ở cột errors; NULL khi qua.
                formatOutcome.passed() ? null : formatOutcome.errors()
        );
        // V5 (Q8 ngày 22/10): cảnh báo lưu ở cột warnings của dòng format; báo cáo đọc lại từ chính cột đó.
        List<String> answerWarnings = readWarnings(formatRow.getWarnings());

        if (!formatOutcome.passed()) {
            // Format lỗi thì dừng sớm, không gọi Solver.
            return new QcPipelineReport(
                    QcStatusDecider.decide(false, DedupStatus.NOT_RUN, AnswerCheckStatus.NOT_RUN),
                    false,
                    formatOutcome.errors(),
                    null,
                    null,
                    null,
                    null,
                    AnswerCheckStatus.NOT_RUN,
                    DedupStatus.NOT_RUN,
                    null,
                    answerWarnings,
                    null,
                    null
            );
        }

        DedupOutcome dedupOutcome = runDedup(input, scope);
        DedupStatus dedupStatus = dedupOutcome.status();
        if (dedupStatus != DedupStatus.NO_DUPLICATE) {
            // Trùng, lỗi hoặc thiếu phạm vi: không gọi Solver. Trùng -> DUPLICATE; còn lại -> GENERATED.
            String reason = dedupStatus == DedupStatus.DUPLICATE
                    ? "answer_check không chạy: câu bị dedup đánh dấu trùng (" + dedupOutcome.detail() + ")"
                    : "answer_check không chạy: dedup chưa kết luận không trùng (" + dedupOutcome.detail() + ")";
            return new QcPipelineReport(
                    QcStatusDecider.decide(true, dedupStatus, AnswerCheckStatus.NOT_RUN),
                    true,
                    List.of(),
                    null,
                    null,
                    null,
                    reason,
                    AnswerCheckStatus.NOT_RUN,
                    dedupStatus,
                    dedupOutcome,
                    answerWarnings,
                    null,
                    null
            );
        }

        AnswerCheckStatus answerCheckStatus;
        Boolean answerCheckPassed = null;
        AnswerChoice judgeAnswer = null;
        String judgeModel = null;
        String solverReasoning;
        SaJudgeKind saJudgeKind = null;
        String saJudgeAnswer = null;
        List<TfJudgement> tfJudgements = null;

        if (!input.runAnswerCheck()) {
            answerCheckStatus = AnswerCheckStatus.NOT_RUN;
            solverReasoning = "answer_check không chạy: bị tắt bởi nơi gọi";
        } else if (!answerSolverChecker.canRunOnline()) {
            // Thiếu API key là lỗi cấu hình, không phải một lần kiểm tra thành công.
            answerCheckStatus = AnswerCheckStatus.NOT_RUN;
            solverReasoning = "answer_check không chạy: chưa cấu hình API key cho Solver";
        } else {
            // W1-2: gọi Solver (callSolver) rồi ghi kết quả (recordSolver), như trước khi tách. Lỗi mạng/HTTP, JSON
            // hỏng, thiếu hoặc sai judge_*, hoặc lỗi khi lưu kết quả: không có kết quả hợp lệ -> ERROR, không lưu dòng
            // answer_check (chk_qcr_judge_only_answer_check bắt buộc kết quả Solver).
            try {
                SolverVerdict verdict = callSolver(input);
                recordSolver(input.questionId(), verdict);
                answerCheckPassed = verdict.passed();
                judgeAnswer = verdict.judgeAnswer();
                judgeModel = verdict.judgeModel();
                solverReasoning = verdict.reasoning();
                saJudgeKind = verdict.saJudgeKind();
                saJudgeAnswer = verdict.saJudgeAnswer();
                tfJudgements = verdict.tfJudgements();
                answerCheckStatus = verdict.status();
            } catch (RuntimeException ex) {
                answerCheckStatus = AnswerCheckStatus.ERROR;
                answerCheckPassed = null;
                judgeAnswer = null;
                judgeModel = null;
                saJudgeKind = null;
                saJudgeAnswer = null;
                tfJudgements = null;
                solverReasoning = "Không hoàn tất answer_check: " + ex.getMessage();
            }
        }

        return new QcPipelineReport(
                QcStatusDecider.decide(true, dedupStatus, answerCheckStatus),
                true,
                List.of(),
                answerCheckPassed,
                judgeAnswer,
                judgeModel,
                solverReasoning,
                answerCheckStatus,
                dedupStatus,
                dedupOutcome,
                answerWarnings,
                saJudgeKind,
                saJudgeAnswer,
                tfJudgements
        );
    }

    /** Lý do Solver không chạy được vì thiếu cấu hình (lỗi cấu hình, không phải lỗi tạm thời: không thử lại). */
    public static final String SOLVER_NOT_CONFIGURED = "answer_check không chạy: chưa cấu hình API key cho Solver";

    /** Solver gọi được (đã cấu hình API key). */
    public boolean solverAvailable() {
        return answerSolverChecker.canRunOnline();
    }

    /**
     * Gọi Solver cho một câu (gọi mạng, KHÔNG ghi DB, không mở transaction). Ném lỗi khi không có kết quả hợp lệ (lỗi
     * mạng/HTTP, hết thời gian chờ, JSON hỏng, thiếu hoặc sai judge_*), nơi gọi coi là ERROR.
     * <ul>
     *   <li>mcq: so judge_answer với correct_option;</li>
     *   <li>sa (V4): so đáp số theo giá trị; ambiguous và không phân tích được là kết quả hợp lệ không khớp;</li>
     *   <li>tf (GĐ3): khớp khi đủ 4 ý khớp; có ý lệch hoặc undecidable là không khớp.</li>
     * </ul>
     * Solver chỉ thấy đề và phương án/ý, không thấy đáp án, nhãn hay lời giải của người soạn.
     */
    public SolverVerdict callSolver(QuestionQcInput input) {
        if (!answerSolverChecker.canRunOnline()) {
            throw new IllegalStateException(SOLVER_NOT_CONFIGURED);
        }
        if (input.questionType() == QuestionType.TF) {
            if (answerCheckRecorder == null) {
                throw new IllegalStateException("Chưa có bộ ghi kết quả Solver câu đúng/sai");
            }
            AnswerSolverChecker.TfSolverOutcome tf = answerSolverChecker.checkTrueFalse(
                    input.questionText(), input.statements(), input.statementLabels());
            return new SolverVerdict(QuestionType.TF, tf.passed(), tf.judgeModel(), tf.reasoning(), null, null, null,
                    tf.judgeLabels());
        }
        if (input.questionType() == QuestionType.SA) {
            AnswerSolverChecker.SaSolverOutcome sa = answerSolverChecker.checkShortAnswer(
                    input.questionText(), input.saAnswer());
            return new SolverVerdict(QuestionType.SA, sa.passed(), sa.judgeModel(), sa.reasoning(), null,
                    sa.judgeKind(), sa.judgeAnswer(), null);
        }
        AnswerSolverChecker.SolverCheckOutcome mcq = answerSolverChecker.check(
                input.questionText(), input.optionA(), input.optionB(), input.optionC(), input.optionD(),
                input.correctOption());
        return new SolverVerdict(QuestionType.MCQ, mcq.passed(), mcq.judgeModel(), mcq.reasoning(), mcq.judgeAnswer(),
                null, null, null);
    }

    /**
     * Ghi kết quả Solver hợp lệ của một câu: dòng answer_check (tf kèm 4 dòng answer_check_statement, cùng transaction).
     * Không gọi mạng. Gọi từ bean khác thì chạy trong transaction của nơi gọi (job answer_check: cùng transaction với
     * trạng thái câu và trạng thái job).
     */
    @Transactional
    public void recordSolver(Integer questionId, SolverVerdict verdict) {
        switch (verdict.questionType()) {
            case TF -> {
                if (answerCheckRecorder == null) {
                    throw new IllegalStateException("Chưa có bộ ghi kết quả Solver câu đúng/sai");
                }
                answerCheckRecorder.recordTrueFalse(questionId, verdict.passed(), verdict.judgeModel(),
                        verdict.tfLabels());
            }
            case SA -> upsertCheckResult(questionId, QuestionType.SA, CheckType.ANSWER_CHECK, verdict.passed(), null,
                    verdict.judgeModel(), verdict.saJudgeKind(), verdict.saJudgeAnswer());
            case MCQ -> upsertCheckResult(questionId, QuestionType.MCQ, CheckType.ANSWER_CHECK, verdict.passed(),
                    verdict.judgeAnswer(), verdict.judgeModel(), null, null);
        }
    }

    /**
     * W1-2 (D5): chép kết quả Solver của {@code fromQuestionId} sang {@code toQuestionId} khi bản sửa chỉ đổi lời giải.
     * Solver không thấy lời giải nên nội dung nó đã xét không đổi. Không gọi mạng. Rỗng khi câu nguồn chưa có dòng
     * answer_check (Solver chưa chạy hoặc lỗi).
     */
    @Transactional
    public Optional<AnswerCheckStatus> copyAnswerCheck(Integer fromQuestionId, Integer toQuestionId) {
        Optional<QualityCheckResult> source = qualityCheckResultRepository
                .findByQuestionIdAndCheckType(fromQuestionId, CheckType.ANSWER_CHECK);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        QualityCheckResult r = source.get();
        List<TruthChoice> labels = null;
        if (r.getQuestionType() == QuestionType.TF) {
            if (answerCheckRecorder == null) {
                throw new IllegalStateException("Chưa có bộ ghi kết quả Solver câu đúng/sai");
            }
            labels = answerCheckRecorder.judgeLabels(r.getId());
        }
        SolverVerdict copy = new SolverVerdict(r.getQuestionType(), Boolean.TRUE.equals(r.getPassed()),
                r.getJudgeModel(), null, r.getJudgeAnswer(), r.getJudgeSaKind(), r.getJudgeSaAnswer(), labels);
        recordSolver(toQuestionId, copy);
        return Optional.of(copy.status());
    }

    /**
     * Trạng thái của câu khi bước answer_check vừa có kết quả {@code answerCheck}, theo các dòng format và dedup đã lưu
     * (bảng quyết định QcStatusDecider, Q-2). Thiếu dòng dedup là dedup chưa kết luận (ERROR).
     */
    @Transactional(readOnly = true)
    public QuestionStatus decideWithStoredChecks(Integer questionId, AnswerCheckStatus answerCheck) {
        Optional<QualityCheckResult> format = qualityCheckResultRepository
                .findByQuestionIdAndCheckType(questionId, CheckType.FORMAT);
        Optional<QualityCheckResult> dedup = qualityCheckResultRepository
                .findByQuestionIdAndCheckType(questionId, CheckType.DEDUP);
        DedupStatus dedupStatus = dedup.isEmpty() ? DedupStatus.ERROR
                : Boolean.TRUE.equals(dedup.get().getPassed()) ? DedupStatus.NO_DUPLICATE : DedupStatus.DUPLICATE;
        boolean formatPassed = format.isPresent() && Boolean.TRUE.equals(format.get().getPassed());
        return QcStatusDecider.decide(formatPassed, dedupStatus, answerCheck);
    }

    /**
     * Gọi bước dedup (transaction ngắn riêng, không gọi mạng). Mọi lỗi được quy về ERROR:
     * transaction của DedupService đã rollback nên không có dòng dedup nào được ghi.
     */
    private DedupOutcome runDedup(QuestionQcInput input, DedupScope scope) {
        try {
            DedupOutcome outcome = dedupService.checkAndRecord(new DedupCandidate(
                    input.questionId(),
                    input.questionText(),
                    input.optionA(),
                    input.optionB(),
                    input.optionC(),
                    input.optionD(),
                    input.questionType(),
                    input.saAnswer(),
                    input.statements()
            ), scope);
            return outcome != null ? outcome : DedupOutcome.error("DedupService không trả kết quả");
        } catch (RuntimeException ex) {
            return DedupOutcome.error("Không hoàn tất dedup: " + ex.getMessage());
        }
    }

    /** Ghi kết quả một bước QC của câu mcq (chữ ký trước V4). */
    @Transactional
    public QualityCheckResult upsertCheckResult(
            Integer questionId,
            CheckType checkType,
            boolean passed,
            AnswerChoice judgeAnswer,
            String judgeModel) {
        return upsertCheckResult(questionId, QuestionType.MCQ, checkType, passed, judgeAnswer, judgeModel, null, null);
    }

    /**
     * Ghi kết quả một bước QC (V4). question_type phải bằng dạng của câu (khóa ngoại kép). Kết quả Solver chỉ ghi
     * cho answer_check: judgeAnswer với mcq, judgeSaKind/judgeSaAnswer với sa.
     */
    @Transactional
    public QualityCheckResult upsertCheckResult(
            Integer questionId,
            QuestionType questionType,
            CheckType checkType,
            boolean passed,
            AnswerChoice judgeAnswer,
            String judgeModel,
            SaJudgeKind judgeSaKind,
            String judgeSaAnswer) {
        return upsertCheckResult(questionId, questionType, checkType, passed, judgeAnswer, judgeModel, judgeSaKind,
                judgeSaAnswer, null);
    }

    /**
     * Như trên, kèm cảnh báo (V5): chỉ ghi vào dòng format (rỗng hoặc null thì cột để NULL); dòng khác luôn NULL.
     * Ghi đè dòng format cũ nên lần chạy lại QC thay cả cảnh báo.
     */
    @Transactional
    public QualityCheckResult upsertCheckResult(
            Integer questionId,
            QuestionType questionType,
            CheckType checkType,
            boolean passed,
            AnswerChoice judgeAnswer,
            String judgeModel,
            SaJudgeKind judgeSaKind,
            String judgeSaAnswer,
            List<String> warnings) {
        return upsertCheckResult(questionId, questionType, checkType, passed, judgeAnswer, judgeModel, judgeSaKind,
                judgeSaAnswer, warnings, null);
    }

    /**
     * Như trên, kèm lý do không qua (V8): chỉ ghi vào dòng format khi passed = false (rỗng thì NULL); dòng khác và dòng
     * format đã qua luôn NULL.
     */
    @Transactional
    public QualityCheckResult upsertCheckResult(
            Integer questionId,
            QuestionType questionType,
            CheckType checkType,
            boolean passed,
            AnswerChoice judgeAnswer,
            String judgeModel,
            SaJudgeKind judgeSaKind,
            String judgeSaAnswer,
            List<String> warnings,
            List<String> errors) {
        QualityCheckResult qcr = qualityCheckResultRepository
                .findByQuestionIdAndCheckType(questionId, checkType)
                .orElseGet(QualityCheckResult::new);
        qcr.setQuestionId(questionId);
        qcr.setQuestionType(questionType != null ? questionType : QuestionType.MCQ);
        qcr.setCheckType(checkType);
        qcr.setPassed(passed);
        if (checkType == CheckType.ANSWER_CHECK) {
            qcr.setJudgeAnswer(judgeAnswer);
            qcr.setJudgeModel(judgeModel);
            qcr.setJudgeSaKind(judgeSaKind);
            qcr.setJudgeSaAnswer(judgeSaAnswer);
        } else {
            qcr.setJudgeAnswer(null);
            qcr.setJudgeModel(null);
            qcr.setJudgeSaKind(null);
            qcr.setJudgeSaAnswer(null);
        }
        qcr.setWarnings(checkType == CheckType.FORMAT ? writeWarnings(warnings) : null);
        qcr.setErrors(checkType == CheckType.FORMAT && !passed ? JsonStringArray.write(errors) : null);
        qcr.setCheckedAt(LocalDateTime.now());
        return qualityCheckResultRepository.save(qcr);
    }

    /** Mảng JSON các chuỗi; null khi không có cảnh báo. */
    static String writeWarnings(List<String> warnings) {
        return JsonStringArray.write(warnings);
    }

    /** Đọc lại cột warnings (mảng JSON chuỗi); null hoặc rỗng thì danh sách rỗng. */
    public static List<String> readWarnings(String json) {
        return JsonStringArray.read(json);
    }

    /**
     * Xóa kết quả dedup và answer_check cũ của một câu trước khi chạy lại QC (quy tắc QC-RERUN),
     * để lần chạy lại bị lỗi không để lại dòng "passed" cũ dễ hiểu nhầm là thành công. Dòng format không xóa: lần chạy lại ghi đè nó.
     * Không có bảng lịch sử nên kết quả của lần chạy trước không được giữ lại.
     */
    public int clearDedupAndAnswerCheckResults(Integer questionId) {
        return qualityCheckResultRepository.deleteByQuestionIdAndCheckTypes(
                questionId, List.of(CheckType.DEDUP, CheckType.ANSWER_CHECK));
    }

    @Transactional(readOnly = true)
    public List<QualityCheckResult> getResultsForQuestion(Integer questionId) {
        return qualityCheckResultRepository.findByQuestionIdOrderByIdAsc(questionId);
    }
}

