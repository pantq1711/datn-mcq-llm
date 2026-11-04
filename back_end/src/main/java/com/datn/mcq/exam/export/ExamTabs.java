package com.datn.mcq.exam.export;

import com.datn.mcq.mathtype.docx.DocxPackage;
import com.datn.mcq.mathtype.docx.ParagraphLines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tab của đề (4/11). Markdown không biểu diễn được tab, nên {@link ExamMarkdownBuilder} đặt mã giữ chỗ duy nhất
 * (số ở giữa hai ký tự vùng riêng, ví dụ U+E000 "2764" U+E001) ở đầu mỗi dòng phương án / ý đúng-sai (mốc 283 của mẫu) và
 * giữa các phương án cùng dòng. Sau pandoc (và sau bước MathType nếu có), {@link #apply} thay mỗi mã bằng
 * {@code <w:tab/>} và thêm {@code w:tabs} vào {@code w:pPr} của đoạn chứa nó, với vị trí dừng (twips từ lề trái) lấy từ
 * chính các mã đó. Mã không có số chỉ thành {@code <w:tab/>}.
 *
 * <p>KHÔNG dùng bảng để xếp cột: công cụ trộn đề của giáo viên đọc phương án theo tab.
 */
public final class ExamTabs {

    private static final Logger log = LoggerFactory.getLogger(ExamTabs.class);

    /** Ký tự vùng riêng (private use) bao mã giữ chỗ tab; nội dung đề bị lọc hai ký tự này. */
    static final char OPEN = '';
    static final char CLOSE = '';

    private static final Pattern TOKEN = Pattern.compile(OPEN + "(\\d*)" + CLOSE);
    private static final String W = DocxPackage.W;

    private ExamTabs() {
    }

    /** Mã giữ chỗ tab dừng tại {@code posTwips}. */
    public static String token(int posTwips) {
        return OPEN + Integer.toString(posTwips) + CLOSE;
    }

    /** Bỏ hai ký tự vùng riêng khỏi chữ của đề để nội dung không giả được mã giữ chỗ. */
    static String strip(String text) {
        return text.replace(Character.toString(OPEN), "").replace(Character.toString(CLOSE), "");
    }

    /**
     * Thay mọi mã giữ chỗ trong {@code word/document.xml} bằng tab. Không có mã nào, hoặc file không đọc được thì trả
     * nguyên file.
     */
    public static byte[] apply(byte[] docx) {
        DocxPackage pkg;
        try {
            pkg = DocxPackage.read(docx);
        } catch (IOException | RuntimeException e) {
            return docx;
        }
        Document doc = pkg.document();
        NodeList all = doc.getElementsByTagNameNS(W, "t");
        List<Element> hits = new ArrayList<>();
        for (int i = 0; i < all.getLength(); i++) {
            String s = all.item(i).getTextContent();
            if (s.indexOf(OPEN) >= 0 || s.indexOf(CLOSE) >= 0) {
                hits.add((Element) all.item(i));
            }
        }
        if (hits.isEmpty()) {
            return docx;
        }
        Map<Element, Set<Integer>> stops = new LinkedHashMap<>();
        for (Element t : hits) {
            Element run = t.getParentNode() instanceof Element p && isRun(p) ? p : null;
            if (run == null || !splittable(run)) {
                t.setTextContent(strip(t.getTextContent()));
                log.warn("Mã giữ chỗ tab nằm trong run không tách được, đã bỏ mã");
                continue;
            }
            replaceRun(run, t.getTextContent(), stops);
        }
        stops.forEach(ParagraphLines::setTabs);
        try {
            return pkg.write();
        } catch (IOException e) {
            log.warn("Không ghi lại được docx sau khi thay tab: {}", e.toString());
            return docx;
        }
    }

    /** Run chỉ gồm w:rPr (tùy chọn) và đúng một w:t. */
    private static boolean splittable(Element run) {
        int texts = 0;
        for (Node c = run.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && W.equals(e.getNamespaceURI())) {
                if ("t".equals(e.getLocalName())) {
                    texts++;
                } else if (!"rPr".equals(e.getLocalName())) {
                    return false;
                }
            }
        }
        return texts == 1;
    }

    private static void replaceRun(Element run, String text, Map<Element, Set<Integer>> stops) {
        Document doc = run.getOwnerDocument();
        Node parent = run.getParentNode();
        Element paragraph = ancestorParagraph(run);
        Matcher m = TOKEN.matcher(text);
        int last = 0;
        while (m.find()) {
            insertText(parent, run, strip(text.substring(last, m.start())));
            Element tabRun = doc.createElementNS(W, "w:r");
            Element rPr = child(run, "rPr");
            if (rPr != null) {
                tabRun.appendChild(rPr.cloneNode(true));
            }
            tabRun.appendChild(doc.createElementNS(W, "w:tab"));
            parent.insertBefore(tabRun, run);
            if (!m.group(1).isEmpty() && paragraph != null) {
                stops.computeIfAbsent(paragraph, k -> new LinkedHashSet<>()).add(Integer.parseInt(m.group(1)));
            }
            last = m.end();
        }
        insertText(parent, run, strip(text.substring(last)));
        parent.removeChild(run);
    }

    /** Chèn trước {@code run} một bản sao của nó chứa {@code text} (bỏ qua chữ rỗng). */
    private static void insertText(Node parent, Element run, String text) {
        if (text.isEmpty()) {
            return;
        }
        Element copy = (Element) run.cloneNode(true);
        child(copy, "t").setTextContent(text);
        parent.insertBefore(copy, run);
    }

    private static Element child(Element parent, String local) {
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && W.equals(e.getNamespaceURI()) && local.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static boolean isRun(Element e) {
        return W.equals(e.getNamespaceURI()) && "r".equals(e.getLocalName());
    }

    private static Element ancestorParagraph(Node n) {
        for (Node a = n.getParentNode(); a != null; a = a.getParentNode()) {
            if (a instanceof Element e && W.equals(e.getNamespaceURI()) && "p".equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }
}
