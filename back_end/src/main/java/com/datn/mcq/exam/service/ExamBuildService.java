package com.datn.mcq.exam.service;

import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamBuildDtos.BuildExamRequest;
import com.datn.mcq.exam.dto.ExamBuildDtos.BuildResultDto;
import com.datn.mcq.exam.dto.ExamBuildDtos.CellProgressDto;
import com.datn.mcq.exam.dto.ExamBuildDtos.ProgressDto;
import com.datn.mcq.exam.dto.ExamBuildDtos.SlotDto;
import com.datn.mcq.exam.entity.Exam;
import com.datn.mcq.exam.entity.ExamSlot;
import com.datn.mcq.exam.repository.ExamRepository;
import com.datn.mcq.exam.repository.ExamSlotRepository;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.llm.prompt.MatrixCellSpec;
import com.datn.mcq.llm.prompt.MatrixPromptDraftV0;
import com.datn.mcq.llm.prompt.TfLabelPattern;
import com.datn.mcq.matrix.dto.MatrixDtos.CreateLessonRequest;
import com.datn.mcq.matrix.entity.ExamMatrix;
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import com.datn.mcq.matrix.service.MatrixService;
import com.datn.mcq.qc.service.QcSummaryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tạo đề theo ma trận một lần bấm (V7, phần C mục 2).
 *
 * <ol>
 *   <li>Mỗi ô: lấy câu trong ngân hàng khớp ô trước ({@link ExamSlotRepository#findBankCandidates}), phần còn thiếu tạo
 *       một run sinh câu cho ô (MatrixPromptDraftV0, production, zero-shot) và mỗi chỗ một job trong hàng đợi.</li>
 *   <li>Đối soát (mỗi lần xem tiến độ hoặc kết quả): job xong với câu qc_passed thì chỗ đó có câu. Câu lệch Solver
 *       (trạng thái generated) thì sinh lại 1 lần; vẫn lệch thì dùng câu đó, giao diện hiện cờ "cần xem kỹ" (quyết định 2).
 *       Câu trùng, lỗi định dạng hoặc job lỗi thì sinh lại, tối đa {@value #MAX_ATTEMPTS} lần mỗi chỗ; hết lượt thì dùng
 *       câu lệch Solver đã giữ (nếu có), không thì chỗ đó hỏng.</li>
 * </ol>
 */
@Service
public class ExamBuildService {

    static final int MAX_ATTEMPTS = 3;
    static final int MAX_SOLVER_RETRIES = 1;
    /** Giãn cách các job lúc tạo đề, như enqueue thường (trần 5 lượt/phút của Free Tier). */
    static final long JOB_SPACING_SECONDS = 13;

    private static final DateTimeFormatter CODE_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private final ExamRepository examRepository;
    private final ExamSlotRepository slotRepository;
    private final MatrixService matrixService;
    private final GenerationService generationService;
    private final CatalogService catalogService;
    private final QcSummaryService qcSummaryService;

    public ExamBuildService(ExamRepository examRepository, ExamSlotRepository slotRepository,
                            MatrixService matrixService, GenerationService generationService,
                            CatalogService catalogService, QcSummaryService qcSummaryService) {
        this.examRepository = examRepository;
        this.slotRepository = slotRepository;
        this.matrixService = matrixService;
        this.generationService = generationService;
        this.catalogService = catalogService;
        this.qcSummaryService = qcSummaryService;
    }

    /**
     * @param actorId người đăng nhập (W1-1): là người tạo đề; ma trận phải của chính người này, không thì 404
     */
    @Transactional
    public BuildResultDto build(BuildExamRequest req, Integer actorId) {
        if (req == null || req.matrixId() == null) {
            throw new IllegalArgumentException("Phải truyền matrixId");
        }
        return buildFromMatrix(matrixService.getOwnedMatrix(req.matrixId(), actorId), req.title(), actorId);
    }

    /**
     * W1-4: sinh theo bài. Tạo ma trận ẩn của bài (kind = 'lesson') rồi tạo đề y như tạo từ ma trận, cùng một
     * transaction: ngân hàng trước, phần thiếu xếp hàng sinh câu; mọi bước sau (tiến độ, Giữ/Đổi/Sửa, vết QC, xuất Word)
     * dùng lại nguyên. Lỗi ở bất kỳ bước nào thì không còn lại ma trận hay template.
     */
    @Transactional
    public BuildResultDto buildByLesson(CreateLessonRequest req, Integer actorId) {
        ExamMatrix matrix = matrixService.createLesson(req, actorId);
        return buildFromMatrix(matrix, null, actorId);
    }

    /** Tạo đề từ một ma trận (của người gọi, đã kiểm tra); {@code requestedTitle} bỏ trống thì lấy tên ma trận. */
    private BuildResultDto buildFromMatrix(ExamMatrix matrix, String requestedTitle, Integer actorId) {
        List<ExamMatrixCell> cells = matrixService.cells(matrix.getId());
        Subject subject = catalogService.getSubjectById(matrix.getSubjectId());
        String title = requestedTitle == null || requestedTitle.isBlank() ? matrix.getName() : requestedTitle.trim();
        if (title.length() > 255) {
            throw new IllegalArgumentException("title dài tối đa 255 ký tự");
        }

        Exam exam = new Exam();
        exam.setExamCode(newExamCode());
        exam.setTitle(title);
        exam.setSubjectId(subject.getId());
        exam.setCreatedBy(actorId);
        exam.setStatus(ExamStatus.DRAFT);
        exam.setMode(Exam.MODE_TEACHER);
        exam.setMatrixId(matrix.getId());
        exam.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        exam = examRepository.save(exam);

        LocalDateTime start = LocalDateTime.now();
        int total = 0;
        int fromBank = 0;
        int jobs = 0;
        String lastTfPattern = null;
        for (ExamMatrixCell cell : cells) {
            List<ExamSlot> slots = new ArrayList<>();
            for (int i = 1; i <= cell.getQuestionCount(); i++) {
                ExamSlot slot = new ExamSlot();
                slot.setExamId(exam.getId());
                slot.setCellId(cell.getId());
                slot.setSlotNo(i);
                slot.setQuestionType(cell.getQuestionType());
                slots.add(slot);
            }
            slotRepository.saveAllAndFlush(slots);
            total += slots.size();

            List<Integer> bank = bankCandidates(exam.getId(), matrix, cell, slots.size());
            List<ExamSlot> missing = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) {
                if (i < bank.size()) {
                    slots.get(i).setBankItemId(bank.get(i));
                    slots.get(i).setState(ExamSlot.READY);
                    fromBank++;
                } else {
                    missing.add(slots.get(i));
                }
            }
            if (!missing.isEmpty()) {
                MatrixCellSpec spec = spec(cell, matrix, subject);
                ExperimentRun run = generationService.createMatrixRun(subject.getId(), spec, missing.size());
                for (ExamSlot slot : missing) {
                    slot.setRunId(run.getId());
                    slot.setAttempts(1);
                    slot.setState(ExamSlot.GENERATING);
                    List<String> note = new ArrayList<>();
                    addLine(note, MatrixPromptDraftV0.slotLine(slot.getSlotNo(), cell.getQuestionCount()));
                    if (cell.getQuestionType() == QuestionType.TF) {
                        lastTfPattern = TfLabelPattern.choose(lastTfPattern, ThreadLocalRandom.current());
                        note.add(TfLabelPattern.promptLine(lastTfPattern));
                    }
                    generationService.enqueueMatrixJob(run.getId(), cell.getTemplateId(), spec, slot.getId(),
                            start.plusSeconds(JOB_SPACING_SECONDS * jobs), joinNote(note));
                    jobs++;
                }
            }
            slotRepository.saveAllAndFlush(slots);
        }
        return new BuildResultDto(exam.getId(), exam.getExamCode(), exam.getTitle(),
                matrix.isLesson() ? null : matrix.getId(), total, fromBank, jobs, matrix.getKind());
    }

    /**
     * Câu ngân hàng khớp một ô, đã xếp hạng mềm theo số lần dùng và đã loại câu đã có trong đề {@code examId} hoặc đã bị
     * Đổi khỏi đề (xem {@link ExamSlotRepository#findBankCandidates}). Dùng chung cho tạo đề và Đổi bằng câu ngân hàng
     * (W1-4). W1-4: ô theo bài không nhập nội dung thì không lọc theo nội dung (dùng lại câu sinh từ ô ma trận cùng chủ đề).
     */
    List<Integer> bankCandidates(Integer examId, ExamMatrix matrix, ExamMatrixCell cell, int limit) {
        boolean anyContent = matrix.isLesson() && (cell.getContent() == null || cell.getContent().isBlank());
        return slotRepository.findBankCandidates(examId, matrix.getSubjectId(), cell.getQuestionType().getDbValue(),
                cell.getTopic(), cell.getContent() != null ? cell.getContent() : "",
                cell.getDifficulty() != null ? cell.getDifficulty().getDbValue() : "",
                cell.getStatementDifficulties() != null ? cell.getStatementDifficulties() : "",
                anyContent, limit);
    }

    /** Tiến độ theo ô; kiểm tra chủ sở hữu rồi mới đối soát và đếm. */
    @Transactional
    public ProgressDto progress(Integer examId, Integer actorId) {
        Exam exam = getOwnedExam(examId, actorId);
        reconcile(examId);
        List<ExamSlot> slots = slotRepository.findByExamIdOrderByCellIdAscSlotNoAsc(examId);
        List<ExamMatrixCell> cells = exam.getMatrixId() != null ? matrixService.cells(exam.getMatrixId()) : List.of();
        Map<Integer, int[]> counts = new LinkedHashMap<>();
        int ready = 0;
        int generating = 0;
        int failed = 0;
        int fromBank = 0;
        for (ExamSlot s : slots) {
            int[] c = counts.computeIfAbsent(s.getCellId(), k -> new int[5]);
            c[0]++;
            if (isFromBank(s)) {
                c[1]++;
                fromBank++;
            }
            if (hasQuestion(s)) {
                c[2]++;
                ready++;
            } else if (ExamSlot.FAILED.equals(s.getState())) {
                c[4]++;
                failed++;
            } else {
                c[3]++;
                generating++;
            }
        }
        List<CellProgressDto> cellDtos = new ArrayList<>();
        for (ExamMatrixCell cell : cells) {
            int[] c = counts.getOrDefault(cell.getId(), new int[5]);
            cellDtos.add(new CellProgressDto(cell.getId(), cell.getPosition(), cell.getPart(), cell.getQuestionType(),
                    cell.getTopic(), cell.getContent(), c[0], c[1], c[2], c[3], c[4]));
        }
        String status = generating > 0 ? "generating" : failed > 0 ? "partial" : "ready";
        return new ProgressDto(exam.getId(), exam.getExamCode(), exam.getTitle(), status, slots.size(), ready,
                generating, failed, fromBank, cellDtos);
    }

    /** Các chỗ của một ô (tóm tắt); kiểm tra chủ sở hữu rồi mới đối soát. */
    @Transactional
    public List<SlotDto> cellSlots(Integer examId, Integer cellId, Integer actorId) {
        getOwnedExam(examId, actorId);
        reconcile(examId);
        List<SlotDto> out = slotRepository.findByExamIdOrderByCellIdAscSlotNoAsc(examId).stream()
                .filter(s -> s.getCellId().equals(cellId))
                .map(ExamBuildService::toDto)
                .toList();
        if (out.isEmpty()) {
            throw new NoSuchElementException("Đề không có ô id=" + cellId);
        }
        return out;
    }

    // ---------- đối soát ----------

    /**
     * Đối soát các chỗ đang sinh của một đề: khóa các chỗ (PESSIMISTIC_WRITE) để hai lần đối soát đồng thời không tạo job
     * trùng. Gọi trong transaction của nơi gọi.
     */
    void reconcile(Integer examId) {
        for (ExamSlot slot : slotRepository.lockByExamId(examId)) {
            if (ExamSlot.GENERATING.equals(slot.getState())) {
                reconcileSlot(slot);
            }
        }
    }

    private void reconcileSlot(ExamSlot slot) {
        Optional<GenerationJob> jobOpt = generationService.latestJobForSlot(slot.getId());
        if (jobOpt.isEmpty()) {
            retryOrFail(slot);
            return;
        }
        GenerationJob job = jobOpt.get();
        switch (job.getStatus()) {
            case PENDING, RUNNING -> {
                return;
            }
            case FAILED -> {
                retryOrFail(slot);
                return;
            }
            case DONE -> {
                // xử lý bên dưới
            }
        }
        if (job.getQuestionId() == null) {
            retryOrFail(slot);
            return;
        }
        GeneratedQuestion q = generationService.getQuestionById(job.getQuestionId());
        switch (q.getStatus()) {
            case QC_PASSED, IN_BANK -> useQuestion(slot, q.getId());
            case GENERATED -> {
                // Lệch Solver (hoặc answer_check/dedup chưa kết luận): sinh lại 1 lần; vẫn lệch thì dùng câu này.
                if (slot.getSolverRetries() < MAX_SOLVER_RETRIES && slot.getAttempts() < MAX_ATTEMPTS) {
                    slot.setFallbackQuestionId(q.getId());
                    slot.setSolverRetries(slot.getSolverRetries() + 1);
                    enqueueRetry(slot);
                } else {
                    useQuestion(slot, q.getId());
                }
            }
            default -> retryOrFail(slot);
        }
        slotRepository.save(slot);
    }

    private void retryOrFail(ExamSlot slot) {
        if (slot.getAttempts() < MAX_ATTEMPTS) {
            enqueueRetry(slot);
        } else if (slot.getFallbackQuestionId() != null) {
            useQuestion(slot, slot.getFallbackQuestionId());
        } else {
            slot.setState(ExamSlot.FAILED);
        }
        slotRepository.save(slot);
    }

    private static void useQuestion(ExamSlot slot, Integer questionId) {
        slot.setGeneratedQuestionId(questionId);
        slot.setState(ExamSlot.READY);
    }

    /**
     * Một job sinh lại tự động cho chỗ (đối soát): giữ yêu cầu bổ sung của giáo viên nếu job trước của chỗ có (cùng một
     * lượt Đổi câu).
     */
    void enqueueRetry(ExamSlot slot) {
        enqueue(slot, generationService.latestJobForSlot(slot.getId())
                .map(j -> MatrixPromptDraftV0.teacherInstruction(j.getPromptNote())).orElse(null));
    }

    /** Job sinh lại khi giáo viên Đổi câu; {@code instruction} null thì không có yêu cầu bổ sung. */
    void enqueueReplace(ExamSlot slot, String instruction) {
        enqueue(slot, instruction);
    }

    /** Một job sinh lại cho chỗ, dùng lại run của ô; chạy ngay. */
    private void enqueue(ExamSlot slot, String instruction) {
        // Chuyển trạng thái trước mọi truy vấn: Hibernate có thể ghi chỗ xuống DB trước khi truy vấn exam_slot, và chỗ
        // vừa bỏ câu khi Đổi mà còn 'ready' thì vi phạm chk_es_ready.
        slot.setState(ExamSlot.GENERATING);
        ExamMatrixCell cell = matrixService.getCell(slot.getCellId());
        ExamMatrix matrix = matrixService.getMatrix(cell.getMatrixId());
        Subject subject = catalogService.getSubjectById(matrix.getSubjectId());
        MatrixCellSpec spec = spec(cell, matrix, subject);
        if (slot.getRunId() == null) {
            slot.setRunId(generationService.createMatrixRun(subject.getId(), spec, 1).getId());
        }
        generationService.enqueueMatrixJob(slot.getRunId(), cell.getTemplateId(), spec, slot.getId(),
                LocalDateTime.now(), promptNote(slot, cell, instruction));
        slot.setAttempts(slot.getAttempts() + 1);
    }

    /**
     * Ghi chú cho job sinh lại (ngày 23/10, mục 5 và 8): dòng "câu thứ k/n" của chỗ; với câu tf, một mẫu nhãn bắt buộc
     * mới (tránh mẫu của câu tf gần nhất cùng đề); cộng lý do lần sinh trước bị loại nếu lần trước format_failed (đọc
     * từ quality_check_result.errors, V8); cuối cùng là khối yêu cầu bổ sung của giáo viên khi Đổi câu (nếu có). Gọi
     * trước khi xếp hàng job mới.
     */
    String promptNote(ExamSlot slot, ExamMatrixCell cell, String instruction) {
        List<String> lines = new ArrayList<>();
        addLine(lines, MatrixPromptDraftV0.slotLine(slot.getSlotNo(), cell.getQuestionCount()));
        if (cell.getQuestionType() == QuestionType.TF) {
            lines.add(TfLabelPattern.promptLine(
                    TfLabelPattern.choose(latestTfPattern(slot), ThreadLocalRandom.current())));
        }
        Optional<GenerationJob> previous = generationService.latestJobForSlot(slot.getId());
        if (previous.isPresent() && previous.get().getQuestionId() != null) {
            GeneratedQuestion q = generationService.getQuestionById(previous.get().getQuestionId());
            if (q.getStatus() == QuestionStatus.FORMAT_FAILED) {
                addLine(lines, MatrixPromptDraftV0.retryHint(qcSummaryService.formatErrors(q.getId())));
            }
        }
        addLine(lines, MatrixPromptDraftV0.teacherBlock(instruction));
        return joinNote(lines);
    }

    /** Mẫu nhãn bắt buộc của job tf mới nhất thuộc các chỗ khác cùng đề; null khi chưa có. */
    private String latestTfPattern(ExamSlot slot) {
        GenerationJob latest = null;
        String pattern = null;
        for (ExamSlot other : slotRepository.findByExamIdOrderByCellIdAscSlotNoAsc(slot.getExamId())) {
            if (other.getId().equals(slot.getId()) || other.getQuestionType() != QuestionType.TF) {
                continue;
            }
            Optional<GenerationJob> job = generationService.latestJobForSlot(other.getId());
            String p = job.map(j -> TfLabelPattern.fromNote(j.getPromptNote())).orElse(null);
            if (p != null && (latest == null || job.get().getId() > latest.getId())) {
                latest = job.get();
                pattern = p;
            }
        }
        return pattern;
    }

    private static void addLine(List<String> lines, String line) {
        if (line != null) {
            lines.add(line);
        }
    }

    private static String joinNote(List<String> lines) {
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    // ---------- tiện ích ----------

    /** Thông số ô cho prompt: chỉ kèm yêu cầu cần đạt của các mức ô dùng. */
    static MatrixCellSpec spec(ExamMatrixCell cell, ExamMatrix matrix, Subject subject) {
        List<Difficulty> levels = cell.getQuestionType() == QuestionType.TF
                ? MatrixService.statementLevels(cell)
                : List.of(cell.getDifficulty());
        Map<Difficulty, String> outcomes = new LinkedHashMap<>();
        for (Difficulty level : new LinkedHashSet<>(levels)) {
            if (cell.learningOutcome(level) != null) {
                outcomes.put(level, cell.learningOutcome(level));
            }
        }
        return new MatrixCellSpec(subject.getName(), matrix.getGradeLevel(), cell.getTopic(), cell.getContent(),
                outcomes, cell.getQuestionType(), cell.getDifficulty(),
                cell.getQuestionType() == QuestionType.TF ? levels : null);
    }

    /**
     * Đề thuộc {@code actorId} (W1-1). Không có hoặc của người khác đều NoSuchElementException cùng thông báo (404), để id
     * đoán được không lộ đề nào có thật. Mọi thao tác trên đề gọi hàm này TRƯỚC khi đối soát (đối soát có ghi DB).
     */
    Exam getOwnedExam(Integer examId, Integer actorId) {
        return examRepository.findByIdAndCreatedBy(examId, actorId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đề id=" + examId));
    }

    static boolean hasQuestion(ExamSlot s) {
        return ExamSlot.READY.equals(s.getState()) || ExamSlot.KEPT.equals(s.getState())
                || ExamSlot.EDITED.equals(s.getState());
    }

    /** Câu của chỗ được lấy từ ngân hàng ngay lúc tạo đề (không phải câu sinh ra rồi Giữ vào ngân hàng). */
    static boolean isFromBank(ExamSlot s) {
        return s.getBankItemId() != null && s.getGeneratedQuestionId() == null;
    }

    static SlotDto toDto(ExamSlot s) {
        String source = s.getGeneratedQuestionId() != null ? "generated" : s.getBankItemId() != null ? "bank" : null;
        return new SlotDto(s.getId(), s.getCellId(), s.getSlotNo(), s.getQuestionType(), s.getState(), source,
                s.getBankItemId(), s.getGeneratedQuestionId(), s.getAttempts());
    }

    private String newExamCode() {
        for (int i = 0; i < 20; i++) {
            String code = "MT" + LocalDateTime.now().format(CODE_TIME)
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
            if (!examRepository.existsByExamCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Không tạo được mã đề không trùng, hãy thử lại");
    }
}
