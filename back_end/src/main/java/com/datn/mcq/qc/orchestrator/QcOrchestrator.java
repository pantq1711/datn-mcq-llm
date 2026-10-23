package com.datn.mcq.qc.orchestrator;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupOutcome;
import com.datn.mcq.qc.dto.DedupScope;
import com.datn.mcq.qc.dto.DedupStatus;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import com.datn.mcq.qc.service.DedupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    public QcOrchestrator(
            FormatChecker formatChecker,
            AnswerSolverChecker answerSolverChecker,
            QualityCheckResultRepository qualityCheckResultRepository,
            DedupService dedupService) {
        this.formatChecker = formatChecker;
        this.answerSolverChecker = answerSolverChecker;
        this.qualityCheckResultRepository = qualityCheckResultRepository;
        this.dedupService = dedupService;
    }

    /**
     * @param scope phạm vi dedup do nơi gọi tính (generation); null hoặc sai hình dạng thì dedup = ERROR,
     *              câu giữ generated và Solver không được gọi
     */
    public QcPipelineReport evaluateAndSave(QuestionQcInput input, DedupScope scope) {
        FormatChecker.FormatCheckOutcome formatOutcome = formatChecker.check(input);
        upsertCheckResult(
                input.questionId(),
                CheckType.FORMAT,
                formatOutcome.passed(),
                null,
                null
        );

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
                    dedupOutcome
            );
        }

        AnswerCheckStatus answerCheckStatus;
        Boolean answerCheckPassed = null;
        AnswerChoice judgeAnswer = null;
        String judgeModel = null;
        String solverReasoning;

        if (!input.runAnswerCheck()) {
            answerCheckStatus = AnswerCheckStatus.NOT_RUN;
            solverReasoning = "answer_check không chạy: bị tắt bởi nơi gọi";
        } else if (!answerSolverChecker.canRunOnline()) {
            // Thiếu API key là lỗi cấu hình, không phải một lần kiểm tra thành công.
            answerCheckStatus = AnswerCheckStatus.NOT_RUN;
            solverReasoning = "answer_check không chạy: chưa cấu hình API key cho Solver";
        } else {
            try {
                AnswerSolverChecker.SolverCheckOutcome solverOutcome = answerSolverChecker.check(
                        input.questionText(),
                        input.optionA(),
                        input.optionB(),
                        input.optionC(),
                        input.optionD(),
                        input.correctOption()
                );
                answerCheckPassed = solverOutcome.passed();
                judgeAnswer = solverOutcome.judgeAnswer();
                judgeModel = solverOutcome.judgeModel();
                solverReasoning = solverOutcome.reasoning();

                upsertCheckResult(
                        input.questionId(),
                        CheckType.ANSWER_CHECK,
                        solverOutcome.passed(),
                        solverOutcome.judgeAnswer(),
                        solverOutcome.judgeModel()
                );
                answerCheckStatus = solverOutcome.passed()
                        ? AnswerCheckStatus.CONSISTENT
                        : AnswerCheckStatus.INCONSISTENT;
            } catch (RuntimeException ex) {
                // Lỗi mạng/HTTP, JSON hỏng, thiếu hoặc sai judge_answer, hoặc lỗi khi lưu kết quả:
                // không có kết quả hợp lệ -> ERROR. Không lưu dòng answer_check
                // (chk_qcr_judge_only_answer_check bắt buộc judge_answer; lưu lỗi cần migration riêng).
                answerCheckStatus = AnswerCheckStatus.ERROR;
                answerCheckPassed = null;
                judgeAnswer = null;
                judgeModel = null;
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
                dedupOutcome
        );
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
                    input.optionD()
            ), scope);
            return outcome != null ? outcome : DedupOutcome.error("DedupService không trả kết quả");
        } catch (RuntimeException ex) {
            return DedupOutcome.error("Không hoàn tất dedup: " + ex.getMessage());
        }
    }

    @Transactional
    public QualityCheckResult upsertCheckResult(
            Integer questionId,
            CheckType checkType,
            boolean passed,
            AnswerChoice judgeAnswer,
            String judgeModel) {
        QualityCheckResult qcr = qualityCheckResultRepository
                .findByQuestionIdAndCheckType(questionId, checkType)
                .orElseGet(QualityCheckResult::new);
        qcr.setQuestionId(questionId);
        qcr.setCheckType(checkType);
        qcr.setPassed(passed);
        if (checkType == CheckType.ANSWER_CHECK) {
            qcr.setJudgeAnswer(judgeAnswer);
            qcr.setJudgeModel(judgeModel);
        } else {
            qcr.setJudgeAnswer(null);
            qcr.setJudgeModel(null);
        }
        qcr.setCheckedAt(LocalDateTime.now());
        return qualityCheckResultRepository.save(qcr);
    }

    /**
     * Xóa kết quả dedup và answer_check cũ của một câu trước khi chạy lại QC (V4), để lần chạy lại bị lỗi
     * không để lại dòng "passed" cũ dễ hiểu nhầm là thành công. Dòng format không xóa: lần chạy lại ghi đè nó.
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

