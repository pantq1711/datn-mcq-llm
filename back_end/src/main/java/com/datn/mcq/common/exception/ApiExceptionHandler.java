package com.datn.mcq.common.exception;

import com.datn.mcq.config.JsonSecurityErrorHandler;
import com.datn.mcq.exam.export.DocumentExportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.unit.DataSize;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.datn.mcq.llm.client.QuotaExhaustedException;
import java.time.Duration;
import java.time.LocalDateTime;
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

    // ---------- F-27: nhật ký lỗi ----------

    /**
     * Ghi WARN cho lỗi 4xx: mã trạng thái, mã lỗi (nếu có, không thì tên lớp ngoại lệ), đường dẫn và userId (nếu đã đăng
     * nhập). KHÔNG ghi thân request, chuỗi truy vấn, mật khẩu, token hay nội dung thông báo lỗi (có thể chứa dữ liệu người
     * dùng nhập).
     */
    private static void warn4xx(HttpStatusCode status, String code, Throwable ex) {
        log.warn("Lỗi phía client {} (mã {}) {} userId={}", status.value(), code != null ? code : ex.getClass().getSimpleName(),
                requestPath(), currentUserId());
    }

    /** 5xx: ERROR kèm stacktrace, cùng đường dẫn và userId để lần ra request. */
    private static void error5xx(HttpStatusCode status, String message, Throwable ex) {
        log.error("{} {} {} userId={}", message, status.value(), requestPath(), currentUserId(), ex);
    }

    private static String requestPath() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getMethod() + " " + attributes.getRequest().getRequestURI();
        }
        return "(không rõ đường dẫn)";
    }

    private static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken token ? token.getToken().getSubject() : "-";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        warn4xx(HttpStatus.BAD_REQUEST, null, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Dữ liệu không hợp lệ"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        warn4xx(HttpStatus.NOT_FOUND, null, ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Không tìm thấy bản ghi"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        warn4xx(HttpStatus.CONFLICT, null, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Trạng thái không hợp lệ"));
    }

    /** W1-2 (D7): lỗi nghiệp vụ kèm mã, ví dụ 409 VERSION_CONFLICT, 400 FORMAT_INVALID kèm danh sách lỗi từng trường. */
    @ExceptionHandler(ApiErrorException.class)
    public ResponseEntity<Map<String, Object>> handleApiError(ApiErrorException ex) {
        if (ex.getStatus().is5xxServerError()) {
            error5xx(ex.getStatus(), "Lỗi nghiệp vụ " + ex.getCode(), ex);
        } else {
            warn4xx(ex.getStatus(), ex.getCode(), ex);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage() != null ? ex.getMessage() : "Yêu cầu không hợp lệ");
        body.put("code", ex.getCode());
        if (!ex.getErrors().isEmpty()) {
            body.put("errors", ex.getErrors());
        }
        var builder = ResponseEntity.status(ex.getStatus());
        if (ex.getRetryAfterSeconds() != null && ex.getRetryAfterSeconds() > 0) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }
        return builder.body(body);
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
            warn4xx(HttpStatus.CONFLICT, "FOREIGN_KEY_CONFLICT", ex);
            log.warn("Xung đột khóa ngoại: {}", ex.getMostSpecificCause().getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "FOREIGN_KEY_CONFLICT", "error",
                    "Dữ liệu vừa bị thay đổi bởi một thao tác khác (bản ghi liên quan không còn hoặc đang được dùng). "
                            + "Hãy tải lại rồi thử lại."));
        }
        if (UNIQUE_VIOLATION.equals(state)) {
            warn4xx(HttpStatus.CONFLICT, "UNIQUE_CONFLICT", ex);
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
        if (ex.getHttpStatus() >= 500) {
            error5xx(HttpStatusCode.valueOf(ex.getHttpStatus()), "Xuất đề ra Word không thành", ex);
        } else {
            warn4xx(HttpStatusCode.valueOf(ex.getHttpStatus()), ex.getCode(), ex);
        }
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
        warn4xx(HttpStatus.UNAUTHORIZED, ex.getCode(), ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage(), "code", ex.getCode()));
    }

    /** W1-1: đăng nhập sai quá nhiều lần (theo email + IP): 429 kèm Retry-After (giây). */
    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<Map<String, String>> handleLoginRateLimited(LoginRateLimitedException ex) {
        warn4xx(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", ex);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of("error", ex.getMessage(), "code", "TOO_MANY_ATTEMPTS"));
    }

    /** W1-1: controller cần người dùng (CurrentUser) mà request không có token hợp lệ. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleUnauthenticated(AuthenticationException ex) {
        warn4xx(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(Map.of("error", JsonSecurityErrorHandler.UNAUTHENTICATED_MESSAGE, "code", "UNAUTHENTICATED"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(AccessDeniedException ex) {
        warn4xx(HttpStatus.FORBIDDEN, "FORBIDDEN", ex);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", JsonSecurityErrorHandler.FORBIDDEN_MESSAGE, "code", "FORBIDDEN"));
    }

    /**
     * W2-3: tải tài liệu vượt giới hạn multipart của máy chủ (spring.servlet.multipart.max-file-size): 413 kèm mã
     * FILE_TOO_LARGE, cùng mã với lỗi quá lớn do bước trích văn bản.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        warn4xx(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", ex);
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
        warn4xx(HttpStatus.BAD_REQUEST, file ? "MISSING_FILE" : "MISSING_PART", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", file ? "Thiếu file: gửi multipart với phần \"file\"." : "Thiếu phần \"" + ex.getRequestPartName()
                        + "\" của yêu cầu.",
                "code", file ? "MISSING_FILE" : "MISSING_PART"));
    }

    /** W2-3: yêu cầu multipart hỏng (sai boundary, bị cắt giữa chừng): 400 thay vì 500. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipart(MultipartException ex) {
        warn4xx(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Yêu cầu tải file không hợp lệ.", "code", "INVALID_MULTIPART"));
    }


    /** W2-4b (C): chạm hạn mức ngày của Gemini: 429 kèm Retry-After (giây) và mã LLM_DAILY_QUOTA_EXHAUSTED. */
    @ExceptionHandler(QuotaExhaustedException.class)
    public ResponseEntity<Map<String, String>> handleQuotaExhausted(QuotaExhaustedException ex) {
        warn4xx(HttpStatus.TOO_MANY_REQUESTS, "LLM_DAILY_QUOTA_EXHAUSTED", ex);
        long retryAfterSeconds = 0;
        if (ex.getResetAt() != null) {
            long sec = Duration.between(LocalDateTime.now(), ex.getResetAt()).toSeconds();
            retryAfterSeconds = Math.max(1, sec);
        }
        var builder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (retryAfterSeconds > 0) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        }
        return builder.body(Map.of(
                "error", ex.getMessage() != null ? ex.getMessage() : "Đã chạm hạn mức gọi Gemini trong ngày",
                "code", "LLM_DAILY_QUOTA_EXHAUSTED"
        ));
    }

    /** Gemini trả 429 hoặc 503: trả lại đúng mã đó thay vì 500. */
    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<Map<String, String>> handleUpstream(UpstreamServiceException ex) {
        if (ex.getHttpStatus() >= 500) {
            // Dịch vụ ngoài (Gemini) tạm hỏng: WARN không kèm stacktrace (không phải lỗi của chương trình), LlmClient đã log thân.
            log.warn("Dịch vụ ngoài trả {} {} userId={}", ex.getHttpStatus(), requestPath(), currentUserId());
        } else {
            warn4xx(HttpStatusCode.valueOf(ex.getHttpStatus()), "UPSTREAM_" + ex.getHttpStatus(), ex);
        }
        return ResponseEntity.status(ex.getHttpStatus())

                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Dịch vụ ngoài tạm thời không phục vụ"));
    }

    /**
     * F-27: ngoại lệ không lường trước trả 500 {@code INTERNAL_ERROR} chung (không lộ chi tiết nội bộ) và ghi ERROR kèm
     * stacktrace. Lỗi khung của Spring MVC (JSON hỏng, sai phương thức, không có đường dẫn...) là {@link ErrorResponse} và
     * vẫn giữ nguyên mã 4xx của nó, không bị gom thành 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        HttpStatusCode frameworkStatus = null;
        HttpHeaders frameworkHeaders = HttpHeaders.EMPTY;
        if (ex instanceof ErrorResponse framework) {
            frameworkStatus = framework.getStatusCode();
            frameworkHeaders = framework.getHeaders();
        } else if (ex instanceof HttpMessageNotReadableException || ex instanceof TypeMismatchException
                || ex instanceof BindException) {
            // DefaultHandlerExceptionResolver vẫn trả 400 cho các lỗi này; không để catch-all biến chúng thành 500.
            frameworkStatus = HttpStatus.BAD_REQUEST;
        }
        if (frameworkStatus != null) {
            if (frameworkStatus.is5xxServerError()) {
                error5xx(frameworkStatus, "Lỗi khung xử lý request", ex);
            } else {
                warn4xx(frameworkStatus, ex.getClass().getSimpleName(), ex);
            }
            Map<String, String> body = new LinkedHashMap<>();
            body.put("error", frameworkMessage(frameworkStatus));
            body.put("code", "REQUEST_" + frameworkStatus.value());
            return ResponseEntity.status(frameworkStatus).headers(frameworkHeaders).body(body);
        }
        error5xx(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi không lường trước", ex);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "Hệ thống gặp lỗi không mong đợi. Vui lòng thử lại sau; nếu vẫn lỗi, hãy báo cho quản trị viên.");
        body.put("code", "INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static String frameworkMessage(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Yêu cầu không hợp lệ (dữ liệu gửi lên sai định dạng).";
            case 404 -> "Không tìm thấy đường dẫn yêu cầu.";
            case 405 -> "Phương thức yêu cầu không được hỗ trợ.";
            case 406, 415 -> "Định dạng dữ liệu không được hỗ trợ.";
            default -> status.is5xxServerError() ? "Hệ thống gặp lỗi không mong đợi." : "Yêu cầu không xử lý được.";
        };
    }
}

