package com.datn.mcq.exam.export;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Giám sát (08/11): thời gian xuất Word theo dạng công thức và số lần bị từ chối vì bận. Không cần pandoc. */
class ExportMetricsTest {

    private MeterRegistry registry;
    private ExportMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ExportMetrics(registry);
    }

    @Test
    void chuoiCoSanOGiaTri0DeLanDauTienCungTinhLaMucTang() {
        // Prometheus không thấy mức tăng nếu chuỗi chỉ xuất hiện khi đã có giá trị 1: rate() ra NaN trên dashboard.
        for (String format : new String[]{"omml", "mathtype"}) {
            for (String outcome : new String[]{"ok", "busy", "error"}) {
                assertEquals(0, registry.get("datn.exam.export").tag("format", format).tag("outcome", outcome)
                        .timer().count(), format + "/" + outcome);
            }
        }
    }

    @Test
    void ghiThoiGianTheoDangCongThuc() {
        String result = metrics.measure("mathtype", () -> "ok");

        assertEquals("ok", result);
        assertEquals(1, registry.get("datn.exam.export").tag("format", "mathtype").tag("outcome", "ok").timer().count());
    }

    @Test
    void exportBusyDemRiengVaNemLaiNguyenLoi() {
        DocumentExportException busy = new DocumentExportException(503, "EXPORT_BUSY", "bận", 30L);

        DocumentExportException thrown = assertThrows(DocumentExportException.class,
                () -> metrics.measure("omml", () -> { throw busy; }));

        assertSame(busy, thrown);
        assertEquals(1, registry.get("datn.exam.export.busy").counter().count());
        assertEquals(1, registry.get("datn.exam.export").tag("format", "omml").tag("outcome", "busy").timer().count());
    }

    @Test
    void loiKhacTinhLaErrorKhongTinhBusy() {
        assertThrows(DocumentExportException.class, () -> metrics.measure("omml", () -> {
            throw new DocumentExportException(500, "pandoc lỗi");
        }));

        assertEquals(0, registry.get("datn.exam.export.busy").counter().count());
        assertEquals(1, registry.get("datn.exam.export").tag("format", "omml").tag("outcome", "error").timer().count());
    }
}
