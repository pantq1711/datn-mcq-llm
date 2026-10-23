package com.datn.mcq.exam.export;

/**
 * Xuất đề ra tệp không thành: 503 khi máy chủ không có pandoc hoặc pandoc chạy quá thời gian, 500 khi pandoc báo lỗi.
 * ApiExceptionHandler trả về đúng mã này kèm thông báo.
 */
public class DocumentExportException extends RuntimeException {

    private final int httpStatus;

    public DocumentExportException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
