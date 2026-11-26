package com.datn.mcq.mathtype;

import com.datn.mcq.exam.export.EquationFormat;
import com.datn.mcq.exam.export.ExamDocxOptions;
import com.datn.mcq.exam.export.ExportContent;
import com.datn.mcq.exam.export.PandocRunner;
import com.datn.mcq.exam.export.PandocTestSupport;
import com.datn.mcq.mathtype.mtef.OmmlToMtef;
import com.datn.mcq.mathtype.preview.PreviewRenderer;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Công thức MathType theo cỡ chữ của đề (26/11, {@code fontSize=14}): EQN_PREFS của MTEF ghi cỡ Full 14pt (chỉ số dưới 8,
 * chỉ số dưới cấp 2 6, ký hiệu 21, ký hiệu nhỏ 14 theo đúng tỉ lệ MathType ghi ở cỡ 12: 7, 5, 18, 12) để bấm đúp sửa trong
 * MathType vẫn cỡ 14; ảnh xem trước dựng ở 14pt nên kích thước hiển thị lớn theo tỉ lệ 14/12. Cỡ 12 giữ đúng byte cũ.
 */
class MathTypeFontSizeTest {

    /** Dãy cỡ (nibble: đơn vị 2 = pt, chữ số, F kết thúc) của 5 cỡ đầu trong EQN_PREFS. */
    private static final String SIZES_12 = "212f27f25f218f212f";
    private static final String SIZES_14 = "214f28f26f221f214f";

    private static Element oMath() throws Exception {
        String xml = "<m:oMath xmlns:m=\"" + MtefTestSupport.M + "\"><m:sSup><m:e><m:r><m:t>x</m:t></m:r></m:e>"
                + "<m:sup><m:r><m:t>2</m:t></m:r></m:sup></m:sSup></m:oMath>";
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
    }

    @Test
    void eqnPrefsTheoCoChu() throws Exception {
        String at12 = HexFormat.of().formatHex(OmmlToMtef.convert(oMath(), true, 12f));
        String at14 = HexFormat.of().formatHex(OmmlToMtef.convert(oMath(), true, 14f));
        assertTrue(at12.contains("1200" + "08" + SIZES_12), "cỡ 12 như MathType 7.12 ghi");
        assertTrue(at14.contains("1200" + "08" + SIZES_14), "cỡ 14: Full 14, Sub 8, Sub2 6, Sym 21, SubSym 14");
        assertFalse(at14.contains(SIZES_12));
        assertEquals(HexFormat.of().formatHex(OmmlToMtef.convert(oMath(), true)), at12, "mặc định là cỡ 12, đúng byte cũ");
        assertEquals(at12.length(), at14.length(), "cùng độ dài: chỉ đổi chữ số cỡ");
    }

    @Test
    void anhXemTruocTheoCoChu() {
        for (String latex : List.of("x^2 + 1", "\\frac{1}{2}", "\\sqrt{3}")) {
            PreviewRenderer.Preview p12 = PreviewRenderer.render(latex, 12f);
            PreviewRenderer.Preview p14 = PreviewRenderer.render(latex, 14f);
            assertEquals(14.0 / 12, p14.heightPt() / p12.heightPt(), 0.05, "chiều cao " + latex);
            assertEquals(14.0 / 12, p14.widthPt() / p12.widthPt(), 0.05, "bề rộng " + latex);
            assertEquals(p14.widthPt(), PreviewRenderer.widthPt(latex, 14f), 1e-9, "widthPt khớp render");
            assertEquals(PreviewRenderer.widthPt(latex), PreviewRenderer.widthPt(latex, 12f), 1e-9, "mặc định 12");
        }
    }

    private static List<Double> shapeHeights(String xml) {
        List<Double> out = new ArrayList<>();
        Matcher m = Pattern.compile("style=\"width:([0-9.]+)pt;height:([0-9.]+)pt").matcher(xml);
        while (m.find()) {
            out.add(Double.parseDouble(m.group(2)));
        }
        return out;
    }

    @Test
    void docxMathTypeCo14LonTheoTiLe() throws Exception {
        Optional<String> pandoc = PandocTestSupport.find();
        assumeTrue(pandoc.isPresent(), "máy không có pandoc");
        String md = "Tính $x^2 + 1$ và $\\frac{1}{2}$.\n";
        PandocRunner runner = new PandocRunner(pandoc.get());
        String xml12 = PandocTestSupport.documentXml(runner.markdownToDocx(md, EquationFormat.MATHTYPE,
                new ExamDocxOptions("101", ExportContent.EXAM, false, 12)).content());
        String xml14 = PandocTestSupport.documentXml(runner.markdownToDocx(md, EquationFormat.MATHTYPE,
                new ExamDocxOptions("101", ExportContent.EXAM, false, 14)).content());
        List<Double> h12 = shapeHeights(xml12);
        List<Double> h14 = shapeHeights(xml14);
        assertEquals(2, h12.size(), xml12);
        assertEquals(2, h14.size());
        for (int i = 0; i < 2; i++) {
            assertEquals(14.0 / 12, h14.get(i) / h12.get(i), 0.05, "đối tượng MathType " + i);
        }
    }
}
