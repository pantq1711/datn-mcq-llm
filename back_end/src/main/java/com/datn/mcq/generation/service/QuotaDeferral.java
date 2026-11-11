package com.datn.mcq.generation.service;

import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.llm.client.QuotaExhaustedException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * C-01: nhận biết job đang HOÃN vì hết hạn mức ngày của Gemini, để Giữ câu, đối soát chỗ và tiến độ đề không treo hàng giờ
 * khi AI hết lượt giữa ngày.
 *
 * <p>Dấu hiệu sẵn có của {@code GenerationJobProcessor#deferJobOnQuotaExhausted} (và của đánh dấu hàng loạt khi worker không
 * nhận job vì hết hạn mức): job {@code pending}, {@code next_run_at} ở TƯƠNG LAI (mốc đặt lại 00:00 giờ Thái Bình Dương), và
 * {@code last_error} có tiền tố {@value #PREFIX} HOẶC {@code next_run_at} xa hơn {@link #FAR} (backoff sau lỗi mạng chỉ
 * 15-45 giây nên không bị nhầm). Hết mốc đặt lại thì job không còn là "hoãn": worker nhận ngay.
 */
public final class QuotaDeferral {

    /** Tiền tố {@code last_error} do {@code deferJobOnQuotaExhausted} ghi. */
    public static final String PREFIX = "quota:";
    /** next_run_at xa hơn khoảng này (kể từ bây giờ) là hoãn dài, không phải backoff. */
    public static final Duration FAR = Duration.ofMinutes(5);

    private QuotaDeferral() {
    }

    /** True khi {@code job} đang hoãn vì hạn mức ngày tại thời điểm {@code now} (giờ JVM, như next_run_at). */
    public static boolean isDeferred(GenerationJob job, LocalDateTime now) {
        if (job == null || job.getStatus() != JobStatus.PENDING || job.getNextRunAt() == null
                || !job.getNextRunAt().isAfter(now)) {
            return false;
        }
        String error = job.getLastError();
        return (error != null && error.startsWith(PREFIX)) || job.getNextRunAt().isAfter(now.plus(FAR));
    }

    /** Mốc chạy tiếp của {@code job} khi đang hoãn vì hạn mức; rỗng khi không hoãn. */
    public static Optional<LocalDateTime> resumeAt(GenerationJob job, LocalDateTime now) {
        return isDeferred(job, now) ? Optional.of(job.getNextRunAt()) : Optional.empty();
    }

    /** "Hết lượt AI hôm nay, hệ thống chạy tiếp lúc HH:mm" (giờ Việt Nam) cho mốc {@code resumeAt} lấy từ next_run_at. */
    public static String message(LocalDateTime resumeAt) {
        return QuotaExhaustedException.resumeMessage(resumeAt, ZoneId.systemDefault());
    }
}
