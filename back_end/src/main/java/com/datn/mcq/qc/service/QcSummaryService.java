package com.datn.mcq.qc.service;

import com.datn.mcq.common.util.JsonStringArray;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.SaJudgeKind;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.AnswerCheckStatementRepository;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tóm tắt kết quả QC đã lưu của một câu, dùng cho cờ "cần xem kỹ" ở chế độ giáo viên (phần C mục 3). Chỉ đọc. Không
 * dùng cho form chấm mù.
 */
@Service
public class QcSummaryService {

    public static final String SOLVER_NOT_RUN = "solver_not_run";
    public static final String SOLVER_INCONSISTENT = "solver_inconsistent";
    public static final String SOLVER_AMBIGUOUS = "solver_ambiguous";
    public static final String SOLVER_UNPARSEABLE = "solver_unparseable";
    public static final String STATEMENT_UNDECIDABLE = "statement_undecidable";
    public static final String DEDUP_NOT_RUN = "dedup_not_run";
    public static final String WARNING_PREFIX = "warning: ";

    /**
     * @param answerCheckPassed null khi không có dòng answer_check (Solver chưa chạy hoặc lỗi)
     * @param warnings          cảnh báo bước format (V5)
     */
    public record QcSummary(boolean dedupRan, Boolean answerCheckPassed, SaJudgeKind judgeSaKind,
                            boolean hasUndecidable, List<String> warnings) {

        /** Lý do "cần xem kỹ"; rỗng khi câu qua QC sạch. */
        public List<String> reviewReasons() {
            List<String> reasons = new ArrayList<>();
            if (answerCheckPassed == null) {
                reasons.add(SOLVER_NOT_RUN);
            } else if (!answerCheckPassed) {
                if (judgeSaKind == SaJudgeKind.AMBIGUOUS) {
                    reasons.add(SOLVER_AMBIGUOUS);
                } else if (judgeSaKind == SaJudgeKind.UNPARSEABLE) {
                    reasons.add(SOLVER_UNPARSEABLE);
                } else {
                    reasons.add(SOLVER_INCONSISTENT);
                }
            }
            if (hasUndecidable) {
                reasons.add(STATEMENT_UNDECIDABLE);
            }
            if (!dedupRan) {
                reasons.add(DEDUP_NOT_RUN);
            }
            for (String w : warnings) {
                reasons.add(WARNING_PREFIX + w);
            }
            return reasons;
        }
    }

    private final QualityCheckResultRepository resultRepository;
    private final AnswerCheckStatementRepository statementRepository;

    public QcSummaryService(QualityCheckResultRepository resultRepository,
                            AnswerCheckStatementRepository statementRepository) {
        this.resultRepository = resultRepository;
        this.statementRepository = statementRepository;
    }

    /** Lý do bước format không qua đã lưu (V8); rỗng khi câu qua format hoặc chưa có dòng format. */
    @Transactional(readOnly = true)
    public List<String> formatErrors(Integer questionId) {
        return JsonStringArray.read(resultRepository.findByQuestionIdAndCheckType(questionId, CheckType.FORMAT)
                .map(QualityCheckResult::getErrors).orElse(null));
    }

    @Transactional(readOnly = true)
    public QcSummary summarize(Integer questionId) {
        Optional<QualityCheckResult> format = resultRepository.findByQuestionIdAndCheckType(questionId, CheckType.FORMAT);
        Optional<QualityCheckResult> dedup = resultRepository.findByQuestionIdAndCheckType(questionId, CheckType.DEDUP);
        Optional<QualityCheckResult> answer =
                resultRepository.findByQuestionIdAndCheckType(questionId, CheckType.ANSWER_CHECK);
        boolean undecidable = answer.isPresent() && statementRepository
                .findByQcResultIdOrderByPositionAsc(answer.get().getId()).stream()
                .anyMatch(s -> s.getJudgeLabel() == TruthChoice.UNDECIDABLE);
        return new QcSummary(
                dedup.isPresent(),
                answer.map(QualityCheckResult::getPassed).orElse(null),
                answer.map(QualityCheckResult::getJudgeSaKind).orElse(null),
                undecidable,
                JsonStringArray.read(format.map(QualityCheckResult::getWarnings).orElse(null)));
    }
}
