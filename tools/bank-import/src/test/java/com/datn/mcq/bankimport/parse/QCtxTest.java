package com.datn.mcq.bankimport.parse;

import com.datn.mcq.bankimport.docx.RichText;
import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.mtef.FormulaResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QCtxTest {

    private static RichText richText(Object... parts) {
        RichText.Builder b = new RichText.Builder();
        for (Object p : parts) {
            if (p instanceof String s) {
                b.text(s, false, false, (byte) 0);
            } else if (p instanceof RichText.Obj o) {
                b.obj(o, false, false);
            }
        }
        return b.build();
    }

    private static RichText.Obj eq(String ref) {
        return new RichText.Obj(true, ref, null, null, null, null, null);
    }

    private static FormulaService fixed(Map<String, String> formulas) {
        FormulaService fs = new FormulaService(Map.of(), false);
        formulas.forEach((k, v) -> fs.cache.put(k, FormulaResult.ok(v)));
        return fs;
    }

    private static QCtx createCtx(Map<String, String> formulas) {
        return new QCtx(new Question(), "test", fixed(formulas));
    }

    @Test
    void khongDoiKhiSauCongThucLaKhoangTrang() {
        QCtx c = createCtx(Map.of("ABCD", "ABCD", "N", "N"));
        RichText rt = richText("tứ giác ", eq("ABCD"), " và điểm ", eq("N"), " nằm");
        assertEquals("tứ giác $ABCD$ và điểm $N$ nằm", c.render(rt, "stem"));
    }

    @Test
    void chenKhoangTrangKhiTheoSauLaChuCaiTiengViet() {
        QCtx c = createCtx(Map.of("ABCD", "ABCD"));
        RichText rt = richText(eq("ABCD"), "có");
        assertEquals("$ABCD$ có", c.render(rt, "stem"));
    }

    @Test
    void chenKhoangTrangKhiTheoSauLaChuCaiNhungGiuNguyenPhatBieuVaDauCau() {
        QCtx c = createCtx(Map.of("D", "D", "150", "150^{\\circ}"));
        RichText rt = richText("góc ", eq("D"), "là ", eq("150"), ".");
        assertEquals("góc $D$ là $150^{\\circ}$.", c.render(rt, "stem"));
    }

    @Test
    void khongDoiKhiTheoNgaySauLaDauCau() {
        QCtx c = createCtx(Map.of("x", "x", "y", "y"));
        RichText rt1 = richText(eq("x"), ".");
        assertEquals("$x$.", c.render(rt1, "stem"));

        RichText rt2 = richText(eq("x"), ", ", eq("y"));
        assertEquals("$x$, $y$", c.render(rt2, "stem"));
    }

    @Test
    void nhieuCongThucLienNhauKhongCoCachSauDauDoLaMo() {
        QCtx c = createCtx(Map.of("A", "A", "B", "B", "C", "C"));
        RichText rtConsecutive = richText(eq("A"), eq("B"), eq("C"));
        String res1 = c.render(rtConsecutive, "stem");
        assertEquals("$A$$B$$C$", res1);
        assertFalse(res1.matches(".*\\$\\s+[^$\\s].*"), "Không bao giờ có khoảng trắng ngay sau $ mở: " + res1);

        RichText rtWithSpaces = richText(eq("A"), " ", eq("B"), " ", eq("C"));
        String res2 = c.render(rtWithSpaces, "stem");
        assertEquals("$A$ $B$ $C$", res2);
        assertFalse(res2.matches(".*\\$\\s+[^$\\s].*"), "Không bao giờ có khoảng trắng ngay sau $ mở: " + res2);
    }
}
