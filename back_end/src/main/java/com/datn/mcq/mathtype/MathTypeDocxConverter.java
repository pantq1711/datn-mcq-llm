package com.datn.mcq.mathtype;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;

import com.datn.mcq.mathtype.docx.DocxPackage;
import com.datn.mcq.mathtype.docx.OleObjectRun;
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
 * không được (cấu trúc chưa hỗ trợ, JLaTeXMath không dựng được ảnh) giữ nguyên dạng Equation (OMML).
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

    /**
     * @param docx      file đã chuyển
     * @param converted số công thức thành đối tượng MathType
     * @param keptOmml  số công thức giữ dạng Equation của Word
     * @param notes     mỗi công thức giữ OMML một dòng "n: lý do LaTeX" (n đếm từ 1)
     */
    public record Result(byte[] docx, int converted, int keptOmml, List<String> notes) { }

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
                notes.add(n + ": giữ OMML (chưa hỗ trợ " + e.getMessage() + ") " + latex.get(i));
                continue;
            }
            try {
                preview = PreviewRenderer.render(latex.get(i));
            } catch (RuntimeException e) {
                notes.add(n + ": giữ OMML (không dựng được ảnh: " + e.getMessage() + ") " + latex.get(i));
                continue;
            }
            String bin = "embeddings/oleObjectMT" + n + ".bin";
            String png = "media/imageMT" + n + ".png";
            pkg.put("word/" + bin, MathTypeOle.build(mtef));
            pkg.put("word/" + png, preview.png());
            String ridOle = pkg.addRelationship(REL_OLE, bin);
            String ridImg = pkg.addRelationship(REL_IMAGE, png);

            Element run = OleObjectRun.create(doc, n, ridOle, ridImg, preview, !shapetypeWritten, objectId + n);
            shapetypeWritten = true;
            Node target = display ? m.getParentNode() : m; // m:oMathPara bọc m:oMath với công thức hiển thị
            if (display) {
                OleObjectRun.centerParagraph((Element) target.getParentNode());
            }
            target.getParentNode().replaceChild(run, target);
            converted++;
        }
        pkg.ensureDefaultContentType("bin", "application/vnd.openxmlformats-officedocument.oleObject");
        pkg.ensureDefaultContentType("png", "image/png");
        try {
            return new Result(pkg.write(), converted, maths.size() - converted, List.copyOf(notes));
        } catch (IOException e) {
            throw new MathTypeConversionException("Không ghi được file docx: " + e.getMessage(), e);
        }
    }
}
