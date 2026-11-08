package com.datn.mcq.generation.worker;

import com.datn.mcq.generation.repository.GenerationJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Giám sát (08/11): số job theo trạng thái ({@code datn_jobs{status=...}}). Một lần scrape đọc bốn gauge nhưng chỉ tốn
 * một truy vấn {@code GROUP BY} nhờ cache ngắn (mặc định 15 giây). Lỗi đọc DB thì giữ giá trị cũ, không làm hỏng scrape.
 */
@Component
public class JobQueueGauges implements MeterBinder {

    private static final Logger LOG = LoggerFactory.getLogger(JobQueueGauges.class);

    /** Giá trị cột {@code generation_job.status} (JobStatus). */
    static final List<String> STATUSES = List.of("pending", "running", "done", "failed");

    private final Supplier<Map<String, Long>> source;
    private final long ttlMs;
    private final LongSupplier clockMs;

    private Map<String, Long> cached = Map.of();
    private long loadedAtMs;
    private boolean loaded;

    @Autowired
    public JobQueueGauges(GenerationJobRepository repository) {
        this(() -> {
            Map<String, Long> out = new HashMap<>();
            for (GenerationJobRepository.StatusCount row : repository.countByStatus()) {
                out.put(row.getStatus(), row.getTotal());
            }
            return out;
        }, Duration.ofSeconds(15), System::currentTimeMillis);
    }

    JobQueueGauges(Supplier<Map<String, Long>> source, Duration ttl, LongSupplier clockMs) {
        this.source = source;
        this.ttlMs = ttl.toMillis();
        this.clockMs = clockMs;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (String status : STATUSES) {
            Gauge.builder("datn.jobs", this, g -> g.value(status))
                    .description("Số job sinh câu theo trạng thái (cache ngắn, không phải số tức thời)")
                    .tag("status", status)
                    .register(registry);
        }
    }

    private synchronized double value(String status) {
        long now = clockMs.getAsLong();
        if (!loaded || now - loadedAtMs >= ttlMs) {
            try {
                cached = source.get();
                loaded = true;
            } catch (RuntimeException ex) {
                LOG.warn("Không đọc được số job theo trạng thái để xuất metric: {}", ex.getMessage());
            }
            loadedAtMs = now;
        }
        return cached.getOrDefault(status, 0L);
    }
}
