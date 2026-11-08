package com.datn.mcq.generation.worker;

import com.datn.mcq.generation.repository.GenerationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Đợt A (ngày 23/10): trả job kẹt ở running (backend tắt giữa chừng, lỗi không bắt được) về pending. Job running có
 * started_at (V10) quá {@code app.worker.stale-after-seconds} (mặc định 300, biến WORKER_STALE_AFTER_SECONDS) được
 * chạy lại ngay, ghi last_error "stale: reset", không tính vào attempt. Lịch gọi nằm ở {@link StaleJobScheduler}.
 */
@Service
public class StaleJobRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(StaleJobRecovery.class);

    private final GenerationJobRepository repository;
    private final int staleAfterSeconds;
    private QueueMetrics queueMetrics;

    public StaleJobRecovery(GenerationJobRepository repository,
                            @Value("${app.worker.stale-after-seconds:300}") int staleAfterSeconds) {
        this.repository = repository;
        this.staleAfterSeconds = Math.max(1, staleAfterSeconds);
    }

    /** Giám sát (08/11): nạp bằng setter để chữ ký constructor (và các test dựng tay) không đổi; có thể null. */
    @Autowired(required = false)
    public void setQueueMetrics(QueueMetrics queueMetrics) {
        this.queueMetrics = queueMetrics;
    }

    /** @return số job đã trả về pending */
    @Transactional
    public int resetStaleJobs() {
        LocalDateTime now = LocalDateTime.now();
        int reset = repository.resetStaleRunning(now, now.minusSeconds(staleAfterSeconds));
        if (queueMetrics != null) {
            queueMetrics.recordStaleReset(reset);
        }
        if (reset > 0) {
            LOG.warn("Đã trả {} job running quá {} giây về pending (stale: reset)", reset, staleAfterSeconds);
        }
        return reset;
    }
}
