package com.datn.mcq.llm.client;

/**
 * Lỗi từ Gemini mà gọi lại cùng yêu cầu chắc chắn lỗi như cũ (C-04, C-10): HTTP 4xx khác 429 (sai tên model, schema bị từ
 * chối, key sai) hoặc phản hồi bị chặn an toàn ({@code finishReason} SAFETY/RECITATION, {@code promptFeedback.blockReason}
 * SAFETY). Worker đánh dấu job {@code failed} ngay, không thử lại và không tốn thêm lượt hạn mức ngày (M-22). Lỗi tạm thời
 * (429, 503, 5xx, mạng, hết token) KHÔNG dùng lớp này.
 */
public class NonRetryableLlmException extends RuntimeException {

    public NonRetryableLlmException(String message) {
        super(message);
    }
}
