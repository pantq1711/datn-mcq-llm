package com.datn.mcq.material.extract;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Dựng docx có đối tượng MathType nhúng (OLE) cho test trích văn bản: document.xml, quan hệ
 * word/_rels/document.xml.rels và các file word/embeddings/oleObjectN.bin. Chỉ chứa nội dung GIẢ hoặc mẫu ngắn của
 * công thức (src/test/resources/mtef/real-*.bin). Không phải test (tên không khớp mẫu của surefire).
 */
final class MathTypeDocx {

    private static final String REL_OLE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/oleObject";

    private final StringBuilder body = new StringBuilder();
    private final Map<String, byte[]> parts = new LinkedHashMap<>();
    private final StringBuilder rels = new StringBuilder();
    private String relsOverride;
    private int nextOle = 1;
    private int nextRel = 10;

    // ---------- run ----------

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String t(String text) {
        return "<w:r><w:t xml:space=\"preserve\">" + esc(text) + "</w:t></w:r>";
    }

    static String bold(String text) {
        return "<w:r><w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">" + esc(text) + "</w:t></w:r>";
    }

    /** Run chứa đối tượng OLE trỏ tới quan hệ {@code rId} (có thể không tồn tại). */
    static String objectRun(String progId, String rId, boolean highlighted, boolean bold) {
        String rPr = (highlighted || bold) ? "<w:rPr>" + (bold ? "<w:b/>" : "")
                + (highlighted ? "<w:highlight w:val=\"yellow\"/>" : "") + "</w:rPr>" : "";
        return "<w:r>" + rPr + "<w:object w:dxaOrig=\"800\" w:dyaOrig=\"300\"><v:shape id=\"s1\" type=\"#_x0000_t75\">"
                + "<v:imagedata r:id=\"rId5\" o:title=\"\"/></v:shape>"
                + "<o:OLEObject Type=\"Embed\" ProgID=\"" + progId + "\" ShapeID=\"s1\" DrawAspect=\"Content\""
                + " ObjectID=\"_1\" r:id=\"" + rId + "\"/></w:object></w:r>";
    }

    /** Công thức Equation.DSMT4: thêm file oleObject và quan hệ, trả run. */
    String eq(byte[] ole) {
        return eq("Equation.DSMT4", ole, false, false);
    }

    String eq(String progId, byte[] ole, boolean highlighted, boolean bold) {
        String id = "rId" + nextRel++;
        String part = "word/embeddings/oleObject" + nextOle++ + ".bin";
        parts.put(part, ole);
        rels.append("<Relationship Id=\"").append(id).append("\" Type=\"").append(REL_OLE).append("\" Target=\"")
                .append(part.substring("word/".length())).append("\"/>");
        return objectRun(progId, id, highlighted, bold);
    }

    // ---------- khối ----------

    MathTypeDocx p(String... runs) {
        body.append("<w:p>").append(String.join("", runs)).append("</w:p>");
        return this;
    }

    MathTypeDocx raw(String xml) {
        body.append(xml);
        return this;
    }

    /** Bảng một hàng; mỗi ô là một đoạn gồm các run. */
    MathTypeDocx tableRow(String[]... cellRuns) {
        StringBuilder sb = new StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr><w:tblGrid>");
        for (int i = 0; i < cellRuns.length; i++) {
            sb.append("<w:gridCol w:w=\"3000\"/>");
        }
        sb.append("</w:tblGrid><w:tr>");
        for (String[] runs : cellRuns) {
            sb.append("<w:tc><w:p>").append(String.join("", runs)).append("</w:p></w:tc>");
        }
        body.append(sb).append("</w:tr></w:tbl>");
        return this;
    }

    /** Thay toàn bộ nội dung document.xml.rels (ví dụ để thử XML độc hại). */
    MathTypeDocx relsXml(String xml) {
        this.relsOverride = xml;
        return this;
    }

    byte[] build() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("[Content_Types].xml", MaterialTestFiles.CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
        files.put("_rels/.rels", MaterialTestFiles.ROOT_RELS.getBytes(StandardCharsets.UTF_8));
        files.put("word/document.xml",
                (MaterialTestFiles.DOC_OPEN + body + MaterialTestFiles.DOC_CLOSE).getBytes(StandardCharsets.UTF_8));
        String relsXml = relsOverride != null ? relsOverride : "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + rels
                + "</Relationships>";
        files.put("word/_rels/document.xml.rels", relsXml.getBytes(StandardCharsets.UTF_8));
        files.putAll(parts);
        return MaterialTestFiles.zip(files);
    }

    // ---------- file mẫu ----------

    /** Một trong sáu đối tượng OLE mẫu (real-1, 2, 3, 5, 8, 9). */
    static byte[] real(int n) {
        try (InputStream in = MathTypeDocx.class.getResourceAsStream("/mtef/real-" + n + ".bin")) {
            if (in == null) {
                throw new IllegalStateException("thiếu /mtef/real-" + n + ".bin");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Đối tượng OLE hợp lệ (đọc được) nhưng có thêm một luồng đệm ngẫu nhiên (không nén được) để vượt trần dung lượng.
     */
    static byte[] oleWithPadding(byte[] ole, int padBytes) {
        byte[] pad = new byte[padBytes];
        new Random(42).nextBytes(pad);
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(ole));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fs.createDocument(new ByteArrayInputStream(pad), "Pad");
            fs.writeFilesystem(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
