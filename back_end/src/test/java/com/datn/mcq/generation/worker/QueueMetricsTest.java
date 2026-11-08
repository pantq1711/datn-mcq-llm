package com.datn.mcq.generation.worker;

import com.datn.mcq.generation.repository.GenerationJobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Giám sát (08/11): số liệu hàng đợi job sinh câu. Không cần DB: dùng registry đơn giản và repository giả. */
class QueueMetricsTest {

    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    @Test
    void chuoiCoSanOGiaTri0ChoLoaiJobVaKetQuaChinh() {
        new QueueMetrics(registry);
        for (String type : new String[]{"generate", "answer_check", "grounding"}) {
            for (String outcome : new String[]{"done", "failed", "pending"}) {
                assertEquals(0, registry.get("datn.job").tag("type", type).tag("outcome", outcome).timer().count(),
                        type + "/" + outcome);
            }
        }
    }

    @Test
    void ghiThoiGianXuLyJobTheoLoaiVaKetQua() {
        QueueMetrics metrics = new QueueMetrics(registry);

        metrics.recordJob("generate", "done", Duration.ofMillis(1500).toNanos());
        metrics.recordJob("generate", "failed", Duration.ofMillis(200).toNanos());
        metrics.recordJob("generate", "done", Duration.ofMillis(500).toNanos());

        assertEquals(2, registry.get("datn.job").tag("type", "generate").tag("outcome", "done").timer().count());
        assertEquals(1, registry.get("datn.job").tag("type", "generate").tag("outcome", "failed").timer().count());
    }

    @Test
    void ketQuaLaNullThiGomVaoUnknown() {
        new QueueMetrics(registry).recordJob("answer_check", null, 1L);

        assertEquals(1, registry.get("datn.job").tag("type", "answer_check").tag("outcome", "unknown").timer().count());
    }

    @Test
    void staleResetCongDungSoJobVaBoQuaKhiBang0() {
        GenerationJobRepository repo = mock(GenerationJobRepository.class);
        when(repo.resetStaleRunning(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0, 2, 1);
        StaleJobRecovery recovery = new StaleJobRecovery(repo, 300);
        recovery.setQueueMetrics(new QueueMetrics(registry));

        assertEquals(0, recovery.resetStaleJobs());
        assertEquals(0.0, registry.get("datn.job.stale.reset").counter().count(), "chưa có job reset thì chưa tăng");
        assertEquals(2, recovery.resetStaleJobs());
        assertEquals(1, recovery.resetStaleJobs());

        assertEquals(3, registry.get("datn.job.stale.reset").counter().count());
    }

    @Test
    void khongCoMetricsThiStaleRecoveryVanChay() {
        GenerationJobRepository repo = mock(GenerationJobRepository.class);
        when(repo.resetStaleRunning(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(4);

        assertEquals(4, new StaleJobRecovery(repo, 300).resetStaleJobs());
    }

    @Test
    void gaugeTrangThaiHangDoiDocTuNguonVaCacheNganHan() {
        Map<String, Long> counts = new HashMap<>(Map.of("pending", 3L, "running", 1L, "done", 40L));
        AtomicInteger queries = new AtomicInteger();
        AtomicLong now = new AtomicLong(1_000);
        JobQueueGauges gauges = new JobQueueGauges(() -> {
            queries.incrementAndGet();
            return new HashMap<>(counts);
        }, Duration.ofSeconds(15), now::get);
        gauges.bindTo(registry);

        assertEquals(3, registry.get("datn.jobs").tag("status", "pending").gauge().value());
        assertEquals(1, registry.get("datn.jobs").tag("status", "running").gauge().value());
        assertEquals(40, registry.get("datn.jobs").tag("status", "done").gauge().value());
        assertEquals(0, registry.get("datn.jobs").tag("status", "failed").gauge().value(), "trạng thái vắng mặt là 0");
        assertEquals(1, queries.get(), "bốn gauge của một lần scrape chỉ tốn một truy vấn DB");

        counts.put("pending", 9L);
        now.addAndGet(5_000);
        assertEquals(3, registry.get("datn.jobs").tag("status", "pending").gauge().value(), "còn trong thời gian cache");

        now.addAndGet(11_000);
        assertEquals(9, registry.get("datn.jobs").tag("status", "pending").gauge().value(), "hết hạn cache thì đọc lại");
        assertEquals(2, queries.get());
    }

    @Test
    void loiDocDbThiGiuGiaTriCuKhongNemLoi() {
        AtomicInteger calls = new AtomicInteger();
        AtomicLong now = new AtomicLong(0);
        JobQueueGauges gauges = new JobQueueGauges(() -> {
            if (calls.incrementAndGet() > 1) {
                throw new IllegalStateException("db down");
            }
            return Map.of("pending", 5L);
        }, Duration.ofSeconds(1), now::get);
        gauges.bindTo(registry);

        assertEquals(5, registry.get("datn.jobs").tag("status", "pending").gauge().value());
        now.addAndGet(60_000);
        assertEquals(5, registry.get("datn.jobs").tag("status", "pending").gauge().value());
    }
}
