package com.datn.mcq.review.dto;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.ReviewDecision;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Phản hồi sau khi nộp đánh giá câu hỏi.
 * Tuyệt đối không trả về answerCorrect theo hợp đồng V1 dòng 137:
 * "Không báo giáo viên biết đáp án họ chọn trùng hay lệch đáp án LLM, trước hay sau khi nộp."
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
        LocalDateTime reviewedAt
) {
}

