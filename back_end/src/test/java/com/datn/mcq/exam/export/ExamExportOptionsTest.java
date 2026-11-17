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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tùy chọn xuất Word (16/11): mã đề (NV-43, tham số {@code examCode}) in ở ô mã đề, tiêu đề trang đáp án và chân trang hai
 * section; chỉ đề (NV-44, {@code content=exam}) thì file dừng ở dòng HẾT, một section. Không truyền tham số mới thì file
 * như cũ. Dữ liệu đề dựng tay, không cần DB, không gọi Gemini; phần gọi pandoc thật bỏ qua khi máy không có pandoc.
 */
class ExamExportOptionsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private static ExamViewDto exam() {
        ExamItemDto mcq = new ExamItemDto(1, 1, 1, "I", "Đa thức", QuestionType.MCQ, "ready", "generated", null, 101,
                "Bậc của đơn thức $2x^3$ là", "$1$", "$3$", "$0$", "$2$", Option.B, null, null, "Số mũ của $x$ là $3$.",
                Difficulty.EASY, false, List.of());
        List<ItemStatementDto> statements = new ArrayList<>();
        String[] texts = {"Đồ thị là một parabol", "Hàm số đồng biến", "Giá trị nhỏ nhất bằng $0$", "Đồ thị đi qua $O$"};
        TruthValue[] labels = {TruthValue.TRUE, TruthValue.FALSE, TruthValue.TRUE, TruthValue.TRUE};
        for (int i = 0; i < 4; i++) {
            statements.add(new ItemStatementDto(StatementKey.values()[i], texts[i], labels[i], Difficulty.EASY));
        }
        ExamItemDto tf = new ExamItemDto(2, 2, 1, "II", "Hàm số", QuestionType.TF, "ready", "generated", null, 201,
                "Cho hàm số $y = x^2$.", null, null, null, null, null, null, statements, "Xét từng ý.", null, false,
                List.of());
        ExamItemDto sa = new ExamItemDto(3, 3, 1, "III", "Tứ giác", QuestionType.SA, "ready", "generated", null, 301,
                "Tính $5 \\cdot 2$.", null, null, null, null, null, "10", null, "Nhân hai số.", Difficulty.MEDIUM, false,
                List.of());
        return new ExamViewDto(16, "MT2611161200016", "Giữa kì I – Toán 8", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 11, 16, 8, 0), "ready", 0,
                List.of(new PartDto("I", 1, 1, List.of(mcq)), new PartDto("II", 1, 1, List.of(tf)),
                        new PartDto("III", 1, 1, List.of(sa))),
                "matrix");
    }

    private static ExamHeader header() {
        return ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, 45);
    }

    private static String markdown(ExamHeader header, ExportContent content) {
        return ExamMarkdownBuilder.build(exam(), header, ExamMarkdownBuilder.DEFAULT_SCORING, TODAY, content);
    }

    // ---------------------------------------------------------------- tham số content

    @Test
    void thamSoContent() {
        assertSame(ExportContent.ALL, ExportContent.parse(null));
        assertSame(ExportContent.ALL, ExportContent.parse("  "));
        assertSame(ExportContent.ALL, ExportContent.parse("all"));
        assertSame(ExportContent.EXAM, ExportContent.parse("exam"));
        assertSame(ExportContent.EXAM, ExportContent.parse(" EXAM "));
        for (String bad : List.of("abc", "answers", "exam,all")) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ExportContent.parse(bad));
            assertTrue(ex.getMessage().contains("all") && ex.getMessage().contains("exam"), ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- Markdown

    /** Không truyền tham số mới (mã 101, content all) thì Markdown đúng như bản dựng cũ. */
    @Test
    void macDinhGiongBanCu() {
        String old = ExamMarkdownBuilder.build(exam(), header(), ExamMarkdownBuilder.DEFAULT_SCORING, TODAY);
        assertEquals(old, markdown(header(), ExportContent.ALL));
        assertEquals(old, markdown(header().withExamCode("101"), ExportContent.ALL));
        assertTrue(old.contains(ExamMarkdownBuilder.ANSWER_TITLE));
        assertTrue(old.contains(">101<"), "ô mã đề");
    }

    @Test
    void maDeInOODauDeVaTieuDeDapAn() {
        String md = markdown(header().withExamCode("205"), ExportContent.ALL);
        assertTrue(md.contains(">205<"), "ô mã đề");
        assertTrue(md.contains("ĐÁP ÁN VÀ HƯỚNG DẪN CHẤM – MÃ ĐỀ 205"), "tiêu đề trang đáp án");
        assertFalse(md.contains(">101<") || md.contains("MÃ ĐỀ 101"), "không còn mã 101");
    }

    /** NV-44: chỉ đề thì Markdown dừng ở dòng HẾT, không có đáp án, ghi chú điểm, lời giải, không có ngắt section. */
    @Test
    void chiDeDungODongHet() {
        String md = markdown(header().withExamCode("205"), ExportContent.EXAM);
        String all = markdown(header().withExamCode("205"), ExportContent.ALL);
        assertTrue(md.strip().endsWith("```"), md);
        int end = md.lastIndexOf(ExamMarkdownBuilder.END_OF_EXAM);
        assertTrue(end > 0, "có dòng HẾT");
        assertFalse(md.substring(end).contains("<w:p>"), "không còn đoạn nào sau HẾT");
        for (String absent : List.of("ĐÁP ÁN", ExamMarkdownBuilder.SOLUTION_TITLE, "Tổng điểm phần trắc nghiệm",
                "Số mũ của", "Nhân hai số", "<w:sectPr", ExamPage.EXAM_FOOTER_REL_ID)) {
            assertFalse(md.contains(absent), "chỉ đề không có: " + absent);
        }
        assertTrue(md.contains(">205<") && md.contains("PHẦN III."), "phần đề giữ nguyên");
        int allEnd = all.indexOf(ExamMarkdownBuilder.END_OF_EXAM);
        String before = all.substring(0, all.lastIndexOf("```{=openxml}", allEnd));
        assertTrue(md.startsWith(before), "phần trước dòng HẾT giống hệt bản đầy đủ");
    }

    // ---------------------------------------------------------------- docx thật (pandoc)

    private static Optional<String> pandoc() {
        Optional<String> found = PandocTestSupport.find();
        assumeTrue(found.isPresent(), "máy không có pandoc: bỏ qua");
        return found;
    }

    private static byte[] docx(ExamHeader header, ExportContent content, EquationFormat format) {
        return new PandocRunner(pandoc().get()).markdownToDocx(markdown(header, content), format,
                new ExamDocxOptions(header.examCode(), content)).content();
    }

    /** Các part chân trang (word/footer*.xml) của file, theo tên part. */
    private static Map<String, String> footers(byte[] docx) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().startsWith("word/footer") && e.getName().endsWith(".xml")) {
                    out.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return out;
    }

    private static int count(String s, String part) {
        return s.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void maDeInOChanTrangHaiSection(EquationFormat format) throws Exception {
        byte[] file = docx(header().withExamCode("205"), ExportContent.ALL, format);
        String xml = PandocTestSupport.documentXml(file);
        assertEquals(2, count(xml, "<w:sectPr"), "hai section như cũ");
        assertTrue(xml.contains("ĐÁP ÁN VÀ HƯỚNG DẪN CHẤM – MÃ ĐỀ 205"));
        Map<String, String> footers = footers(file);
        assertTrue(footers.values().stream().anyMatch(f -> f.contains(">Mã đề 205<")), footers.keySet().toString());
        assertTrue(footers.values().stream().anyMatch(f -> f.contains(">Đáp án mã đề 205<")), footers.keySet().toString());
        assertFalse(footers.values().stream().anyMatch(f -> f.contains("ã đề 101<")), "không còn mã 101 ở chân trang");
    }

    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void macDinhChanTrangMaDe101(EquationFormat format) throws Exception {
        byte[] file = new PandocRunner(pandoc().get()).markdownToDocx(markdown(header(), ExportContent.ALL), format)
                .content();
        Map<String, String> footers = footers(file);
        assertTrue(footers.values().stream().anyMatch(f -> f.contains(">Mã đề 101<")));
        assertTrue(footers.values().stream().anyMatch(f -> f.contains(">Đáp án mã đề 101<")));
    }

    /** NV-44: chỉ đề thì file một section (đánh số trang từ 1 mặc định), chân trang "Mã đề X", dừng ở HẾT. */
    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void chiDeMotSectionDungODongHet(EquationFormat format) throws Exception {
        byte[] file = docx(header().withExamCode("205"), ExportContent.EXAM, format);
        String xml = PandocTestSupport.documentXml(file);
        assertEquals(1, count(xml, "<w:sectPr"), "một section");
        assertFalse(xml.contains("pgNumType"), "không đánh số lại trang");
        assertTrue(xml.contains("------ HẾT ------") && xml.contains("PHẦN III."));
        assertFalse(xml.contains("ĐÁP ÁN") || xml.contains("LỜI GIẢI"), "không có đáp án và lời giải");
        String afterEnd = xml.substring(xml.indexOf("------ HẾT ------"));
        assertEquals(0, count(afterEnd, "<w:p>") + count(afterEnd, "<w:p "), "không còn đoạn nào sau HẾT");
        assertTrue(xml.contains("w:w=\"" + ExamPage.PAGE_WIDTH_TWIPS + "\"")
                && xml.contains("w:left=\"" + ExamPage.MARGIN_LEFT_TWIPS + "\""), "khổ và lề như cũ");
        java.util.regex.Matcher ref = java.util.regex.Pattern.compile("<w:footerReference[^>]*r:id=\"([^\"]+)\"")
                .matcher(xml);
        assertTrue(ref.find(), "sectPr trỏ tới chân trang");
        String target = null;
        java.util.regex.Matcher rel = java.util.regex.Pattern.compile("<Relationship [^>]*>")
                .matcher(PandocTestSupport.entry(file, "word/_rels/document.xml.rels"));
        while (rel.find()) {
            if (rel.group().contains("Id=\"" + ref.group(1) + "\"")) {
                target = rel.group().replaceAll(".*Target=\"([^\"]+)\".*", "$1");
            }
        }
        Map<String, String> footers = footers(file);
        String footer = footers.get("word/" + target);
        assertTrue(footer != null && footer.contains(">Mã đề 205<") && !footer.contains("Đáp án"),
                target + " " + footers.keySet());
    }
}
