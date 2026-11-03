package com.datn.mcq.mathtype;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;

import com.datn.mcq.mathtype.docx.DocxPackage;
import com.datn.mcq.mathtype.docx.OleObjectRun;
import com.datn.mcq.mathtype.docx.PageGeometry;
import com.datn.mcq.mathtype.docx.ParagraphLines;
import com.datn.mcq.mathtype.layout.FormulaBreaker;
import com.datn.mcq.mathtype.layout.FormulaBreaker.Level;
import com.datn.mcq.mathtype.layout.FormulaBreaker.Piece;
import com.datn.mcq.mathtype.mtef.OmmlToMtef;
import com.datn.mcq.mathtype.ole.MathTypeOle;
import com.datn.mcq.mathtype.preview.PreviewRenderer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Hậu xử lý docx do pandoc sinh: mỗi m:oMath thành đối tượng MathType OLE (w:object + v:shape + v:imagedata PNG +
 * o:OLEObject ProgID="Equation.DSMT4"), giống cấu trúc Word lưu khi MathType chèn công thức. Công thức nào chuyển
 * MTEF không được (cấu trúc/ký hiệu chưa hỗ trợ, JLaTeXMath không dựng được ảnh) mới giữ nguyên dạng Equation (OMML);
 * KHÔNG bao giờ giữ OMML vì lý do bố cục — giáo viên không có MathType trả phí thì không Convert Equations được.
 *
 * <p>Bố cục (đối tượng OLE không tự xuống dòng): công thức rộng hơn lề chữ (khổ giấy − lề − thụt lề đoạn) thì
 * (1) ngắt tại dấu quan hệ cấp ngoài cùng, (2) đoạn còn rộng thì ngắt tại +/− cấp ngoài cùng, (3) khối không ngắt được
 * thì thu nhỏ kích thước hiển thị của riêng đối tượng đó. Mỗi đoạn là một đối tượng OLE trên một dòng riêng (đoạn
 * văn riêng, canh giữa); chữ trước/sau công thức giữ trong đoạn gốc / đoạn kế tiếp. Chi tiết ở {@link FormulaBreaker}.
 *
 * <p>LaTeX gốc của công thức thứ i (để dựng ảnh) lấy theo thứ tự xuất hiện: pandoc sinh đúng một m:oMath cho mỗi
 * $...$ theo thứ tự trong Markdown (danh sách lấy từ {@code pandoc -t json}). Số lượng lệch nhau thì ném
 * {@link MathTypeConversionException}, không đoán.
 *
 * <p>Thuần Java, không phụ thuộc module khác. Chuyển từ spike 24/10 (spikes/mathtype-ole/java-proto).
 */
public final class MathTypeDocxConverter {

    static final String REL_OLE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/oleObject";
    static final String REL_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";
    /** Sai số khi so bề rộng với lề (pt), để công thức vừa khít lề không bị coi là tràn vì làm tròn. */
    private static final double TOLERANCE_PT = 0.01;

    /**
     * @param docx      file đã chuyển
     * @param converted số công thức thành đối tượng MathType (một công thức bị ngắt dòng vẫn tính là một)
     * @param keptOmml  số công thức giữ dạng Equation của Word (chuyển MTEF thất bại thật)
     * @param scaled    số công thức có đối tượng bị thu nhỏ cho vừa lề (khối rộng không ngắt được)
     * @param notes     mỗi công thức giữ OMML một dòng "n: lý do LaTeX" (n đếm từ 1)
     */
    public record Result(byte[] docx, int converted, int keptOmml, int scaled, List<String> notes) { }

    /** Một đoạn công thức sẽ thành một đối tượng OLE, kèm bề rộng ảnh xem trước (pt). */
    private record Seg(Piece piece, double width) { }

    /** Công thức không chuyển được sang MathType (lý do dùng cho ghi chú). */
    private static final class KeepOmml extends Exception {
        KeepOmml(String reason) {
            super(reason);
        }
    }

    private MathTypeDocxConverter() {
    }

    /** Số công thức dạng Equation của Word (m:oMath) trong docx; 0 nếu không đọc được file. */
    public static int countEquations(byte[] docx) {
        try {
            return DocxPackage.read(docx).document().getElementsByTagNameNS(OmmlToMtef.M, "oMath").getLength();
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    /**
     * @param docx  docx do pandoc sinh (công thức dạng OMML)
     * @param latex LaTeX của từng công thức, đúng thứ tự xuất hiện
     * @throws MathTypeConversionException docx không đọc/ghi được, hoặc số công thức khác số LaTeX
     */
    public static Result convert(byte[] docx, List<String> latex) {
        DocxPackage pkg;
        try {
            pkg = DocxPackage.read(docx);
        } catch (IOException e) {
            throw new MathTypeConversionException("Không đọc được file docx: " + e.getMessage(), e);
        }
        Document doc = pkg.document();

        NodeList found = doc.getElementsByTagNameNS(OmmlToMtef.M, "oMath");
        List<Element> maths = new ArrayList<>();
        for (int i = 0; i < found.getLength(); i++) {
            maths.add((Element) found.item(i));
        }
        if (maths.size() != latex.size()) {
            throw new MathTypeConversionException(
                    "Số m:oMath (" + maths.size() + ") khác số LaTeX (" + latex.size() + ")");
        }
        Element root = doc.getDocumentElement();
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:v", DocxPackage.V);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:o", DocxPackage.O);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:r", DocxPackage.R);

        List<String> notes = new ArrayList<>();
        int converted = 0;
        int scaledFormulas = 0;
        int seq = 0; // số đối tượng OLE đã ghi (một công thức bị ngắt cho nhiều đối tượng)
        boolean shapetypeWritten = false;
        long objectId = 1_700_000_000L;
        for (int i = 0; i < maths.size(); i++) {
            Element m = maths.get(i);
            int n = i + 1;
            boolean display = m.getParentNode() instanceof Element p && "oMathPara".equals(p.getLocalName());
            Node target = display ? m.getParentNode() : m; // m:oMathPara bọc m:oMath với công thức hiển thị
            Element holder = target.getParentNode() instanceof Element p && isParagraph(p) ? p : null;
            Element anyParagraph = holder != null ? holder : ancestorParagraph(target);
            double avail = anyParagraph != null ? PageGeometry.availableWidthPt(pkg, anyParagraph)
                    : PageGeometry.textWidthPt(pkg);

            List<Seg> segs;
            List<byte[]> mtefs;
            List<PreviewRenderer.Preview> previews;
            boolean standalone;
            try {
                segs = layout(m, latex.get(i), avail, holder != null);
                standalone = isStandalone(segs, avail);
                try {
                    mtefs = new ArrayList<>();
                    previews = new ArrayList<>();
                    prepare(doc, m, segs, !display && !standalone, mtefs, previews);
                } catch (KeepOmml | RuntimeException e) {
                    if (segs.size() == 1) {
                        throw e;
                    }
                    // một đoạn không dựng được: bỏ ngắt dòng, giữ cả công thức làm một đối tượng (thu nhỏ nếu tràn)
                    segs = List.of(whole(m, latex.get(i)));
                    standalone = isStandalone(segs, avail);
                    mtefs = new ArrayList<>();
                    previews = new ArrayList<>();
                    prepare(doc, m, segs, !display && !standalone, mtefs, previews);
                }
            } catch (KeepOmml e) {
                notes.add(n + ": giữ OMML (" + e.getMessage() + ") " + latex.get(i));
                continue;
            } catch (OmmlToMtef.Unsupported e) {
                notes.add(n + ": giữ OMML (chưa hỗ trợ " + e.getMessage() + ") " + latex.get(i));
                continue;
            } catch (RuntimeException e) {
                notes.add(n + ": giữ OMML (không dựng được ảnh: " + e.getMessage() + ") " + latex.get(i));
                continue;
            }

            List<Element> runs = new ArrayList<>();
            boolean anyScaled = false;
            for (int k = 0; k < segs.size(); k++) {
                seq++;
                PreviewRenderer.Preview preview = previews.get(k);
                String bin = "embeddings/oleObjectMT" + seq + ".bin";
                String png = "media/imageMT" + seq + ".png";
                pkg.put("word/" + bin, MathTypeOle.build(mtefs.get(k)));
                pkg.put("word/" + png, preview.png());
                String ridOle = pkg.addRelationship(REL_OLE, bin);
                String ridImg = pkg.addRelationship(REL_IMAGE, png);
                double scale = preview.widthPt() > avail + TOLERANCE_PT ? avail / preview.widthPt() : 1.0;
                anyScaled |= scale < 1.0;
                runs.add(OleObjectRun.create(doc, seq, ridOle, ridImg, preview, !shapetypeWritten, objectId + seq, scale));
                shapetypeWritten = true;
            }
            if (!standalone) {
                if (display) {
                    OleObjectRun.centerParagraph((Element) target.getParentNode());
                }
                target.getParentNode().replaceChild(runs.get(0), target);
            } else if (holder != null) {
                ParagraphLines.replaceWithLines(pkg, holder, target, runs);
            } else {
                target.getParentNode().replaceChild(runs.get(0), target); // lồng trong khối khác: chỉ thu nhỏ tại chỗ
            }
            converted++;
            if (anyScaled) {
                scaledFormulas++;
            }
        }
        pkg.ensureDefaultContentType("bin", "application/vnd.openxmlformats-officedocument.oleObject");
        pkg.ensureDefaultContentType("png", "image/png");
        try {
            return new Result(pkg.write(), converted, maths.size() - converted, scaledFormulas, List.copyOf(notes));
        } catch (IOException e) {
            throw new MathTypeConversionException("Không ghi được file docx: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- bố cục

    /**
     * Chia công thức thành các đoạn vừa lề: công thức vừa lề (hoặc không thể tách khỏi đoạn) giữ nguyên; rộng hơn thì
     * ngắt theo quan hệ, rồi đoạn còn rộng ngắt theo +/−. Đoạn nào vẫn rộng hơn lề sẽ bị thu nhỏ ở bước ghi.
     */
    private static List<Seg> layout(Element m, String latex, double avail, boolean splittable) {
        Seg whole = whole(m, latex);
        if (!splittable || whole.width() <= avail + TOLERANCE_PT) {
            return List.of(whole);
        }
        try {
            List<Seg> out = new ArrayList<>();
            for (Piece byRelation : FormulaBreaker.split(whole.piece(), Level.RELATION)) {
                Seg seg = byRelation == whole.piece() ? whole : measure(byRelation);
                if (seg.width() <= avail + TOLERANCE_PT) {
                    out.add(seg);
                    continue;
                }
                for (Piece byAdditive : FormulaBreaker.split(byRelation, Level.ADDITIVE)) {
                    out.add(byAdditive == byRelation ? seg : measure(byAdditive));
                }
            }
            return out;
        } catch (RuntimeException e) {
            return List.of(whole); // JLaTeXMath không đo được một đoạn: không ngắt, để bước ghi thu nhỏ cả công thức
        }
    }

    private static Seg whole(Element m, String latex) {
        return measure(new Piece(latex, elementChildren(m)));
    }

    private static Seg measure(Piece piece) {
        return new Seg(piece, PreviewRenderer.widthPt(piece.latex()));
    }

    /** Công thức đứng thành dòng riêng: bị ngắt, hoặc (không ngắt được) tràn lề nên phải thu nhỏ. */
    private static boolean isStandalone(List<Seg> segs, double avail) {
        return segs.size() > 1 || segs.get(0).width() > avail + TOLERANCE_PT;
    }

    /** Dựng MTEF và ảnh xem trước cho từng đoạn; ném nếu có đoạn không chuyển được. */
    private static void prepare(Document doc, Element m, List<Seg> segs, boolean inline, List<byte[]> mtefs,
                                List<PreviewRenderer.Preview> previews) throws KeepOmml {
        for (Seg seg : segs) {
            Element oMath = segs.size() == 1 ? m : oMathOf(doc, seg.piece().children());
            mtefs.add(OmmlToMtef.convert(oMath, inline));
            try {
                previews.add(PreviewRenderer.render(seg.piece().latex()));
            } catch (RuntimeException e) {
                throw new KeepOmml("không dựng được ảnh: " + e.getMessage());
            }
        }
    }

    private static Element oMathOf(Document doc, List<Element> children) {
        Element oMath = doc.createElementNS(OmmlToMtef.M, "m:oMath");
        for (Element c : children) {
            oMath.appendChild(c.cloneNode(true));
        }
        return oMath;
    }

    private static List<Element> elementChildren(Element parent) {
        List<Element> out = new ArrayList<>();
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e) {
                out.add(e);
            }
        }
        return out;
    }

    private static boolean isParagraph(Element e) {
        return DocxPackage.W.equals(e.getNamespaceURI()) && "p".equals(e.getLocalName());
    }

    private static Element ancestorParagraph(Node n) {
        for (Node a = n.getParentNode(); a != null; a = a.getParentNode()) {
            if (a instanceof Element e && isParagraph(e)) {
                return e;
            }
        }
        return null;
    }
}
