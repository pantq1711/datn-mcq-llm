package com.datn.mcq.auth.service;

import com.datn.mcq.config.SecurityProperties;
import com.datn.mcq.config.SecurityProperties.LoginRateLimit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn đăng nhập sai (W1-1, "nếu kịp"): đếm theo khóa (email đã chuẩn hóa + IP). Sai {@code maxFailures} lần trong
 * {@code window} thì khóa {@code lock}; đăng nhập đúng thì xóa bộ đếm. Giữ trong bộ nhớ một tiến trình: khởi động lại thì
 * mất, chạy nhiều bản backend thì mỗi bản đếm riêng (chấp nhận cho đồ án). Khóa theo cả IP để người khác không khóa được
 * tài khoản của giáo viên chỉ bằng cách nhập sai email đó.
 */
@Component
public class LoginAttemptLimiter {

    /** Quá số khóa này thì dọn các mục đã hết hạn. */
    static final int CLEANUP_THRESHOLD = 10_000;

    private record Attempts(int failures, Instant windowStart, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final LoginRateLimit config;
    private final Clock clock;

    @Autowired
    public LoginAttemptLimiter(SecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptLimiter(SecurityProperties properties, Clock clock) {
        this.config = properties.loginRateLimit();
        this.clock = clock;
    }

    public static String key(String normalizedEmail, String clientIp) {
        return normalizedEmail + "|" + (clientIp == null ? "" : clientIp);
    }

    /** Thời gian còn bị khóa; {@link Duration#ZERO} nếu không bị khóa. */
    public Duration lockedFor(String key) {
        Attempts a = attempts.get(key);
        Instant now = clock.instant();
        if (a == null || a.lockedUntil() == null || !a.lockedUntil().isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, a.lockedUntil());
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        attempts.compute(key, (k, a) -> {
            if (a == null || a.windowStart().plus(config.window()).isBefore(now)
                    || (a.lockedUntil() != null && !a.lockedUntil().isAfter(now))) {
                a = new Attempts(0, now, null);
            }
            int failures = a.failures() + 1;
            return failures >= config.maxFailures()
                    ? new Attempts(0, now, now.plus(config.lock()))
                    : new Attempts(failures, a.windowStart(), null);
        });
        if (attempts.size() > CLEANUP_THRESHOLD) {
            cleanup(now);
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private void cleanup(Instant now) {
        attempts.entrySet().removeIf(e -> {
            Attempts a = e.getValue();
            boolean lockOver = a.lockedUntil() == null || !a.lockedUntil().isAfter(now);
            return lockOver && a.windowStart().plus(config.window()).isBefore(now);
        });
    }
}
