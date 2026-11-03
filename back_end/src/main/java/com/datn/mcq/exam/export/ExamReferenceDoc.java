package com.datn.mcq.exam.export;

import com.datn.mcq.mathtype.docx.DocxPackage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Dựng file mẫu {@code --reference-doc} của pandoc theo khuôn đề kiểm tra THCS (4/11) từ {@code export/reference.docx}
 * (reference.docx mặc định của pandoc + compatibilityMode 15). Vá trong bộ nhớ mỗi lần xuất, nên khổ trang và lề chỉ nằm ở
 * một chỗ ({@link ExamPage}) và bộ ngắt dòng MathType ({@code PageGeometry}) đọc đúng khổ đó từ sectPr của file xuất:
 * <ul>
 *   <li>trang A4, lề trên/dưới 1,5 cm, trái 2 cm, phải 1,5 cm;</li>
 *   <li>mọi kiểu dùng Times New Roman 13, kể cả tiêu đề: bỏ phông theme, bỏ màu xanh, tiêu đề đậm đen;</li>
 *   <li>giãn dòng 1,15, cách đoạn 0–3 pt;</li>
 *   <li>các kiểu của đề: câu hỏi (thụt lề treo), phương án, dòng hướng dẫn, ô bảng...;</li>
 *   <li>chân trang "Trang x/y – Mã đề 101" căn giữa (trường PAGE/NUMPAGES).</li>
 * </ul>
 */
public final class ExamReferenceDoc {

    static final String W = DocxPackage.W;
    private static final String R = DocxPackage.R;
    private static final String FOOTER_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer";
    private static final String FOOTER_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml";
    private static final String FONT = ExamPage.FONT;
    private static final String SIZE = Integer.toString(ExamPage.FONT_SIZE_PT * 2);
    /** Cách đoạn tối đa (twips) = 3 pt. */
    private static final int MAX_SPACING = ExamPage.PARAGRAPH_AFTER_TWIPS;

    private ExamReferenceDoc() {
    }

    /** @param base nội dung {@code export/reference.docx} */
    public static byte[] build(byte[] base) throws IOException {
        DocxPackage pkg = DocxPackage.read(base);
        Document styles = pkg.editable("word/styles.xml");
        if (styles == null) {
            throw new IOException("File mẫu thiếu word/styles.xml");
        }
        patchStyles(styles);
        // sectPr cuối thân tài liệu là section ĐÁP ÁN (đánh số lại từ 1, chân trang "Đáp án mã đề"); section đề do
        // ExamMarkdownBuilder đóng bằng sectPr trong đoạn HẾT, chân trang của nó do ExamSections thêm sau pandoc.
        // Id riêng: "rIdMT<n>" là Id của bộ chuyển MathType (đối tượng OLE, ảnh) và pandoc giữ nguyên Id của chân trang.
        pkg.put("word/footerAnswer.xml", footerXml(ANSWER_FOOTER_SUFFIX).getBytes(StandardCharsets.UTF_8));
        pkg.ensureOverrideContentType("/word/footerAnswer.xml", FOOTER_TYPE);
        pkg.addRelationship(ExamPage.ANSWER_FOOTER_REL_ID, FOOTER_REL, "footerAnswer.xml");
        patchSection(pkg.document());
        return pkg.write();
    }

    // ---------------------------------------------------------------- trang

    static final String EXAM_FOOTER_SUFFIX = " – Mã đề " + ExamHeader.ORIGINAL_CODE;
    static final String ANSWER_FOOTER_SUFFIX = " – Đáp án mã đề " + ExamHeader.ORIGINAL_CODE;
    static final String FOOTER_REL_TYPE = FOOTER_REL;
    static final String FOOTER_CONTENT_TYPE = FOOTER_TYPE;

    private static void patchSection(Document doc) {
        Element body = (Element) doc.getElementsByTagNameNS(W, "body").item(0);
        Element sect = fragment(doc, ExamPage.sectionProperties(ExamPage.ANSWER_FOOTER_REL_ID, true));
        Node old = null;
        for (Node c = body.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && W.equals(e.getNamespaceURI()) && "sectPr".equals(e.getLocalName())) {
                old = c;
            }
        }
        if (old != null) {
            body.replaceChild(sect, old);
        } else {
            body.appendChild(sect);
        }
    }

    /** Chân trang "Trang x/y{suffix}" căn giữa: x = PAGE, y = SECTIONPAGES (số trang của riêng section). */
    static String footerXml(String suffix) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:ftr xmlns:w=\"" + W + "\" xmlns:r=\"" + R + "\"><w:p><w:pPr><w:pStyle w:val=\"ExamFooter\"/>"
                + "<w:jc w:val=\"center\"/></w:pPr>"
                + "<w:r><w:t xml:space=\"preserve\">Trang </w:t></w:r>" + field("PAGE")
                + "<w:r><w:t>/</w:t></w:r>" + field("SECTIONPAGES")
                + "<w:r><w:t xml:space=\"preserve\">" + suffix + "</w:t></w:r></w:p></w:ftr>";
    }

    private static String field(String instruction) {
        return "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r>"
                + "<w:r><w:instrText xml:space=\"preserve\"> " + instruction + " </w:instrText></w:r>"
                + "<w:r><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:t>1</w:t></w:r>"
                + "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r>";
    }

    // ---------------------------------------------------------------- kiểu

    private static void patchStyles(Document doc) {
        Element root = doc.getDocumentElement();
        Element defaults = child(root, "docDefaults");
        Element newDefaults = fragment(doc, "<w:docDefaults><w:rPrDefault><w:rPr>" + fonts()
                + "<w:sz w:val=\"" + SIZE + "\"/><w:szCs w:val=\"" + SIZE + "\"/>"
                + "<w:lang w:val=\"vi-VN\" w:eastAsia=\"zh-CN\" w:bidi=\"ar-SA\"/></w:rPr></w:rPrDefault>"
                + "<w:pPrDefault><w:pPr><w:spacing w:before=\"0\" w:after=\"" + ExamPage.PARAGRAPH_AFTER_TWIPS
                + "\" w:line=\"" + ExamPage.LINE_SPACING + "\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>");
        if (defaults != null) {
            root.replaceChild(newDefaults, defaults);
        } else {
            root.insertBefore(newDefaults, root.getFirstChild());
        }

        NodeList styleList = root.getElementsByTagNameNS(W, "style");
        for (int i = 0; i < styleList.getLength(); i++) {
            Element style = (Element) styleList.item(i);
            sanitizeRunProperties(style);
            clampSpacing(style);
            String id = style.getAttributeNS(W, "styleId");
            if (id.matches("Heading[1-9]")) {
                makeHeading(doc, style, "Heading1".equals(id));
            }
        }
        for (String xml : customStyles()) {
            root.appendChild(fragment(doc, xml));
        }
    }

    private static String fonts() {
        return "<w:rFonts w:ascii=\"" + FONT + "\" w:eastAsia=\"" + FONT + "\" w:hAnsi=\"" + FONT + "\" w:cs=\"" + FONT
                + "\"/>";
    }

    /** Phông Times New Roman 13 cho mọi kiểu: bỏ phông theme, bỏ màu, cỡ chữ về 13. */
    private static void sanitizeRunProperties(Element style) {
        Element rPr = child(style, "rPr");
        if (rPr == null) {
            return;
        }
        Element fonts = child(rPr, "rFonts");
        if (fonts != null) {
            while (fonts.getAttributes().getLength() > 0) {
                fonts.removeAttributeNode((org.w3c.dom.Attr) fonts.getAttributes().item(0));
            }
            for (String a : new String[]{"ascii", "eastAsia", "hAnsi", "cs"}) {
                fonts.setAttributeNS(W, "w:" + a, FONT);
            }
        }
        for (Element color : children(rPr, "color")) {
            rPr.removeChild(color);
        }
        for (String size : new String[]{"sz", "szCs"}) {
            Element e = child(rPr, size);
            if (e != null) {
                e.setAttributeNS(W, "w:val", SIZE);
            }
        }
    }

    /** Cách đoạn (trước/sau) của mọi kiểu không quá 3 pt. */
    private static void clampSpacing(Element style) {
        Element pPr = child(style, "pPr");
        Element spacing = pPr == null ? null : child(pPr, "spacing");
        if (spacing == null) {
            return;
        }
        for (String a : new String[]{"before", "after"}) {
            String v = spacing.getAttributeNS(W, a);
            if (!v.isEmpty() && Integer.parseInt(v) > MAX_SPACING) {
                spacing.setAttributeNS(W, "w:" + a, Integer.toString(MAX_SPACING));
            }
        }
        spacing.removeAttributeNS(W, "beforeLines");
        spacing.removeAttributeNS(W, "afterLines");
    }

    /** Tiêu đề: đậm, đen, Times New Roman 13; tiêu đề cấp 1 (tên phần đáp án, lời giải) căn giữa. */
    private static void makeHeading(Document doc, Element style, boolean center) {
        Element rPr = child(style, "rPr");
        if (rPr == null) {
            rPr = doc.createElementNS(W, "w:rPr");
            style.appendChild(rPr);
        }
        if (child(rPr, "b") == null) {
            Element b = doc.createElementNS(W, "w:b");
            Element bCs = doc.createElementNS(W, "w:bCs");
            Element fonts = child(rPr, "rFonts");
            Node anchor = fonts == null ? rPr.getFirstChild() : fonts.getNextSibling();
            rPr.insertBefore(b, anchor);
            rPr.insertBefore(bCs, anchor);
        }
        if (center) {
            Element pPr = child(style, "pPr");
            if (pPr != null && child(pPr, "jc") == null) {
                Element jc = doc.createElementNS(W, "w:jc");
                jc.setAttributeNS(W, "w:val", "center");
                // jc đứng trước outlineLvl trong lược đồ
                pPr.insertBefore(jc, child(pPr, "outlineLvl"));
            }
        }
    }

    /** Các kiểu đoạn riêng của đề; số đo thụt lề lấy từ {@link ExamPage}. */
    private static String[] customStyles() {
        int q = ExamPage.QUESTION_INDENT_TWIPS;
        int optLeft = ExamPage.OPTION_LABEL_START_TWIPS + ExamPage.OPTION_HANGING_TWIPS;
        int after = ExamPage.PARAGRAPH_AFTER_TWIPS;
        return new String[]{
                style("ExamInstruction", "<w:keepNext/><w:spacing w:before=\"" + after + "\" w:after=\"" + after + "\"/>", ""),
                style("ExamQuestion", "<w:keepNext/><w:keepLines/><w:spacing w:before=\"" + after + "\" w:after=\"" + after
                        + "\"/><w:ind w:left=\"" + q + "\" w:hanging=\"" + q + "\"/>", ""),
                style("ExamQuestionCont", "<w:spacing w:before=\"0\" w:after=\"" + after + "\"/><w:ind w:left=\"" + q + "\"/>",
                        ""),
                style("ExamOption", "<w:keepLines/><w:spacing w:before=\"0\" w:after=\"0\"/><w:ind w:left=\"" + optLeft
                        + "\" w:hanging=\"" + ExamPage.OPTION_HANGING_TWIPS + "\"/>", ""),
                style("ExamCenter", "<w:spacing w:before=\"" + after + "\" w:after=\"" + after + "\"/><w:jc w:val=\"center\"/>",
                        ""),
                style("ExamHeaderCell", "<w:spacing w:before=\"0\" w:after=\"0\"/><w:jc w:val=\"center\"/>", ""),
                style("ExamTableCell", "<w:spacing w:before=\"20\" w:after=\"20\" w:line=\"240\" w:lineRule=\"auto\"/>"
                        + "<w:jc w:val=\"center\"/>", ""),
                style("ExamSubHeading", "<w:keepNext/><w:spacing w:before=\"" + after + "\" w:after=\"" + after + "\"/>",
                        "<w:b/><w:bCs/>"),
                style("ExamNote", "<w:spacing w:before=\"" + after + "\" w:after=\"" + after + "\"/>", ""),
                style("ExamSpacer", "<w:spacing w:before=\"0\" w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/>",
                        "<w:sz w:val=\"8\"/><w:szCs w:val=\"8\"/>"),
                style("ExamFooter", "<w:spacing w:before=\"0\" w:after=\"0\"/><w:jc w:val=\"center\"/>", ""),
        };
    }

    private static String style(String id, String pPr, String rPr) {
        return "<w:style w:type=\"paragraph\" w:customStyle=\"1\" w:styleId=\"" + id + "\"><w:name w:val=\"" + id + "\"/>"
                + "<w:basedOn w:val=\"Normal\"/><w:qFormat/><w:pPr>" + pPr + "</w:pPr>"
                + (rPr.isEmpty() ? "" : "<w:rPr>" + rPr + "</w:rPr>") + "</w:style>";
    }

    // ---------------------------------------------------------------- DOM

    /** Parse một đoạn XML (dùng tiền tố w:, r:) rồi nhập vào {@code target}. */
    private static Element fragment(Document target, String xml) {
        String wrapped = "<root xmlns:w=\"" + W + "\" xmlns:r=\"" + R + "\">" + xml + "</root>";
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document d = f.newDocumentBuilder().parse(new ByteArrayInputStream(wrapped.getBytes(StandardCharsets.UTF_8)));
            Element first = null;
            for (Node c = d.getDocumentElement().getFirstChild(); c != null && first == null; c = c.getNextSibling()) {
                if (c instanceof Element e) {
                    first = e;
                }
            }
            return (Element) target.importNode(first, true);
        } catch (Exception e) {
            throw new IllegalStateException("Đoạn XML của file mẫu không hợp lệ: " + xml, e);
        }
    }

    private static Element child(Element parent, String local) {
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && W.equals(e.getNamespaceURI()) && local.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static java.util.List<Element> children(Element parent, String local) {
        java.util.List<Element> out = new java.util.ArrayList<>();
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && W.equals(e.getNamespaceURI()) && local.equals(e.getLocalName())) {
                out.add(e);
            }
        }
        return out;
    }
}
