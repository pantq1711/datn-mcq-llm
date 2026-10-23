package com.datn.mcq.common.exception;

import com.datn.mcq.exam.export.DocumentExportException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Dữ liệu không hợp lệ"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Không tìm thấy bản ghi"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Trạng thái không hợp lệ"));
    }

    /** Xuất đề ra Word không thành (thiếu pandoc, quá thời gian, pandoc báo lỗi): trả đúng mã kèm thông báo. */
    @ExceptionHandler(DocumentExportException.class)
    public ResponseEntity<Map<String, String>> handleExport(DocumentExportException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Không xuất được đề"));
    }

    /** Gemini trả 429 hoặc 503: trả lại đúng mã đó thay vì 500. */
    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<Map<String, String>> handleUpstream(UpstreamServiceException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Dịch vụ ngoài tạm thời không phục vụ"));
    }
}

