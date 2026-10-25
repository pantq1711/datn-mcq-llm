package com.datn.mcq.common.exception;

/**
 * Đăng nhập hoặc refresh không thành (401). {@code code} gửi kèm cho frontend: INVALID_CREDENTIALS, REFRESH_INVALID.
 * Không dùng cho lỗi dữ liệu người dùng đã đăng nhập (ví dụ sai mật khẩu hiện tại khi đổi mật khẩu trả 400), để
 * frontend không nhầm thành phiên hết hạn.
 */
public class AuthFailureException extends RuntimeException {

    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String REFRESH_INVALID = "REFRESH_INVALID";

    private final String code;

    public AuthFailureException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
