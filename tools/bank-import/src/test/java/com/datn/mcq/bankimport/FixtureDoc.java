package com.datn.mcq.bankimport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.datn.mcq.bankimport.DocxBuilder.*;
import static com.datn.mcq.bankimport.MtefBuilder.*;

/**
 * Tài liệu giả tái hiện các biến thể quan sát được trong 4 bộ câu hỏi thật (nội dung hoàn toàn tự bịa):
 * số câu tự động và "Câu N.", nhãn mức tô cyan hay chữ thường, đáp án tô vàng ở nhiều phạm vi, phương án nhiều
 * cột trong một đoạn, bảng bố cục, phần II ba biến thể, dòng "Đáp án: Đ S S Đ", bảng gộp, lời giải có numPr,
 * văn bản NFD, chữ mũ gõ tay, ảnh nhỏ/lớn, bảng tiêu đề chứa tên giáo viên và dòng quảng cáo cuối file.
 */
final class FixtureDoc {

    static final String FILE_NAME = "KHỐI 8 - CHƯƠNG 9 - BÀI 9 - ĐỊNH LÝ GIẢ - GV NGUYỄN VĂN GIẢ pb.docx";

    /** Chuỗi không được xuất hiện trong bất kỳ output nào. */
    static final List<String> FORBIDDEN = List.of("NGUYỄN VĂN GIẢ", "Nguyễn Văn Giả", "TRƯỜNG GIẢ SỐ MỘT", "Quangcao",
            "quangcao", "Tài liệu được chia sẻ", "http");

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final byte[] WMF = {(byte) 0xD7, (byte) 0xCD, (byte) 0xC6, (byte) 0x9A};

    static Path write(Path dir) throws IOException {
        DocxBuilder d = new DocxBuilder();
        byte[] frac34 = ole(mtef(frac(num("3"), num("4"))));
        byte[] sumFormula = ole(mtef(tmpl(T_SUM, 0, line(var("i")), nullLine(), nullLine(), ch(SYM, 0x2211))));

        // bảng tiêu đề đầu file: trường và tên giáo viên (phải bị bỏ hoàn toàn)
        d.table(List.of(
                List.of(cell(cellParas(t("TRƯỜNG GIẢ SỐ MỘT"))), cell(cellParas(t("ĐỀ LUYỆN TẬP")))),
                List.of(cell(cellParas(t("GV NGUYỄN VĂN GIẢ"))), cell(cellParas(t("Lớp 8"))))));
        d.p("");
        d.p("PHẦN I. CÂU TRẮC NGHIỆM NHIỀU PHƯƠNG ÁN LỰA CHỌN");

        // Q1: số tự động + [NB] cyan, mỗi phương án một đoạn, tô vàng chỉ phần chữ, có công thức
        d.numbered(cy("[NB]"), t(" Câu hỏi giả một có "), d.eq(frac34), t(" trong đề."));
        d.p("A. phương án A").p("B. phương án B").p(t("C. "), y("phương án C")).p("D. phương án D");

        // Q2: hai phương án một đoạn, tab, tô vàng bắt đầu từ dấu chấm của nhãn
        d.numbered(cy("[TH]"), t(" Câu hai."));
        d.p(tab(), t("A. 1"), tab(), tab(), t("B"), y(". hai"), tab());
        d.p(tab(), t("C. 3"), tab(), tab(), t("D. 4"));

        // Q3: "Câu 3." gõ tay, nhãn không tô, phương án nằm trong bảng bố cục kèm ảnh ở ô bên
        d.p(t("Câu 3. "), t("[VD]"), t(" Câu ba với bảng bố cục."));
        d.table(List.of(List.of(
                cell(cellParas(t("A. một")), cellParas(t("B. hai")), cellParas(t("C. ba")), cellParas(t("D. "), y("bốn"))),
                cell(cellParas(d.drawing("png", PNG, 4, 3))))));

        // Q4: bốn phương án trong một đoạn, nhãn mức đầy đủ
        d.numbered(cy("[VẬN DỤNG CAO]"), t(" Câu bốn."));
        d.p(t("A. a   B. b   C. c   D. "), y("d"));

        // Q5: có lời giải, trong lời giải có dòng numPr (không phải câu mới)
        d.numbered(cy("[NB]"), t(" Câu năm có lời giải."));
        d.p(t("A. "), y("đúng")).p("B. sai").p("C. sai").p("D. sai");
        d.p(t("Hướng dẫn giải: "), t("Bước một."));
        d.numbered(t("Bước hai có numPr."));

        // Q6: bảng dữ liệu trong đề + hình lớn + phương án B là ảnh công thức WMF nhỏ
        d.numbered(cy("[TH]"), t(" Câu sáu có bảng dữ liệu và hình."));
        d.table(List.of(List.of(cell(cellParas(t("x"))), cell(cellParas(t("1")))),
                List.of(cell(cellParas(t("y"))), cell(cellParas(t("2"))))));
        d.p(d.drawing("png", PNG, 5, 4));
        d.p(t("A. "), y("có")).p(t("B. "), d.pict("wmf", WMF, 0.6, 0.5)).p("C. ba").p("D. bốn");

        // Q7: văn bản NFD (dấu tách rời) và chữ mũ gõ tay
        d.numbered(cy("[NB]"), t(" Toán học BC"), sup("2"), t(" bằng"));
        d.p("A. một").p(t("B. "), y("hai")).p("C. ba").p("D. bốn");

        // Q8: chú thích "Hình 1" sau phương án D
        d.numbered(cy("[NB]"), t(" Câu tám."), d.eq(ole(mtefEmpty())));   // đối tượng công thức rỗng: bỏ qua có cờ
        d.p("A. một").p("B. hai").p("C. ba").p(t("D. "), y("bốn"));
        d.p(tab(), tab(), t("Hình 1"));

        // Q9: không có nhãn mức
        d.numbered(t("Câu 9. Câu chín không có nhãn mức."));
        d.p(t("A. "), y("một")).p("B. hai").p("C. ba").p("D. bốn");

        // Q10: không tô đáp án nào
        d.numbered(cy("[TH]"), t(" Câu mười không tô đáp án."));
        d.p("A. một").p("B. hai").p("C. ba").p("D. bốn");

        // Q11: tô hai đáp án
        d.numbered(cy("[TH]"), t(" Câu mười một tô hai đáp án."));
        d.p(t("A. "), y("một")).p("B. hai").p(t("C. "), y("ba")).p("D. bốn");

        // Q12: nội dung phương án chứa chuỗi giống nhãn ("Điểm C." trong phương án B) không được nhận làm nhãn
        d.numbered(cy("[TH]"), t(" Câu mười hai."));
        d.p("A. Điểm D.");
        d.p(y("B. Điểm C."), tab(), tab());
        d.p("C. Điểm B.").p("D. Điểm A.");

        d.p("PHẦN II. CÂU TRẮC NGHIỆM ĐÚNG SAI");

        // G1: bảng 6 hàng, hàng đề gộp ô, ý đúng tô vàng ở cột nội dung; có lời giải ngay sau bảng
        d.table(List.of(
                List.of(merged(3, cellParas(cy("[TH]"), t(" Đề dẫn giả một.")))),
                List.of(cell(cellParas(t("Xét tính đúng sai các mệnh đề sau:"))), cell(cellParas(t("Đúng"))), cell(cellParas(t("Sai")))),
                stmt("a) ", "ý a đúng", true), stmt("b) ", "ý b sai", false), stmt("c) ", "ý c sai", false),
                stmt("d) ", "ý d đúng", true)));
        d.p(t("Hướng dẫn giải: "), t("Giải thích ý a."));

        // G2: không tô vàng, đáp án ở dòng chữ ngay sau bảng
        d.table(List.of(
                List.of(merged(3, cellParas(cy("[NB]"), t(" Đề dẫn giả hai.")))),
                List.of(cell(cellParas(t("Xét tính đúng sai"))), cell(cellParas(t("Đúng"))), cell(cellParas(t("Sai")))),
                stmt("a) ", "ý một", false), stmt("b) ", "ý hai", false), stmt("c) ", "ý ba", false),
                stmt("d) ", "ý bốn", false)));
        d.p("Đáp án: S Đ Đ S");

        // G3: câu dẫn là đoạn văn đứng TRƯỚC bảng; bảng không có nhãn a-d; tô vàng ở ý thứ hai
        d.p("Câu 1 [NB]. Đề dẫn giả ba.");
        d.table(List.of(
                List.of(cell(cellParas(t("Xét tính đúng sai"))), cell(cellParas(t("Đúng"))), cell(cellParas(t("Sai")))),
                stmt("", "ý một", false), stmt("", "ý hai", true), stmt("", "ý ba", false), stmt("", "ý bốn", false)));

        // G4: một bảng gộp hai câu
        d.table(List.of(
                List.of(merged(3, cellParas(cy("[VD]"), t(" Đề dẫn gộp một.")))),
                List.of(cell(cellParas(t("Xét tính đúng sai"))), cell(cellParas(t("Đúng"))), cell(cellParas(t("Sai")))),
                stmt("a) ", "một a", true), stmt("b) ", "một b", false), stmt("c) ", "một c", false), stmt("d) ", "một d", false),
                List.of(merged(3, cellParas(cy("[VDC]"), t(" Đề dẫn gộp hai.")))),
                List.of(cell(cellParas(t("Xét tính đúng sai"))), cell(cellParas(t("Đúng"))), cell(cellParas(t("Sai")))),
                stmt("a) ", "hai a", false), stmt("b) ", "hai b", false), stmt("c) ", "hai c", true), stmt("d) ", "hai d", true)));

        // G5: không có dấu hiệu đáp án nào
        d.table(List.of(
                List.of(merged(3, cellParas(cy("[NB]"), t(" Đề dẫn không đáp án.")))),
                List.of(cell(cellParas(t("Xét tính đúng sai"))), cell(cellParas(t("Đúng"))), cell(cellParas(t("Sai")))),
                stmt("a) ", "x", false), stmt("b) ", "y", false), stmt("c) ", "z", false), stmt("d) ", "w", false),
                // hàng một ô không có nhãn mức, nằm sau các ý: lời gợi ý của đề, không phải đề dẫn của câu mới
                List.of(merged(3, cellParas(t("Gợi ý: gợi ý giả."))))));

        d.p("PHẦN III. CÂU TRẮC NGHIỆM TRẢ LỜI NGẮN");

        // S1: đáp số tô vàng, số thập phân dùng dấu phẩy
        d.numbered(cy("[NB]"), t(" Tính giả một."));
        d.p(t("Trả lời: "), sym(), sym()).p(t("Đáp số: "), y("7,5"));

        // S2: đáp số là công thức
        d.numbered(cy("[TH]"), t(" Tính giả hai."));
        d.p(t("Trả lời: "), sym()).p(t("Đáp số: "), d.eq(frac34));

        // S3: ảnh đứng trước "Đáp số" cùng đoạn, đối tượng OLE vẽ, lời giải không có nhãn
        d.numbered(cy("[VD]"), t(" Tính giả ba có hình."));
        d.p(d.oleDrawing(oleWithoutEquation(), 5, 4));
        d.p(t("Trả lời: "), sym());
        d.p(d.drawing("png", PNG, 3, 3), t("Đáp số: 5"));
        d.p("Ta có A.").p("Vậy 5.");

        // S4: "Câu 4 [VDC]." gõ tay, đề có công thức chưa hỗ trợ, đáp số kèm %
        d.p(t("Câu 4 [VDC]. Tính bốn "), d.eq(sumFormula), t("."));
        d.p("Trả lời: ").p("Đáp số: 11,5%");

        d.p("Tài liệu được chia sẻ bởi Website Quangcao.edu.vn");
        d.p("https://www.quangcao.edu.vn");
        return d.write(dir, FILE_NAME);
    }

    private static List<DocxBuilder.Cell> stmt(String label, String text, boolean truth) {
        DocxBuilder.Run body = truth ? y(text) : t(text);
        return List.of(cell(cellParas(t(label), body)), cell(cellParas(sym())), cell(cellParas(sym())));
    }

    private FixtureDoc() {}
}
