package com.datn.mcq.llm.client;

/**
 * Thiếu cấu hình LLM (chưa có LLM_API_KEY / GEMINI_API_KEY). Vẫn là {@link IllegalStateException} như trước, nhưng có kiểu
 * riêng để worker nhận ra đây là lỗi cấu hình: gọi lại không thể khỏi, nên job hỏng ngay thay vì thử lại (C-04, M-22).
 */
public class LlmNotConfiguredException extends IllegalStateException {

    public LlmNotConfiguredException(String message) {
        super(message);
    }
}
