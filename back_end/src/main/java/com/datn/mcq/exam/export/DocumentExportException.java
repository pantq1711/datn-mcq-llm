package com.datn.mcq.exam.export;

/**
 * Xuất đề ra tệp không thành: 503 khi máy chủ không có pandoc hoặc quá tải (EXPORT_BUSY), hoặc pandoc chạy quá
 * thời gian; 500 khi pandoc báo lỗi. ApiExceptionHandler trả về đúng mã này kèm thông báo và header Retry-After nếu có.
 */
public class DocumentExportException extends RuntimeException {

    private final int httpStatus;
    private final String code;
    private final Long retryAfterSeconds;

    public DocumentExportException(int httpStatus, String message) {
        this(httpStatus, null, message, null);
    }

    public DocumentExportException(int httpStatus, String code, String message) {
        this(httpStatus, code, message, null);
    }

    public DocumentExportException(int httpStatus, String code, String message, Long retryAfterSeconds) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
