package com.datn.mcq.review.dto;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.enums.TruthValue;

/**
 * Đánh giá của giáo viên cho một ý câu Đúng/Sai (GĐ3).
 *
 * @param reviewerLabel nhãn nhập mù trước khi thấy nhãn LLM: T, F hoặc undecidable
 * @param difficultyLabel mức độ của ý theo giáo viên
 * @param finalLabel    nhãn cuối khi duyệt (T/F); approved_as_is: bỏ trống hoặc trùng reviewerLabel;
 *                      approved_with_edit: bắt buộc; rejected: phải trống
 * @param editedText    nội dung ý sau khi sửa (chỉ dùng với approved_with_edit); bỏ trống là giữ nguyên
 */
public record StatementReviewInput(
        StatementKey key,
        TruthChoice reviewerLabel,
        Difficulty difficultyLabel,
        TruthValue finalLabel,
        String editedText
) {
}
