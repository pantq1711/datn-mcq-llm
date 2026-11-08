package com.datn.mcq.generation.worker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Giám sát (08/11): số liệu hàng đợi job sinh câu. Thẻ chỉ nhận giá trị hữu hạn ({@code type}: generate, answer_check,
 * grounding; {@code outcome}: trạng thái job sau khi xử lý). Prometheus hiển thị {@code datn_job_seconds},
 * {@code datn_job_stale_reset_total}; số job theo trạng thái nằm ở {@link JobQueueGauges}.
 */
@Component
public class QueueMetrics {

    private final MeterRegistry registry;
    private final Counter staleReset;

    public QueueMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.staleReset = Counter.builder("datn.job.stale.reset")
                .description("Số job running quá hạn đã được trả về pending (stale: reset)")
                .register(registry);
        // Khởi tạo sẵn ở giá trị 0 để lần đầu tiên cũng tính là mức tăng (xem ExportMetrics).
        for (String type : new String[]{"generate", "answer_check", "grounding"}) {
            for (String outcome : new String[]{"done", "failed", "pending"}) {
                timer(type, outcome);
            }
        }
    }

    private Timer timer(String type, String outcome) {
        return Timer.builder("datn.job")
                .description("Thời gian worker xử lý một job (từ lúc nhận tới lúc ghi kết quả)")
                .tag("type", type)
                .tag("outcome", outcome)
                .register(registry);
    }

    /** @param outcome trạng thái job sau lần xử lý ("done", "failed", "pending"...); null gom vào "unknown" */
    public void recordJob(String type, String outcome, long nanos) {
        timer(type, outcome == null || outcome.isBlank() ? "unknown" : outcome)
                .record(Duration.ofNanos(Math.max(0L, nanos)));
    }

    public void recordStaleReset(int count) {
        if (count > 0) {
            staleReset.increment(count);
        }
    }
}
