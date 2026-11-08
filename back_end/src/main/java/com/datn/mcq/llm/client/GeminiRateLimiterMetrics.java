package com.datn.mcq.llm.client;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Giám sát (08/11): mức dùng hạn mức ngày của {@link GeminiRateLimiter} dưới dạng gauge ({@code datn_gemini_daily_used},
 * {@code datn_gemini_daily_quota}). Spring Boot tự gắn mọi bean {@link MeterBinder} vào registry.
 *
 * <p>Lưu ý: bộ đếm nằm trong bộ nhớ và nạp lại từ DB khi khởi động (xem Javadoc {@link GeminiRateLimiter}), nên sau khi
 * khởi động lại giữa ngày số này có thể thấp hơn thực tế.
 */
@Component
public class GeminiRateLimiterMetrics implements MeterBinder {

    private final GeminiRateLimiter limiter;

    public GeminiRateLimiterMetrics(GeminiRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("datn.gemini.daily.used", limiter, GeminiRateLimiter::getDailyUsed)
                .description("Số lượt gọi Gemini đã tính trong ngày (giờ Thái Bình Dương)")
                .register(registry);
        Gauge.builder("datn.gemini.daily.quota", limiter, GeminiRateLimiter::getDailyQuota)
                .description("Hạn mức lượt gọi Gemini mỗi ngày đã cấu hình (app.llm.daily-quota)")
                .register(registry);
    }
}
