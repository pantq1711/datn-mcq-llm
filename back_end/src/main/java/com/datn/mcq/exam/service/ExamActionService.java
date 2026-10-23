package com.datn.mcq.exam.service;

import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.entity.ExamSlot;
import com.datn.mcq.exam.entity.ExamSlotAction;
import com.datn.mcq.exam.repository.ExamSlotActionRepository;
import com.datn.mcq.exam.repository.ExamSlotRepository;
import com.datn.mcq.matrix.service.MatrixService;
import com.datn.mcq.review.service.TeacherReviewService;
import com.datn.mcq.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Thao tác trên từng câu của đề ở chế độ giáo viên (V7, phần C mục 4): Giữ (vào ngân hàng) và Đổi câu khác (sinh lại
 * đúng ô). Mỗi thao tác ghi một dòng exam_slot_action để thống kê giữ/sửa/đổi, tách khỏi chỉ số chấm mù.
 */
@Service
public class ExamActionService {

    static final String NO_QUESTION_MESSAGE = "Chỗ này chưa có câu hỏi.";
    static final String ALREADY_KEPT_MESSAGE = "Câu hỏi này đã được giữ.";
    static final String GENERATING_MESSAGE = "Chỗ này đang sinh câu hỏi, hãy chờ xong rồi thao tác.";

    private final ExamSlotRepository slotRepository;
    private final ExamSlotActionRepository actionRepository;
    private final ExamBuildService buildService;
    private final ExamViewService viewService;
    private final MatrixService matrixService;
    private final TeacherReviewService teacherReviewService;
    private final UserService userService;

    public ExamActionService(ExamSlotRepository slotRepository, ExamSlotActionRepository actionRepository,
                             ExamBuildService buildService, ExamViewService viewService, MatrixService matrixService,
                             TeacherReviewService teacherReviewService, UserService userService) {
        this.slotRepository = slotRepository;
        this.actionRepository = actionRepository;
        this.buildService = buildService;
        this.viewService = viewService;
        this.matrixService = matrixService;
        this.teacherReviewService = teacherReviewService;
        this.userService = userService;
    }

    /**
     * Giữ câu: câu lấy từ ngân hàng thì chỉ đánh dấu; câu hệ thống sinh thì ghi teacher_review 'assisted' và bank_item
     * (TeacherReviewService#keepAssisted) trong cùng transaction.
     */
    @Transactional
    public ExamItemDto keep(Integer examId, Integer slotId, Integer teacherId) {
        buildService.reconcile(examId);
        ExamSlot slot = loadSlot(examId, slotId);
        if (ExamSlot.KEPT.equals(slot.getState()) || ExamSlot.EDITED.equals(slot.getState())) {
            throw new IllegalStateException(ALREADY_KEPT_MESSAGE);
        }
        if (!ExamSlot.READY.equals(slot.getState())) {
            throw new IllegalStateException(NO_QUESTION_MESSAGE);
        }
        Integer actorId = actor(teacherId);
        if (slot.getGeneratedQuestionId() != null) {
            Integer bankItemId = teacherReviewService.keepAssisted(slot.getGeneratedQuestionId(), actorId);
            slot.setBankItemId(bankItemId);
        }
        slot.setState(ExamSlot.KEPT);
        slotRepository.save(slot);
        actionRepository.save(new ExamSlotAction(slot.getId(), ExamSlotAction.KEEP, slot.getGeneratedQuestionId(),
                slot.getBankItemId(), null, actorId));
        return viewService.item(slot, matrixService.getCell(slot.getCellId()));
    }

    /**
     * Đổi câu khác: bỏ câu hiện tại khỏi chỗ (câu cũ giữ nguyên trạng thái để truy vết; câu đã Giữ vẫn ở ngân hàng) và
     * sinh lại đúng ô. Prompt sinh lại liệt kê các câu đã sinh của ô (gồm câu vừa bị đổi) để model không lặp lại.
     */
    @Transactional
    public ExamItemDto replace(Integer examId, Integer slotId, Integer teacherId) {
        buildService.reconcile(examId);
        ExamSlot slot = loadSlot(examId, slotId);
        if (ExamSlot.GENERATING.equals(slot.getState()) || ExamSlot.PENDING.equals(slot.getState())) {
            throw new IllegalStateException(GENERATING_MESSAGE);
        }
        Integer actorId = actor(teacherId);
        actionRepository.save(new ExamSlotAction(slot.getId(), ExamSlotAction.REPLACE, slot.getGeneratedQuestionId(),
                slot.getBankItemId(), null, actorId));
        slot.setBankItemId(null);
        slot.setGeneratedQuestionId(null);
        slot.setFallbackQuestionId(null);
        slot.setAttempts(0);
        slot.setSolverRetries(0);
        buildService.enqueueRetry(slot);
        slotRepository.save(slot);
        return viewService.item(slot, matrixService.getCell(slot.getCellId()));
    }

    private ExamSlot loadSlot(Integer examId, Integer slotId) {
        ExamSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu id=" + slotId + " trong đề"));
        if (!slot.getExamId().equals(examId)) {
            throw new NoSuchElementException("Không tìm thấy câu id=" + slotId + " trong đề");
        }
        return slot;
    }

    private Integer actor(Integer teacherId) {
        return teacherId != null ? userService.getById(teacherId).getId()
                : userService.getOrCreateDefaultTeacher().getId();
    }
}
