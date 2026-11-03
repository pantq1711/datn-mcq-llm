package com.datn.mcq.exam.export;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import com.datn.mcq.mathtype.docx.DocxPackage;
import com.datn.mcq.mathtype.docx.PageGeometry;
import com.datn.mcq.mathtype.preview.PreviewRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * File Word xuất ra theo khuôn đề kiểm tra THCS (4/11), kiểm trên document.xml / styles.xml thật do pandoc sinh
 * (cần pandoc; máy không có thì bỏ qua), cho cả công thức Equation (omml) và MathType. Dữ liệu đề dựng tay, không gọi
 * Gemini, không cần DB.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExamDocxFormatTest {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String TAB = "⇥";
    private static final String FORMULA = "∎";

    private static final ExamScoringProperties SCORING = new ExamScoringProperties(new BigDecimal("0.25"),
            new BigDecimal("0.5"), List.of(BigDecimal.ZERO, new BigDecimal("0.1"), new BigDecimal("0.25"),
            new BigDecimal("0.5"), new BigDecimal("1.0")));

    private static final String ANSWER_TITLE = "ĐÁP ÁN VÀ HƯỚNG DẪN CHẤM – MÃ ĐỀ 101";

    // ---------------------------------------------------------------- dữ liệu đề

    private static final List<String> MEDIUM = List.of(
            "$\\frac{x+1}{x-1} + \\frac{2x}{x+3} - \\frac{5}{x}$",
            "$\\frac{x-1}{x+1} + \\frac{2x}{x-3} - \\frac{5}{x}$",
            "$(x + 1)(x - 2)(x + 3) + 5x$",
            "$x^3 - 3x^2 + 3x - 1 = (x - 1)^3$");
    private static final List<String> LONG = List.of(
            "Hai đường thẳng song song thì không có điểm chung nào cả",
            "Hai đường thẳng cắt nhau thì có đúng một điểm chung duy nhất",
            "Hai đường thẳng trùng nhau thì có vô số điểm chung với nhau",
            "Hai đường thẳng chéo nhau thì không nằm trong cùng một mặt phẳng");

    private static ExamItemDto mcq(int no, String stem, List<String> o, Option correct, String explanation) {
        return new ExamItemDto(no, 1, no, "I", "Đa thức", QuestionType.MCQ, "ready", "generated", null, 100 + no, stem,
                o.get(0), o.get(1), o.get(2), o.get(3), correct, null, null, explanation, Difficulty.EASY, false, List.of());
    }

    private static ExamItemDto tf(int no, String stem, String explanation, TruthValue... labels) {
        List<ItemStatementDto> statements = new ArrayList<>();
        String[] texts = {"Đồ thị là một parabol", "Hàm số đồng biến trên $\\mathbb{R}$", "Giá trị nhỏ nhất bằng $0$",
                "Đồ thị đi qua $O$."};
        for (int i = 0; i < 4; i++) {
            statements.add(new ItemStatementDto(StatementKey.values()[i], texts[i], labels[i], Difficulty.EASY));
        }
        return new ExamItemDto(20 + no, 2, no, "II", "Hàm số", QuestionType.TF, "kept", "bank", 7, null, stem, null, null,
                null, null, null, null, statements, explanation, null, false, List.of());
    }

    private static ExamItemDto sa(int no, String stem, String answer, String explanation) {
        return new ExamItemDto(40 + no, 3, no, "III", "Tứ giác", QuestionType.SA, "ready", "generated", null, 300 + no,
                stem, null, null, null, null, null, answer, null, explanation, Difficulty.MEDIUM, false, List.of());
    }

    /** 14 câu phần I (để chia hai bảng đáp án 12 + 2), 2 câu phần II, 2 câu phần III. */
    private static ExamViewDto exam() {
        List<ExamItemDto> part1 = new ArrayList<>();
        part1.add(mcq(1, "Bậc của đơn thức $2x^3$ là:", List.of("$1$", "$3$", "$0$", "$2$"), Option.B,
                "Số mũ của $x$ là $3$."));
        part1.add(mcq(2, "Biểu thức nào sau đây bằng $A$ :", MEDIUM, Option.C, "Khai triển rồi rút gọn."));
        part1.add(mcq(3, "Khẳng định nào sau đây đúng", LONG, Option.D, null));
        for (int i = 4; i <= 14; i++) {
            part1.add(mcq(i, "Tính giá trị biểu thức thứ " + i + " bằng", List.of("$1$", "$2$", "$3$", "$4$"),
                    Option.values()[i % 4], "Lời giải câu " + i + "."));
        }
        List<ExamItemDto> part2 = List.of(
                tf(1, "Cho hàm số $y = x^2$.",
                        "a) Đồ thị là parabol.\nb) Hàm số nghịch biến khi $x < 0$.\nc) Giá trị nhỏ nhất là $0$.\nd) Điểm $O$ thuộc đồ thị.",
                        TruthValue.TRUE, TruthValue.FALSE, TruthValue.TRUE, TruthValue.TRUE),
                tf(2, "Cho hàm số $y = x^2$.", "Xét từng ý theo định nghĩa.",
                        TruthValue.FALSE, TruthValue.FALSE, TruthValue.TRUE, TruthValue.TRUE));
        List<ExamItemDto> part3 = List.of(
                sa(1, "Tính $5 \\cdot 2$.", "10", "Nhân hai số."),
                sa(2, "Tính $92^2$.", "8464", "Bình phương của $92$."));
        return new ExamViewDto(14, "MT2610231200014", "Đề thử khuôn đề", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 11, 4, 8, 0), "ready", 0,
                List.of(new PartDto("I", 14, 14, part1), new PartDto("II", 2, 2, part2), new PartDto("III", 2, 2, part3)),
                "matrix");
    }

    // ---------------------------------------------------------------- dựng file (một lần cho cả lớp)

    private final Map<EquationFormat, byte[]> docx = new EnumMap<>(EquationFormat.class);
    private String pandoc;

    @BeforeAll
    void dungFile() {
        Optional<String> found = PandocTestSupport.find();
        assumeTrue(found.isPresent(), "máy không có pandoc: bỏ qua");
        pandoc = found.get();
        String markdown = ExamMarkdownBuilder.build(exam(), ExamHeader.of("Giữa kì I – Toán 9", "Toán", 9, 90), SCORING);
        for (EquationFormat format : EquationFormat.values()) {
            docx.put(format, new PandocRunner(pandoc).markdownToDocx(markdown, format).content());
        }
        saveSamples();
    }

    /** Ghi hai file mẫu vào target/exam-docx-samples để mở bằng Word xem bố cục (target/ không được commit). */
    private void saveSamples() {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of("target", "exam-docx-samples");
            java.nio.file.Files.createDirectories(dir);
            for (Map.Entry<EquationFormat, byte[]> e : docx.entrySet()) {
                java.nio.file.Files.write(dir.resolve("de-mau-" + e.getKey().name().toLowerCase() + ".docx"), e.getValue());
            }
        } catch (java.io.IOException | RuntimeException ignored) {
            // chỉ để xem tay; không ghi được thì bỏ qua
        }
    }

    // ---------------------------------------------------------------- đọc docx

    private static Document parse(byte[] docx, String part) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(
                PandocTestSupport.entry(docx, part).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static List<Element> children(Element parent, String ns, String local) {
        List<Element> out = new ArrayList<>();
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && ns.equals(e.getNamespaceURI()) && local.equals(e.getLocalName())) {
                out.add(e);
            }
        }
        return out;
    }

    private static Element child(Element parent, String local) {
        List<Element> l = children(parent, W, local);
        return l.isEmpty() ? null : l.get(0);
    }

    private static String val(Element e) {
        return e == null ? null : e.getAttributeNS(W, "val");
    }

    /** Thân tài liệu: các đoạn và bảng cấp cao nhất, theo thứ tự. */
    private static List<Element> body(Document doc) {
        Element body = (Element) doc.getElementsByTagNameNS(W, "body").item(0);
        List<Element> out = new ArrayList<>();
        for (Node c = body.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && W.equals(e.getNamespaceURI()) && ("p".equals(e.getLocalName())
                    || "tbl".equals(e.getLocalName()))) {
                out.add(e);
            }
        }
        return out;
    }

    private static boolean isParagraph(Element e) {
        return "p".equals(e.getLocalName());
    }

    /** Chữ của đoạn: w:t nguyên văn, w:tab thành ⇥, công thức (m:oMath hoặc đối tượng MathType) thành ∎. */
    private static String text(Element p) {
        StringBuilder sb = new StringBuilder();
        for (Node c = p.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (!(c instanceof Element e)) {
                continue;
            }
            if (M.equals(e.getNamespaceURI()) && ("oMath".equals(e.getLocalName()) || "oMathPara".equals(e.getLocalName()))) {
                sb.append(FORMULA);
            } else if ("r".equals(e.getLocalName())) {
                for (Node r = e.getFirstChild(); r != null; r = r.getNextSibling()) {
                    if (r instanceof Element re && W.equals(re.getNamespaceURI())) {
                        switch (re.getLocalName()) {
                            case "t" -> sb.append(re.getTextContent());
                            case "tab" -> sb.append(TAB);
                            case "object" -> sb.append(FORMULA);
                            default -> { }
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String style(Element p) {
        Element pPr = child(p, "pPr");
        return pPr == null ? null : val(child(pPr, "pStyle"));
    }

    private static boolean runIsBold(Element run) {
        Element rPr = child(run, "rPr");
        return rPr != null && child(rPr, "b") != null;
    }

    private static List<Element> runs(Element p) {
        return children(p, W, "r");
    }

    /** w:ind trong pPr của kiểu đoạn có styleId đã cho. */
    private static Element styleIndent(Document styles, String styleId) {
        NodeList list = styles.getElementsByTagNameNS(W, "style");
        for (int i = 0; i < list.getLength(); i++) {
            Element style = (Element) list.item(i);
            if (styleId.equals(style.getAttributeNS(W, "styleId"))) {
                return child(child(style, "pPr"), "ind");
            }
        }
        throw new AssertionError("không có kiểu " + styleId);
    }

    private static List<Integer> tabStops(Element p) {
        Element pPr = child(p, "pPr");
        Element tabs = pPr == null ? null : child(pPr, "tabs");
        List<Integer> out = new ArrayList<>();
        if (tabs != null) {
            for (Element t : children(tabs, W, "tab")) {
                assertEquals("left", t.getAttributeNS(W, "val"));
                out.add(Integer.parseInt(t.getAttributeNS(W, "pos")));
            }
        }
        return out;
    }

    private static Element paragraphStarting(List<Element> body, String prefix) {
        return body.stream().filter(e -> isParagraph(e) && text(e).startsWith(prefix)).findFirst()
                .orElseThrow(() -> new AssertionError("không có đoạn bắt đầu bằng: " + prefix));
    }

    private static Element paragraphEqual(List<Element> body, String text) {
        return body.stream().filter(e -> isParagraph(e) && text(e).equals(text)).findFirst()
                .orElseThrow(() -> new AssertionError("không có đoạn: " + text));
    }

    /** Các đoạn kiểu ExamOption liền sau đoạn {@code question}. */
    private static List<Element> optionParagraphsAfter(List<Element> body, Element question) {
        List<Element> out = new ArrayList<>();
        for (int i = body.indexOf(question) + 1; i < body.size() && isParagraph(body.get(i))
                && "ExamOption".equals(style(body.get(i))); i++) {
            out.add(body.get(i));
        }
        return out;
    }

    private static List<List<String>> table(Element tbl) {
        List<List<String>> rows = new ArrayList<>();
        for (Element tr : children(tbl, W, "tr")) {
            List<String> cells = new ArrayList<>();
            for (Element tc : children(tr, W, "tc")) {
                StringBuilder sb = new StringBuilder();
                for (Element p : children(tc, W, "p")) {
                    sb.append(text(p));
                }
                cells.add(sb.toString());
            }
            rows.add(cells);
        }
        return rows;
    }

    /** Các bảng nằm giữa hai đoạn (theo chữ) của thân tài liệu. */
    private static List<Element> tablesBetween(List<Element> body, String fromText, String toText) {
        int from = body.indexOf(paragraphEqual(body, fromText));
        int to = body.indexOf(paragraphEqual(body, toText));
        assertTrue(from >= 0 && to > from, fromText + " phải đứng trước " + toText);
        return body.subList(from, to).stream().filter(e -> !isParagraph(e)).toList();
    }

    // ---------------------------------------------------------------- 1. trang, phông, chân trang

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void trangA4VaLeTheoHangSo(EquationFormat format) throws Exception {
        Document doc = parse(docx.get(format), "word/document.xml");
        for (Element sect : List.of(examSection(doc), answerSection(doc))) {
            Element pgSz = child(sect, "pgSz");
            assertEquals("11906", pgSz.getAttributeNS(W, "w"), "595,3pt");
            assertEquals("16838", pgSz.getAttributeNS(W, "h"), "841,9pt");
            Element pgMar = child(sect, "pgMar");
            assertEquals("850", pgMar.getAttributeNS(W, "top"), "1,5 cm");
            assertEquals("850", pgMar.getAttributeNS(W, "bottom"), "1,5 cm");
            assertEquals("1134", pgMar.getAttributeNS(W, "left"), "2 cm");
            assertEquals("850", pgMar.getAttributeNS(W, "right"), "1,5 cm");
        }
        assertEquals(ExamPage.MARGIN_TOP_TWIPS, 850);
        assertEquals(ExamPage.MARGIN_LEFT_TWIPS, 1134);
        assertEquals(ExamPage.PAGE_WIDTH_TWIPS, 11906);
        assertEquals(595.3, ExamPage.PAGE_WIDTH_TWIPS / 20.0, 0.001);
        assertEquals(841.9, ExamPage.PAGE_HEIGHT_TWIPS / 20.0, 0.001);
    }

    /** Bộ ngắt dòng MathType đọc lề chữ từ sectPr của file xuất nên phải ra 496,1pt (A4 − 2cm − 1,5cm), không còn 468pt. */
    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void leChuCuaBoNgatDongTheoKhoMoi(EquationFormat format) throws Exception {
        assertEquals(496.1, PageGeometry.textWidthPt(DocxPackage.read(docx.get(format))), 0.001);
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void chanTrangHaiSectionTrangXTrenYSectionPages(EquationFormat format) throws Exception {
        Document doc = parse(docx.get(format), "word/document.xml");
        assertFooter(format, examSection(doc), "– Mã đề 101");
        assertFooter(format, answerSection(doc), "– Đáp án mã đề 101");
    }

    /** Chân trang của section: "Trang {PAGE}/{SECTIONPAGES} <hậu tố>" căn giữa, đúng thứ tự, không dùng NUMPAGES. */
    private void assertFooter(EquationFormat format, Element sect, String suffix) throws Exception {
        Element ref = child(sect, "footerReference");
        assertNotNull(ref, "sectPr phải trỏ tới chân trang");
        String rid = ref.getAttributeNS(R, "id");
        Document rels = parse(docx.get(format), "word/_rels/document.xml.rels");
        String target = null;
        NodeList list = rels.getElementsByTagName("Relationship");
        for (int i = 0; i < list.getLength(); i++) {
            Element rel = (Element) list.item(i);
            if (rid.equals(rel.getAttribute("Id"))) {
                target = rel.getAttribute("Target");
            }
        }
        assertNotNull(target, "quan hệ " + rid);
        String footer = PandocTestSupport.entry(docx.get(format), "word/" + target);
        int trang = footer.indexOf(">Trang <");
        int page = footer.indexOf("PAGE");
        int slash = footer.indexOf(">/<");
        int sectionPages = footer.indexOf("SECTIONPAGES");
        int code = footer.indexOf(suffix);
        assertTrue(trang > 0 && page > trang && slash > page && sectionPages > slash && code > sectionPages,
                "Trang {PAGE}/{SECTIONPAGES} " + suffix + " đúng thứ tự: " + footer);
        assertFalse(footer.contains("NUMPAGES"), "y là số trang của section, không phải của cả file");
        assertTrue(footer.contains("w:val=\"center\""), "căn giữa");
        assertTrue(footer.contains("fldChar"), "dùng trường PAGE/SECTIONPAGES");
    }

    /** Section đề kết thúc ở sectPr trong đoạn "------ HẾT ------"; section đáp án là sectPr cuối thân tài liệu. */
    private static Element examSection(Document doc) {
        Element end = paragraphEqual(body(doc), "------ HẾT ------");
        Element sect = child(child(end, "pPr"), "sectPr");
        assertNotNull(sect, "đoạn HẾT mang sectPr của section đề");
        return sect;
    }

    private static Element answerSection(Document doc) {
        return children((Element) doc.getElementsByTagNameNS(W, "body").item(0), W, "sectPr").get(0);
    }

    /** Hai section: A4 cùng lề, đáp án đánh số lại từ 1 và sang trang mới; đúng hai sectPr trong cả file. */
    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void ngatSectionTruocDapAnDanhSoLaiTu1(EquationFormat format) throws Exception {
        Document doc = parse(docx.get(format), "word/document.xml");
        assertEquals(2, doc.getElementsByTagNameNS(W, "sectPr").getLength(), "đúng hai section");
        Element exam = examSection(doc);
        Element answer = answerSection(doc);
        assertEquals(child(exam, "pgMar").getAttributes().getLength(), child(answer, "pgMar").getAttributes().getLength());
        for (String a : List.of("top", "bottom", "left", "right", "header", "footer")) {
            assertEquals(child(exam, "pgMar").getAttributeNS(W, a), child(answer, "pgMar").getAttributeNS(W, a), a);
        }
        assertNull(child(exam, "pgNumType"), "section đề đánh số từ 1 mặc định");
        assertEquals("1", child(answer, "pgNumType").getAttributeNS(W, "start"), "section đáp án đánh số lại từ 1");
        assertTrue(child(answer, "type") == null || "nextPage".equals(val(child(answer, "type"))), "sang trang mới");
        List<Element> body = body(doc);
        assertEquals(body.indexOf(paragraphEqual(body, "------ HẾT ------")) + 1,
                body.indexOf(paragraphEqual(body, ANSWER_TITLE)), "tiêu đề đáp án là đoạn đầu của section mới");
        assertEquals(496.1, PageGeometry.textWidthPt(DocxPackage.read(docx.get(format))), 0.001,
                "bộ ngắt dòng MathType đọc đúng lề");
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void phongTimesNewRoman13KhongThemeKhongMauXanh(EquationFormat format) throws Exception {
        String styles = PandocTestSupport.entry(docx.get(format), "word/styles.xml");
        assertFalse(styles.contains("asciiTheme") || styles.contains("hAnsiTheme") || styles.contains("majorHAnsi")
                || styles.contains("minorHAnsi"), "bỏ phông theme");
        // Pandoc tự thêm các kiểu tô màu mã nguồn "...Tok" (KeywordTok...) vào mọi file; đề không có khối mã nên không dùng.
        String withoutCodeHighlight = styles.replaceAll("(?s)<w:style\\s[^>]*w:styleId=\"[A-Za-z]+Tok\"[^>]*>.*?</w:style>", "");
        Matcher color = Pattern.compile("themeColor|0F4761|w:color").matcher(withoutCodeHighlight);
        assertFalse(color.find(), "bỏ màu xanh của tiêu đề: " + (color.hitEnd() ? "" : withoutCodeHighlight.substring(
                Math.max(0, color.start() - 200), Math.min(withoutCodeHighlight.length(), color.end() + 100))));
        Matcher fonts = Pattern.compile("w:ascii=\"([^\"]+)\"").matcher(styles);
        while (fonts.find()) {
            assertEquals("Times New Roman", fonts.group(1));
        }
        Matcher sizes = Pattern.compile("<w:sz w:val=\"(\\d+)\"").matcher(styles);
        while (sizes.find()) {
            assertTrue(Integer.parseInt(sizes.group(1)) <= 26, "không có cỡ chữ lớn hơn 13 (kể cả tiêu đề): " + sizes.group());
        }
        assertTrue(PandocTestSupport.normalIsTimesNewRoman13(styles));
        Matcher heading = Pattern.compile("(?s)<w:style\\s[^>]*w:styleId=\"Heading1\"[^>]*>(.*?)</w:style>").matcher(styles);
        assertTrue(heading.find());
        assertTrue(Pattern.compile("<w:b\\s*/>").matcher(heading.group(1)).find(), "tiêu đề đậm");
        assertTrue(styles.contains("w:line=\"276\""), "giãn dòng 1,15");
        assertFalse(Pattern.compile("w:(before|after)=\"(\\d+)\"").matcher(styles).results()
                .anyMatch(m -> Integer.parseInt(m.group(2)) > 60), "cách đoạn tối đa 3pt (60 twips)");
    }

    // ---------------------------------------------------------------- 2. đầu đề

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void dauDeBangHaiCotKhongVienVaODeMaCoVien(EquationFormat format) throws Exception {
        Document doc = parse(docx.get(format), "word/document.xml");
        List<Element> tables = body(doc).stream().filter(e -> !isParagraph(e)).toList();
        Element head = tables.get(0);
        assertEquals(2, children(child(head, "tblGrid"), W, "gridCol").size(), "bảng đầu đề 2 cột");
        assertEquals(ExamPage.TEXT_WIDTH_TWIPS, children(child(head, "tblGrid"), W, "gridCol").stream()
                .mapToInt(c -> Integer.parseInt(c.getAttributeNS(W, "w"))).sum(), "bảng rộng đúng bề rộng chữ");
        assertTrue(Integer.parseInt(children(child(head, "tblGrid"), W, "gridCol").get(1).getAttributeNS(W, "w")) >= 6200,
                "cột phải đủ rộng cho dòng \"Thời gian làm bài: …, không kể thời gian phát đề\"");
        for (String side : List.of("top", "left", "bottom", "right", "insideH", "insideV")) {
            assertEquals("nil", val(child(child(child(head, "tblPr"), "tblBorders"), side)), "không viền: " + side);
        }
        List<List<String>> cells = table(head);
        assertEquals(1, cells.size());
        String left = cells.get(0).get(0);
        String right = cells.get(0).get(1);
        assertTrue(left.contains("PHÒNG GD&ĐT ……………") && left.contains("TRƯỜNG THCS ……………")
                && left.contains("(Đề có") && left.contains(" trang)"), left);
        assertTrue(right.contains("ĐỀ KIỂM TRA GIỮA KÌ I") && right.contains("NĂM HỌC …… – ……")
                && right.contains("Môn: TOÁN 9")
                && right.contains("Thời gian làm bài: 90 phút, không kể thời gian phát đề"), right);
        Element leftCell = children(children(head, W, "tr").get(0), W, "tc").get(0);
        Element rightCell = children(children(head, W, "tr").get(0), W, "tc").get(1);
        List<Element> lp = children(leftCell, W, "p");
        assertTrue(runs(lp.get(1)).stream().allMatch(ExamDocxFormatTest::runIsBold), "TRƯỜNG THCS đậm");
        assertTrue(runs(lp.get(2)).stream().allMatch(ExamDocxFormatTest::runIsItalic), "(Đề có … trang) nghiêng");
        String xmlText = PandocTestSupport.documentXml(docx.get(format));
        assertTrue(xmlText.contains("SECTIONPAGES"), "số trang của phần đề là trường SECTIONPAGES");
        assertFalse(xmlText.contains("NUMPAGES"), "không đếm cả trang đáp án");
        List<Element> rp = children(rightCell, W, "p");
        assertTrue(runs(rp.get(0)).stream().allMatch(ExamDocxFormatTest::runIsBold), "ĐỀ KIỂM TRA... đậm");
        assertTrue(runs(rp.get(3)).stream().allMatch(ExamDocxFormatTest::runIsItalic), "dòng thời gian nghiêng");

        // dòng dưới: họ tên, lớp và ô "Mã đề 101" có viền
        Element second = tables.get(1);
        List<List<String>> row = table(second);
        assertEquals("Họ và tên: …………………………………  Lớp: ………  ", row.get(0).get(0));
        assertEquals("Mã đề 101", row.get(0).get(1));
        Element codeCell = children(children(second, W, "tr").get(0), W, "tc").get(1);
        Element borders = child(child(codeCell, "tcPr"), "tcBorders");
        assertNotNull(borders, "ô Mã đề có viền");
        assertEquals("single", val(child(borders, "right")), "viền bên phải");
        Element nameCell = children(children(second, W, "tr").get(0), W, "tc").get(0);
        assertTrue(child(nameCell, "tcPr") == null || child(child(nameCell, "tcPr"), "tcBorders") == null
                || "nil".equals(val(child(child(child(nameCell, "tcPr"), "tcBorders"), "right"))), "ô họ tên không viền");
    }

    // ---------------------------------------------------------------- 3. dòng hướng dẫn, HẾT

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void dongHuongDanMoiPhanDamPhanTenThuongPhanSau(EquationFormat format) throws Exception {
        List<Element> body = body(parse(docx.get(format), "word/document.xml"));
        List<String> expected = List.of(
                "PHẦN I. Câu trắc nghiệm nhiều phương án lựa chọn.| Thí sinh trả lời từ câu 1 đến câu 14. "
                        + "Mỗi câu hỏi thí sinh chỉ chọn một phương án.",
                "PHẦN II. Câu trắc nghiệm đúng sai.| Thí sinh trả lời từ câu 1 đến câu 2. "
                        + "Trong mỗi ý a), b), c), d) ở mỗi câu, thí sinh chọn đúng hoặc sai.",
                "PHẦN III. Câu trắc nghiệm trả lời ngắn.| Thí sinh trả lời từ câu 1 đến câu 2.");
        List<Element> lines = body.stream().filter(e -> isParagraph(e) && "ExamInstruction".equals(style(e))).toList();
        assertEquals(3, lines.size());
        for (int i = 0; i < 3; i++) {
            StringBuilder boldPart = new StringBuilder();
            StringBuilder plainPart = new StringBuilder();
            for (Element run : runs(lines.get(i))) {
                (runIsBold(run) ? boldPart : plainPart).append(runText(run));
            }
            assertEquals(expected.get(i), boldPart + "|" + plainPart, "bold = tên phần, thường = phần sau, cùng một đoạn");
        }
    }

    private static String runText(Element run) {
        StringBuilder sb = new StringBuilder();
        for (Element t : children(run, W, "t")) {
            sb.append(t.getTextContent());
        }
        return sb.toString();
    }

    private static boolean runIsItalic(Element run) {
        Element rPr = child(run, "rPr");
        return rPr != null && child(rPr, "i") != null;
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void cuoiDeCoDongHetCanGiuaTruocTrangDapAn(EquationFormat format) throws Exception {
        List<Element> body = body(parse(docx.get(format), "word/document.xml"));
        Element end = paragraphEqual(body, "------ HẾT ------");
        assertEquals("ExamCenter", style(end));
        assertTrue(PandocTestSupport.entry(docx.get(format), "word/styles.xml").matches(
                "(?s).*w:styleId=\"ExamCenter\".*?<w:jc w:val=\"center\".*"), "căn giữa");
        int lastQuestion = body.indexOf(paragraphStarting(body, "Câu 2.⇥Tính"));
        assertTrue(body.indexOf(end) > lastQuestion, "HẾT sau câu cuối");
        assertTrue(body.indexOf(end) < body.indexOf(paragraphEqual(body, ANSWER_TITLE)), "HẾT trước trang đáp án");
    }

    // ---------------------------------------------------------------- 4. câu hỏi

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void cauHoiThutLeTreoNhanDamCauDanSauTab(EquationFormat format) throws Exception {
        byte[] file = docx.get(format);
        List<Element> body = body(parse(file, "word/document.xml"));
        Element q1 = paragraphStarting(body, "Câu 1.⇥Bậc của đơn thức");
        assertEquals("ExamQuestion", style(q1));
        assertEquals("Câu 1.", runs(q1).get(0).getTextContent());
        assertTrue(runIsBold(runs(q1).get(0)), "nhãn Câu k. đậm");
        assertEquals("Câu 1.⇥Bậc của đơn thức " + FORMULA + " là", text(q1), "\"là:\" thành \"là\"");
        Document styles = parse(file, "word/styles.xml");
        Element question = styleIndent(styles, "ExamQuestion");
        assertEquals("1000", question.getAttributeNS(W, "left"));
        assertEquals("1000", question.getAttributeNS(W, "hanging"),
                "thụt lề treo: câu dẫn thẳng hàng sau nhãn, dòng sau thẳng chữ đầu câu dẫn");
        Element option = styleIndent(styles, "ExamOption");
        assertEquals("1320", option.getAttributeNS(W, "left"));
        assertEquals("320", option.getAttributeNS(W, "hanging"));
        assertFalse(PandocTestSupport.documentXml(file).contains("là:"), "không còn \"là:\"");
        assertEquals("Câu 2.⇥Biểu thức nào sau đây bằng " + FORMULA + " :",
                text(paragraphStarting(body, "Câu 2.⇥Biểu thức nào sau đây bằng")),
                "dấu hai chấm không đứng sau \"là\" thì giữ nguyên");
    }

    // ---------------------------------------------------------------- 5. phương án xếp cột bằng tab

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void soNganBonCotMotDongTabDungTaiW4W2Ba4W(EquationFormat format) throws Exception {
        List<Element> body = body(parse(docx.get(format), "word/document.xml"));
        List<Element> options = optionParagraphsAfter(body, paragraphStarting(body, "Câu 1.⇥Bậc của đơn thức"));
        assertEquals(1, options.size(), "4 cột = một dòng");
        assertEquals("A. ∎.⇥B. ∎.⇥C. ∎.⇥D. ∎.".replace("∎", FORMULA), text(options.get(0)));
        assertEquals(List.of(3231, 5461, 7692), tabStops(options.get(0)));
        assertTrue(runIsBold(runs(options.get(0)).get(0)) && "A.".equals(runs(options.get(0)).get(0).getTextContent()),
                "nhãn A. đậm");
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void bieuThucVuaHaiDongHaiCotTabDungTaiW2(EquationFormat format) throws Exception {
        List<Element> body = body(parse(docx.get(format), "word/document.xml"));
        List<Element> options = optionParagraphsAfter(body, paragraphStarting(body, "Câu 2.⇥Biểu thức nào"));
        assertEquals(2, options.size(), "2 cột = hai dòng A,B / C,D");
        assertEquals("A. ∎.⇥B. ∎.".replace("∎", FORMULA), text(options.get(0)));
        assertEquals("C. ∎.⇥D. ∎.".replace("∎", FORMULA), text(options.get(1)));
        assertEquals(List.of(5461), tabStops(options.get(0)));
        assertEquals(List.of(5461), tabStops(options.get(1)));
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void cauChuDaiMoiPhuongAnMotDongKhongTab(EquationFormat format) throws Exception {
        List<Element> body = body(parse(docx.get(format), "word/document.xml"));
        List<Element> options = optionParagraphsAfter(body, paragraphStarting(body, "Câu 3.⇥Khẳng định nào"));
        assertEquals(4, options.size());
        assertEquals("A. Hai đường thẳng song song thì không có điểm chung nào cả.", text(options.get(0)));
        assertEquals("D. Hai đường thẳng chéo nhau thì không nằm trong cùng một mặt phẳng.", text(options.get(3)));
        for (Element p : options) {
            assertFalse(text(p).contains(TAB), "không tab trong " + text(p));
            assertTrue(tabStops(p).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void khongDungBangDeXepCotVaKhongConMaGiuCho(EquationFormat format) throws Exception {
        String xml = PandocTestSupport.documentXml(docx.get(format));
        List<Element> body = body(parse(docx.get(format), "word/document.xml"));
        int firstOption = body.indexOf(paragraphStarting(body, "Câu 1.⇥Bậc của đơn thức"));
        int lastOption = body.indexOf(paragraphEqual(body, "------ HẾT ------"));
        assertTrue(body.subList(firstOption, lastOption).stream().noneMatch(e -> !isParagraph(e)),
                "phần đề không có bảng nào (công cụ trộn đề đọc theo tab)");
        assertFalse(xml.contains("") || xml.contains(""), "mã giữ chỗ tab đã được thay hết");
    }

    // ---------------------------------------------------------------- 6. chuẩn hóa dấu chấm

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void phuongAnVaYDungSaiCoDauChamCuoi(EquationFormat format) throws Exception {
        List<Element> body = body(parse(docx.get(format), "word/document.xml"));
        Element tf1 = paragraphStarting(body, "Câu 1.⇥Cho hàm số");
        List<Element> statements = optionParagraphsAfter(body, tf1);
        assertEquals(4, statements.size());
        assertEquals("a) Đồ thị là một parabol.", text(statements.get(0)));
        assertEquals("b) Hàm số đồng biến trên " + FORMULA + ".", text(statements.get(1)), "dấu chấm sau công thức");
        assertEquals("c) Giá trị nhỏ nhất bằng " + FORMULA + ".", text(statements.get(2)));
        assertEquals("d) Đồ thị đi qua " + FORMULA + ".", text(statements.get(3)), "đã có dấu chấm thì không thêm");
        List<Element> long3 = optionParagraphsAfter(body, paragraphStarting(body, "Câu 3.⇥Khẳng định"));
        assertTrue(text(long3.get(1)).endsWith("duy nhất."));
    }

    // ---------------------------------------------------------------- 7. đáp án

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void trangDapAnTieuDeSangTrangMoiVaBangPhanI(EquationFormat format) throws Exception {
        List<Element> body = body(parse(docx.get(format), "word/document.xml"));
        Element title = paragraphEqual(body, ANSWER_TITLE);
        Element pPr = child(title, "pPr");
        assertNull(child(pPr, "pageBreakBefore"), "sang trang mới bằng ngắt section, không thêm ngắt trang");
        assertEquals("Heading1", style(title));
        assertTrue(runs(title).stream().allMatch(ExamDocxFormatTest::runIsBold) || PandocTestSupport.entry(docx.get(format),
                "word/styles.xml").matches("(?s).*w:styleId=\"Heading1\".*?<w:b\\s*/>.*"), "đậm");
        assertTrue(PandocTestSupport.entry(docx.get(format), "word/styles.xml").matches(
                "(?s).*w:styleId=\"Heading1\".*?<w:jc w:val=\"center\".*"), "căn giữa");

        List<Element> tables = tablesBetween(body, ANSWER_TITLE, "LỜI GIẢI CHI TIẾT");
        List<List<String>> t1 = table(tables.get(0));
        List<String> expectedHeader = new ArrayList<>(List.of("Câu"));
        List<String> expectedAnswers = new ArrayList<>(List.of("Chọn"));
        List<ExamItemDto> items = exam().parts().get(0).items();
        for (int i = 1; i <= 12; i++) {
            expectedHeader.add(Integer.toString(i));
            expectedAnswers.add(items.get(i - 1).correctOption().name());
        }
        assertEquals(2, t1.size(), "2 hàng");
        assertEquals(13, t1.get(0).size(), "N + 1 cột (12 câu + cột nhãn)");
        assertEquals(expectedHeader, t1.get(0));
        assertEquals(expectedAnswers, t1.get(1));
        for (Element tbl : tables) {
            assertNotNull(child(child(tbl, "tblPr"), "tblBorders"), "bảng có viền");
            assertEquals("single", val(child(child(child(tbl, "tblPr"), "tblBorders"), "top")));
        }
        // 14 câu: bảng thứ hai có 2 câu còn lại, cách bảng thứ nhất bằng một đoạn (Word gộp hai bảng kề nhau thành một)
        assertTrue(body.indexOf(tables.get(1)) - body.indexOf(tables.get(0)) >= 2,
                "giữa hai bảng đáp án phần I phải có đoạn ngăn cách");
        List<List<String>> t2 = table(tables.get(1));
        assertEquals(List.of("Câu", "13", "14"), t2.get(0));
        assertEquals(List.of("Chọn", items.get(12).correctOption().name(), items.get(13).correctOption().name()), t2.get(1));
        assertEquals(1, body.stream().filter(e -> isParagraph(e) && text(e).equals(
                "Mỗi câu trả lời đúng thí sinh được 0,25 điểm.")).count(), "điểm phần I lấy từ cấu hình");
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void bangDapAnPhanIICauYDapAn(EquationFormat format) throws Exception {
        List<Element> body = body(parse(docx.get(format), "word/document.xml"));
        List<Element> tables = tablesBetween(body, ANSWER_TITLE, "LỜI GIẢI CHI TIẾT");
        List<List<String>> t = table(tables.get(2));
        assertEquals(List.of("Câu", "Ý", "Đáp án"), t.get(0));
        assertEquals(9, t.size(), "tiêu đề + 2 câu × 4 ý");
        assertEquals(List.of("1", "a", "Đúng"), t.get(1));
        assertEquals(List.of("", "b", "Sai"), t.get(2), "câu 1 hàng b (ô Câu gộp dọc)");
        assertEquals(List.of("", "c", "Đúng"), t.get(3));
        assertEquals(List.of("", "d", "Đúng"), t.get(4));
        assertEquals(List.of("2", "a", "Sai"), t.get(5));
        assertEquals(List.of("", "d", "Đúng"), t.get(8));
        for (List<String> row : t) {
            assertEquals(3, row.size());
        }
        String joined = body.stream().filter(ExamDocxFormatTest::isParagraph).map(ExamDocxFormatTest::text)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(joined.contains("đúng 1 ý được 0,1 điểm") && joined.contains("đúng 2 ý được 0,25 điểm")
                && joined.contains("đúng 3 ý được 0,5 điểm") && joined.contains("đúng cả 4 ý được 1 điểm"), joined);
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void bangDapAnPhanIIIHaiHangSoDapAn(EquationFormat format) throws Exception {
        List<Element> body = body(parse(docx.get(format), "word/document.xml"));
        List<Element> tables = tablesBetween(body, ANSWER_TITLE, "LỜI GIẢI CHI TIẾT");
        List<List<String>> t = table(tables.get(3));
        assertEquals(2, t.size());
        assertEquals(List.of("Câu", "1", "2"), t.get(0));
        assertEquals(List.of("Đáp án", "10", "8464"), t.get(1));
        assertTrue(body.stream().anyMatch(e -> isParagraph(e) && text(e).equals(
                "Mỗi câu trả lời đúng thí sinh được 0,5 điểm.")), "điểm phần III theo cấu hình");
        assertEquals(4, tables.size(), "I (2 bảng) + II + III");
    }

    // ---------------------------------------------------------------- 8. lời giải chi tiết

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void loiGiaiChiTietNhanDamTuDuLieuDapAn(EquationFormat format) throws Exception {
        List<Element> body = body(parse(docx.get(format), "word/document.xml"));
        int start = body.indexOf(paragraphEqual(body, "LỜI GIẢI CHI TIẾT"));
        assertEquals("Heading1", style(body.get(start)));
        List<Element> sol = body.subList(start + 1, body.size());

        Element c1 = sol.stream().filter(e -> isParagraph(e) && text(e).equals("Câu 1. Chọn B.")).findFirst().orElseThrow();
        assertTrue(runs(c1).stream().allMatch(ExamDocxFormatTest::runIsBold), "nhãn đậm");
        assertEquals("Số mũ của " + FORMULA + " là " + FORMULA + ".", text(sol.get(sol.indexOf(c1) + 1)),
                "lời giải ở đoạn sau");
        // câu 3 không có lời giải: chỉ dòng nhãn, đoạn kế tiếp là nhãn câu 4
        Element c3 = sol.stream().filter(e -> isParagraph(e) && text(e).equals("Câu 3. Chọn D.")).findFirst().orElseThrow();
        assertEquals("Câu 4. Chọn " + exam().parts().get(0).items().get(3).correctOption().name() + ".",
                text(sol.get(sol.indexOf(c3) + 1)));

        // phần II: tách được theo ý (câu 1) và không tách được (câu 2)
        int p2 = sol.indexOf(sol.stream().filter(e -> isParagraph(e) && text(e).equals("Câu 1.")).findFirst().orElseThrow());
        assertEquals("a) Đúng.", text(sol.get(p2 + 1)));
        assertTrue(runs(sol.get(p2 + 1)).stream().allMatch(ExamDocxFormatTest::runIsBold));
        assertEquals("Đồ thị là parabol.", text(sol.get(p2 + 2)));
        assertEquals("b) Sai.", text(sol.get(p2 + 3)));
        assertEquals("Hàm số nghịch biến khi " + FORMULA + ".", text(sol.get(p2 + 4)));
        int p2b = sol.indexOf(sol.stream().filter(e -> isParagraph(e) && text(e).equals("Câu 2.")).findFirst().orElseThrow());
        assertEquals(List.of("a) Sai.", "b) Sai.", "c) Đúng.", "d) Đúng.", "Xét từng ý theo định nghĩa."),
                sol.subList(p2b + 1, p2b + 6).stream().map(ExamDocxFormatTest::text).toList(),
                "không tách được: 4 dòng nhãn rồi cả đoạn lời giải");

        // phần III
        Element s1 = sol.stream().filter(e -> isParagraph(e) && text(e).equals("Câu 1. Đáp án: 10.")).findFirst().orElseThrow();
        assertTrue(runs(s1).stream().allMatch(ExamDocxFormatTest::runIsBold));
        assertEquals("Nhân hai số.", text(sol.get(sol.indexOf(s1) + 1)));
        assertTrue(sol.stream().anyMatch(e -> isParagraph(e) && text(e).equals("Câu 2. Đáp án: 8464.")));
    }

    // ---------------------------------------------------------------- 9. MathType căn dòng

    /**
     * Đối tượng MathType trong dòng phải thẳng đường chân chữ: ảnh xem trước có phần dưới baseline (depth, kể cả lề dưới
     * của ảnh) nên mỗi đối tượng được hạ w:position âm = −round(2 × depth) nửa điểm.
     */
    @Test
    void doiTuongMathTypeTrongDongHaXuongTheoPhanDuoiBaseline() throws Exception {
        assumeTrue(pandoc != null);
        byte[] file = new PandocRunner(pandoc).markdownToDocx("Cho $\\frac{a}{b}$ và $x$ và $y_1$ nữa.\n",
                EquationFormat.MATHTYPE).content();
        Document doc = parse(file, "word/document.xml");
        List<String> latex = List.of("\\frac{a}{b}", "x", "y_1");
        NodeList objects = doc.getElementsByTagNameNS(W, "object");
        assertEquals(3, objects.getLength());
        for (int i = 0; i < 3; i++) {
            Element run = (Element) objects.item(i).getParentNode();
            Element position = child(child(run, "rPr"), "position");
            assertNotNull(position, "công thức " + latex.get(i) + " phải có w:position");
            int expected = -(int) Math.round(PreviewRenderer.render(latex.get(i)).depthPt() * 2);
            assertEquals(expected, Integer.parseInt(val(position)), "w:position của " + latex.get(i));
            assertTrue(expected < 0, "hạ xuống (âm): " + expected);
        }
        int frac = -Integer.parseInt(val(child(child((Element) objects.item(0).getParentNode(), "rPr"), "position")));
        int plain = -Integer.parseInt(val(child(child((Element) objects.item(1).getParentNode(), "rPr"), "position")));
        assertTrue(frac > plain, "phân số có phần dưới baseline sâu hơn chữ x: " + frac + " vs " + plain);
    }

    // ---------------------------------------------------------------- bộ ngắt dòng MathType theo khổ A4

    /** Công thức rộng trong (468pt, 496,1pt] vừa lề chữ A4 nên không bị ngắt hay thu nhỏ (trước đây lề 468pt sẽ ngắt). */
    @Test
    void congThucRong480ptVuaLeA4KhongBiNgatHayThuNho() throws Exception {
        assumeTrue(pandoc != null);
        String latex = formulaOfWidth(478, 494);
        double width = PreviewRenderer.widthPt(latex);
        PandocRunner.Docx out = new PandocRunner(pandoc).markdownToDocx("$" + latex + "$\n", EquationFormat.MATHTYPE);
        assertEquals(0, out.equationsKeptOmml());
        assertEquals(0, out.equationsScaled(), "vừa lề A4 (496,1pt) nên không thu nhỏ: " + width);
        Document doc = parse(out.content(), "word/document.xml");
        assertEquals(1, doc.getElementsByTagNameNS(W, "object").getLength(), "một đối tượng, không ngắt dòng");
        Element shape = (Element) doc.getElementsByTagNameNS("urn:schemas-microsoft-com:vml", "shape").item(0);
        Matcher m = Pattern.compile("width:([0-9.]+)pt").matcher(shape.getAttribute("style"));
        assertTrue(m.find());
        assertEquals(width, Double.parseDouble(m.group(1)), 0.02, "hiển thị đủ kích thước gốc");
    }

    /** Công thức rộng hơn 496,1pt vẫn bị ngắt/thu nhỏ để không vượt lề mới. */
    @Test
    void congThucRongHonLeA4VanBiNgat() throws Exception {
        assumeTrue(pandoc != null);
        String f = formulaOfWidth(520, 560);
        String latex = f + " = " + f;
        PandocRunner.Docx out = new PandocRunner(pandoc).markdownToDocx("$" + latex + "$\n", EquationFormat.MATHTYPE);
        Document doc = parse(out.content(), "word/document.xml");
        NodeList shapes = doc.getElementsByTagNameNS("urn:schemas-microsoft-com:vml", "shape");
        assertTrue(shapes.getLength() >= 2, "ngắt tại dấu =");
        for (int i = 0; i < shapes.getLength(); i++) {
            Matcher m = Pattern.compile("width:([0-9.]+)pt").matcher(((Element) shapes.item(i)).getAttribute("style"));
            assertTrue(m.find());
            assertTrue(Double.parseDouble(m.group(1)) <= 496.1 + 0.05, "vượt lề: " + m.group(1));
        }
    }

    /** Phân số có tử là chuỗi chữ cái, tăng dần cho tới khi ảnh xem trước rộng trong [min, max] pt. */
    private static String formulaOfWidth(double min, double max) {
        String all = "abcdefghijklmnopqrstuvwxyz".repeat(12);
        for (int n = 1; n <= all.length(); n++) {
            String latex = "\\frac{" + all.substring(0, n) + "}{xyz}";
            double w = PreviewRenderer.widthPt(latex);
            if (w >= min) {
                assertTrue(w <= max, "không dựng được công thức rộng trong [" + min + "," + max + "]: " + w);
                return latex;
            }
        }
        throw new IllegalStateException("không dựng được công thức rộng " + min);
    }
}
