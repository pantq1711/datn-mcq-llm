package com.datn.mcq.generation.worker;

import com.datn.mcq.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lịch trả job RUNNING quá hạn về PENDING (đợt A): chạy ngay khi backend khởi động và lặp theo chu kỳ, chỉ khi worker
 * bật. Không cần database: StaleJobRecovery được thay bằng bản đếm số lần gọi.
 */
class StaleJobSchedulerTest {

    static final class CountingRecovery extends StaleJobRecovery {
        final AtomicInteger calls = new AtomicInteger();

        CountingRecovery() {
            super(null, 300);
        }

        @Override
        public int resetStaleJobs() {
            calls.incrementAndGet();
            return 0;
        }
    }

    private static boolean waitFor(AtomicInteger counter, int atLeast, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (counter.get() >= atLeast) {
                return true;
            }
            Thread.sleep(20);
        }
        return counter.get() >= atLeast;
    }

    @Test
    void workerBatThiChayKhiKhoiDongVaTheoChuKy() {
        CountingRecovery recovery = new CountingRecovery();
        new ApplicationContextRunner()
                .withPropertyValues("app.worker.enabled=true", "app.worker.stale-check-ms=300")
                .withBean(StaleJobRecovery.class, () -> recovery)
                .withUserConfiguration(SchedulingConfig.class, StaleJobScheduler.class)
                .run(ctx -> {
                    assertTrue(ctx.containsBean("staleJobScheduler"));
                    assertTrue(waitFor(recovery.calls, 1, 250), "chạy ngay khi khởi động, trước chu kỳ đầu");
                    assertTrue(waitFor(recovery.calls, 3, 3000), "lặp theo chu kỳ: " + recovery.calls.get());
                });
    }

    @Test
    void workerTatThiKhongChay() throws Exception {
        CountingRecovery recovery = new CountingRecovery();
        new ApplicationContextRunner()
                .withPropertyValues("app.worker.enabled=false", "app.worker.stale-check-ms=100")
                .withBean(StaleJobRecovery.class, () -> recovery)
                .withUserConfiguration(SchedulingConfig.class, StaleJobScheduler.class)
                .run(ctx -> assertFalse(ctx.containsBean("staleJobScheduler")));
        Thread.sleep(300);
        assertEquals(0, recovery.calls.get());
    }

    @Test
    void chuKyMacDinh2PhutVaNguongMacDinh300Giay() throws Exception {
        var scheduled = StaleJobScheduler.class.getMethod("resetStaleJobs")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertEquals("${app.worker.stale-check-ms:120000}", scheduled.fixedDelayString());
        assertEquals(0, scheduled.initialDelay());
        var param = StaleJobRecovery.class.getConstructors()[0].getParameters()[1]
                .getAnnotation(org.springframework.beans.factory.annotation.Value.class);
        assertEquals("${app.worker.stale-after-seconds:300}", param.value());
    }
}
