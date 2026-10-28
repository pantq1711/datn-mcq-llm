package com.datn.mcq.material.extract;

import org.springframework.stereotype.Component;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Đếm công thức và ảnh trong {@code word/document.xml} của docx: đối tượng MathType nhúng (OLE, ProgID
 * {@code Equation.*} như Equation.DSMT4 và Equation.3) mà pandoc bỏ qua; Equation gốc của Word (OMML) mà pandoc đọc được;
 * ảnh ngoài đối tượng OLE (có thể là công thức dạng ảnh, không OCR). Ảnh xem trước nằm trong đối tượng OLE không tính.
 *
 * <p>XML không được có DTD hay thực thể (Word không sinh ra): có là từ chối, không giải mã (chống XXE và billion laughs).
 */
@Component
public class DocxFormulaCounter {

    /** Số công thức, ảnh đếm được. */
    public record FormulaStats(int mathType, int omml, int images) {
        public int formulas() {
            return mathType + omml;
        }
    }

    static final String DOCUMENT_XML = "word/document.xml";
    private static final long MAX_XML_BYTES = 100L * 1024 * 1024;

    private static final String NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String NS_O = "urn:schemas-microsoft-com:office:office";
    private static final String NS_V = "urn:schemas-microsoft-com:vml";
    private static final String NS_M = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    private static final String NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main";

    FormulaStats count(Path docx) {
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            ZipEntry entry = zip.getEntry(DOCUMENT_XML);
            if (entry == null) {
                throw MaterialErrors.invalid("File Word không có nội dung chính.");
            }
            try (InputStream in = new MarkupGuard(zip.getInputStream(entry), MAX_XML_BYTES)) {
                return parse(in);
            }
        } catch (IOException | XMLStreamException e) {
            throw MaterialErrors.invalid("Không đọc được cấu trúc của file Word (file hỏng hoặc có nội dung không hợp lệ).");
        }
    }

    private static FormulaStats parse(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        trySet(factory, XMLConstants.ACCESS_EXTERNAL_DTD);
        trySet(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA);
        XMLStreamReader reader = factory.createXMLStreamReader(in);
        int mathType = 0;
        int omml = 0;
        int images = 0;
        int objectDepth = 0;
        int ommlDepth = 0;
        boolean inPict = false;
        int pictImages = 0;
        boolean pictHasOle = false;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    String ns = reader.getNamespaceURI();
                    if (NS_W.equals(ns) && name.equals("object")) {
                        objectDepth++;
                    } else if (NS_W.equals(ns) && name.equals("pict")) {
                        inPict = true;
                        pictImages = 0;
                        pictHasOle = false;
                    } else if (NS_O.equals(ns) && name.equals("OLEObject")) {
                        String progId = reader.getAttributeValue(null, "ProgID");
                        if (progId != null && progId.startsWith("Equation.")) {
                            mathType++;
                        }
                        pictHasOle = true;
                    } else if (NS_M.equals(ns) && name.equals("oMath")) {
                        if (ommlDepth == 0) {
                            omml++;
                        }
                        ommlDepth++;
                    } else if ((NS_A.equals(ns) && name.equals("blip")) || (NS_V.equals(ns)
                            && name.equals("imagedata"))) {
                        if (objectDepth == 0) {
                            if (inPict) {
                                pictImages++;
                            } else {
                                images++;
                            }
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName();
                    String ns = reader.getNamespaceURI();
                    if (NS_W.equals(ns) && name.equals("object")) {
                        objectDepth--;
                    } else if (NS_W.equals(ns) && name.equals("pict")) {
                        // w:pict kiểu cũ có thể chứa đối tượng OLE: ảnh bên trong là ảnh xem trước, không tính.
                        if (!pictHasOle) {
                            images += pictImages;
                        }
                        inPict = false;
                    } else if (NS_M.equals(ns) && name.equals("oMath")) {
                        ommlDepth--;
                    }
                }
            }
        } finally {
            reader.close();
        }
        return new FormulaStats(mathType, omml, images);
    }

    private static void trySet(XMLInputFactory factory, String property) {
        try {
            factory.setProperty(property, "");
        } catch (IllegalArgumentException ignored) {
            // bộ phân tích không hỗ trợ thuộc tính này; SUPPORT_DTD = false và không thực thể ngoài vẫn có hiệu lực
        }
    }

    /**
     * Đọc XML có giới hạn dung lượng; từ chối khi thấy {@code <!DOCTYPE} hoặc {@code <!ENTITY} (quét toàn bộ luồng, không
     * chỉ phần đầu) hoặc khi XML không phải UTF-8 (UTF-16 làm chuỗi quét không khớp).
     */
    private static final class MarkupGuard extends FilterInputStream {
        private static final byte[] DOCTYPE = "<!DOCTYPE".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        private static final byte[] ENTITY = "<!ENTITY".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        private final long max;
        private long read;
        private int doctypeAt;
        private int entityAt;
        private boolean first = true;

        MarkupGuard(InputStream in, long max) {
            super(in);
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n <= 0) {
                return n;
            }
            read += n;
            if (read > max) {
                throw new IOException("XML quá lớn");
            }
            for (int i = off; i < off + n; i++) {
                int c = b[i] & 0xFF;
                if (first) {
                    first = false;
                    if (c == 0xFF || c == 0xFE || c == 0x00) {
                        throw new IOException("XML không phải UTF-8");
                    }
                }
                doctypeAt = c == DOCTYPE[doctypeAt] ? doctypeAt + 1 : (c == DOCTYPE[0] ? 1 : 0);
                entityAt = c == ENTITY[entityAt] ? entityAt + 1 : (c == ENTITY[0] ? 1 : 0);
                if (doctypeAt == DOCTYPE.length || entityAt == ENTITY.length) {
                    throw new IOException("XML có DTD hoặc thực thể");
                }
            }
            return n;
        }
    }
}
