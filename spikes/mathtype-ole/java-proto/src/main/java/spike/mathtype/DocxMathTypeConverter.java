package spike.mathtype;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Hậu xử lý docx do pandoc sinh: mỗi m:oMath -> đối tượng MathType OLE (w:object + v:shape + v:imagedata PNG +
 * o:OLEObject ProgID="Equation.DSMT4"), giống cấu trúc Word lưu khi MathType chèn công thức. Công thức nào chuyển
 * không được (cấu trúc chưa hỗ trợ, JLaTeXMath không dựng được ảnh) giữ nguyên dạng Equation (OMML).
 *
 * <p>LaTeX gốc của công thức thứ i (để dựng ảnh) lấy theo thứ tự xuất hiện: pandoc sinh đúng một m:oMath cho mỗi
 * $...$ theo thứ tự trong Markdown. Số lượng lệch nhau thì dừng, không đoán.
 */
final class DocxMathTypeConverter {

    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    static final String V = "urn:schemas-microsoft-com:vml";
    static final String O = "urn:schemas-microsoft-com:office:office";
    static final String PR = "http://schemas.openxmlformats.org/package/2006/relationships";
    static final String CT = "http://schemas.openxmlformats.org/package/2006/content-types";
    static final String REL_OLE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/oleObject";
    static final String REL_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";

    record Result(byte[] docx, List<String> log, int converted, int kept) { }

    private DocxMathTypeConverter() {
    }

    static Result convert(byte[] docx, List<String> latex) throws Exception {
        Map<String, byte[]> parts = unzip(docx);
        Document doc = parse(parts.get("word/document.xml"));
        Document rels = parse(parts.get("word/_rels/document.xml.rels"));
        Document types = parse(parts.get("[Content_Types].xml"));

        NodeList found = doc.getElementsByTagNameNS(OmmlToMtef.M, "oMath");
        List<Element> maths = new ArrayList<>();
        for (int i = 0; i < found.getLength(); i++) {
            maths.add((Element) found.item(i));
        }
        if (maths.size() != latex.size()) {
            throw new IllegalStateException("Số m:oMath (" + maths.size() + ") khác số LaTeX (" + latex.size() + ")");
        }
        Element root = doc.getDocumentElement();
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:v", V);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:o", O);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:r", R);

        List<String> log = new ArrayList<>();
        int converted = 0;
        boolean shapetypeWritten = false;
        long objectId = 1_700_000_000L;
        for (int i = 0; i < maths.size(); i++) {
            Element m = maths.get(i);
            int n = i + 1;
            boolean display = m.getParentNode() instanceof Element p && "oMathPara".equals(p.getLocalName());
            byte[] mtef;
            PreviewRenderer.Preview preview;
            try {
                mtef = OmmlToMtef.convert(m, !display);
            } catch (OmmlToMtef.Unsupported e) {
                log.add(n + ": GIỮ OMML (chưa hỗ trợ " + e.getMessage() + ") " + latex.get(i));
                continue;
            }
            try {
                preview = PreviewRenderer.render(latex.get(i));
            } catch (RuntimeException e) {
                log.add(n + ": GIỮ OMML (không dựng được ảnh: " + e.getMessage() + ") " + latex.get(i));
                continue;
            }
            String bin = "embeddings/oleObjectMT" + n + ".bin";
            String png = "media/imageMT" + n + ".png";
            parts.put("word/" + bin, MathTypeOle.build(mtef));
            parts.put("word/" + png, preview.png());
            String ridOle = addRel(rels, REL_OLE, bin);
            String ridImg = addRel(rels, REL_IMAGE, png);

            Element run = objectRun(doc, n, ridOle, ridImg, preview, !shapetypeWritten, objectId + n);
            shapetypeWritten = true;
            Node target = display ? m.getParentNode() : m; // m:oMathPara bọc m:oMath với công thức hiển thị
            if (display) {
                setCentered((Element) target.getParentNode());
            }
            target.getParentNode().replaceChild(run, target);
            converted++;
            log.add(String.format(Locale.ROOT, "%d: MathType OLE, MTEF %d byte, ảnh %.1fx%.1fpt (depth %.1fpt) %s",
                    n, mtef.length, preview.widthPt(), preview.heightPt(), preview.depthPt(), latex.get(i)));
        }
        ensureDefault(types, "bin", "application/vnd.openxmlformats-officedocument.oleObject");
        ensureDefault(types, "png", "image/png");

        parts.put("word/document.xml", serialize(doc));
        parts.put("word/_rels/document.xml.rels", serialize(rels));
        parts.put("[Content_Types].xml", serialize(types));
        return new Result(zip(parts), log, converted, maths.size() - converted);
    }

    /** w:r chứa w:object, theo đúng mẫu Word lưu cho đối tượng MathType (xem out/ref_unz/word/document.xml). */
    private static Element objectRun(Document doc, int n, String ridOle, String ridImg, PreviewRenderer.Preview p,
                                     boolean withShapetype, long objectId) {
        Element r = doc.createElementNS(W, "w:r");
        Element rPr = el(doc, r, W, "w:rPr");
        // hạ đối tượng xuống theo phần dưới đường cơ sở (đơn vị nửa point), như MathType làm
        int halfPts = (int) Math.round(p.depthPt() * 2);
        if (halfPts != 0) {
            el(doc, rPr, W, "w:position").setAttributeNS(W, "w:val", Integer.toString(-halfPts));
        }
        Element obj = el(doc, r, W, "w:object");
        obj.setAttributeNS(W, "w:dxaOrig", Long.toString(Math.round(p.widthPt() * 20)));
        obj.setAttributeNS(W, "w:dyaOrig", Long.toString(Math.round(p.heightPt() * 20)));
        if (withShapetype) {
            obj.appendChild(shapetype(doc));
        }
        String shapeId = "_x0000_i" + (1025 + n);
        Element shape = el(doc, obj, V, "v:shape");
        shape.setAttribute("id", shapeId);
        shape.setAttribute("type", "#_x0000_t75");
        shape.setAttribute("style", String.format(Locale.ROOT, "width:%.2fpt;height:%.2fpt", p.widthPt(), p.heightPt()));
        shape.setAttributeNS(O, "o:ole", "");
        Element img = el(doc, shape, V, "v:imagedata");
        img.setAttributeNS(R, "r:id", ridImg);
        img.setAttributeNS(O, "o:title", "");
        Element ole = el(doc, obj, O, "o:OLEObject");
        ole.setAttribute("Type", "Embed");
        ole.setAttribute("ProgID", MathTypeOle.PROG_ID);
        ole.setAttribute("ShapeID", shapeId);
        ole.setAttribute("DrawAspect", "Content");
        ole.setAttribute("ObjectID", "_" + objectId);
        ole.setAttributeNS(R, "r:id", ridOle);
        return r;
    }

    /** v:shapetype #_x0000_t75 (khung ảnh) như Word ghi ở đối tượng OLE đầu tiên. */
    private static Element shapetype(Document doc) {
        Element st = doc.createElementNS(V, "v:shapetype");
        st.setAttribute("id", "_x0000_t75");
        st.setAttribute("coordsize", "21600,21600");
        st.setAttributeNS(O, "o:spt", "75");
        st.setAttributeNS(O, "o:preferrelative", "t");
        st.setAttribute("path", "m@4@5l@4@11@9@11@9@5xe");
        st.setAttribute("filled", "f");
        st.setAttribute("stroked", "f");
        el(doc, st, V, "v:stroke").setAttribute("joinstyle", "miter");
        Element f = el(doc, st, V, "v:formulas");
        for (String eqn : new String[]{"if lineDrawn pixelLineWidth 0", "sum @0 1 0", "sum 0 0 @1", "prod @2 1 2",
                "prod @3 21600 pixelWidth", "prod @3 21600 pixelHeight", "sum @0 0 1", "prod @6 1 2",
                "prod @7 21600 pixelWidth", "sum @8 21600 0", "prod @7 21600 pixelHeight", "sum @10 21600 0"}) {
            el(doc, f, V, "v:f").setAttribute("eqn", eqn);
        }
        Element path = el(doc, st, V, "v:path");
        path.setAttributeNS(O, "o:extrusionok", "f");
        path.setAttribute("gradientshapeok", "t");
        path.setAttributeNS(O, "o:connecttype", "rect");
        el(doc, st, O, "o:lock").setAttributeNS(V, "v:ext", "edit");
        ((Element) st.getLastChild()).setAttribute("aspectratio", "t");
        return st;
    }

    private static void setCentered(Element p) {
        if (!"p".equals(p.getLocalName())) {
            return;
        }
        Element pPr = null;
        for (Node c = p.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && "pPr".equals(e.getLocalName())) {
                pPr = e;
            }
        }
        if (pPr == null) {
            pPr = p.getOwnerDocument().createElementNS(W, "w:pPr");
            p.insertBefore(pPr, p.getFirstChild());
        }
        el(p.getOwnerDocument(), pPr, W, "w:jc").setAttributeNS(W, "w:val", "center");
    }

    private static Element el(Document doc, Element parent, String ns, String qname) {
        Element e = doc.createElementNS(ns, qname);
        parent.appendChild(e);
        return e;
    }

    private static int relCounter = 0;

    private static String addRel(Document rels, String type, String target) {
        String id = "rIdMT" + (++relCounter);
        Element rel = rels.createElementNS(PR, "Relationship");
        rel.setAttribute("Id", id);
        rel.setAttribute("Type", type);
        rel.setAttribute("Target", target);
        rels.getDocumentElement().appendChild(rel);
        return id;
    }

    private static void ensureDefault(Document types, String ext, String contentType) {
        NodeList defs = types.getElementsByTagNameNS(CT, "Default");
        for (int i = 0; i < defs.getLength(); i++) {
            if (ext.equalsIgnoreCase(((Element) defs.item(i)).getAttribute("Extension"))) {
                return;
            }
        }
        Element d = types.createElementNS(CT, "Default");
        d.setAttribute("Extension", ext);
        d.setAttribute("ContentType", contentType);
        types.getDocumentElement().insertBefore(d, types.getDocumentElement().getFirstChild());
    }

    // ---------------------------------------------------------------- zip / xml

    static Map<String, byte[]> unzip(byte[] docx) throws IOException {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                parts.put(e.getName(), z.readAllBytes());
            }
        }
        return parts;
    }

    static byte[] zip(Map<String, byte[]> parts) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            // [Content_Types].xml đứng đầu như Word/pandoc
            List<String> names = new ArrayList<>(parts.keySet());
            names.remove("[Content_Types].xml");
            names.add(0, "[Content_Types].xml");
            for (String name : names) {
                z.putNextEntry(new ZipEntry(name));
                z.write(parts.get(name));
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setExpandEntityReferences(false);
        try (InputStream in = new ByteArrayInputStream(xml)) {
            return f.newDocumentBuilder().parse(in);
        }
    }

    static byte[] serialize(Document doc) throws Exception {
        var t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.STANDALONE, "yes");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        t.transform(new DOMSource(doc), new StreamResult((OutputStream) out));
        return out.toByteArray();
    }
}
