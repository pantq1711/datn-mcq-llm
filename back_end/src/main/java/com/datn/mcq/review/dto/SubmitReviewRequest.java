package com.datn.mcq.review.dto;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.ReasonCode;
import com.datn.mcq.enums.ReviewDecision;
import com.datn.mcq.enums.SaAnswerKind;

import java.util.List;

/**
 * Body nộp đánh giá. Các trường V4 đều không bắt buộc với câu mcq (client mcq cũ không phải đổi).
 *
 * @param questionType     dạng câu client đang chấm (tùy chọn); khác dạng thật của câu thì 400
 * @param reviewerSaKind   (sa) number hoặc ambiguous, nhập mù trước khi thấy đáp số LLM
 * @param reviewerSaAnswer (sa) đáp số giáo viên nhập khi chọn number; chỉ cần phân tích được (LENIENT)
 * @param finalSaAnswer    (sa) đáp số cuối khi duyệt; viết chuẩn (STRICT). approved_as_is: bỏ trống hoặc trùng
 *                         reviewerSaAnswer
 * @param statements       (tf, GĐ3) đánh giá đủ 4 ý a-d; xem {@link StatementReviewInput}
 */
public record SubmitReviewRequest(
        Integer reviewerId,
        Purpose reviewPurpose,
        AnswerChoice reviewerAnswer,
        Boolean formatOk,
        Difficulty difficultyLabel,
        ReviewDecision decision,
        Option finalOption,
        String editedQuestionText,
        String editedOptionA,
        String editedOptionB,
        String editedOptionC,
        String editedOptionD,
        String editedExplanation,
        List<ReasonCode> reasonCodes,
        String otherReasonDetail,
        QuestionType questionType,
        SaAnswerKind reviewerSaKind,
        String reviewerSaAnswer,
        String finalSaAnswer,
        List<StatementReviewInput> statements
) {
    /** Chữ ký trước V4 (câu mcq). */
    public SubmitReviewRequest(
            Integer reviewerId,
            Purpose reviewPurpose,
            AnswerChoice reviewerAnswer,
            Boolean formatOk,
            Difficulty difficultyLabel,
            ReviewDecision decision,
            Option finalOption,
            String editedQuestionText,
            String editedOptionA,
            String editedOptionB,
            String editedOptionC,
            String editedOptionD,
            String editedExplanation,
            List<ReasonCode> reasonCodes,
            String otherReasonDetail) {
        this(reviewerId, reviewPurpose, reviewerAnswer, formatOk, difficultyLabel, decision, finalOption,
                editedQuestionText, editedOptionA, editedOptionB, editedOptionC, editedOptionD, editedExplanation,
                reasonCodes, otherReasonDetail, null, null, null, null, null);
    }
}
