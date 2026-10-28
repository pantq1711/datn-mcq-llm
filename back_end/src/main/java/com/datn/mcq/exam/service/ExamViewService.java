package com.datn.mcq.exam.service;

import com.datn.mcq.bank.dto.BankItemDto;
import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamPageDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamSummaryDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import com.datn.mcq.exam.entity.Exam;
import com.datn.mcq.exam.entity.ExamSlot;
import com.datn.mcq.exam.repository.ExamRepository;
import com.datn.mcq.exam.repository.ExamSlotRepository;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.matrix.entity.ExamMatrix;
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import com.datn.mcq.matrix.service.MatrixService;
import com.datn.mcq.qc.service.QcSummaryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Xem đề ở chế độ giáo viên (V7, phần C mục 3): câu theo phần I-III, kèm đáp án, đáp số, nhãn từng ý, lời giải và cờ
 * "cần xem kỹ". Câu lấy từ ngân hàng hoặc đã Giữ/Sửa hiển thị bản trong ngân hàng và không gắn cờ (giáo viên đã duyệt).
 */
@Service
public class ExamViewService {

    private final ExamBuildService buildService;
    private final ExamRepository examRepository;
    private final ExamSlotRepository slotRepository;
    private final MatrixService matrixService;
    private final GenerationService generationService;
    private final BankService bankService;
    private final QcSummaryService qcSummaryService;

    public ExamViewService(ExamBuildService buildService, ExamRepository examRepository,
                           ExamSlotRepository slotRepository, MatrixService matrixService,
                           GenerationService generationService, BankService bankService,
                           QcSummaryService qcSummaryService) {
        this.buildService = buildService;
        this.examRepository = examRepository;
        this.slotRepository = slotRepository;
        this.matrixService = matrixService;
        this.generationService = generationService;
        this.bankService = bankService;
        this.qcSummaryService = qcSummaryService;
    }

    static final int MAX_PAGE_SIZE = 50;

    /** W1-2: lý do "cần xem kỹ" khi Solver đang kiểm lại bản giáo viên sửa (thay solver_not_run). */
    public static final String SOLVER_PENDING = "solver_pending";
    /** W1-2: job kiểm lại bằng Solver thất bại (hết lượt thử, thiếu cấu hình); thay solver_not_run. */
    public static final String SOLVER_FAILED = "solver_failed";
    /** W2-3: job "bám tài liệu" của câu đang chờ hoặc đang chạy (chưa có kết quả). */
    public static final String GROUNDING_PENDING = "grounding_pending";
    /** W2-3: job "bám tài liệu" của câu thất bại (hết lượt thử, thiếu cấu hình), không có kết quả. */
    public static final String GROUNDING_FAILED = "grounding_failed";

    /**
     * W2-3: lý do "cần xem kỹ" của một câu: lý do đã lưu (kể cả kết luận "bám tài liệu" NOT_SUPPORTED/UNKNOWN) cộng, khi câu
     * có job grounding mà chưa có kết quả, {@value #GROUNDING_PENDING} (đang chờ/chạy) hoặc {@value #GROUNDING_FAILED}.
     * Câu không dùng tài liệu không có job nên không bị ảnh hưởng.
     */
    private List<String> reviewReasonsOf(Integer questionId) {
        List<String> reasons = new ArrayList<>(qcSummaryService.summarize(questionId).reviewReasons());
        addGroundingJobReason(generationService, questionId, reasons);
        return reasons;
    }

    /** Xem {@link #reviewReasonsOf}; dùng chung với vết QC. */
    static void addGroundingJobReason(GenerationService generationService, Integer questionId, List<String> reasons) {
        if (reasons.contains(QcSummaryService.GROUNDING_NOT_SUPPORTED) || reasons.contains(QcSummaryService.GROUNDING_UNKNOWN)) {
            return;
        }
        generationService.latestGroundingJob(questionId).ifPresent(job -> {
            if (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.RUNNING) {
                reasons.add(GROUNDING_PENDING);
            } else if (job.getStatus() == JobStatus.FAILED) {
                reasons.add(GROUNDING_FAILED);
            }
        });
    }

    /**
     * Đề của người gọi, mới nhất trước (W1-1). Chỉ đọc, không đối soát: số chỗ theo lần đối soát gần nhất. Cờ "cần xem
     * kỹ" tính như {@link #item}: chỗ ready, câu hệ thống sinh, chưa có bản ngân hàng, có lý do cần xem.
     */
    @Transactional(readOnly = true)
    public ExamPageDto list(Integer actorId, int page, int size, String kind) {
        if (page < 0) {
            throw new IllegalArgumentException("page phải từ 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        if (kind != null && !kind.equals(ExamMatrix.KIND_LESSON) && !kind.equals(ExamMatrix.KIND_MATRIX)) {
            throw new IllegalArgumentException("kind phải là lesson hoặc matrix");
        }
        PageRequest paging = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Exam> exams = kind == null ? examRepository.findByCreatedBy(actorId, paging)
                : examRepository.findByCreatedByAndMatrixKind(actorId, kind, paging);
        Map<Integer, String> kinds = matrixService.kindsByIds(exams.getContent().stream().map(Exam::getMatrixId)
                .filter(java.util.Objects::nonNull).distinct().toList());
        List<Integer> ids = exams.getContent().stream().map(Exam::getId).toList();
        Map<Integer, List<ExamSlot>> slotsByExam = ids.isEmpty() ? Map.of()
                : slotRepository.findByExamIdIn(ids).stream().collect(Collectors.groupingBy(ExamSlot::getExamId));
        List<ExamSummaryDto> items = new ArrayList<>();
        for (Exam e : exams.getContent()) {
            List<ExamSlot> slots = slotsByExam.getOrDefault(e.getId(), List.of());
            int ready = 0;
            int kept = 0;
            int needsReview = 0;
            int generating = 0;
            int failed = 0;
            for (ExamSlot s : slots) {
                switch (s.getState()) {
                    case ExamSlot.READY -> {
                        ready++;
                        if (s.getBankItemId() == null && s.getGeneratedQuestionId() != null
                                && !reviewReasonsOf(s.getGeneratedQuestionId()).isEmpty()) {
                            needsReview++;
                        }
                    }
                    case ExamSlot.KEPT, ExamSlot.EDITED -> kept++;
                    case ExamSlot.FAILED -> failed++;
                    default -> generating++;
                }
            }
            String examKind = kindOf(e, kinds);
            items.add(new ExamSummaryDto(e.getId(), e.getExamCode(), e.getTitle(), e.getMode(), examKind,
                    e.getStatus(), publicMatrixId(e, examKind), e.getCreatedAt(), slots.size(), ready, kept, needsReview, generating, failed));
        }
        return new ExamPageDto(items, page, size, exams.getTotalElements(), exams.getTotalPages());
    }

    @Transactional
    public ExamViewDto view(Integer examId, Integer actorId) {
        Exam exam = buildService.getOwnedExam(examId, actorId);
        buildService.reconcile(examId);
        List<ExamMatrixCell> cells = exam.getMatrixId() != null ? matrixService.cells(exam.getMatrixId()) : List.of();
        Map<Integer, ExamMatrixCell> cellById = cells.stream()
                .collect(Collectors.toMap(ExamMatrixCell::getId, Function.identity()));
        List<ExamSlot> slots = new ArrayList<>(slotRepository.findByExamIdOrderByCellIdAscSlotNoAsc(examId));
        slots.sort(Comparator.comparing((ExamSlot s) -> cellById.get(s.getCellId()).getPosition())
                .thenComparing(ExamSlot::getSlotNo));

        Map<String, List<ExamItemDto>> byPart = new LinkedHashMap<>();
        for (String part : List.of("I", "II", "III")) {
            byPart.put(part, new ArrayList<>());
        }
        int generating = 0;
        int failed = 0;
        int needsReview = 0;
        for (ExamSlot slot : slots) {
            ExamItemDto item = item(slot, cellById.get(slot.getCellId()));
            byPart.computeIfAbsent(item.part(), k -> new ArrayList<>()).add(item);
            if (item.needsReview()) {
                needsReview++;
            }
            if (ExamSlot.FAILED.equals(slot.getState())) {
                failed++;
            } else if (!ExamBuildService.hasQuestion(slot)) {
                generating++;
            }
        }
        List<PartDto> parts = new ArrayList<>();
        for (Map.Entry<String, List<ExamItemDto>> e : byPart.entrySet()) {
            int ready = (int) e.getValue().stream().filter(i -> i.questionText() != null).count();
            parts.add(new PartDto(e.getKey(), e.getValue().size(), ready, e.getValue()));
        }
        String buildStatus = generating > 0 ? "generating" : failed > 0 ? "partial" : "ready";
        String kind = kindOf(exam, exam.getMatrixId() == null ? Map.of()
                : matrixService.kindsByIds(List.of(exam.getMatrixId())));
        return new ExamViewDto(exam.getId(), exam.getExamCode(), exam.getTitle(), exam.getMode(), exam.getStatus(),
                publicMatrixId(exam, kind), exam.getCreatedAt(), buildStatus, needsReview, parts, kind);
    }

    /** W1-4: loại đề suy ra từ ma trận gốc; đề cũ không có ma trận là "manual". */
    private static String kindOf(Exam exam, Map<Integer, String> kindsByMatrix) {
        if (exam.getMatrixId() == null) {
            return "manual";
        }
        return kindsByMatrix.getOrDefault(exam.getMatrixId(), ExamMatrix.KIND_MATRIX);
    }

    /** Id ma trận cho client: ma trận ẩn của đề theo bài không dùng được theo id nên không lộ. */
    private static Integer publicMatrixId(Exam exam, String kind) {
        return ExamMatrix.KIND_LESSON.equals(kind) ? null : exam.getMatrixId();
    }

    ExamItemDto item(ExamSlot slot, ExamMatrixCell cell) {
        String source = slot.getGeneratedQuestionId() != null ? "generated"
                : slot.getBankItemId() != null ? "bank" : null;
        boolean hasQuestion = ExamBuildService.hasQuestion(slot);
        boolean showBank = hasQuestion && slot.getBankItemId() != null;
        if (showBank) {
            BankItemDto b = bankService.getItem(slot.getBankItemId());
            List<ItemStatementDto> statements = b.statements() == null ? null : b.statements().stream()
                    .map(s -> new ItemStatementDto(s.key(), s.text(), s.label(), s.difficulty())).toList();
            Integer original = slot.getGeneratedQuestionId() == null ? null
                    : generationService.originalQuestionId(slot.getGeneratedQuestionId());
            boolean edited = original != null && !original.equals(slot.getGeneratedQuestionId());
            return new ExamItemDto(slot.getId(), slot.getCellId(), slot.getSlotNo(), cell.getPart(), cell.getTopic(),
                    slot.getQuestionType(), slot.getState(), source, slot.getBankItemId(), slot.getGeneratedQuestionId(),
                    b.questionText(), b.optionA(), b.optionB(), b.optionC(), b.optionD(), b.correctOption(),
                    b.saAnswer(), statements, b.explanation(), b.difficulty(), false, List.of(), null, edited,
                    edited ? original : null);
        }
        if (hasQuestion && slot.getGeneratedQuestionId() != null) {
            GeneratedQuestion q = generationService.getQuestionById(slot.getGeneratedQuestionId());
            List<ItemStatementDto> statements = q.getQuestionType() != QuestionType.TF ? null
                    : generationService.getStatements(q.getId()).stream()
                            .map(s -> new ItemStatementDto(s.getStatementKey(), s.getStatementText(), s.getLlmLabel(),
                                    s.getFinalDifficulty() != null ? s.getFinalDifficulty() : s.getLlmDifficulty()))
                            .toList();
            Difficulty difficulty = q.getFinalDifficulty() != null ? q.getFinalDifficulty() : q.getLlmDifficulty();
            List<String> reasons = reviewReasonsOf(q.getId());
            // W1-2 / W2-4b: câu chờ Solver kiểm (job answer_check) thì "đang kiểm", job thất bại hoặc Solver lỗi thì nói rõ;
            // cả hai không bao giờ là đạt.
            String qcState = null;
            Optional<GenerationJob> check = generationService.latestAnswerCheckJob(q.getId());
            if (reasons.contains(QcSummaryService.SOLVER_NOT_RUN)) {
                if (check.isPresent() && (check.get().getStatus() == JobStatus.PENDING
                        || check.get().getStatus() == JobStatus.RUNNING)) {
                    reasons.set(reasons.indexOf(QcSummaryService.SOLVER_NOT_RUN), SOLVER_PENDING);
                    qcState = ExamItemDto.QC_CHECKING;
                } else if ((check.isPresent() && check.get().getStatus() == JobStatus.FAILED)
                        || q.getSolverError() != null) {
                    reasons.set(reasons.indexOf(QcSummaryService.SOLVER_NOT_RUN), SOLVER_FAILED);
                }
            }
            if (qcState == null) {
                qcState = reasons.isEmpty() ? ExamItemDto.QC_PASSED : ExamItemDto.QC_NEEDS_REVIEW;
            }
            Integer original = q.getEditedFromQuestionId() == null ? null : generationService.originalQuestionId(q.getId());
            return new ExamItemDto(slot.getId(), slot.getCellId(), slot.getSlotNo(), cell.getPart(), cell.getTopic(),
                    slot.getQuestionType(), slot.getState(), source, null, q.getId(), q.getQuestionText(),
                    q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(), q.getCorrectOption(),
                    q.getSaAnswer(), statements, q.getExplanation(), difficulty, !reasons.isEmpty(), reasons, qcState,
                    original != null, original);
        }
        return new ExamItemDto(slot.getId(), slot.getCellId(), slot.getSlotNo(), cell.getPart(), cell.getTopic(),
                slot.getQuestionType(), slot.getState(), null, null, null, null, null, null, null, null, null, null,
                null, null, null, false, List.of(), null, false, null, slot.getFailureReason());
    }
}
