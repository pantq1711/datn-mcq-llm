package com.datn.mcq.mathtype.docx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Gói docx mở trong bộ nhớ: các part dạng byte, cùng ba part XML cần sửa ({@code word/document.xml},
 * {@code word/_rels/document.xml.rels}, {@code [Content_Types].xml}) đã parse thành DOM. Ghi lại thì
 * {@code [Content_Types].xml} đứng đầu như Word và pandoc.
 */
public final class DocxPackage {

    public static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    public static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    public static final String V = "urn:schemas-microsoft-com:vml";
    public static final String O = "urn:schemas-microsoft-com:office:office";
    static final String PR = "http://schemas.openxmlformats.org/package/2006/relationships";
    static final String CT = "http://schemas.openxmlformats.org/package/2006/content-types";

    static final String DOCUMENT = "word/document.xml";
    static final String DOCUMENT_RELS = "word/_rels/document.xml.rels";
    static final String CONTENT_TYPES = "[Content_Types].xml";

    private final Map<String, byte[]> parts;
    private final Document document;
    private final Document rels;
    private final Document types;
    private int relCounter;

    private DocxPackage(Map<String, byte[]> parts) throws IOException {
        for (String required : List.of(DOCUMENT, DOCUMENT_RELS, CONTENT_TYPES)) {
            if (!parts.containsKey(required)) {
                throw new IOException("Không phải file docx: thiếu " + required);
            }
        }
        this.parts = parts;
        this.document = parse(parts.get(DOCUMENT));
        this.rels = parse(parts.get(DOCUMENT_RELS));
        this.types = parse(parts.get(CONTENT_TYPES));
    }

    public static DocxPackage read(byte[] docx) throws IOException {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                parts.put(e.getName(), z.readAllBytes());
            }
        }
        return new DocxPackage(parts);
    }

    /** {@code word/document.xml}; sửa trực tiếp trên DOM này. */
    public Document document() {
        return document;
    }

    /** Thêm part mới (vd. {@code word/embeddings/oleObjectMT1.bin}). */
    public void put(String partName, byte[] content) {
        parts.put(partName, content);
    }

    /** Thêm một quan hệ của document.xml, trả Id ("rIdMT1", "rIdMT2"…; đánh số riêng cho từng gói). */
    public String addRelationship(String type, String target) {
        String id = "rIdMT" + (++relCounter);
        Element rel = rels.createElementNS(PR, "Relationship");
        rel.setAttribute("Id", id);
        rel.setAttribute("Type", type);
        rel.setAttribute("Target", target);
        rels.getDocumentElement().appendChild(rel);
        return id;
    }

    /** Khai báo kiểu nội dung theo phần mở rộng nếu chưa có. */
    public void ensureDefaultContentType(String ext, String contentType) {
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

    public byte[] write() throws IOException {
        parts.put(DOCUMENT, serialize(document));
        parts.put(DOCUMENT_RELS, serialize(rels));
        parts.put(CONTENT_TYPES, serialize(types));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            List<String> names = new ArrayList<>(parts.keySet());
            names.remove(CONTENT_TYPES);
            names.add(0, CONTENT_TYPES);
            for (String name : names) {
                z.putNextEntry(new ZipEntry(name));
                z.write(parts.get(name));
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private static Document parse(byte[] xml) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            try (InputStream in = new ByteArrayInputStream(xml)) {
                return f.newDocumentBuilder().parse(in);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("XML trong docx không hợp lệ", e);
        }
    }

    private static byte[] serialize(Document doc) throws IOException {
        try {
            var t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.STANDALONE, "yes");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            t.transform(new DOMSource(doc), new StreamResult((OutputStream) out));
            return out.toByteArray();
        } catch (Exception e) {
            throw new IOException("Không ghi được XML của docx", e);
        }
    }
}
