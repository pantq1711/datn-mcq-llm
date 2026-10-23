package com.datn.mcq.exam.service;

import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
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
import com.datn.mcq.matrix.entity.ExamMatrix;
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import com.datn.mcq.matrix.service.MatrixService;
import com.datn.mcq.user.entity.UserAccount;
import com.datn.mcq.user.service.UserService;
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
    private final UserService userService;

    public ExamBuildService(ExamRepository examRepository, ExamSlotRepository slotRepository,
                            MatrixService matrixService, GenerationService generationService,
                            CatalogService catalogService, UserService userService) {
        this.examRepository = examRepository;
        this.slotRepository = slotRepository;
        this.matrixService = matrixService;
        this.generationService = generationService;
        this.catalogService = catalogService;
        this.userService = userService;
    }

    @Transactional
    public BuildResultDto build(BuildExamRequest req) {
        if (req == null || req.matrixId() == null) {
            throw new IllegalArgumentException("Phải truyền matrixId");
        }
        ExamMatrix matrix = matrixService.getMatrix(req.matrixId());
        List<ExamMatrixCell> cells = matrixService.cells(matrix.getId());
        UserAccount teacher = req.teacherId() != null
                ? userService.getById(req.teacherId())
                : userService.getOrCreateDefaultTeacher();
        Subject subject = catalogService.getSubjectById(matrix.getSubjectId());
        String title = req.title() == null || req.title().isBlank() ? matrix.getName() : req.title().trim();
        if (title.length() > 255) {
            throw new IllegalArgumentException("title dài tối đa 255 ký tự");
        }

        Exam exam = new Exam();
        exam.setExamCode(newExamCode());
        exam.setTitle(title);
        exam.setSubjectId(subject.getId());
        exam.setCreatedBy(teacher.getId());
        exam.setStatus(ExamStatus.DRAFT);
        exam.setMode(Exam.MODE_TEACHER);
        exam.setMatrixId(matrix.getId());
        exam.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        exam = examRepository.save(exam);

        LocalDateTime start = LocalDateTime.now();
        int total = 0;
        int fromBank = 0;
        int jobs = 0;
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

            List<Integer> bank = slotRepository.findBankCandidates(exam.getId(), subject.getId(),
                    cell.getQuestionType().getDbValue(), cell.getTopic(),
                    cell.getContent() != null ? cell.getContent() : "",
                    cell.getDifficulty() != null ? cell.getDifficulty().getDbValue() : "",
                    cell.getStatementDifficulties() != null ? cell.getStatementDifficulties() : "",
                    slots.size());
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
                    generationService.enqueueMatrixJob(run.getId(), cell.getTemplateId(), spec, slot.getId(),
                            start.plusSeconds(JOB_SPACING_SECONDS * jobs));
                    jobs++;
                }
            }
            slotRepository.saveAllAndFlush(slots);
        }
        return new BuildResultDto(exam.getId(), exam.getExamCode(), exam.getTitle(), matrix.getId(), total, fromBank,
                jobs);
    }

    /** Tiến độ theo ô; đối soát trước khi đếm. */
    @Transactional
    public ProgressDto progress(Integer examId) {
        Exam exam = getExam(examId);
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

    /** Các chỗ của một ô (tóm tắt); đối soát trước. */
    @Transactional
    public List<SlotDto> cellSlots(Integer examId, Integer cellId) {
        getExam(examId);
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

    /** Một job sinh lại cho chỗ, dùng lại run của ô; chạy ngay. */
    void enqueueRetry(ExamSlot slot) {
        ExamMatrixCell cell = matrixService.getCell(slot.getCellId());
        ExamMatrix matrix = matrixService.getMatrix(cell.getMatrixId());
        Subject subject = catalogService.getSubjectById(matrix.getSubjectId());
        MatrixCellSpec spec = spec(cell, matrix, subject);
        if (slot.getRunId() == null) {
            slot.setRunId(generationService.createMatrixRun(subject.getId(), spec, 1).getId());
        }
        generationService.enqueueMatrixJob(slot.getRunId(), cell.getTemplateId(), spec, slot.getId(),
                LocalDateTime.now());
        slot.setAttempts(slot.getAttempts() + 1);
        slot.setState(ExamSlot.GENERATING);
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

    Exam getExam(Integer examId) {
        return examRepository.findById(examId)
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
