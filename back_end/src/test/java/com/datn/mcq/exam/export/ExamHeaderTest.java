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
        for (String bad : new String[]{"4", "181", "0", "-45", "abc", "45.5", "4 5", "+45",
                "٤٥", "०३०", "４５"}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ExamHeader.parseDuration(bad), bad);
            org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("5") && ex.getMessage().contains("180"),
                    ex.getMessage());
        }
    }

    /**
     * 16/11: tên đề đã mở đầu bằng "Kiểm tra" hoặc "Đề kiểm tra" (không phân biệt hoa thường, bỏ khoảng trắng đầu) thì
     * không lặp chữ "KIỂM TRA" ở dòng tên kì.
     */
    @Test
    void tenDeMoDauBangKiemTraKhongLapChu() {
        assertEquals("ĐỀ KIỂM TRA GIỮA KÌ I", ExamHeader.of("Kiểm tra giữa kì I", "Toán", 8, null).titleLine());
        assertEquals("ĐỀ KIỂM TRA CUỐI KÌ II", ExamHeader.blank("  đề kiểm tra cuối kì II").titleLine());
        assertEquals("ĐỀ KIỂM TRA GIỮA KÌ I", ExamHeader.of("KIỂM TRA giữa kì I – Toán 8 (bản sao)", "Toán", 8, null)
                .titleLine(), "cùng với bỏ hậu tố");
        assertEquals("ĐỀ KIỂM TRA CHƯƠNG 1", ExamHeader.blank("Đề kiểm tra: Chương 1").titleLine(), "bỏ dấu nối sau tiền tố");
        assertEquals("ĐỀ KIỂM TRA 15 PHÚT", ExamHeader.blank("Kiểm  tra   15 phút").titleLine(), "nhiều khoảng trắng");
        assertEquals("ĐỀ KIỂM TRA", ExamHeader.blank("Kiểm tra").titleLine(), "chỉ có tiền tố");
        assertEquals("ĐỀ KIỂM TRA", ExamHeader.blank("Đề kiểm tra").titleLine());
        // NFD (bàn phím gõ dấu tổ hợp) cũng nhận ra
        assertEquals("ĐỀ KIỂM TRA GIỮA KÌ I", ExamHeader.blank(
                java.text.Normalizer.normalize("Kiểm tra giữa kì I", java.text.Normalizer.Form.NFD)).titleLine());
        // không phải tiền tố thì giữ nguyên như trước
        assertEquals("ĐỀ KIỂM TRA KIỂM TRANH CHẤP", ExamHeader.blank("Kiểm tranh chấp").titleLine());
        assertEquals("ĐỀ KIỂM TRA BÀI KIỂM TRA 15 PHÚT", ExamHeader.blank("Bài kiểm tra 15 phút").titleLine());
        assertEquals("ĐỀ KIỂM TRA ĐỀ GIỮA KÌ I", ExamHeader.blank("Đề giữa kì I").titleLine());
        assertEquals("ĐỀ KIỂM TRA GIỮA KÌ I", ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null).titleLine());
    }

    /** NV-42 (16/11): tên kì lấy theo tên đề; bỏ hậu tố "(bản sao)" (kể cả lặp) rồi mới bỏ "– Môn Khối". */
    @Test
    void tenKiBoHauToBanSao() {
        assertEquals("Giữa kì I", ExamHeader.of("Giữa kì I – Toán 8 (bản sao)", "Toán", 8, null).periodName());
        assertEquals("ĐỀ KIỂM TRA MA TRẬN GK1 TOÁN 8",
                ExamHeader.of("Ma trận GK1 Toán 8 (bản sao)", "Toán", 8, null).titleLine());
        assertEquals("Giữa kì I", ExamHeader.of("Giữa kì I – Toán 8 (bản sao) (bản sao)", "Toán", 8, null).periodName());
        assertEquals("Giữa kì I", ExamHeader.of("Giữa kì I (Bản sao)  ", "Toán", 8, null).periodName(),
                "không phân biệt hoa thường, bỏ khoảng trắng cuối");
        assertEquals("Kiểm tra chương 1", ExamHeader.blank("Kiểm tra chương 1 (bản sao)").periodName(),
                "đề không có ma trận cũng bỏ");
        assertEquals("Bản sao (bản sao) đề cũ", ExamHeader.of("Bản sao (bản sao) đề cũ", "Toán", 8, null).periodName(),
                "chỉ bỏ khi là hậu tố");
        assertEquals("(bản sao)", ExamHeader.of("(bản sao)", "Toán", 8, null).periodName(), "bỏ hết thì giữ nguyên tên");
    }

    /** NV-43 (16/11): mã đề in ra mặc định 101; đổi bằng tham số examCode thì ô mã đề và chân trang theo mã đó. */
    @Test
    void maDeTheoThamSo() {
        ExamHeader h = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null);
        assertEquals("101", h.examCode());
        ExamHeader other = h.withExamCode("205");
        assertEquals("205", other.code());
        assertEquals("Mã đề 205", other.codeLine());
        assertEquals("Giữa kì I", other.periodName(), "các dòng khác giữ nguyên");
        assertEquals(h.titleLine(), other.titleLine());
        assertEquals("101", ExamHeader.blank("Đề").code());
        assertEquals("101", ExamHeader.blank("Đề", 45).withExamCode(null).code(), "null là mã mặc định");
    }

    /** examCode: số nguyên 100–999; không có (null, trống) là 101; ngoài khoảng hoặc không phải số thì lỗi (400). */
    @Test
    void thamSoMaDe() {
        assertEquals("101", ExamHeader.parseExamCode(null));
        assertEquals("101", ExamHeader.parseExamCode("  "));
        assertEquals("100", ExamHeader.parseExamCode("100"));
        assertEquals("205", ExamHeader.parseExamCode(" 205 "));
        assertEquals("999", ExamHeader.parseExamCode("999"));
        for (String bad : new String[]{"99", "1000", "0", "-101", "abc", "10.5", "1 01", "+101", "١٠١"}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ExamHeader.parseExamCode(bad), bad);
            org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("100") && ex.getMessage().contains("999"),
                    ex.getMessage());
        }
    }
}
