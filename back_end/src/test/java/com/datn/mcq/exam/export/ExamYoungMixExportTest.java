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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Bản xuất cho phần mềm trộn đề YoungMix (26/11, {@code content=youngmix}), theo quy tắc của huongdan.youngmix.vn: chỉ nội
 * dung đề; dòng thẻ {@code <g3#1>}, {@code <g3#2>}, {@code <g1#3>} ngay dưới tiêu đề và dòng hướng dẫn của mỗi phần; nhãn
 * phương án đúng ("B.") và nhãn ý Đúng ("a)") gạch chân, chỉ nhãn; phần III có dòng "A. đáp án" dưới câu hỏi; phương án và
 * ý không nằm trong bảng; hình inline. Dữ liệu dựng tay, không cần DB, không gọi Gemini; phần pandoc bỏ qua khi thiếu pandoc.
 */
class ExamYoungMixExportTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    private static ExamItemDto mcq(int no, String stem, Option correct, String explanation, String... options) {
        return new ExamItemDto(no, no, no, "I", "Đa thức", QuestionType.MCQ, "ready", "generated", null, 100 + no, stem,
                options[0], options[1], options[2], options[3], correct, null, null, explanation, Difficulty.EASY, false,
                List.of());
    }

    private static ExamItemDto sa(int no, String stem, String answer) {
        return new ExamItemDto(30 + no, 30 + no, no, "III", "Số", QuestionType.SA, "ready", "generated", null, 300 + no,
                stem, null, null, null, null, null, answer, null, "Tính trực tiếp.", Difficulty.MEDIUM, false, List.of());
    }

    static ExamViewDto exam() {
        ExamItemDto q1 = mcq(1, "Bậc của đơn thức $2x^3$ là", Option.B, "Số mũ của $x$ là $3$.", "$1$", "$3$", "$0$", "$2$");
        ExamItemDto q2 = mcq(2, "Cho tam giác $ABC$ như hình vẽ.\n⟦img:5⟧\nSố đo góc $A$ là", Option.D,
                "Xem lại hình.\n⟦img:5⟧\nTổng ba góc là $180°$.", "$30°$", "$45°$", "$60°$", "$90°$");
        List<ItemStatementDto> statements = new ArrayList<>();
        String[] texts = {"Đồ thị là một parabol", "Hàm số đồng biến trên $\\mathbb{R}$", "Giá trị nhỏ nhất bằng $0$",
                "Đồ thị đi qua $O$"};
        TruthValue[] labels = {TruthValue.TRUE, TruthValue.FALSE, TruthValue.TRUE, TruthValue.TRUE};
        for (int i = 0; i < 4; i++) {
            statements.add(new ItemStatementDto(StatementKey.values()[i], texts[i], labels[i], Difficulty.EASY));
        }
        ExamItemDto tf = new ExamItemDto(21, 21, 1, "II", "Hàm số", QuestionType.TF, "ready", "generated", null, 201,
                "Cho hàm số $y = x^2$.", null, null, null, null, null, null, statements, "Xét từng ý.", null, false,
                List.of());
        return new ExamViewDto(26, "MT2611261200026", "Giữa kì I – Toán 8", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 11, 26, 8, 0), "ready", 0,
                List.of(new PartDto("I", 2, 2, List.of(q1, q2)), new PartDto("II", 1, 1, List.of(tf)),
                        new PartDto("III", 3, 3, List.of(sa(1, "Tính $5 \\cdot 2$.", "10"),
                                sa(2, "Tính $5 : 2$.", "2.5"), sa(3, "Tính $1 : 2$ dưới dạng phân số.", "$\\frac{1}{2}$")))),
                "matrix");
    }

    static Map<Long, ExamFigures.Figure> figures() {
        return Map.of(5L, new ExamFigures.Figure(5, "image/png", ExamFigureDocxTest.png(120, 80, Color.BLUE), 60));
    }

    private static ExamHeader header() {
        return ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, 45);
    }

    static ExamDocxOptions options(int fontSize) {
        return new ExamDocxOptions(ExamHeader.ORIGINAL_CODE, ExportContent.YOUNGMIX, false, fontSize);
    }

    private static String markdown() {
        return ExamMarkdownBuilder.build(exam(), header(), ExamMarkdownBuilder.DEFAULT_SCORING, TODAY,
                options(ExamDocxOptions.DEFAULT_FONT_SIZE_PT), figures());
    }

    // ---------------------------------------------------------------- tham số

    @Test
    void thamSoContentYoungMix() {
        assertSame(ExportContent.YOUNGMIX, ExportContent.parse("youngmix"));
        assertSame(ExportContent.YOUNGMIX, ExportContent.parse(" YoungMix "));
        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ExportContent.parse("young-mix"));
        assertTrue(ex.getMessage().contains("youngmix"), ex.getMessage());
        // không áp tờ phách cho bản YoungMix
        assertFalse(new ExamDocxOptions("205", ExportContent.YOUNGMIX, true, 12).phach());
    }

    /** Ảnh trong lời giải không nạp: bản YoungMix không có lời giải. */
    @Test
    void anhLoiGiaiKhongTinhChoYoungMix() {
        assertEquals(2, ExamFigures.referenced(exam(), ExportContent.ALL).get(5L));
        assertEquals(1, ExamFigures.referenced(exam(), ExportContent.EXAM).get(5L));
        assertEquals(1, ExamFigures.referenced(exam(), ExportContent.YOUNGMIX).get(5L));
    }

    // ---------------------------------------------------------------- Markdown

    @Test
    void chiNoiDungDeKhongDauDeHetDapAnLoiGiai() {
        String md = markdown();
        for (String absent : List.of("UBND", "TRƯỜNG THCS", "NĂM HỌC", "Mã đề", "Mã phách", "Họ và tên",
                ExamMarkdownBuilder.END_OF_EXAM, "ĐÁP ÁN", ExamMarkdownBuilder.SOLUTION_TITLE, "Tổng điểm phần trắc nghiệm",
                "Mỗi câu trả lời đúng", "Số mũ của", "Xét từng ý", "Tính trực tiếp", "<w:sectPr", "<w:tbl")) {
            assertFalse(md.contains(absent), "bản YoungMix không có: " + absent);
        }
        assertTrue(md.contains("PHẦN I. TRẮC NGHIỆM NHIỀU LỰA CHỌN") && md.contains("Học sinh chọn một đáp án đúng"),
                "giữ tiêu đề phần và dòng hướng dẫn");
        assertTrue(md.startsWith("```{=openxml}\n<w:p><w:pPr><w:pStyle w:val=\"ExamPartHeading\"/>"),
                "file mở đầu bằng tiêu đề phần I: " + md.substring(0, 120));
    }

    @Test
    void nhanDungGachChanTrongMarkdown() {
        String md = markdown();
        assertTrue(md.contains("**[B.]{.underline}** $3$."), "câu 1: nhãn B gạch chân, nội dung không");
        assertTrue(md.contains("**[D.]{.underline}** $90°$."), "câu 2: nhãn D gạch chân");
        assertEquals(2, count(md, "]{.underline}** $"), "chỉ 2 phương án gạch chân ở phần I");
        for (String plain : List.of("**A.** $1$.", "**C.** $0$.", "**D.** $2$.", "**A.** $30°$.")) {
            assertTrue(md.contains(plain), "phương án sai giữ như cũ: " + plain);
        }
        for (String key : List.of("a", "c", "d")) {
            assertTrue(md.contains("**[" + key + ")]{.underline}**"), "ý Đúng " + key + ") gạch chân");
        }
        assertTrue(md.contains("**b)**") && !md.contains("[b)]"), "ý Sai b) để nguyên");
    }

    private static int count(String s, String part) {
        return s.split(Pattern.quote(part), -1).length - 1;
    }

    // ---------------------------------------------------------------- docx thật

    private static Optional<String> pandoc() {
        Optional<String> found = PandocTestSupport.find();
        assumeTrue(found.isPresent(), "máy không có pandoc: bỏ qua");
        return found;
    }

    static byte[] docx(EquationFormat format, int fontSize) {
        ExamDocxOptions o = options(fontSize);
        String md = ExamMarkdownBuilder.build(exam(), header(), ExamMarkdownBuilder.DEFAULT_SCORING, TODAY, o, figures());
        return new PandocRunner(pandoc().get()).markdownToDocx(md, format, o).content();
    }

    /** Một đoạn w:p của thân tài liệu: chữ (w:t, tab là "\t") và XML gốc. */
    record Para(String text, String xml) {}

    static List<Para> paragraphs(String documentXml) {
        List<Para> out = new ArrayList<>();
        Matcher m = Pattern.compile("<w:p[ >].*?</w:p>", Pattern.DOTALL).matcher(documentXml);
        while (m.find()) {
            String xml = m.group();
            StringBuilder text = new StringBuilder();
            Matcher t = Pattern.compile("<w:tab/>|<w:t(?: [^>]*)?>([^<]*)</w:t>").matcher(xml);
            while (t.find()) {
                text.append(t.group(1) == null ? "\t" : t.group(1));
            }
            out.add(new Para(unescape(text.toString()), xml));
        }
        return out;
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }

    /** Các run có chữ của đoạn: chữ và có gạch chân đơn hay không. */
    static List<Map.Entry<String, Boolean>> runs(String paragraphXml) {
        List<Map.Entry<String, Boolean>> out = new ArrayList<>();
        Matcher r = Pattern.compile("<w:r>(.*?)</w:r>|<w:r [^>]*>(.*?)</w:r>", Pattern.DOTALL).matcher(paragraphXml);
        while (r.find()) {
            String run = r.group(1) != null ? r.group(1) : r.group(2);
            Matcher t = Pattern.compile("<w:t(?: [^>]*)?>([^<]*)</w:t>").matcher(run);
            StringBuilder text = new StringBuilder();
            while (t.find()) {
                text.append(t.group(1));
            }
            if (!text.isEmpty()) {
                out.add(Map.entry(text.toString(), run.contains("<w:u w:val=\"single\"")));
            }
        }
        return out;
    }

    private static int indexStarting(List<Para> ps, String prefix, int from) {
        for (int i = from; i < ps.size(); i++) {
            if (ps.get(i).text().strip().startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void docxKhongDauDeChanTrangHetDapAnVaKhongCoBang(EquationFormat format) throws Exception {
        byte[] file = docx(format, 12);
        String xml = PandocTestSupport.documentXml(file);
        assertFalse(xml.contains("<w:tbl"), "không có bảng (đầu đề, bảng đáp án, bảng quanh phương án)");
        assertFalse(xml.contains("footerReference"), "không có chân trang");
        assertEquals(1, count(xml, "<w:sectPr"), "một section");
        for (String absent : List.of("UBND", "Mã đề", "HẾT", "ĐÁP ÁN", "LỜI GIẢI", "Số mũ của", "Họ và tên")) {
            assertFalse(xml.contains(absent), "không có: " + absent);
        }
        assertFalse(xml.contains("<w:numPr"), "không dùng numbering tự động của Word");
        assertFalse(xml.contains("<wp:anchor"), "hình In Line with Text");
        assertTrue(xml.contains("<wp:inline"), "câu có hình vẫn có hình");
        if (format == EquationFormat.MATHTYPE) {
            assertTrue(xml.contains("Equation.DSMT4"), "công thức là MathType");
        } else {
            assertTrue(xml.contains("<m:oMath"), "công thức là Equation");
        }
    }

    /** Đúng 3 dòng thẻ, mỗi dòng ngay dưới dòng hướng dẫn của phần và ngay trên "Câu 1." của phần. */
    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void baDongTheNhomDungViTri(EquationFormat format) throws Exception {
        List<Para> ps = paragraphs(PandocTestSupport.documentXml(docx(format, 12)));
        List<String> tags = ps.stream().map(p -> p.text().strip()).filter(t -> t.matches("<g\\d#\\d>")).toList();
        assertEquals(List.of("<g3#1>", "<g3#2>", "<g1#3>"), tags);
        String[][] parts = {{"PHẦN I.", "<g3#1>"}, {"PHẦN II.", "<g3#2>"}, {"PHẦN III.", "<g1#3>"}};
        for (String[] part : parts) {
            int heading = indexStarting(ps, part[0], 0);
            assertTrue(heading >= 0, part[0]);
            assertTrue(ps.get(heading + 1).text().startsWith("Học sinh"), "dòng hướng dẫn ngay sau tiêu đề " + part[0]);
            assertEquals(part[1], ps.get(heading + 2).text(), "dòng thẻ ngay dưới dòng hướng dẫn " + part[0]);
            assertTrue(ps.get(heading + 3).text().startsWith("Câu 1."), "câu 1 ngay sau dòng thẻ " + part[0]);
            assertTrue(runs(ps.get(heading + 2).xml()).stream().noneMatch(Map.Entry::getValue), "thẻ không gạch chân");
        }
        assertTrue(ps.get(0).text().startsWith("PHẦN I."), "đoạn đầu tiên của file là tiêu đề phần I");
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void nhanPhuongAnDungVaYDungGachChan(EquationFormat format) throws Exception {
        List<Para> ps = paragraphs(PandocTestSupport.documentXml(docx(format, 12)));
        // câu 1 (đáp án B), câu 2 (đáp án D), câu đúng/sai (a, c, d Đúng; b Sai)
        List<Map.Entry<String, Boolean>> labelRuns = new ArrayList<>();
        int part1 = indexStarting(ps, "PHẦN I.", 0);
        int part3 = indexStarting(ps, "PHẦN III.", 0);
        for (int i = part1; i < part3; i++) {
            for (Map.Entry<String, Boolean> run : runs(ps.get(i).xml())) {
                if (run.getValue()) {
                    labelRuns.add(run);
                }
            }
        }
        assertEquals(List.of("B.", "D.", "a)", "c)", "d)"), labelRuns.stream().map(Map.Entry::getKey).toList(),
                "chỉ nhãn đúng gạch chân, đúng thứ tự");
        // nhãn sai không gạch chân, nhưng có mặt
        List<String> plainLabels = new ArrayList<>();
        for (int i = part1; i < part3; i++) {
            for (Map.Entry<String, Boolean> run : runs(ps.get(i).xml())) {
                if (!run.getValue() && run.getKey().matches("[A-D]\\.|[a-d]\\)")) {
                    plainLabels.add(run.getKey());
                }
            }
        }
        assertEquals(List.of("A.", "C.", "D.", "A.", "B.", "C.", "b)"), plainLabels);
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void phanBaCoDongDapAnDuoiCauHoi(EquationFormat format) throws Exception {
        List<Para> ps = paragraphs(PandocTestSupport.documentXml(docx(format, 12)));
        int part3 = indexStarting(ps, "PHẦN III.", 0);
        List<String> expected = List.of("A. 10", "A. 2,5", "A. \\frac{1}{2}");
        for (int k = 1; k <= 3; k++) {
            int q = indexStarting(ps, "Câu " + k + ".", part3);
            assertTrue(q > part3, "câu " + k);
            Para answer = ps.get(q + 1);
            assertEquals(expected.get(k - 1), answer.text().strip(), "dòng đáp án ngay dưới câu " + k);
            assertFalse(answer.xml().contains("<m:oMath") || answer.xml().contains("Equation.DSMT4"),
                    "đáp án là chữ thường, không công thức");
        }
    }
}
