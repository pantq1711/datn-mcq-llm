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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * W1-3 (phần 2c): nội dung các câu đang nằm trong cùng đề, để dedup câu sinh mới và câu giáo viên sửa so với chúng. Chỉ
 * đọc.
 *
 * <p>Dedup theo run chỉ thấy câu qc_passed/in_bank và câu cùng run; còn thiếu (1) câu ở ô khác chưa qua QC (lệch Solver
 * được dùng kèm cờ, bản sửa đang chờ Solver), (2) văn bản câu ngân hàng đã sửa lúc duyệt (khác câu nguồn). Với câu ngân
 * hàng, ứng viên mang id của câu nguồn (generated_question) để kết quả dedup trỏ được tới bản ghi có thật.
 */
@Component
public class ExamSlotSiblings implements SlotDedupContext {

    private final ExamSlotRepository slotRepository;
    private final BankService bankService;
    private final GenerationService generationService;
    private final com.datn.mcq.exam.repository.ExamRepository examRepository;

    public ExamSlotSiblings(ExamSlotRepository slotRepository, BankService bankService,
                            GenerationService generationService,
                            com.datn.mcq.exam.repository.ExamRepository examRepository) {
        this.slotRepository = slotRepository;
        this.bankService = bankService;
        this.generationService = generationService;
        this.examRepository = examRepository;
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
        List<DedupCandidate> out = new ArrayList<>();
        for (ExamSlot other : slotRepository.findByExamIdOrderByCellIdAscSlotNoAsc(examId)) {
            if (other.getId().equals(excludeSlotId) || other.getQuestionType() != type) {
                continue;
            }
            DedupCandidate candidate = of(other);
            if (candidate != null && candidate.hasContent()) {
                out.add(candidate);
            }
        }
        return out;
    }

    private DedupCandidate of(ExamSlot slot) {
        if (ExamBuildService.hasQuestion(slot)) {
            if (slot.getBankItemId() != null) {
                // Câu ngân hàng (lấy từ ngân hàng hoặc đã Giữ): văn bản đang hiển thị trong đề là bản ngân hàng.
                BankItemDto bank = bankService.getItem(slot.getBankItemId());
                return fromBank(bank);
            }
            return slot.getGeneratedQuestionId() == null ? null : fromGenerated(slot.getGeneratedQuestionId());
        }
        if (ExamSlot.GENERATING.equals(slot.getState())) {
            // Chỗ đang sinh: câu vừa sinh của job mới nhất (đã tới bước QC) cũng đã nằm trong đề.
            Optional<GenerationJob> job = generationService.latestJobForSlot(slot.getId());
            if (job.isPresent() && job.get().getQuestionId() != null) {
                return fromGenerated(job.get().getQuestionId());
            }
        }
        return null;
    }

    private static DedupCandidate fromBank(BankItemDto b) {
        return switch (b.questionType()) {
            case MCQ -> new DedupCandidate(b.sourceQuestionId(), b.questionText(), b.optionA(), b.optionB(),
                    b.optionC(), b.optionD());
            case SA -> DedupCandidate.shortAnswer(b.sourceQuestionId(), b.questionText(), b.saAnswer());
            case TF -> DedupCandidate.trueFalse(b.sourceQuestionId(), b.questionText(), b.statements() == null
                    ? List.of() : b.statements().stream().map(BankItemDto.StatementDto::text).toList());
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