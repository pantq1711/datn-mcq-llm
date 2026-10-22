package com.datn.mcq.review.service;

import com.datn.mcq.bank.dto.BankStatementInput;
import com.datn.mcq.bank.entity.BankItem;
import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.common.util.ShortAnswerValue;
import com.datn.mcq.common.util.TextDistanceUtils;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.ReasonCode;
import com.datn.mcq.enums.ReviewDecision;
import com.datn.mcq.enums.SaAnswerKind;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.service.ExperimentService;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import com.datn.mcq.generation.parser.LatexFormatValidator;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.review.dto.BlindQuestionReviewDto;
import com.datn.mcq.review.dto.BlindStatementDto;
import com.datn.mcq.review.dto.ReviewQuestionListItemDto;
import com.datn.mcq.review.dto.StatementReviewInput;
import com.datn.mcq.review.dto.StatementReviewResult;
import com.datn.mcq.review.dto.SubmitReviewRequest;
import com.datn.mcq.review.dto.SubmitReviewResponse;
import com.datn.mcq.review.entity.TeacherReview;
import com.datn.mcq.review.entity.TeacherReviewReason;
import com.datn.mcq.review.entity.TeacherReviewStatement;
import com.datn.mcq.review.repository.TeacherReviewReasonRepository;
import com.datn.mcq.review.repository.TeacherReviewRepository;
import com.datn.mcq.review.repository.TeacherReviewStatementRepository;
import com.datn.mcq.user.entity.UserAccount;
import com.datn.mcq.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // V4, GĐ2 (câu Trả lời ngắn). Các thông điệp chỉ nói về dữ liệu giáo viên gửi, không nhắc đáp số hay QC của LLM.
    static final String TYPE_MISMATCH_MESSAGE = "Dữ liệu đánh giá không khớp dạng câu hỏi.";
    static final String SA_FIELD_NOT_APPLICABLE = "Trường %s không áp dụng cho câu trả lời ngắn.";
    static final String REVIEWER_SA_UNREADABLE_MESSAGE = "Đáp số giáo viên nhập không đọc được thành một số.";
    static final String FINAL_SA_INVALID_PREFIX = "Đáp số cuối không hợp lệ: ";
    static final String LEADING_ZERO_HINT_PREFIX = "Đáp số có chữ số 0 thừa ở đầu, hãy nhập ";

    private static final Logger LOG = LoggerFactory.getLogger(TeacherReviewService.class);

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
    private final TeacherReviewStatementRepository teacherReviewStatementRepository;

    public TeacherReviewService(
            TeacherReviewRepository teacherReviewRepository,
            TeacherReviewReasonRepository teacherReviewReasonRepository,
            GenerationService generationService,
            CatalogService catalogService,
            BankService bankService,
            UserService userService,
            ExperimentService experimentService,
            TeacherReviewStatementRepository teacherReviewStatementRepository) {
        this.teacherReviewRepository = teacherReviewRepository;
        this.teacherReviewReasonRepository = teacherReviewReasonRepository;
        this.generationService = generationService;
        this.catalogService = catalogService;
        this.bankService = bankService;
        this.userService = userService;
        this.experimentService = experimentService;
        this.teacherReviewStatementRepository = teacherReviewStatementRepository;
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
                    gq.getCreatedAt(),
                    gq.getQuestionType()
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
                gq.getCreatedAt(),
                gq.getQuestionType(),
                blindStatements(gq)
        );
    }

    /** GĐ3: 4 ý của câu tf, chỉ vị trí và nội dung (không có nhãn hay mức của LLM); mcq/sa trả null. */
    private List<BlindStatementDto> blindStatements(GeneratedQuestion gq) {
        if (gq.getQuestionType() != QuestionType.TF) {
            return null;
        }
        return generationService.getStatements(gq.getId()).stream()
                .map(s -> new BlindStatementDto(s.getStatementKey(), s.getStatementText()))
                .toList();
    }

    @Transactional
    public SubmitReviewResponse submitReview(Integer questionId, SubmitReviewRequest req) {
        // V4: dạng câu client đang chấm. Body mcq (không có trường sa, không ghi questionType) đi đúng đường kiểm tra
        // cũ, trước khi tải câu hỏi, để thứ tự lỗi 400/404 của mcq không đổi.
        QuestionType claimedType = claimedType(req);
        if (claimedType == QuestionType.MCQ) {
            validateSubmitRequest(req);
        }

        // Câu duplicate/format_failed: 404 như câu không tồn tại, trước mọi thao tác ghi (không ghi đè trạng thái cuối).
        GeneratedQuestion gq = loadReviewableQuestion(questionId);
        QuestionType actualType = gq.getQuestionType() != null ? gq.getQuestionType() : QuestionType.MCQ;
        if (actualType != claimedType) {
            throw new IllegalArgumentException(TYPE_MISMATCH_MESSAGE);
        }
        if (actualType == QuestionType.SA) {
            validateShortAnswerRequest(req);
        } else if (actualType == QuestionType.TF) {
            validateTrueFalseRequest(req);
        }
        validateReasonCodesForType(req.reasonCodes(), actualType);
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

        if (actualType == QuestionType.SA) {
            return submitShortAnswer(gq, req, subject, actualPurpose, reviewerId);
        }
        if (actualType == QuestionType.TF) {
            return submitTrueFalse(gq, req, subject, actualPurpose, reviewerId);
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

    /**
     * Dạng câu client đang chấm: questionType nếu có; không có thì là sa khi body có trường sa, còn lại là mcq.
     */
    static QuestionType claimedType(SubmitReviewRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Body đánh giá không được rỗng");
        }
        if (req.questionType() != null) {
            return req.questionType();
        }
        if (req.statements() != null) {
            return QuestionType.TF;
        }
        if (req.reviewerSaKind() != null || req.reviewerSaAnswer() != null || req.finalSaAnswer() != null) {
            return QuestionType.SA;
        }
        return QuestionType.MCQ;
    }

    /**
     * Kiểm tra body chấm câu Trả lời ngắn (bản sửa 1 mục 8, quyết định A và E): đáp số chấm mù chỉ cần phân tích
     * được (LENIENT), đáp số cuối vào ngân hàng phải viết chuẩn (STRICT).
     */
    static void validateShortAnswerRequest(SubmitReviewRequest req) {
        if (req.editedExplanation() != null) {
            throw new IllegalArgumentException(EDITED_EXPLANATION_NOT_ACCEPTED_MESSAGE);
        }
        rejectIfPresent("reviewerAnswer", req.reviewerAnswer());
        rejectIfPresent("finalOption", req.finalOption());
        rejectIfPresent("editedOptionA", req.editedOptionA());
        rejectIfPresent("editedOptionB", req.editedOptionB());
        rejectIfPresent("editedOptionC", req.editedOptionC());
        rejectIfPresent("editedOptionD", req.editedOptionD());
        if (req.formatOk() == null) {
            throw new IllegalArgumentException("Phải chọn đánh giá định dạng (formatOk)");
        }
        if (req.difficultyLabel() == null) {
            throw new IllegalArgumentException("Phải chọn mức độ khó (difficultyLabel)");
        }
        if (req.decision() == null) {
            throw new IllegalArgumentException("Phải chọn kết luận (decision)");
        }
        if (req.reviewerSaKind() == null) {
            throw new IllegalArgumentException("Phải chọn reviewerSaKind (number, ambiguous)");
        }
        boolean hasAnswer = req.reviewerSaAnswer() != null && !req.reviewerSaAnswer().isBlank();
        if (req.reviewerSaKind() == SaAnswerKind.NUMBER) {
            if (!hasAnswer) {
                throw new IllegalArgumentException("Khi chọn 'number', phải nhập reviewerSaAnswer");
            }
            if (!ShortAnswerValue.parse(req.reviewerSaAnswer(), ShortAnswerValue.Mode.LENIENT).ok()) {
                throw new IllegalArgumentException(REVIEWER_SA_UNREADABLE_MESSAGE);
            }
        } else {
            if (hasAnswer) {
                throw new IllegalArgumentException("Khi chọn 'ambiguous', reviewerSaAnswer phải để trống");
            }
            if (req.decision() == ReviewDecision.APPROVED_AS_IS) {
                throw new IllegalArgumentException(
                        "Khi reviewerSaKind là 'ambiguous', không được chọn 'approved_as_is'");
            }
        }

        switch (req.decision()) {
            case REJECTED -> {
                if (req.finalSaAnswer() != null) {
                    throw new IllegalArgumentException("Khi chọn 'rejected', finalSaAnswer phải để trống (null)");
                }
                validateRejectReasons(req);
            }
            case APPROVED_AS_IS -> {
                String reviewer = req.reviewerSaAnswer().trim();
                if (req.finalSaAnswer() != null && !req.finalSaAnswer().trim().equals(reviewer)) {
                    throw new IllegalArgumentException(
                            "Khi chọn approved_as_is, finalSaAnswer phải trùng reviewerSaAnswer");
                }
                requireStrictFinalAnswer(reviewer);
            }
            case APPROVED_WITH_EDIT -> {
                if (req.finalSaAnswer() == null || req.finalSaAnswer().isBlank()) {
                    throw new IllegalArgumentException("Khi chọn approved_with_edit, bắt buộc nhập finalSaAnswer");
                }
                requireStrictFinalAnswer(req.finalSaAnswer());
            }
        }
    }

    private static void rejectIfPresent(String field, Object value) {
        if (value != null) {
            throw new IllegalArgumentException(SA_FIELD_NOT_APPLICABLE.formatted(field));
        }
    }

    /**
     * Quyết định E: đáp số cuối (vào ngân hàng) dùng chế độ STRICT. Có chữ số 0 thừa ở đầu thì thông báo nêu cách
     * sửa, tính từ chính chuỗi giáo viên nhập (quyết định 3 sau GĐ2), không dùng đáp số LLM.
     */
    private static void requireStrictFinalAnswer(String finalAnswer) {
        ShortAnswerValue.ParseResult parsed = ShortAnswerValue.parse(finalAnswer, ShortAnswerValue.Mode.STRICT);
        if (parsed.ok()) {
            return;
        }
        if (ShortAnswerValue.MSG_LEADING_ZERO.equals(parsed.error())) {
            throw new IllegalArgumentException(
                    LEADING_ZERO_HINT_PREFIX + ShortAnswerValue.withoutLeadingZeros(finalAnswer));
        }
        throw new IllegalArgumentException(FINAL_SA_INVALID_PREFIX + parsed.error());
    }

    /** rejected: ít nhất một lý do; mã other có chi tiết (giống mcq). */
    private static void validateRejectReasons(SubmitReviewRequest req) {
        if (req.reasonCodes() == null || req.reasonCodes().isEmpty()) {
            throw new IllegalArgumentException("Khi chọn 'rejected', phải có ít nhất 1 lý do trong reasonCodes");
        }
        if (req.reasonCodes().contains(ReasonCode.OTHER)
                && (req.otherReasonDetail() == null || req.otherReasonDetail().trim().isEmpty())) {
            throw new IllegalArgumentException("Khi chọn lý do 'other', bắt buộc nhập chi tiết (otherReasonDetail)");
        }
    }

    /**
     * Nộp đánh giá câu Trả lời ngắn (V4, GĐ2). Gọi trong transaction của {@link #submitReview}, sau khi đã kiểm
     * request, purpose và người chấm. answer_correct do server tính theo giá trị (LENIENT): ambiguous là FALSE;
     * NULL chỉ khi đáp số LLM đã lưu không còn phân tích được (quyết định A).
     */
    private SubmitReviewResponse submitShortAnswer(
            GeneratedQuestion gq,
            SubmitReviewRequest req,
            Subject subject,
            Purpose actualPurpose,
            Integer reviewerId) {
        SaAnswerKind kind = req.reviewerSaKind();
        ReviewDecision decision = req.decision();
        String reviewerAnswer = kind == SaAnswerKind.NUMBER ? req.reviewerSaAnswer().trim() : null;

        Boolean answerCorrect;
        if (kind == SaAnswerKind.AMBIGUOUS) {
            answerCorrect = Boolean.FALSE;
        } else {
            ShortAnswerValue.ParseResult llm = ShortAnswerValue.parse(gq.getSaAnswer(), ShortAnswerValue.Mode.LENIENT);
            if (llm.ok()) {
                ShortAnswerValue mine = ShortAnswerValue.parse(reviewerAnswer, ShortAnswerValue.Mode.LENIENT).value();
                answerCorrect = mine.sameValue(llm.value());
            } else {
                // Chỉ xảy ra khi bộ phân tích đổi sau khi câu đã qua format; đếm riêng khi phân tích (answer_correct NULL).
                LOG.warn("Đáp số LLM của câu sa id={} không còn phân tích được ({}); answer_correct = NULL",
                        gq.getId(), llm.error());
                answerCorrect = null;
            }
        }

        String finalAnswer = null;
        boolean edited = false;
        Integer editDistance = null;
        String savedEditedQ = null;
        if (decision == ReviewDecision.APPROVED_AS_IS) {
            finalAnswer = reviewerAnswer;
        } else if (decision == ReviewDecision.APPROVED_WITH_EDIT) {
            finalAnswer = req.finalSaAnswer().trim();
            String effQ = fallback(req.editedQuestionText(), gq.getQuestionText());
            if (effQ.isBlank()) {
                throw new IllegalArgumentException("Nội dung câu hỏi sau khi sửa không được để trống");
            }
            // edit_distance của câu sa chỉ tính trên đề; đổi đáp số không tính (như đổi đáp án mcq).
            int dist = TextDistanceUtils.levenshteinDistance(gq.getQuestionText(), effQ);
            if (dist > 0) {
                edited = true;
                editDistance = dist;
                savedEditedQ = effQ;
            }
        }
        if (finalAnswer != null && GenerationJobProcessor.isMathSubject(subject.getName())) {
            List<String> latexErrors = new ArrayList<>();
            if (savedEditedQ != null) {
                appendFieldErrors("question_text", savedEditedQ, latexErrors);
            }
            appendFieldErrors("final_sa_answer", finalAnswer, latexErrors);
            if (!latexErrors.isEmpty()) {
                throw new IllegalArgumentException(
                        "Bản sửa còn lỗi định dạng công thức LaTeX: " + String.join("; ", latexErrors));
            }
        }

        TeacherReview review = teacherReviewRepository
                .findByQuestionIdAndReviewerId(gq.getId(), reviewerId)
                .orElseGet(TeacherReview::new);
        review.setQuestionType(QuestionType.SA);
        review.setQuestionId(gq.getId());
        review.setReviewerId(reviewerId);
        review.setReviewPurpose(actualPurpose);
        review.setReviewerAnswer(null);
        review.setReviewerSaKind(kind);
        review.setReviewerSaAnswer(reviewerAnswer);
        review.setAnswerCorrect(answerCorrect);
        review.setFormatOk(req.formatOk());
        review.setDifficultyLabel(req.difficultyLabel());
        review.setDecision(decision);
        review.setFinalOption(null);
        review.setFinalSaAnswer(finalAnswer);
        review.setEdited(edited);
        review.setEditedQuestionText(savedEditedQ);
        review.setEditedOptionA(null);
        review.setEditedOptionB(null);
        review.setEditedOptionC(null);
        review.setEditedOptionD(null);
        review.setEditedExplanation(null);
        review.setEditDistance(editDistance);
        review.setReviewedAt(LocalDateTime.now());
        TeacherReview savedReview = teacherReviewRepository.save(review);
        saveReasons(savedReview.getId(), req);

        Integer bankItemId = null;
        QuestionStatus resultingQuestionStatus = null;
        if (actualPurpose == Purpose.PRODUCTION) {
            resultingQuestionStatus = gq.getStatus();
            if (decision == ReviewDecision.APPROVED_AS_IS || decision == ReviewDecision.APPROVED_WITH_EDIT) {
                if (gq.getStatus() != QuestionStatus.QC_PASSED && gq.getStatus() != QuestionStatus.IN_BANK) {
                    throw new IllegalArgumentException(NOT_READY_FOR_BANK_MESSAGE);
                }
                GeneratedQuestion updatedGq = generationService.updateStatusAndDifficulty(
                        gq.getId(), QuestionStatus.IN_BANK, req.difficultyLabel());
                resultingQuestionStatus = updatedGq.getStatus();
                BankItem item = bankService.createOrUpdateShortAnswerFromReview(
                        gq.getId(),
                        savedReview.getId(),
                        edited ? savedEditedQ : gq.getQuestionText(),
                        finalAnswer,
                        explanationForBank(subject.getName(), gq.getExplanation()),
                        req.difficultyLabel());
                bankItemId = item.getId();
            } else if (decision == ReviewDecision.REJECTED) {
                GeneratedQuestion updatedGq = generationService.updateStatusAndDifficulty(
                        gq.getId(), QuestionStatus.REJECTED, req.difficultyLabel());
                resultingQuestionStatus = updatedGq.getStatus();
                bankService.removeBankItemIfUnused(gq.getId());
            }
        }

        return new SubmitReviewResponse(
                savedReview.getId(),
                gq.getId(),
                actualPurpose,
                null,
                req.formatOk(),
                req.difficultyLabel(),
                decision,
                null,
                edited,
                editDistance,
                resultingQuestionStatus,
                bankItemId,
                savedReview.getReviewedAt(),
                kind,
                reviewerAnswer,
                finalAnswer
        );
    }

    /** Ghi lại lý do từ chối của một lần nộp (xóa lý do cũ của review đó trước). */
    private void saveReasons(Integer reviewId, SubmitReviewRequest req) {
        teacherReviewReasonRepository.deleteByReviewId(reviewId);
        if (req.decision() == ReviewDecision.REJECTED && req.reasonCodes() != null) {
            for (ReasonCode code : new LinkedHashSet<>(req.reasonCodes())) {
                String detail = (code == ReasonCode.OTHER) ? req.otherReasonDetail().trim() : null;
                teacherReviewReasonRepository.save(new TeacherReviewReason(reviewId, code, detail));
            }
        }
    }

    /**
     * Số ý (0-4) giáo viên chọn khớp nhãn LLM trong một lần chấm câu tf (GĐ3). Chỉ số cấp câu suy ra từ
     * teacher_review_statement.label_correct, không lưu riêng. Dùng cho phân tích, không trả về form chấm.
     */
    @Transactional(readOnly = true)
    public long countMatchedStatements(Integer reviewId) {
        return teacherReviewStatementRepository.countByReviewIdAndLabelCorrectTrue(reviewId);
    }

    static final String TF_FIELD_NOT_APPLICABLE = "Trường %s không áp dụng cho câu đúng/sai.";
    static final String TF_NEED_FOUR_MESSAGE = "Phải gửi đủ 4 ý a, b, c, d (mỗi ý một lần)";

    /**
     * Kiểm tra body chấm câu Đúng/Sai (GĐ3): đủ 4 ý a-d, mỗi ý có reviewerLabel (T, F, undecidable) và difficultyLabel;
     * có undecidable thì không được approved_as_is; approved_as_is: finalLabel bỏ trống hoặc trùng reviewerLabel;
     * approved_with_edit: bắt buộc finalLabel cho cả 4 ý; rejected: finalLabel phải trống và có lý do.
     * Các thông điệp chỉ nói về dữ liệu giáo viên gửi.
     */
    static void validateTrueFalseRequest(SubmitReviewRequest req) {
        if (req.editedExplanation() != null) {
            throw new IllegalArgumentException(EDITED_EXPLANATION_NOT_ACCEPTED_MESSAGE);
        }
        rejectIfPresentTf("reviewerAnswer", req.reviewerAnswer());
        rejectIfPresentTf("finalOption", req.finalOption());
        rejectIfPresentTf("difficultyLabel", req.difficultyLabel());
        rejectIfPresentTf("editedOptionA", req.editedOptionA());
        rejectIfPresentTf("editedOptionB", req.editedOptionB());
        rejectIfPresentTf("editedOptionC", req.editedOptionC());
        rejectIfPresentTf("editedOptionD", req.editedOptionD());
        rejectIfPresentTf("reviewerSaKind", req.reviewerSaKind());
        rejectIfPresentTf("reviewerSaAnswer", req.reviewerSaAnswer());
        rejectIfPresentTf("finalSaAnswer", req.finalSaAnswer());
        if (req.formatOk() == null) {
            throw new IllegalArgumentException("Phải chọn đánh giá định dạng (formatOk)");
        }
        if (req.decision() == null) {
            throw new IllegalArgumentException("Phải chọn kết luận (decision)");
        }
        List<StatementReviewInput> statements = req.statements();
        if (statements == null || statements.size() != 4
                || statements.stream().anyMatch(s -> s == null || s.key() == null)
                || statements.stream().map(StatementReviewInput::key).distinct().count() != 4) {
            throw new IllegalArgumentException(TF_NEED_FOUR_MESSAGE);
        }
        for (StatementReviewInput s : orderedStatements(statements)) {
            if (s.reviewerLabel() == null) {
                throw new IllegalArgumentException(
                        "Ý " + s.key().getDbValue() + ": phải chọn reviewerLabel (T, F, undecidable)");
            }
            if (s.difficultyLabel() == null) {
                throw new IllegalArgumentException("Ý " + s.key().getDbValue() + ": phải chọn difficultyLabel");
            }
        }
        List<StatementReviewInput> ordered = orderedStatements(statements);
        switch (req.decision()) {
            case APPROVED_AS_IS -> {
                if (ordered.stream().anyMatch(s -> s.reviewerLabel() == TruthChoice.UNDECIDABLE)) {
                    throw new IllegalArgumentException(
                            "Khi có ý là 'undecidable', không được chọn 'approved_as_is'");
                }
                for (StatementReviewInput s : ordered) {
                    if (s.finalLabel() != null && !s.reviewerLabel().matches(s.finalLabel())) {
                        throw new IllegalArgumentException("Khi chọn approved_as_is, finalLabel của ý "
                                + s.key().getDbValue() + " phải trùng reviewerLabel");
                    }
                }
            }
            case APPROVED_WITH_EDIT -> {
                for (StatementReviewInput s : ordered) {
                    if (s.finalLabel() == null) {
                        throw new IllegalArgumentException("Khi chọn approved_with_edit, bắt buộc chọn finalLabel cho ý "
                                + s.key().getDbValue());
                    }
                }
            }
            case REJECTED -> {
                if (ordered.stream().anyMatch(s -> s.finalLabel() != null)) {
                    throw new IllegalArgumentException("Khi chọn 'rejected', finalLabel của các ý phải để trống");
                }
                validateRejectReasons(req);
            }
        }
    }

    private static void rejectIfPresentTf(String field, Object value) {
        if (value != null) {
            throw new IllegalArgumentException(TF_FIELD_NOT_APPLICABLE.formatted(field));
        }
    }

    /** 4 ý theo thứ tự a-d (đã kiểm đủ 4 khóa khác nhau). */
    private static List<StatementReviewInput> orderedStatements(List<StatementReviewInput> statements) {
        return statements.stream()
                .sorted(java.util.Comparator.comparing(StatementReviewInput::key))
                .toList();
    }

    /**
     * Nộp đánh giá câu Đúng/Sai (GĐ3). Gọi trong transaction của {@link #submitReview}, sau khi đã kiểm request,
     * purpose và người chấm. label_correct của từng ý do server tính (reviewerLabel khớp nhãn LLM; undecidable luôn
     * FALSE); teacher_review.answer_correct và difficulty_label để NULL. Duyệt production thì chép vào ngân hàng với
     * 4 ý và cập nhật final_difficulty của từng ý; mọi lỗi làm rollback toàn bộ.
     */
    private SubmitReviewResponse submitTrueFalse(
            GeneratedQuestion gq,
            SubmitReviewRequest req,
            Subject subject,
            Purpose actualPurpose,
            Integer reviewerId) {
        ReviewDecision decision = req.decision();
        List<StatementReviewInput> inputs = orderedStatements(req.statements());
        List<GeneratedQuestionStatement> originals = generationService.getStatements(gq.getId());
        if (originals.size() != 4) {
            throw new IllegalArgumentException(NOT_READY_FOR_BANK_MESSAGE);
        }

        List<TruthValue> finalLabels = new ArrayList<>(4);
        for (StatementReviewInput s : inputs) {
            finalLabels.add(switch (decision) {
                case APPROVED_AS_IS -> s.reviewerLabel() == TruthChoice.TRUE ? TruthValue.TRUE : TruthValue.FALSE;
                case APPROVED_WITH_EDIT -> s.finalLabel();
                case REJECTED -> null;
            });
        }

        boolean edited = false;
        Integer editDistance = null;
        String savedEditedQ = null;
        List<String> effectiveStatements = new ArrayList<>(4);
        for (GeneratedQuestionStatement o : originals) {
            effectiveStatements.add(o.getStatementText());
        }
        List<String> savedEditedStatements = null;
        if (decision == ReviewDecision.APPROVED_WITH_EDIT) {
            String effQ = fallback(req.editedQuestionText(), gq.getQuestionText());
            List<String> effS = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                effS.add(fallback(inputs.get(i).editedText(), originals.get(i).getStatementText()));
            }
            if (effQ.isBlank() || effS.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("Nội dung đề dẫn và 4 ý sau khi sửa không được để trống");
            }
            if (GenerationJobProcessor.isMathSubject(subject.getName())) {
                List<String> latexErrors = new ArrayList<>();
                appendFieldErrors("question_text", effQ, latexErrors);
                for (int i = 0; i < 4; i++) {
                    appendFieldErrors("statement_" + inputs.get(i).key().getDbValue(), effS.get(i), latexErrors);
                }
                if (!latexErrors.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Bản sửa còn lỗi định dạng công thức LaTeX: " + String.join("; ", latexErrors));
                }
            }
            // edit_distance câu tf: Levenshtein trên đề dẫn + 4 ý; đổi nhãn không tính.
            int dist = TextDistanceUtils.levenshteinDistance(gq.getQuestionText(), effQ);
            for (int i = 0; i < 4; i++) {
                dist += TextDistanceUtils.levenshteinDistance(originals.get(i).getStatementText(), effS.get(i));
            }
            if (dist > 0) {
                edited = true;
                editDistance = dist;
                savedEditedQ = effQ;
                savedEditedStatements = effS;
                effectiveStatements = effS;
            }
        }

        TeacherReview review = teacherReviewRepository
                .findByQuestionIdAndReviewerId(gq.getId(), reviewerId)
                .orElseGet(TeacherReview::new);
        review.setQuestionType(QuestionType.TF);
        review.setQuestionId(gq.getId());
        review.setReviewerId(reviewerId);
        review.setReviewPurpose(actualPurpose);
        review.setReviewerAnswer(null);
        review.setReviewerSaKind(null);
        review.setReviewerSaAnswer(null);
        review.setAnswerCorrect(null);
        review.setFormatOk(req.formatOk());
        review.setDifficultyLabel(null);
        review.setDecision(decision);
        review.setFinalOption(null);
        review.setFinalSaAnswer(null);
        review.setEdited(edited);
        review.setEditedQuestionText(savedEditedQ);
        review.setEditedOptionA(null);
        review.setEditedOptionB(null);
        review.setEditedOptionC(null);
        review.setEditedOptionD(null);
        review.setEditedExplanation(null);
        review.setEditDistance(editDistance);
        review.setReviewedAt(LocalDateTime.now());
        TeacherReview savedReview = teacherReviewRepository.saveAndFlush(review);
        Integer reviewId = savedReview.getId();
        LocalDateTime reviewedAt = savedReview.getReviewedAt();

        teacherReviewStatementRepository.deleteByReviewId(reviewId);
        List<StatementReviewResult> results = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            StatementReviewInput in = inputs.get(i);
            TeacherReviewStatement row = new TeacherReviewStatement(reviewId, in.key());
            row.setReviewerLabel(in.reviewerLabel());
            row.setDifficultyLabel(in.difficultyLabel());
            row.setLabelCorrect(in.reviewerLabel().matches(originals.get(i).getLlmLabel()));
            row.setFinalLabel(finalLabels.get(i));
            row.setEditedStatementText(savedEditedStatements != null ? savedEditedStatements.get(i) : null);
            teacherReviewStatementRepository.save(row);
            results.add(new StatementReviewResult(in.key(), in.reviewerLabel(), in.difficultyLabel(), finalLabels.get(i)));
        }
        teacherReviewStatementRepository.flush();
        saveReasons(reviewId, req);

        Integer bankItemId = null;
        QuestionStatus resultingQuestionStatus = null;
        List<Difficulty> levels = inputs.stream().map(StatementReviewInput::difficultyLabel).toList();
        if (actualPurpose == Purpose.PRODUCTION) {
            resultingQuestionStatus = gq.getStatus();
            if (decision == ReviewDecision.APPROVED_AS_IS || decision == ReviewDecision.APPROVED_WITH_EDIT) {
                if (gq.getStatus() != QuestionStatus.QC_PASSED && gq.getStatus() != QuestionStatus.IN_BANK) {
                    throw new IllegalArgumentException(NOT_READY_FOR_BANK_MESSAGE);
                }
                // Mức độ cấp câu của tf để NULL; mức độ cuối ghi theo từng ý.
                GeneratedQuestion updatedGq = generationService.updateStatusAndDifficulty(
                        gq.getId(), QuestionStatus.IN_BANK, null);
                generationService.updateStatementFinalDifficulties(gq.getId(), levels);
                resultingQuestionStatus = updatedGq.getStatus();
                List<BankStatementInput> bankStatements = new ArrayList<>(4);
                for (int i = 0; i < 4; i++) {
                    bankStatements.add(new BankStatementInput(inputs.get(i).key(), effectiveStatements.get(i),
                            finalLabels.get(i), levels.get(i)));
                }
                BankItem item = bankService.createOrUpdateTrueFalseFromReview(
                        gq.getId(),
                        reviewId,
                        edited ? savedEditedQ : gq.getQuestionText(),
                        explanationForBank(subject.getName(), gq.getExplanation()),
                        bankStatements);
                bankItemId = item.getId();
            } else if (decision == ReviewDecision.REJECTED) {
                GeneratedQuestion updatedGq = generationService.updateStatusAndDifficulty(
                        gq.getId(), QuestionStatus.REJECTED, null);
                resultingQuestionStatus = updatedGq.getStatus();
                bankService.removeBankItemIfUnused(gq.getId());
            }
        }

        return new SubmitReviewResponse(
                reviewId,
                gq.getId(),
                actualPurpose,
                null,
                req.formatOk(),
                null,
                decision,
                null,
                edited,
                editDistance,
                resultingQuestionStatus,
                bankItemId,
                reviewedAt,
                null,
                null,
                null,
                results
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

