package com.datn.mcq.assignment.service;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.common.security.SlidingWindowIpLimiter;
import com.datn.mcq.config.PublicLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Giới hạn tần suất của endpoint công khai (quyết định 16/11, AN-03); vượt thì 429 {@code TOO_MANY_REQUESTS} kèm Retry-After.
 *
 * <ul>
 *   <li>Bắt đầu lượt: theo IP, gọi TRƯỚC khi đọc DB. Hạn mức rộng vì cả lớp chung một IP.</li>
 *   <li>Mở lại và nộp: theo id lượt làm, hai bộ đếm riêng, gọi SAU khi token đã khớp một lượt (token sai trả 401 và không
 *       tính; token 32 byte nên không dò được). Lượt này gọi quá nhiều chỉ chặn chính nó, không chặn bạn cùng IP.</li>
 * </ul>
 *
 * Dùng {@link SlidingWindowIpLimiter} cho cả khóa IP và khóa lượt (khóa {@code attempt-<id>} không có dấu ':', nên lớp đó
 * giữ nguyên văn, không coi là IPv6).
 */
@Component
@EnableConfigurationProperties(PublicLimitProperties.class)
public class PublicRateLimiter {

    public static final String CODE = "TOO_MANY_REQUESTS";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final SlidingWindowIpLimiter start;
    private final SlidingWindowIpLimiter resume;
    private final SlidingWindowIpLimiter submit;

    public PublicRateLimiter(PublicLimitProperties properties) {
        this.start = new SlidingWindowIpLimiter(properties.startPerIpPerMinute(), WINDOW);
        this.resume = new SlidingWindowIpLimiter(properties.resumePerAttemptPerMinute(), WINDOW);
        this.submit = new SlidingWindowIpLimiter(properties.submitPerAttemptPerMinute(), WINDOW);
    }

    public void checkStart(String clientIp) {
        check(start, clientIp, "Có quá nhiều lượt bắt đầu từ địa chỉ mạng này. Thử lại sau ");
    }

    public void checkResume(Integer attemptId) {
        check(resume, key(attemptId), "Em mở lại bài quá nhanh. Thử lại sau ");
    }

    public void checkSubmit(Integer attemptId) {
        check(submit, key(attemptId), "Em bấm nộp quá nhiều lần. Thử lại sau ");
    }

    private static String key(Integer attemptId) {
        return "attempt-" + attemptId;
    }

    private static void check(SlidingWindowIpLimiter limiter, String key, String message) {
        long wait = limiter.tryAcquire(key);
        if (wait > 0) {
            throw ApiErrorException.tooManyRequests(CODE, message + wait + " giây.", wait);
        }
    }
}
