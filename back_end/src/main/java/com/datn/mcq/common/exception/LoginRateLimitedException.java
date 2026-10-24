package com.datn.mcq.common.exception;

/** Đăng nhập sai quá nhiều lần, đang bị khóa tạm (429, code TOO_MANY_ATTEMPTS, header Retry-After). */
public class LoginRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super("Đăng nhập sai quá nhiều lần. Thử lại sau " + Math.max(1, (retryAfterSeconds + 59) / 60) + " phút.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
