package com.datn.mcq.mathtype.layout;

import com.datn.mcq.mathtype.layout.FormulaBreaker.Level;
import com.datn.mcq.mathtype.layout.FormulaBreaker.Piece;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import static com.datn.mcq.mathtype.mtef.OmmlToMtef.M;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Chỗ ngắt dòng của công thức: chỉ cấp ngoài cùng, dấu âm đứng đầu không phải chỗ ngắt, hai bên LaTeX/OMML phải khớp. */
class FormulaBreakerTest {

    private static List<String> pieces(String latex, Level level) {
        List<String> out = new ArrayList<>();
        int from = 0;
        for (int at : FormulaBreaker.latexBreaks(latex, level)) {
            out.add(latex.substring(from, at).strip());
            from = at;
        }
        out.add(latex.substring(from).strip());
        return out;
    }

    @Test
    void ngatTaiMoiQuanHeCapNgoaiCung() {
        assertEquals(List.of("A", "= B", "= C"), pieces("A = B = C", Level.RELATION));
        assertEquals(List.of("a", "\\le b", "\\ge c", "\\neq d", "< e", "> f"),
                pieces("a \\le b \\ge c \\neq d < e > f", Level.RELATION));
        assertEquals(List.of("p", "\\Rightarrow q", "\\Leftrightarrow r", "\\approx s"),
                pieces("p \\Rightarrow q \\Leftrightarrow r \\approx s", Level.RELATION));
    }

    @Test
    void khongNgatTrongNgoacPhanSoCanChiSoLeftRightBegin() {
        assertEquals(List.of("(a = b) + \\frac{c = d}{e} + \\sqrt{x = 1} + x_{i = 1}^{n}"),
                pieces("(a = b) + \\frac{c = d}{e} + \\sqrt{x = 1} + x_{i = 1}^{n}", Level.RELATION));
        assertEquals(List.of("\\left( a + b \\right) \\ge c"),
                pieces("\\left( a + b \\right) \\ge c", Level.ADDITIVE));
        assertEquals(List.of("\\left( a = b \\right)", "= c"), pieces("\\left( a = b \\right) = c", Level.RELATION));
        assertEquals(List.of("\\begin{cases} x + y = 3 \\\\ x - y = 1 \\end{cases}"),
                pieces("\\begin{cases} x + y = 3 \\\\ x - y = 1 \\end{cases}", Level.RELATION));
        assertEquals(List.of("\\begin{cases} x + y = 3 \\\\ x - y = 1 \\end{cases}"),
                pieces("\\begin{cases} x + y = 3 \\\\ x - y = 1 \\end{cases}", Level.ADDITIVE));
        // ngoặc thường (không \left): vẫn là ngoặc, không ngắt bên trong
        assertEquals(List.of("(a + b)^2", "+ c"), pieces("(a + b)^2 + c", Level.ADDITIVE));
    }

    @Test
    void ngatTaiCongTruHaiNgoiKhongNgatDauAm() {
        assertEquals(List.of("- a", "- b", "+ c"), pieces("- a - b + c", Level.ADDITIVE));
        assertEquals(List.of("a", "- b", "+ c"), pieces("a - b + c", Level.ADDITIVE));
        // dấu âm sau dấu quan hệ, sau toán tử nhân, sau ^ / _ là dấu âm
        assertEquals(List.of("x = - a", "+ b"), pieces("x = - a + b", Level.ADDITIVE));
        assertEquals(List.of("a \\cdot - b", "+ c"), pieces("a \\cdot - b + c", Level.ADDITIVE));
        assertEquals(List.of("x^- y", "+ z"), pieces("x^- y + z", Level.ADDITIVE));
        // tầng quan hệ không ngắt tại +/−
        assertEquals(List.of("a + b - c"), pieces("a + b - c", Level.RELATION));
    }

    @Test
    void doanBatDauBangQuanHeKhongNgatTaiChinNo() {
        assertEquals(List.of("= a", "+ b"), pieces("= a + b", Level.ADDITIVE));
        assertEquals(List.of("= - a", "- b"), pieces("= - a - b", Level.ADDITIVE));
    }

    @Test
    void splitCatLatexVaOmmlSongSong() throws Exception {
        List<Element> kids = children("<m:r><m:t>a</m:t></m:r><m:r><m:rPr><m:sty m:val=\"p\"/></m:rPr><m:t>=</m:t></m:r>"
                + "<m:r><m:t>b</m:t></m:r><m:r><m:rPr><m:sty m:val=\"p\"/></m:rPr><m:t>≠</m:t></m:r>"
                + "<m:f><m:num><m:r><m:t>c</m:t></m:r></m:num><m:den><m:r><m:t>d</m:t></m:r></m:den></m:f>");
        Piece whole = new Piece("a = b \\ne \\frac{c}{d}", kids);

        List<Piece> parts = FormulaBreaker.split(whole, Level.RELATION);

        assertEquals(3, parts.size());
        assertEquals("a", parts.get(0).latex());
        assertEquals("= b", parts.get(1).latex());
        assertEquals("\\ne \\frac{c}{d}", parts.get(2).latex());
        assertEquals(List.of(1, 2, 2), parts.stream().map(p -> p.children().size()).toList());
        assertSame(kids.get(1), parts.get(1).children().get(0), "đoạn 2 bắt đầu bằng chính dấu =");
        assertSame(kids.get(3), parts.get(2).children().get(0), "đoạn 3 bắt đầu bằng chính dấu ≠");
    }

    /** pandoc gắn chỉ số vào ngoặc đóng: "(a+b)^2 = c" thành "(", a, +, b, sSup[")" mũ 2], "=", c. */
    @Test
    void ngoacDongNamTrongThanChiSoVanDongNgoacMo() throws Exception {
        String run = "<m:r><m:rPr><m:sty m:val=\"p\"/></m:rPr><m:t>%s</m:t></m:r>";
        List<Element> kids = children(String.format(run, "(") + "<m:r><m:t>a</m:t></m:r>" + String.format(run, "+")
                + "<m:r><m:t>b</m:t></m:r><m:sSup><m:e>" + String.format(run, ")") + "</m:e><m:sup><m:r><m:t>2</m:t></m:r>"
                + "</m:sup></m:sSup>" + String.format(run, "=") + "<m:r><m:t>c</m:t></m:r>");
        List<Piece> parts = FormulaBreaker.split(new Piece("(a + b)^2 = c", kids), Level.RELATION);
        assertEquals(2, parts.size(), "dấu = sau (a+b)^2 là chỗ ngắt cấp ngoài cùng");
        assertEquals("(a + b)^2", parts.get(0).latex());
        assertEquals("= c", parts.get(1).latex());
        // "+" bên trong ngoặc không phải chỗ ngắt, "+" ngoài ngoặc mới là chỗ ngắt
        List<Element> kids2 = children(String.format(run, "(") + "<m:r><m:t>a</m:t></m:r>" + String.format(run, "+")
                + "<m:r><m:t>b</m:t></m:r><m:sSup><m:e>" + String.format(run, ")") + "</m:e><m:sup><m:r><m:t>2</m:t></m:r>"
                + "</m:sup></m:sSup>" + String.format(run, "+") + "<m:r><m:t>c</m:t></m:r>");
        List<Piece> sums = FormulaBreaker.split(new Piece("(a + b)^2 + c", kids2), Level.ADDITIVE);
        assertEquals(List.of("(a + b)^2", "+ c"), sums.stream().map(Piece::latex).toList());
    }

    @Test
    void latexVaOmmlLechSoChoNgatThiKhongNgat() throws Exception {
        List<Element> kids = children("<m:r><m:t>a</m:t></m:r><m:r><m:t>b</m:t></m:r>"); // OMML không có dấu quan hệ
        Piece whole = new Piece("a = b", kids);
        List<Piece> parts = FormulaBreaker.split(whole, Level.RELATION);
        assertEquals(1, parts.size());
        assertSame(whole, parts.get(0), "không đoán: trả nguyên công thức");
    }

    @Test
    void dauAmDungDauTrongOmmlKhongBiNgat() throws Exception {
        List<Element> kids = children("<m:r><m:rPr><m:sty m:val=\"p\"/></m:rPr><m:t>−</m:t></m:r><m:r><m:t>a</m:t></m:r>"
                + "<m:r><m:rPr><m:sty m:val=\"p\"/></m:rPr><m:t>−</m:t></m:r><m:r><m:t>b</m:t></m:r>");
        List<Piece> parts = FormulaBreaker.split(new Piece("- a - b", kids), Level.ADDITIVE);
        assertEquals(2, parts.size());
        assertEquals("- a", parts.get(0).latex());
        assertEquals("- b", parts.get(1).latex());
    }

    private static List<Element> children(String inner) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Element root = f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(
                ("<m:oMath xmlns:m=\"" + M + "\">" + inner + "</m:oMath>").getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
        List<Element> out = new ArrayList<>();
        for (Node c = root.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e) {
                out.add(e);
            }
        }
        return out;
    }
}
