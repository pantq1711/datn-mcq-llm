package com.datn.mcq.mathtype.docx;

import static com.datn.mcq.mathtype.docx.DocxPackage.W;

import java.util.HashSet;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Bề rộng chữ có thể dùng của một đoạn: khổ giấy − lề trái − lề phải (sectPr cuối của thân tài liệu, vốn do pandoc chép
 * từ reference.docx) − thụt lề trái/phải của đoạn (khai báo trực tiếp, trong cấp độ danh sách đánh số, hoặc trong kiểu
 * đoạn và các kiểu cha của nó).
 *
 * <p>sectPr không khai báo pgSz/pgMar (reference.docx của pandoc là vậy) thì Word dùng mặc định của chính nó:
 * Letter 12240 × 15840 twips, lề 1440 twips (1 inch) → chữ rộng 468pt.
 */
public final class PageGeometry {

    static final double DEFAULT_PAGE_TWIPS = 12240;
    static final double DEFAULT_MARGIN_TWIPS = 1440;
    /** Sàn để thụt lề bất thường không làm bề rộng khả dụng về 0 hoặc âm. */
    public static final double MIN_WIDTH_PT = 72;

    private PageGeometry() {
    }

    /** Bề rộng chữ (pt) giữa hai lề của trang. */
    public static double textWidthPt(DocxPackage pkg) {
        double page = DEFAULT_PAGE_TWIPS;
        double left = DEFAULT_MARGIN_TWIPS;
        double right = DEFAULT_MARGIN_TWIPS;
        double gutter = 0;
        Element sect = lastBodySectPr(pkg.document());
        if (sect != null) {
            Element pgSz = child(sect, "pgSz");
            Element pgMar = child(sect, "pgMar");
            page = twips(pgSz, "w", page);
            left = twips(pgMar, "left", left);
            right = twips(pgMar, "right", right);
            gutter = twips(pgMar, "gutter", 0);
        }
        return Math.max(MIN_WIDTH_PT, (page - left - right - gutter) / 20.0);
    }

    /** Bề rộng (pt) còn lại cho công thức trong đoạn {@code p}: bề rộng chữ trừ thụt lề trái và phải của đoạn. */
    public static double availableWidthPt(DocxPackage pkg, Element p) {
        double[] ind = indentTwips(pkg, p);
        return Math.max(MIN_WIDTH_PT, textWidthPt(pkg) - (ind[0] + ind[1]) / 20.0);
    }

    /** Thụt lề trái và phải của đoạn, đơn vị twips (0 nếu không khai báo). */
    public static double[] indentTwips(DocxPackage pkg, Element p) {
        double[] all = indentAllTwips(pkg, p);
        return new double[]{all[0], all[1]};
    }

    /**
     * Độ treo (w:hanging) của đoạn, đơn vị twips (0 nếu không có): dòng đầu bắt đầu ở (thụt lề trái − độ treo). Đoạn mới tách
     * ra từ đoạn có thụt lề treo phải bỏ độ treo, nếu không dòng đầu của chữ nối tiếp lùi ra khỏi chỗ thẳng hàng.
     */
    public static double hangingTwips(DocxPackage pkg, Element p) {
        return indentAllTwips(pkg, p)[2];
    }

    /** [trái, phải, treo] theo thứ tự ưu tiên: khai báo trực tiếp, cấp độ danh sách, kiểu đoạn và kiểu cha. */
    private static double[] indentAllTwips(DocxPackage pkg, Element p) {
        Double[] ind = new Double[3];
        Element pPr = child(p, "pPr");
        if (pPr != null) {
            fill(ind, child(pPr, "ind"));
            fill(ind, numberingIndent(pkg, child(pPr, "numPr")));
        }
        String styleId = pPr == null ? null : val(child(pPr, "pStyle"));
        Document styles = pkg.xml("word/styles.xml");
        if (styles != null) {
            if (styleId == null) {
                styleId = defaultParagraphStyle(styles);
            }
            Set<String> seen = new HashSet<>();
            while (styleId != null && seen.add(styleId)) {
                Element style = style(styles, styleId);
                if (style == null) {
                    break;
                }
                Element stylePPr = child(style, "pPr");
                if (stylePPr != null) {
                    fill(ind, child(stylePPr, "ind"));
                    fill(ind, numberingIndent(pkg, child(stylePPr, "numPr")));
                }
                styleId = val(child(style, "basedOn"));
            }
        }
        return new double[]{ind[0] == null ? 0 : ind[0], ind[1] == null ? 0 : ind[1], ind[2] == null ? 0 : ind[2]};
    }

    // ---------------------------------------------------------------- chi tiết

    private static Element lastBodySectPr(Document doc) {
        NodeList bodies = doc.getElementsByTagNameNS(W, "body");
        if (bodies.getLength() == 0) {
            return null;
        }
        Element found = null;
        for (Node c = bodies.item(0).getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && W.equals(e.getNamespaceURI()) && "sectPr".equals(e.getLocalName())) {
                found = e;
            }
        }
        return found;
    }

    /** Thụt lề (w:ind) của cấp độ danh sách mà w:numPr trỏ tới; null nếu không có. */
    private static Element numberingIndent(DocxPackage pkg, Element numPr) {
        if (numPr == null) {
            return null;
        }
        String numId = val(child(numPr, "numId"));
        if (numId == null || "0".equals(numId)) {
            return null;
        }
        String ilvl = val(child(numPr, "ilvl"));
        Document numbering = pkg.xml("word/numbering.xml");
        if (numbering == null) {
            return null;
        }
        String abstractId = null;
        NodeList nums = numbering.getElementsByTagNameNS(W, "num");
        for (int i = 0; i < nums.getLength() && abstractId == null; i++) {
            Element num = (Element) nums.item(i);
            if (numId.equals(num.getAttributeNS(W, "numId"))) {
                abstractId = val(child(num, "abstractNumId"));
            }
        }
        if (abstractId == null) {
            return null;
        }
        NodeList abstracts = numbering.getElementsByTagNameNS(W, "abstractNum");
        for (int i = 0; i < abstracts.getLength(); i++) {
            Element abs = (Element) abstracts.item(i);
            if (!abstractId.equals(abs.getAttributeNS(W, "abstractNumId"))) {
                continue;
            }
            for (Node c = abs.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (c instanceof Element lvl && "lvl".equals(lvl.getLocalName())
                        && (ilvl == null ? "0" : ilvl).equals(lvl.getAttributeNS(W, "ilvl"))) {
                    Element lvlPPr = child(lvl, "pPr");
                    return lvlPPr == null ? null : child(lvlPPr, "ind");
                }
            }
        }
        return null;
    }

    private static void fill(Double[] ind, Element indEl) {
        if (indEl == null) {
            return;
        }
        if (ind[0] == null) {
            ind[0] = attr(indEl, "left", "start");
        }
        if (ind[1] == null) {
            ind[1] = attr(indEl, "right", "end");
        }
        if (ind[2] == null) {
            ind[2] = attr(indEl, "hanging", "hanging");
        }
    }

    private static Double attr(Element e, String name, String alias) {
        for (String n : new String[]{name, alias}) {
            String v = e.getAttributeNS(W, n);
            if (!v.isEmpty()) {
                try {
                    return Double.parseDouble(v);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String defaultParagraphStyle(Document styles) {
        NodeList list = styles.getElementsByTagNameNS(W, "style");
        for (int i = 0; i < list.getLength(); i++) {
            Element s = (Element) list.item(i);
            if ("paragraph".equals(s.getAttributeNS(W, "type")) && "1".equals(s.getAttributeNS(W, "default"))) {
                return s.getAttributeNS(W, "styleId");
            }
        }
        return null;
    }

    private static Element style(Document styles, String id) {
        NodeList list = styles.getElementsByTagNameNS(W, "style");
        for (int i = 0; i < list.getLength(); i++) {
            Element s = (Element) list.item(i);
            if (id.equals(s.getAttributeNS(W, "styleId"))) {
                return s;
            }
        }
        return null;
    }

    private static double twips(Element e, String name, double fallback) {
        if (e == null) {
            return fallback;
        }
        String v = e.getAttributeNS(W, name);
        if (v.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String val(Element e) {
        if (e == null) {
            return null;
        }
        String v = e.getAttributeNS(W, "val");
        return v.isEmpty() ? null : v;
    }

    static Element child(Element parent, String localName) {
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && W.equals(e.getNamespaceURI()) && localName.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }
}
