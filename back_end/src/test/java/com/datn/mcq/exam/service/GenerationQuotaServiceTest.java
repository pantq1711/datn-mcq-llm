package com.datn.mcq.exam.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D1: ngày tính theo giờ Việt Nam (UTC+7), không phải UTC; Retry-After tới 0 giờ giờ Việt Nam; nội dung thông báo. */
class GenerationQuotaServiceTest {

    private static Clock at(String utcInstant) {
        return Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC);
    }

    @Test
    void ngayDoiLucNuaDemGioVietNamLa17hUtc() {
        assertEquals(LocalDate.of(2026, 10, 30), GenerationQuotaService.today(at("2026-10-30T16:59:59Z")));
        assertEquals(LocalDate.of(2026, 10, 31), GenerationQuotaService.today(at("2026-10-30T17:00:00Z")));
        // 20h UTC ngày 30 đã là sáng ngày 31 ở Việt Nam
        assertEquals(LocalDate.of(2026, 10, 31), GenerationQuotaService.today(at("2026-10-30T20:00:00Z")));
    }

    @Test
    void retryAfterTinhDenNuaDemKeTiepGioVietNam() {
        assertEquals(3600, GenerationQuotaService.secondsUntilNextDay(at("2026-10-30T16:00:00Z")));
        assertEquals(1, GenerationQuotaService.secondsUntilNextDay(at("2026-10-30T16:59:59Z")));
        assertEquals(86400, GenerationQuotaService.secondsUntilNextDay(at("2026-10-30T17:00:00Z")));
    }

    @Test
    void thongBaoNeuConBaoNhieuVaKhiNaoDuocTaoTiep() {
        String m = GenerationQuotaService.dailyMessage(115, 8, 120, 3 * 3600 + 25 * 60, LocalDate.of(2026, 10, 30));
        assertTrue(m.contains("tối đa 120 câu"), m);
        assertTrue(m.contains("đã dùng 115 câu, còn 5 câu"), m);
        assertTrue(m.contains("cần 8 câu"), m);
        assertTrue(m.contains("từ 0 giờ ngày 31/10/2026"), m);
        assertTrue(m.contains("sau khoảng 3 giờ 25 phút"), m);
    }

    @Test
    void thongBaoKhiHetSachHanMucVaConDuoiMotGio() {
        String m = GenerationQuotaService.dailyMessage(130, 1, 120, 90, LocalDate.of(2026, 10, 30));
        assertTrue(m.contains("còn 0 câu"), m);
        assertTrue(m.contains("sau khoảng 2 phút"), m);
    }
}
