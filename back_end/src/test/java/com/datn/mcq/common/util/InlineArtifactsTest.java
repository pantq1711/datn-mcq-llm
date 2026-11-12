package com.datn.mcq.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ba lỗi định dạng mới thấy trên máy chủ (11/11): "&lt;br&gt;" ngoài công thức, mũi tên "-&gt;" / "=&gt;" viết bằng chữ, và "\\" bên
 * trong một công thức không có môi trường \begin. Mỗi hàm idempotent; không có gì để sửa thì trả đúng đối tượng chuỗi cũ.
 */
class InlineArtifactsTest {

    // ---------------------------------------------------------------- <br>

    @Test
    void theBrBaDangNgoaiCongThucThanhXuongDongThat() {
        assertEquals("Dòng 1\nDòng 2", InlineArtifacts.htmlBreaks("Dòng 1<br>Dòng 2").text());
        assertEquals("a\nb", InlineArtifacts.htmlBreaks("a<br/>b").text());
        assertEquals("a\nb", InlineArtifacts.htmlBreaks("a<br />b").text());
        assertEquals("a\nb", InlineArtifacts.htmlBreaks("a <BR> b").text(), "khoảng trắng hai bên bỏ đi, không phân biệt hoa thường");
        assertEquals("a\nb\nc", InlineArtifacts.htmlBreaks("a<br>b<br/>c").text());
        assertEquals(2, InlineArtifacts.htmlBreaks("a<br>b<br/>c").fixed());
    }

    @Test
    void theBrSatXuongDongThatKhongSinhDongTrong() {
        assertEquals("a\nb", InlineArtifacts.htmlBreaks("a<br>\nb").text());
        assertEquals("a\nb", InlineArtifacts.htmlBreaks("a\n<br>b").text());
    }

    @Test
    void theBrTrongCongThucVaDauDoLaThoatGiuNguyen() {
        String inside = "Cho $a<br>b$ nữa";
        assertSame(inside, InlineArtifacts.htmlBreaks(inside).text());
        String escaped = "giá \\$5<br>x";
        assertEquals("giá \\$5\nx", InlineArtifacts.htmlBreaks(escaped).text());
    }

    @Test
    void htmlBreaksIdempotent() {
        String once = InlineArtifacts.htmlBreaks("a<br>b $x$<br />c").text();
        assertEquals(0, InlineArtifacts.htmlBreaks(once).fixed());
        assertSame(once, InlineArtifacts.htmlBreaks(once).text());
    }

    // ---------------------------------------------------------------- -> và =>

    @Test
    void muiTenNgoaiCongThucThanhDauSuyRa() {
        assertEquals("x = 1 ⇒ x = 2", InlineArtifacts.arrows("x = 1 -> x = 2").text());
        assertEquals("x = 1 ⇒ x = 2", InlineArtifacts.arrows("x = 1 => x = 2").text());
        assertEquals("A⇒B", InlineArtifacts.arrows("A->B").text());
        assertEquals(2, InlineArtifacts.arrows("a -> b => c").fixed());
    }

    @Test
    void muiTenTrongCongThucThanhRightarrowCoCachTruocChu() {
        assertEquals("$x = 1 \\Rightarrow y$", InlineArtifacts.arrows("$x = 1 -> y$").text());
        assertEquals("$a \\Rightarrow b$", InlineArtifacts.arrows("$a => b$").text());
        assertEquals("$a\\Rightarrow b$", InlineArtifacts.arrows("$a->b$").text(), "thêm cách để lệnh không dính vào chữ sau");
        assertEquals("$a\\Rightarrow$", InlineArtifacts.arrows("$a->$").text());
        assertEquals("Ta có $x = 2 \\Rightarrow$ ⇒ $y$", InlineArtifacts.arrows("Ta có $x = 2 ->$ -> $y$").text());
    }

    @Test
    void muiTenKhacGiuNguyen() {
        for (String keep : new String[] {
                "a --> b", "a <- b", "a <-> b", "a <=> b", "a ==> b", "a ->> b", "a >= b", "a <= b",
                "$a \\rightarrow b$", "$a \\Rightarrow b$", "$a --> b$", "$a <- b$", "$\\text{a -> b}$ và $c$"}) {
            assertSame(keep, InlineArtifacts.arrows(keep).text(), keep);
            assertEquals(0, InlineArtifacts.arrows(keep).fixed(), keep);
        }
    }

    @Test
    void dauDoLaThoatKhongMoCongThucKhiDoiMuiTen() {
        assertEquals("giá \\$5 ⇒ \\$6", InlineArtifacts.arrows("giá \\$5 -> \\$6").text());
    }

    @Test
    void arrowsIdempotent() {
        String once = InlineArtifacts.arrows("a -> b, $c => d$, $e->f$").text();
        assertEquals("a ⇒ b, $c \\Rightarrow d$, $e\\Rightarrow f$", once);
        assertSame(once, InlineArtifacts.arrows(once).text());
    }

    // ---------------------------------------------------------------- "\\" trong công thức không có \begin

    @Test
    void doubleBackslashTrongCongThucKhongCoMoiTruongThanhCacCongThucRieng() {
        assertEquals("$a$\n$b$", InlineArtifacts.splitMathLines("$a \\\\ b$").text());
        assertEquals("$a$\n$b$\n$c$", InlineArtifacts.splitMathLines("$a \\\\ b \\\\ c$").text());
        assertEquals("Ta có $x = 1$\n$y = 2$ nên", InlineArtifacts.splitMathLines("Ta có $x = 1 \\\\ y = 2$ nên").text());
        assertEquals("$$a$$\n$$b$$", InlineArtifacts.splitMathLines("$$a \\\\ b$$").text());
        assertEquals(1, InlineArtifacts.splitMathLines("$a \\\\ b$").fixed());
    }

    @Test
    void doubleBackslashCuoiHoacDauCongThucChiBoDi() {
        assertEquals("$a$", InlineArtifacts.splitMathLines("$a \\\\$").text());
        assertEquals("$b$", InlineArtifacts.splitMathLines("$\\\\ b$").text());
    }

    @Test
    void congThucCoMoiTruongGiuNguyen() {
        for (String keep : new String[] {
                "$\\begin{cases} x = 1 \\\\ y = 2 \\end{cases}$",
                "$\\begin{aligned} a &= b \\\\ &= c \\end{aligned}$",
                "$$\\begin{array}{cc} 1 \\\\ 2 \\end{array}$$",
                "$\\substack{a \\\\ b}$",
                "$\\left( a \\\\ b \\right)$",
                "$a \\neq b$",
                "$a \\$ b$"}) {
            assertSame(keep, InlineArtifacts.splitMathLines(keep).text(), keep);
            assertEquals(0, InlineArtifacts.splitMathLines(keep).fixed(), keep);
        }
    }

    @Test
    void splitMathLinesIdempotent() {
        String once = InlineArtifacts.splitMathLines("Ta có $x = 1 \\\\ y = 2$.").text();
        assertSame(once, InlineArtifacts.splitMathLines(once).text());
    }

    // ---------------------------------------------------------------- cảnh báo và hàm gộp

    @Test
    void canhBaoMangTienToDaSuaVaKhongBaoKhiKhongSuaGi() {
        String w1 = InlineArtifacts.htmlBreaksWarning("explanation", InlineArtifacts.htmlBreaks("a<br>b"));
        String w2 = InlineArtifacts.arrowsWarning("explanation", InlineArtifacts.arrows("a -> b"));
        String w3 = InlineArtifacts.splitMathLinesWarning("explanation", InlineArtifacts.splitMathLines("$a \\\\ b$"));
        for (String w : new String[] {w1, w2, w3}) {
            assertTrue(w != null && w.startsWith("json: đã sửa ") && w.endsWith("ở explanation"), String.valueOf(w));
        }
        assertNull(InlineArtifacts.htmlBreaksWarning("explanation", InlineArtifacts.htmlBreaks("abc")));
        assertNull(InlineArtifacts.arrowsWarning("explanation", InlineArtifacts.arrows("abc")));
        assertNull(InlineArtifacts.splitMathLinesWarning("explanation", InlineArtifacts.splitMathLines("abc")));
    }

    @Test
    void normalizeApDungCaBaQuyTac() {
        assertEquals("a\nb ⇒ c\n$x$\n$y$ $p \\Rightarrow q$",
                InlineArtifacts.normalize("a<br>b -> c<br/>$x \\\\ y$ $p -> q$"));
        String clean = "Không có gì $x$.";
        assertSame(clean, InlineArtifacts.normalize(clean));
    }
}
