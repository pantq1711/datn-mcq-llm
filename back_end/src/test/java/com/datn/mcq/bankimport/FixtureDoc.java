package com.datn.mcq.bankimport;

import java.util.List;

import static com.datn.mcq.bankimport.DocxBuilder.cell;
import static com.datn.mcq.bankimport.DocxBuilder.cellParas;
import static com.datn.mcq.bankimport.DocxBuilder.cy;
import static com.datn.mcq.bankimport.DocxBuilder.merged;
import static com.datn.mcq.bankimport.DocxBuilder.sym;
import static com.datn.mcq.bankimport.DocxBuilder.t;
import static com.datn.mcq.bankimport.DocxBuilder.tab;
import static com.datn.mcq.bankimport.DocxBuilder.y;
import static com.datn.mcq.mtef.MtefBuilder.SYM;
import static com.datn.mcq.mtef.MtefBuilder.T_SUM;
import static com.datn.mcq.mtef.MtefBuilder.ch;
import static com.datn.mcq.mtef.MtefBuilder.frac;
import static com.datn.mcq.mtef.MtefBuilder.line;
import static com.datn.mcq.mtef.MtefBuilder.mtef;
import static com.datn.mcq.mtef.MtefBuilder.mtefEmpty;
import static com.datn.mcq.mtef.MtefBuilder.nullLine;
import static com.datn.mcq.mtef.MtefBuilder.num;
import static com.datn.mcq.mtef.MtefBuilder.ole;
import static com.datn.mcq.mtef.MtefBuilder.oleWithoutEquation;
import static com.datn.mcq.mtef.MtefBuilder.tmpl;
import static com.datn.mcq.mtef.MtefBuilder.var;

/**
 * Tài liệu giả tái hiện các biến thể quan sát được trong bộ câu hỏi theo bài (nội dung hoàn toàn tự bịa, chuyển từ
 * tools/bank-import): số câu tự động và "Câu N.", nhãn mức tô cyan hay chữ thường, đáp án tô vàng ở nhiều phạm vi,
 * bảng bố cục, phần II ba biến thể, dòng "Đáp án: Đ S S Đ", bảng gộp, lời giải có numPr, chữ mũ gõ tay, ảnh, bảng tiêu
 * đề chứa tên giáo viên và dòng quảng cáo cuối file. Không phải test.
 */
public final class FixtureDoc {

    public static final String FILE_NAME = "KHỐI 8 - CHƯƠNG 9 - BÀI 9 - ĐỊNH LÝ GIẢ - GV NGUYỄN VĂN GIẢ pb.docx";
    public static final String DISPLAY_NAME = "KHỐI 8 - CHƯƠNG 9 - BÀI 9 - ĐỊNH LÝ GIẢ";
    public static final String TOPIC = "ĐỊNH LÝ GIẢ";

    /** Chuỗi không được xuất hiện trong bất kỳ output, dòng DB hay log nào. */
    public static final List<String> FORBIDDEN = List.of("NGUYỄN VĂN GIẢ", "Nguyễn Văn Giả", "TRƯỜNG GIẢ SỐ MỘT",
            "Quangcao", "quangcao", "Tài liệu được chia sẻ", "http");

    /** Đoạn văn chỉ có trong đề câu 1 phần I (để kiểm nội dung câu không lọt vào log). */
    public static final String Q1_MARKER = "Câu hỏi giả một có";

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final byte[] WMF = {(byte) 0xD7, (byte) 0xCD, (byte) 0xC6, (byte) 0x9A};

    private FixtureDoc() {}

    /** Bộ đầy đủ: 12 mcq, 6 tf, 4 sa (22 câu); 3 câu có ảnh, 1 câu có công thức chưa hỗ trợ. */
    public static byte[] full() {
        DocxBuilder d = new DocxBuilder();
        byte[] frac34 = ole(mtef(frac(num("3"), num("4"))));
        byte[] sumFormula = ole(mtef(tmpl(T_SUM, 0, line(var("i")), nullLine(), nullLine(), ch(SYM, 0x2211))));

        // bảng tiêu đề đầu file: trường và tên giáo viên (phải bị bỏ hoàn toàn)
        d.table(List.of(
                List.of(cell(cellParas(t("TRƯỜNG GIẢ SỐ MỘT"))), cell(cellParas(t("ĐỀ LUYỆN TẬP")))),
                List.of(cell(cellParas(t("GV NGUYỄN VĂN GIẢ"))), cell(cellParas(t("Lớp 8"))))));
        d.p("");
        d.p("PHẦN I. CÂU TRẮC NGHIỆM NHIỀU PHƯƠNG ÁN LỰA CHỌN");

        // Q1: số tự động + [NB] cyan, mỗi phương án một đoạn, tô vàng chỉ phần chữ, có công thức; không có lời giải
        d.numbered(cy("[NB]"), t(" " + Q1_MARKER + " "), d.eq(frac34), t(" trong đề."));
        d.p("A. phương án A").p("B. phương án B").p(t("C. "), y("phương án C")).p("D. phương án D");

        // Q2: hai phương án một đoạn, tab, tô vàng bắt đầu từ dấu chấm của nhãn
        d.numbered(cy("[TH]"), t(" Câu hai."));
        d.p(tab(), t("A. 1"), tab(), tab(), t("B"), y(". hai"), tab());
        d.p(tab(), t("C. 3"), tab(), tab(), t("D. 4"));

        // Q3: "Câu 3." gõ tay, phương án nằm trong bảng bố cục kèm ảnh ở ô bên (có ảnh → bỏ qua)
        d.p(t("Câu 3. "), t("[VD]"), t(" Câu ba với bảng bố cục."));
        d.table(List.of(List.of(
                cell(cellParas(t("A. một")), cellParas(t("B. hai")), cellParas(t("C. ba")), cellParas(t("D. "), y("bốn"))),
                cell(cellParas(d.drawing("png", PNG, 4, 3))))));

        // Q4: bốn phương án trong một đoạn, nhãn mức đầy đủ (VẬN DỤNG CAO → hard)
        d.numbered(cy("[VẬN DỤNG CAO]"), t(" Câu bốn."));
        d.p(t("A. a   B. b   C. c   D. "), y("d"));

        // Q5: có lời giải, trong lời giải có dòng numPr (không phải câu mới)
        d.numbered(cy("[NB]"), t(" Câu năm có lời giải."));
        d.p(t("A. "), y("đúng")).p("B. sai").p("C. sai").p("D. sai");
        d.p(t("Hướng dẫn giải: "), t("Bước một."));
        d.numbered(t("Bước hai có numPr."));

        // Q6: bảng dữ liệu trong đề + hình lớn + phương án B là ảnh công thức WMF nhỏ (có ảnh → bỏ qua)
        d.numbered(cy("[TH]"), t(" Câu sáu có bảng dữ liệu và hình."));
        d.table(List.of(List.of(cell(cellParas(t("x"))), cell(cellParas(t("1")))),
                List.of(cell(cellParas(t("y"))), cell(cellParas(t("2"))))));
        d.p(d.drawing("png", PNG, 5, 4));
        d.p(t("A. "), y("có")).p(t("B. "), d.pict("wmf", WMF, 0.6, 0.5)).p("C. ba").p("D. bốn");

        // Q7: chữ mũ gõ tay
        d.numbered(cy("[NB]"), t(" Toán học BC"), DocxBuilder.sup("2"), t(" bằng"));
        d.p("A. một").p(t("B. "), y("hai")).p("C. ba").p("D. bốn");

        // Q8: đối tượng công thức rỗng (bỏ qua có cờ)
        d.numbered(cy("[NB]"), t(" Câu tám."), d.eq(ole(mtefEmpty())));
        d.p("A. một").p("B. hai").p("C. ba").p(t("D. "), y("bốn"));

        // Q9: không có nhãn mức
        d.numbered(t("Câu 9. Câu chín không có nhãn mức."));
        d.p(t("A. "), y("một")).p("B. hai").p("C. ba").p("D. bốn");

        // Q10: không tô đáp án nào
        d.numbered(cy("[TH]"), t(" Câu mười không tô đáp án."));
        d.p("A. một").p("B. hai").p("C. ba").p("D. bốn");

        // Q11: tô hai đáp án
        d.numbered(cy("[TH]"), t(" Câu mười một tô hai đáp án."));
        d.p(t("A. "), y("một")).p("B. hai").p(t("C. "), y("ba")).p("D. bốn");

        // Q12: nội dung phương án chứa chuỗi giống nhãn ("Điểm C." trong phương án B)
        d.numbered(cy("[TH]"), t(" Câu mười hai."));
        d.p("A. Điểm D.");
        d.p(y("B. Điểm C."), tab(), tab());
        d.p("C. Điểm B.").p("D. Điểm A.");

        d.p("PHẦN II. CÂU TRẮC NGHIỆM ĐÚNG SAI");

        // G1: bảng 6 hàng, hàng đề gộp ô, ý đúng tô vàng; có lời giải ngay sau bảng
        d.table(List.of(
                List.of(merged(3, cellParas(cy("[TH]"), t(" Đề dẫn giả một.")))),
                List.of(cell(cellParas(t("Xét tính đúng sai các mệnh đề sau:"))), cell(cellParas(t("Đúng"))),
                        cell(cellParas(t("Sai")))),
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

        // G3: câu dẫn là đoạn văn đứng TRƯỚC bảng; tô vàng ở ý thứ hai
        d.p("Câu 1 [NB]. Đề dẫn giả ba.");
        d.table(List.of(
                List.of(cell(cellParas(t("Xét tính đúng sai"))), cell(cellParas(t("Đúng"))), cell(cellParas(t("Sai")))),
                stmt("", "ý một", false), stmt("", "ý hai", true), stmt("", "ý ba", false), stmt("", "ý bốn", false)));

        // G4: một bảng gộp hai câu
        d.table(List.of(
                List.of(merged(3, cellParas(cy("[VD]"), t(" Đề dẫn gộp một.")))),
                List.of(cell(cellParas(t("Xét tính đúng sai"))), cell(cellParas(t("Đúng"))), cell(cellParas(t("Sai")))),
                stmt("a) ", "một a", true), stmt("b) ", "một b", false), stmt("c) ", "một c", false),
                stmt("d) ", "một d", false),
                List.of(merged(3, cellParas(cy("[VDC]"), t(" Đề dẫn gộp hai.")))),
                List.of(cell(cellParas(t("Xét tính đúng sai"))), cell(cellParas(t("Đúng"))), cell(cellParas(t("Sai")))),
                stmt("a) ", "hai a", false), stmt("b) ", "hai b", false), stmt("c) ", "hai c", true),
                stmt("d) ", "hai d", true)));

        // G5: không có dấu hiệu đáp án nào; hàng gợi ý sau các ý
        d.table(List.of(
                List.of(merged(3, cellParas(cy("[NB]"), t(" Đề dẫn không đáp án.")))),
                List.of(cell(cellParas(t("Xét tính đúng sai"))), cell(cellParas(t("Đúng"))), cell(cellParas(t("Sai")))),
                stmt("a) ", "x", false), stmt("b) ", "y", false), stmt("c) ", "z", false), stmt("d) ", "w", false),
                List.of(merged(3, cellParas(t("Gợi ý: gợi ý giả."))))));

        d.p("PHẦN III. CÂU TRẮC NGHIỆM TRẢ LỜI NGẮN");

        // S1: đáp số tô vàng, số thập phân dùng dấu phẩy
        d.numbered(cy("[NB]"), t(" Tính giả một."));
        d.p(t("Trả lời: "), sym(), sym()).p(t("Đáp số: "), y("7,5"));

        // S2: đáp số là công thức phân số (không đạt STRICT)
        d.numbered(cy("[TH]"), t(" Tính giả hai."));
        d.p(t("Trả lời: "), sym()).p(t("Đáp số: "), d.eq(frac34));

        // S3: ảnh trước "Đáp số", đối tượng OLE vẽ (có ảnh → bỏ qua)
        d.numbered(cy("[VD]"), t(" Tính giả ba có hình."));
        d.p(d.oleDrawing(oleWithoutEquation(), 5, 4));
        d.p(t("Trả lời: "), sym());
        d.p(d.drawing("png", PNG, 3, 3), t("Đáp số: 5"));
        d.p("Ta có A.").p("Vậy 5.");

        // S4: công thức chưa hỗ trợ (∑) → bỏ qua
        d.p(t("Câu 4 [VDC]. Tính bốn "), d.eq(sumFormula), t("."));
        d.p("Trả lời: ").p("Đáp số: 11,5%");

        d.p("Tài liệu được chia sẻ bởi Website Quangcao.edu.vn");
        d.p("https://www.quangcao.edu.vn");
        return d.bytes();
    }

    /** {@code n} câu trắc nghiệm đơn giản, đủ cấu trúc, nội dung khác nhau rõ rệt (không trùng nhau). */
    public static byte[] simpleMcq(int n) {
        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN I. CÂU TRẮC NGHIỆM NHIỀU PHƯƠNG ÁN LỰA CHỌN");
        for (int i = 1; i <= n; i++) {
            d.numbered(cy("[NB]"), t(" " + word(i) + " cộng " + word(i + 7) + " bằng mấy, câu số " + i + "?"));
            d.p(t("A. "), y(word(2 * i + 7))).p("B. " + word(i + 1)).p("C. " + word(i + 2)).p("D. " + word(i + 3));
            d.p(t("Lời giải: "), t("Cộng hai số " + i + "."));
        }
        return d.bytes();
    }

    /** Một câu trắc nghiệm có bảng dữ liệu (không ảnh) và một câu sa bình thường. */
    public static byte[] withDataTable() {
        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN I. CÂU TRẮC NGHIỆM NHIỀU PHƯƠNG ÁN LỰA CHỌN");
        d.numbered(cy("[TH]"), t(" Câu có bảng số liệu."));
        d.table(List.of(List.of(cell(cellParas(t("x"))), cell(cellParas(t("1")))),
                List.of(cell(cellParas(t("y"))), cell(cellParas(t("2"))))));
        d.p(t("A. "), y("có")).p("B. hai").p("C. ba").p("D. bốn");
        d.p("PHẦN III. CÂU TRẮC NGHIỆM TRẢ LỜI NGẮN");
        d.numbered(cy("[VD]"), t(" Tính tổng hai và ba."));
        d.p("Trả lời: ").p(t("Đáp số: "), y("5"));
        return d.bytes();
    }

    /** Hai câu trắc nghiệm giống hệt nhau trong cùng một file. */
    public static byte[] duplicatedInFile() {
        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN I. CÂU TRẮC NGHIỆM NHIỀU PHƯƠNG ÁN LỰA CHỌN");
        for (int k = 0; k < 2; k++) {
            d.numbered(cy("[NB]"), t(" Hình vuông có mấy cạnh bằng nhau?"));
            d.p("A. 2").p("B. 3").p(t("C. "), y("4")).p("D. 5");
        }
        return d.bytes();
    }

    private static String word(int i) {
        String[] w = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(i).toCharArray()) {
            sb.append(sb.isEmpty() ? "" : " ").append(w[c - '0']);
        }
        return sb.toString();
    }

    private static List<DocxBuilder.Cell> stmt(String label, String text, boolean truth) {
        DocxBuilder.Run body = truth ? y(text) : t(text);
        return List.of(cell(cellParas(t(label), body)), cell(cellParas(sym())), cell(cellParas(sym())));
    }
}
