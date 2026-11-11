package com.datn.mcq.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dấu căn lề "&" ngoài môi trường \begin trong công thức (11/11, đề 1 câu 8: texmath báo "unexpected '&'"). */
class AlignmentAmpersandTest {

    /** Đúng hai công thức lệch của đề 1 (lời giải câu 8), nguyên văn. */
    @Test
    void haiCongThucLechCuaDe1() {
        AlignmentAmpersand.Result r = AlignmentAmpersand.repair(
                "Ta có đa thức $5x^2 - 10xy$. $&= 5x \\cdot x - 5x \\cdot 2y$. $&= 5x(x - 2y)$.");

        assertEquals("Ta có đa thức $5x^2 - 10xy$. $= 5x \\cdot x - 5x \\cdot 2y$. $= 5x(x - 2y)$.", r.text());
        assertEquals(2, r.fixed());
        String warning = AlignmentAmpersand.warning("explanation", r);
        assertTrue(warning.startsWith("json: đã sửa 2 dấu & căn lề") && warning.endsWith("ở explanation"), warning);
    }

    @Test
    void dauCanLeGiuaCongThucVaCongThucHienThi() {
        assertEquals("$a = b$", AlignmentAmpersand.repair("$a &= b$").text());
        assertEquals("$a b$", AlignmentAmpersand.repair("$a & b$").text());
        assertEquals("$$= 4(x - 2)$$", AlignmentAmpersand.repair("$$&= 4(x - 2)$$").text());
        assertEquals("$= 1$ và $ = 2$", AlignmentAmpersand.repair("$&= 1$ và $ &= 2$").text());
    }

    @Test
    void giuNguyenKhiCoMoiTruongHoacDauAThat() {
        for (String keep : new String[] {
                "$\\begin{aligned} x &= 1 \\\\ &= 2 \\end{aligned}$",
                "$\\begin{cases} x = 1 & x > 0 \\\\ y = 2 & x \\le 0 \\end{cases}$",
                "$A \\& B$",
                "$\\text{a & b} + 1$",
                "$\\mathrm{R&D}$",
                "Tom & Jerry, $x = 1$",
                "$&$",
                "$x &= 1",
                "Không có công thức & nào"}) {
            AlignmentAmpersand.Result r = AlignmentAmpersand.repair(keep);
            assertSame(keep, r.text(), keep);
            assertEquals(0, r.fixed(), keep);
            assertNull(AlignmentAmpersand.warning("explanation", r));
        }
        assertNull(AlignmentAmpersand.repair(null).text());
    }

    @Test
    void chiSuaCongThucCoDauNgoaiMoiTruong() {
        assertEquals("$\\text{a & b} = 1$, $\\begin{aligned}a&=b\\end{aligned}$, $= c$",
                AlignmentAmpersand.repair("$\\text{a & b} &= 1$, $\\begin{aligned}a&=b\\end{aligned}$, $&= c$").text());
    }
}
