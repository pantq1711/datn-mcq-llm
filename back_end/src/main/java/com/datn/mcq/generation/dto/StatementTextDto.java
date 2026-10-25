package com.datn.mcq.generation.dto;

import com.datn.mcq.enums.StatementKey;

/** Một ý a-d của câu Đúng/Sai trong phản hồi sinh câu: chỉ vị trí và nội dung, không kèm nhãn (V4, GĐ3). */
public record StatementTextDto(StatementKey key, String text) {
}
