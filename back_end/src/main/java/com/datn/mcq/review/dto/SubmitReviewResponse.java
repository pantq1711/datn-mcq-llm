package com.datn.mcq.review.dto;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.ReviewDecision;

import java.time.LocalDateTime;

public record SubmitReviewResponse(
        Integer reviewId,
        Integer questionId,
        Purpose reviewPurpose,
        AnswerChoice reviewerAnswer,
        boolean answerCorrect,
        boolean formatOk,
        Difficulty difficultyLabel,
        ReviewDecision decision,
        Option finalOption,
        boolean edited,
        Integer editDistance,
        QuestionStatus questionStatus,
        Integer bankItemId,
        LocalDateTime reviewedAt
) {
}

