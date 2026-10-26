package com.datn.mcq.material.extract;

import com.datn.mcq.exam.export.PandocRunner;
import com.datn.mcq.exam.export.PandocTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * W2-3 bước 5a: làm sạch định dạng markdown của pandoc ở khâu trích (**, _, dấu \ thoát ký tự, tiêu đề #, liên kết) để
 * extracted_text lưu trong DB đã là văn bản sạch, và GIỮ NGUYÊN công thức $...$ / $$...$$.
 */
class MarkdownCleanerTest {

    private static String clean(String s) {
        return MarkdownCleaner.clean(s);
    }

    @Test
    void boDauInDamVaNghieng() {
        assertEquals("Spike: công thức MathType", clean("**Spike: công thức MathType**"));
        assertEquals("một từ nghiêng và từ khác", clean("một từ _nghiêng_ và từ *khác*"));
        assertEquals("gạch ngang bỏ", clean("gạch ngang ~~bỏ~~"));
        assertEquals("H2O và x2", clean("H~2~O và x^2^"));
    }

    @Test
    void giuGachDuoiGiuaChuNhuTenBien() {
        assertEquals("biến snake_case và a_b_c", clean("biến snake_case và a_b_c"));
    }

    @Test
    void boDauThoatKyTuCuaPandocNhungGiuKyTuThat() {
        assertEquals("Giá 5$ và a_b, *sao*, [x], 1. mục, a < b, #1, a|b, a\\b",
                clean("Giá 5\\$ và a\\_b, \\*sao\\*, \\[x\\], 1\\. mục, a \\< b, \\#1, a\\|b, a\\\\b"));
    }

    @Test
    void giuNguyenCongThucDollarKeCaKyTuDacBietBenTrong() {
        String in = "Tính $a_1 * b^{\\frac{1}{2}} \\ge _x_$ và **xong**\n\n$$x_{n} \\\\ y *z*$$\n\nHết";
        assertEquals("Tính $a_1 * b^{\\frac{1}{2}} \\ge _x_$ và xong\n\n$$x_{n} \\\\ y *z*$$\n\nHết", clean(in));
    }

    @Test
    void dollarThoatLaChuThatConDollarChuaThoatGhepCapLaCongThuc() {
        assertEquals("Giá 5$ và $x$ rẻ", clean("Giá 5\\$ và $x$ rẻ"));
        assertEquals("giá 5$ rẻ", clean("giá 5$ rẻ"), "một dấu $ lẻ không có cặp là chữ thường");
    }

    @Test
    void boTieuDeHashNhungGiuChuTieuDe() {
        assertEquals("Bài 3. Hằng đẳng thức\n\nMục 1.1 Khái niệm\n\nNội dung",
                clean("# Bài 3. Hằng đẳng thức\n\n## Mục 1.1 Khái niệm\n\nNội dung"));
        assertEquals("giữa dòng # không phải tiêu đề", clean("giữa dòng # không phải tiêu đề"));
    }

    @Test
    void lienKetChiGiuChuHienThi() {
        assertEquals("Xem tại đây và http://x.y/z", clean("[Xem tại đây](http://x.y/z) và <http://x.y/z>"));
    }

    @Test
    void boDongKeBangVaDauTrichDan() {
        String in = "  -------------  ------\n  a              b\n  -------------  ------\n  1              2\n\n> trích dẫn\n\n---\n\nHết";
        String out = clean(in);
        assertFalse(out.contains("-----"), out);
        assertTrue(out.contains("a              b") && out.contains("1              2"), "giữ nội dung bảng: " + out);
        assertTrue(out.contains("trích dẫn") && !out.contains(">"), out);
    }

    @Test
    void gachDauDongNgatDongThanhXuongDongVaDanhSachGon() {
        assertEquals("dòng một\ndòng hai", clean("dòng một\\\ndòng hai"));
        assertEquals("- Mục một\n- Mục hai\n1. Bước một", clean("-   Mục một\n-   Mục hai\n1.  Bước một"));
    }

    @Test
    void khongDoiVanBanThuongVaCongThucDaSach() {
        String plain = "Cho tam giác ABC có $\\widehat{A} = 90^\\circ$. Tính cạnh BC (đơn vị: cm).";
        assertEquals(plain, clean(plain));
        assertEquals("", clean(""));
    }

    @Test
    void docxThatPhatRaVanBanSachGiuCongThuc() {
        String pandoc = PandocTestSupport.find().orElse(null);
        assumeTrue(pandoc != null, "máy không có pandoc: bỏ qua");
        byte[] docx = new PandocRunner(pandoc).markdownToDocx("# Bài 3. Hằng đẳng thức\n\n**Định nghĩa:** _bình phương_ của "
                + "một tổng là $(a+b)^2 = a^2 + 2ab + b^2$; giá 5 \\$ và a\\_b.\n\n- Mục *một*\n- Mục hai\n");
        MaterialProperties props = new MaterialProperties();
        ExtractedText r = new DocxTextExtractor(props, pandoc, new ZipSafetyScanner(props), new DocxFormulaCounter())
                .extract(docx);
        String t = r.text();
        assertTrue(t.contains("Bài 3. Hằng đẳng thức"), t);
        assertTrue(t.contains("Định nghĩa: bình phương của một tổng là $(a + b)^{2} = a^{2} + 2ab + b^{2}$"), t);
        assertTrue(t.contains("giá 5 $ và a_b."), t);
        assertFalse(t.contains("**") || t.contains("#") || t.contains("\\"), "không còn ký hiệu markdown: " + t);
        assertTrue(t.contains("- Mục một") && t.contains("- Mục hai"), t);
    }
}
