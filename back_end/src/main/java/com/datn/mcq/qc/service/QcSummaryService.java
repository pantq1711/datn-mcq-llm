package com.datn.mcq.qc.service;

import com.datn.mcq.common.util.JsonStringArray;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.SaJudgeKind;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.generation.parser.LlmOutputRepair;
import com.datn.mcq.llm.prompt.McqAnswerPlan;
import com.datn.mcq.llm.prompt.TfLabelPattern;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.checker.ExplanationDraftLeak;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.checker.QuestionWordingCheck;
import com.datn.mcq.qc.dto.GroundingVerdict;
import com.datn.mcq.qc.dto.TfJudgement;
import com.datn.mcq.qc.entity.AnswerCheckStatement;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.entity.QuestionGrounding;
import com.datn.mcq.qc.repository.AnswerCheckStatementRepository;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import com.datn.mcq.qc.repository.QuestionGroundingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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
    /** Thẩm định 31/10: lời giải có "tính lại", "làm lại" hoặc "sửa lại" (không chặn, vì cũng gặp trong lời giải bình thường). */
    public static final String EXPLANATION_DRAFT_SUSPECT = "explanation_draft_suspect";
    /** W1-2: dedup báo trùng (bản giáo viên sửa), dạng "duplicate: &lt;id câu bị trùng&gt;". */
    public static final String DUPLICATE_PREFIX = "duplicate: ";
    /** W2-3: QC "bám tài liệu" kết luận đáp án/lời giải/câu hỏi không khớp tài liệu (chỉ cảnh báo, câu giữ nguyên trạng thái). */
    public static final String GROUNDING_NOT_SUPPORTED = "grounding_not_supported";
    /** W2-3: QC "bám tài liệu" không kết luận được (đoạn tài liệu không đủ, hoặc bằng chứng không kiểm chứng được). */
    public static final String GROUNDING_UNKNOWN = "grounding_unknown";

    /**
     * Nhóm cảnh báo format KHÔNG bật cờ "cần xem kỹ" (2/11, sau đo đề 11): hệ thống đã tự sửa và việc sửa chắc chắn không đổi
     * nghĩa câu, nên giáo viên không cần soát lại. Cảnh báo vẫn được lưu ở {@code quality_check_result.warnings} và vẫn hiện
     * trong "Kết quả tự kiểm tra" (vết QC). Mọi cảnh báo KHÔNG khớp nhóm này, kể cả cảnh báo chưa biết, bật cờ: thà thừa một
     * cờ còn hơn lọt câu lỗi. Khi thêm một việc tự sửa mới, thêm mẫu vào đây CHỈ khi sửa đó chắc chắn đúng.
     * <ul>
     *   <li>{@code json: đã sửa …}: nhân đôi "\" của lệnh LaTeX, gỡ vỏ {@code \text{}} ngoài $, đổi ký hiệu độ sai thành
     *       {@code ^{\circ}} (cùng tiền tố {@link LlmOutputRepair#ESCAPE_WARNING_PREFIX}).</li>
     *   <li>{@code <trường>: đã tự đóng $}: "$" quên đóng ở cuối trường, chỉ khi phần sau trông như công thức VÀ chuỗi gốc có
     *       đúng 1 dấu "$" chưa thoát (F-02). Từ 3 dấu trở lên, cảnh báo là
     *       {@code <trường>: đã tự đóng $ khi chuỗi có N dấu $ (có thể đóng sai chỗ)}, KHÔNG khớp mẫu này nên bật cờ.</li>
     *   <li>{@code <trường>: đã tự bọc $ quanh … LaTeX nằm ngoài $}: biểu thức LaTeX liền mạch ngoài $ được bọc $.</li>
     *   <li>{@code statement_x: đã bỏ tiền tố …}: bỏ "a)", "b." thừa ở đầu ý tf.</li>
     * </ul>
     * Nhãn tf lệch mẫu mục tiêu ({@link TfLabelPattern#isDeviationWarning}) cũng không bật cờ nhưng do model chọn nhãn, không
     * phải việc tự sửa, nên nằm riêng. Chữ đúng của câu mcq khác chữ đã giao ({@link McqAnswerPlan#isDeviationWarning}, 3/11) cũng
     * không bật cờ: câu vẫn đúng toán học, chỉ lệch vị trí đáp án; cảnh báo chỉ để thống kê mức tuân thủ. Cảnh báo gợi ý dùng
     * ký hiệu thay chữ ở phương án mcq ({@link QuestionWordingCheck#isSymbolSuggestedWarning}, góp ý giáo viên 4/11) cũng không
     * bật cờ: chỉ để đo.
     */
    static final List<Pattern> AUTO_FIXED_WARNING_PATTERNS = List.of(
            Pattern.compile("^" + Pattern.quote(LlmOutputRepair.ESCAPE_WARNING_PREFIX) + ".*", Pattern.DOTALL),
            Pattern.compile("^[a-z_]+: đã tự đóng \\$$"),
            Pattern.compile("^[a-z_]+: đã tự bọc \\$ quanh .*", Pattern.DOTALL),
            Pattern.compile("^statement_[a-d]: đã bỏ tiền tố .*", Pattern.DOTALL));

    /** True khi cảnh báo thuộc nhóm hệ thống đã tự sửa chắc chắn ({@link #AUTO_FIXED_WARNING_PATTERNS}), không bật cờ. */
    public static boolean isAutoFixedWarning(String warning) {
        return warning != null && AUTO_FIXED_WARNING_PATTERNS.stream().anyMatch(p -> p.matcher(warning).matches());
    }

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

        /** Cảnh báo "label: 4 ý cùng nhãn T|F" của câu Đúng/Sai (2/11: đối soát đề sinh lại chỗ có câu này); rỗng nếu không có. */
        public Optional<String> sameLabelWarning() {
            return warnings.stream().filter(w -> w.startsWith(FormatChecker.SAME_LABEL_WARNING_PREFIX)).findFirst();
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
                // Mọi cảnh báo đều được lưu và hiện ở vết QC; chỉ nhóm AUTO_FIXED_WARNING_PATTERNS (hệ thống đã tự sửa chắc
                // chắn) và nhãn tf lệch mẫu mục tiêu (26/10) không bật cờ. Mọi cảnh báo còn lại, kể cả cảnh báo chưa biết,
                // bật cờ (2/11). Từ 31/10: lời giải có "tính lại"/"làm lại"/"sửa lại" bật mã riêng thay vì "warning: ...".
                if (ExplanationDraftLeak.SUSPECT_WARNING.equals(w)) {
                    reasons.add(EXPLANATION_DRAFT_SUSPECT);
                } else if (!isAutoFixedWarning(w) && !TfLabelPattern.isDeviationWarning(w)
                        && !McqAnswerPlan.isDeviationWarning(w) && !QuestionWordingCheck.isSymbolSuggestedWarning(w)) {
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

    /** Tóm tắt QC của một câu: dùng chung logic với {@link #summarizeAll} (không có hai bản tính cờ). */
    @Transactional(readOnly = true)
    public QcSummary summarize(Integer questionId) {
        return summarizeAll(List.of(questionId)).get(questionId);
    }

    /**
     * F-07: tóm tắt QC của cả một tập câu bằng số truy vấn CỐ ĐỊNH (không phụ thuộc số câu): một cho dòng QC (format,
     * dedup, answer_check), một cho nhãn Solver từng ý nhưng chỉ khi trong tập có câu Đúng/Sai đã có answer_check, một cho
     * kết quả "bám tài liệu". Mỗi (câu, loại kiểm) có tối đa một dòng (UNIQUE ở V1), nên không cần chọn "mới nhất".
     *
     * @return đủ mọi id được hỏi (câu chưa có dòng QC nào cho {@code dedupRan = false}, Solver chưa chạy); rỗng nếu không hỏi id nào
     */
    @Transactional(readOnly = true)
    public Map<Integer, QcSummary> summarizeAll(Collection<Integer> questionIds) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (Integer id : questionIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        Map<Integer, QcSummary> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Map<Integer, Map<CheckType, QualityCheckResult>> results = new HashMap<>();
        for (QualityCheckResult r : resultRepository.findByQuestionIdIn(ids)) {
            results.computeIfAbsent(r.getQuestionId(), k -> new EnumMap<>(CheckType.class)).put(r.getCheckType(), r);
        }
        // F-07: chỉ câu Đúng/Sai có dòng ý (answer_check_statement); mcq và sa không cần truy vấn này.
        List<Integer> tfAnswerRows = new ArrayList<>();
        for (Map<CheckType, QualityCheckResult> byType : results.values()) {
            QualityCheckResult answer = byType.get(CheckType.ANSWER_CHECK);
            if (answer != null && answer.getQuestionType() == QuestionType.TF) {
                tfAnswerRows.add(answer.getId());
            }
        }
        Set<Integer> undecidableRows = new HashSet<>();
        if (!tfAnswerRows.isEmpty()) {
            for (AnswerCheckStatement s : statementRepository.findByQcResultIdIn(tfAnswerRows)) {
                if (s.getJudgeLabel() == TruthChoice.UNDECIDABLE) {
                    undecidableRows.add(s.getQcResultId());
                }
            }
        }
        Map<Integer, GroundingVerdict> grounding = new HashMap<>();
        for (QuestionGrounding g : groundingRepository.findByQuestionIdIn(ids)) {
            grounding.put(g.getQuestionId(), GroundingVerdict.fromDb(g.getVerdict()));
        }
        for (Integer id : ids) {
            Map<CheckType, QualityCheckResult> byType = results.getOrDefault(id, Map.of());
            QualityCheckResult format = byType.get(CheckType.FORMAT);
            QualityCheckResult dedup = byType.get(CheckType.DEDUP);
            QualityCheckResult answer = byType.get(CheckType.ANSWER_CHECK);
            out.put(id, new QcSummary(
                    dedup != null,
                    answer == null ? null : answer.getPassed(),
                    answer == null ? null : answer.getJudgeSaKind(),
                    answer != null && undecidableRows.contains(answer.getId()),
                    JsonStringArray.read(format == null ? null : format.getWarnings()),
                    dedup != null && Boolean.FALSE.equals(dedup.getPassed()) ? dedup.getComparedToQuestionId() : null,
                    grounding.get(id),
                    answer == null ? null : answer.getJudgeAnswer(),
                    answer != null && answer.getSolverReasoning() != null
                            && answer.getSolverReasoning().startsWith(AnswerSolverChecker.OPTIONS_VERDICT_PREFIX)));
        }
        return out;
    }
}
