package com.datn.mcq.exam.service;

import com.datn.mcq.bank.dto.BankItemDto;
import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import com.datn.mcq.exam.entity.Exam;
import com.datn.mcq.exam.entity.ExamSlot;
import com.datn.mcq.exam.repository.ExamSlotRepository;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import com.datn.mcq.matrix.service.MatrixService;
import com.datn.mcq.qc.service.QcSummaryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Xem đề ở chế độ giáo viên (V7, phần C mục 3): câu theo phần I-III, kèm đáp án, đáp số, nhãn từng ý, lời giải và cờ
 * "cần xem kỹ". Câu lấy từ ngân hàng hoặc đã Giữ/Sửa hiển thị bản trong ngân hàng và không gắn cờ (giáo viên đã duyệt).
 */
@Service
public class ExamViewService {

    private final ExamBuildService buildService;
    private final ExamSlotRepository slotRepository;
    private final MatrixService matrixService;
    private final GenerationService generationService;
    private final BankService bankService;
    private final QcSummaryService qcSummaryService;

    public ExamViewService(ExamBuildService buildService, ExamSlotRepository slotRepository,
                           MatrixService matrixService, GenerationService generationService, BankService bankService,
                           QcSummaryService qcSummaryService) {
        this.buildService = buildService;
        this.slotRepository = slotRepository;
        this.matrixService = matrixService;
        this.generationService = generationService;
        this.bankService = bankService;
        this.qcSummaryService = qcSummaryService;
    }

    @Transactional
    public ExamViewDto view(Integer examId) {
        Exam exam = buildService.getExam(examId);
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
        return new ExamViewDto(exam.getId(), exam.getExamCode(), exam.getTitle(), exam.getMode(), exam.getStatus(),
                exam.getMatrixId(), exam.getCreatedAt(), buildStatus, needsReview, parts);
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
            return new ExamItemDto(slot.getId(), slot.getCellId(), slot.getSlotNo(), cell.getPart(), cell.getTopic(),
                    slot.getQuestionType(), slot.getState(), source, slot.getBankItemId(), slot.getGeneratedQuestionId(),
                    b.questionText(), b.optionA(), b.optionB(), b.optionC(), b.optionD(), b.correctOption(),
                    b.saAnswer(), statements, b.explanation(), b.difficulty(), false, List.of());
        }
        if (hasQuestion && slot.getGeneratedQuestionId() != null) {
            GeneratedQuestion q = generationService.getQuestionById(slot.getGeneratedQuestionId());
            List<ItemStatementDto> statements = q.getQuestionType() != QuestionType.TF ? null
                    : generationService.getStatements(q.getId()).stream()
                            .map(s -> new ItemStatementDto(s.getStatementKey(), s.getStatementText(), s.getLlmLabel(),
                                    s.getFinalDifficulty() != null ? s.getFinalDifficulty() : s.getLlmDifficulty()))
                            .toList();
            Difficulty difficulty = q.getFinalDifficulty() != null ? q.getFinalDifficulty() : q.getLlmDifficulty();
            List<String> reasons = qcSummaryService.summarize(q.getId()).reviewReasons();
            return new ExamItemDto(slot.getId(), slot.getCellId(), slot.getSlotNo(), cell.getPart(), cell.getTopic(),
                    slot.getQuestionType(), slot.getState(), source, null, q.getId(), q.getQuestionText(),
                    q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(), q.getCorrectOption(),
                    q.getSaAnswer(), statements, q.getExplanation(), difficulty, !reasons.isEmpty(), reasons);
        }
        return new ExamItemDto(slot.getId(), slot.getCellId(), slot.getSlotNo(), cell.getPart(), cell.getTopic(),
                slot.getQuestionType(), slot.getState(), null, null, null, null, null, null, null, null, null, null,
                null, null, null, false, List.of());
    }
}
