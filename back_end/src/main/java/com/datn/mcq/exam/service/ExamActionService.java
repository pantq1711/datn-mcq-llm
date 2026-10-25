package com.datn.mcq.exam.service;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.common.util.JsonStringArray;
import com.datn.mcq.exam.dto.ExamEditDtos.EditQuestionRequest;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.generation.dto.QuestionEditInput;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.generation.service.QuestionEditService;
import com.datn.mcq.exam.entity.ExamSlot;
import com.datn.mcq.exam.entity.ExamSlotAction;
import com.datn.mcq.exam.repository.ExamSlotActionRepository;
import com.datn.mcq.exam.repository.ExamSlotRepository;
import com.datn.mcq.llm.prompt.MatrixPromptDraftV0;
import com.datn.mcq.matrix.service.MatrixService;
import com.datn.mcq.review.service.TeacherReviewService;
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

    /** Mã lỗi 409 của W1-2 (D7). */
    public static final String SLOT_KEPT = "SLOT_KEPT";
    public static final String SLOT_NOT_READY = "SLOT_NOT_READY";
    public static final String SLOT_FROM_BANK = "SLOT_FROM_BANK";
    public static final String VERSION_CONFLICT = "VERSION_CONFLICT";
    public static final String QC_CHECKING = "QC_CHECKING";
    static final String QC_CHECKING_MESSAGE =
            "Câu vừa sửa đang được kiểm tra lại (Solver), hãy chờ kiểm xong rồi Giữ.";

    private final ExamSlotRepository slotRepository;
    private final ExamSlotActionRepository actionRepository;
    private final ExamBuildService buildService;
    private final ExamViewService viewService;
    private final MatrixService matrixService;
    private final TeacherReviewService teacherReviewService;
    private final QuestionEditService questionEditService;
    private final GenerationService generationService;
    private final ExamSlotSiblings siblings;

    public ExamActionService(ExamSlotRepository slotRepository, ExamSlotActionRepository actionRepository,
                             ExamBuildService buildService, ExamViewService viewService, MatrixService matrixService,
                             TeacherReviewService teacherReviewService, QuestionEditService questionEditService,
                             GenerationService generationService, ExamSlotSiblings siblings) {
        this.siblings = siblings;
        this.slotRepository = slotRepository;
        this.actionRepository = actionRepository;
        this.buildService = buildService;
        this.viewService = viewService;
        this.matrixService = matrixService;
        this.teacherReviewService = teacherReviewService;
        this.questionEditService = questionEditService;
        this.generationService = generationService;
    }

    /**
     * Giữ câu: câu lấy từ ngân hàng thì chỉ đánh dấu; câu hệ thống sinh thì ghi teacher_review 'assisted' và bank_item
     * (TeacherReviewService#keepAssisted) trong cùng transaction.
     */
    @Transactional
    public ExamItemDto keep(Integer examId, Integer slotId, Integer actorId) {
        buildService.getOwnedExam(examId, actorId);
        buildService.reconcile(examId);
        ExamSlot slot = loadSlot(examId, slotId);
        if (ExamSlot.KEPT.equals(slot.getState()) || ExamSlot.EDITED.equals(slot.getState())) {
            throw new IllegalStateException(ALREADY_KEPT_MESSAGE);
        }
        if (!ExamSlot.READY.equals(slot.getState())) {
            throw new IllegalStateException(NO_QUESTION_MESSAGE);
        }
        boolean edited = false;
        if (slot.getGeneratedQuestionId() != null) {
            // W1-2 (D4): bản sửa còn đang chờ Solver kiểm lại thì chưa cho Giữ; job thất bại thì Giữ được (kèm cờ).
            if (questionEditService.hasActiveAnswerCheck(slot.getGeneratedQuestionId())) {
                throw ApiErrorException.conflict(QC_CHECKING, QC_CHECKING_MESSAGE);
            }
            edited = generationService.getQuestionById(slot.getGeneratedQuestionId()).getEditedFromQuestionId() != null;
            Integer bankItemId = teacherReviewService.keepAssisted(slot.getGeneratedQuestionId(), actorId);
            slot.setBankItemId(bankItemId);
        }
        // W1-2 (D6): Giữ bản giáo viên sửa thì chỗ ở trạng thái edited (V7), câu chưa sửa là kept.
        slot.setState(edited ? ExamSlot.EDITED : ExamSlot.KEPT);
        slotRepository.save(slot);
        actionRepository.save(new ExamSlotAction(slot.getId(), ExamSlotAction.KEEP, slot.getGeneratedQuestionId(),
                slot.getBankItemId(), null, actorId));
        return viewService.item(slot, matrixService.getCell(slot.getCellId()));
    }

    /**
     * Đổi câu khác: bỏ câu hiện tại khỏi chỗ (câu cũ giữ nguyên trạng thái để truy vết; câu đã Giữ vẫn ở ngân hàng) và
     * sinh lại đúng ô. Prompt sinh lại liệt kê các câu đã sinh của ô (gồm câu vừa bị đổi) để model không lặp lại.
     *
     * @param instruction yêu cầu bổ sung của giáo viên (tùy chọn, trim, tối đa
     *                    {@value MatrixPromptDraftV0#TEACHER_INSTRUCTION_MAX} ký tự): ghép vào cuối prompt_note của
     *                    job sinh lại và các lần thử tự động của cùng lượt Đổi, lưu ở nhật ký exam_slot_action (V9)
     */
    @Transactional
    public ExamItemDto replace(Integer examId, Integer slotId, Integer actorId, String instruction) {
        buildService.getOwnedExam(examId, actorId);
        String cleanInstruction = normalizeInstruction(instruction);
        buildService.reconcile(examId);
        ExamSlot slot = loadSlot(examId, slotId);
        if (ExamSlot.GENERATING.equals(slot.getState()) || ExamSlot.PENDING.equals(slot.getState())) {
            throw new IllegalStateException(GENERATING_MESSAGE);
        }
        ExamSlotAction log = new ExamSlotAction(slot.getId(), ExamSlotAction.REPLACE, slot.getGeneratedQuestionId(),
                slot.getBankItemId(), null, actorId);
        log.setInstruction(cleanInstruction);
        actionRepository.save(log);
        slot.setBankItemId(null);
        slot.setGeneratedQuestionId(null);
        slot.setFallbackQuestionId(null);
        slot.setAttempts(0);
        slot.setSolverRetries(0);
        buildService.enqueueReplace(slot, cleanInstruction);
        slotRepository.save(slot);
        return viewService.item(slot, matrixService.getCell(slot.getCellId()));
    }

    /**
     * W1-2: giáo viên sửa câu của một chỗ chưa Giữ. Mỗi lần sửa tạo một bản mới (generated_question, trỏ bản trước),
     * bản gốc LLM giữ nguyên; QC không cần LLM chạy ngay, Solver (nếu cần) xếp vào hàng đợi, request không chờ. Ghi một
     * dòng exam_slot_action 'edit' (bản mới, các trường đã đổi, edit_distance, người sửa, thời điểm).
     *
     * <p>Thứ tự: chủ sở hữu (404, chưa ghi gì) → đối soát, khóa mọi chỗ của đề (hai lần sửa đồng thời xếp hàng nhau) →
     * trạng thái chỗ (409) → phiên bản (409 VERSION_CONFLICT) → tạo bản sửa (400 khi sai hình dạng hoặc định dạng).
     */
    @Transactional
    public ExamItemDto editQuestion(Integer examId, Integer slotId, Integer actorId, EditQuestionRequest request) {
        buildService.getOwnedExam(examId, actorId);
        buildService.reconcile(examId);
        ExamSlot slot = loadSlot(examId, slotId);
        if (ExamSlot.KEPT.equals(slot.getState()) || ExamSlot.EDITED.equals(slot.getState())) {
            throw ApiErrorException.conflict(SLOT_KEPT, "Câu đã được Giữ vào ngân hàng; sửa câu ngân hàng chưa hỗ trợ.");
        }
        if (!ExamSlot.READY.equals(slot.getState())) {
            throw ApiErrorException.conflict(SLOT_NOT_READY, "Chỗ này chưa có câu để sửa (đang sinh hoặc hỏng).");
        }
        if (slot.getGeneratedQuestionId() == null) {
            throw ApiErrorException.conflict(SLOT_FROM_BANK,
                    "Câu lấy từ ngân hàng; sửa câu ngân hàng chưa hỗ trợ (dùng Đổi câu khác).");
        }
        if (request == null || request.baseQuestionId() == null) {
            throw ApiErrorException.badRequest(QuestionEditService.INVALID_REQUEST, "Thiếu baseQuestionId");
        }
        if (!request.baseQuestionId().equals(slot.getGeneratedQuestionId())) {
            throw ApiErrorException.conflict(VERSION_CONFLICT,
                    "Câu đã thay đổi kể từ lúc bạn mở (có người sửa hoặc đổi câu). Hãy tải lại rồi sửa tiếp.");
        }
        // W1-3: bản sửa cũng được so trùng với các câu đang nằm ở những chỗ khác của đề.
        QuestionEditService.EditOutcome outcome = questionEditService.createVersion(slot.getGeneratedQuestionId(),
                toInput(request), siblings.candidates(examId, slot.getId(), slot.getQuestionType()));
        if (outcome.changed()) {
            slot.setGeneratedQuestionId(outcome.questionId());
            slot.setState(ExamSlot.READY);
            slotRepository.save(slot);
            ExamSlotAction log = new ExamSlotAction(slot.getId(), ExamSlotAction.EDIT, outcome.questionId(), null,
                    outcome.editDistance(), actorId);
            log.setChangedFields(JsonStringArray.write(outcome.changedFields()));
            actionRepository.save(log);
        }
        return viewService.item(slot, matrixService.getCell(slot.getCellId()));
    }

    private static QuestionEditInput toInput(EditQuestionRequest r) {
        return new QuestionEditInput(r.questionText(), r.optionA(), r.optionB(), r.optionC(), r.optionD(),
                r.correctOption(), r.saAnswer(), r.statements() == null ? null : r.statements().stream()
                        .map(s -> s == null ? null : new QuestionEditInput.StatementEdit(s.key(), s.text(), s.label()))
                        .toList(),
                r.explanation());
    }

    /** Trim; trống thì null; dài hơn giới hạn thì 400. */
    static String normalizeInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return null;
        }
        String s = instruction.strip();
        int length = s.codePointCount(0, s.length());
        if (length > MatrixPromptDraftV0.TEACHER_INSTRUCTION_MAX) {
            throw new IllegalArgumentException("instruction dài tối đa " + MatrixPromptDraftV0.TEACHER_INSTRUCTION_MAX
                    + " ký tự (hiện " + length + ")");
        }
        return s;
    }

    private ExamSlot loadSlot(Integer examId, Integer slotId) {
        ExamSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu id=" + slotId + " trong đề"));
        if (!slot.getExamId().equals(examId)) {
            throw new NoSuchElementException("Không tìm thấy câu id=" + slotId + " trong đề");
        }
        return slot;
    }
}
