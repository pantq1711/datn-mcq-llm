package com.datn.mcq.common.exception;

/**
 * Dịch vụ ngoài (Gemini) từ chối tạm thời: 429 (giới hạn tần suất) hoặc 503 (quá tải, bảo trì).
 * ApiExceptionHandler trả về đúng mã này kèm thông báo; thông báo không chứa body thô của dịch vụ ngoài.
 * Kế thừa RuntimeException để các nơi đang bắt RuntimeException (worker, answer_check) giữ nguyên hành vi.
 */
public class UpstreamServiceException extends RuntimeException {

    private final int httpStatus;

    public UpstreamServiceException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
