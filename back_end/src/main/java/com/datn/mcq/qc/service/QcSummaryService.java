package com.datn.mcq.qc.service;

import com.datn.mcq.common.util.JsonStringArray;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.SaJudgeKind;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.generation.parser.LlmOutputRepair;
import com.datn.mcq.llm.prompt.TfLabelPattern;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.dto.GroundingVerdict;
import com.datn.mcq.qc.dto.TfJudgement;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.AnswerCheckStatementRepository;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import com.datn.mcq.qc.repository.QuestionGroundingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    /** Thẩm định 31/10: Solver xét từng phương án thấy từ 2 phương án đúng trở lên (mcq, judge_answer = multiple). */
    public static final String SOLVER_MULTIPLE_CORRECT = "solver_multiple_correct";
    /** Thẩm định 31/10: Solver xét từng phương án không thấy phương án nào đúng (mcq, judge_answer = none). */
    public static final String SOLVER_NO_CORRECT = "solver_no_correct";
    public static final String STATEMENT_UNDECIDABLE = "statement_undecidable";
    public static final String DEDUP_NOT_RUN = "dedup_not_run";
    public static final String WARNING_PREFIX = "warning: ";
    /** W1-2: dedup báo trùng (bản giáo viên sửa), dạng "duplicate: &lt;id câu bị trùng&gt;". */
    public static final String DUPLICATE_PREFIX = "duplicate: ";
    /** W2-3: QC "bám tài liệu" kết luận đáp án/lời giải/câu hỏi không khớp tài liệu (chỉ cảnh báo, câu giữ nguyên trạng thái). */
    public static final String GROUNDING_NOT_SUPPORTED = "grounding_not_supported";
    /** W2-3: QC "bám tài liệu" không kết luận được (đoạn tài liệu không đủ, hoặc bằng chứng không kiểm chứng được). */
    public static final String GROUNDING_UNKNOWN = "grounding_unknown";

    /**
     * @param answerCheckPassed null khi không có dòng answer_check (Solver chưa chạy hoặc lỗi)
     * @param warnings          cảnh báo bước format (V5)
     * @param duplicateOf       (W1-2) câu bị trùng khi dòng dedup không qua; null khi không trùng
     * @param grounding         (W2-3) kết luận "bám tài liệu"; null khi câu không có kết quả (không dùng tài liệu, chưa chấm
     *                          hoặc chấm lỗi)
     * @param judgeAnswer       mcq: đáp án Solver đã lưu (A-D, multiple, none); null với dạng khác hoặc khi chưa có dòng
     * @param solverOptionCheck true khi ghi chú Solver có khối xét từng phương án (Solver v3); chỉ khi đó multiple/none mới
     *                          tách thành lý do riêng, kết quả Solver cũ giữ {@code solver_inconsistent}
     */
    public record QcSummary(boolean dedupRan, Boolean answerCheckPassed, SaJudgeKind judgeSaKind,
                            boolean hasUndecidable, List<String> warnings, Integer duplicateOf,
                            GroundingVerdict grounding, AnswerChoice judgeAnswer, boolean solverOptionCheck) {

        public QcSummary(boolean dedupRan, Boolean answerCheckPassed, SaJudgeKind judgeSaKind,
                         boolean hasUndecidable, List<String> warnings, Integer duplicateOf,
                         GroundingVerdict grounding) {
            this(dedupRan, answerCheckPassed, judgeSaKind, hasUndecidable, warnings, duplicateOf, grounding, null,
                    false);
        }

        public QcSummary(boolean dedupRan, Boolean answerCheckPassed, SaJudgeKind judgeSaKind,
                         boolean hasUndecidable, List<String> warnings, Integer duplicateOf) {
            this(dedupRan, answerCheckPassed, judgeSaKind, hasUndecidable, warnings, duplicateOf, null);
        }

        public QcSummary(boolean dedupRan, Boolean answerCheckPassed, SaJudgeKind judgeSaKind,
                         boolean hasUndecidable, List<String> warnings) {
            this(dedupRan, answerCheckPassed, judgeSaKind, hasUndecidable, warnings, null, null);
        }

        /** Lý do "cần xem kỹ"; rỗng khi câu qua QC sạch. */
        public List<String> reviewReasons() {
            List<String> reasons = new ArrayList<>();
            if (duplicateOf != null) {
                // Câu trùng thì Solver không chạy (Q-2), không thêm solver_not_run.
                reasons.add(DUPLICATE_PREFIX + duplicateOf);
            } else if (answerCheckPassed == null) {
                reasons.add(SOLVER_NOT_RUN);
            } else if (!answerCheckPassed) {
                if (judgeSaKind == SaJudgeKind.AMBIGUOUS) {
                    reasons.add(SOLVER_AMBIGUOUS);
                } else if (judgeSaKind == SaJudgeKind.UNPARSEABLE) {
                    reasons.add(SOLVER_UNPARSEABLE);
                } else if (solverOptionCheck && judgeAnswer == AnswerChoice.MULTIPLE) {
                    reasons.add(SOLVER_MULTIPLE_CORRECT);
                } else if (solverOptionCheck && judgeAnswer == AnswerChoice.NONE) {
                    reasons.add(SOLVER_NO_CORRECT);
                } else {
                    reasons.add(SOLVER_INCONSISTENT);
                }
            }
            if (hasUndecidable) {
                reasons.add(STATEMENT_UNDECIDABLE);
            }
            if (grounding == GroundingVerdict.NOT_SUPPORTED) {
                reasons.add(GROUNDING_NOT_SUPPORTED);
            } else if (grounding == GroundingVerdict.UNKNOWN) {
                reasons.add(GROUNDING_UNKNOWN);
            }
            if (!dedupRan) {
                reasons.add(DEDUP_NOT_RUN);
            }
            for (String w : warnings) {
                // Ngày 23/10: cảnh báo đã sửa escape LaTeX không bật cờ; "đã tự đóng $" và các cảnh báo khác vẫn bật.
                // Từ 26/10: nhãn tf lệch mẫu mục tiêu là hợp lệ, cũng không bật cờ (vẫn lưu ở warnings và hiện trong vết QC).
                if (!w.startsWith(LlmOutputRepair.ESCAPE_WARNING_PREFIX) && !TfLabelPattern.isDeviationWarning(w)) {
                    reasons.add(WARNING_PREFIX + w);
                }
            }
            return reasons;
        }
    }

    /** Bước format đã lưu (V5, V8): lỗi chặn và cảnh báo không chặn (gồm cả các lần hệ thống tự sửa). */
    public record FormatStep(boolean passed, List<String> errors, List<String> warnings, LocalDateTime checkedAt) {}

    /** Bước dedup đã lưu: điểm tương đồng cao nhất, câu khớp và ngưỡng đã dùng lúc kiểm. */
    public record DedupStep(boolean passed, Double similarity, Integer comparedToQuestionId, Double threshold,
                            LocalDateTime checkedAt) {}

    /** Kết quả Solver hợp lệ đã lưu (V14: kèm lời giải thích). {@code judgeLabels} chỉ có với câu tf. */
    public record SolverStep(boolean passed, String judgeModel, AnswerChoice judgeAnswer, SaJudgeKind judgeSaKind,
                             String judgeSaAnswer, List<TfJudgement> judgeLabels, String reasoning,
                             LocalDateTime checkedAt) {}

    /** W2-3: kết quả "bám tài liệu" đã lưu; {@code materialRefs} là mảng JSON các đoạn tài liệu đã dùng. */
    public record GroundingStep(GroundingVerdict verdict, String reason, String evidenceQuote, Boolean evidenceFound,
                                String judgeModel, String materialRefs, LocalDateTime checkedAt) {}

    /** Chi tiết từng bước QC đã lưu của một câu (W1-3, vết QC); bước chưa có dòng thì null. */
    public record QcDetail(FormatStep format, DedupStep dedup, SolverStep solver, GroundingStep grounding) {}

    private final QualityCheckResultRepository resultRepository;
    private final AnswerCheckStatementRepository statementRepository;
    private final QuestionGroundingRepository groundingRepository;

    public QcSummaryService(QualityCheckResultRepository resultRepository,
                            AnswerCheckStatementRepository statementRepository,
                            QuestionGroundingRepository groundingRepository) {
        this.resultRepository = resultRepository;
        this.statementRepository = statementRepository;
        this.groundingRepository = groundingRepository;
    }

    /** Lý do bước format không qua đã lưu (V8); rỗng khi câu qua format hoặc chưa có dòng format. */
    @Transactional(readOnly = true)
    public List<String> formatErrors(Integer questionId) {
        return JsonStringArray.read(resultRepository.findByQuestionIdAndCheckType(questionId, CheckType.FORMAT)
                .map(QualityCheckResult::getErrors).orElse(null));
    }

    /** W1-3: chi tiết từng bước QC đã lưu của một câu. Chỉ đọc. */
    @Transactional(readOnly = true)
    public QcDetail detail(Integer questionId) {
        FormatStep format = null;
        DedupStep dedup = null;
        SolverStep solver = null;
        for (QualityCheckResult r : resultRepository.findByQuestionIdOrderByIdAsc(questionId)) {
            switch (r.getCheckType()) {
                case FORMAT -> format = new FormatStep(Boolean.TRUE.equals(r.getPassed()),
                        JsonStringArray.read(r.getErrors()), JsonStringArray.read(r.getWarnings()), r.getCheckedAt());
                case DEDUP -> dedup = new DedupStep(Boolean.TRUE.equals(r.getPassed()), r.getSimilarityScore(),
                        r.getComparedToQuestionId(), r.getThresholdUsed(), r.getCheckedAt());
                case ANSWER_CHECK -> {
                    List<TfJudgement> labels = null;
                    if (r.getQuestionType() == QuestionType.TF) {
                        labels = statementRepository.findByQcResultIdOrderByPositionAsc(r.getId()).stream()
                                .map(s -> new TfJudgement(s.getStatementKey(), s.getJudgeLabel())).toList();
                    }
                    solver = new SolverStep(Boolean.TRUE.equals(r.getPassed()), r.getJudgeModel(), r.getJudgeAnswer(),
                            r.getJudgeSaKind(), r.getJudgeSaAnswer(), labels, r.getSolverReasoning(),
                            r.getCheckedAt());
                }
            }
        }
        GroundingStep grounding = groundingRepository.findByQuestionId(questionId)
                .map(g -> new GroundingStep(GroundingVerdict.fromDb(g.getVerdict()), g.getReason(), g.getEvidenceQuote(),
                        g.getEvidenceFound(), g.getJudgeModel(), g.getMaterialRefs(), g.getCheckedAt()))
                .orElse(null);
        return new QcDetail(format, dedup, solver, grounding);
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
                JsonStringArray.read(format.map(QualityCheckResult::getWarnings).orElse(null)),
                dedup.filter(d -> Boolean.FALSE.equals(d.getPassed()))
                        .map(QualityCheckResult::getComparedToQuestionId).orElse(null),
                groundingRepository.findByQuestionId(questionId).map(g -> GroundingVerdict.fromDb(g.getVerdict()))
                        .orElse(null),
                answer.map(QualityCheckResult::getJudgeAnswer).orElse(null),
                answer.map(QualityCheckResult::getSolverReasoning)
                        .filter(r -> r.startsWith(AnswerSolverChecker.OPTIONS_VERDICT_PREFIX)).isPresent());
    }
}
