package com.datn.mcq.mathtype;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.datn.mcq.mathtype.MtefTestSupport.M;
import static com.datn.mcq.mathtype.MtefTestSupport.O;
import static com.datn.mcq.mathtype.MtefTestSupport.R;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hậu xử lý docx của pandoc: mỗi Equation (OMML) thành đối tượng MathType OLE, công thức chưa hỗ trợ giữ OMML. */
class MathTypeDocxConverterTest {

    private static final String V = "urn:schemas-microsoft-com:vml";
    private static final String REL_OLE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/oleObject";
    private static final String REL_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";

    @Test
    void muoiCongThucThanhMuoiDoiTuongMathType() {
        MathTypeDocxConverter.Result r = MathTypeDocxConverter.convert(
                MtefTestSupport.resource("exprs10-pandoc.docx"), MtefTestSupport.latexList("exprs10.tex.txt"));
        assertEquals(10, r.converted());
        assertEquals(0, r.keptOmml());

        Map<String, byte[]> parts = MtefTestSupport.unzip(r.docx());
        byte[] documentXml = parts.get("word/document.xml");
        assertEquals(0, MtefTestSupport.count(documentXml, M, "oMath"), "không còn Equation của Word");
        assertEquals(1, MtefTestSupport.count(documentXml, V, "shapetype"), "v:shapetype chỉ ở đối tượng đầu");

        Map<String, Element> rels = relationships(parts);
        NodeList oles = MtefTestSupport.parse(documentXml).getElementsByTagNameNS(O, "OLEObject");
        assertEquals(10, oles.getLength());
        for (int i = 0; i < oles.getLength(); i++) {
            Element ole = (Element) oles.item(i);
            assertEquals("Equation.DSMT4", ole.getAttribute("ProgID"));
            assertEquals("Embed", ole.getAttribute("Type"));
            Element rel = rels.get(ole.getAttributeNS(R, "id"));
            assertNotNull(rel, "quan hệ của OLE " + i);
            assertEquals(REL_OLE, rel.getAttribute("Type"));
            assertNotNull(parts.get("word/" + rel.getAttribute("Target")), "file " + rel.getAttribute("Target"));
        }
        NodeList images = MtefTestSupport.parse(documentXml).getElementsByTagNameNS(V, "imagedata");
        assertEquals(10, images.getLength());
        for (int i = 0; i < images.getLength(); i++) {
            Element rel = rels.get(((Element) images.item(i)).getAttributeNS(R, "id"));
            assertEquals(REL_IMAGE, rel.getAttribute("Type"));
            byte[] png = parts.get("word/" + rel.getAttribute("Target"));
            assertNotNull(png, "ảnh xem trước " + rel.getAttribute("Target"));
            assertEquals((byte) 0x89, png[0]);
            assertEquals('P', png[1], "ảnh PNG");
        }

        String types = new String(parts.get("[Content_Types].xml"), StandardCharsets.UTF_8);
        assertTrue(types.contains("Extension=\"bin\"")
                && types.contains("application/vnd.openxmlformats-officedocument.oleObject"), types);
        assertTrue(types.contains("Extension=\"png\"") && types.contains("image/png"), types);
    }

    @Test
    void congThucChuaHoTroGiuEquationCacCongThucKhacVanDoi() {
        // chars68 có \lfloor x\rfloor: ⌊ ⌋ chưa có mã hóa đã đối chiếu với MathType
        MathTypeDocxConverter.Result r = MathTypeDocxConverter.convert(
                MtefTestSupport.resource("chars68-pandoc.docx"), MtefTestSupport.latexList("chars68.tex.txt"));
        assertEquals(67, r.converted(), String.join("\n", r.notes()));
        assertEquals(1, r.keptOmml(), String.join("\n", r.notes()));
        byte[] documentXml = MtefTestSupport.unzip(r.docx()).get("word/document.xml");
        assertEquals(1, MtefTestSupport.count(documentXml, M, "oMath"));
        assertEquals(67, MtefTestSupport.count(documentXml, O, "OLEObject"));
        assertTrue(r.notes().stream().anyMatch(n -> n.startsWith("56:")), String.join("\n", r.notes()));
    }

    @Test
    void soCongThucKhacSoLatexThiNemLoiKhongDoan() {
        List<String> nine = MtefTestSupport.latexList("exprs10.tex.txt").subList(0, 9);
        byte[] docx = MtefTestSupport.resource("exprs10-pandoc.docx");
        MathTypeConversionException ex = assertThrows(MathTypeConversionException.class,
                () -> MathTypeDocxConverter.convert(docx, nine));
        assertTrue(ex.getMessage().contains("10") && ex.getMessage().contains("9"), ex.getMessage());
    }

    @Test
    void fileKhongPhaiDocxThiNemLoiCoKieu() {
        assertThrows(MathTypeConversionException.class,
                () -> MathTypeDocxConverter.convert("khong phai docx".getBytes(StandardCharsets.UTF_8), List.of()));
    }

    @Test
    void demSoEquationTrongDocx() {
        assertEquals(10, MathTypeDocxConverter.countEquations(MtefTestSupport.resource("exprs10-pandoc.docx")));
        assertEquals(0, MathTypeDocxConverter.countEquations(MtefTestSupport.resource("exprs10-mathtype.docx")),
                "file MathType không còn Equation");
        assertEquals(0, MathTypeDocxConverter.countEquations("khong phai docx".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void chayHaiLanChoCungKetQua() {
        // spike đánh số quan hệ bằng bộ đếm static: lần chạy sau có Id khác lần trước
        byte[] in = MtefTestSupport.resource("exprs10-pandoc.docx");
        List<String> latex = MtefTestSupport.latexList("exprs10.tex.txt");
        Map<String, byte[]> a = MtefTestSupport.unzip(MathTypeDocxConverter.convert(in, latex).docx());
        Map<String, byte[]> b = MtefTestSupport.unzip(MathTypeDocxConverter.convert(in, latex).docx());
        assertArrayEquals(a.get("word/document.xml"), b.get("word/document.xml"));
        assertArrayEquals(a.get("word/_rels/document.xml.rels"), b.get("word/_rels/document.xml.rels"));
    }

    private static Map<String, Element> relationships(Map<String, byte[]> parts) {
        Document rels = MtefTestSupport.parse(parts.get("word/_rels/document.xml.rels"));
        Map<String, Element> byId = new HashMap<>();
        NodeList list = rels.getDocumentElement().getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            if (list.item(i) instanceof Element e) {
                byId.put(e.getAttribute("Id"), e);
            }
        }
        return byId;
    }
}
