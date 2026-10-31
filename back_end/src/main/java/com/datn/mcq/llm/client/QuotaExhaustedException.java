package com.datn.mcq.llm.client;

import java.time.LocalDateTime;

/**
 * W2-4b (C): ném ngay khi lượt gọi Gemini chạm hạn mức ngày ({@code app.llm.daily-quota}), không ngủ chờ tới giờ đặt
 * lại (tránh giữ job ở running quá hạn stale).
 */
public class QuotaExhaustedException extends RuntimeException {

    private final int dailyQuota;
    private final LocalDateTime resetAt;

    public QuotaExhaustedException(int dailyQuota, LocalDateTime resetAt) {
        super("Đã chạm hạn mức gọi Gemini trong ngày (" + dailyQuota + " lượt); đặt lại lúc " + resetAt);
        this.dailyQuota = dailyQuota;
        this.resetAt = resetAt;
    }

    public int getDailyQuota() {
        return dailyQuota;
    }

    public LocalDateTime getResetAt() {
        return resetAt;
    }
}

