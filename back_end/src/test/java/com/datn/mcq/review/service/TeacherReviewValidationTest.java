package com.datn.mcq.review.service;

import com.datn.mcq.common.util.TextDistanceUtils;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.ReasonCode;
import com.datn.mcq.enums.ReviewDecision;
import com.datn.mcq.review.dto.BlindQuestionReviewDto;
import com.datn.mcq.review.dto.SubmitReviewRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeacherReviewValidationTest {

    @Test
    void blindQuestionReviewDtoMustNotExposeCorrectOptionDifficultyOrQcResults() {
        Set<String> fieldNames = Arrays.stream(BlindQuestionReviewDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertFalse(fieldNames.contains("correctOption"));
        assertFalse(fieldNames.contains("correct_option"));
        assertFalse(fieldNames.contains("llmDifficulty"));
        assertFalse(fieldNames.contains("judgeAnswer"));
        assertFalse(fieldNames.contains("similarityScore"));
    }

    @Test
    void shouldRejectApprovedAsIsWhenReviewerAnswerIsNoneOrMultiple() {
        SubmitReviewRequest noneApprovedAsIs = new SubmitReviewRequest(
                1,
                Purpose.EXPERIMENT,
                AnswerChoice.NONE,
                true,
                Difficulty.MEDIUM,
                ReviewDecision.APPROVED_AS_IS,
                Option.A,
                null, null, null, null, null, null,
                List.of(),
                null
        );
        assertThrows(IllegalArgumentException.class,
                () -> TeacherReviewService.validateSubmitRequest(noneApprovedAsIs));

        SubmitReviewRequest multipleApprovedAsIs = new SubmitReviewRequest(
                1,
                Purpose.EXPERIMENT,
                AnswerChoice.MULTIPLE,
                true,
                Difficulty.MEDIUM,
                ReviewDecision.APPROVED_AS_IS,
                Option.A,
                null, null, null, null, null, null,
                List.of(),
                null
        );
        assertThrows(IllegalArgumentException.class,
                () -> TeacherReviewService.validateSubmitRequest(multipleApprovedAsIs));
    }

    @Test
    void shouldEnforceRejectedRulesForFinalOptionReasonsAndOtherDetail() {
        SubmitReviewRequest rejectedWithFinalOption = new SubmitReviewRequest(
                1,
                Purpose.PRODUCTION,
                AnswerChoice.NONE,
                true,
                Difficulty.HARD,
                ReviewDecision.REJECTED,
                Option.A,
                null, null, null, null, null, null,
                List.of(ReasonCode.ANSWER_NONE_CORRECT),
                null
        );
        assertThrows(IllegalArgumentException.class,
                () -> TeacherReviewService.validateSubmitRequest(rejectedWithFinalOption));

        SubmitReviewRequest rejectedWithoutReason = new SubmitReviewRequest(
                1,
                Purpose.PRODUCTION,
                AnswerChoice.NONE,
                true,
                Difficulty.HARD,
                ReviewDecision.REJECTED,
                null,
                null, null, null, null, null, null,
                List.of(),
                null
        );
        assertThrows(IllegalArgumentException.class,
                () -> TeacherReviewService.validateSubmitRequest(rejectedWithoutReason));

        SubmitReviewRequest rejectedWithOtherButBlankDetail = new SubmitReviewRequest(
                1,
                Purpose.PRODUCTION,
                AnswerChoice.NONE,
                true,
                Difficulty.HARD,
                ReviewDecision.REJECTED,
                null,
                null, null, null, null, null, null,
                List.of(ReasonCode.OTHER),
                "   "
        );
        assertThrows(IllegalArgumentException.class,
                () -> TeacherReviewService.validateSubmitRequest(rejectedWithOtherButBlankDetail));

        SubmitReviewRequest validRejectedWithOther = new SubmitReviewRequest(
                1,
                Purpose.PRODUCTION,
                AnswerChoice.NONE,
                true,
                Difficulty.HARD,
                ReviewDecision.REJECTED,
                null,
                null, null, null, null, null, null,
                List.of(ReasonCode.OTHER),
                "Biểu thức mẫu thức chưa rút gọn hết"
        );
        assertDoesNotThrow(() -> TeacherReviewService.validateSubmitRequest(validRejectedWithOther));
    }

    @Test
    void editDistanceShouldIgnoreSurroundingSpacesAndCrLfNormalization() {
        assertEquals(0, TextDistanceUtils.levenshteinDistance(
                "  Bước 1: $x \\neq 0$\r\nBước 2: $x = 2$  ",
                "Bước 1: $x \\neq 0$\nBước 2: $x = 2$"
        ));
        assertTrue(TextDistanceUtils.levenshteinDistance(
                "Tính $2x + 3 = 0$",
                "Tính $2x - 3 = 0$"
        ) > 0);
    }
}

