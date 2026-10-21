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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TeacherReviewService {

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

    @Transactional(readOnly = true)
    public List<ReviewQuestionListItemDto> listQuestionsForReview(QuestionStatus status) {
        List<GeneratedQuestion> questions = generationService.listQuestions(null);
        List<ReviewQuestionListItemDto> result = new ArrayList<>();
        for (GeneratedQuestion gq : questions) {
            QuestionTemplate tpl = catalogService.getTemplateById(gq.getTemplateId());
            Subject subject = catalogService.getSubjectById(tpl.getSubjectId());
            ExperimentRun run = experimentService.getById(gq.getRunId());
            boolean isExperiment = run.getPurpose() == Purpose.EXPERIMENT;

            // Câu production được lọc theo status; câu experiment luôn được giữ nguyên tập câu độc lập với trạng thái QC
            if (!isExperiment && status != null && gq.getStatus() != status) {
                continue;
            }

            Boolean effLatexValid = null;
            QuestionStatus effStatus = null;
            if (!isExperiment) {
                effStatus = gq.getStatus();
                List<String> latexErrors = collectLatexErrors(
                        subject.getName(),
                        gq.getQuestionText(),
                        gq.getOptionA(),
                        gq.getOptionB(),
                        gq.getOptionC(),
                        gq.getOptionD(),
                        gq.getExplanation()
                );
                effLatexValid = latexErrors.isEmpty();
            }

            result.add(new ReviewQuestionListItemDto(
                    gq.getId(),
                    gq.getTemplateId(),
                    run.getPurpose(),
                    subject.getName(),
                    tpl.getGradeLevel(),
                    tpl.getTopic(),
                    gq.getQuestionText(),
                    effStatus,
                    effLatexValid,
                    gq.getCreatedAt()
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BlindQuestionReviewDto getBlindQuestionForReview(Integer questionId) {
        GeneratedQuestion gq = generationService.getQuestionById(questionId);
        QuestionTemplate tpl = catalogService.getTemplateById(gq.getTemplateId());
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());
        ExperimentRun run = experimentService.getById(gq.getRunId());

        boolean isExperiment = run.getPurpose() == Purpose.EXPERIMENT;

        QuestionStatus effStatus = null;
        Boolean effLatexValid = null;
        List<String> effLatexErrors = null;

        if (!isExperiment) {
            effStatus = gq.getStatus();
            effLatexErrors = collectLatexErrors(
                    subject.getName(),
                    gq.getQuestionText(),
                    gq.getOptionA(),
                    gq.getOptionB(),
                    gq.getOptionC(),
                    gq.getOptionD(),
                    gq.getExplanation()
            );
            effLatexValid = effLatexErrors.isEmpty();
        }

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
                effStatus,
                effLatexValid,
                effLatexErrors,
                gq.getCreatedAt()
        );
    }

    @Transactional
    public SubmitReviewResponse submitReview(Integer questionId, SubmitReviewRequest req) {
        validateSubmitRequest(req);

        GeneratedQuestion gq = generationService.getQuestionById(questionId);
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
        String savedEditedExp = null;

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
            String effExp = fallback(req.editedExplanation(), gq.getExplanation());

            if (effQ.isBlank() || effA.isBlank() || effB.isBlank() || effC.isBlank() || effD.isBlank()) {
                throw new IllegalArgumentException("Nội dung câu hỏi và 4 phương án A-D sau khi sửa không được để trống");
            }
            List<String> editedLatexErrors = collectLatexErrors(
                    subject.getName(), effQ, effA, effB, effC, effD, effExp);
            if (!editedLatexErrors.isEmpty()) {
                throw new IllegalArgumentException(
                        "Bản sửa còn lỗi định dạng công thức LaTeX: " + String.join("; ", editedLatexErrors));
            }

            int dist = TextDistanceUtils.totalQuestionEditDistance(
                    gq.getQuestionText(), gq.getOptionA(), gq.getOptionB(), gq.getOptionC(), gq.getOptionD(), gq.getExplanation(),
                    effQ, effA, effB, effC, effD, effExp
            );
            if (dist > 0) {
                edited = true;
                editDistance = dist;
                savedEditedQ = effQ;
                savedEditedA = effA;
                savedEditedB = effB;
                savedEditedC = effC;
                savedEditedD = effD;
                savedEditedExp = effExp;
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
        review.setEditedExplanation(savedEditedExp);
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
                // Chỉ câu đã qua kiểm tra chất lượng tự động (hoặc đang trong ngân hàng) mới được duyệt vào ngân hàng
                if (gq.getStatus() != QuestionStatus.QC_PASSED && gq.getStatus() != QuestionStatus.IN_BANK) {
                    throw new IllegalArgumentException(
                            "Câu hỏi chưa đạt yêu cầu kiểm tra chất lượng tự động (trạng thái: "
                            + gq.getStatus().getDbValue() + "), không thể duyệt vào ngân hàng câu hỏi.");
                }

                String finalQ = edited ? savedEditedQ : gq.getQuestionText();
                String finalA = edited ? savedEditedA : gq.getOptionA();
                String finalB = edited ? savedEditedB : gq.getOptionB();
                String finalC = edited ? savedEditedC : gq.getOptionC();
                String finalD = edited ? savedEditedD : gq.getOptionD();
                String finalExp = edited ? savedEditedExp : gq.getExplanation();

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

    private void appendFieldErrors(String field, String text, List<String> errors) {
        for (String err : LatexFormatValidator.validate(text)) {
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

