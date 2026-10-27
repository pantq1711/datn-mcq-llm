package com.datn.mcq.exam.service;

import com.datn.mcq.bank.service.BankService;
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
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import com.datn.mcq.matrix.service.MatrixService;
import com.datn.mcq.review.service.AssistedKeepService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    /** W1-4 (D5-B): Đổi bằng câu ngân hàng khi ngân hàng hết câu phù hợp (409; chỗ giữ nguyên). */
    public static final String BANK_EXHAUSTED = "BANK_EXHAUSTED";
    static final String BANK_EXHAUSTED_MESSAGE = "Ngân hàng không còn câu phù hợp cho chỗ này (cùng môn, dạng, mức, chủ "
            + "đề; đã loại câu có trong đề và câu đã đổi ra). Hãy sinh câu mới.";
    static final String QC_CHECKING_MESSAGE =
            "Câu vừa sửa đang được kiểm tra lại (Solver), hãy chờ kiểm xong rồi Giữ.";

    private final ExamSlotRepository slotRepository;
    private final ExamSlotActionRepository actionRepository;
    private final ExamBuildService buildService;
    private final ExamViewService viewService;
    private final MatrixService matrixService;
    private final AssistedKeepService assistedKeepService;
    private final QuestionEditService questionEditService;
    private final GenerationService generationService;
    private final ExamSlotSiblings siblings;
    private final ExamContextService contextService;
    private final BankService bankService;

    public ExamActionService(ExamSlotRepository slotRepository, ExamSlotActionRepository actionRepository,
                             ExamBuildService buildService, ExamViewService viewService, MatrixService matrixService,
                             AssistedKeepService assistedKeepService, QuestionEditService questionEditService,
                             GenerationService generationService, ExamSlotSiblings siblings,
                             ExamContextService contextService, BankService bankService) {
        this.siblings = siblings;
        this.slotRepository = slotRepository;
        this.actionRepository = actionRepository;
        this.buildService = buildService;
        this.viewService = viewService;
        this.matrixService = matrixService;
        this.assistedKeepService = assistedKeepService;
        this.questionEditService = questionEditService;
        this.generationService = generationService;
        this.contextService = contextService;
        this.bankService = bankService;
    }

    /**
     * Giữ câu: câu lấy từ ngân hàng thì chỉ đánh dấu; câu hệ thống sinh thì ghi teacher_review 'assisted' và bank_item
     * (AssistedKeepService#keepAssisted) trong cùng transaction.
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
            Integer bankItemId = assistedKeepService.keepAssisted(slot.getGeneratedQuestionId(), actorId);
            slot.setBankItemId(bankItemId);
            // W2-3 (quyết định E): câu sinh từ tài liệu của giáo viên vào ngân hàng nhưng chỉ chính họ thấy và dùng lại.
            if (!contextService.materialsFor(examId, slot.getCellId()).isEmpty()) {
                bankService.restrictToUser(bankItemId, actorId);
            }
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
     * @param source      W1-4 (D5-B): "generate" (mặc định khi bỏ trống) sinh câu mới qua hàng đợi; "bank" lấy câu ngân
     *                    hàng khớp ô ngay (không job), hết câu thì 409 {@value #BANK_EXHAUSTED} và chỗ giữ nguyên. Giá trị
     *                    khác, hoặc "bank" kèm instruction: 400 INVALID_REQUEST (trước mọi thay đổi)
     * @param instruction yêu cầu bổ sung của giáo viên (tùy chọn, trim, tối đa
     *                    {@value MatrixPromptDraftV0#TEACHER_INSTRUCTION_MAX} ký tự): ghép vào cuối prompt_note của
     *                    job sinh lại và các lần thử tự động của cùng lượt Đổi, lưu ở nhật ký exam_slot_action (V9)
     */
    @Transactional
    public ExamItemDto replace(Integer examId, Integer slotId, Integer actorId, String source, String instruction) {
        buildService.getOwnedExam(examId, actorId);
        String replaceSource = normalizeSource(source);
        String cleanInstruction = normalizeInstruction(instruction);
        if (ExamSlotAction.SOURCE_BANK.equals(replaceSource) && cleanInstruction != null) {
            throw ApiErrorException.badRequest(QuestionEditService.INVALID_REQUEST,
                    "instruction chỉ dùng khi source = generate (lấy câu ngân hàng không sinh câu mới).");
        }
        buildService.reconcile(examId);
        ExamSlot slot = loadSlot(examId, slotId);
        if (ExamSlot.GENERATING.equals(slot.getState()) || ExamSlot.PENDING.equals(slot.getState())) {
            throw new IllegalStateException(GENERATING_MESSAGE);
        }
        // Chọn câu ngân hàng TRƯỚC mọi thay đổi: hết câu thì chỗ giữ nguyên, không nhật ký. Truy vấn native tự ghi các
        // thay đổi đang chờ xuống DB, nên không được đã bỏ câu khỏi chỗ (chk_es_ready, L-33).
        Integer newBankItemId = null;
        if (ExamSlotAction.SOURCE_BANK.equals(replaceSource)) {
            ExamMatrixCell cell = matrixService.getCell(slot.getCellId());
            List<Integer> candidates = buildService.bankCandidates(examId, actorId,
                    matrixService.getMatrix(cell.getMatrixId()), cell, 1);
            if (candidates.isEmpty()) {
                throw ApiErrorException.conflict(BANK_EXHAUSTED, BANK_EXHAUSTED_MESSAGE);
            }
            newBankItemId = candidates.get(0);
        }
        ExamSlotAction log = new ExamSlotAction(slot.getId(), ExamSlotAction.REPLACE, slot.getGeneratedQuestionId(),
                slot.getBankItemId(), null, actorId);
        log.setInstruction(cleanInstruction);
        log.setReplaceSource(replaceSource);
        actionRepository.save(log);
        slot.setBankItemId(newBankItemId);
        slot.setGeneratedQuestionId(null);
        slot.setFallbackQuestionId(null);
        slot.setFailureReason(null);
        slot.setAttempts(0);
        slot.setSolverRetries(0);
        if (newBankItemId != null) {
            slot.setState(ExamSlot.READY);
        } else {
            buildService.enqueueReplace(slot, cleanInstruction);
        }
        slotRepository.save(slot);
        return viewService.item(slot, matrixService.getCell(slot.getCellId()));
    }

    /** Bỏ trống thì "generate"; chỉ nhận "bank" hoặc "generate" (không phân biệt hoa thường), còn lại 400. */
    static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return ExamSlotAction.SOURCE_GENERATE;
        }
        String s = source.strip().toLowerCase(java.util.Locale.ROOT);
        if (!s.equals(ExamSlotAction.SOURCE_BANK) && !s.equals(ExamSlotAction.SOURCE_GENERATE)) {
            throw ApiErrorException.badRequest(QuestionEditService.INVALID_REQUEST,
                    "source phải là \"bank\" hoặc \"generate\".");
        }
        return s;
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
                toInput(request), siblings.candidates(examId, slot.getId(), slot.getQuestionType()), actorId);
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
