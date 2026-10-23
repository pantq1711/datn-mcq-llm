package com.datn.mcq.generation.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Đợt A (ngày 23/10): gọi {@link StaleJobRecovery} ngay khi backend khởi động (initialDelay 0) và sau đó mỗi
 * {@code app.worker.stale-check-ms} (mặc định 120000 = 2 phút). Chỉ có khi worker bật (app.worker.enabled=true, cùng
 * điều kiện với SchedulingConfig).
 */
@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true")
public class StaleJobScheduler {

    private final StaleJobRecovery recovery;

    public StaleJobScheduler(StaleJobRecovery recovery) {
        this.recovery = recovery;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${app.worker.stale-check-ms:120000}")
    public void resetStaleJobs() {
        recovery.resetStaleJobs();
    }
}
