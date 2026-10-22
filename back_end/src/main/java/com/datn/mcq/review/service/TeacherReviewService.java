package com.datn.mcq.review.service;

import com.datn.mcq.bank.entity.BankItem;
import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.common.util.TextDistanceUtils;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.ReasonCode;
import com.datn.mcq.enums.ReviewDecision;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.service.ExperimentService;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.parser.LatexFormatValidator;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.review.dto.BlindQuestionReviewDto;
import com.datn.mcq.review.dto.ReviewQuestionListItemDto;
import com.datn.mcq.review.dto.SubmitReviewRequest;
import com.datn.mcq.review.dto.SubmitReviewResponse;
import com.datn.mcq.review.entity.TeacherReview;
import com.datn.mcq.review.entity.TeacherReviewReason;
import com.datn.mcq.review.repository.TeacherReviewReasonRepository;
import com.datn.mcq.review.repository.TeacherReviewRepository;
import com.datn.mcq.user.entity.UserAccount;
import com.datn.mcq.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class TeacherReviewService {

    /** Trạng thái không vào vòng chấm của cả production và experiment (quyết định 21/10 mục 5). */
    private static final Set<QuestionStatus> EXCLUDED_FROM_REVIEW =
            EnumSet.of(QuestionStatus.DUPLICATE, QuestionStatus.FORMAT_FAILED);

    /** Thông điệp chung cho câu không tồn tại hoặc bị loại khỏi vòng chấm; không nhắc trùng, định dạng hay QC. */
    static final String NOT_FOUND_MESSAGE = "Không tìm thấy câu hỏi";

    static final String NOT_READY_FOR_BANK_MESSAGE =
            "Câu hỏi chưa đủ điều kiện để duyệt vào ngân hàng câu hỏi.";

    /** Thông điệp trung tính khi request gửi editedExplanation (quyết định 2 ngày 22/10). */
    static final String EDITED_EXPLANATION_NOT_ACCEPTED_MESSAGE =
            "Trường editedExplanation không được hỗ trợ ở bước chấm này, hãy để trống.";

    private final TeacherReviewRepository teacherReviewRepository;
    private final TeacherReviewReasonRepository teacherReviewReasonRepository;
    private final GenerationService generationService;
    private final CatalogService catalogService;
    private final BankService bankService;
    private final UserService userService;
    private final ExperimentService experimentService;

    public TeacherReviewService(
            TeacherReviewRepository teacherReviewRepository,
            TeacherReviewReasonRepository teacherReviewReasonRepository,
            GenerationService generationService,
            CatalogService catalogService,
            BankService bankService,
            UserService userService,
            ExperimentService experimentService) {
        this.teacherReviewRepository = teacherReviewRepository;
        this.teacherReviewReasonRepository = teacherReviewReasonRepository;
        this.generationService = generationService;
        this.catalogService = catalogService;
        this.bankService = bankService;
        this.userService = userService;
        this.experimentService = experimentService;
    }

    /**
     * Danh sách chấm mù (quyết định 21/10 mục 2 và 5, quyết định 1 ngày 22/10):
     * <ul>
     *   <li>production: chỉ câu qc_passed. Mọi lần nộp đánh giá production đều chuyển câu sang in_bank hoặc
     *       rejected, nên câu qc_passed là câu chưa được chấm; không cần (và chưa có) danh tính người chấm
     *       phía server để lọc thêm theo người chấm (Q-6).</li>
     *   <li>experiment: loại duplicate và format_failed; không lọc theo kết quả answer_check.</li>
     * </ul>
     * Không nhận tham số lọc theo trạng thái để người chấm không suy ra được kết quả QC.
     * Item không chứa status hay kết quả QC.
     */
    @Transactional(readOnly = true)
    public List<ReviewQuestionListItemDto> listQuestionsForReview() {
        List<GeneratedQuestion> questions = generationService.listQuestions(null);
        List<ReviewQuestionListItemDto> result = new ArrayList<>();
        for (GeneratedQuestion gq : questions) {
            if (!isReviewable(gq)) {
                continue;
            }
            ExperimentRun run = experimentService.getById(gq.getRunId());
            if (run.getPurpose() == Purpose.PRODUCTION && gq.getStatus() != QuestionStatus.QC_PASSED) {
                continue;
            }
            QuestionTemplate tpl = catalogService.getTemplateById(gq.getTemplateId());
            Subject subject = catalogService.getSubjectById(tpl.getSubjectId());

            result.add(new ReviewQuestionListItemDto(
                    gq.getId(),
                    gq.getTemplateId(),
                    run.getPurpose(),
                    subject.getName(),
                    tpl.getGradeLevel(),
                    tpl.getTopic(),
                    gq.getQuestionText(),
                    gq.getCreatedAt()
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BlindQuestionReviewDto getBlindQuestionForReview(Integer questionId) {
        GeneratedQuestion gq = loadReviewableQuestion(questionId);
        QuestionTemplate tpl = catalogService.getTemplateById(gq.getTemplateId());
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());
        ExperimentRun run = experimentService.getById(gq.getRunId());

        return new BlindQuestionReviewDto(
                gq.getId(),
                gq.getTemplateId(),
                run.getPurpose(),
                subject.getName(),
                tpl.getGradeLevel(),
                tpl.getTopic(),
                gq.getQuestionText(),
                gq.getOptionA(),
                gq.getOptionB(),
                gq.getOptionC(),
                gq.getOptionD(),
                gq.getCreatedAt()
        );
    }

    @Transactional
    public SubmitReviewResponse submitReview(Integer questionId, SubmitReviewRequest req) {
        validateSubmitRequest(req);

        // Câu duplicate/format_failed: 404 như câu không tồn tại, trước mọi thao tác ghi (không ghi đè trạng thái cuối).
        GeneratedQuestion gq = loadReviewableQuestion(questionId);
        validateReasonCodesForType(req.reasonCodes(), gq.getQuestionType());
        QuestionTemplate tpl = catalogService.getTemplateById(gq.getTemplateId());
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());
        ExperimentRun run = experimentService.getById(gq.getRunId());

        Purpose actualPurpose = run.getPurpose();
        if (req.reviewPurpose() != null && req.reviewPurpose() != actualPurpose) {
            throw new IllegalArgumentException(
                    "reviewPurpose (" + req.reviewPurpose().getDbValue()
                    + ") không khớp với nguồn gốc của câu hỏi (" + actualPurpose.getDbValue() + ").");
        }

        Integer reviewerId = req.reviewerId();
        if (reviewerId == null) {
            UserAccount defaultTeacher = userService.getOrCreateDefaultTeacher();
            reviewerId = defaultTeacher.getId();
        } else {
            userService.getById(reviewerId);
        }

        // Chặn người chấm thực nghiệm gửi lại nhiều lần để dò đáp án hoặc ghi đè nhãn
        if (actualPurpose == Purpose.EXPERIMENT) {
            if (teacherReviewRepository.findByQuestionIdAndReviewerId(questionId, reviewerId).isPresent()) {
                throw new IllegalStateException(
                        "Người chấm đã nộp đánh giá cho câu hỏi thực nghiệm này, không thể gửi lại hoặc ghi đè.");
            }
        }

        AnswerChoice reviewerAnswer = req.reviewerAnswer();
        ReviewDecision decision = req.decision();

        boolean answerCorrect = gq.getCorrectOption() != null
                && reviewerAnswer.getDbValue().equals(gq.getCorrectOption().getDbValue());

        Option finalOption = null;
        boolean edited = false;
        Integer editDistance = null;
        String savedEditedQ = null;
        String savedEditedA = null;
        String savedEditedB = null;
        String savedEditedC = null;
        String savedEditedD = null;

        if (decision == ReviewDecision.APPROVED_AS_IS) {
            Option reviewerAsOption = Option.valueOf(reviewerAnswer.name());
            if (req.finalOption() != null && req.finalOption() != reviewerAsOption) {
                throw new IllegalArgumentException(
                        "Khi chọn approved_as_is, final_option phải trùng với reviewer_answer (" + reviewerAsOption.getDbValue() + ")");
            }
            finalOption = reviewerAsOption;
        } else if (decision == ReviewDecision.APPROVED_WITH_EDIT) {
            if (req.finalOption() == null) {
                throw new IllegalArgumentException("Khi chọn approved_with_edit, bắt buộc chọn final_option (A-D)");
            }
            finalOption = req.finalOption();
            String effQ = fallback(req.editedQuestionText(), gq.getQuestionText());
            String effA = fallback(req.editedOptionA(), gq.getOptionA());
            String effB = fallback(req.editedOptionB(), gq.getOptionB());
            String effC = fallback(req.editedOptionC(), gq.getOptionC());
            String effD = fallback(req.editedOptionD(), gq.getOptionD());

            if (effQ.isBlank() || effA.isBlank() || effB.isBlank() || effC.isBlank() || effD.isBlank()) {
                throw new IllegalArgumentException("Nội dung câu hỏi và 4 phương án A-D sau khi sửa không được để trống");
            }
            // Chỉ kiểm tra nội dung giáo viên thấy và sửa được (đề và 4 phương án); lời giải ẩn không tham gia,
            // để lỗi trả về không tiết lộ gì về lời giải (quyết định 2 ngày 22/10).
            List<String> editedLatexErrors = collectLatexErrors(
                    subject.getName(), effQ, effA, effB, effC, effD, null);
            if (!editedLatexErrors.isEmpty()) {
                throw new IllegalArgumentException(
                        "Bản sửa còn lỗi định dạng công thức LaTeX: " + String.join("; ", editedLatexErrors));
            }

            // edit_distance: tổng Levenshtein trên question_text và 4 phương án (V1, quyết định 2 ngày 22/10).
            int dist = TextDistanceUtils.totalQuestionEditDistance(
                    gq.getQuestionText(), gq.getOptionA(), gq.getOptionB(), gq.getOptionC(), gq.getOptionD(),
                    effQ, effA, effB, effC, effD
            );
            if (dist > 0) {
                edited = true;
                editDistance = dist;
                savedEditedQ = effQ;
                savedEditedA = effA;
                savedEditedB = effB;
                savedEditedC = effC;
                savedEditedD = effD;
            }
        }

        TeacherReview review = teacherReviewRepository
                .findByQuestionIdAndReviewerId(questionId, reviewerId)
                .orElseGet(TeacherReview::new);
        review.setQuestionId(questionId);
        review.setReviewerId(reviewerId);
        review.setReviewPurpose(actualPurpose);
        review.setReviewerAnswer(reviewerAnswer);
        review.setAnswerCorrect(answerCorrect);
        review.setFormatOk(req.formatOk());
        review.setDifficultyLabel(req.difficultyLabel());
        review.setDecision(decision);
        review.setFinalOption(finalOption);
        review.setEdited(edited);
        review.setEditedQuestionText(savedEditedQ);
        review.setEditedOptionA(savedEditedA);
        review.setEditedOptionB(savedEditedB);
        review.setEditedOptionC(savedEditedC);
        review.setEditedOptionD(savedEditedD);
        // Bước chấm này không nhận sửa lời giải: edited_explanation luôn NULL (quyết định 2 ngày 22/10).
        review.setEditedExplanation(null);
        review.setEditDistance(editDistance);
        review.setReviewedAt(LocalDateTime.now());
        TeacherReview savedReview = teacherReviewRepository.save(review);

        teacherReviewReasonRepository.deleteByReviewId(savedReview.getId());
        if (decision == ReviewDecision.REJECTED && req.reasonCodes() != null) {
            Set<ReasonCode> uniqueCodes = new LinkedHashSet<>(req.reasonCodes());
            for (ReasonCode code : uniqueCodes) {
                String detail = (code == ReasonCode.OTHER) ? req.otherReasonDetail().trim() : null;
                teacherReviewReasonRepository.save(
                        new TeacherReviewReason(savedReview.getId(), code, detail));
            }
        }

        Integer bankItemId = null;
        // Experiment: không trả trạng thái câu hỏi (là kết quả QC) trong response, theo M-7.
        QuestionStatus resultingQuestionStatus = null;
        if (actualPurpose == Purpose.PRODUCTION) {
            resultingQuestionStatus = gq.getStatus();
            if (decision == ReviewDecision.APPROVED_AS_IS || decision == ReviewDecision.APPROVED_WITH_EDIT) {
                // Chỉ câu đã qua kiểm tra chất lượng tự động (hoặc đang trong ngân hàng) mới được duyệt vào ngân hàng.
                // Thông báo không nêu trạng thái hay kết quả QC (quyết định 21/10 mục 2).
                if (gq.getStatus() != QuestionStatus.QC_PASSED && gq.getStatus() != QuestionStatus.IN_BANK) {
                    throw new IllegalArgumentException(NOT_READY_FOR_BANK_MESSAGE);
                }

                String finalQ = edited ? savedEditedQ : gq.getQuestionText();
                String finalA = edited ? savedEditedA : gq.getOptionA();
                String finalB = edited ? savedEditedB : gq.getOptionB();
                String finalC = edited ? savedEditedC : gq.getOptionC();
                String finalD = edited ? savedEditedD : gq.getOptionD();
                // Ngân hàng giữ lời giải LLM (quyết định 21/10 mục 3), trừ khi lời giải còn lỗi định dạng
                // LaTeX nghiêm trọng thì lưu NULL (việc 2 ngày 22/10).
                String finalExp = explanationForBank(subject.getName(), gq.getExplanation());

                GeneratedQuestion updatedGq = generationService.updateStatusAndDifficulty(
                        gq.getId(), QuestionStatus.IN_BANK, req.difficultyLabel());
                resultingQuestionStatus = updatedGq.getStatus();

                BankItem item = bankService.createOrUpdateFromReview(
                        gq.getId(),
                        savedReview.getId(),
                        finalQ,
                        finalA,
                        finalB,
                        finalC,
                        finalD,
                        finalOption,
                        finalExp,
                        req.difficultyLabel()
                );
                bankItemId = item.getId();
            } else if (decision == ReviewDecision.REJECTED) {
                GeneratedQuestion updatedGq = generationService.updateStatusAndDifficulty(
                        gq.getId(), QuestionStatus.REJECTED, req.difficultyLabel());
                resultingQuestionStatus = updatedGq.getStatus();
                // Thu hồi / xóa khỏi bank_item nếu câu hỏi trước đó từng được duyệt vào ngân hàng
                bankService.removeBankItemIfUnused(gq.getId());
            }
        }

        return new SubmitReviewResponse(
                savedReview.getId(),
                gq.getId(),
                actualPurpose,
                reviewerAnswer,
                req.formatOk(),
                req.difficultyLabel(),
                decision,
                finalOption,
                edited,
                editDistance,
                resultingQuestionStatus,
                bankItemId,
                savedReview.getReviewedAt()
        );
    }

    public static void validateSubmitRequest(SubmitReviewRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Body đánh giá không được rỗng");
        }
        if (req.editedExplanation() != null) {
            throw new IllegalArgumentException(EDITED_EXPLANATION_NOT_ACCEPTED_MESSAGE);
        }
        if (req.reviewerAnswer() == null) {
            throw new IllegalArgumentException("Phải chọn reviewerAnswer (A, B, C, D, multiple, none)");
        }
        if (req.formatOk() == null) {
            throw new IllegalArgumentException("Phải chọn đánh giá định dạng (formatOk)");
        }
        if (req.difficultyLabel() == null) {
            throw new IllegalArgumentException("Phải chọn mức độ khó (difficultyLabel)");
        }
        if (req.decision() == null) {
            throw new IllegalArgumentException("Phải chọn kết luận (decision)");
        }

        if ((req.reviewerAnswer() == AnswerChoice.MULTIPLE || req.reviewerAnswer() == AnswerChoice.NONE)
                && req.decision() == ReviewDecision.APPROVED_AS_IS) {
            throw new IllegalArgumentException(
                    "Khi reviewer_answer là 'multiple' hoặc 'none', không được chọn 'approved_as_is'");
        }

        if (req.decision() == ReviewDecision.REJECTED) {
            if (req.finalOption() != null) {
                throw new IllegalArgumentException("Khi chọn 'rejected', final_option phải để trống (null)");
            }
            if (req.reasonCodes() == null || req.reasonCodes().isEmpty()) {
                throw new IllegalArgumentException("Khi chọn 'rejected', phải có ít nhất 1 lý do trong reasonCodes");
            }
            if (req.reasonCodes().contains(ReasonCode.OTHER)
                    && (req.otherReasonDetail() == null || req.otherReasonDetail().trim().isEmpty())) {
                throw new IllegalArgumentException("Khi chọn lý do 'other', bắt buộc nhập chi tiết (otherReasonDetail)");
            }
        }
    }

    /**
     * Mã lý do phải dùng được cho dạng câu (quyết định 3 ngày 22/10): statement_undecidable chỉ cho tf,
     * answer_ambiguous chỉ cho sa, mã riêng mcq không dùng cho tf/sa. Trước V4, hai mã mới không có trong enum
     * nên request mcq chứa chúng bị từ chối 400 khi đọc JSON; kiểm tra này giữ mã 400 đó cho mcq.
     */
    static void validateReasonCodesForType(List<ReasonCode> reasonCodes, QuestionType questionType) {
        if (reasonCodes == null) {
            return;
        }
        QuestionType type = questionType != null ? questionType : QuestionType.MCQ;
        for (ReasonCode code : reasonCodes) {
            if (code != null && !code.allowedFor(type)) {
                throw new IllegalArgumentException(
                        "Mã lý do '" + code.getDbValue() + "' không áp dụng cho dạng câu này.");
            }
        }
    }

    private List<String> collectLatexErrors(
            String subjectName,
            String q, String a, String b, String c, String d, String exp) {
        List<String> errors = new ArrayList<>();
        if (!GenerationJobProcessor.isMathSubject(subjectName)) {
            return errors;
        }
        appendFieldErrors("question_text", q, errors);
        appendFieldErrors("option_a", a, errors);
        appendFieldErrors("option_b", b, errors);
        appendFieldErrors("option_c", c, errors);
        appendFieldErrors("option_d", d, errors);
        if (exp != null && !exp.isBlank()) {
            appendFieldErrors("explanation", exp, errors);
        }
        return errors;
    }

    /**
     * Lời giải chép vào bank_item (việc 2 ngày 22/10): với môn Toán, lời giải còn lỗi định dạng LaTeX nghiêm trọng
     * thì trả null; lời giải hợp lệ hoặc chỉ có cảnh báo được giữ nguyên. Môn khác không kiểm LaTeX.
     */
    static String explanationForBank(String subjectName, String explanation) {
        if (explanation == null || explanation.isBlank() || !GenerationJobProcessor.isMathSubject(subjectName)) {
            return explanation;
        }
        return LatexFormatValidator.check(explanation).hasErrors() ? null : explanation;
    }

    private static boolean isReviewable(GeneratedQuestion gq) {
        return gq.getStatus() == null || !EXCLUDED_FROM_REVIEW.contains(gq.getStatus());
    }

    /**
     * Lấy câu cho detail/submit chấm mù. Câu không tồn tại và câu bị loại khỏi vòng chấm (duplicate, format_failed)
     * đều ném cùng một NoSuchElementException với thông điệp chung, để người chấm không phân biệt được.
     */
    private GeneratedQuestion loadReviewableQuestion(Integer questionId) {
        GeneratedQuestion gq;
        try {
            gq = generationService.getQuestionById(questionId);
        } catch (NoSuchElementException ex) {
            throw new NoSuchElementException(NOT_FOUND_MESSAGE);
        }
        if (!isReviewable(gq)) {
            throw new NoSuchElementException(NOT_FOUND_MESSAGE);
        }
        return gq;
    }

    /** Chỉ lỗi fatal; cảnh báo (lệnh ngoài danh sách cho phép) không chặn bản sửa. */
    private void appendFieldErrors(String field, String text, List<String> errors) {
        for (String err : LatexFormatValidator.check(text).errors()) {
            errors.add(field + ": " + err);
        }
    }

    private String fallback(String candidate, String original) {
        if (candidate != null) {
            return candidate.trim();
        }
        return original != null ? original.trim() : "";
    }
}

