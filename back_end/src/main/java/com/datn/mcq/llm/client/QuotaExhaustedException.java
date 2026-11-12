package com.datn.mcq.llm.client;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

    /** Giờ Việt Nam: thông báo cho giáo viên nêu giờ theo đây, không theo giờ máy chủ. */
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * C-01: thông báo cho giáo viên khi hết lượt AI trong ngày: "Hết lượt AI hôm nay, hệ thống chạy tiếp lúc HH:mm" (giờ Việt
     * Nam, tính từ mốc đặt lại {@code resumeAt}).
     *
     * @param resumeAt   mốc đặt lại (giờ địa phương của {@code sourceZone}, như {@code generation_job.next_run_at})
     * @param sourceZone múi giờ mà {@code resumeAt} đang biểu diễn (giờ máy chủ)
     */
    public static String resumeMessage(LocalDateTime resumeAt, ZoneId sourceZone) {
        String time = resumeAt.atZone(sourceZone).withZoneSameInstant(VIETNAM).format(HOUR_MINUTE);
        return "Hết lượt AI hôm nay, hệ thống chạy tiếp lúc " + time;
    }

    public int getDailyQuota() {
        return dailyQuota;
    }

    public LocalDateTime getResetAt() {
        return resetAt;
    }
}

