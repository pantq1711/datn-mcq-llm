package com.datn.mcq.bankimport.docx;

import com.datn.mcq.bankimport.docx.Block.Cell;
import com.datn.mcq.bankimport.docx.Block.Para;
import com.datn.mcq.bankimport.docx.Block.Tbl;
import com.datn.mcq.bankimport.docx.RichText.Obj;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Đọc .docx thành danh sách khối (đoạn, bảng) ở thân tài liệu. Chỉ đọc word/document.xml, quan hệ của nó và các tệp
 * media/embeddings; bỏ qua header, footer, chú thích. Văn bản được chuẩn hóa NFC theo từng run.
 */
public final class DocxReader {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String V = "urn:schemas-microsoft-com:vml";
    private static final String O = "urn:schemas-microsoft-com:office:office";
    private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";

    private static final Pattern STYLE_SIZE = Pattern.compile("(width|height)\\s*:\\s*([0-9.]+)\\s*(pt|cm|in|mm|px)?");

    /** Nội dung đã đọc. {@code binaries}: word/media/* và word/embeddings/* theo đường dẫn trong zip. */
    public record Result(List<Block> blocks, Map<String, byte[]> binaries) {}

    private final Map<String, String> rels = new HashMap<>();

    public static Result read(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            DocxReader reader = new DocxReader();
            ZipEntry relEntry = zip.getEntry("word/_rels/document.xml.rels");
            if (relEntry != null) {
                reader.loadRels(parse(zip, relEntry));
            }
            ZipEntry docEntry = zip.getEntry("word/document.xml");
            if (docEntry == null) {
                throw new IOException("Khong co word/document.xml");
            }
            Document doc = parse(zip, docEntry);
            Map<String, byte[]> binaries = new LinkedHashMap<>();
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory() && (e.getName().startsWith("word/media/") || e.getName().startsWith("word/embeddings/"))) {
                    try (InputStream in = zip.getInputStream(e)) {
                        binaries.put(e.getName(), in.readAllBytes());
                    }
                }
            }
            return new Result(reader.body(doc), binaries);
        } catch (org.xml.sax.SAXException | javax.xml.parsers.ParserConfigurationException e) {
            throw new IOException("Khong doc duoc XML cua tai lieu", e);
        }
    }

    private static Document parse(ZipFile zip, ZipEntry entry) throws IOException,
            javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream in = zip.getInputStream(entry)) {
            return f.newDocumentBuilder().parse(in);
        }
    }

    private void loadRels(Document d) {
        NodeList l = d.getElementsByTagName("Relationship");
        for (int i = 0; i < l.getLength(); i++) {
            Element e = (Element) l.item(i);
            String target = e.getAttribute("Target");
            rels.put(e.getAttribute("Id"), target.startsWith("/") ? target.substring(1) : "word/" + target);
        }
    }

    // ---------- khối ----------

    private List<Block> body(Document doc) {
        List<Block> out = new ArrayList<>();
        NodeList bodies = doc.getElementsByTagNameNS(W, "body");
        if (bodies.getLength() == 0) {
            return out;
        }
        for (Node n = bodies.item(0).getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && W.equals(e.getNamespaceURI())) {
                if (e.getLocalName().equals("p")) {
                    out.add(paragraph(e));
                } else if (e.getLocalName().equals("tbl")) {
                    out.add(table(e));
                }
            }
        }
        return out;
    }

    private Tbl table(Element tbl) {
        List<List<Cell>> rows = new ArrayList<>();
        for (Element tr : children(tbl, "tr")) {
            List<Cell> cells = new ArrayList<>();
            for (Element tc : children(tr, "tc")) {
                int span = 1;
                for (Element pr : children(tc, "tcPr")) {
                    for (Element gs : children(pr, "gridSpan")) {
                        span = intAttr(gs, "val", 1);
                    }
                }
                List<Para> paras = new ArrayList<>();
                collectCellParas(tc, paras);
                cells.add(new Cell(span, paras));
            }
            rows.add(cells);
        }
        return new Tbl(rows);
    }

    /** Đoạn trong ô; bảng lồng nhau được làm phẳng thành các đoạn nối tiếp. */
    private void collectCellParas(Element parent, List<Para> out) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && W.equals(e.getNamespaceURI())) {
                if (e.getLocalName().equals("p")) {
                    out.add(paragraph(e));
                } else if (e.getLocalName().equals("tbl")) {
                    for (Element tr : children(e, "tr")) {
                        for (Element tc : children(tr, "tc")) {
                            collectCellParas(tc, out);
                        }
                    }
                }
            }
        }
    }

    private Para paragraph(Element p) {
        boolean numbered = false;
        for (Element pPr : children(p, "pPr")) {
            numbered = !children(pPr, "numPr").isEmpty();
        }
        RichText.Builder b = new RichText.Builder();
        walk(p, b);
        return new Para(b.build(), numbered);
    }

    /** Duyệt theo thứ tự tài liệu; mc:AlternateContent chỉ lấy nhánh Choice để không trùng ảnh dự phòng. */
    private void walk(Element parent, RichText.Builder b) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element e)) {
                continue;
            }
            String ns = e.getNamespaceURI();
            String name = e.getLocalName();
            if (W.equals(ns) && name.equals("r")) {
                run(e, b);
            } else if (MC.equals(ns) && name.equals("AlternateContent")) {
                for (Element choice : children(e, "Choice")) {
                    walk(choice, b);
                    break;
                }
            } else if (W.equals(ns) && (name.equals("pPr") || name.equals("rPr"))) {
                // thuộc tính, không phải nội dung
            } else {
                walk(e, b);
            }
        }
    }

    private void run(Element r, RichText.Builder b) {
        boolean yellow = false;
        boolean cyan = false;
        byte script = 0;
        for (Element rPr : children(r, "rPr")) {
            for (Element h : children(rPr, "highlight")) {
                String v = h.getAttributeNS(W, "val");
                yellow = "yellow".equals(v);
                cyan = "cyan".equals(v);
            }
            for (Element va : children(rPr, "vertAlign")) {
                String v = va.getAttributeNS(W, "val");
                script = (byte) ("superscript".equals(v) ? 1 : "subscript".equals(v) ? 2 : 0);
            }
        }
        for (Node n = r.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element e) || !W.equals(e.getNamespaceURI())) {
                continue;
            }
            switch (e.getLocalName()) {
                case "t" -> b.text(Normalizer.normalize(e.getTextContent(), Normalizer.Form.NFC), yellow, cyan, script);
                case "tab" -> b.text("\t", yellow, cyan, (byte) 0);
                case "br", "cr" -> b.text("\n", yellow, cyan, (byte) 0);
                case "sym" -> b.text("￼", yellow, cyan, (byte) 0);     // ký hiệu Wingdings/Webdings: bỏ khi chuẩn hóa
                case "object" -> object(e, b, yellow, cyan);
                case "drawing" -> drawing(e, b, yellow, cyan);
                case "pict" -> pict(e, b, yellow, cyan);
                default -> { }
            }
        }
    }

    private void object(Element obj, RichText.Builder b, boolean yellow, boolean cyan) {
        Element ole = firstDesc(obj, O, "OLEObject");
        Element img = firstDesc(obj, V, "imagedata");
        String preview = img == null ? null : rels.get(img.getAttributeNS(R, "id"));
        Double[] size = shapeSize(firstDesc(obj, V, "shape"));
        if (ole != null) {
            String progId = ole.getAttribute("ProgID");
            String ref = rels.get(ole.getAttributeNS(R, "id"));
            if (progId.startsWith("Equation.") && ref != null) {
                b.obj(new Obj(true, ref, preview, progId, "bin", size[0], size[1]), yellow, cyan);
                return;
            }
            if (preview != null) {       // đối tượng vẽ (FXDraw, PBrush...): dùng ảnh xem trước
                b.obj(new Obj(false, preview, null, progId, ext(preview), size[0], size[1]), yellow, cyan);
            }
            return;
        }
        if (preview != null) {
            b.obj(new Obj(false, preview, null, null, ext(preview), size[0], size[1]), yellow, cyan);
        }
    }

    private void drawing(Element d, RichText.Builder b, boolean yellow, boolean cyan) {
        Element blip = firstDesc(d, A, "blip");
        if (blip == null) {
            return;
        }
        String ref = rels.get(blip.getAttributeNS(R, "embed"));
        if (ref == null) {
            return;
        }
        Double w = null;
        Double h = null;
        Element extent = firstDesc(d, WP, "extent");
        if (extent != null) {
            w = intAttr(extent, "cx", 0) / 360000.0;
            h = intAttr(extent, "cy", 0) / 360000.0;
        }
        b.obj(new Obj(false, ref, null, null, ext(ref), w, h), yellow, cyan);
    }

    private void pict(Element pict, RichText.Builder b, boolean yellow, boolean cyan) {
        Element img = firstDesc(pict, V, "imagedata");
        if (img == null) {
            return;
        }
        String ref = rels.get(img.getAttributeNS(R, "id"));
        if (ref == null) {
            return;
        }
        Double[] size = shapeSize(firstDesc(pict, V, "shape"));
        b.obj(new Obj(false, ref, null, null, ext(ref), size[0], size[1]), yellow, cyan);
    }

    /** Kích thước hiển thị (cm) từ style="width:..;height:.." của v:shape; null nếu không có. */
    private static Double[] shapeSize(Element shape) {
        Double[] out = {null, null};
        if (shape == null) {
            return out;
        }
        Matcher m = STYLE_SIZE.matcher(shape.getAttribute("style"));
        while (m.find()) {
            double v = Double.parseDouble(m.group(2));
            String unit = m.group(3) == null ? "px" : m.group(3);
            double cm = switch (unit) {
                case "pt" -> v / 28.3465;
                case "in" -> v * 2.54;
                case "mm" -> v / 10;
                case "px" -> v / 37.795;
                default -> v;
            };
            out[m.group(1).equals("width") ? 0 : 1] = cm;
        }
        return out;
    }

    private static String ext(String ref) {
        int i = ref.lastIndexOf('.');
        return i < 0 ? "" : ref.substring(i + 1).toLowerCase();
    }

    // ---------- DOM ----------

    private static List<Element> children(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && W.equals(e.getNamespaceURI()) && localName.equals(e.getLocalName())) {
                out.add(e);
            }
        }
        return out;
    }

    private static Element firstDesc(Element root, String ns, String localName) {
        NodeList l = root.getElementsByTagNameNS(ns, localName);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    private static int intAttr(Element e, String name, int dflt) {
        String v = e.getAttributeNS(W, name);
        if (v.isEmpty()) {
            v = e.getAttribute(name);
        }
        if (v.isEmpty()) {
            v = e.getAttribute("w:" + name);
        }
        try {
            return v.isEmpty() ? dflt : (int) Long.parseLong(v);
        } catch (NumberFormatException ex) {
            return dflt;
        }
    }

    private DocxReader() {}
}
