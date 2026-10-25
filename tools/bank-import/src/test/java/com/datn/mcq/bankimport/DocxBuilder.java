package com.datn.mcq.bankimport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Dựng .docx tối thiểu (document.xml, quan hệ, media, embeddings) cho test. Chỉ chứa nội dung GIẢ; không dùng bộ câu hỏi
 * thương mại làm fixture.
 */
public final class DocxBuilder {

    /** Một run: XML đã dựng sẵn. */
    public record Run(String xml) {}

    private final List<String> body = new ArrayList<>();
    private final Map<String, byte[]> parts = new LinkedHashMap<>();   // đường dẫn trong zip → nội dung
    private final List<String> rels = new ArrayList<>();
    private int nextRel = 1;
    private int nextMedia = 1;
    private int nextEmbedding = 1;

    // ---------- run ----------

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Run textRun(String text, String rpr) {
        return new Run("<w:r>" + (rpr.isEmpty() ? "" : "<w:rPr>" + rpr + "</w:rPr>") + "<w:t xml:space=\"preserve\">"
                + esc(text) + "</w:t></w:r>");
    }

    public static Run t(String text) {
        return textRun(text, "");
    }

    /** Tô vàng (đáp án). */
    public static Run y(String text) {
        return textRun(text, "<w:highlight w:val=\"yellow\"/>");
    }

    /** Tô cyan (nhãn mức). */
    public static Run cy(String text) {
        return textRun(text, "<w:highlight w:val=\"cyan\"/>");
    }

    /** Chữ mũ gõ tay (vertAlign). */
    public static Run sup(String text) {
        return textRun(text, "<w:vertAlign w:val=\"superscript\"/>");
    }

    public static Run tab() {
        return new Run("<w:r><w:tab/></w:r>");
    }

    /** Ký hiệu Wingdings/Webdings (ô trống). */
    public static Run sym() {
        return new Run("<w:r><w:sym w:font=\"Webdings\" w:char=\"F063\"/></w:r>");
    }

    /** Công thức MathType: đối tượng OLE + ảnh xem trước WMF. {@code yellow}: run mang highlight vàng. */
    public Run eq(byte[] ole) {
        return eq(ole, false);
    }

    public Run eq(byte[] ole, boolean yellow) {
        String preview = addMedia("wmf", new byte[]{1, 0, 9, 0});
        String embed = addPart("word/embeddings/oleObject" + (nextEmbedding++) + ".bin", ole, null);
        return new Run("<w:r>" + (yellow ? "<w:rPr><w:highlight w:val=\"yellow\"/></w:rPr>" : "") + "<w:object>"
                + "<v:shape id=\"s" + nextRel + "\" style=\"width:20pt;height:12pt\"><v:imagedata r:id=\"" + preview + "\"/></v:shape>"
                + "<o:OLEObject Type=\"Embed\" ProgID=\"Equation.DSMT4\" r:id=\"" + embed + "\"/></w:object></w:r>");
    }

    /** Đối tượng OLE không phải công thức (ví dụ đối tượng vẽ) kèm ảnh xem trước. */
    public Run oleDrawing(byte[] ole, double widthCm, double heightCm) {
        String preview = addMedia("emf", new byte[]{1, 0, 0, 0});
        String embed = addPart("word/embeddings/oleObject" + (nextEmbedding++) + ".bin", ole, null);
        return new Run("<w:r><w:object><v:shape style=\"width:" + (widthCm * 28.35) + "pt;height:" + (heightCm * 28.35)
                + "pt\"><v:imagedata r:id=\"" + preview + "\"/></v:shape>"
                + "<o:OLEObject Type=\"Embed\" ProgID=\"FXDraw.Graphic\" r:id=\"" + embed + "\"/></w:object></w:r>");
    }

    /** Ảnh (w:drawing, inline) với kích thước hiển thị tính bằng cm. */
    public Run drawing(String ext, byte[] bytes, double widthCm, double heightCm) {
        String rid = addMedia(ext, bytes);
        long cx = Math.round(widthCm * 360000);
        long cy = Math.round(heightCm * 360000);
        return new Run("<w:r><w:drawing><wp:inline><wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>"
                + "<a:graphic><a:graphicData><pic:pic><pic:blipFill><a:blip r:embed=\"" + rid + "\"/></pic:blipFill></pic:pic>"
                + "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r>");
    }

    /** Ảnh dạng w:pict/v:imagedata (thường là công thức dán thành ảnh WMF). */
    public Run pict(String ext, byte[] bytes, double widthCm, double heightCm) {
        String rid = addMedia(ext, bytes);
        return new Run("<w:r><w:pict><v:shape style=\"width:" + (widthCm * 28.35) + "pt;height:" + (heightCm * 28.35)
                + "pt\"><v:imagedata r:id=\"" + rid + "\"/></v:shape></w:pict></w:r>");
    }

    // ---------- khối ----------

    private static String paraXml(boolean numbered, Run... runs) {
        StringBuilder sb = new StringBuilder("<w:p>");
        if (numbered) {
            sb.append("<w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr></w:pPr>");
        }
        for (Run r : runs) {
            sb.append(r.xml());
        }
        return sb.append("</w:p>").toString();
    }

    public DocxBuilder p(Run... runs) {
        body.add(paraXml(false, runs));
        return this;
    }

    public DocxBuilder p(String text) {
        return p(t(text));
    }

    /** Đoạn đánh số tự động (numPr), như các tiêu đề câu trong bộ thật. */
    public DocxBuilder numbered(Run... runs) {
        body.add(paraXml(true, runs));
        return this;
    }

    /** Ô của bảng: mỗi ô là danh sách đoạn; {@code span} &gt; 1 là ô gộp (gridSpan). */
    public record Cell(int span, List<String> paragraphs) {}

    public static String cellParas(Run... runs) {
        return paraXml(false, runs);
    }

    public static Cell cell(String... paraXmls) {
        return new Cell(1, List.of(paraXmls));
    }

    public static Cell merged(int span, String... paraXmls) {
        return new Cell(span, List.of(paraXmls));
    }

    public DocxBuilder table(List<List<Cell>> rows) {
        StringBuilder sb = new StringBuilder("<w:tbl>");
        for (List<Cell> row : rows) {
            sb.append("<w:tr>");
            for (Cell c : row) {
                sb.append("<w:tc><w:tcPr>").append(c.span() > 1 ? "<w:gridSpan w:val=\"" + c.span() + "\"/>" : "")
                        .append("</w:tcPr>");
                for (String px : c.paragraphs()) {
                    sb.append(px);
                }
                if (c.paragraphs().isEmpty()) {
                    sb.append("<w:p/>");
                }
                sb.append("</w:tc>");
            }
            sb.append("</w:tr>");
        }
        body.add(sb.append("</w:tbl>").toString());
        return this;
    }

    // ---------- gói ----------

    private String addMedia(String ext, byte[] bytes) {
        return addPart("word/media/image" + (nextMedia++) + "." + ext, bytes, null);
    }

    /** Thêm một phần vào zip kèm quan hệ trong document.xml.rels; trả về rId. */
    private String addPart(String path, byte[] bytes, String ignored) {
        parts.put(path, bytes);
        String rid = "rId" + (nextRel++);
        rels.add("<Relationship Id=\"" + rid + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
                + (path.contains("embeddings") ? "oleObject" : "image") + "\" Target=\"" + path.substring("word/".length())
                + "\"/>");
        return rid;
    }

    public Path write(Path dir, String fileName) throws IOException {
        Path file = dir.resolve(fileName);
        String doc = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
                + " xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\""
                + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                + " xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\""
                + " xmlns:v=\"urn:schemas-microsoft-com:vml\" xmlns:o=\"urn:schemas-microsoft-com:office:office\""
                + " xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\"><w:body>"
                + String.join("", body) + "<w:sectPr/></w:body></w:document>";
        String relsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas."
                + "openxmlformats.org/package/2006/relationships\">" + String.join("", rels) + "</Relationships>";
        try (OutputStream os = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(os)) {
            put(zip, "[Content_Types].xml", ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas."
                    + "openxmlformats.org/package/2006/content-types\"/>").getBytes(StandardCharsets.UTF_8));
            put(zip, "word/document.xml", doc.getBytes(StandardCharsets.UTF_8));
            put(zip, "word/_rels/document.xml.rels", relsXml.getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> e : parts.entrySet()) {
                put(zip, e.getKey(), e.getValue());
            }
        }
        return file;
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
