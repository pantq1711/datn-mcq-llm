package com.datn.mcq.mathtype;

import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Hỗ trợ test module MathType: giải mã MTEF v5 thành cây chữ để so cấu trúc (chuyển từ
 * spikes/mathtype-ole/tools/mtef_dump.py) và đọc docx theo đoạn văn như tools/compare_mtef.py.
 */
public final class MtefTestSupport {

    public static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    public static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    public static final String O = "urn:schemas-microsoft-com:office:office";
    public static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static final String[] TMPL = {"tmANGLE", "tmPAREN", "tmBRACE", "tmBRACK", "tmBAR", "tmDBAR", "tmFLOOR",
            "tmCEILING", "tmOBRACK", "tmINTERVAL", "tmROOT", "tmFRACT", "tmUBAR", "tmOBAR", "tmARROW", "tmINTEG",
            "tmSUM", "tmPROD", "tmCOPROD", "tmUNION", "tmINTER", "tmINTOP", "tmSUMOP", "tmLIM", "tmHBRACE",
            "tmHBRACK", "tmLDIV", "tmSUB", "tmSUP", "tmSUBSUP", "tmDIRAC", "tmVEC", "tmTILDE", "tmHAT", "tmARC",
            "tmJSTATUS", "tmSTRIKE", "tmBOX"};
    private static final Map<Integer, String> FACE = Map.ofEntries(Map.entry(1, "TEXT"), Map.entry(2, "FUNC"),
            Map.entry(3, "VAR"), Map.entry(4, "LCGREEK"), Map.entry(5, "UCGREEK"), Map.entry(6, "SYM"),
            Map.entry(7, "VEC"), Map.entry(8, "NUM"), Map.entry(11, "MTEXTRA"), Map.entry(12, "TEXT_FE"),
            Map.entry(22, "EXPAND"), Map.entry(23, "MARKER"), Map.entry(24, "SPACE"));

    /** Dòng bị bỏ khi so: màu, record tương lai (TeX gốc MathType 7 lưu), header và các record định nghĩa. */
    private static final String[] SKIP = {"COLOR", "FUTURE", "MTEF v", "FONT_DEF", "ENCODING_DEF", "EQN_PREFS",
            "FONT_STYLE_DEF"};

    private MtefTestSupport() {
    }

    /** Một đoạn văn không rỗng của docx: OLE MathType (MTEF), công thức OMML, hoặc chỉ có chữ. */
    public record Para(byte[] mtef, Element oMath, boolean display) {
    }

    // ---------------------------------------------------------------- docx

    public static byte[] resource(String name) {
        try (InputStream in = MtefTestSupport.class.getResourceAsStream("/mathtype/" + name)) {
            if (in == null) {
                throw new IllegalStateException("thiếu resource /mathtype/" + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Danh sách LaTeX (mỗi dòng một công thức, bỏ dòng trống), cùng thứ tự với các đoạn của docx mẫu. */
    public static List<String> latexList(String name) {
        return new String(resource(name), StandardCharsets.UTF_8).lines().map(String::strip)
                .filter(s -> !s.isEmpty()).toList();
    }

    public static Map<String, byte[]> unzip(byte[] docx) {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                parts.put(e.getName(), z.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parts;
    }

    public static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Các đoạn không rỗng theo thứ tự (cùng quy tắc ghép đoạn của compare_mtef.py). */
    public static List<Para> paragraphs(byte[] docx) {
        Map<String, byte[]> parts = unzip(docx);
        Document doc = parse(parts.get("word/document.xml"));
        Map<String, String> target = new HashMap<>();
        NodeList rels = parse(parts.get("word/_rels/document.xml.rels")).getDocumentElement().getChildNodes();
        for (int i = 0; i < rels.getLength(); i++) {
            if (rels.item(i) instanceof Element rel) {
                target.put(rel.getAttribute("Id"), "word/" + rel.getAttribute("Target"));
            }
        }
        List<Para> out = new ArrayList<>();
        NodeList ps = doc.getElementsByTagNameNS(W, "p");
        for (int i = 0; i < ps.getLength(); i++) {
            Element p = (Element) ps.item(i);
            Element ole = first(p, O, "OLEObject");
            Element oMath = first(p, M, "oMath");
            if (ole != null) {
                out.add(new Para(equationNative(parts.get(target.get(ole.getAttributeNS(R, "id")))), null, false));
            } else if (oMath != null) {
                boolean display = oMath.getParentNode() instanceof Element pe && "oMathPara".equals(pe.getLocalName());
                out.add(new Para(null, oMath, display));
            } else if (hasText(p)) {
                out.add(new Para(null, null, false));
            }
        }
        return out;
    }

    public static int count(byte[] xml, String ns, String localName) {
        return parse(xml).getElementsByTagNameNS(ns, localName).getLength();
    }

    private static Element first(Element p, String ns, String localName) {
        NodeList l = p.getElementsByTagNameNS(ns, localName);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    private static boolean hasText(Element p) {
        NodeList ts = p.getElementsByTagNameNS(W, "t");
        for (int i = 0; i < ts.getLength(); i++) {
            if (!ts.item(i).getTextContent().isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** Luồng "Equation Native" của oleObject.bin, bỏ header EQNOLEFILEHDR, trả MTEF. */
    public static byte[] equationNative(byte[] ole) {
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(ole));
             DocumentInputStream in = fs.createDocumentInputStream("Equation Native")) {
            byte[] data = in.readAllBytes();
            ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int cbHdr = b.getShort(0) & 0xFFFF;
            int cbObj = b.getInt(8);
            byte[] mtef = new byte[cbObj];
            System.arraycopy(data, cbHdr, mtef, 0, cbObj);
            return mtef;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- MTEF

    /** Cây MTEF dạng dòng chữ (thụt lề theo độ sâu), đã bỏ các dòng {@link #SKIP}. */
    public static List<String> dump(byte[] mtef) {
        Reader r = new Reader(mtef);
        for (int i = 0; i < 5; i++) {
            r.u8();
        }
        r.cstr();
        r.u8();
        List<String> body = new ArrayList<>();
        while (!r.eof()) {
            if (!record(r, 0, body)) {
                body.add("END");
            }
        }
        List<String> out = new ArrayList<>();
        for (String line : body) {
            String s = line.strip();
            boolean skip = false;
            for (String p : SKIP) {
                skip |= s.startsWith(p);
            }
            if (!skip) {
                out.add(line);
            }
        }
        return out;
    }

    private static final class Reader {
        private final byte[] d;
        private int p;

        Reader(byte[] d) {
            this.d = d;
        }

        int u8() {
            return d[p++] & 0xFF;
        }

        int u16() {
            int v = (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8);
            p += 2;
            return v;
        }

        int uint() {
            int v = u8();
            return v == 255 ? u16() : v;
        }

        String cstr() {
            int start = p;
            while (d[p] != 0) {
                p++;
            }
            String s = new String(d, start, p - start, StandardCharsets.ISO_8859_1);
            p++;
            return s;
        }

        boolean eof() {
            return p >= d.length;
        }
    }

    private static void nudge(Reader r, int opts) {
        if ((opts & 0x08) != 0) {
            int dx = r.u8();
            int dy = r.u8();
            if (dx == 128 && dy == 128) {
                r.u16();
                r.u16();
            }
        }
    }

    private static void dimArray(Reader r) {
        int count = r.u8();
        int done = 0;
        while (done < count) {
            int b = r.u8();
            for (int n : new int[]{b >> 4, b & 0xF}) {
                if (n == 0xF) {
                    done++;
                    if (done == count) {
                        break;
                    }
                }
            }
        }
    }

    private static void ruler(Reader r) {
        int tag = r.u8();
        if (tag != 7) {
            throw new IllegalStateException("cần RULER, gặp " + tag);
        }
        int n = r.u8();
        for (int i = 0; i < n; i++) {
            r.u8();
            r.u16();
        }
    }

    private static void list(Reader r, int depth, List<String> out) {
        while (!r.eof()) {
            if (!record(r, depth, out)) {
                return;
            }
        }
    }

    /** Đọc một record; false nếu là END. */
    private static boolean record(Reader r, int depth, List<String> out) {
        String pad = "  ".repeat(depth);
        int t = r.u8();
        switch (t) {
            case 0 -> {
                return false;
            }
            case 1 -> {
                int o = r.u8();
                nudge(r, o);
                if ((o & 0x04) != 0) {
                    r.u16();
                }
                if ((o & 0x02) != 0) {
                    ruler(r);
                }
                if ((o & 0x01) != 0) {
                    out.add(pad + "LINE(null)");
                } else {
                    out.add(pad + "LINE");
                    list(r, depth + 1, out);
                }
            }
            case 2 -> {
                int o = r.u8();
                nudge(r, o);
                int face = r.u8() - 128;
                Integer code = (o & 0x20) != 0 ? null : r.u16();
                Integer fp = (o & 0x04) != 0 ? Integer.valueOf(r.u8()) : (o & 0x10) != 0 ? Integer.valueOf(r.u16()) : null;
                out.add(pad + "CHAR " + FACE.getOrDefault(face, Integer.toString(face))
                        + String.format(" U+%04X", code == null ? 0 : code)
                        + (fp == null ? "" : String.format(" fontpos=0x%02X", fp))
                        + ((o & 0x02) != 0 ? " funcstart" : ""));
                if ((o & 0x01) != 0) {
                    list(r, depth + 1, out);
                }
            }
            case 3 -> {
                int o = r.u8();
                nudge(r, o);
                int sel = r.u8();
                int v = r.u8();
                if ((v & 0x80) != 0) {
                    v = (v & 0x7F) | (r.u8() << 8);
                }
                int topt = r.u8();
                out.add(pad + "TMPL " + (sel < TMPL.length ? TMPL[sel] : Integer.toString(sel))
                        + String.format(" var=0x%X opt=%d", v, topt));
                list(r, depth + 1, out);
            }
            case 4 -> {
                int o = r.u8();
                nudge(r, o);
                int ha = r.u8();
                int va = r.u8();
                if ((o & 0x02) != 0) {
                    ruler(r);
                }
                out.add(pad + "PILE halign=" + ha + " valign=" + va);
                list(r, depth + 1, out);
            }
            case 5 -> {
                int o = r.u8();
                nudge(r, o);
                int va = r.u8();
                int hj = r.u8();
                int vj = r.u8();
                int rows = r.u8();
                int cols = r.u8();
                r.p += ((rows + 1) * 2 + 7) / 8;
                r.p += ((cols + 1) * 2 + 7) / 8;
                out.add(pad + "MATRIX " + rows + "x" + cols + " valign=" + va + " hjust=" + hj + " vjust=" + vj);
                list(r, depth + 1, out);
            }
            case 6 -> {
                int o = r.u8();
                nudge(r, o);
                out.add(pad + "EMBELL type=" + r.u8());
            }
            case 7 -> {
                r.p--;
                ruler(r);
                out.add(pad + "RULER");
            }
            case 8 -> {
                int idx = r.uint();
                int st = r.u8();
                out.add(pad + "FONT_STYLE_DEF font=" + idx + " style=" + st);
            }
            case 9 -> {
                int a = r.u8();
                if (a == 101) {
                    out.add(pad + "SIZE explicit=" + (short) r.u16());
                } else if (a == 100) {
                    int ls = r.u8();
                    out.add(pad + "SIZE lsize=" + ls + " dsize=" + r.u16());
                } else {
                    out.add(pad + "SIZE lsize=" + a + " dsize=" + (r.u8() - 128));
                }
            }
            case 10, 11, 12, 13, 14 -> out.add(pad + new String[]{"FULL", "SUB", "SUB2", "SYM", "SUBSYM"}[t - 10]);
            case 15 -> out.add(pad + "COLOR " + r.uint());
            case 16 -> {
                int o = r.u8();
                for (int i = 0; i < ((o & 1) != 0 ? 4 : 3); i++) {
                    r.u16();
                }
                if ((o & 4) != 0) {
                    r.cstr();
                }
                out.add(pad + "COLOR_DEF");
            }
            case 17 -> {
                int idx = r.uint();
                out.add(pad + "FONT_DEF enc=" + idx + " " + r.cstr());
            }
            case 18 -> {
                r.u8();
                dimArray(r);
                dimArray(r);
                int n = r.u8();
                for (int i = 0; i < n; i++) {
                    if (r.uint() != 0) {
                        r.u8();
                    }
                }
                out.add(pad + "EQN_PREFS");
            }
            case 19 -> out.add(pad + "ENCODING_DEF " + r.cstr());
            default -> {
                if (t < 100) {
                    throw new IllegalStateException("record MTEF không biết: " + t + " tại byte " + (r.p - 1));
                }
                int n = r.uint();
                r.p += n;
                out.add(pad + "FUTURE type=" + t);
            }
        }
        return true;
    }
}
