package com.datn.mcq.exam.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Các dòng đầu đề Word (2/11): tên kì theo tên ma trận (in hoa, bỏ phần "– Môn Khối" thừa), môn kèm khối, thời gian
 * (để trống khi ma trận không có), mã đề hiển thị.
 */
class ExamHeaderTest {

    @Test
    void tenKiLayTuTenMaTranVaBoPhanMonKhoiThua() {
        ExamHeader h = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null);
        assertEquals("Giữa kì I", h.periodName());
        assertEquals("ĐỀ KIỂM TRA GIỮA KÌ I", h.titleLine());
        assertEquals("Môn: Toán 8 — Thời gian: …… phút", h.subjectLine());
        assertEquals("Mã đề: 101", h.codeLine());
    }

    @Test
    void phanMonKhoiThuaChiBoKhiDungLaHauToCuaMonVaKhoi() {
        assertEquals("Cuối kì II", ExamHeader.of("Cuối kì II - Toán 9", "Toán", 9, null).periodName());
        assertEquals("Cuối kì II", ExamHeader.of("Cuối kì II — toán 9", "Toán", 9, null).periodName());
        assertEquals("Giữa kì I – Toán 8", ExamHeader.of("Giữa kì I – Toán 8", "Vật lí", 8, null).periodName(),
                "môn khác thì không phải hậu tố thừa");
        assertEquals("Kiểm tra Toán 8 chương 1", ExamHeader.of("Kiểm tra Toán 8 chương 1", "Toán", 8, null).periodName());
        assertEquals("Toán 8", ExamHeader.of("Toán 8", "Toán", 8, null).periodName(), "bỏ hết thì giữ nguyên tên");
        assertEquals("Giữa kì I", ExamHeader.of("  Giữa kì I  ", "Toán", 8, null).periodName());
    }

    @Test
    void khongCoKhoiVaCoThoiGian() {
        ExamHeader h = ExamHeader.of("Cuối chương 1", "Toán", null, 45);
        assertEquals("ĐỀ KIỂM TRA CUỐI CHƯƠNG 1", h.titleLine());
        assertEquals("Môn: Toán — Thời gian: 45 phút", h.subjectLine());
    }

    @Test
    void thieuThongTinThiDeTrong() {
        ExamHeader h = ExamHeader.blank(null);
        assertEquals("ĐỀ KIỂM TRA", h.titleLine());
        assertEquals("Môn: …… — Thời gian: …… phút", h.subjectLine());
        assertEquals("Mã đề: 101", h.codeLine());
        assertEquals("ĐỀ KIỂM TRA", ExamHeader.blank("   ").titleLine());
    }

    /** Hiện chưa có chức năng trộn mã đề nên chỉ có đề gốc; mã hiển thị là số 3 chữ số, 101 cho đề gốc. */
    @Test
    void maDeHienThiLaSoBaChuSo() {
        assertEquals("101", ExamHeader.ORIGINAL_CODE);
        assertEquals("102", ExamHeader.displayCode(1));
        assertEquals("101", ExamHeader.displayCode(0));
    }
}
