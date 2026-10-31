package com.datn.mcq.llm.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * W2-4b (C): bộ điều tiết lượt gọi Gemini DUY NHẤT trong ứng dụng, đặt tại phễu {@link LlmClient} để mọi lượt gọi
 * (sinh câu, Solver, grounding, sinh đồng bộ) dùng chung một hạn mức:
 * <ul>
 *   <li>Khoảng cách tối thiểu giữa hai lượt gọi liên tiếp: {@code app.llm.min-spacing-ms} (mặc định 4200 ms, tương ứng
 *       ~14,2 lượt/phút, có biên an toàn dưới trần 15 RPM của Gemini free tier).</li>
 *   <li>Khi một luồng nhận HTTP 429: {@link #onRateLimited(Duration)} tạm dừng TOÀN BỘ bộ điều tiết theo
 *       {@code retryDelay} trong phản hồi của Gemini (hoặc {@code app.llm.rate-limit-cooldown-ms}, mặc định 60000 ms khi
 *       phản hồi không có {@code retryDelay}).</li>
 *   <li>Hạn mức ngày: {@code app.llm.daily-quota} (mặc định 480 lượt/ngày, dưới trần 500 RPD), đặt lại lúc 00:00 giờ
 *       Thái Bình Dương ({@code America/Los_Angeles}). Bộ đếm nằm trong bộ nhớ, tính MỌI lượt gọi (kể cả lỗi mạng/429/5xx),
 *       và khởi tạo ở lần dùng đầu tiên bằng số lượt gọi thành công đã ghi trong DB kể từ 00:00 giờ Thái Bình Dương hôm đó.
 *       Chạm hạn mức ngày khi đang ở trong job thì ném ngay {@link QuotaExhaustedException} (không ngủ chờ tới 00:00 để
 *       tránh biến job thành stale).</li>
 * </ul>
 *
 * <p><b>Hạn chế đã biết:</b> (1) Bộ điều tiết giữ trạng thái trong bộ nhớ của một tiến trình JVM nên chỉ chính xác khi
 * chạy 1 backend instance (đúng với triển khai hiện tại). (2) Khi ứng dụng khởi động lại trong ngày, bộ đếm ngày khởi
 * tạo từ các dòng kết quả đã lưu trong DB nên không đếm được các lượt gọi thất bại/429 xảy ra trước lúc khởi động lại;
 * ngưỡng mặc định 480 (thay vì 500) dành biên an toàn cho phần đếm thiếu này.
 */
@Component
public class GeminiRateLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(GeminiRateLimiter.class);

    /** Múi giờ Gemini đặt lại hạn mức ngày (00:00 Pacific Time). */
    public static final ZoneId PACIFIC_ZONE = ZoneId.of("America/Los_Angeles");

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final long minSpacingMs;
    private final int dailyQuota;
    private final long defaultCooldownMs;
    private final Clock clock;
    private final Sleeper sleeper;
    private final GeminiDailyUsageLookup usageLookup;

    private LocalDate currentPacificDay;
    private LocalDate quotaExhaustedWarnedForDay;
    private int dailyUsed;
    private long nextAllowedAtMs;
    private long cooldownUntilMs;

    @Autowired
    public GeminiRateLimiter(
            @Value("${app.llm.min-spacing-ms:4200}") long minSpacingMs,
            @Value("${app.llm.daily-quota:480}") int dailyQuota,
            @Value("${app.llm.rate-limit-cooldown-ms:60000}") long defaultCooldownMs,
            @Autowired(required = false) GeminiDailyUsageLookup usageLookup) {
        this(minSpacingMs, dailyQuota, defaultCooldownMs, Clock.systemDefaultZone(),
                d -> Thread.sleep(Math.max(0L, d.toMillis())), usageLookup);
    }

    public GeminiRateLimiter(
            long minSpacingMs,
            int dailyQuota,
            long defaultCooldownMs,
            Clock clock,
            Sleeper sleeper,
            GeminiDailyUsageLookup usageLookup) {
        this.minSpacingMs = Math.max(0L, minSpacingMs);
        this.dailyQuota = dailyQuota;
        this.defaultCooldownMs = Math.max(0L, defaultCooldownMs);
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        this.sleeper = sleeper != null ? sleeper : d -> Thread.sleep(Math.max(0L, d.toMillis()));
        this.usageLookup = usageLookup;
    }

    /**
     * Kiểm tra còn hạn mức ngày để worker quyết định có nhận job mới từ hàng đợi hay không. Chạm hạn mức thì log WARN
     * (mỗi ngày một lần) và trả {@code false} để job giữ nguyên ở {@code pending}.
     */
    public synchronized boolean hasDailyQuota() {
        Instant now = clock.instant();
        refreshDayLocked(now);
        if (dailyQuota > 0 && dailyUsed >= dailyQuota) {
            if (!currentPacificDay.equals(quotaExhaustedWarnedForDay)) {
                quotaExhaustedWarnedForDay = currentPacificDay;
                LOG.warn("Đã chạm hạn mức gọi Gemini trong ngày ({}/{} lượt); không nhận job mới tới {} (00:00 giờ Thái Bình Dương)",
                        dailyUsed, dailyQuota, nextResetLocalDateTime(now));
            }
            return false;
        }
        return true;
    }

    /**
     * Xin phép trước một lượt gọi HTTP tới Gemini. Chờ đủ khoảng cách tối thiểu ({@code minSpacingMs}) và khoảng tạm dừng
     * do 429 (nếu có); nếu đã chạm hạn mức ngày thì ném ngay {@link QuotaExhaustedException} mà không ngủ chờ.
     */
    public void acquire() {
        while (true) {
            long waitMs;
            synchronized (this) {
                Instant now = clock.instant();
                refreshDayLocked(now);
                if (dailyQuota > 0 && dailyUsed >= dailyQuota) {
                    throw new QuotaExhaustedException(dailyQuota, nextResetLocalDateTime(now));
                }
                long nowMs = now.toEpochMilli();
                long earliestMs = Math.max(nextAllowedAtMs, cooldownUntilMs);
                if (nowMs >= earliestMs) {
                    dailyUsed++;
                    nextAllowedAtMs = nowMs + minSpacingMs;
                    return;
                }
                waitMs = earliestMs - nowMs;
            }
            try {
                sleeper.sleep(Duration.ofMillis(waitMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Luồng gọi Gemini bị ngắt khi chờ bộ điều tiết", e);
            }
        }
    }

    /**
     * Ghi nhận phản hồi HTTP 429 từ Gemini: tạm dừng TOÀN BỘ bộ điều tiết trong {@code retryDelay} (hoặc
     * {@code defaultCooldownMs} khi {@code retryDelay} null/không dương).
     */
    public synchronized void onRateLimited(Duration retryDelay) {
        long delayMs;
        if (retryDelay == null || retryDelay.isNegative()) {
            delayMs = defaultCooldownMs;
        } else if (retryDelay.isZero()) {
            delayMs = minSpacingMs;
        } else {
            delayMs = retryDelay.toMillis();
        }
        if (delayMs <= 0) {
            return;
        }
        long nowMs = clock.instant().toEpochMilli();
        long untilMs = nowMs + delayMs;
        if (untilMs > cooldownUntilMs) {
            cooldownUntilMs = untilMs;
        }
        if (untilMs > nextAllowedAtMs) {
            nextAllowedAtMs = untilMs;
        }
        LOG.warn("Gemini trả HTTP 429: tạm dừng toàn bộ bộ điều tiết {} ms", delayMs);
    }

    public synchronized int getDailyUsed() {
        refreshDayLocked(clock.instant());
        return dailyUsed;
    }

    public long getMinSpacingMs() {
        return minSpacingMs;
    }

    public int getDailyQuota() {
        return dailyQuota;
    }

    public long getDefaultCooldownMs() {
        return defaultCooldownMs;
    }

    /** Đặt lại trạng thái để lần kiểm tra kế tiếp nạp lại số lượt gọi trong ngày từ DB. */
    public synchronized void resetFromDatabase() {
        this.currentPacificDay = null;
        this.quotaExhaustedWarnedForDay = null;
        this.dailyUsed = 0;
        this.nextAllowedAtMs = 0L;
        this.cooldownUntilMs = 0L;
    }

    private void refreshDayLocked(Instant now) {
        LocalDate pacificToday = now.atZone(PACIFIC_ZONE).toLocalDate();
        if (currentPacificDay == null) {
            LocalDateTime startLocal = startOfPacificDayLocal(pacificToday);
            if (usageLookup != null) {
                try {
                    dailyUsed = Math.max(0, usageLookup.countCallsSince(startLocal));
                    currentPacificDay = pacificToday;
                    quotaExhaustedWarnedForDay = null;
                } catch (RuntimeException ex) {
                    LOG.warn("Không thể nạp số lượt gọi Gemini từ DB cho ngày {}: {}", pacificToday, ex.getMessage());
                }
            } else {
                dailyUsed = 0;
                currentPacificDay = pacificToday;
                quotaExhaustedWarnedForDay = null;
            }
        } else if (!pacificToday.equals(currentPacificDay)) {
            currentPacificDay = pacificToday;
            quotaExhaustedWarnedForDay = null;
            dailyUsed = 0;
        }
    }

    private LocalDateTime startOfPacificDayLocal(LocalDate pacificDay) {
        ZonedDateTime startPacific = pacificDay.atStartOfDay(PACIFIC_ZONE);
        return LocalDateTime.ofInstant(startPacific.toInstant(), clock.getZone());
    }

    private LocalDateTime nextResetLocalDateTime(Instant now) {
        LocalDate pacificToday = now.atZone(PACIFIC_ZONE).toLocalDate();
        ZonedDateTime nextMidnightPacific = pacificToday.plusDays(1).atStartOfDay(PACIFIC_ZONE);
        return LocalDateTime.ofInstant(nextMidnightPacific.toInstant(), clock.getZone());
    }
}
