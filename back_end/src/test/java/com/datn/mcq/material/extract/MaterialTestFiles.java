package com.datn.mcq.material.extract;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * File mẫu tự dựng trong bộ nhớ cho test trích văn bản (không commit file nhị phân, không dùng tài liệu có bản quyền).
 * Không phải test (tên không khớp mẫu của surefire).
 */
final class MaterialTestFiles {

    private MaterialTestFiles() {}

    static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument."
            + "wordprocessingml.document.main+xml\"/></Types>";

    static final String ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
            + "officeDocument\" Target=\"word/document.xml\"/></Relationships>";

    static final String DOC_OPEN = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
            + " xmlns:o=\"urn:schemas-microsoft-com:office:office\" xmlns:v=\"urn:schemas-microsoft-com:vml\""
            + " xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\""
            + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
            + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><w:body>";
    static final String DOC_CLOSE = "</w:body></w:document>";

    static byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Một mục gồm {@code size} byte 0 (nén rất mạnh), ghi theo từng khối để không giữ cả khối trong bộ nhớ. */
    static byte[] zipWithZeros(String entryName, long size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            zip.putNextEntry(new ZipEntry(entryName));
            byte[] chunk = new byte[1 << 20];
            for (long written = 0; written < size; written += chunk.length) {
                zip.write(chunk, 0, (int) Math.min(chunk.length, size - written));
            }
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** docx tối giản (đủ để pandoc đọc): phần thân là {@code bodyXml}. */
    static byte[] minimalDocx(String bodyXml) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("[Content_Types].xml", CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
        files.put("_rels/.rels", ROOT_RELS.getBytes(StandardCharsets.UTF_8));
        files.put("word/document.xml", (DOC_OPEN + bodyXml + DOC_CLOSE).getBytes(StandardCharsets.UTF_8));
        return zip(files);
    }

    static String paragraph(String text) {
        return "<w:p><w:r><w:t xml:space=\"preserve\">" + text + "</w:t></w:r></w:p>";
    }

    /** Công thức MathType nhúng (đối tượng OLE) kèm ảnh xem trước, trong một đoạn riêng. */
    static String oleEquation(String progId) {
        return "<w:p><w:r><w:object w:dxaOrig=\"800\" w:dyaOrig=\"300\"><v:shape id=\"s1\" type=\"#_x0000_t75\">"
                + "<v:imagedata r:id=\"rId5\" o:title=\"\"/></v:shape>"
                + "<o:OLEObject Type=\"Embed\" ProgID=\"" + progId + "\" ShapeID=\"s1\" DrawAspect=\"Content\""
                + " ObjectID=\"_1\" r:id=\"rId6\"/></w:object></w:r></w:p>";
    }

    /** Công thức Equation gốc của Word (OMML). */
    static String omml(String latexish) {
        return "<w:p><m:oMath><m:r><m:t>" + latexish + "</m:t></m:r></m:oMath></w:p>";
    }

    static String drawingImage() {
        return "<w:p><w:r><w:drawing><a:graphic><a:graphicData><a:blip r:embed=\"rId7\"/></a:graphicData></a:graphic>"
                + "</w:drawing></w:r></w:p>";
    }

    static String vmlImage() {
        return "<w:p><w:r><w:pict><v:shape id=\"p1\"><v:imagedata r:id=\"rId8\"/></v:shape></w:pict></w:r></w:p>";
    }

    /** PDF mỗi trang một dòng chữ (font chuẩn, chỉ ASCII). */
    static byte[] pdf(String... pageTexts) {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                if (text != null && !text.isEmpty()) {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.beginText();
                        cs.setFont(font, 12);
                        cs.newLineAtOffset(50, 700);
                        cs.showText(text);
                        cs.endText();
                    }
                }
            }
            return save(doc);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF chỉ có trang trắng (như bản scan không có lớp chữ). */
    static byte[] blankPdf(int pages) {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            return save(doc);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF mã hóa: mở cần mật khẩu người dùng. */
    static byte[] passwordPdf(String text) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pw", "user-pw",
                    new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            return save(doc);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF mở không cần mật khẩu nhưng cấm sao chép nội dung (chỉ có mật khẩu chủ). */
    static byte[] copyProtectedPdf(String text) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            AccessPermission permission = new AccessPermission();
            permission.setCanExtractContent(false);
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pw", "", permission);
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            return save(doc);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
