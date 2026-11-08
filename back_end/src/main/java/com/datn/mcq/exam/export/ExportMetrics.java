package com.datn.mcq.exam.export;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Giám sát (08/11): thời gian xuất Word ({@code datn_exam_export_seconds{format,outcome}}) và số lần bị từ chối vì máy chủ
 * bận ({@code datn_exam_export_busy_total}, mã {@code EXPORT_BUSY}). Thẻ {@code format}: omml, mathtype;
 * {@code outcome}: ok, busy, error.
 */
@Component
public class ExportMetrics {

    private final MeterRegistry registry;
    private final Counter busy;

    public ExportMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.busy = Counter.builder("datn.exam.export.busy")
                .description("Số lần xuất Word bị từ chối vì hết chỗ xếp hàng (503 EXPORT_BUSY)")
                .register(registry);
        // Khởi tạo sẵn các chuỗi ở giá trị 0: nếu chuỗi chỉ xuất hiện khi đã có giá trị 1, Prometheus không thấy mức tăng
        // của lần đầu tiên và rate()/increase() trên dashboard ra NaN khi lưu lượng thưa.
        for (String format : new String[]{"omml", "mathtype"}) {
            for (String outcome : new String[]{"ok", "busy", "error"}) {
                timer(format, outcome);
            }
        }
    }

    private Timer timer(String format, String outcome) {
        return Timer.builder("datn.exam.export")
                .description("Thời gian dựng và xuất một đề ra Word (gồm chờ xếp hàng pandoc)")
                .tag("format", format)
                .tag("outcome", outcome)
                .register(registry);
    }

    /** Chạy {@code action}, ghi thời gian và kết quả; ném lại nguyên lỗi của {@code action}. */
    public <T> T measure(String format, Supplier<T> action) {
        long start = System.nanoTime();
        String outcome = "ok";
        try {
            return action.get();
        } catch (DocumentExportException ex) {
            outcome = "EXPORT_BUSY".equals(ex.getCode()) ? "busy" : "error";
            if ("busy".equals(outcome)) {
                busy.increment();
            }
            throw ex;
        } catch (RuntimeException ex) {
            outcome = "error";
            throw ex;
        } finally {
            timer(format, outcome).record(Duration.ofNanos(System.nanoTime() - start));
        }
    }
}
