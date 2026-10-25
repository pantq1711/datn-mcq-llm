package com.datn.mcq.review.dto;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.ReviewDecision;
import com.datn.mcq.enums.SaAnswerKind;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phản hồi sau khi nộp đánh giá câu hỏi.
 * Tuyệt đối không trả về answerCorrect theo hợp đồng V1 dòng 137:
 * "Không báo giáo viên biết đáp án họ chọn trùng hay lệch đáp án LLM, trước hay sau khi nộp."
 *
 * <p>V4 (câu sa): chỉ lặp lại đáp số do chính giáo viên nhập (reviewerSaKind, reviewerSaAnswer, finalSaAnswer);
 * bỏ khỏi JSON khi null nên phản hồi của câu mcq không đổi. Không có đáp số LLM.
 *
 * <p>GĐ3 (câu tf): statements lặp lại nhãn, mức độ và nhãn cuối do giáo viên nhập; không có label_correct.
 */
public record SubmitReviewResponse(
        Integer reviewId,
        Integer questionId,
        Purpose reviewPurpose,
        AnswerChoice reviewerAnswer,
        boolean formatOk,
        Difficulty difficultyLabel,
        ReviewDecision decision,
        Option finalOption,
        boolean edited,
        Integer editDistance,
        // Null với experiment và bị loại khỏi JSON, để không lộ kết quả QC (M-7).
        @JsonInclude(JsonInclude.Include.NON_NULL) QuestionStatus questionStatus,
        Integer bankItemId,
        LocalDateTime reviewedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) SaAnswerKind reviewerSaKind,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reviewerSaAnswer,
        @JsonInclude(JsonInclude.Include.NON_NULL) String finalSaAnswer,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<StatementReviewResult> statements
) {
    /** Phản hồi có trường SA (chữ ký GĐ2): không có ý TF. */
    public SubmitReviewResponse(
            Integer reviewId,
            Integer questionId,
            Purpose reviewPurpose,
            AnswerChoice reviewerAnswer,
            boolean formatOk,
            Difficulty difficultyLabel,
            ReviewDecision decision,
            Option finalOption,
            boolean edited,
            Integer editDistance,
            QuestionStatus questionStatus,
            Integer bankItemId,
            LocalDateTime reviewedAt,
            SaAnswerKind reviewerSaKind,
            String reviewerSaAnswer,
            String finalSaAnswer) {
        this(reviewId, questionId, reviewPurpose, reviewerAnswer, formatOk, difficultyLabel, decision, finalOption,
                edited, editDistance, questionStatus, bankItemId, reviewedAt, reviewerSaKind, reviewerSaAnswer,
                finalSaAnswer, null);
    }

    /** Phản hồi của câu mcq (chữ ký trước V4). */
    public SubmitReviewResponse(
            Integer reviewId,
            Integer questionId,
            Purpose reviewPurpose,
            AnswerChoice reviewerAnswer,
            boolean formatOk,
            Difficulty difficultyLabel,
            ReviewDecision decision,
            Option finalOption,
            boolean edited,
            Integer editDistance,
            QuestionStatus questionStatus,
            Integer bankItemId,
            LocalDateTime reviewedAt) {
        this(reviewId, questionId, reviewPurpose, reviewerAnswer, formatOk, difficultyLabel, decision, finalOption,
                edited, editDistance, questionStatus, bankItemId, reviewedAt, null, null, null, null);
    }
}
