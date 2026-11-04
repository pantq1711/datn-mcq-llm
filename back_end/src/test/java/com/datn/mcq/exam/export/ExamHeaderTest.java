package com.datn.mcq.exam.export;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Các dòng đầu đề Word (khuôn đề kiểm tra THCS theo mẫu của trường, 4/11): ngày tháng năm hiện tại, tên kì (theo tên ma
 * trận, in hoa, bỏ phần "– Môn Khối" thừa), năm học (từ tháng 8 là năm nay – năm sau), "Môn: …, Lớp: ….", thời gian làm bài
 * (tham số durationMinutes 5–180, để trống khi không có), mã đề hiển thị.
 */
class ExamHeaderTest {

    private static final LocalDate NOV_2026 = LocalDate.of(2026, 11, 4);

    @Test
    void tenKiLayTuTenMaTranVaBoPhanMonKhoiThua() {
        ExamHeader h = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null);
        assertEquals("Giữa kì I", h.periodName());
        assertEquals("ĐỀ KIỂM TRA GIỮA KÌ I", h.titleLine());
        assertEquals("Môn: Toán, Lớp: 8.", h.subjectLine());
        assertEquals("(Thời gian làm bài …… phút không kể thời gian giao đề)", h.durationLine());
        assertEquals("Mã đề 101", h.codeLine());
        assertEquals("101", h.code());
    }

    @Test
    void dongNgayThangNamLayNamHienTai() {
        ExamHeader h = ExamHeader.blank("Giữa kì I");
        assertEquals("Thứ..... ngày........ tháng..... năm 2026", h.dateLine(NOV_2026));
        assertEquals("Thứ..... ngày........ tháng..... năm 2027", h.dateLine(LocalDate.of(2027, 1, 15)));
    }

    /** Năm học: từ tháng 8 là năm nay – năm sau, trước tháng 8 là năm trước – năm nay. */
    @Test
    void namHocTinhTuThang8() {
        ExamHeader h = ExamHeader.blank("Giữa kì I");
        assertEquals("NĂM HỌC 2026 - 2027", h.yearLine(NOV_2026));
        assertEquals("NĂM HỌC 2026 - 2027", h.yearLine(LocalDate.of(2026, 8, 1)), "tháng 8 là đầu năm học mới");
        assertEquals("NĂM HỌC 2025 - 2026", h.yearLine(LocalDate.of(2026, 7, 31)), "tháng 7 vẫn là năm học cũ");
        assertEquals("NĂM HỌC 2026 - 2027", h.yearLine(LocalDate.of(2026, 12, 31)));
        assertEquals("NĂM HỌC 2026 - 2027", h.yearLine(LocalDate.of(2027, 1, 1)), "tháng 1 thuộc năm học bắt đầu năm trước");
        assertEquals("NĂM HỌC 2024 - 2025", h.yearLine(LocalDate.of(2025, 5, 20)));
    }

    @Test
    void khongCoKhoiVaCoThoiGian() {
        ExamHeader h = ExamHeader.of("Cuối chương 1", "Toán", null, 45);
        assertEquals("ĐỀ KIỂM TRA CUỐI CHƯƠNG 1", h.titleLine());
        assertEquals("Môn: Toán, Lớp: …….", h.subjectLine());
        assertEquals("(Thời gian làm bài 45 phút không kể thời gian giao đề)", h.durationLine());
    }

    @Test
    void thieuThongTinThiDeTrong() {
        ExamHeader h = ExamHeader.blank(null);
        assertEquals("ĐỀ KIỂM TRA", h.titleLine());
        assertEquals("Môn: ……, Lớp: …….", h.subjectLine());
        assertEquals("(Thời gian làm bài …… phút không kể thời gian giao đề)", h.durationLine());
        assertEquals("Mã đề 101", h.codeLine());
        assertEquals("ĐỀ KIỂM TRA", ExamHeader.blank("   ").titleLine());
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

    /** Hiện chưa có chức năng trộn mã đề nên chỉ có đề gốc; mã hiển thị là số 3 chữ số, 101 cho đề gốc. */
    @Test
    void maDeHienThiLaSoBaChuSo() {
        assertEquals("101", ExamHeader.ORIGINAL_CODE);
        assertEquals("102", ExamHeader.displayCode(1));
        assertEquals("101", ExamHeader.displayCode(0));
    }

    /** durationMinutes: số nguyên 5–180; không có thì null (in "……"); ngoài khoảng hoặc không phải số thì lỗi (400). */
    @Test
    void thamSoThoiGianLamBai() {
        assertNull(ExamHeader.parseDuration(null));
        assertNull(ExamHeader.parseDuration("  "));
        assertEquals(5, ExamHeader.parseDuration("5"));
        assertEquals(45, ExamHeader.parseDuration(" 45 "));
        assertEquals(180, ExamHeader.parseDuration("180"));
        for (String bad : new String[]{"4", "181", "0", "-45", "abc", "45.5", "4 5"}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ExamHeader.parseDuration(bad), bad);
            org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("5") && ex.getMessage().contains("180"),
                    ex.getMessage());
        }
    }
}
