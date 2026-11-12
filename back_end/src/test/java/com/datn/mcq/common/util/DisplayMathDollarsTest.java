package com.datn.mcq.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** C-02 (rà soát 11/11): "$$...$$" về "$...$" (xuất Word coi mỗi "$$" là một "$" rỗng rồi một "$" lẻ). */
class DisplayMathDollarsTest {

    @Test
    void congThucHaiDauDolaThanhMotDau() {
        DisplayMathDollars.Result r = DisplayMathDollars.repair("$$x^2-4=0$$");
        assertEquals("$x^2-4=0$", r.text());
        assertEquals(1, r.fixed());
    }

    @Test
    void chiDoiCapHaiDauGiuNguyenCongThucMotDau() {
        DisplayMathDollars.Result r = DisplayMathDollars.repair("a $$x$$ b $y$");
        assertEquals("a $x$ b $y$", r.text());
        assertEquals(1, r.fixed());
        assertEquals("$a$ và $b$, $c$", DisplayMathDollars.repair("$a$ và $$b$$, $$c$$").text());
        assertEquals(2, DisplayMathDollars.repair("$a$ và $$b$$, $$c$$").fixed());
    }

    @Test
    void haiCongThucMotDauDungLienKhongBiHieuNhamLaHaiDau() {
        String s = "$a$$b$";
        assertSame(s, DisplayMathDollars.repair(s).text());
    }

    @Test
    void boQuaDauDolaThoatVaHaiDauKhongCoCapDong() {
        String escaped = "Giá \\$$5$ một quyển";
        assertSame(escaped, DisplayMathDollars.repair(escaped).text(), "\\$ là dấu $ thật, $5$ là công thức một dấu");
        String unclosed = "a $$x^2 b";
        assertSame(unclosed, DisplayMathDollars.repair(unclosed).text(), "$$ không có cặp đóng giữ nguyên");
        String halfClosed = "a $$x^2$ b";
        assertSame(halfClosed, DisplayMathDollars.repair(halfClosed).text());
        assertEquals("\\$ và $x$", DisplayMathDollars.repair("\\$ và $$x$$").text());
    }

    @Test
    void khoangTrangVaXuongDongQuanhCongThucDuocBo() {
        assertEquals("Ta có:\n$x = 2$\nVậy", DisplayMathDollars.repair("Ta có:\n$$\n x = 2 \n$$\nVậy").text());
    }

    @Test
    void congThucRongHoacNullGiuNguyenVaCanhBao() {
        assertSame("$$$$", DisplayMathDollars.repair("$$$$").text());
        assertNull(DisplayMathDollars.repair(null).text());
        assertNull(DisplayMathDollars.warning("stem", DisplayMathDollars.repair("$x$")));
        assertEquals("json: đã sửa 2 công thức $$...$$ thành $...$ ở explanation",
                DisplayMathDollars.warning("explanation", DisplayMathDollars.repair("$$a$$ và $$b$$")));
    }
}
