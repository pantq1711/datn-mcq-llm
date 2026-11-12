package com.datn.mcq.generation.service;

import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.llm.client.QuotaExhaustedException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-01: nhận biết job đang HOÃN vì hết hạn mức ngày của Gemini bằng dấu hiệu sẵn có của
 * {@code GenerationJobProcessor.deferJobOnQuotaExhausted} (pending, {@code last_error} tiền tố "quota:", {@code next_run_at}
 * = mốc đặt lại) và thông báo giờ chạy tiếp theo giờ Việt Nam. Không cần DB.
 */
class QuotaDeferralTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 10, 0);

    private static GenerationJob job(JobStatus status, String lastError, LocalDateTime nextRunAt) {
        GenerationJob j = new GenerationJob();
        j.setStatus(status);
        j.setLastError(lastError);
        j.setNextRunAt(nextRunAt);
        return j;
    }

    @Test
    void jobHoanVeMocDatLaiLaDeferred() {
        GenerationJob j = job(JobStatus.PENDING, "quota: Đã chạm hạn mức gọi Gemini trong ngày (480 lượt)", NOW.plusHours(5));
        assertTrue(QuotaDeferral.isDeferred(j, NOW));
        assertEquals(Optional.of(NOW.plusHours(5)), QuotaDeferral.resumeAt(j, NOW));
    }

    @Test
    void chiCanNextRunAtXaHonVaiPhutCungLaDeferred() {
        assertTrue(QuotaDeferral.isDeferred(job(JobStatus.PENDING, null, NOW.plusHours(5)), NOW));
        assertTrue(QuotaDeferral.isDeferred(job(JobStatus.PENDING, null, NOW.plusMinutes(6)), NOW));
    }

    @Test
    void jobPendingThuongKhongPhaiDeferred() {
        assertFalse(QuotaDeferral.isDeferred(job(JobStatus.PENDING, null, NOW), NOW), "xếp hàng, đến hạn ngay");
        assertFalse(QuotaDeferral.isDeferred(job(JobStatus.PENDING, null, NOW.minusMinutes(1)), NOW));
        assertFalse(QuotaDeferral.isDeferred(job(JobStatus.PENDING, "Lỗi kết nối Gemini API: x", NOW.plusSeconds(30)), NOW),
                "backoff ngắn 15-45 giây sau lỗi mạng");
        assertFalse(QuotaDeferral.isDeferred(job(JobStatus.PENDING, null, NOW.plusMinutes(4)), NOW));
        assertFalse(QuotaDeferral.isDeferred(job(JobStatus.PENDING, null, null), NOW));
        assertEquals(Optional.empty(), QuotaDeferral.resumeAt(job(JobStatus.PENDING, null, NOW), NOW));
    }

    @Test
    void mocDatLaiDaQuaThiKhongConLaDeferred() {
        GenerationJob j = job(JobStatus.PENDING, "quota: x", NOW.minusMinutes(1));
        assertFalse(QuotaDeferral.isDeferred(j, NOW), "tới hạn rồi: worker nhận ngay");
        assertFalse(QuotaDeferral.isDeferred(job(JobStatus.PENDING, "quota: x", NOW), NOW));
    }

    @Test
    void jobKhongPhaiPendingKhongBaoGioLaDeferred() {
        for (JobStatus s : new JobStatus[] {JobStatus.RUNNING, JobStatus.DONE, JobStatus.FAILED}) {
            assertFalse(QuotaDeferral.isDeferred(job(s, "quota: x", NOW.plusHours(5)), NOW), s.name());
        }
        assertFalse(QuotaDeferral.isDeferred(null, NOW));
    }

    @Test
    void thongBaoGioChayTiepTheoGioVietNam() {
        // 00:00 giờ Thái Bình Dương (UTC-8 mùa đông) = 15:00 giờ Việt Nam
        LocalDateTime resetAt = LocalDateTime.of(2026, 11, 12, 0, 0);
        String message = QuotaExhaustedException.resumeMessage(resetAt, ZoneId.of("America/Los_Angeles"));
        assertEquals("Hết lượt AI hôm nay, hệ thống chạy tiếp lúc 15:00", message);
    }

    @Test
    void thongBaoQuaNgayVaPhutLe() {
        // máy chủ chạy giờ UTC: 17:05 UTC = 00:05 sáng hôm sau giờ Việt Nam
        assertEquals("Hết lượt AI hôm nay, hệ thống chạy tiếp lúc 00:05", QuotaExhaustedException.resumeMessage(
                LocalDateTime.of(2026, 11, 11, 17, 5), ZoneId.of("UTC")));
        assertEquals("Hết lượt AI hôm nay, hệ thống chạy tiếp lúc 07:30", QuotaExhaustedException.resumeMessage(
                LocalDateTime.of(2026, 11, 12, 0, 30), ZoneId.of("UTC")));
    }

    @Test
    void messageDungMuiGioMayChu() {
        LocalDateTime resetAt = LocalDateTime.of(2026, 11, 12, 0, 0);
        assertEquals(QuotaExhaustedException.resumeMessage(resetAt, ZoneId.systemDefault()),
                QuotaDeferral.message(resetAt));
    }
}
