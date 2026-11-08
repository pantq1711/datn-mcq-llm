package com.datn.mcq.exam.export;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Markdown (pandoc) của đề để {@link PandocRunner} chuyển sang Word, theo khuôn đề kiểm tra THCS (4/11). Không cần pandoc.
 * Khối openxml thô (đầu đề, ngắt trang, HẾT, bảng đáp án) được cắt ra khỏi chuỗi trước khi so phần Markdown còn lại;
 * cấu trúc của các khối thô được kiểm trên document.xml thật ở {@code ExamDocxFormatTest}.
 */
class ExamMarkdownBuilderTest {

    private static final ExamScoringProperties SCORING = new ExamScoringProperties(new BigDecimal("0.25"),
            new BigDecimal("0.5"), List.of(BigDecimal.ZERO, new BigDecimal("0.1"), new BigDecimal("0.25"),
            new BigDecimal("0.5"), new BigDecimal("1.0")));
    private static final ExamHeader HEADER = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null);

    private static ExamItemDto mcq(int slotNo, String state, String text, String a, String b, String c, String d,
                                   Option correct, String explanation) {
        return new ExamItemDto(slotNo, 1, slotNo, "I", "Đa thức", QuestionType.MCQ, state, text == null ? null : "generated",
                null, text == null ? null : 100 + slotNo, text, a, b, c, d, correct, null, null, explanation,
                Difficulty.EASY, false, List.of());
    }

    private static ExamItemDto tf(String text, List<ItemStatementDto> statements, String explanation) {
        return new ExamItemDto(9, 2, 1, "II", "Hằng đẳng thức", QuestionType.TF, "kept", "bank", 7, null, text, null,
                null, null, null, null, null, statements, explanation, null, false, List.of());
    }

    private static ExamItemDto sa(String state, String text, String answer, String explanation) {
        return new ExamItemDto(10, 3, 1, "III", "Tứ giác", QuestionType.SA, state, text == null ? null : "generated",
                null, text == null ? null : 300, text, null, null, null, null, null, answer, null, explanation,
                Difficulty.MEDIUM, true, List.of("solver_inconsistent"));
    }

    private static ItemStatementDto st(StatementKey key, String text, TruthValue label) {
        return new ItemStatementDto(key, text, label, Difficulty.EASY);
    }

    private static ExamViewDto viewOf(PartDto... parts) {
        return new ExamViewDto(5, "MT2610231200001", "Đề giữa kì I", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 10, 23, 12, 0), "partial", 1, List.of(parts), "matrix");
    }

    private static ExamViewDto exam() {
        List<ExamItemDto> part1 = List.of(
                mcq(1, "ready", "Bậc của đơn thức $2x^3$ là:", "$1$", "$3$", "$0$", "$2$", Option.B,
                        "Số mũ của $x$ là $3$."),
                mcq(2, "failed", null, null, null, null, null, null, null),
                mcq(3, "generating", null, null, null, null, null, null, null));
        List<ExamItemDto> part2 = List.of(tf("Cho biểu thức $P = (x + 1)^2$.", List.of(
                st(StatementKey.A, "$P = x^2 + 2x + 1$.", TruthValue.TRUE),
                st(StatementKey.B, "Với $x = 0$ thì $P = 0$.", TruthValue.FALSE),
                st(StatementKey.C, "$P \\geq 0$ với mọi $x$.", TruthValue.TRUE),
                st(StatementKey.D, "Giá trị nhỏ nhất của $P$ là $1$.", TruthValue.FALSE)),
                "a) Khai triển.\nb) Thay $x = 0$ được $P = 1$."));
        List<ExamItemDto> part3 = List.of(sa("ready", "Tứ giác $ABCD$ có ba góc $80^\\circ$, $90^\\circ$, $100^\\circ$."
                + " Góc còn lại bằng bao nhiêu độ?", "90", "Tổng bốn góc là $360^\\circ$."));
        return viewOf(new PartDto("I", 3, 1, part1), new PartDto("II", 1, 1, part2), new PartDto("III", 1, 1, part3));
    }

    /** Bỏ mọi khối openxml thô (đầu đề, ngắt trang, HẾT, bảng đáp án...) và gộp dòng trống thừa. */
    private static String markdownOnly(String markdown) {
        return markdown.replaceAll("(?s)```\\{=openxml\\}\\n.*?\\n```\\n?", "").replaceAll("\\n{3,}", "\n\n").strip();
    }

    private static String t(int pos) {
        return ExamTabs.token(pos);
    }

    /** Ngày cố định để năm học và năm trong đầu đề không phụ thuộc đồng hồ máy. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    @Test
    void dungCauTrucDeLoiGiai() {
        String expected = """
                ::: {custom-style="ExamQuestion"}
                **Câu 1.** Bậc của đơn thức $2x^3$ là
                :::

                ::: {custom-style="ExamOption"}
                %1$s**A.** $1$.%2$s**B.** $3$.%3$s**C.** $0$.%4$s**D.** $2$.
                :::

                ::: {custom-style="ExamQuestion"}
                **Câu 2.** \\[Chưa có câu – cần sinh lại\\]
                :::

                ::: {custom-style="ExamQuestion"}
                **Câu 3.** \\[Chưa có câu – đang sinh\\]
                :::

                ::: {custom-style="ExamQuestion"}
                **Câu 1.** Cho biểu thức $P = (x + 1)^2$.
                :::

                ::: {custom-style="ExamOption"}
                %1$s**a)** $P = x^2 + 2x + 1$.

                %1$s**b)** Với $x = 0$ thì $P = 0$.

                %1$s**c)** $P \\geq 0$ với mọi $x$.

                %1$s**d)** Giá trị nhỏ nhất của $P$ là $1$.
                :::

                ::: {custom-style="ExamQuestion"}
                **Câu 1.** Tứ giác $ABCD$ có ba góc $80°$, $90°$, $100°$. Góc còn lại bằng bao nhiêu độ?
                :::

                ::: {custom-style="ExamNote"}
                Mỗi câu trả lời đúng thí sinh được 0,25 điểm.
                :::

                ::: {custom-style="ExamNote"}
                Điểm của mỗi câu: thí sinh chọn đúng 1 ý được 0,1 điểm; đúng 2 ý được 0,25 điểm; đúng 3 ý được 0,5 điểm; đúng cả 4 ý được 1 điểm.
                :::

                ::: {custom-style="ExamNote"}
                Mỗi câu trả lời đúng thí sinh được 0,5 điểm.
                :::

                **Câu 1. Chọn B.**

                Số mũ của $x$ là $3$.

                **Câu 2.** —

                **Câu 3.** —

                **Câu 1.**

                **a) Đúng.**

                **b) Sai.**

                **c) Đúng.**

                **d) Sai.**

                a\\) Khai triển.

                b\\) Thay $x = 0$ được $P = 1$.

                **Câu 1. Đáp án: 90.**

                Tổng bốn góc là $360°$.""".formatted(t(283), t(2764), t(5244), t(7725));
        assertEquals(expected, markdownOnly(ExamMarkdownBuilder.build(exam(), HEADER, SCORING, TODAY)));
    }

    /**
     * Tiêu đề phần theo mẫu của trường: "PHẦN I. TRẮC NGHIỆM NHIỀU LỰA CHỌN (x,x điểm)" đậm in hoa, tổng điểm = điểm một câu
     * × số câu (phần II: điểm tối đa của một câu = đúng cả 4 ý), rồi dòng hướng dẫn nghiêng.
     */
    @Test
    void tieuDePhanCoTongDiemVaDongHuongDanNghieng() {
        String md = ExamMarkdownBuilder.build(exam(), HEADER, SCORING, TODAY);
        for (String line : List.of(
                "PHẦN I. TRẮC NGHIỆM NHIỀU LỰA CHỌN (0,75 điểm)",
                "Học sinh chọn một đáp án đúng duy nhất từ câu 1 đến câu 3.",
                "PHẦN II. TRẮC NGHIỆM ĐÚNG/SAI (1,0 điểm)",
                "Học sinh trả lời Đúng (Đ) hoặc Sai (S) cho từng ý a), b), c), d) trong mỗi câu.",
                "PHẦN III. TRẮC NGHIỆM TRẢ LỜI NGẮN (0,5 điểm)",
                "Học sinh viết câu trả lời ngắn gọn (kết quả số) từ câu 1 đến câu 1.")) {
            assertTrue(md.contains(line), "thiếu: " + line + "\n" + md);
        }
        assertFalse(md.contains("Thí sinh trả lời từ câu"), "câu hướng dẫn cũ đã được thay");
    }

    /** Tổng điểm phần lấy từ ExamScoringProperties × số câu, một chữ số thập phân tối thiểu, dấu phẩy. */
    @Test
    void tongDiemPhanDinhDangMotChuSoThapPhan() {
        assertEquals("3,0", ExamMarkdownBuilder.totalPoints(new BigDecimal("0.25").multiply(BigDecimal.valueOf(12))));
        assertEquals("2,0", ExamMarkdownBuilder.totalPoints(new BigDecimal("0.50").multiply(BigDecimal.valueOf(4))));
        assertEquals("0,75", ExamMarkdownBuilder.totalPoints(new BigDecimal("0.25").multiply(BigDecimal.valueOf(3))));
        assertEquals("1,5", ExamMarkdownBuilder.totalPoints(new BigDecimal("1.50")));
        assertEquals("4,0", ExamMarkdownBuilder.totalPoints(new BigDecimal("4")));
    }

    /** Cuối đề có dòng "------ HẾT ------" căn giữa, trước trang đáp án; đáp án bắt đầu ở trang mới. */
    @Test
    void cuoiDeCoDongHetRoiToiTrangDapAnMoi() {
        String md = ExamMarkdownBuilder.build(exam(), HEADER, SCORING);
        int het = md.indexOf("------ HẾT ------");
        int answer = md.indexOf("ĐÁP ÁN VÀ HƯỚNG DẪN CHẤM – MÃ ĐỀ 101");
        int solutions = md.indexOf("LỜI GIẢI CHI TIẾT");
        assertTrue(het > 0 && answer > het && solutions > answer, "HẾT < ĐÁP ÁN < LỜI GIẢI: " + het + "," + answer + "," + solutions);
        assertTrue(md.contains("<w:pStyle w:val=\"ExamCenter\"/><w:sectPr>") && md.contains("<w:rPr><w:b/><w:bCs/><w:i/><w:iCs/></w:rPr><w:t xml:space=\"preserve\">------ HẾT ------</w:t>"),
                "HẾT căn giữa");
        // đáp án sang trang mới bằng ngắt section: đoạn HẾT mang sectPr của section đề
        String endParagraph = md.substring(md.lastIndexOf("<w:p>", het), md.indexOf("</w:p>", het));
        assertTrue(endParagraph.contains("<w:sectPr>") && endParagraph.contains("w:footerReference"),
                "đoạn HẾT kết thúc section đề: " + endParagraph);
        String answerParagraph = md.substring(md.lastIndexOf("<w:p>", answer), answer);
        assertFalse(answerParagraph.contains("<w:pageBreakBefore/>"), "ngắt section đã sang trang mới");
    }

    /** Phần không có câu thì bỏ dòng hướng dẫn, bảng đáp án và lời giải của phần đó. */
    @Test
    void phanKhongCoCauThiBo() {
        ExamViewDto onlyTwo = viewOf(new PartDto("I", 0, 0, List.of()),
                new PartDto("III", 1, 1, List.of(sa("ready", "Tính $1 + 1$.", "2", "Cộng hai số."))));
        String md = ExamMarkdownBuilder.build(onlyTwo, HEADER, SCORING);
        assertFalse(md.contains("PHẦN I."), md);
        assertFalse(md.contains("PHẦN II."), md);
        assertTrue(md.contains("PHẦN III. TRẮC NGHIỆM TRẢ LỜI NGẮN (0,5 điểm)"), md);
        assertFalse(md.contains("0,25 điểm"), "không có phần I thì không ghi điểm phần I");
    }

    /** Điểm lấy từ ExamScoringProperties, dấu phẩy thập phân, bỏ số 0 thừa. */
    @Test
    void diemLayTuCauHinhDinhDangDauPhay() {
        ExamScoringProperties custom = new ExamScoringProperties(new BigDecimal("0.2"), new BigDecimal("1.00"),
                List.of(BigDecimal.ZERO, new BigDecimal("0.15"), new BigDecimal("0.3"), new BigDecimal("0.6"),
                        new BigDecimal("1.5")));
        String md = markdownOnly(ExamMarkdownBuilder.build(exam(), HEADER, custom));
        assertTrue(md.contains("Mỗi câu trả lời đúng thí sinh được 0,2 điểm."), md);
        assertTrue(md.contains("Mỗi câu trả lời đúng thí sinh được 1 điểm."), md);
        assertTrue(md.contains("đúng 1 ý được 0,15 điểm; đúng 2 ý được 0,3 điểm; đúng 3 ý được 0,6 điểm; "
                + "đúng cả 4 ý được 1,5 điểm"), md);
    }

    // ---------- chuẩn hóa lúc xuất (chỉ trong file xuất, không sửa DB) ----------

    @Test
    void phuongAnVaYDungSaiKetThucBangDauChamNeuChuaCoDauKetCau() {
        ExamItemDto q = mcq(1, "ready", "Chọn khẳng định đúng", "Hai đường thẳng song song", "$x = 2$ hoặc $x = 3$",
                "Có đúng $3$ nghiệm?", "Vô nghiệm…", Option.A, null);
        ExamItemDto f = tf("Cho hàm số $y = x^2$.", List.of(
                st(StatementKey.A, "Đồ thị là một parabol", TruthValue.TRUE),
                st(StatementKey.B, "Hàm số đồng biến trên $\\mathbb{R}$", TruthValue.FALSE),
                st(StatementKey.C, "Hàm số có giá trị nhỏ nhất bằng $0$!", TruthValue.TRUE),
                st(StatementKey.D, "Đồ thị đi qua $O$.", TruthValue.TRUE)), null);
        String md = markdownOnly(ExamMarkdownBuilder.build(
                viewOf(new PartDto("I", 1, 1, List.of(q)), new PartDto("II", 1, 1, List.of(f))), HEADER, SCORING));
        assertTrue(md.contains("**A.** Hai đường thẳng song song."), "thêm . sau chữ: " + md);
        assertTrue(md.contains("**B.** $x = 2$ hoặc $x = 3$."), "thêm . sau công thức: " + md);
        assertTrue(md.contains("**C.** Có đúng $3$ nghiệm?") && !md.contains("nghiệm?."), "giữ ?: " + md);
        assertTrue(md.contains("**D.** Vô nghiệm…") && !md.contains("nghiệm….") , "giữ …: " + md);
        assertTrue(md.contains("**a)** Đồ thị là một parabol."), md);
        assertTrue(md.contains("**b)** Hàm số đồng biến trên $\\mathbb{R}$."), "dấu chấm đặt sau công thức: " + md);
        assertTrue(md.contains("**c)** Hàm số có giá trị nhỏ nhất bằng $0$!") && !md.contains("$0$!."), "giữ !: " + md);
        assertTrue(md.contains("**d)** Đồ thị đi qua $O$.") && !md.contains("$O$.."), "đã có . thì không thêm: " + md);
    }

    @Test
    void cauDanKetThucBangLaHaiChamBoDauHaiCham() {
        for (String stem : new String[]{"Bậc của đơn thức $x^2$ là:", "Bậc của đơn thức $x^2$ là :", "Bậc của đơn thức $x^2$ là : "}) {
            String md = markdownOnly(ExamMarkdownBuilder.build(viewOf(new PartDto("I", 1, 1, List.of(
                    mcq(1, "ready", stem, "$1$", "$2$", "$3$", "$4$", Option.B, null)))), HEADER, SCORING));
            assertTrue(md.contains("**Câu 1.** Bậc của đơn thức $x^2$ là\n"), "\"là:\" thành \"là\": " + stem + "\n" + md);
            assertFalse(md.contains("là:") || md.contains("là :"), md);
        }
        // chỉ đổi khi kết thúc bằng "là:"; dấu hai chấm ở nơi khác giữ nguyên
        String keep = markdownOnly(ExamMarkdownBuilder.build(viewOf(new PartDto("I", 1, 1, List.of(
                mcq(1, "ready", "Cho: $x = 1$. Khẳng định nào sau đây đúng:", "$1$", "$2$", "$3$", "$4$", Option.B, null)))),
                HEADER, SCORING));
        assertTrue(keep.contains("Cho: $x = 1$. Khẳng định nào sau đây đúng:\n"), keep);
    }

    // ---------- lời giải chi tiết ----------

    /** Ý đúng/sai: lời giải tách được theo từng ý a–d thì đặt dưới nhãn của ý đó. */
    @Test
    void loiGiaiTfTachDuocThiDatDuoiTungY() {
        ExamItemDto f = tf("Cho $P = (x + 1)^2$.", List.of(
                st(StatementKey.A, "Một", TruthValue.TRUE), st(StatementKey.B, "Hai", TruthValue.FALSE),
                st(StatementKey.C, "Ba", TruthValue.FALSE), st(StatementKey.D, "Bốn", TruthValue.TRUE)),
                "a) Khai triển được $x^2 + 2x + 1$.\nb) Với $x = 0$ thì $P = 1$.\nc) Sai vì $P \\geq 0$.\nd) Đúng.\nDo đó chọn.");
        String md = markdownOnly(ExamMarkdownBuilder.build(viewOf(new PartDto("II", 1, 1, List.of(f))), HEADER, SCORING));
        String solutions = md.substring(md.indexOf("**Câu 1.**\n"));
        assertEquals("""
                **Câu 1.**

                **a) Đúng.**

                Khai triển được $x^2 + 2x + 1$.

                **b) Sai.**

                Với $x = 0$ thì $P = 1$.

                **c) Sai.**

                Sai vì $P \\geq 0$.

                **d) Đúng.**

                Đúng.

                Do đó chọn.""", solutions);
    }

    /** Câu không có lời giải: chỉ còn dòng nhãn (kèm đáp án lấy từ dữ liệu đáp án), không bỏ câu. */
    @Test
    void cauKhongLoiGiaiChiCoDongNhan() {
        List<ExamItemDto> part1 = List.of(
                mcq(1, "kept", "Câu nhập không lời giải", "$1$", "$2$", "$3$", "$4$", Option.A, null),
                mcq(2, "kept", "Câu có lời giải", "$1$", "$2$", "$3$", "$4$", Option.B, "Lời giải câu hai."),
                mcq(3, "kept", "Câu lời giải trống", "$1$", "$2$", "$3$", "$4$", Option.C, "   "),
                mcq(4, "failed", null, null, null, null, null, null, null));
        String md = markdownOnly(ExamMarkdownBuilder.build(viewOf(new PartDto("I", 4, 1, part1)), HEADER, SCORING));
        assertEquals("""
                **Câu 1. Chọn A.**

                **Câu 2. Chọn B.**

                Lời giải câu hai.

                **Câu 3. Chọn C.**

                **Câu 4.** —""", md.substring(md.indexOf("**Câu 1. Chọn A.**")));
    }

    // ---------- đầu đề ----------

    /** Khối openxml đầu tiên của markdown: hai bảng (đầu đề và dòng họ tên, mã đề). */
    private static String headerBlock(String markdown) {
        assertTrue(markdown.startsWith("```{=openxml}\n<w:tbl>"), markdown.substring(0, 80));
        return markdown.substring(0, markdown.indexOf("\n```\n\n"));
    }

    @Test
    void dauDeCoDayDuCacDongTheoMauCuaTruong() {
        String block = headerBlock(ExamMarkdownBuilder.build(exam(), ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, 45),
                SCORING, TODAY));
        for (String part : List.of(
                "UBND PHƯỜNG ……………", "TRƯỜNG THCS ……………", "Thứ..... ngày........ tháng..... năm 2026",
                "ĐỀ KIỂM TRA GIỮA KÌ I", "NĂM HỌC 2026 - 2027", "Môn: Toán, Lớp: 8.",
                "(Thời gian làm bài 45 phút không kể thời gian giao đề)",
                "Mã đề", "Điểm bài thi", "Mã phách", ">101<")) {
            assertTrue(block.contains(part), "đầu đề thiếu: " + part + "\n" + block);
        }
        assertFalse(block.contains("Họ và tên"), "bỏ dòng Họ và tên/Lớp");
        assertFalse(block.contains("Đề có") || block.contains("SECTIONPAGES"), "bỏ \"(Đề có N trang)\"");
        assertFalse(block.contains("PHÒNG GD"), block);
    }

    @Test
    void maHeThongMTKhongInRaFileVaKhongConDongTieuDeCu() {
        String markdown = ExamMarkdownBuilder.build(exam(), HEADER, SCORING);
        assertFalse(markdown.contains("MT2610231200001"), "mã hệ thống không in ra");
        assertFalse(markdown.contains("MT2"));
        assertFalse(markdown.contains("**Mã đề:**"));
        assertFalse(markdown.contains("# Đề giữa kì I"), "tiêu đề do đầu đề thay");
    }

    @Test
    void thoiGianKhongCoThiDeTrong() {
        String without = headerBlock(ExamMarkdownBuilder.build(exam(), HEADER, SCORING, TODAY));
        assertTrue(without.contains("(Thời gian làm bài …… phút không kể thời gian giao đề)"), without);
    }

    /** Năm học và năm trong dòng ngày lấy từ ngày xuất: tháng 7 vẫn là năm học trước. */
    @Test
    void namHocVaNamTheoNgayXuat() {
        String july = headerBlock(ExamMarkdownBuilder.build(exam(), HEADER, SCORING, LocalDate.of(2027, 7, 3)));
        assertTrue(july.contains("NĂM HỌC 2026 - 2027") && july.contains("năm 2027"), july);
        String sept = headerBlock(ExamMarkdownBuilder.build(exam(), HEADER, SCORING, LocalDate.of(2027, 9, 3)));
        assertTrue(sept.contains("NĂM HỌC 2027 - 2028") && sept.contains("năm 2027"), sept);
    }

    @Test
    void dauDeThieuThongTinVanDungDinhDang() {
        String block = headerBlock(ExamMarkdownBuilder.build(exam(), ExamHeader.blank(""), SCORING, TODAY));
        assertTrue(block.contains("ĐỀ KIỂM TRA<"), block);
        assertTrue(block.contains("Môn: ……, Lớp: …….") , block);
        assertTrue(block.contains(">101<"), block);
    }

    @Test
    void kyTuXmlTrongTenKiDuocThoat() {
        String block = headerBlock(ExamMarkdownBuilder.build(exam(), ExamHeader.of("Kì <1> & \"2\"", "Toán", 8, null),
                SCORING, TODAY));
        assertTrue(block.contains("KÌ &lt;1&gt; &amp; &quot;2&quot;"), block);
        assertFalse(block.contains("<1>"));
    }

    /** Chữ ký cũ (không truyền đầu đề, điểm) vẫn dùng được: tiêu đề lấy từ tên đề, mã đề hiển thị 101. */
    @Test
    void buildKhongTruyenDauDeDungTieuDeCuaDe() {
        String markdown = ExamMarkdownBuilder.build(exam());
        String block = headerBlock(markdown);
        assertTrue(block.contains("ĐỀ KIỂM TRA ĐỀ GIỮA KÌ I"), block);
        assertTrue(block.contains(">101<"));
        assertFalse(markdown.contains("MT2"));
    }

    /** F-05: trong $...$ mọi kiểu xuống dòng thành khoảng trắng, để không dòng nào của công thức bắt đầu bằng ``` hay <. */
    @Test
    void xuongDongTrongCongThucThanhKhoangTrang() {
        String out = ExamMarkdownBuilder.inline("Cho $a\n```{=openxml}\n<w:p/>\r\n```\n\rb$ hết");
        assertEquals("Cho $a ```{=openxml} <w:p/> ``` b$ hết", out);
        assertFalse(out.matches("(?s).*\\R.*"), "không còn ký tự xuống dòng nào");
        assertEquals("$x^2$", ExamMarkdownBuilder.inline("$x^2$"));
    }

    /** F-05: dòng tiêu đề đề không được chứa xuống dòng (khối openxml của đầu đề sẽ bị đóng sớm bởi dòng ```). */
    @Test
    void tenDeNhieuDongKhongPhaBoKhoiDauDe() {
        String block = ExamMarkdownBuilder.headerBlock(ExamHeader.blank("Đề A\n```\n\n# X"), TODAY);
        assertEquals(2, block.split("(?m)^```", -1).length - 1, "chỉ dòng mở và dòng đóng khối openxml: " + block);
    }

    @Test
    void thoatKyTuMarkdownNgoaiCongThucGiuNguyenTrongCongThuc() {
        assertEquals("a\\*b\\_c \\#1 \\[x\\] $a_1 * b_2$ 5\\$ \\<br\\> \\@ref", ExamMarkdownBuilder.inline(
                "a*b_c #1 [x] $a_1 * b_2$ 5\\$ <br> @ref"));
        assertEquals("$x + 1$ và $y$", ExamMarkdownBuilder.inline("$ x + 1 $ và $y$"), "bỏ khoảng trắng sát dấu $");
        assertEquals("Giá \\$5 thôi", ExamMarkdownBuilder.inline("Giá $5 thôi"), "$ lẻ là chữ thường");
        assertEquals("\\\\frac12", ExamMarkdownBuilder.inline("\\frac12"), "LaTeX ngoài $ giữ nguyên chữ");
        assertEquals("1\\. Tính", ExamMarkdownBuilder.inline("1. Tính"), "không thành danh sách đánh số");
    }

    /** Nội dung đề không được giả mã giữ chỗ tab (ký tự vùng riêng bị lọc). */
    @Test
    void noiDungDeKhongGiaDuocMaGiuChoTab() {
        assertEquals("ab", ExamMarkdownBuilder.inline("a" + ExamTabs.token(1234) .replaceAll("\\d", "") + "b"));
        assertEquals("xy", ExamMarkdownBuilder.inline("x" + ExamTabs.token(283) + "y").replaceAll("\\d", ""));
        assertFalse(ExamMarkdownBuilder.inline("x" + ExamTabs.token(283) + "y").contains(ExamTabs.token(283)));
    }
}
