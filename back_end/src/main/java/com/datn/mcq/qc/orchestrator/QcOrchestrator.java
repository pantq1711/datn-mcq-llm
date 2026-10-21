package com.datn.mcq.qc.orchestrator;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupStatus;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Điều phối tuần tự pipeline QC: format -> (dedup) -> answer_check.
 * Tuân thủ quy tắc 4 trong docs/architecture/backend-structure.md:
 * qc không đọc entity generated_question; generation truyền nội dung câu hỏi vào và tự cập nhật status.
 */
@Service
public class QcOrchestrator {

    private final FormatChecker formatChecker;
    private final AnswerSolverChecker answerSolverChecker;
    private final QualityCheckResultRepository qualityCheckResultRepository;

    public QcOrchestrator(
            FormatChecker formatChecker,
            AnswerSolverChecker answerSolverChecker,
            QualityCheckResultRepository qualityCheckResultRepository) {
        this.formatChecker = formatChecker;
        this.answerSolverChecker = answerSolverChecker;
        this.qualityCheckResultRepository = qualityCheckResultRepository;
    }

    public QcPipelineReport evaluateAndSave(QuestionQcInput input) {
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
                    DedupStatus.NOT_RUN
            );
        }

        // Dedup chưa được triển khai: không giả định đã đạt (Q-2).
        DedupStatus dedupStatus = DedupStatus.NOT_RUN;

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
                dedupStatus
        );
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

    @Transactional(readOnly = true)
    public List<QualityCheckResult> getResultsForQuestion(Integer questionId) {
        return qualityCheckResultRepository.findByQuestionIdOrderByIdAsc(questionId);
    }
}

