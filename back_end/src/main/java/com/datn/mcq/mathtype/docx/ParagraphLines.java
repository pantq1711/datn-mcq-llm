package com.datn.mcq.mathtype.docx;

import static com.datn.mcq.mathtype.docx.DocxPackage.W;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Thuộc tính đoạn (w:pPr) và việc đưa công thức quá rộng ra khỏi đoạn: mỗi dòng công thức một đoạn riêng, canh giữa như
 * công thức riêng dòng; chữ trước công thức ở lại đoạn gốc, chữ sau sang đoạn mới.
 */
public final class ParagraphLines {

    /** Thứ tự con của w:pPr theo lược đồ OOXML (Word có thể từ chối file sai thứ tự). */
    private static final List<String> PPR_ORDER = List.of("pStyle", "keepNext", "keepLines", "pageBreakBefore",
            "framePr", "widowControl", "numPr", "suppressLineNumbers", "pBdr", "shd", "tabs", "suppressAutoHyphens",
            "kinsoku", "wordWrap", "overflowPunct", "topLinePunct", "autoSpaceDE", "autoSpaceDN", "bidi",
            "adjustRightInd", "snapToGrid", "spacing", "ind", "contextualSpacing", "mirrorIndents", "suppressOverlap",
            "jc", "textDirection", "textAlignment", "textboxTightWrap", "outlineLvl", "divId", "cnfStyle", "rPr",
            "sectPr", "pPrChange");

    private ParagraphLines() {
    }

    /** Canh giữa đoạn (thay w:jc cũ nếu có). */
    public static void center(Element p) {
        Element pPr = pPr(p, true);
        setChild(pPr, "jc", "val", "center");
    }

    /**
     * Thay {@code target} (con trực tiếp của {@code p}: m:oMath hoặc m:oMathPara) bằng các dòng {@code runs}, mỗi dòng
     * một đoạn canh giữa. Chữ đứng trước giữ trong {@code p}; không có chữ trước thì dòng đầu nằm luôn trong {@code p}.
     * Chữ đứng sau sang một đoạn mới ngay sau dòng cuối.
     */
    public static void replaceWithLines(DocxPackage pkg, Element p, Node target, List<Element> runs) {
        Document doc = p.getOwnerDocument();
        List<Node> before = new ArrayList<>();
        List<Node> after = new ArrayList<>();
        boolean seen = false;
        for (Node c = p.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c == target) {
                seen = true;
            } else if (!(c instanceof Element e && W.equals(e.getNamespaceURI()) && "pPr".equals(e.getLocalName()))) {
                (seen ? after : before).add(c);
            }
        }
        // bỏ hết khỏi đoạn rồi dựng lại: pPr ở lại
        for (Node c : before) {
            p.removeChild(c);
        }
        for (Node c : after) {
            p.removeChild(c);
        }
        p.removeChild(target);
        trimEdge(before, false);
        trimEdge(after, true);

        Element pPr = pPr(p, false);
        double[] indent = PageGeometry.indentTwips(pkg, p);
        boolean listed = pPr != null && PageGeometry.child(pPr, "numPr") != null;

        List<Element> lines = new ArrayList<>(runs);
        if (before.isEmpty()) {
            p.appendChild(lines.remove(0));
            center(p);
        } else {
            before.forEach(p::appendChild);
        }
        Node last = p;
        for (Element run : lines) {
            Element np = newParagraph(doc, pPr, false, indent);
            center(np);
            np.appendChild(run);
            last.getParentNode().insertBefore(np, last.getNextSibling());
            last = np;
        }
        if (!after.isEmpty()) {
            Element np = newParagraph(doc, pPr, listed, indent);
            after.forEach(np::appendChild);
            last.getParentNode().insertBefore(np, last.getNextSibling());
        }
    }

    // ---------------------------------------------------------------- chi tiết

    /**
     * Đoạn mới có pPr là bản sao của đoạn gốc nhưng bỏ w:numPr (không đánh số lại). Đoạn chữ nối tiếp của một mục danh
     * sách ({@code keepIndent}) khai báo trực tiếp thụt lề trái của mục đó để vẫn thẳng hàng.
     */
    private static Element newParagraph(Document doc, Element sourcePPr, boolean keepIndent, double[] indent) {
        Element np = doc.createElementNS(W, "w:p");
        if (sourcePPr != null) {
            Element pPr = (Element) sourcePPr.cloneNode(true);
            Element numPr = PageGeometry.child(pPr, "numPr");
            if (numPr != null) {
                pPr.removeChild(numPr);
                if (keepIndent && indent[0] != 0) {
                    Element ind = setChild(pPr, "ind", "left", Long.toString(Math.round(indent[0])));
                    ind.removeAttributeNS(W, "hanging");
                    ind.removeAttributeNS(W, "firstLine");
                }
            }
            np.appendChild(pPr);
        }
        return np;
    }

    private static Element pPr(Element p, boolean create) {
        Element pPr = PageGeometry.child(p, "pPr");
        if (pPr == null && create) {
            pPr = p.getOwnerDocument().createElementNS(W, "w:pPr");
            p.insertBefore(pPr, p.getFirstChild());
        }
        return pPr;
    }

    /** Đặt (thay nếu đã có) con {@code name} của pPr với một thuộc tính w:{@code attr}, đúng thứ tự lược đồ. */
    private static Element setChild(Element pPr, String name, String attr, String value) {
        Element e = PageGeometry.child(pPr, name);
        if (e == null) {
            e = pPr.getOwnerDocument().createElementNS(W, "w:" + name);
            int rank = PPR_ORDER.indexOf(name);
            Node before = null;
            for (Node c = pPr.getFirstChild(); c != null && before == null; c = c.getNextSibling()) {
                if (c instanceof Element ce && W.equals(ce.getNamespaceURI())
                        && PPR_ORDER.indexOf(ce.getLocalName()) > rank) {
                    before = c;
                }
            }
            pPr.insertBefore(e, before);
        }
        e.setAttributeNS(W, "w:" + attr, value);
        return e;
    }

    /**
     * Bỏ các nút chỉ có khoảng trắng ở rìa (cuối danh sách chữ trước; đầu danh sách chữ sau) và cắt khoảng trắng ở rìa
     * chữ còn lại, để đầu/cuối đoạn không có dấu cách thừa.
     */
    private static void trimEdge(List<Node> nodes, boolean head) {
        while (!nodes.isEmpty()) {
            Node n = head ? nodes.get(0) : nodes.get(nodes.size() - 1);
            Element t = firstText(n, head);
            if (isBlankRun(n)) {
                nodes.remove(head ? 0 : nodes.size() - 1);
                continue;
            }
            if (t != null) {
                String s = t.getTextContent();
                String trimmed = head ? s.stripLeading() : s.stripTrailing();
                if (trimmed.isEmpty()) {
                    nodes.remove(head ? 0 : nodes.size() - 1);
                    continue;
                }
                t.setTextContent(trimmed);
            }
            return;
        }
    }

    /** Run chỉ chứa chữ trắng (hoặc U+200B): pandoc chèn các run này quanh công thức. */
    private static boolean isBlankRun(Node n) {
        if (!(n instanceof Element e) || !W.equals(e.getNamespaceURI()) || !"r".equals(e.getLocalName())) {
            return false;
        }
        boolean hasText = false;
        for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (!(c instanceof Element ce)) {
                continue;
            }
            if ("rPr".equals(ce.getLocalName())) {
                continue;
            }
            if (!"t".equals(ce.getLocalName()) || !blank(ce.getTextContent())) {
                return false;
            }
            hasText = true;
        }
        return hasText;
    }

    /** w:t của run chữ ở rìa (đầu hoặc cuối) của nút này; null nếu nút không phải run chữ. */
    private static Element firstText(Node n, boolean head) {
        if (!(n instanceof Element e) || !W.equals(e.getNamespaceURI()) || !"r".equals(e.getLocalName())) {
            return null;
        }
        Element found = null;
        for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element ce && "t".equals(ce.getLocalName())) {
                if (head && found == null) {
                    found = ce;
                } else if (!head) {
                    found = ce;
                }
            }
        }
        return found;
    }

    private static boolean blank(String s) {
        return s.chars().allMatch(ch -> Character.isWhitespace(ch) || ch == 0x200B);
    }
}
