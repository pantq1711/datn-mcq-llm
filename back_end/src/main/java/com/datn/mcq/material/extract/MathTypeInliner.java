package com.datn.mcq.material.extract;

import com.datn.mcq.mtef.FormulaResult;
import com.datn.mcq.mtef.MtefConverter;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.XMLConstants;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Đổi công thức MathType (đối tượng OLE {@code Equation.DSMT*}) trong docx thành LaTeX TRƯỚC khi pandoc đọc, vì pandoc
 * bỏ qua đối tượng OLE. Mỗi {@code w:object} chứa {@code o:OLEObject} có ProgID {@code Equation.*} được thay bằng một
 * mã giữ chỗ duy nhất (văn bản thường, không chứa ký tự mà pandoc thoát); sau khi pandoc trích xong,
 * {@link #restore} đổi mã thành {@code $latex$} ({@code $$latex$$} nếu công thức đứng riêng một dòng). Chèn thẳng
 * {@code $...$} vào docx không được vì pandoc sẽ thoát {@code $} và {@code \}.
 *
 * <p>Công thức không chuyển được (không phải DSMT như Equation.3, thiếu quan hệ hoặc file, file OLE quá
 * {@link #MAX_OLE_BYTES}, MTEF lỗi hoặc chưa hỗ trợ, quá {@link #MAX_FORMULAS} công thức) thành "{@value
 * #UNREADABLE_TEXT}" và được đếm; lỗi của một công thức không làm hỏng công thức khác hay cả tài liệu.
 *
 * <p>An toàn: XML đọc bằng bộ phân tích đã tắt DTD và thực thể ngoài, có {@link DocxFormulaCounter.MarkupGuard} chặn
 * {@code <!DOCTYPE}, {@code <!ENTITY} và giới hạn dung lượng; mỗi file OLE đọc tối đa {@link #MAX_OLE_BYTES} byte;
 * đường dẫn trong quan hệ được chuẩn hóa và chỉ dùng để tra mục trong chính file zip (không chạm hệ thống tệp). Nếu
 * docx không có công thức MathType nào thì không ghi lại file, pandoc đọc đúng file gốc.
 */
final class MathTypeInliner {

    /** Dung lượng tối đa của một file oleObject; lớn hơn thì không đọc. */
    static final int MAX_OLE_BYTES = 1 << 20;
    /** Số công thức tối đa được chuyển trong một tài liệu; phần còn lại thành "[công thức]". */
    static final int MAX_FORMULAS = 2000;
    static final String UNREADABLE_TEXT = "[công thức]";

    /** Số sự kiện XML tối đa của một {@code w:object} được giữ trong bộ nhớ để xét; quá thì giữ nguyên đối tượng. */
    private static final int MAX_OBJECT_EVENTS = 5000;
    private static final int MAX_RELATIONSHIPS = 50_000;

    private static final String NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String NS_O = "urn:schemas-microsoft-com:office:office";
    private static final String NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String RELS_XML = "word/_rels/document.xml.rels";
    private static final String TOKEN_OPEN = "⟦MT";
    private static final String TOKEN_CLOSE = "⟧";
    /** Chữ thay cho chỉ số trong mã giữ chỗ của công thức không đọc được. */
    private static final String UNREADABLE_INDEX = "x";
    private static final SecureRandom RANDOM = new SecureRandom();

    private MathTypeInliner() {}

    /**
     * Kết quả tiền xử lý.
     *
     * @param docx       file docx cho pandoc đọc: file gốc nếu không có công thức MathType, ngược lại là bản đã thay mã
     * @param latex      LaTeX của các công thức đã chuyển, theo chỉ số trong mã giữ chỗ
     * @param unreadable số công thức MathType không chuyển được
     * @param nonce      chuỗi ngẫu nhiên của lần này, nằm trong mọi mã giữ chỗ (văn bản người dùng không thể trùng)
     */
    record Prepared(Path docx, List<String> latex, int unreadable, String nonce) {
        /** Số công thức MathType đã gặp (chuyển được và không chuyển được). */
        int total() {
            return latex.size() + unreadable;
        }
    }

    private static final class State {
        final String nonce = newNonce();
        final List<String> latex = new ArrayList<>();
        int unreadable;
        Map<String, String> oleEntries;     // quan hệ rId → tên mục trong zip; nạp khi gặp công thức đầu tiên
    }

    /**
     * Thay các đối tượng MathType trong {@code word/document.xml} của {@code in} bằng mã giữ chỗ; nếu có thay thì ghi
     * docx mới vào {@code out}.
     *
     * @throws com.datn.mcq.common.exception.ApiErrorException INVALID_FILE khi XML hỏng hoặc có DTD/thực thể
     */
    static Prepared prepare(Path in, Path out) {
        State st = new State();
        Path newDocument = out.resolveSibling(out.getFileName() + ".document.xml");
        try (ZipFile zip = new ZipFile(in.toFile())) {
            ZipEntry doc = zip.getEntry(DocxFormulaCounter.DOCUMENT_XML);
            if (doc == null) {
                throw MaterialErrors.invalid("File Word không có nội dung chính.");
            }
            try (InputStream src = new DocxFormulaCounter.MarkupGuard(zip.getInputStream(doc),
                    DocxFormulaCounter.MAX_XML_BYTES); OutputStream dst = Files.newOutputStream(newDocument)) {
                rewrite(src, dst, zip, st);
            }
            if (st.latex.isEmpty() && st.unreadable == 0) {
                return new Prepared(in, List.of(), 0, st.nonce);
            }
            writeZip(zip, newDocument, out);
        } catch (IOException | XMLStreamException e) {
            throw MaterialErrors.invalid("Không đọc được cấu trúc của file Word (file hỏng hoặc có nội dung không hợp lệ).");
        } finally {
            try {
                Files.deleteIfExists(newDocument);
            } catch (IOException ignored) {
                // thư mục tạm được dọn sau
            }
        }
        return new Prepared(out, List.copyOf(st.latex), st.unreadable, st.nonce);
    }

    /**
     * Đổi mã giữ chỗ trong markdown của pandoc: công thức đã chuyển thành {@code $latex$} ({@code $$latex$$} khi mã
     * chiếm trọn một dòng); công thức không đọc được thành {@code \[công thức\]} (thoát như pandoc thoát dấu ngoặc,
     * để bước làm sạch không đọc nhầm thành liên kết).
     */
    static String restore(String markdown, Prepared prepared) {
        if (prepared.total() == 0) {
            return markdown;
        }
        String prefix = Pattern.quote(TOKEN_OPEN + prepared.nonce());
        String tail = "(\\d+|" + UNREADABLE_INDEX + ")" + Pattern.quote(TOKEN_CLOSE);
        Pattern lone = Pattern.compile("(?m)^([ \\t]*)" + prefix + tail + "[ \\t]*$");
        String s = lone.matcher(markdown).replaceAll(m -> {
            String latex = latexOf(m.group(2), prepared);
            return Matcher.quoteReplacement(latex == null ? m.group() : m.group(1) + "$$" + latex + "$$");
        });
        return Pattern.compile(prefix + tail).matcher(s).replaceAll(m -> {
            String latex = latexOf(m.group(1), prepared);
            return Matcher.quoteReplacement(latex == null ? "\\[công thức\\]" : "$" + latex + "$");
        });
    }

    /** LaTeX của công thức có chỉ số {@code index}; null nếu không đọc được hoặc chỉ số không hợp lệ. */
    private static String latexOf(String index, Prepared prepared) {
        if (UNREADABLE_INDEX.equals(index)) {
            return null;
        }
        try {
            int i = Integer.parseInt(index);
            return i >= 0 && i < prepared.latex().size() ? prepared.latex().get(i) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String token(State st, int index) {
        return TOKEN_OPEN + st.nonce + index + TOKEN_CLOSE;
    }

    private static String unreadableToken(State st) {
        return TOKEN_OPEN + st.nonce + UNREADABLE_INDEX + TOKEN_CLOSE;
    }

    private static String newNonce() {
        byte[] b = new byte[6];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder(12);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }

    // ---------- ghi lại document.xml ----------

    private static void rewrite(InputStream src, OutputStream dst, ZipFile zip, State st)
            throws XMLStreamException, IOException {
        XMLEventReader reader = DocxFormulaCounter.secureFactory().createXMLEventReader(src);
        XMLEventWriter writer = XMLOutputFactory.newFactory().createXMLEventWriter(dst, "UTF-8");
        XMLEventFactory events = XMLEventFactory.newFactory();
        Deque<QName> open = new ArrayDeque<>();
        try {
            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                if (event.isStartElement() && isWord(event.asStartElement().getName(), "object")) {
                    object(event.asStartElement(), reader, writer, events, open, zip, st);
                    continue;
                }
                if (event.isStartElement()) {
                    open.push(event.asStartElement().getName());
                } else if (event.isEndElement()) {
                    open.pop();
                }
                writer.add(event);
            }
            writer.flush();
        } finally {
            reader.close();
            writer.close();
        }
    }

    /** Xử lý một {@code w:object}: thay bằng mã giữ chỗ nếu là công thức MathType, ngược lại ghi lại nguyên vẹn. */
    private static void object(StartElement start, XMLEventReader reader, XMLEventWriter writer,
                               XMLEventFactory events, Deque<QName> open, ZipFile zip, State st)
            throws XMLStreamException, IOException {
        List<XMLEvent> buffer = new ArrayList<>();
        buffer.add(start);
        String progId = null;
        String relId = null;
        boolean passThrough = false;
        int depth = 1;
        while (depth > 0) {
            XMLEvent e = reader.nextEvent();
            if (e.isStartElement()) {
                depth++;
                StartElement s = e.asStartElement();
                if (progId == null && NS_O.equals(s.getName().getNamespaceURI())
                        && "OLEObject".equals(s.getName().getLocalPart())) {
                    Attribute prog = s.getAttributeByName(new QName("ProgID"));
                    Attribute rel = s.getAttributeByName(new QName(NS_R, "id"));
                    progId = prog == null ? "" : prog.getValue();
                    relId = rel == null ? null : rel.getValue();
                }
            } else if (e.isEndElement()) {
                depth--;
            }
            if (passThrough) {
                writer.add(e);
            } else {
                buffer.add(e);
                if (buffer.size() > MAX_OBJECT_EVENTS) {
                    for (XMLEvent b : buffer) {
                        writer.add(b);
                    }
                    buffer.clear();
                    passThrough = true;
                }
            }
        }
        if (passThrough) {
            return;
        }
        if (progId == null || !progId.startsWith("Equation.")) {
            for (XMLEvent b : buffer) {
                writer.add(b);
            }
            return;
        }
        String latex = convert(progId, relId, zip, st);
        String text;
        if (latex == null) {
            st.unreadable++;
            text = unreadableToken(st);
        } else {
            text = token(st, st.latex.size());
            st.latex.add(latex);
        }
        writeText(start, text, writer, events, open);
    }

    /** Ghi {@code <w:t>text</w:t>}; thêm {@code <w:r>} bao ngoài nếu {@code w:object} không nằm trong một run. */
    private static void writeText(StartElement objectStart, String text, XMLEventWriter writer,
                                  XMLEventFactory events, Deque<QName> open) throws XMLStreamException {
        QName parent = open.peek();
        boolean inRun = parent != null && isWord(parent, "r");
        String prefix = objectStart.getName().getPrefix();
        if (!inRun) {
            writer.add(events.createStartElement(prefix, NS_W, "r"));
        }
        writer.add(events.createStartElement(prefix, NS_W, "t", List.of(events.createAttribute(
                new QName(XMLConstants.XML_NS_URI, "space", XMLConstants.XML_NS_PREFIX), "preserve")).iterator(), null));
        writer.add(events.createCharacters(text));
        writer.add(events.createEndElement(prefix, NS_W, "t"));
        if (!inRun) {
            writer.add(events.createEndElement(prefix, NS_W, "r"));
        }
    }

    private static boolean isWord(QName name, String local) {
        return NS_W.equals(name.getNamespaceURI()) && local.equals(name.getLocalPart());
    }

    // ---------- đọc và chuyển một công thức ----------

    /** @return LaTeX, hoặc null khi không đọc được vì bất kỳ lý do nào (không ném ngoại lệ vì dữ liệu của một công thức) */
    private static String convert(String progId, String relId, ZipFile zip, State st) throws XMLStreamException,
            IOException {
        if (st.latex.size() >= MAX_FORMULAS || !progId.startsWith("Equation.DSMT") || relId == null) {
            return null;
        }
        if (st.oleEntries == null) {
            st.oleEntries = readOleRelationships(zip);
        }
        String entryName = st.oleEntries.get(relId);
        ZipEntry entry = entryName == null ? null : zip.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            return null;
        }
        byte[] ole;
        try (InputStream in = zip.getInputStream(entry)) {
            ole = in.readNBytes(MAX_OLE_BYTES + 1);
        }
        if (ole.length > MAX_OLE_BYTES) {
            return null;
        }
        FormulaResult result;
        try {
            result = MtefConverter.fromOle(ole);
        } catch (RuntimeException | StackOverflowError e) {
            return null;
        }
        if (!result.hasLatex()) {
            return null;
        }
        String latex = result.latex().replaceAll("\\s+", " ").strip();
        return latex.isEmpty() ? null : latex;
    }

    /** Quan hệ kiểu oleObject trong document.xml.rels (bỏ liên kết ngoài): rId → tên mục trong zip. */
    private static Map<String, String> readOleRelationships(ZipFile zip) throws XMLStreamException, IOException {
        Map<String, String> out = new HashMap<>();
        ZipEntry rels = zip.getEntry(RELS_XML);
        if (rels == null) {
            return out;
        }
        try (InputStream in = new DocxFormulaCounter.MarkupGuard(zip.getInputStream(rels),
                DocxFormulaCounter.MAX_XML_BYTES)) {
            XMLStreamReader r = DocxFormulaCounter.secureFactory().createXMLStreamReader(in);
            try {
                while (r.hasNext() && out.size() < MAX_RELATIONSHIPS) {
                    if (r.next() != XMLStreamConstants.START_ELEMENT || !NS_PKG_REL.equals(r.getNamespaceURI())
                            || !"Relationship".equals(r.getLocalName())) {
                        continue;
                    }
                    String id = r.getAttributeValue(null, "Id");
                    String type = r.getAttributeValue(null, "Type");
                    String mode = r.getAttributeValue(null, "TargetMode");
                    String entry = resolve(r.getAttributeValue(null, "Target"));
                    if (id != null && entry != null && type != null && type.endsWith("/oleObject")
                            && !"External".equalsIgnoreCase(mode)) {
                        out.put(id, entry);
                    }
                }
            } finally {
                r.close();
            }
        }
        return out;
    }

    /** Chuẩn hóa Target (tương đối với word/ hoặc bắt đầu bằng /) thành tên mục zip; null nếu thoát khỏi gốc hoặc lạ. */
    static String resolve(String target) {
        if (target == null || target.isEmpty() || target.indexOf('\\') >= 0 || target.indexOf(':') >= 0
                || target.indexOf('\0') >= 0) {
            return null;
        }
        String full = target.startsWith("/") ? target.substring(1) : "word/" + target;
        Deque<String> parts = new ArrayDeque<>();
        for (String seg : full.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) {
                continue;
            }
            if (seg.equals("..")) {
                if (parts.isEmpty()) {
                    return null;
                }
                parts.removeLast();
            } else {
                parts.addLast(seg);
            }
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }

    // ---------- ghi docx mới ----------

    /** Chép mọi mục của zip gốc sang {@code out}, riêng word/document.xml lấy từ {@code newDocument}. */
    private static void writeZip(ZipFile zip, Path newDocument, Path out) throws IOException {
        try (ZipOutputStream zo = new ZipOutputStream(Files.newOutputStream(out))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                zo.putNextEntry(new ZipEntry(e.getName()));
                if (!e.isDirectory()) {
                    if (e.getName().equals(DocxFormulaCounter.DOCUMENT_XML)) {
                        Files.copy(newDocument, zo);
                    } else {
                        try (InputStream in = zip.getInputStream(e)) {
                            in.transferTo(zo);
                        }
                    }
                }
                zo.closeEntry();
            }
        }
    }
}
