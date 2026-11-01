package com.datn.mcq.common.exception;

/**
 * Đăng nhập bị giới hạn tần suất (429, code TOO_MANY_ATTEMPTS, header Retry-After): sai quá nhiều lần theo email + IP, hoặc
 * quá nhiều lần thử từ cùng một IP (D3).
 */
public class LoginRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super("Đăng nhập sai quá nhiều lần. Thử lại sau " + Math.max(1, (retryAfterSeconds + 59) / 60) + " phút.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private LoginRateLimitedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Quá nhiều lần đăng nhập từ cùng một IP trong một khoảng ngắn (kể cả đăng nhập đúng). */
    public static LoginRateLimitedException byIp(long retryAfterSeconds) {
        return new LoginRateLimitedException("Có quá nhiều lần đăng nhập từ địa chỉ mạng này. Thử lại sau "
                + Math.max(1, retryAfterSeconds) + " giây.", retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
