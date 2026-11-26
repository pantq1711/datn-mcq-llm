package com.datn.mcq.exam.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tờ phách và cỡ chữ của file Word (26/11, tham số {@code phach} và {@code fontSize}).
 * <ul>
 *   <li>{@code phach=false} (mặc định mới): bảng ô dưới đầu đề là "Mã đề | Điểm", ngay dưới là dòng "Họ và tên: … Lớp: …";</li>
 *   <li>{@code phach=true}: đầu đề như trước 26/11 ("Mã đề | Điểm bài thi | Mã phách", không họ tên) và một trang tờ phách
 *       đầu file (section riêng không chân trang; section đề đánh số trang lại từ 1);</li>
 *   <li>{@code fontSize=14}: mọi kiểu chữ của đề cỡ 14 (sz 28), dòng ô mã đề cao theo tỉ lệ; bản đặc tả giữ 12.</li>
 * </ul>
 * Dữ liệu đề của {@link ExamYoungMixExportTest}; phần pandoc bỏ qua khi thiếu pandoc.
 */
class ExamPhachFontSizeExportTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
    private static final ExamHeader HEADER = ExamHeader.of("Cuối kì I – Toán 8", "Toán", 8, 90);
    static final String NAME_LINE = "Họ và tên: …………………… Lớp: ………";

    private static ExamDocxOptions options(ExportContent content, boolean phach, int fontSize) {
        return new ExamDocxOptions("205", content, phach, fontSize);
    }

    private static String markdown(ExamDocxOptions o) {
        return ExamMarkdownBuilder.build(ExamYoungMixExportTest.exam(), HEADER.withExamCode(o.examCode()),
                ExamMarkdownBuilder.DEFAULT_SCORING, TODAY, o, ExamYoungMixExportTest.figures());
    }

    // ---------------------------------------------------------------- tham số

    @Test
    void thamSoFontSize() {
        assertEquals(12, ExamDocxOptions.parseFontSize(null));
        assertEquals(12, ExamDocxOptions.parseFontSize(" "));
        assertEquals(12, ExamDocxOptions.parseFontSize("12"));
        assertEquals(14, ExamDocxOptions.parseFontSize(" 14 "));
        for (String bad : List.of("13", "12pt", "abc", "014", "+14", "14.0")) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ExamDocxOptions.parseFontSize(bad), bad);
            assertTrue(ex.getMessage().contains("fontSize") && ex.getMessage().contains("12")
                    && ex.getMessage().contains("14"), ex.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> new ExamDocxOptions("101", ExportContent.ALL, false, 13));
    }

    @Test
    void thamSoPhach() {
        assertFalse(ExamDocxOptions.parsePhach(null));
        assertFalse(ExamDocxOptions.parsePhach(""));
        assertTrue(ExamDocxOptions.parsePhach("true"));
        assertTrue(ExamDocxOptions.parsePhach(" TRUE "));
        assertFalse(ExamDocxOptions.parsePhach("false"));
        for (String bad : List.of("yes", "1", "co", "truee")) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ExamDocxOptions.parsePhach(bad), bad);
            assertTrue(ex.getMessage().contains("phach") && ex.getMessage().contains("true"), ex.getMessage());
        }
    }

    /** Tờ phách chỉ cho "Chỉ đề" và "Đề kèm đáp án"; bản đặc tả giữ cỡ 12. */
    @Test
    void phachVaCoChuChiApChoNoiDungPhuHop() {
        assertTrue(options(ExportContent.ALL, true, 12).phach());
        assertTrue(options(ExportContent.EXAM, true, 12).phach());
        assertFalse(options(ExportContent.SPEC, true, 12).phach());
        assertFalse(options(ExportContent.YOUNGMIX, true, 12).phach());
        assertEquals(12, options(ExportContent.SPEC, false, 14).fontSizePt());
        assertEquals(14, options(ExportContent.YOUNGMIX, false, 14).fontSizePt());
        assertEquals(14, options(ExportContent.EXAM, false, 14).fontSizePt());
        assertSame(ExportContent.ALL, ExamDocxOptions.DEFAULT.content());
        assertFalse(ExamDocxOptions.DEFAULT.phach());
        assertEquals(12, ExamDocxOptions.DEFAULT.fontSizePt());
    }

    // ---------------------------------------------------------------- đầu đề (Markdown)

    /** Khối openxml đầu tiên: bảng đầu đề, bảng ô, dòng họ tên. */
    private static String firstBlock(String md) {
        return md.substring(0, md.indexOf("\n```\n\n"));
    }

    @Test
    void khongPhachHaiOMaDeDiemVaDongHoTen() {
        for (ExportContent c : List.of(ExportContent.ALL, ExportContent.EXAM)) {
            String block = firstBlock(markdown(options(c, false, 12)));
            assertTrue(block.contains(">Mã đề<") && block.contains(">Điểm<") && block.contains(">205<"), block);
            assertFalse(block.contains("Mã phách") || block.contains("Điểm bài thi"), "bỏ Mã phách: " + c);
            assertTrue(block.contains(NAME_LINE), "dòng họ tên dưới bảng ô: " + c);
            assertTrue(block.indexOf(NAME_LINE) > block.lastIndexOf("</w:tbl>"), "dòng họ tên nằm sau bảng ô");
        }
        // mặc định (không truyền phach, đường dựng theo content như trước 26/11) là không phách
        String old = ExamMarkdownBuilder.build(ExamYoungMixExportTest.exam(), HEADER, ExamMarkdownBuilder.DEFAULT_SCORING,
                TODAY, ExportContent.ALL, ExamYoungMixExportTest.figures());
        assertTrue(old.contains(NAME_LINE) && !old.contains("Mã phách"));
    }

    @Test
    void coPhachTrangToPhachRoiDauDeCu() {
        String md = markdown(options(ExportContent.EXAM, true, 12));
        int page = md.indexOf(">Mã phách<");
        int section = md.indexOf("<w:sectPr>");
        int header = md.indexOf(">Điểm bài thi<");
        assertTrue(page > 0 && section > page && header > section, "tờ phách, ngắt section, rồi đầu đề");
        String phach = md.substring(0, section);
        for (String part : List.of("UBND PHƯỜNG", "TRƯỜNG THCS", "Họ và tên:", "Lớp:",
                "Thứ..... ngày........ tháng..... năm 2026", "ĐỀ KIỂM TRA CUỐI KÌ I", "NĂM HỌC 2026 - 2027",
                "Môn: Toán, Lớp: 8.", "(Thời gian làm bài 90 phút không kể thời gian giao đề)")) {
            assertTrue(phach.contains(part), "tờ phách thiếu: " + part);
        }
        assertFalse(phach.contains("PHẦN I.") || phach.contains("Câu 1.") || phach.contains(">205<"),
                "tờ phách không có đề, không có mã đề");
        assertFalse(phach.substring(0, section).contains("footerReference")
                        || md.substring(section, md.indexOf("</w:sectPr>", section)).contains("footerReference"),
                "section tờ phách không có chân trang");
        String rest = md.substring(section);
        assertTrue(rest.contains(">Mã đề<") && rest.contains(">Điểm bài thi<") && rest.contains(">Mã phách<"),
                "đầu đề 3 ô như trước 26/11");
        assertFalse(rest.contains(NAME_LINE), "đầu đề có phách không có dòng họ tên");
    }

    // ---------------------------------------------------------------- docx thật

    private static Optional<String> pandoc() {
        Optional<String> found = PandocTestSupport.find();
        assumeTrue(found.isPresent(), "máy không có pandoc: bỏ qua");
        return found;
    }

    private static byte[] docx(ExamDocxOptions o, EquationFormat format) {
        return new PandocRunner(pandoc().get()).markdownToDocx(markdown(o), format, o).content();
    }

    private static List<String> sectPrs(String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<w:sectPr[ >].*?</w:sectPr>", Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** Ô chữ của các bảng của thân tài liệu, theo thứ tự (mỗi bảng: các hàng, mỗi hàng: chữ từng ô). */
    private static List<List<List<String>>> tables(String xml) {
        List<List<List<String>>> out = new ArrayList<>();
        Matcher tbl = Pattern.compile("<w:tbl>.*?</w:tbl>", Pattern.DOTALL).matcher(xml);
        while (tbl.find()) {
            List<List<String>> rows = new ArrayList<>();
            Matcher tr = Pattern.compile("<w:tr[ >].*?</w:tr>", Pattern.DOTALL).matcher(tbl.group());
            while (tr.find()) {
                List<String> cells = new ArrayList<>();
                Matcher tc = Pattern.compile("<w:tc>.*?</w:tc>", Pattern.DOTALL).matcher(tr.group());
                while (tc.find()) {
                    cells.add(ExamYoungMixExportTest.paragraphs(tc.group()).stream()
                            .map(ExamYoungMixExportTest.Para::text).reduce((a, b) -> a + "|" + b).orElse(""));
                }
                rows.add(cells);
            }
            out.add(rows);
        }
        return out;
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void docxKhongPhachBangHaiOVaDongHoTen(EquationFormat format) throws Exception {
        String xml = PandocTestSupport.documentXml(docx(options(ExportContent.ALL, false, 12), format));
        List<List<List<String>>> tables = tables(xml);
        assertEquals(List.of(List.of("Mã đề", "Điểm"), List.of("205", "")), tables.get(1), "bảng ô 2 cột");
        List<ExamYoungMixExportTest.Para> ps = ExamYoungMixExportTest.paragraphs(
                xml.substring(xml.indexOf("</w:tbl>", xml.indexOf(">Điểm<"))));
        assertEquals(NAME_LINE, ps.get(0).text(), "đoạn ngay sau bảng ô là dòng họ tên");
        assertEquals(2, sectPrs(xml).size(), "hai section như cũ");
        assertFalse(xml.contains("Mã phách"));
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void docxCoPhachBaSectionToPhachKhongChanTrang(EquationFormat format) throws Exception {
        byte[] file = docx(options(ExportContent.ALL, true, 12), format);
        String xml = PandocTestSupport.documentXml(file);
        List<String> sects = sectPrs(xml);
        assertEquals(3, sects.size(), "tờ phách, đề, đáp án");
        assertFalse(sects.get(0).contains("footerReference"), "tờ phách không có chân trang");
        assertTrue(sects.get(1).contains(ExamPage.EXAM_FOOTER_REL_ID) && sects.get(1).contains("<w:pgNumType w:start=\"1\"/>"),
                "section đề có chân trang Mã đề, đánh số trang từ 1");
        assertTrue(sects.get(2).contains("<w:pgNumType w:start=\"1\"/>"), "section đáp án đánh số lại");
        List<List<List<String>>> tables = tables(xml);
        List<String> left = List.of(tables.get(0).get(0).get(0).split("\\|"));
        assertEquals(List.of("UBND PHƯỜNG ……………", "TRƯỜNG THCS ……………"), left.subList(0, 2));
        assertTrue(left.get(2).startsWith("Họ và tên:") && left.get(3).startsWith("Lớp:"), left.toString());
        assertEquals(List.of("Thứ..... ngày........ tháng..... năm 2026", "ĐỀ KIỂM TRA CUỐI KÌ I", "NĂM HỌC 2026 - 2027",
                "Môn: Toán, Lớp: 8.", "(Thời gian làm bài 90 phút không kể thời gian giao đề)"),
                List.of(tables.get(0).get(0).get(1).split("\\|")));
        assertEquals("Mã phách", tables.get(1).get(0).get(0), "ô có viền Mã phách");
        assertTrue(xml.indexOf("Mã phách") < xml.indexOf("<w:sectPr"), "ô Mã phách ở trang tờ phách");
        assertEquals(List.of(List.of("Mã đề", "Điểm bài thi", "Mã phách"), List.of("205", "", "")), tables.get(3),
                "đầu đề có phách như trước 26/11");
        assertFalse(xml.contains(NAME_LINE));
        assertTrue(ExamExportOptionsTest.footersOf(file).values().stream().anyMatch(f -> f.contains(">Mã đề 205<")));
    }

    @Test
    void docxChiDeCoPhachHaiSection() throws Exception {
        String xml = PandocTestSupport.documentXml(docx(options(ExportContent.EXAM, true, 12), EquationFormat.OMML));
        List<String> sects = sectPrs(xml);
        assertEquals(2, sects.size(), "tờ phách, đề");
        assertFalse(sects.get(0).contains("footerReference"));
        assertTrue(sects.get(1).contains("footerReference") && sects.get(1).contains("<w:pgNumType w:start=\"1\"/>"));
        assertFalse(xml.contains("ĐÁP ÁN"));
    }

    // ---------------------------------------------------------------- cỡ chữ

    private static String styles(ExamDocxOptions o) throws Exception {
        return PandocTestSupport.entry(PandocRunner.referenceDocBytes(o), "word/styles.xml");
    }

    private static List<Integer> sizes(String styles) {
        List<Integer> out = new ArrayList<>();
        Matcher m = Pattern.compile("<w:sz w:val=\"(\\d+)\"").matcher(styles);
        while (m.find()) {
            out.add(Integer.parseInt(m.group(1)));
        }
        return out;
    }

    private static String style(String styles, String id) {
        Matcher m = Pattern.compile("(?s)<w:style\\s[^>]*w:styleId=\"" + id + "\"[^>]*>(.*?)</w:style>").matcher(styles);
        assertTrue(m.find(), id);
        return m.group(1);
    }

    @Test
    void coChu14MoiKieuSz28() throws Exception {
        for (ExportContent c : List.of(ExportContent.ALL, ExportContent.EXAM, ExportContent.YOUNGMIX)) {
            String styles = styles(options(c, false, 14));
            assertTrue(PandocTestSupport.normalIsTimesNewRoman(styles, 28), "Normal 14: " + c);
            assertFalse(sizes(styles).contains(24), "không còn cỡ 12: " + c);
            assertTrue(style(styles, "Heading1").contains("<w:sz w:val=\"28\""), "tiêu đề cỡ 14");
            assertTrue(style(styles, "ExamBoxCell").contains("w:line=\"420\""), "dòng ô mã đề cao theo tỉ lệ 14/12");
        }
        String normal = styles(options(ExportContent.ALL, false, 12));
        assertTrue(PandocTestSupport.normalIsTimesNewRoman12(normal));
        assertTrue(style(normal, "ExamBoxCell").contains("w:line=\"360\""), "cỡ 12 như cũ");
        assertTrue(PandocTestSupport.normalIsTimesNewRoman12(styles(options(ExportContent.SPEC, false, 14))),
                "bản đặc tả giữ cỡ 12");
    }

    @Test
    void coChu14HangMaDeCaoTheoTiLe() {
        assertTrue(markdown(options(ExportContent.ALL, false, 14)).contains("<w:trHeight w:val=\"1680\"/>"));
        assertTrue(markdown(options(ExportContent.ALL, false, 12)).contains("<w:trHeight w:val=\"1440\"/>"));
    }

    /** Phương án vừa 4 cột ở cỡ 12 nhưng không vừa ở cỡ 14 thì xuống 2 cột. */
    @Test
    void optionLayoutDoTheoCo14() {
        double quarter = ExamPage.optionLineWidthPt() / 4;
        String found = null;
        for (int n = 1; n < 60 && found == null; n++) {
            String option = "x".repeat(n);
            double w = OptionLayout.widthPt('A', option, 12);
            if (w <= quarter - OptionLayout.PADDING_PT && w > quarter - OptionLayout.PADDING_PT - 8) {
                found = option;
            }
        }
        assertTrue(found != null, "không dựng được phương án sát mức 4 cột");
        List<String> four = List.of(found, found, found, found);
        assertEquals(OptionLayout.FOUR_COLUMNS, OptionLayout.choose(four, 12));
        assertEquals(OptionLayout.FOUR_COLUMNS, OptionLayout.choose(four));
        assertEquals(OptionLayout.TWO_COLUMNS, OptionLayout.choose(four, 14));
        double ratio = OptionLayout.widthPt('A', "abc $x^2+1$ def", 14) / OptionLayout.widthPt('A', "abc $x^2+1$ def", 12);
        assertEquals(14.0 / 12, ratio, 0.03, "chữ và công thức đo theo cỡ 14");
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void docxCo14(EquationFormat format) throws Exception {
        byte[] file = docx(options(ExportContent.EXAM, false, 14), format);
        String styles = PandocTestSupport.entry(file, "word/styles.xml");
        assertTrue(PandocTestSupport.normalIsTimesNewRoman(styles, 28));
        String xml = PandocTestSupport.documentXml(file);
        assertFalse(xml.contains("<w:sz w:val=\"24\""), "không có chữ cỡ 12 đặt trực tiếp");
    }
}
