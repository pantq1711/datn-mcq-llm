package com.datn.mcq.common.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Lỗi nghiệp vụ trả về kèm mã máy đọc được (W1-2, quyết định D7): body {@code {"error", "code", "errors"?}}, trong đó
 * {@code errors} là danh sách chi tiết (ví dụ lỗi định dạng từng trường) và chỉ có khi khác rỗng.
 */
public class ApiErrorException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<String> errors;

    public ApiErrorException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiErrorException(HttpStatus status, String code, String message, List<String> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ApiErrorException badRequest(String code, String message) {
        return new ApiErrorException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiErrorException conflict(String code, String message) {
        return new ApiErrorException(HttpStatus.CONFLICT, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<String> getErrors() {
        return errors;
    }
}
