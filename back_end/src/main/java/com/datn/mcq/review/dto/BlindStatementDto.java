package com.datn.mcq.review.dto;

import com.datn.mcq.enums.StatementKey;

/**
 * Một ý a-d trong form chấm mù câu Đúng/Sai (GĐ3): chỉ vị trí và nội dung. Không có nhãn, mức độ của LLM
 * hay kết quả Solver.
 */
public record BlindStatementDto(StatementKey key, String text) {
}
