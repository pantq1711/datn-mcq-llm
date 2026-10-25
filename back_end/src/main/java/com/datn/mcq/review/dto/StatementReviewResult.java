package com.datn.mcq.review.dto;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.enums.TruthValue;

/**
 * Lặp lại đánh giá của chính giáo viên cho một ý trong phản hồi nộp bài (GĐ3). Không có label_correct
 * (so với nhãn LLM) để không báo giáo viên biết mình chọn trùng hay lệch LLM.
 */
public record StatementReviewResult(
        StatementKey key,
        TruthChoice reviewerLabel,
        Difficulty difficultyLabel,
        TruthValue finalLabel
) {
}
