package com.datn.mcq.mathtype.docx;

import static com.datn.mcq.mathtype.docx.DocxPackage.O;
import static com.datn.mcq.mathtype.docx.DocxPackage.R;
import static com.datn.mcq.mathtype.docx.DocxPackage.V;
import static com.datn.mcq.mathtype.docx.DocxPackage.W;

import java.util.Locale;

import com.datn.mcq.mathtype.ole.MathTypeOle;
import com.datn.mcq.mathtype.preview.PreviewRenderer.Preview;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Dựng run {@code w:r} chứa {@code w:object} (v:shape + v:imagedata + o:OLEObject ProgID="Equation.DSMT4"), theo đúng
 * mẫu Word lưu khi MathType chèn công thức (spike 24/10, spikes/mathtype-ole/BAO_CAO.md mục 1).
 */
public final class OleObjectRun {

    private OleObjectRun() {
    }

    /**
     * @param n             số thứ tự công thức (từ 1), dùng cho ShapeID
     * @param ridOle        Id quan hệ tới oleObject.bin
     * @param ridImg        Id quan hệ tới ảnh xem trước
     * @param withShapetype ghi v:shapetype (chỉ ở đối tượng đầu tiên, như Word)
     */
    public static Element create(Document doc, int n, String ridOle, String ridImg, Preview p, boolean withShapetype,
                                 long objectId) {
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

    /** Canh giữa đoạn chứa công thức riêng dòng (thay cho m:oMathPara). */
    public static void centerParagraph(Element p) {
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

    private static Element el(Document doc, Element parent, String ns, String qname) {
        Element e = doc.createElementNS(ns, qname);
        parent.appendChild(e);
        return e;
    }
}
