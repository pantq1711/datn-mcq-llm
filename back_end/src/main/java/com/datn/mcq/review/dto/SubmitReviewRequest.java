package com.datn.mcq.review.dto;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.ReasonCode;
import com.datn.mcq.enums.ReviewDecision;

import java.util.List;

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
        String otherReasonDetail
) {
}

