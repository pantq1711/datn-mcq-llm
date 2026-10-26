package com.datn.mcq.common.exception;

import com.datn.mcq.config.JsonSecurityErrorHandler;
import com.datn.mcq.exam.export.DocumentExportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Giới hạn một file khi tải lên, từ cấu hình multipart (ghi trong thông báo 413). */
    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    private static String formatSize(long bytes) {
        long mb = 1024L * 1024;
        return bytes % mb == 0 ? bytes / mb + " MB" : bytes % 1024 == 0 ? bytes / 1024 + " KB" : bytes + " byte";
    }

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

    /** SQLState của khóa ngoại bị vi phạm (PostgreSQL 23503). */
    static final String FOREIGN_KEY_VIOLATION = "23503";
    /** SQLState của khóa duy nhất bị vi phạm (PostgreSQL 23505). */
    static final String UNIQUE_VIOLATION = "23505";

    /**
     * W1-4 (K-5): lỗi toàn vẹn dữ liệu. Khóa ngoại hoặc khóa duy nhất bị vi phạm thường do hai thao tác đồng thời (ví dụ
     * tạo đề và xóa ma trận, hai lần tạo cùng mã): 409 kèm mã {@code FOREIGN_KEY_CONFLICT} / {@code UNIQUE_CONFLICT}, không
     * lộ tên ràng buộc hay câu SQL. Lỗi toàn vẹn khác (CHECK, NOT NULL) là lỗi của chương trình: vẫn 500, kèm mã
     * {@code DATA_INTEGRITY} và ghi log đầy đủ.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String state = sqlState(ex);
        if (FOREIGN_KEY_VIOLATION.equals(state)) {
            log.warn("Xung đột khóa ngoại: {}", ex.getMostSpecificCause().getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "FOREIGN_KEY_CONFLICT", "error",
                    "Dữ liệu vừa bị thay đổi bởi một thao tác khác (bản ghi liên quan không còn hoặc đang được dùng). "
                            + "Hãy tải lại rồi thử lại."));
        }
        if (UNIQUE_VIOLATION.equals(state)) {
            log.warn("Xung đột khóa duy nhất: {}", ex.getMostSpecificCause().getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "UNIQUE_CONFLICT", "error",
                    "Dữ liệu bị trùng với một bản ghi vừa được tạo bởi thao tác khác. Hãy tải lại rồi thử lại."));
        }
        log.error("Lỗi toàn vẹn dữ liệu không mong đợi (SQLState {})", state, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", "DATA_INTEGRITY", "error",
                "Lỗi toàn vẹn dữ liệu không mong đợi."));
    }

    /** SQLState của nguyên nhân gần nhất có mã (đi theo chuỗi nguyên nhân); null khi không có. */
    private static String sqlState(Throwable ex) {
        Throwable t = ex;
        for (int depth = 0; t != null && depth < 20; depth++, t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    /** Xuất đề ra Word không thành (thiếu pandoc, quá thời gian, pandoc báo lỗi): trả đúng mã kèm thông báo. */
    @ExceptionHandler(DocumentExportException.class)
    public ResponseEntity<Map<String, String>> handleExport(DocumentExportException ex) {
        var builder = ResponseEntity.status(ex.getHttpStatus()).contentType(MediaType.APPLICATION_JSON);
        if (ex.getRetryAfterSeconds() != null && ex.getRetryAfterSeconds() > 0) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage() != null ? ex.getMessage() : "Không xuất được đề");
        if (ex.getCode() != null) {
            body.put("code", ex.getCode());
        }
        return builder.body(body);
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

    /**
     * W2-3: tải tài liệu vượt giới hạn multipart của máy chủ (spring.servlet.multipart.max-file-size): 413 kèm mã
     * FILE_TOO_LARGE, cùng mã với lỗi quá lớn do bước trích văn bản.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        // Tomcat/Spring không báo ngưỡng khi từ chối (getMaxUploadSize() = -1): lấy từ cấu hình multipart.
        long max = ex.getMaxUploadSize() > 0 ? ex.getMaxUploadSize() : maxFileSize.toBytes();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "File quá lớn: tối đa " + formatSize(max) + " mỗi file.");
        body.put("code", "FILE_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(body);
    }

    /** W2-3: yêu cầu tải lên thiếu phần file (hoặc phần khác): 400 kèm mã. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingPart(MissingServletRequestPartException ex) {
        boolean file = "file".equals(ex.getRequestPartName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", file ? "Thiếu file: gửi multipart với phần \"file\"." : "Thiếu phần \"" + ex.getRequestPartName()
                        + "\" của yêu cầu.",
                "code", file ? "MISSING_FILE" : "MISSING_PART"));
    }

    /** W2-3: yêu cầu multipart hỏng (sai boundary, bị cắt giữa chừng): 400 thay vì 500. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipart(MultipartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Yêu cầu tải file không hợp lệ.", "code", "INVALID_MULTIPART"));
    }

    /** Gemini trả 429 hoặc 503: trả lại đúng mã đó thay vì 500. */
    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<Map<String, String>> handleUpstream(UpstreamServiceException ex) {
        return ResponseEntity.status(ex.getHttpStatus())

                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Dịch vụ ngoài tạm thời không phục vụ"));
    }
}

