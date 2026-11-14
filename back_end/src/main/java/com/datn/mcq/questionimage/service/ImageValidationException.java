package com.datn.mcq.questionimage.service;

/**
 * Ngoại lệ khi kiểm tra và chuẩn hóa ảnh câu hỏi không hợp lệ (I2, PR #61).
 */
public class ImageValidationException extends RuntimeException {

    private final String errorCode;

    public ImageValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ImageValidationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
