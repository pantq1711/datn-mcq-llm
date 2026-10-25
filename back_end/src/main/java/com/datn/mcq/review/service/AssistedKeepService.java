package com.datn.mcq.review.service;

import com.datn.mcq.bank.dto.BankStatementInput;
import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.ReviewDecision;
import com.datn.mcq.enums.SaAnswerKind;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.experiment.service.ExperimentService;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.review.entity.TeacherReview;
import com.datn.mcq.review.entity.TeacherReviewStatement;
import com.datn.mcq.review.repository.TeacherReviewRepository;
import com.datn.mcq.review.repository.TeacherReviewStatementRepository;
import com.datn.mcq.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Giữ câu ở chế độ giáo viên (V7, phần C mục 4; quyết định 22/10 và 25/10 ở docs/ai-development-rules.md). Đây là phần
 * còn lại của TeacherReviewService sau khi gỡ luồng chấm mù (25/10).
 */
@Service
public class AssistedKeepService {

    static final String KEEP_NOT_ALLOWED_MESSAGE = "Câu hỏi này không thể đưa vào ngân hàng.";

    private final TeacherReviewRepository teacherReviewRepository;
    private final GenerationService generationService;
    private final CatalogService catalogService;
    private final BankService bankService;
    private final UserService userService;
    private final ExperimentService experimentService;
    private final TeacherReviewStatementRepository teacherReviewStatementRepository;

    public AssistedKeepService(
            TeacherReviewRepository teacherReviewRepository,
            GenerationService generationService,
            CatalogService catalogService,
            BankService bankService,
            UserService userService,
            ExperimentService experimentService,
            TeacherReviewStatementRepository teacherReviewStatementRepository) {
        this.teacherReviewRepository = teacherReviewRepository;
        this.generationService = generationService;
        this.catalogService = catalogService;
        this.bankService = bankService;
        this.userService = userService;
        this.experimentService = experimentService;
        this.teacherReviewStatementRepository = teacherReviewStatementRepository;
    }

    /**
     * Giữ câu ở chế độ giáo viên: ghi teacher_review production với review_mode = 'assisted', quyết định approved_as_is
     * với chính đáp án/đáp số/nhãn của LLM (giáo viên đã thấy và chấp nhận), rồi chép vào bank_item trong cùng
     * transaction (M-9).
     *
     * <p>Không đòi câu qc_passed, vì câu lệch Solver được đưa vào đề kèm cờ "cần xem kỹ" và giáo viên Giữ sau khi đã thấy
     * cờ. Không nhận câu duplicate, format_failed, rejected hoặc câu thuộc run experiment. Đánh giá assisted bị loại
     * khỏi các chỉ số chấm mù cũ.
     *
     * @return id của bank_item
     */
    @Transactional
    public Integer keepAssisted(Integer questionId, Integer reviewerId) {
        GeneratedQuestion gq = generationService.getQuestionById(questionId);
        if (experimentService.getById(gq.getRunId()).getPurpose() != Purpose.PRODUCTION
                || (gq.getStatus() != QuestionStatus.QC_PASSED && gq.getStatus() != QuestionStatus.GENERATED
                    && gq.getStatus() != QuestionStatus.IN_BANK)) {
            throw new IllegalArgumentException(KEEP_NOT_ALLOWED_MESSAGE);
        }
        // W1-1: người Giữ là người đăng nhập (chủ đề), không còn dùng giáo viên mặc định.
        if (reviewerId == null) {
            throw new IllegalArgumentException("Thiếu người Giữ câu");
        }
        Integer teacherId = userService.getById(reviewerId).getId();
        QuestionTemplate tpl = catalogService.getTemplateById(gq.getTemplateId());
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());
        String explanation = explanationForBank(subject.getName(), gq.getExplanation());
        QuestionType type = gq.getQuestionType() != null ? gq.getQuestionType() : QuestionType.MCQ;

        TeacherReview review = teacherReviewRepository.findByQuestionIdAndReviewerId(gq.getId(), teacherId)
                .orElseGet(TeacherReview::new);
        review.setQuestionType(type);
        review.setQuestionId(gq.getId());
        review.setReviewerId(teacherId);
        review.setReviewPurpose(Purpose.PRODUCTION);
        review.setReviewMode(TeacherReview.MODE_ASSISTED);
        review.setFormatOk(Boolean.TRUE);
        review.setDecision(ReviewDecision.APPROVED_AS_IS);
        review.setEdited(false);
        review.setEditedQuestionText(null);
        review.setEditedOptionA(null);
        review.setEditedOptionB(null);
        review.setEditedOptionC(null);
        review.setEditedOptionD(null);
        review.setEditedExplanation(null);
        review.setEditDistance(null);
        review.setReviewedAt(LocalDateTime.now());

        switch (type) {
            case MCQ -> {
                if (gq.getCorrectOption() == null || gq.getLlmDifficulty() == null) {
                    throw new IllegalArgumentException(KEEP_NOT_ALLOWED_MESSAGE);
                }
                review.setReviewerAnswer(DbEnum.fromDbValue(AnswerChoice.class, gq.getCorrectOption().getDbValue()));
                review.setAnswerCorrect(Boolean.TRUE);
                review.setDifficultyLabel(gq.getLlmDifficulty());
                review.setFinalOption(gq.getCorrectOption());
                review.setReviewerSaKind(null);
                review.setReviewerSaAnswer(null);
                review.setFinalSaAnswer(null);
                TeacherReview saved = teacherReviewRepository.saveAndFlush(review);
                generationService.updateStatusAndDifficulty(gq.getId(), QuestionStatus.IN_BANK, gq.getLlmDifficulty());
                return bankService.createOrUpdateFromReview(gq.getId(), saved.getId(), gq.getQuestionText(),
                        gq.getOptionA(), gq.getOptionB(), gq.getOptionC(), gq.getOptionD(), gq.getCorrectOption(),
                        explanation, gq.getLlmDifficulty()).getId();
            }
            case SA -> {
                if (gq.getSaAnswer() == null || gq.getLlmDifficulty() == null) {
                    throw new IllegalArgumentException(KEEP_NOT_ALLOWED_MESSAGE);
                }
                review.setReviewerAnswer(null);
                review.setFinalOption(null);
                review.setReviewerSaKind(SaAnswerKind.NUMBER);
                review.setReviewerSaAnswer(gq.getSaAnswer());
                review.setFinalSaAnswer(gq.getSaAnswer());
                review.setAnswerCorrect(Boolean.TRUE);
                review.setDifficultyLabel(gq.getLlmDifficulty());
                TeacherReview saved = teacherReviewRepository.saveAndFlush(review);
                generationService.updateStatusAndDifficulty(gq.getId(), QuestionStatus.IN_BANK, gq.getLlmDifficulty());
                return bankService.createOrUpdateShortAnswerFromReview(gq.getId(), saved.getId(), gq.getQuestionText(),
                        gq.getSaAnswer(), explanation, gq.getLlmDifficulty()).getId();
            }
            case TF -> {
                List<GeneratedQuestionStatement> statements = generationService.getStatements(gq.getId());
                if (statements.size() != 4 || statements.stream()
                        .anyMatch(s -> s.getLlmLabel() == null || s.getLlmDifficulty() == null)) {
                    throw new IllegalArgumentException(KEEP_NOT_ALLOWED_MESSAGE);
                }
                review.setReviewerAnswer(null);
                review.setFinalOption(null);
                review.setReviewerSaKind(null);
                review.setReviewerSaAnswer(null);
                review.setFinalSaAnswer(null);
                review.setAnswerCorrect(null);
                review.setDifficultyLabel(null);
                TeacherReview saved = teacherReviewRepository.saveAndFlush(review);
                teacherReviewStatementRepository.deleteByReviewId(saved.getId());
                List<BankStatementInput> bankStatements = new ArrayList<>(4);
                List<Difficulty> levels = new ArrayList<>(4);
                for (GeneratedQuestionStatement s : statements) {
                    TeacherReviewStatement row = new TeacherReviewStatement(saved.getId(), s.getStatementKey());
                    row.setReviewerLabel(s.getLlmLabel() == TruthValue.TRUE ? TruthChoice.TRUE : TruthChoice.FALSE);
                    row.setDifficultyLabel(s.getLlmDifficulty());
                    row.setLabelCorrect(Boolean.TRUE);
                    row.setFinalLabel(s.getLlmLabel());
                    teacherReviewStatementRepository.save(row);
                    bankStatements.add(new BankStatementInput(s.getStatementKey(), s.getStatementText(),
                            s.getLlmLabel(), s.getLlmDifficulty()));
                    levels.add(s.getLlmDifficulty());
                }
                teacherReviewStatementRepository.flush();
                generationService.updateStatusAndDifficulty(gq.getId(), QuestionStatus.IN_BANK, null);
                generationService.updateStatementFinalDifficulties(gq.getId(), levels);
                return bankService.createOrUpdateTrueFalseFromReview(gq.getId(), saved.getId(), gq.getQuestionText(),
                        explanation, bankStatements).getId();
            }
        }
        throw new IllegalStateException("Dạng câu không hỗ trợ: " + type);
    }

    /**
     * Lời giải chép vào bank_item: luôn giữ nguyên lời giải LLM (đợt A bổ sung 4, ngày 23/10). Lỗi LaTeX trong lời giải
     * chỉ là cảnh báo ở bước format, không làm mất lời giải. Thay quy tắc việc 2 ngày 22/10 (lưu NULL khi lời giải còn
     * lỗi LaTeX nghiêm trọng), là nguyên nhân câu Giữ ở đề 2 chỗ 20 mất lời giải.
     */
    static String explanationForBank(String subjectName, String explanation) {
        return explanation;
    }
}
