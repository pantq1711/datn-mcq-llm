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
import com.datn.mcq.exam.dto.ExamViewDtos.RequestsDto;
import com.datn.mcq.exam.entity.Exam;
import com.datn.mcq.exam.entity.ExamSlot;
import com.datn.mcq.exam.repository.ExamRepository;
import com.datn.mcq.exam.repository.ExamSlotRepository;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.matrix.entity.ExamMatrix;
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import com.datn.mcq.matrix.service.MatrixService;
import com.datn.mcq.qc.service.QcSummaryService;
import com.datn.mcq.qc.service.QcSummaryService.QcSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final ExamContextService contextService;

    private final double similarThreshold;

    public ExamViewService(ExamBuildService buildService, ExamRepository examRepository,
                           ExamSlotRepository slotRepository, MatrixService matrixService,
                           GenerationService generationService, BankService bankService,
                           QcSummaryService qcSummaryService, ExamContextService contextService,
                           @Value("${app.exam.similar-threshold:0.82}") double similarThreshold) {
        if (!(similarThreshold > 0.0 && similarThreshold <= 1.0)) {
            throw new IllegalArgumentException("app.exam.similar-threshold phải nằm trong (0, 1], nhận " + similarThreshold);
        }
        this.similarThreshold = similarThreshold;
        this.contextService = contextService;
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
     * W2-3: thêm vào lý do "cần xem kỹ" đã lưu của một câu (kể cả kết luận "bám tài liệu" NOT_SUPPORTED/UNKNOWN), khi câu
     * có job grounding mà chưa có kết quả, {@value #GROUNDING_PENDING} (đang chờ/chạy) hoặc {@value #GROUNDING_FAILED}.
     * Câu không dùng tài liệu không có job nên không bị ảnh hưởng. Dùng chung với vết QC (tra job của một câu).
     */
    static void addGroundingJobReason(GenerationService generationService, Integer questionId, List<String> reasons) {
        if (hasGroundingVerdict(reasons)) {
            return;
        }
        addGroundingJobReason(generationService.latestGroundingJob(questionId), reasons);
    }

    /** Như trên với job đã nạp sẵn (xem đề, danh sách đề nạp job theo lô); cùng một logic. */
    static void addGroundingJobReason(Optional<GenerationJob> groundingJob, List<String> reasons) {
        if (hasGroundingVerdict(reasons)) {
            return;
        }
        groundingJob.ifPresent(job -> {
            if (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.RUNNING) {
                reasons.add(GROUNDING_PENDING);
            } else if (job.getStatus() == JobStatus.FAILED) {
                reasons.add(GROUNDING_FAILED);
            }
        });
    }

    private static boolean hasGroundingVerdict(List<String> reasons) {
        return reasons.contains(QcSummaryService.GROUNDING_NOT_SUPPORTED) || reasons.contains(QcSummaryService.GROUNDING_UNKNOWN);
    }

    /**
     * F-07: dữ liệu của một nhóm chỗ, nạp theo lô bằng số truy vấn cố định (không phụ thuộc số câu). Nội dung câu và mọi cờ
     * "cần xem kỹ" lấy từ đây, không truy vấn thêm.
     *
     * @param bank       câu ngân hàng của các chỗ đã có bản ngân hàng
     * @param questions  câu sinh của các chỗ hiển thị bản sinh (chỗ chưa có bản ngân hàng)
     * @param statements 4 ý của các câu tf trong {@code questions}
     * @param originals  bản gốc LLM của câu sinh đã sửa và của câu sinh đứng sau chỗ đã có bản ngân hàng
     * @param jobs       job answer_check và grounding_check mới nhất của {@code questions}
     * @param qc         tóm tắt QC của {@code questions}
     * @param scopes     (PR B) mức lấy yêu cầu của job đã sinh ra câu (theo id câu gốc LLM)
     * @param requests   (PR B) yêu cầu của đề và các ô theo id đề, để tính yêu cầu thừa hưởng của từng chỗ
     */
    private record Loaded(Map<Integer, BankItemDto> bank, Map<Integer, GeneratedQuestion> questions,
                          Map<Integer, List<GeneratedQuestionStatement>> statements, Map<Integer, Integer> originals,
                          GenerationService.CheckJobs jobs, Map<Integer, QcSummary> qc, Map<Integer, String> scopes,
                          Map<Integer, ExamContextService.Resolved> requests) {}

    private Loaded load(Collection<ExamSlot> slots) {
        Set<Integer> bankIds = new LinkedHashSet<>();
        Set<Integer> generatedIds = new LinkedHashSet<>();
        Set<Integer> originalIds = new LinkedHashSet<>();
        for (ExamSlot slot : slots) {
            if (!ExamBuildService.hasQuestion(slot)) {
                continue;
            }
            if (slot.getBankItemId() != null) {
                bankIds.add(slot.getBankItemId());
                if (slot.getGeneratedQuestionId() != null) {
                    originalIds.add(slot.getGeneratedQuestionId());
                }
            } else if (slot.getGeneratedQuestionId() != null) {
                generatedIds.add(slot.getGeneratedQuestionId());
            }
        }
        Map<Integer, BankItemDto> bank = bankService.getItems(bankIds);
        Map<Integer, GeneratedQuestion> questions = generationService.questionsByIds(generatedIds);
        List<Integer> tfIds = questions.values().stream().filter(q -> q.getQuestionType() == QuestionType.TF)
                .map(GeneratedQuestion::getId).toList();
        for (GeneratedQuestion q : questions.values()) {
            if (q.getEditedFromQuestionId() != null) {
                originalIds.add(q.getId());
            }
        }
        Map<Integer, Integer> originals = generationService.originalQuestionIds(originalIds);
        // PR B: số truy vấn cố định (một cho mức yêu cầu của job, một cho yêu cầu của đề và ô), không theo số câu.
        Set<Integer> scopeIds = new LinkedHashSet<>(generatedIds);
        questions.values().forEach(q -> scopeIds.add(originals.getOrDefault(q.getId(), q.getId())));
        Set<Integer> examIds = new LinkedHashSet<>();
        slots.forEach(s -> examIds.add(s.getExamId()));
        return new Loaded(bank, questions, generationService.statementsByQuestionIds(tfIds), originals,
                generationService.latestCheckJobs(generatedIds), qcSummaryService.summarizeAll(generatedIds),
                generationService.requestScopes(scopeIds), contextService.requestsOf(examIds));
    }

    /** PR B: yêu cầu thừa hưởng của chỗ (mã đã lọc theo dạng câu); null khi không có. */
    private static RequestsDto inheritedRequests(ExamSlot slot, Loaded data) {
        ExamContextService.Resolved resolved = data.requests().get(slot.getExamId());
        ExamContextService.RequestLevel level = resolved == null ? null : resolved.requestFor(slot.getCellId());
        if (level == null) {
            return null;
        }
        List<String> codes = level.codesFor(slot.getQuestionType());
        return codes.isEmpty() && level.text() == null ? null : new RequestsDto(codes, level.text(), level.scope());
    }

    /** PR B: yêu cầu mà câu sinh {@code q} được sinh theo; null khi câu không có yêu cầu. */
    private static RequestsDto ownRequests(GeneratedQuestion q, Loaded data) {
        List<String> codes = q.getRequestCodes() == null ? List.of() : q.getRequestCodes();
        if (codes.isEmpty() && q.getRequestText() == null) {
            return null;
        }
        Integer root = q.getEditedFromQuestionId() == null ? q.getId() : data.originals().getOrDefault(q.getId(), q.getId());
        return new RequestsDto(codes, q.getRequestText(), data.scopes().get(root));
    }

    /**
     * Các phần I-III (có thể thêm Phần khác) của từng đề, theo thứ tự hiển thị, đã gắn cờ similar_in_exam. MỘT hàm cho xem
     * đề và danh sách đề, nên số "cần xem lại" của hai nơi cùng một nguồn. Nạp dữ liệu của mọi chỗ một lần.
     */
    private Map<Integer, Map<String, List<ExamItemDto>>> buildParts(Map<Integer, List<ExamSlot>> slotsByExam) {
        List<ExamSlot> all = slotsByExam.values().stream().flatMap(List::stream).toList();
        Map<Integer, ExamMatrixCell> cells = matrixService.cellsByIds(
                all.stream().map(ExamSlot::getCellId).collect(Collectors.toCollection(LinkedHashSet::new)));
        Loaded loaded = load(all);
        Map<Integer, Map<String, List<ExamItemDto>>> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<ExamSlot>> entry : slotsByExam.entrySet()) {
            List<ExamSlot> slots = new ArrayList<>(entry.getValue());
            slots.sort(Comparator.comparing((ExamSlot s) -> cells.get(s.getCellId()).getPosition())
                    .thenComparing(ExamSlot::getSlotNo));
            Map<String, List<ExamItemDto>> byPart = new LinkedHashMap<>();
            for (String part : List.of("I", "II", "III")) {
                byPart.put(part, new ArrayList<>());
            }
            for (ExamSlot slot : slots) {
                ExamItemDto item = toItem(slot, cells.get(slot.getCellId()), loaded);
                byPart.computeIfAbsent(item.part(), k -> new ArrayList<>()).add(item);
            }
            flagSimilarInExam(byPart);
            out.put(entry.getKey(), byPart);
        }
        return out;
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
        // F-07: số "cần xem lại" lấy từ ĐÚNG các mục mà trang chi tiết dựng (cùng buildParts, gồm cả cờ similar_in_exam),
        // nạp theo lô cho mọi đề của trang.
        Map<Integer, Map<String, List<ExamItemDto>>> partsByExam = buildParts(slotsByExam);
        List<ExamSummaryDto> items = new ArrayList<>();
        for (Exam e : exams.getContent()) {
            List<ExamSlot> slots = slotsByExam.getOrDefault(e.getId(), List.of());
            int ready = 0;
            int kept = 0;
            int generating = 0;
            int failed = 0;
            for (ExamSlot s : slots) {
                switch (s.getState()) {
                    case ExamSlot.READY -> ready++;
                    case ExamSlot.KEPT, ExamSlot.EDITED -> kept++;
                    case ExamSlot.FAILED -> failed++;
                    default -> generating++;
                }
            }
            int needsReview = countNeedsReview(partsByExam.getOrDefault(e.getId(), Map.of()));
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
        List<ExamSlot> slots = slotRepository.findByExamIdOrderByCellIdAscSlotNoAsc(examId);
        Map<String, List<ExamItemDto>> byPart = buildParts(Map.of(examId, slots)).get(examId);
        int generating = 0;
        int failed = 0;
        for (ExamSlot slot : slots) {
            if (ExamSlot.FAILED.equals(slot.getState())) {
                failed++;
            } else if (!ExamBuildService.hasQuestion(slot)) {
                generating++;
            }
        }
        int needsReview = countNeedsReview(byPart);
        List<PartDto> parts = new ArrayList<>();
        for (Map.Entry<String, List<ExamItemDto>> e : byPart.entrySet()) {
            int ready = (int) e.getValue().stream().filter(i -> i.questionText() != null).count();
            parts.add(new PartDto(e.getKey(), e.getValue().size(), ready, e.getValue()));
        }
        String buildStatus = generating > 0 ? "generating" : failed > 0 ? "partial" : "ready";
        String kind = kindOf(exam, exam.getMatrixId() == null ? Map.of()
                : matrixService.kindsByIds(List.of(exam.getMatrixId())));
        return new ExamViewDto(exam.getId(), exam.getExamCode(), exam.getTitle(), exam.getMode(), exam.getStatus(),
                publicMatrixId(exam, kind), exam.getCreatedAt(), buildStatus, needsReview, parts, kind,
                exam.getChallenge().getDbValue());
    }

    /**
     * Thẩm định 31/10 (lớp 2): gắn lý do {@code similar_in_exam: <Phần>.<số>} cho câu hệ thống sinh cùng khuôn với một câu
     * ĐỨNG TRƯỚC nó trong CHÍNH đề này; từ 2/11 mỗi cặp chỉ câu sau bị gắn, và số đếm theo Phần như trang đề ("Câu k" của
     * từng Phần), xem {@link ExamQuestionSimilarity#flag}. Tính lúc xem, không ghi DB, không đổi trạng thái câu.
     */
    private void flagSimilarInExam(Map<String, List<ExamItemDto>> byPart) {
        ExamQuestionSimilarity.flag(byPart, similarThreshold);
    }

    /** Số chỗ "cần xem kỹ" của một đề: định nghĩa duy nhất, dùng cho cả trang chi tiết và danh sách đề. */
    private static int countNeedsReview(Map<String, List<ExamItemDto>> byPart) {
        return (int) byPart.values().stream().flatMap(List::stream).filter(ExamItemDto::needsReview).count();
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

    /** Mục của một chỗ (ExamActionService trả sau mỗi thao tác); cùng logic với xem đề, nạp dữ liệu của riêng chỗ này. */
    ExamItemDto item(ExamSlot slot, ExamMatrixCell cell) {
        return toItem(slot, cell, load(List.of(slot)));
    }

    private ExamItemDto toItem(ExamSlot slot, ExamMatrixCell cell, Loaded data) {
        ExamItemDto item = toItemWithoutRequests(slot, cell, data);
        // PR B: cờ thông tin và yêu cầu chỉ có với câu hệ thống sinh đang hiện bản sinh (chưa có bản ngân hàng).
        boolean generated = ExamBuildService.hasQuestion(slot) && slot.getBankItemId() == null
                && slot.getGeneratedQuestionId() != null;
        GeneratedQuestion q = generated ? data.questions().get(slot.getGeneratedQuestionId()) : null;
        return item.withRequests(q == null ? List.of() : data.qc().get(q.getId()).infoReasons(),
                q == null ? null : ownRequests(q, data), inheritedRequests(slot, data));
    }

    private ExamItemDto toItemWithoutRequests(ExamSlot slot, ExamMatrixCell cell, Loaded data) {
        String source = slot.getGeneratedQuestionId() != null ? "generated"
                : slot.getBankItemId() != null ? "bank" : null;
        boolean hasQuestion = ExamBuildService.hasQuestion(slot);
        boolean showBank = hasQuestion && slot.getBankItemId() != null;
        if (showBank) {
            BankItemDto b = data.bank().get(slot.getBankItemId());
            List<ItemStatementDto> statements = b.statements() == null ? null : b.statements().stream()
                    .map(s -> new ItemStatementDto(s.key(), s.text(), s.label(), s.difficulty())).toList();
            Integer original = slot.getGeneratedQuestionId() == null ? null
                    : data.originals().get(slot.getGeneratedQuestionId());
            boolean edited = original != null && !original.equals(slot.getGeneratedQuestionId());
            return new ExamItemDto(slot.getId(), slot.getCellId(), slot.getSlotNo(), cell.getPart(), cell.getTopic(),
                    slot.getQuestionType(), slot.getState(), source, slot.getBankItemId(), slot.getGeneratedQuestionId(),
                    b.questionText(), b.optionA(), b.optionB(), b.optionC(), b.optionD(), b.correctOption(),
                    b.saAnswer(), statements, b.explanation(), b.difficulty(), false, List.of(), null, edited,
                    edited ? original : null);
        }
        if (hasQuestion && slot.getGeneratedQuestionId() != null) {
            GeneratedQuestion q = data.questions().get(slot.getGeneratedQuestionId());
            List<ItemStatementDto> statements = q.getQuestionType() != QuestionType.TF ? null
                    : data.statements().getOrDefault(q.getId(), List.of()).stream()
                            .map(s -> new ItemStatementDto(s.getStatementKey(), s.getStatementText(), s.getLlmLabel(),
                                    s.getFinalDifficulty() != null ? s.getFinalDifficulty() : s.getLlmDifficulty()))
                            .toList();
            Difficulty difficulty = q.getFinalDifficulty() != null ? q.getFinalDifficulty() : q.getLlmDifficulty();
            // W2-3: lý do đã lưu (QC) cộng lý do của job "bám tài liệu" chưa có kết quả.
            List<String> reasons = new ArrayList<>(data.qc().get(q.getId()).reviewReasons());
            addGroundingJobReason(Optional.ofNullable(data.jobs().grounding().get(q.getId())), reasons);
            // W1-2 / W2-4b: câu chờ Solver kiểm (job answer_check) thì "đang kiểm", job thất bại hoặc Solver lỗi thì nói rõ;
            // cả hai không bao giờ là đạt.
            String qcState = null;
            Optional<GenerationJob> check = Optional.ofNullable(data.jobs().answerCheck().get(q.getId()));
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
            Integer original = q.getEditedFromQuestionId() == null ? null : data.originals().get(q.getId());
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
