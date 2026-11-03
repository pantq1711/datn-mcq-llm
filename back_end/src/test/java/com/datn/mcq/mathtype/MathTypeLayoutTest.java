package com.datn.mcq.mathtype;

import com.datn.mcq.exam.export.EquationFormat;
import com.datn.mcq.exam.export.PandocRunner;
import com.datn.mcq.exam.export.PandocTestSupport;
import com.datn.mcq.mathtype.mtef.OmmlToMtef;
import com.datn.mcq.mathtype.preview.PreviewRenderer;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.datn.mcq.mathtype.MtefTestSupport.M;
import static com.datn.mcq.mathtype.MtefTestSupport.O;
import static com.datn.mcq.mathtype.MtefTestSupport.R;
import static com.datn.mcq.mathtype.MtefTestSupport.W;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Công thức rộng hơn lề chữ (xuất MathType): đối tượng OLE không tự xuống dòng nên bộ chuyển phải ngắt dòng
 * (quan hệ cấp ngoài cùng, rồi +/−) hoặc thu nhỏ đối tượng; mọi dòng đều là OLE, không giữ Equation vì lý do bố cục.
 * docx dựng tay (OMML giống pandoc) kèm LaTeX tương ứng, nên không cần pandoc.
 */
class MathTypeLayoutTest {

    private static final String V = "urn:schemas-microsoft-com:vml";
    private static final double EPS = 0.05;

    // ---------------------------------------------------------------- dựng công thức (LaTeX + OMML cùng cấu trúc)

    private record Fx(String latex, String omml) {
        Fx plus(Fx next) {
            return new Fx(latex + " " + next.latex, omml + next.omml);
        }
    }

    private static Fx runs(String letters) {
        StringBuilder omml = new StringBuilder();
        for (char c : letters.toCharArray()) {
            omml.append("<m:r><m:t>").append(c).append("</m:t></m:r>");
        }
        return new Fx(letters, omml.toString());
    }

    private static Fx op(String latex, String ch) {
        return new Fx(latex, "<m:r><m:rPr><m:sty m:val=\"p\"/></m:rPr><m:t>" + ch + "</m:t></m:r>");
    }

    private static Fx frac(Fx num, Fx den) {
        return new Fx("\\frac{" + num.latex + "}{" + den.latex + "}", "<m:f><m:fPr><m:type m:val=\"bar\"/></m:fPr><m:num>"
                + num.omml + "</m:num><m:den>" + den.omml + "</m:den></m:f>");
    }

    /** Phân số có tử là chuỗi chữ cái, tăng dần cho tới khi ảnh xem trước rộng ít nhất {@code targetPt}. */
    private static Fx term(double targetPt) {
        String all = "abcdefghijklmnopqrstuvwxyz".repeat(12);
        for (int n = 1; n <= all.length(); n++) {
            Fx f = frac(runs(all.substring(0, n)), runs("xyz"));
            if (PreviewRenderer.render(f.latex).widthPt() >= targetPt) {
                return f;
            }
        }
        throw new IllegalStateException("không dựng được phân số rộng " + targetPt);
    }

    private static Fx eq() {
        return op("=", "=");
    }

    private static Fx plus() {
        return op("+", "+");
    }

    private static Fx minus() {
        return op("-", "−");
    }

    private static Fx cat(Fx... parts) {
        Fx out = parts[0];
        for (int i = 1; i < parts.length; i++) {
            out = out.plus(parts[i]);
        }
        return out;
    }

    private static double width(Fx f) {
        return PreviewRenderer.render(f.latex).widthPt();
    }

    // ---------------------------------------------------------------- dựng docx

    private static final String PGSZ_DEFAULT_MARGIN = "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/>"
            + "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/></w:sectPr>";

    private static String math(Fx f) {
        return "<m:oMath>" + f.omml + "</m:oMath>";
    }

    private static String text(String s) {
        return "<w:r><w:t xml:space=\"preserve\">" + s + "</w:t></w:r>";
    }

    private static String para(String pPr, String inner) {
        return "<w:p><w:pPr>" + pPr + "</w:pPr>" + inner + "</w:p>";
    }

    private static byte[] docx(String body, String sectPr, String stylesXml) {
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"" + W + "\" xmlns:m=\"" + M + "\" xmlns:r=\"" + R + "\"><w:body>" + body + sectPr
                + "</w:body></w:document>";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            put(z, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            put(z, "word/_rels/document.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>");
            put(z, "word/document.xml", document);
            if (stylesXml != null) {
                put(z, "word/styles.xml", stylesXml);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bos.toByteArray();
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static Document documentOf(byte[] docx) {
        return MtefTestSupport.parse(MtefTestSupport.unzip(docx).get("word/document.xml"));
    }

    /** Bề rộng (pt) hiển thị của từng v:shape, theo thứ tự trong tài liệu. */
    private static List<Double> shapeWidths(Document doc) {
        List<Double> out = new ArrayList<>();
        NodeList shapes = doc.getElementsByTagNameNS(V, "shape");
        Pattern p = Pattern.compile("width:([0-9.]+)pt");
        for (int i = 0; i < shapes.getLength(); i++) {
            Matcher m = p.matcher(((Element) shapes.item(i)).getAttribute("style"));
            assertTrue(m.find(), "style của v:shape có width");
            out.add(Double.parseDouble(m.group(1)));
        }
        return out;
    }

    private static List<Element> paragraphs(Document doc) {
        List<Element> out = new ArrayList<>();
        NodeList ps = doc.getElementsByTagNameNS(W, "p");
        for (int i = 0; i < ps.getLength(); i++) {
            out.add((Element) ps.item(i));
        }
        return out;
    }

    private static int objectsIn(Element p) {
        return p.getElementsByTagNameNS(W, "object").getLength();
    }

    private static String textOf(Element p) {
        StringBuilder sb = new StringBuilder();
        NodeList ts = p.getElementsByTagNameNS(W, "t");
        for (int i = 0; i < ts.getLength(); i++) {
            sb.append(ts.item(i).getTextContent());
        }
        return sb.toString();
    }

    /** Mã Unicode của ký tự đầu tiên trong MTEF của từng đối tượng OLE, theo thứ tự trong tài liệu. */
    private static List<String> firstCharOfEachOle(byte[] docx) {
        Map<String, byte[]> parts = MtefTestSupport.unzip(docx);
        Document doc = MtefTestSupport.parse(parts.get("word/document.xml"));
        Map<String, String> target = new java.util.HashMap<>();
        NodeList rels = MtefTestSupport.parse(parts.get("word/_rels/document.xml.rels")).getDocumentElement().getChildNodes();
        for (int i = 0; i < rels.getLength(); i++) {
            if (rels.item(i) instanceof Element rel) {
                target.put(rel.getAttribute("Id"), "word/" + rel.getAttribute("Target"));
            }
        }
        List<String> out = new ArrayList<>();
        NodeList oles = doc.getElementsByTagNameNS(O, "OLEObject");
        for (int i = 0; i < oles.getLength(); i++) {
            byte[] mtef = MtefTestSupport.equationNative(
                    parts.get(target.get(((Element) oles.item(i)).getAttributeNS(R, "id"))));
            out.add(MtefTestSupport.dump(mtef).stream().filter(l -> l.strip().startsWith("CHAR")).findFirst().orElse("(không có)"));
        }
        return out;
    }

    // ---------------------------------------------------------------- tầng 1: quan hệ cấp ngoài cùng

    @Test
    void congThucRongNgatTaiMoiDauQuanHeCapNgoaiCungMoiDongMotDoiTuong() {
        Fx t = term(300);
        Fx f = cat(t, eq(), t, eq(), t);
        assertTrue(width(f) > 468 + 100, "tiền điều kiện: công thức rộng hơn lề, " + width(f));
        assertTrue(width(t) <= 468 && width(cat(eq(), t)) <= 468, "tiền điều kiện: từng đoạn vừa lề");

        byte[] in = docx(para("<w:pStyle w:val=\"BodyText\"/>", math(f)), PGSZ_DEFAULT_MARGIN, null);
        MathTypeDocxConverter.Result r = MathTypeDocxConverter.convert(in, List.of(f.latex));

        assertEquals(0, r.keptOmml(), "không giữ Equation vì lý do bố cục: " + r.notes());
        assertEquals(0, r.scaled(), "ngắt được thì không thu nhỏ");
        assertEquals(1, r.converted(), "một công thức (ba dòng)");
        Document doc = documentOf(r.docx());
        assertEquals(0, doc.getElementsByTagNameNS(M, "oMath").getLength(), "toàn OLE");
        List<Element> ps = paragraphs(doc);
        assertEquals(3, ps.size(), "mỗi đoạn công thức một dòng (một đoạn văn) riêng");
        for (Element p : ps) {
            assertEquals(1, objectsIn(p));
            assertTrue(textOf(p).isBlank());
        }
        for (double w : shapeWidths(doc)) {
            assertTrue(w <= 468 + EPS, "dòng rộng " + w + "pt vượt lề 468pt");
        }
        List<String> first = firstCharOfEachOle(r.docx());
        assertFalse(first.get(0).contains("U+003D"), "dòng 1 không bắt đầu bằng dấu quan hệ: " + first);
        assertTrue(first.get(1).contains("U+003D") && first.get(2).contains("U+003D"),
                "từ dòng 2 bắt đầu bằng dấu quan hệ: " + first);
    }

    // ---------------------------------------------------------------- tầng 2: + / −

    @Test
    void tongDaiKhongCoQuanHeNgatTaiDauCongTru() {
        Fx t = term(250);
        Fx f = cat(t, plus(), t, plus(), t, plus(), t, plus(), t);
        assertTrue(width(f) > 468 * 2);

        MathTypeDocxConverter.Result r = MathTypeDocxConverter.convert(
                docx(para("", math(f)), PGSZ_DEFAULT_MARGIN, null), List.of(f.latex));

        assertEquals(0, r.keptOmml(), r.notes().toString());
        Document doc = documentOf(r.docx());
        assertEquals(5, paragraphs(doc).size());
        assertEquals(5, doc.getElementsByTagNameNS(O, "OLEObject").getLength());
        for (double w : shapeWidths(doc)) {
            assertTrue(w <= 468 + EPS, w + "pt vượt lề");
        }
        List<String> first = firstCharOfEachOle(r.docx());
        assertFalse(first.get(0).contains("U+002B"), first.toString());
        for (int i = 1; i < 5; i++) {
            assertTrue(first.get(i).contains("U+002B"), "dòng " + (i + 1) + " bắt đầu bằng +: " + first);
        }
    }

    @Test
    void dauAmDungDauKhongBiNgat() {
        Fx t = term(250);
        Fx f = cat(minus(), t, minus(), t, minus(), t, minus(), t); // −T − T − T − T: 3 dấu trừ hai ngôi
        assertTrue(width(f) > 468 * 2);

        MathTypeDocxConverter.Result r = MathTypeDocxConverter.convert(
                docx(para("", math(f)), PGSZ_DEFAULT_MARGIN, null), List.of(f.latex));

        Document doc = documentOf(r.docx());
        assertEquals(4, paragraphs(doc).size(), "dấu − đứng đầu đi liền với số hạng đầu, không thành dòng riêng");
        assertEquals(0, r.keptOmml());
    }

    // ---------------------------------------------------------------- tầng 3: thu nhỏ

    @Test
    void khoiKhongNgatDuocThuNhoNhungVanLaDoiTuongOle() {
        Fx f = term(700);
        assertTrue(width(f) > 468 + 100);

        MathTypeDocxConverter.Result r = MathTypeDocxConverter.convert(
                docx(para("", math(f)), PGSZ_DEFAULT_MARGIN, null), List.of(f.latex));

        assertEquals(0, r.keptOmml());
        assertEquals(1, r.scaled(), "một công thức bị thu nhỏ");
        Document doc = documentOf(r.docx());
        assertEquals(0, doc.getElementsByTagNameNS(M, "oMath").getLength());
        assertEquals(1, doc.getElementsByTagNameNS(O, "OLEObject").getLength());
        double shown = shapeWidths(doc).get(0);
        assertTrue(shown <= 468 + EPS, "đối tượng thu nhỏ còn " + shown + "pt");
        Element obj = (Element) doc.getElementsByTagNameNS(W, "object").item(0);
        double natural = Double.parseDouble(obj.getAttributeNS(W, "dxaOrig")) / 20.0;
        assertTrue(natural > 468 + 100, "kích thước gốc (dxaOrig) giữ nguyên: " + natural);
    }

    // ---------------------------------------------------------------- không đổi khi vừa lề

    @Test
    void congThucNganKhongDoi() {
        Fx f = cat(runs("x"), eq(), runs("3"));
        MathTypeDocxConverter.Result r = MathTypeDocxConverter.convert(
                docx(para("", text("Cho ") + math(f) + text(".")), PGSZ_DEFAULT_MARGIN, null), List.of(f.latex));

        assertEquals(0, r.scaled());
        Document doc = documentOf(r.docx());
        List<Element> ps = paragraphs(doc);
        assertEquals(1, ps.size(), "công thức giữa câu vẫn nằm trong đoạn");
        assertEquals(1, objectsIn(ps.get(0)));
        assertEquals("Cho .", textOf(ps.get(0)));
        double natural = Double.parseDouble(((Element) doc.getElementsByTagNameNS(W, "object").item(0))
                .getAttributeNS(W, "dxaOrig")) / 20.0;
        assertEquals(natural, shapeWidths(doc).get(0), 0.01, "không thu nhỏ");
    }

    // ---------------------------------------------------------------- giữa câu

    @Test
    void congThucQuaRongGiuaCauTachThanhCacDongChuTruocSauGiuTrongDoan() {
        Fx t = term(300);
        Fx f = cat(t, eq(), t, eq(), t);
        MathTypeDocxConverter.Result r = MathTypeDocxConverter.convert(
                docx(para("<w:pStyle w:val=\"BodyText\"/>", text("Cho ") + math(f) + text(" xong.")),
                        PGSZ_DEFAULT_MARGIN, null), List.of(f.latex));

        List<Element> ps = paragraphs(documentOf(r.docx()));
        assertEquals(5, ps.size(), "chữ trước, 3 dòng công thức, chữ sau");
        assertEquals("Cho", textOf(ps.get(0)), "khoảng trắng ở rìa đoạn được cắt");
        assertEquals(0, objectsIn(ps.get(0)));
        for (int i = 1; i <= 3; i++) {
            assertEquals(1, objectsIn(ps.get(i)), "đoạn " + i);
            assertTrue(textOf(ps.get(i)).isBlank());
        }
        assertEquals("xong.", textOf(ps.get(4)));
        assertEquals(0, objectsIn(ps.get(4)));
    }

    // ---------------------------------------------------------------- lề chữ đọc từ sectPr, trừ thụt lề đoạn

    @Test
    void leChuDocTuSectPrKhongGanCung() {
        Fx t = term(200);
        Fx f = cat(t, plus(), t); // ~410pt: vừa lề 468pt, không vừa lề hẹp
        double natural = width(f);
        assertTrue(natural > 330 && natural < 468, "tiền điều kiện " + natural);

        MathTypeDocxConverter.Result wide = MathTypeDocxConverter.convert(
                docx(para("", math(f)), PGSZ_DEFAULT_MARGIN, null), List.of(f.latex));
        assertEquals(1, paragraphs(documentOf(wide.docx())).size());

        // lề trái/phải 3000 twips: chữ rộng (12240 − 6000) / 20 = 312pt
        String narrowSect = "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/><w:pgMar w:top=\"1440\" w:right=\"3000\""
                + " w:bottom=\"1440\" w:left=\"3000\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/></w:sectPr>";
        MathTypeDocxConverter.Result narrow = MathTypeDocxConverter.convert(
                docx(para("", math(f)), narrowSect, null), List.of(f.latex));
        Document doc = documentOf(narrow.docx());
        assertEquals(2, paragraphs(doc).size());
        for (double w : shapeWidths(doc)) {
            assertTrue(w <= 312 + EPS, w + "pt vượt lề 312pt");
        }
    }

    @Test
    void khongCoPgSzThiDungMacDinhCuaWordLetterLeMotInch() {
        Fx t = term(300);
        Fx f = cat(t, eq(), t);
        MathTypeDocxConverter.Result r = MathTypeDocxConverter.convert(
                docx(para("", math(f)), "<w:sectPr/>", null), List.of(f.latex));
        Document doc = documentOf(r.docx());
        assertEquals(2, paragraphs(doc).size());
        for (double w : shapeWidths(doc)) {
            assertTrue(w <= 468 + EPS);
        }
    }

    @Test
    void thutLeDoanTruVaoBeRongKhaDung() {
        Fx t = term(200);
        Fx f = cat(t, plus(), t);
        // thụt trái 2880 twips = 144pt → còn 324pt; công thức ~410pt phải ngắt dù nhỏ hơn 468pt
        MathTypeDocxConverter.Result direct = MathTypeDocxConverter.convert(
                docx(para("<w:ind w:left=\"2880\"/>", math(f)), PGSZ_DEFAULT_MARGIN, null), List.of(f.latex));
        Document d1 = documentOf(direct.docx());
        assertEquals(2, paragraphs(d1).size());
        for (double w : shapeWidths(d1)) {
            assertTrue(w <= 324 + EPS, w + "pt vượt 324pt");
        }

        // thụt lề khai báo trong kiểu đoạn
        String styles = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:styles xmlns:w=\"" + W + "\"><w:style w:type=\"paragraph\""
                + " w:styleId=\"Thut\"><w:name w:val=\"Thut\"/><w:pPr><w:ind w:left=\"2880\" w:right=\"0\"/></w:pPr></w:style></w:styles>";
        MathTypeDocxConverter.Result styled = MathTypeDocxConverter.convert(
                docx(para("<w:pStyle w:val=\"Thut\"/>", math(f)), PGSZ_DEFAULT_MARGIN, styles), List.of(f.latex));
        assertEquals(2, paragraphs(documentOf(styled.docx())).size());
    }

    // ---------------------------------------------------------------- hồi quy từ đề 14 (pandoc thật)

    /**
     * Hai công thức rộng nhất của đề 14 (726pt và 561pt): pandoc đặt ")" làm thân của sSup ("(…)^2" thành "(", …,
     * sSup[")" mũ 2]), nên nếu bộ ngắt không đếm ngoặc đóng nằm trong thân chỉ số thì dấu "(" không bao giờ đóng và
     * công thức bị thu nhỏ thay vì ngắt tại dấu "=".
     */
    @Test
    void congThucRongCuaDe14NgatTaiDauBangKhongThuNho() throws Exception {
        Optional<String> pandoc = PandocTestSupport.find();
        assumeTrue(pandoc.isPresent(), "máy không có pandoc: bỏ qua");
        String f1 = "(3x - 2)^2 - (3x - 4)(3x + 4) + x(x - 6) = (9x^2 - 12x + 4) - (9x^2 - 16) + (x^2 - 6x) = "
                + "9x^2 - 12x + 4 - 9x^2 + 16 + x^2 - 6x = x^2 - 18x + 20";
        String f2 = "M - N = (4x^3y - 2xy^3 + 3) - (-2x^3y + 5xy^3 - 1) = 4x^3y - 2xy^3 + 3 + 2x^3y - 5xy^3 + 1 = "
                + "6x^3y - 7xy^3 + 4";
        assertTrue(PreviewRenderer.widthPt(f1) > 700 && PreviewRenderer.widthPt(f2) > 540, "tiền điều kiện: rộng hơn lề 468pt");

        PandocRunner.Docx out = new PandocRunner(pandoc.get())
                .markdownToDocx("**Câu 1.** Rút gọn $" + f1 + "$.\n\n**Câu 2.** Tính $" + f2 + "$.\n", EquationFormat.MATHTYPE);

        assertEquals(0, out.equationsKeptOmml());
        assertEquals(0, out.equationsScaled(), "ngắt được tại dấu = nên không phải thu nhỏ");
        Document doc = documentOf(out.content());
        assertEquals(0, doc.getElementsByTagNameNS(M, "oMath").getLength());
        assertTrue(doc.getElementsByTagNameNS(O, "OLEObject").getLength() >= 8, "mỗi công thức ít nhất 4 dòng");
        for (double w : shapeWidths(doc)) {
            assertTrue(w <= 468 + EPS, w + "pt vượt lề 468pt");
        }
    }

    // ---------------------------------------------------------------- P1: công thức giữa câu

    @Test
    void congThucGiuaCauNamTrongDongChuKhongKhoangTrangThua() throws Exception {
        Optional<String> pandoc = PandocTestSupport.find();
        assumeTrue(pandoc.isPresent(), "máy không có pandoc: bỏ qua");
        PandocRunner.Docx out = new PandocRunner(pandoc.get())
                .markdownToDocx("Cho $x = 3$.\n\nTính $(3)$\n", EquationFormat.MATHTYPE);
        assertEquals(0, out.equationsKeptOmml());

        List<Element> ps = paragraphs(documentOf(out.content()));
        assertEquals(2, ps.size(), "công thức không tách đoạn");
        assertEquals(1, objectsIn(ps.get(0)));
        assertEquals("Cho .", textOf(ps.get(0)), "một dấu cách trước công thức, không thêm trước dấu chấm");
        assertEquals(1, objectsIn(ps.get(1)));
        assertEquals("Tính ", textOf(ps.get(1)), "không thêm khoảng trắng sau công thức");
    }

    // ---------------------------------------------------------------- P1: \widehat / \hat

    @Test
    void muRongChoNenNhieuKyTuMuThuongChoMotKyTu() {
        byte[] wide = OmmlToMtef.convert(oMath("<m:acc><m:accPr><m:chr m:val=\"̂\"/></m:accPr><m:e>"
                + runs("ABC").omml + "</m:e></m:acc>"), true);
        List<String> wideDump = MtefTestSupport.dump(wide);
        assertTrue(wideDump.stream().anyMatch(l -> l.contains("TMPL tmHAT")), "nền ABC dùng mẫu mũ rộng: " + wideDump);

        byte[] plain = OmmlToMtef.convert(oMath("<m:acc><m:accPr><m:chr m:val=\"̂\"/></m:accPr><m:e>"
                + runs("x").omml + "</m:e></m:acc>"), true);
        List<String> plainDump = MtefTestSupport.dump(plain);
        assertFalse(plainDump.stream().anyMatch(l -> l.contains("TMPL")), "nền x dùng mũ thường: " + plainDump);
        assertTrue(plainDump.stream().anyMatch(l -> l.contains("EMBELL type=9")), plainDump.toString());
    }

    private static Element oMath(String inner) {
        return MtefTestSupport.parse(("<m:oMath xmlns:m=\"" + M + "\">" + inner + "</m:oMath>").getBytes(StandardCharsets.UTF_8))
                .getDocumentElement();
    }
}
