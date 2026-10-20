package com.datn.mcq.qc.orchestrator;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.checker.FormatChecker;
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
            return new QcPipelineReport(
                    QuestionStatus.FORMAT_FAILED,
                    false,
                    formatOutcome.errors(),
                    null,
                    null,
                    null,
                    null
            );
        }

        Boolean answerCheckPassed = null;
        AnswerChoice judgeAnswer = null;
        String judgeModel = null;
        String solverReasoning = null;

        if (input.runAnswerCheck() && answerSolverChecker.canRunOnline()) {
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
            } catch (RuntimeException ex) {
                // Nếu Solver trả về JSON lỗi thoát ký tự hoặc lỗi mạng, không lưu dòng answer_check rỗng
                // để đảm bảo chk_qcr_judge_only_answer_check luôn thỏa mãn.
                solverReasoning = "Không hoàn tất answer_check: " + ex.getMessage();
            }
        }

        return new QcPipelineReport(
                QuestionStatus.QC_PASSED,
                true,
                List.of(),
                answerCheckPassed,
                judgeAnswer,
                judgeModel,
                solverReasoning
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

