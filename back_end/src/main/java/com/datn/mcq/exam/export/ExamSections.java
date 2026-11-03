package com.datn.mcq.exam.export;

import com.datn.mcq.mathtype.docx.DocxPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Hai section của file xuất (4/11): section đề kết thúc ở sectPr nằm trong đoạn "------ HẾT ------" (do
 * {@link ExamMarkdownBuilder} viết), section đáp án là sectPr cuối tài liệu (chép từ file mẫu, {@link ExamReferenceDoc}).
 * Pandoc chỉ mang theo chân trang mà sectPr cuối trỏ tới, nên chân trang của section đề ("Trang x/y – Mã đề 101") được
 * thêm ở đây, sau pandoc (và sau bước MathType nếu có): part, quan hệ và kiểu nội dung.
 */
final class ExamSections {

    private static final Logger log = LoggerFactory.getLogger(ExamSections.class);
    private static final String PART = "word/footerExam.xml";

    private ExamSections() {
    }

    /**
     * Thêm chân trang section đề nếu tài liệu có sectPr trỏ tới {@link ExamPage#EXAM_FOOTER_REL_ID}. Tài liệu không có
     * (không phải đề) hoặc không đọc được thì trả nguyên.
     */
    static byte[] ensureExamFooter(byte[] docx) {
        try {
            DocxPackage pkg = DocxPackage.read(docx);
            if (!referencesExamFooter(pkg)) {
                return docx;
            }
            pkg.put(PART, ExamReferenceDoc.footerXml(ExamReferenceDoc.EXAM_FOOTER_SUFFIX).getBytes(StandardCharsets.UTF_8));
            pkg.ensureOverrideContentType("/" + PART, ExamReferenceDoc.FOOTER_CONTENT_TYPE);
            pkg.addRelationship(ExamPage.EXAM_FOOTER_REL_ID, ExamReferenceDoc.FOOTER_REL_TYPE, "footerExam.xml");
            return pkg.write();
        } catch (IOException | RuntimeException e) {
            log.warn("Không thêm được chân trang section đề: {}", e.toString());
            return docx;
        }
    }

    private static boolean referencesExamFooter(DocxPackage pkg) {
        NodeList refs = pkg.document().getElementsByTagNameNS(DocxPackage.W, "footerReference");
        for (int i = 0; i < refs.getLength(); i++) {
            if (ExamPage.EXAM_FOOTER_REL_ID.equals(((Element) refs.item(i)).getAttributeNS(DocxPackage.R, "id"))) {
                return true;
            }
        }
        return false;
    }
}
