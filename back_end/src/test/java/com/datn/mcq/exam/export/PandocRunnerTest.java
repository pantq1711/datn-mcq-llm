package com.datn.mcq.exam.export;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Gọi pandoc để chuyển Markdown của đề sang docx (ngày 23/10, đợt 3, việc 3). */
class PandocRunnerTest {

    @Test
    void khongCoPandocTra503CoThongBao() {
        PandocRunner runner = new PandocRunner("pandoc-khong-co-tren-may-nay");
        DocumentExportException ex = assertThrows(DocumentExportException.class, () -> runner.markdownToDocx("# A"));
        assertEquals(503, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("pandoc") && ex.getMessage().contains("README"), ex.getMessage());
    }

    /** Chỉ chạy khi máy có pandoc; công thức $...$ thành Equation gốc của Word (m:oMath). */
    @Test
    void pandocThatSinhDocxCoEquationGocCuaWord() throws Exception {
        Optional<String> pandoc = PandocTestSupport.find();
        assumeTrue(pandoc.isPresent(), "máy không có pandoc: bỏ qua");
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        long before;
        try (var s = Files.list(tmp)) {
            before = s.filter(p -> p.getFileName().toString().startsWith(PandocRunner.TEMP_PREFIX)).count();
        }
        byte[] docx = new PandocRunner(pandoc.get()).markdownToDocx("# Đề thử\n\n**Câu 1.** Tính $\\frac{1}{2} + x^2$.\n\n"
                + "A. $1$\n\nB. $2$\n\na\\) Ý một.\n");
        assertEquals('P', docx[0]);
        assertEquals('K', docx[1]);
        String xml = PandocTestSupport.documentXml(docx);
        assertTrue(xml.contains("m:oMath"), "công thức là Equation gốc của Word");
        assertTrue(xml.contains(">A.</w:t>") && xml.contains(">a) Ý một.</w:t>"),
                "nhãn A. và a) giữ nguyên chữ (pandoc tách dấu cách trước công thức thành run riêng)");
        assertFalse(xml.contains("w:numPr"), "không biến A./a) thành danh sách tự đánh số");
        try (var s = Files.list(tmp)) {
            assertEquals(before, s.filter(p -> p.getFileName().toString().startsWith(PandocRunner.TEMP_PREFIX)).count(),
                    "dọn thư mục tạm sau khi xong");
        }
        // Đợt A bổ sung 3: không mở ở Compatibility Mode (để MathType Convert Equations chạy), font Times New Roman 13.
        assertTrue(PandocTestSupport.hasCompatibilityMode15(PandocTestSupport.entry(docx, "word/settings.xml")),
                "settings.xml khai báo compatibilityMode = 15");
        assertTrue(PandocTestSupport.normalIsTimesNewRoman13(PandocTestSupport.entry(docx, "word/styles.xml")),
                "kiểu Normal là Times New Roman 13");
    }

    /** Chỉ chạy khi máy có pandoc: công thức thành đối tượng MathType, công thức chưa hỗ trợ (⌊ ⌋) giữ Equation. */
    @Test
    void pandocThatXuatMathTypeGiuEquationChoCongThucChuaHoTro() throws Exception {
        Optional<String> pandoc = PandocTestSupport.find();
        assumeTrue(pandoc.isPresent(), "máy không có pandoc: bỏ qua");
        PandocRunner runner = new PandocRunner(pandoc.get());
        String md = "**Câu 1.** Tính $\\frac{3}{4} + x^2$ và $\\lfloor x\\rfloor$.\n";

        PandocRunner.Docx mathType = runner.markdownToDocx(md, EquationFormat.MATHTYPE);
        assertEquals(1, mathType.mathTypeObjects());
        assertEquals(1, mathType.equationsKeptOmml());
        String xml = PandocTestSupport.documentXml(mathType.content());
        assertEquals(1, xml.split("ProgID=\"Equation.DSMT4\"", -1).length - 1);
        assertEquals(1, xml.split("<m:oMath[ >]", -1).length - 1, "công thức chưa hỗ trợ giữ Equation");
        assertTrue(xml.contains("Câu 1."));

        PandocRunner.Docx omml = runner.markdownToDocx(md, EquationFormat.OMML);
        assertEquals(0, omml.mathTypeObjects());
        assertNull(omml.equationsKeptOmml(), "xuất Equation thì không báo số giữ lại");
        assertFalse(PandocTestSupport.documentXml(omml.content()).contains("Equation.DSMT4"));
    }

    /** Lệch số công thức (không biết ảnh nào thuộc công thức nào): trả nguyên file Equation, không lỗi 500. */
    @Test
    void soCongThucLechThiTraNguyenFileEquation() throws Exception {
        byte[] docx;
        try (var in = PandocRunnerTest.class.getResourceAsStream("/mathtype/exprs10-pandoc.docx")) {
            docx = in.readAllBytes();
        }
        PandocRunner.Docx r = PandocRunner.toMathType(docx, List.of("x"));
        assertArrayEquals(docx, r.content());
        assertEquals(0, r.mathTypeObjects());
        assertEquals(10, r.equationsKeptOmml(), "cả 10 công thức giữ Equation");

        PandocRunner.Docx broken = PandocRunner.toMathType("khong phai docx".getBytes(StandardCharsets.UTF_8), List.of());
        assertEquals(0, broken.mathTypeObjects());
        assertEquals(0, broken.equationsKeptOmml());
    }

    /** LaTeX từng công thức lấy từ AST "pandoc -t json", đúng thứ tự, gộp khoảng trắng. */
    @Test
    void docLatexTuJsonCuaPandoc() {
        String json = "{\"pandoc-api-version\":[1,23],\"meta\":{},\"blocks\":[{\"t\":\"Para\",\"c\":["
                + "{\"t\":\"Math\",\"c\":[{\"t\":\"InlineMath\"},\"\\\\frac{1}{2}\"]},{\"t\":\"Space\"},"
                + "{\"t\":\"Strong\",\"c\":[{\"t\":\"Math\",\"c\":[{\"t\":\"DisplayMath\"},\" x  +\\n y \"]}]}]}]}";
        assertEquals(List.of("\\frac{1}{2}", "x + y"),
                PandocRunner.latexFromPandocJson(json.getBytes(StandardCharsets.UTF_8)));
    }

    /** Tham số equation của API: omml | mathtype, không phân biệt hoa thường; giá trị khác là lỗi 400. */
    @Test
    void thamSoEquation() {
        assertEquals(EquationFormat.OMML, EquationFormat.parse(null));
        assertEquals(EquationFormat.OMML, EquationFormat.parse("omml"));
        assertEquals(EquationFormat.MATHTYPE, EquationFormat.parse("MathType"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> EquationFormat.parse("abc"));
        assertTrue(ex.getMessage().contains("omml") && ex.getMessage().contains("mathtype"), ex.getMessage());
    }

    /** File mẫu --reference-doc trong resources (không cần pandoc). */
    @Test
    void fileMauCoCompatibilityMode15VaFontTimesNewRoman() throws Exception {
        byte[] reference;
        try (var in = PandocRunner.class.getResourceAsStream(PandocRunner.REFERENCE_DOC_RESOURCE)) {
            assertTrue(in != null, "thiếu " + PandocRunner.REFERENCE_DOC_RESOURCE);
            reference = in.readAllBytes();
        }
        assertTrue(PandocTestSupport.hasCompatibilityMode15(PandocTestSupport.entry(reference, "word/settings.xml")));
        String styles = PandocTestSupport.entry(reference, "word/styles.xml");
        assertTrue(PandocTestSupport.normalIsTimesNewRoman13(styles));
        assertFalse(styles.substring(styles.indexOf("<w:rPrDefault>"), styles.indexOf("</w:rPrDefault>"))
                .contains("asciiTheme"), "mặc định của tài liệu không dùng font theo theme");
    }
}
