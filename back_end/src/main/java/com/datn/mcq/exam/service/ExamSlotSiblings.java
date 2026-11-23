package com.datn.mcq.exam.service;

import com.datn.mcq.bank.dto.BankItemDto;
import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.entity.ExamSlot;
import com.datn.mcq.exam.repository.ExamSlotRepository;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.generation.service.SlotDedupContext;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.questionimage.service.QuestionImageService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * W1-3 (phần 2c): nội dung các câu đang nằm trong cùng đề, để dedup câu sinh mới và câu giáo viên sửa so với chúng. Chỉ
 * đọc.
 *
 * <p>Dedup theo run chỉ thấy câu qc_passed/in_bank và câu cùng run; còn thiếu (1) câu ở ô khác chưa qua QC (lệch Solver
 * được dùng kèm cờ, bản sửa đang chờ Solver), (2) văn bản câu ngân hàng đã sửa lúc duyệt (khác câu nguồn). Với câu ngân
 * hàng, ứng viên mang id của câu nguồn (generated_question) để kết quả dedup trỏ được tới bản ghi có thật.
 *
 * <p>X1 (H7): ứng viên câu ngân hàng mang khóa hình ({@link QuestionImageService#imageKeysBySourceQuestion}) nên câu khác
 * hình không bao giờ trùng; câu ngân hàng và khóa hình của mọi chỗ nạp theo lô (số truy vấn không theo số chỗ).
 */
@Component
public class ExamSlotSiblings implements SlotDedupContext {

    private final ExamSlotRepository slotRepository;
    private final BankService bankService;
    private final GenerationService generationService;
    private final com.datn.mcq.exam.repository.ExamRepository examRepository;
    private final QuestionImageService questionImageService;

    public ExamSlotSiblings(ExamSlotRepository slotRepository, BankService bankService,
                            GenerationService generationService,
                            com.datn.mcq.exam.repository.ExamRepository examRepository,
                            QuestionImageService questionImageService) {
        this.slotRepository = slotRepository;
        this.bankService = bankService;
        this.generationService = generationService;
        this.examRepository = examRepository;
        this.questionImageService = questionImageService;
    }

    /** Câu ngân hàng của các chỗ (theo id bank_item) và khóa hình theo id câu nguồn, nạp theo lô. */
    private record BankData(Map<Integer, BankItemDto> items, Map<Integer, String> imageKeys) {}

    /** {@code withKeys} false: chỉ cần chữ câu, không truy vấn ảnh. */
    private BankData bankData(List<ExamSlot> slots, boolean withKeys) {
        List<Integer> ids = slots.stream().filter(ExamBuildService::hasQuestion).map(ExamSlot::getBankItemId)
                .filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return new BankData(Map.of(), Map.of());
        }
        Map<Integer, BankItemDto> items = bankService.getItems(ids);
        Map<Integer, String> keys = withKeys ? questionImageService.imageKeysBySourceQuestion(
                items.values().stream().map(BankItemDto::sourceQuestionId).toList()) : Map.of();
        return new BankData(items, keys);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DedupCandidate> candidatesForSlot(Integer examSlotId) {
        return slotRepository.findById(examSlotId)
                .map(s -> candidates(s.getExamId(), s.getId(), s.getQuestionType()))
                .orElse(List.of());
    }

    /**
     * Các câu cùng dạng đang nằm ở những chỗ KHÁC của đề {@code examId}. Câu đã bị Đổi ra khỏi chỗ, câu lỗi định dạng,
     * câu trùng và câu bị loại không tính.
     */
    @Transactional(readOnly = true)
    public List<DedupCandidate> candidates(Integer examId, Integer excludeSlotId, QuestionType type) {
        List<ExamSlot> others = slotRepository.findByExamIdOrderByCellIdAscSlotNoAsc(examId).stream()
                .filter(other -> !other.getId().equals(excludeSlotId) && other.getQuestionType() == type).toList();
        BankData bank = bankData(others, true);
        List<DedupCandidate> out = new ArrayList<>();
        for (ExamSlot other : others) {
            for (DedupCandidate candidate : of(other, bank)) {
                if (candidate != null && candidate.hasContent()) {
                    out.add(candidate);
                }
            }
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> sameCellQuestionTexts(Integer examSlotId) {
        ExamSlot self = slotRepository.findById(examSlotId).orElse(null);
        if (self == null) {
            return List.of();
        }
        List<ExamSlot> others = slotRepository.findByExamIdOrderByCellIdAscSlotNoAsc(self.getExamId()).stream()
                .filter(other -> !other.getId().equals(self.getId()) && other.getCellId().equals(self.getCellId()))
                .toList();
        BankData bank = bankData(others, false);
        List<String> out = new ArrayList<>();
        for (ExamSlot other : others) {
            for (DedupCandidate candidate : of(other, bank)) {
                if (candidate != null && candidate.hasContent()) {
                    out.add(candidate.questionText());
                }
            }
        }
        return out;
    }

    private List<DedupCandidate> of(ExamSlot slot, BankData bankData) {
        if (ExamBuildService.hasQuestion(slot)) {
            if (slot.getBankItemId() != null) {
                // Câu ngân hàng (lấy từ ngân hàng hoặc đã Giữ): văn bản đang hiển thị trong đề là bản ngân hàng.
                BankItemDto bank = bankData.items().get(slot.getBankItemId());
                return List.of(fromBank(bank, bankData.imageKeys().getOrDefault(bank.sourceQuestionId(), "")));
            }
            return slot.getGeneratedQuestionId() == null ? List.of()
                    : java.util.Collections.singletonList(fromGenerated(slot.getGeneratedQuestionId()));
        }
        List<DedupCandidate> out = new ArrayList<>();
        if (ExamSlot.GENERATING.equals(slot.getState())) {
            // Chỗ đang sinh: câu vừa sinh của job mới nhất (đã tới bước QC) cũng đã nằm trong đề.
            Optional<GenerationJob> job = generationService.latestJobForSlot(slot.getId());
            Integer latest = job.map(GenerationJob::getQuestionId).orElse(null);
            if (latest != null) {
                out.add(fromGenerated(latest));
            }
            // Câu lệch Solver đang được sinh lại là fallback của chỗ: job sinh lại mới chưa có câu, nhưng nếu lần sinh lại
            // hỏng thì câu này vẫn được dùng vào đề (ExamBuildService.failOrUseFallback).
            Integer fallback = slot.getFallbackQuestionId();
            if (fallback != null && !fallback.equals(latest)) {
                out.add(fromGenerated(fallback));
            }
        }
        return out;
    }

    private static DedupCandidate fromBank(BankItemDto b, String imageKey) {
        return switch (b.questionType()) {
            case MCQ -> new DedupCandidate(b.sourceQuestionId(), b.questionText(), b.optionA(), b.optionB(),
                    b.optionC(), b.optionD(), imageKey);
            case SA -> DedupCandidate.shortAnswer(b.sourceQuestionId(), b.questionText(), b.saAnswer(), imageKey);
            case TF -> DedupCandidate.trueFalse(b.sourceQuestionId(), b.questionText(), b.statements() == null
                    ? List.of() : b.statements().stream().map(BankItemDto.StatementDto::text).toList(), imageKey);
        };
    }

    private DedupCandidate fromGenerated(Integer questionId) {
        GeneratedQuestion q = generationService.getQuestionById(questionId);
        QuestionStatus status = q.getStatus();
        if (status == QuestionStatus.DUPLICATE || status == QuestionStatus.FORMAT_FAILED
                || status == QuestionStatus.REJECTED) {
            return null;
        }
        QuestionType type = q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MCQ;
        return switch (type) {
            case MCQ -> new DedupCandidate(q.getId(), q.getQuestionText(), q.getOptionA(), q.getOptionB(),
                    q.getOptionC(), q.getOptionD());
            case SA -> DedupCandidate.shortAnswer(q.getId(), q.getQuestionText(), q.getSaAnswer());
            case TF -> DedupCandidate.trueFalse(q.getId(), q.getQuestionText(),
                    generationService.getStatements(q.getId()).stream()
                            .map(GeneratedQuestionStatement::getStatementText).toList());
        };
    }
    @Override
    @Transactional(readOnly = true)
    public Integer examIdForSlot(Integer examSlotId) {
        return examSlotId == null ? null
                : slotRepository.findById(examSlotId).map(ExamSlot::getExamId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer creatorUserIdForSlot(Integer examSlotId) {
        if (examSlotId == null) {
            return null;
        }
        return slotRepository.findById(examSlotId)
                .flatMap(s -> examRepository.findById(s.getExamId()))
                .map(com.datn.mcq.exam.entity.Exam::getCreatedBy)
                .orElse(null);
    }
}