package com.datn.mcq.bankimport;

import com.datn.mcq.bankimport.mtef.FormulaResult;
import com.datn.mcq.bankimport.mtef.FormulaResult.Status;
import com.datn.mcq.bankimport.mtef.MtefConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;

import static com.datn.mcq.bankimport.MtefBuilder.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MTEF v5 → LaTeX (W2-1). Công thức chưa dịch được phải là UNSUPPORTED kèm lý do, không bao giờ đoán. */
class MtefToLatexTest {

    private static FormulaResult convert(Obj... content) {
        return MtefConverter.fromMtef(mtef(content));
    }

    private static void assertOk(String expected, FormulaResult r) {
        assertEquals(Status.OK, r.status(), () -> "reason=" + r.reason());
        assertEquals(expected, r.latex());
    }

    @Test
    void phanSo() {
        assertOk("\\frac{3}{4}", convert(frac(num("3"), num("4"))));
    }

    @Test
    void canVaLuyThuaTrongCan() {
        assertOk("\\sqrt{x^{2}+1}", convert(tmpl(T_ROOT, 0, line(var("x"), sup(num("2")), ch8(SYM, '+'), num("1")), nullLine())));
    }

    @Test
    void canBacN() {
        assertOk("\\sqrt[3]{8}", convert(tmpl(T_ROOT, 1, line(num("8")), line(num("3")))));
    }

    @Test
    void chiSoDuoiVaSoDo() {
        assertOk("x_{1}", convert(var("x"), subscript(num("1"))));
        assertOk("60^{\\circ}", convert(num("60"), sup(ch(SYM, 0xB0))));
    }

    @Test
    void gocMuRongVaBangNhau() {
        assertOk("\\widehat{ABC}=60^{\\circ}", convert(
                tmpl(T_HAT, 0, line(var("ABC")), ch(EXPAND, 0x302)), ch8(SYM, '='), num("60"), sup(ch(SYM, 0xB0))));
    }

    @Test
    void ngoacTronBaoPhanSoVaLuyThua() {
        assertOk("\\left(x+\\frac{1}{2}\\right)^{2}", convert(
                tmpl(T_PAREN, 3, line(var("x"), ch8(SYM, '+'), frac(num("1"), num("2"))), ch(EXPAND, '('), ch(EXPAND, ')')),
                sup(num("2"))));
    }

    @Test
    void giaTriTuyetDoi() {
        assertOk("\\left|x\\right|", convert(tmpl(T_BAR, 3, line(var("x")), ch(EXPAND, '|'), ch(EXPAND, '|'))));
    }

    @Test
    void dauPhayThapPhanVaDonViChu() {
        assertOk("BC=12{,}5\\text{ cm}", convert(var("BC"), ch8(SYM, '='), num("12"), ch(FUNC, ','), num("5"), str(TEXT, " cm")));
    }

    @Test
    void dauPhayKhongKeSoThapPhanGiuNguyen() {
        // Chữ Việt: tọa độ dùng dấu chấm phẩy; dấu phẩy giữa hai chữ số LIỀN NHAU mới là dấu thập phân.
        assertOk("A(1;2)", convert(var("A"), ch(SYM, '('), num("1"), ch(FUNC, ';'), num("2"), ch(SYM, ')')));
        assertOk("1,\\,2", convert(num("1"), ch(FUNC, ','), ch(SPACE, 0xEF02), num("2")));
    }

    @Test
    void khoangTrangDonTrongCheDoChuLaDauCachKhongPhaiTextRong() {
        assertOk("AB=3\\ cm", convert(var("AB"), ch8(SYM, '='), num("3"), str(TEXT, " "), var("cm")));
    }

    @Test
    void hamLuongGiac() {
        assertOk("\\sin x", convert(str(FUNC, "sin"), var("x")));
    }

    @Test
    void chuHyLapVaKyHieuToan() {
        assertOk("\\Delta ABC\\neq 0", convert(ch(UCGREEK, 0x394), var("ABC"), ch(SYM, 0x2260), num("0")));
        assertOk("\\triangle ABC", convert(ch(MTEXTRA, 0x25B3), var("ABC")));
        assertOk("a\\cdot b-c\\Rightarrow d", convert(var("a"), ch(SYM, 0x22C5), var("b"), ch(SYM, 0x2212), var("c"),
                ch(SYM, 0x21D2), var("d")));
    }

    @Test
    void kyTuDacBietCuaLatexDuocThoat() {
        assertOk("50\\%", convert(num("50"), ch(SYM, '%')));
    }

    @Test
    void khoangTrangMathType() {
        assertOk("a\\,b", convert(var("a"), ch(SPACE, 0xEF02), var("b")));
        assertOk("ab", convert(var("a"), ch(SPACE, 0xEF01), var("b")));   // zero-width space: không có hiển thị
    }

    @Test
    void maKhoangTrangLaBiBoThanhPartial() {
        FormulaResult r = convert(var("a"), ch(SPACE, 0xEF0B), var("b"));
        assertEquals(Status.PARTIAL, r.status());
        assertEquals("ab", r.latex());
        assertTrue(r.reason().contains("EF0B"), r.reason());
    }

    @Test
    void heDuongThang() {
        assertOk("\\begin{cases}x+y=3\\\\x-y=1\\end{cases}", convert(
                tmpl(T_BRACE, 1, line(matrix(2, 1,
                        line(var("x"), ch8(SYM, '+'), var("y"), ch8(SYM, '='), num("3")),
                        line(var("x"), ch8(SYM, 0x2212), var("y"), ch8(SYM, '='), num("1")))),
                        ch(EXPAND, '{'))));
    }

    @Test
    void templateChuaHoTroLaUnsupportedKhongDoan() {
        FormulaResult r = convert(tmpl(T_SUM, 0, line(var("i")), nullLine(), nullLine(), ch(SYM, 0x2211)));
        assertEquals(Status.UNSUPPORTED, r.status());
        assertNull(r.latex());
        assertTrue(r.reason().contains("tmSUM"), r.reason());
    }

    @Test
    void typefaceLaUnsupported() {
        FormulaResult r = convert(ch(9, 'x'));
        assertEquals(Status.UNSUPPORTED, r.status());
        assertNull(r.latex());
    }

    @Test
    void rulerTrongLineKhongCoByteThe() {
        // Lỗi gốc của mtef_dump.py: 14/891 đối tượng MathType 7.4 (khóa DSMT7) bị báo "expected RULER got 1".
        FormulaResult r = MtefConverter.fromMtef(mtefOfLine(lineWithRuler(new int[]{0x0C3C}, var("y"), ch8(SYM, '='), num("2"))));
        assertOk("y=2", r);
    }

    @Test
    void rulerTrongLineLongNhau() {
        assertOk("\\frac{y}{2}", MtefConverter.fromMtef(mtef(
                tmpl(T_FRACT, 0, lineWithRuler(new int[]{1, 2}, var("y")), line(num("2"))))));
    }

    // ---- các ca thấy trong bộ thật (W2-1) ----

    @Test
    void dauMuTrenKyTuCoRecordColorXenTrongDanhSachEmbellishment() {
        assertOk("\\hat{A}=\\widehat{C}", convert(charWithEmbellAndColor(VAR, 'A', 9), ch8(SYM, '='),
                tmpl(T_HAT, 0, line(var("C")), ch(EXPAND, 0x302))));
    }

    @Test
    void donViGoBangKieuNguoiDungTypefaceAm() {
        byte[] m = mtefWithDefs(line(num("3"), fontStyleDef(1, 0), ch(-1, 'c'), ch(-1, 'm')), fontDef("Times New Roman"));
        assertOk("3\\text{cm}", MtefConverter.fromMtef(m));
    }

    @Test
    void donViGoBangUser1TheoEqnPrefs() {
        byte[] m = mtefWithDefs(line(num("5"), ch(9, 'c'), ch(9, 'm')), fontDef("Times New Roman"), eqnPrefsWithUser1(1));
        assertOk("5\\text{cm}", MtefConverter.fromMtef(m));
    }

    @Test
    void kieuNguoiDungNghiengDamVaFontDongRong() {
        assertOk("ab", MtefConverter.fromMtef(mtefWithDefs(line(fontStyleDef(1, 2), ch(-1, 'a'), ch(-1, 'b')),
                fontDef("Times New Roman"))));
        assertOk("\\textbf{ab}", MtefConverter.fromMtef(mtefWithDefs(line(fontStyleDef(1, 1), ch(-1, 'a'), ch(-1, 'b')),
                fontDef("Times New Roman"))));
        assertOk("5\\texttt{cm}", MtefConverter.fromMtef(mtefWithDefs(line(num("5"), ch(9, 'c'), ch(9, 'm')),
                fontDef("Courier New"), eqnPrefsWithUser1(1))));
    }

    @Test
    void kieuNguoiDungNghiengDeTranChoChuSoToanTuNgoacVaBienMotChuCai() {
        // Nhóm A: chữ số, toán tử, ngoặc, biến 1 chữ cái -> để trần trong chế độ toán
        assertOk("x", MtefConverter.fromMtef(mtefWithDefs(line(fontStyleDef(1, 2), ch(-1, 'x')),
                fontDef("Times New Roman"))));
        assertOk("1", MtefConverter.fromMtef(mtefWithDefs(line(fontStyleDef(1, 2), ch(-1, '1')),
                fontDef("Times New Roman"))));
        assertOk("=", MtefConverter.fromMtef(mtefWithDefs(line(fontStyleDef(1, 2), ch(-1, '=')),
                fontDef("Times New Roman"))));
        assertOk("(", MtefConverter.fromMtef(mtefWithDefs(line(fontStyleDef(1, 2), ch(-1, '(')),
                fontDef("Times New Roman"))));
    }

    @Test
    void kieuNguoiDungNghiengChuCaiLatinKhongDauDeTran() {
        // Nhóm A: chuỗi chỉ gồm chữ cái Latin không dấu (vd MN, ABC, NP) -> để trần trong chế độ toán
        assertOk("MN", MtefConverter.fromMtef(mtefWithDefs(line(fontStyleDef(1, 2), ch(-1, 'M'), ch(-1, 'N')),
                fontDef("Times New Roman"))));
        assertOk("ABC", MtefConverter.fromMtef(mtefWithDefs(line(fontStyleDef(1, 2), ch(-1, 'A'), ch(-1, 'B'), ch(-1, 'C')),
                fontDef("Times New Roman"))));
        // k8c3b1-mcq-004-f3: MN^2=MP^2-\ NP^2
        assertOk("MN^{2}=MP^{2}-\\ NP^{2}", MtefConverter.fromMtef(mtefWithDefs(line(
                fontStyleDef(1, 2),
                ch(-1, 'M'), ch(-1, 'N'),
                sup(ch(-1, '2')),
                ch(-1, '='), ch(-1, 'M'), ch(-1, 'P'),
                sup(ch(-1, '2')),
                ch(-1, '-'), ch(-1, ' '), ch(-1, 'N'), ch(-1, 'P'),
                sup(ch(-1, '2'))),
                fontDef("Times New Roman"))));
    }

    @Test
    void kieuNguoiDungNghiengChiDungTextKhiCoDauTiengVietHoacKhoangTrangGiuaCacTu() {
        // Có khoảng trắng giữa các từ -> \text{...}
        assertOk("\\text{a b}", MtefConverter.fromMtef(mtefWithDefs(line(fontStyleDef(1, 2),
                ch(-1, 'a'), ch(-1, ' '), ch(-1, 'b')),
                fontDef("Times New Roman"))));
    }

    @Test
    void congThucCoKhoangTrangCuoiVaDauXuongDong() {
        // Nhóm A: khoảng trắng ở cuối công thức không sinh dấu \ đứng lẻ
        assertOk("60^{\\circ}", convert(num("60"), sup(ch(SYM, 0xB0)), ch(SPACE, 0xEF02)));
        assertOk("220^{\\circ}", convert(num("220"), sup(ch(SYM, 0xB0)), str(TEXT, " ")));
    }

    @Test
    void kieuNguoiDungDungFontKyHieuLaUnsupportedKhongDoan() {
        byte[] m = mtefWithDefs(line(fontStyleDef(1, 0), ch(-1, 'x')), fontDef("Symbol"));
        FormulaResult r = MtefConverter.fromMtef(m);
        assertEquals(Status.UNSUPPORTED, r.status());
        assertTrue(r.reason().contains("typeface"), r.reason());
    }

    @Test
    void user1KhongCoTrongEqnPrefsLaUnsupported() {
        FormulaResult r = convert(ch(9, 'c'));
        assertEquals(Status.UNSUPPORTED, r.status());
        assertTrue(r.reason().contains("typeface_9"), r.reason());
    }

    @Test
    void giaTriTuyetDoiVeBangKyTuMtExtra() {
        assertOk("\\left|5x\\right|", convert(tmpl(T_BAR, 3, line(num("5"), var("x")), ch(EXPAND, 0xEC07), ch(EXPAND, 0xEC08))));
    }

    @Test
    void bieuThucNhieuDongTrongPile() {
        assertOk("\\begin{array}{l}a=1\\\\b=2\\end{array}", MtefConverter.fromMtef(mtefOfPile(pile(1,
                line(var("a"), ch8(SYM, '='), num("1")), line(var("b"), ch8(SYM, '='), num("2"))))));
    }

    @Test
    void congThucRongLaUnsupportedVoiLyDoRieng() {
        FormulaResult r = MtefConverter.fromMtef(mtefEmpty());
        assertEquals(Status.UNSUPPORTED, r.status());
        assertEquals("cong_thuc_rong", r.reason());
    }

    @Test
    void luongBiCatCutLaUnsupportedKhongNemNgoaiLe() {
        byte[] full = mtef(frac(num("3"), num("4")));
        byte[] cut = java.util.Arrays.copyOf(full, full.length - 6);
        FormulaResult r = MtefConverter.fromMtef(cut);
        assertEquals(Status.UNSUPPORTED, r.status());
        assertNull(r.latex());
        assertNotNull(r.reason());
    }

    @Test
    void doiTuongOleBocMtef() {
        byte[] ole = ole(mtef(frac(num("3"), num("4"))));
        assertOk("\\frac{3}{4}", MtefConverter.fromOle(ole));
    }

    @Test
    void oleKhongPhaiCongThucLaUnsupportedCoLyDo() {
        FormulaResult r = MtefConverter.fromOle(oleWithoutEquation());
        assertEquals(Status.UNSUPPORTED, r.status());
        assertTrue(r.reason().contains("Equation Native"), r.reason());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "real-1.bin|\\frac{5}{3}",
            "real-3.bin|x_{1}",
            "real-5.bin|\\widehat{ABC}=60^{\\circ}",
            "real-8.bin|\\left(x+\\frac{1}{2}\\right)^{2}",
            "real-9.bin|\\begin{cases}x+y=3\\\\x-y=1\\end{cases}"})
    void fileMathTypeThat(String file, String expected) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/mtef/" + file)) {
            assertNotNull(in, file);
            assertOk(expected, MtefConverter.fromOle(in.readAllBytes()));
        }
    }

    @Test
    void fileMathTypeThatCoCanVaLuyThua() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/mtef/real-2.bin")) {
            assertOk("\\sqrt{x^{1}+1}", MtefConverter.fromOle(in.readAllBytes()));   // biểu thức đã được sửa trong MathType
        }
    }
}
