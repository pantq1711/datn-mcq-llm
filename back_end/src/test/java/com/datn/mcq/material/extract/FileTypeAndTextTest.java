package com.datn.mcq.material.extract;

import com.datn.mcq.common.exception.ApiErrorException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

import static com.datn.mcq.material.extract.MaterialTestFiles.minimalDocx;
import static com.datn.mcq.material.extract.MaterialTestFiles.paragraph;
import static com.datn.mcq.material.extract.MaterialTestFiles.pdf;
import static com.datn.mcq.material.extract.MaterialTestFiles.utf8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W2-3 bước 2: nhận dạng loại file bằng magic bytes (không tin đuôi hay Content-Type), giải mã txt và chuẩn hóa văn bản.
 */
class FileTypeAndTextTest {

    private final MaterialProperties props = new MaterialProperties();

    private static void assertError(ApiErrorException ex, HttpStatus status, String code) {
        assertEquals(code, ex.getCode(), ex.getMessage());
        assertEquals(status, ex.getStatus(), ex.getMessage());
    }

    // ---------- FileTypeSniffer ----------

    @Test
    void nhanDangPdfZipVaVanBanTheoNoiDung() {
        assertEquals(FileTypeSniffer.Kind.PDF, FileTypeSniffer.sniff(pdf("Hang dang thuc")));
        assertEquals(FileTypeSniffer.Kind.ZIP, FileTypeSniffer.sniff(minimalDocx(paragraph("a"))));
        assertEquals(FileTypeSniffer.Kind.TEXT, FileTypeSniffer.sniff(utf8("Hằng đẳng thức đáng nhớ")));
    }

    @Test
    void fileRongBi400() {
        assertError(assertThrows(ApiErrorException.class, () -> FileTypeSniffer.sniff(new byte[0])),
                HttpStatus.BAD_REQUEST, "EMPTY_FILE");
    }

    @Test
    void docCuOleBi415KemHuongDanLuuThanhDocx() {
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0};
        ApiErrorException ex = assertThrows(ApiErrorException.class, () -> FileTypeSniffer.sniff(ole));
        assertError(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
        assertTrue(ex.getMessage().contains(".docx"), ex.getMessage());
    }

    @Test
    void fileNhiPhanKhacBi415DuDuoiLaTxt() {
        byte[][] samples = {
                {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
                {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0},
                {0x7F, 'E', 'L', 'F', 2, 1, 1, 0},
                {'G', 'I', 'F', '8', '9', 'a', 1, 0},
                {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F'},
                {'R', 'a', 'r', '!', 0x1A, 0x07, 0, 0},
                {'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C, 0, 4},
                utf8("{\\rtf1\\ansi Xin chao}"),
        };
        for (byte[] s : samples) {
            assertError(assertThrows(ApiErrorException.class, () -> FileTypeSniffer.sniff(s)),
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
        }
    }

    @Test
    void utf16VaByteNulBi415() {
        byte[] utf16 = "Xin chào".getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[utf16.length + 2];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(utf16, 0, withBom, 2, utf16.length);
        assertError(assertThrows(ApiErrorException.class, () -> FileTypeSniffer.sniff(withBom)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
        assertError(assertThrows(ApiErrorException.class, () -> FileTypeSniffer.sniff(new byte[]{'a', 0, 'b'})),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
    }

    // ---------- PlainTextExtractor ----------

    @Test
    void txtUtf8TiengVietGiuNguyenVaBoBom() {
        byte[] body = utf8("Hằng đẳng thức đáng nhớ: $(a+b)^2$");
        byte[] bom = new byte[body.length + 3];
        bom[0] = (byte) 0xEF;
        bom[1] = (byte) 0xBB;
        bom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bom, 3, body.length);
        ExtractedText r = new PlainTextExtractor(props).extract(bom);
        assertEquals("Hằng đẳng thức đáng nhớ: $(a+b)^2$", r.text());
        assertEquals("text/plain", r.mimeType());
        assertEquals("plain", r.method());
        assertEquals(null, r.pageCount());
        assertTrue(r.warnings().isEmpty());
    }

    @Test
    void txtKhongPhaiUtf8Bi415NoiRoCanLuuUtf8() {
        // "à" trong Windows-1258/Latin-1 là 0xE0: không phải UTF-8 hợp lệ.
        byte[] latin = {'M', 'o', 't', ' ', (byte) 0xE0, ' ', 'b', 'a'};
        ApiErrorException ex = assertThrows(ApiErrorException.class, () -> new PlainTextExtractor(props).extract(latin));
        assertError(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
        assertTrue(ex.getMessage().contains("UTF-8"), ex.getMessage());
    }

    @Test
    void txtNhieuKyTuDieuKhienBiXemLaNhiPhan() {
        StringBuilder sb = new StringBuilder("Dau bai ");
        for (int i = 0; i < 40; i++) {
            sb.append((char) 0x01);
        }
        sb.append(" cuoi bai");
        assertError(assertThrows(ApiErrorException.class, () -> FileTypeSniffer.sniff(utf8(sb.toString()))),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void txtChiCoKhoangTrangBi422KhongCoVanBan() {
        assertError(assertThrows(ApiErrorException.class,
                () -> new PlainTextExtractor(props).extract(utf8("  \n\t \n"))),
                HttpStatus.UNPROCESSABLE_CONTENT, "NO_EXTRACTABLE_TEXT");
    }

    @Test
    void txtQuaDaiBi422TextTooLong() {
        props.setMaxChars(20);
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> new PlainTextExtractor(props).extract(utf8("x".repeat(21))));
        assertError(ex, HttpStatus.UNPROCESSABLE_CONTENT, "TEXT_TOO_LONG");
        assertEquals("x".repeat(20), new PlainTextExtractor(props).extract(utf8("x".repeat(20))).text());
    }

    // ---------- TextNormalizer ----------

    @Test
    void chuanHoaNfcChoTiengViet() {
        String nfd = Normalizer.normalize("Hằng đẳng thức", Normalizer.Form.NFD);
        assertFalse(nfd.equals("Hằng đẳng thức"), "đầu vào thật sự là NFD");
        assertEquals("Hằng đẳng thức", TextNormalizer.normalize(nfd));
    }

    @Test
    void boKyTuNulDieuKhienBidiVaTagAn() {
        String hidden = new String(Character.toChars(0xE0041)); // TAG LATIN CAPITAL LETTER A (ẩn)
        String in = "A\u0000B\u0007C‮DE⁦F​G" + hidden + "H﻿I­J";
        assertEquals("ABCDEFGHIJ", TextNormalizer.normalize(in));
    }

    @Test
    void giuTabXuongDongDonDepDongTrongVaKhoangTrangCuoiDong() {
        String in = "a\r\nb  \r\n\r\n\r\n\r\n\r\nc d\tx\r";
        assertEquals("a\nb\n\nc d\tx", TextNormalizer.normalize(in));
    }

    @Test
    void giuNguyenCongThucLatex() {
        String latex = "Cho $\\frac{1}{2} + x^{2} \\ge 0$ và $$\\sqrt{9}=3$$";
        assertEquals(latex, TextNormalizer.normalize(latex));
    }
}
