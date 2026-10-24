package com.datn.mcq.common.exception;

import com.datn.mcq.config.JsonSecurityErrorHandler;
import com.datn.mcq.exam.export.DocumentExportException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
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

    /** W1-2 (D7): lỗi nghiệp vụ kèm mã, ví dụ 409 VERSION_CONFLICT, 400 FORMAT_INVALID kèm danh sách lỗi từng trường. */
    @ExceptionHandler(ApiErrorException.class)
    public ResponseEntity<Map<String, Object>> handleApiError(ApiErrorException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage() != null ? ex.getMessage() : "Yêu cầu không hợp lệ");
        body.put("code", ex.getCode());
        if (!ex.getErrors().isEmpty()) {
            body.put("errors", ex.getErrors());
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /** Xuất đề ra Word không thành (thiếu pandoc, quá thời gian, pandoc báo lỗi): trả đúng mã kèm thông báo. */
    @ExceptionHandler(DocumentExportException.class)
    public ResponseEntity<Map<String, String>> handleExport(DocumentExportException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Không xuất được đề"));
    }

    /** W1-1: đăng nhập hoặc refresh không thành: 401 kèm code (INVALID_CREDENTIALS, REFRESH_INVALID). */
    @ExceptionHandler(AuthFailureException.class)
    public ResponseEntity<Map<String, String>> handleAuthFailure(AuthFailureException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage(), "code", ex.getCode()));
    }

    /** W1-1: đăng nhập sai quá nhiều lần (theo email + IP): 429 kèm Retry-After (giây). */
    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<Map<String, String>> handleLoginRateLimited(LoginRateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of("error", ex.getMessage(), "code", "TOO_MANY_ATTEMPTS"));
    }

    /** W1-1: controller cần người dùng (CurrentUser) mà request không có token hợp lệ. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleUnauthenticated(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(Map.of("error", JsonSecurityErrorHandler.UNAUTHENTICATED_MESSAGE, "code", "UNAUTHENTICATED"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", JsonSecurityErrorHandler.FORBIDDEN_MESSAGE, "code", "FORBIDDEN"));
    }

    /** Gemini trả 429 hoặc 503: trả lại đúng mã đó thay vì 500. */
    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<Map<String, String>> handleUpstream(UpstreamServiceException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Dịch vụ ngoài tạm thời không phục vụ"));
    }
}

