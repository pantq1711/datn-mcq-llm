package com.datn.mcq.exam.export;

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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Markdown của đề để pandoc chuyển sang Word (ngày 23/10, đợt 3, việc 3). Không cần pandoc. */
class ExamMarkdownBuilderTest {

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
        return new ExamViewDto(5, "MT2610231200001", "Đề giữa kì I", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 10, 23, 12, 0), "partial", 1, List.of(
                        new PartDto("I", 3, 1, part1), new PartDto("II", 1, 1, part2), new PartDto("III", 1, 1, part3)));
    }

    @Test
    void dungCauTrucDeDapAnLoiGiai() {
        String expected = """
                ## PHẦN I. Câu trắc nghiệm nhiều phương án lựa chọn

                **Câu 1.** Bậc của đơn thức $2x^3$ là:

                A. $1$

                B. $3$

                C. $0$

                D. $2$

                **Câu 2.** \\[Chưa có câu – cần sinh lại\\]

                **Câu 3.** \\[Chưa có câu – đang sinh\\]

                ## PHẦN II. Câu trắc nghiệm đúng sai

                **Câu 1.** Cho biểu thức $P = (x + 1)^2$.

                a\\) $P = x^2 + 2x + 1$.

                b\\) Với $x = 0$ thì $P = 0$.

                c\\) $P \\geq 0$ với mọi $x$.

                d\\) Giá trị nhỏ nhất của $P$ là $1$.

                ## PHẦN III. Câu trắc nghiệm trả lời ngắn

                **Câu 1.** Tứ giác $ABCD$ có ba góc $80^\\circ$, $90^\\circ$, $100^\\circ$. Góc còn lại bằng bao nhiêu độ?

                ```{=openxml}
                <w:p><w:r><w:br w:type="page"/></w:r></w:p>
                ```

                # ĐÁP ÁN

                ## PHẦN I

                Câu 1: B

                Câu 2: —

                Câu 3: —

                ## PHẦN II

                Câu 1: ĐSĐS

                ## PHẦN III

                Câu 1: 90

                # LỜI GIẢI

                ## PHẦN I

                **Câu 1.** Số mũ của $x$ là $3$.

                **Câu 2.** —

                **Câu 3.** —

                ## PHẦN II

                **Câu 1.** a\\) Khai triển.

                b\\) Thay $x = 0$ được $P = 1$.

                ## PHẦN III

                **Câu 1.** Tổng bốn góc là $360^\\circ$.
                """;
        String markdown = ExamMarkdownBuilder.build(exam(), HEADER);
        int body = markdown.indexOf(HEADER_END);
        assertTrue(body > 0, markdown);
        assertEquals(expected, markdown.substring(body + HEADER_END.length()));
    }

    // ---------- đầu đề (2/11) ----------

    private static final ExamHeader HEADER = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null);
    private static final String HEADER_END = "\n```\n\n";

    /** Khối openxml đầu tiên của markdown: bảng 2 cột không viền và dòng họ tên, lớp. */
    private static String headerBlock(String markdown) {
        assertTrue(markdown.startsWith("```{=openxml}\n<w:tbl>"), markdown.substring(0, 80));
        return markdown.substring(0, markdown.indexOf(HEADER_END));
    }

    @Test
    void dauDeLaBangHaiCotKhongVienVaDongHoTen() {
        String block = headerBlock(ExamMarkdownBuilder.build(exam(), HEADER));
        for (String part : List.of(
                "PHÒNG GD&amp;ĐT ……………", "TRƯỜNG THCS ……………",
                "ĐỀ KIỂM TRA GIỮA KÌ I", "Môn: Toán 8 — Thời gian: …… phút", "Mã đề: 101",
                "Họ và tên: ………………………", "Lớp: ………")) {
            assertTrue(block.contains(part), "đầu đề thiếu: " + part + "\n" + block);
        }
        assertEquals(1, block.split("<w:tbl>", -1).length - 1, "đúng một bảng");
        assertEquals(2, block.split("<w:tc>", -1).length - 1, "hai cột");
        assertEquals(1, block.split("<w:tr>", -1).length - 1, "một dòng");
        for (String side : List.of("top", "left", "bottom", "right", "insideH", "insideV")) {
            assertTrue(block.contains("<w:" + side + " w:val=\"nil\"/>"), "bảng không viền: thiếu " + side);
        }
        assertTrue(block.indexOf("Mã đề: 101") > block.indexOf("ĐỀ KIỂM TRA"), "ba dòng cột phải theo thứ tự");
        assertTrue(block.indexOf("Họ và tên") > block.indexOf("</w:tbl>"), "dòng họ tên nằm dưới bảng");
    }

    @Test
    void maHeThongMTKhongInRaFileVaKhongConDongTieuDeCu() {
        String markdown = ExamMarkdownBuilder.build(exam(), HEADER);
        assertFalse(markdown.contains("MT2610231200001"), "mã hệ thống không in ra");
        assertFalse(markdown.contains("MT2"));
        assertFalse(markdown.contains("**Mã đề:**"));
        assertFalse(markdown.contains("# Đề giữa kì I"), "tiêu đề do đầu đề thay");
    }

    @Test
    void thoiGianLayTuMaTranNeuCoKhongCoThiDeTrong() {
        String withTime = headerBlock(ExamMarkdownBuilder.build(exam(), ExamHeader.of("Giữa kì I", "Toán", 8, 90)));
        assertTrue(withTime.contains("Môn: Toán 8 — Thời gian: 90 phút"), withTime);
        assertFalse(withTime.contains("……  phút"));
        String without = headerBlock(ExamMarkdownBuilder.build(exam(), HEADER));
        assertTrue(without.contains("Thời gian: …… phút"), without);
    }

    @Test
    void dauDeThieuThongTinVanDungDinhDang() {
        String block = headerBlock(ExamMarkdownBuilder.build(exam(), ExamHeader.blank("")));
        assertTrue(block.contains("ĐỀ KIỂM TRA<"), block);
        assertTrue(block.contains("Môn: …… — Thời gian: …… phút"), block);
        assertTrue(block.contains("Mã đề: 101"), block);
    }

    @Test
    void kyTuXmlTrongTenKiDuocThoat() {
        String block = headerBlock(ExamMarkdownBuilder.build(exam(), ExamHeader.of("Kì <1> & \"2\"", "Toán", 8, null)));
        assertTrue(block.contains("KÌ &lt;1&gt; &amp; &quot;2&quot;"), block);
        assertFalse(block.contains("<1>"));
    }

    /** Chữ ký cũ (không truyền đầu đề) vẫn dùng được: tiêu đề lấy từ tên đề, mã đề hiển thị 101. */
    @Test
    void buildKhongTruyenDauDeDungTieuDeCuaDe() {
        String markdown = ExamMarkdownBuilder.build(exam());
        String block = headerBlock(markdown);
        assertTrue(block.contains("ĐỀ KIỂM TRA ĐỀ GIỮA KÌ I"), block);
        assertTrue(block.contains("Mã đề: 101"));
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
        String block = ExamMarkdownBuilder.headerBlock(ExamHeader.blank("Đề A\n```\n\n# X"));
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
}
