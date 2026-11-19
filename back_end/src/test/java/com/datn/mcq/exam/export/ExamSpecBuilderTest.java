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
import com.datn.mcq.exam.export.ExamSpecBuilder.SpecCell;
import com.datn.mcq.exam.export.ExamSpecBuilder.SpecRow;
import com.datn.mcq.exam.export.ExamSpecBuilder.SpecTable;
import com.datn.mcq.mathtype.docx.DocxPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
 * Bản đặc tả đề kiểm tra (18/11, mẫu Phụ lục 2 Công văn 7991/BGDĐT-GDTrH, quyết định D1–D7 của An): mỗi hàng là một (chủ đề,
 * nội dung), ô yêu cầu cần đạt liệt kê các mức; 9 cột TNKQ (dạng × mức) ghi "số câu (C…)", câu đánh lại từ 1 mỗi phần như
 * file đề; câu Đúng – Sai đếm theo ý ("2 (C1a, C1b)"); 3 cột Tự luận để trống; chân bảng tổng số câu, điểm, tỉ lệ. Chỗ chưa có
 * câu (đang soạn, hỏng) vẫn được đếm. Dữ liệu dựng tay, không cần DB, không gọi Gemini; phần gọi pandoc thật bỏ qua khi máy
 * không có pandoc.
 */
class ExamSpecBuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final String E = "Nhận biết được đơn thức, đa thức.";
    private static final String OPS = "Nhận biết được các phép tính cộng, trừ, nhân, chia đa thức.";
    private static final String OPS_M = "Thu gọn, tính được giá trị của đa thức.";
    private static final String OPS_H = "Chứng minh biểu thức không phụ thuộc vào biến.";
    private static final String QUAD_M = "Hiểu tính chất của hình thang cân.";
    private static final String QUAD_H = "Tính được góc trong tứ giác.";

    private static Map<Difficulty, String> outcomes(Object... levelAndText) {
        Map<Difficulty, String> out = new EnumMap<>(Difficulty.class);
        for (int i = 0; i < levelAndText.length; i += 2) {
            out.put((Difficulty) levelAndText[i], (String) levelAndText[i + 1]);
        }
        return out;
    }

    /**
     * Ma trận theo mẫu "Giữa kì I – Toán 8" thu gọn: chủ đề "Đa thức" (2 nội dung, câu ở phần I và II), chủ đề "Tứ giác"
     * (phần I và III). Thứ tự ô không theo phần để kiểm thứ tự hàng theo lần xuất hiện đầu.
     */
    private static List<SpecCell> cells() {
        return List.of(
                new SpecCell(11, 1, "I", QuestionType.MCQ, "Đa thức", "Đơn thức, đa thức",
                        outcomes(Difficulty.EASY, E), Difficulty.EASY, null),
                new SpecCell(12, 2, "I", QuestionType.MCQ, "Đa thức", "Các phép tính đa thức",
                        outcomes(Difficulty.EASY, OPS), Difficulty.EASY, null),
                new SpecCell(13, 3, "I", QuestionType.MCQ, "Tứ giác", "Tứ giác, hình thang cân",
                        outcomes(Difficulty.MEDIUM, QUAD_M), Difficulty.MEDIUM, null),
                new SpecCell(14, 4, "II", QuestionType.TF, "Đa thức", "Các phép tính đa thức",
                        outcomes(Difficulty.EASY, OPS, Difficulty.MEDIUM, OPS_M, Difficulty.HARD, OPS_H), null,
                        List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)),
                new SpecCell(15, 5, "III", QuestionType.SA, "Tứ giác", "Tứ giác, hình thang cân",
                        outcomes(Difficulty.HARD, QUAD_H), Difficulty.HARD, null));
    }

    private static int nextSlot = 1;

    private static ExamItemDto mcq(int cellId, int slotNo, Difficulty level) {
        return new ExamItemDto(nextSlot++, cellId, slotNo, "I", "x", QuestionType.MCQ, "ready", "generated", null, 100,
                "Câu hỏi $x$", "$1$", "$2$", "$3$", "$4$", Option.B, null, null, "Lời giải.", level, false, List.of());
    }

    private static ExamItemDto tf(int cellId, int slotNo) {
        List<ItemStatementDto> statements = new ArrayList<>();
        Difficulty[] levels = {Difficulty.EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD};
        for (int i = 0; i < 4; i++) {
            statements.add(new ItemStatementDto(StatementKey.values()[i], "Ý " + i, TruthValue.TRUE, levels[i]));
        }
        return new ExamItemDto(nextSlot++, cellId, slotNo, "II", "x", QuestionType.TF, "ready", "generated", null, 200,
                "Cho đa thức.", null, null, null, null, null, null, statements, "Xét từng ý.", null, false, List.of());
    }

    /** Chỗ chưa có câu: {@code state} "failed" (hỏng) hoặc "generating" (đang soạn). */
    private static ExamItemDto missing(int cellId, int slotNo, String part, QuestionType type, String state) {
        return new ExamItemDto(nextSlot++, cellId, slotNo, part, "x", type, state, null, null, null, null, null, null,
                null, null, null, null, null, null, null, false, List.of());
    }

    private static ExamViewDto exam() {
        List<ExamItemDto> partI = new ArrayList<>();
        for (int k = 1; k <= 4; k++) {
            partI.add(mcq(11, k, Difficulty.EASY));
        }
        partI.add(missing(12, 1, "I", QuestionType.MCQ, "failed"));
        partI.add(mcq(13, 1, Difficulty.MEDIUM));
        partI.add(missing(13, 2, "I", QuestionType.MCQ, "generating"));
        List<ExamItemDto> partII = List.of(tf(14, 1));
        List<ExamItemDto> partIII = List.of(missing(15, 1, "III", QuestionType.SA, "failed"),
                missing(15, 2, "III", QuestionType.SA, "pending"));
        return new ExamViewDto(18, "MT2611181200018", "Giữa kì I – Toán 8", "teacher", ExamStatus.DRAFT, 7,
                LocalDateTime.of(2026, 11, 18, 8, 0), "partial", 0,
                List.of(new PartDto("I", 7, 5, partI), new PartDto("II", 1, 1, partII),
                        new PartDto("III", 2, 0, partIII)),
                "matrix");
    }

    private static ExamHeader header() {
        return ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, 90);
    }

    private static SpecTable table() {
        return ExamSpecBuilder.table(exam(), cells(), ExamMarkdownBuilder.DEFAULT_SCORING);
    }

    /** 12 ô số câu của một hàng: Nhiều lựa chọn, Đúng – Sai, Trả lời ngắn, Tự luận × Biết, Hiểu, Vận dụng. */
    private static List<String> counts(String... nonEmpty) {
        List<String> out = new ArrayList<>(Collections.nCopies(12, ""));
        for (int i = 0; i < nonEmpty.length; i += 2) {
            out.set(Integer.parseInt(nonEmpty[i]), nonEmpty[i + 1]);
        }
        return out;
    }

    // ---------------------------------------------------------------- bảng (mô hình)

    @Test
    void hangTheoChuDeVaNoiDungTheoLanXuatHienDau() {
        List<SpecRow> rows = table().rows();
        assertEquals(3, rows.size(), rows.toString());
        assertEquals(List.of("Đa thức", "Đa thức", "Tứ giác"), rows.stream().map(SpecRow::topic).toList());
        assertEquals(List.of("Đơn thức, đa thức", "Các phép tính đa thức", "Tứ giác, hình thang cân"),
                rows.stream().map(SpecRow::content).toList());
        assertEquals(List.of(1, 1, 2), rows.stream().map(SpecRow::tt).toList(), "TT theo chủ đề");
        assertEquals(List.of(true, false, true), rows.stream().map(SpecRow::firstOfTopic).toList());
        assertEquals(2, rows.get(0).topicRowSpan(), "ô chủ đề gộp dọc 2 hàng");
        assertEquals(1, rows.get(2).topicRowSpan());
    }

    /** Ô yêu cầu cần đạt: các mức hàng có câu, theo thứ tự Biết, Hiểu, Vận dụng; câu trùng hệt nhau ghi một lần. */
    @Test
    void yeuCauCanDatLietKeTheoMuc() {
        List<SpecRow> rows = table().rows();
        assertEquals(List.of("- Biết: " + E), rows.get(0).outcomes());
        assertEquals(List.of("- Biết: " + OPS, "- Hiểu: " + OPS_M, "- Vận dụng: " + OPS_H), rows.get(1).outcomes(),
                "ô phần I và ô phần II cùng nội dung: mức Biết trùng chữ chỉ ghi một lần");
        assertEquals(List.of("- Hiểu: " + QUAD_M, "- Vận dụng: " + QUAD_H), rows.get(2).outcomes());
    }

    /**
     * Số câu đánh lại từ 1 mỗi phần theo thứ tự của file đề (ô theo vị trí, rồi số chỗ); 3 câu liên tiếp trở lên ghi dải
     * "C1–C4"; Đúng – Sai đếm theo ý ở mức của ý; chỗ hỏng và chỗ đang soạn vẫn được đếm; cột Tự luận trống.
     */
    @Test
    void soCauVaChiDinhCau() {
        List<SpecRow> rows = table().rows();
        assertEquals(counts("0", "4 (C1–C4)"), rows.get(0).counts());
        assertEquals(counts("0", "1 (C5)", "3", "2 (C1a, C1b)", "4", "1 (C1c)", "5", "1 (C1d)"), rows.get(1).counts(),
                "C5 là chỗ hỏng: vẫn đếm; ý a, b mức Biết, c Hiểu, d Vận dụng");
        assertEquals(counts("1", "2 (C6, C7)", "8", "2 (C1, C2)"), rows.get(2).counts(),
                "C7 đang soạn, phần III cả hai chỗ chưa có câu: vẫn đếm");
    }

    /** Chân bảng: tổng số câu theo cột (0 để trống); điểm và tỉ lệ theo dạng từ bảng điểm của đề; Tự luận trống. */
    @Test
    void chanBangTongSoCauDiemTiLe() {
        SpecTable t = table();
        assertEquals(counts("0", "5", "1", "2", "3", "2", "4", "1", "5", "1", "8", "2"), t.totalCounts());
        assertEquals(List.of("1,75", "1,0", "1,0", ""), t.points(), "7 × 0,25; 1 × 1; 2 × 0,5");
        assertEquals(List.of("17,5", "10", "10", ""), t.percents());
        assertTrue(t.essayNote(), "tổng 3,75 < 10: ghi chú phần tự luận");
    }

    /** Đề theo bài (ma trận ẩn kind = lesson): chủ đề là tên bài, nội dung trống, yêu cầu cần đạt mặc định in nguyên văn. */
    @Test
    void deTheoBaiNoiDungTrongYeuCauMacDinh() {
        String lesson = "Bài 3. Hằng đẳng thức đáng nhớ";
        String easy = "Nhận biết được kiến thức cơ bản của bài: " + lesson + ".";
        String medium = "Hiểu và vận dụng trực tiếp kiến thức của bài: " + lesson + ".";
        String hard = "Vận dụng kiến thức của bài \"" + lesson + "\" để giải quyết bài toán nhiều bước hoặc tình huống mới.";
        List<SpecCell> cells = List.of(
                new SpecCell(21, 1, "I", QuestionType.MCQ, lesson, null, outcomes(Difficulty.EASY, easy),
                        Difficulty.EASY, null),
                new SpecCell(22, 2, "II", QuestionType.TF, lesson, null,
                        outcomes(Difficulty.EASY, easy, Difficulty.MEDIUM, medium, Difficulty.HARD, hard), null,
                        List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)));
        ExamViewDto view = new ExamViewDto(19, "MT2611181200019", lesson, "teacher", ExamStatus.DRAFT, 8,
                LocalDateTime.of(2026, 11, 18, 8, 0), "generating", 0,
                List.of(new PartDto("I", 3, 0, List.of(missing(21, 1, "I", QuestionType.MCQ, "pending"),
                                missing(21, 2, "I", QuestionType.MCQ, "generating"),
                                missing(21, 3, "I", QuestionType.MCQ, "pending"))),
                        new PartDto("II", 1, 0, List.of(missing(22, 1, "II", QuestionType.TF, "pending"))),
                        new PartDto("III", 0, 0, List.of())),
                "lesson");
        SpecTable t = ExamSpecBuilder.table(view, cells, ExamMarkdownBuilder.DEFAULT_SCORING);
        assertEquals(1, t.rows().size(), "một bài, một hàng");
        SpecRow row = t.rows().get(0);
        assertEquals(lesson, row.topic());
        assertEquals("", row.content(), "nội dung trống để trống");
        assertEquals(List.of("- Biết: " + easy, "- Hiểu: " + medium, "- Vận dụng: " + hard), row.outcomes());
        assertEquals(counts("0", "3 (C1–C3)", "3", "2 (C1a, C1b)", "4", "1 (C1c)", "5", "1 (C1d)"), row.counts(),
                "ý của câu Đúng – Sai chưa có câu lấy mức theo ô ma trận");
        assertEquals(List.of("0,75", "1,0", "", ""), t.points(), "phần không có câu: điểm trống");
        assertEquals(List.of("7,5", "10", "", ""), t.percents());
    }

    /** Ô không có yêu cầu cần đạt cho mức đang dùng (dữ liệu cũ): ghi "—". */
    @Test
    void thieuYeuCauCanDatGhiGachNgang() {
        List<SpecCell> cells = List.of(new SpecCell(31, 1, "I", QuestionType.MCQ, "Đa thức", "Đơn thức",
                outcomes(), Difficulty.EASY, null));
        ExamViewDto view = new ExamViewDto(20, "MT1", "Đề", "teacher", ExamStatus.DRAFT, 9,
                LocalDateTime.of(2026, 11, 18, 8, 0), "ready", 0,
                List.of(new PartDto("I", 1, 1, List.of(mcq(31, 1, Difficulty.EASY)))), "matrix");
        SpecTable t = ExamSpecBuilder.table(view, cells, ExamMarkdownBuilder.DEFAULT_SCORING);
        assertEquals(List.of("- Biết: —"), t.rows().get(0).outcomes());
    }

    /** Tổng đúng 10 điểm thì không có ghi chú phần tự luận. */
    @Test
    void duMuoiDiemKhongGhiChuTuLuan() {
        List<SpecCell> cells = List.of(new SpecCell(41, 1, "I", QuestionType.MCQ, "Đa thức", "Đơn thức",
                outcomes(Difficulty.EASY, E), Difficulty.EASY, null));
        List<ExamItemDto> items = new ArrayList<>();
        for (int k = 1; k <= 40; k++) {
            items.add(mcq(41, k, Difficulty.EASY));
        }
        ExamViewDto view = new ExamViewDto(21, "MT2", "Đề", "teacher", ExamStatus.DRAFT, 9,
                LocalDateTime.of(2026, 11, 18, 8, 0), "ready", 0, List.of(new PartDto("I", 40, 40, items)), "matrix");
        SpecTable t = ExamSpecBuilder.table(view, cells, ExamMarkdownBuilder.DEFAULT_SCORING);
        assertEquals("40 (C1–C40)", t.rows().get(0).counts().get(0));
        assertEquals(List.of("10,0", "", "", ""), t.points());
        assertEquals(List.of("100", "", "", ""), t.percents());
        assertFalse(t.essayNote());
    }

    // ---------------------------------------------------------------- Markdown / openxml

    private static String markdown() {
        return ExamSpecBuilder.build(exam(), cells(), header(), ExamMarkdownBuilder.DEFAULT_SCORING, TODAY);
    }

    /** Chữ của mọi run (w:t) trong Markdown, nối theo thứ tự. */
    private static List<String> texts(String md) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<w:t(?: [^>]*)?>([^<]*)</w:t>").matcher(md);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    void dauDeKhongCoMaDeDiemMaPhach() {
        String md = markdown();
        List<String> t = texts(md);
        for (String expected : List.of(ExamMarkdownBuilder.HEADER_LEFT_1, ExamMarkdownBuilder.HEADER_LEFT_2,
                "ĐỀ KIỂM TRA GIỮA KÌ I", "NĂM HỌC 2026 - 2027", "Môn: Toán, Lớp: 8.",
                "(Thời gian làm bài 90 phút không kể thời gian giao đề)", ExamSpecBuilder.TITLE)) {
            assertTrue(t.contains(expected), "thiếu: " + expected + " trong " + t);
        }
        for (String absent : List.of("Mã đề", "Điểm bài thi", "Mã phách", "Thứ..... ngày")) {
            assertFalse(md.contains(absent), "đầu đề bản đặc tả không có: " + absent);
        }
    }

    @Test
    void tieuDeBangBonTangTheoMau() {
        String md = markdown();
        List<String> t = texts(md);
        for (String expected : List.of("TT", "Chủ đề/Chương", "Nội dung/đơn vị kiến thức", "Yêu cầu cần đạt",
                "Số câu hỏi ở các mức độ đánh giá", "TNKQ", "Tự luận", "Nhiều lựa chọn", "“Đúng – Sai”", "Trả lời ngắn",
                "Tổng số câu", "Tổng số điểm", "Tỉ lệ %")) {
            assertTrue(t.contains(expected), "thiếu: " + expected);
        }
        assertEquals(4, Collections.frequency(t, "Biết"), "Biết ở 4 dạng");
        assertEquals(4, Collections.frequency(t, "Hiểu"));
        assertEquals(4, Collections.frequency(t, "Vận dụng"));
        assertTrue(md.contains("<w:gridSpan w:val=\"12\"/>"), "ô 'Số câu hỏi…' trải 12 cột");
        assertTrue(md.contains("<w:gridSpan w:val=\"9\"/>"), "ô TNKQ trải 9 cột");
        assertTrue(md.contains("<w:tblHeader/>"), "tiêu đề bảng lặp lại ở mỗi trang");
        // hàng cuối của thân bảng và 2 dòng chân đầu giữ liền dòng sau: 3 dòng chân không tách khỏi bảng sang trang mới
        String lastRows = md.substring(md.lastIndexOf("Tứ giác, hình thang cân"));
        String tiLe = lastRows.substring(lastRows.indexOf("Tỉ lệ %"));
        assertTrue(lastRows.substring(0, lastRows.indexOf("Tỉ lệ %")).contains("<w:keepNext/>"), "giữ liền dòng sau");
        assertFalse(tiLe.contains("<w:keepNext/>"), "dòng cuối không cần giữ liền");
        assertEquals(7, t.stream().filter(s -> s.matches("\\d+ \\(C.*\\)")).count(),
                "7 ô số câu trong thân bảng: " + t);
    }

    @Test
    void thanBangVaGhiChu() {
        List<String> t = texts(markdown());
        for (String expected : List.of("Đa thức", "Tứ giác", "Đơn thức, đa thức", "- Biết: " + E, "4 (C1–C4)",
                "2 (C1a, C1b)", "1 (C1d)", "2 (C6, C7)", "1,75", "17,5", ExamMarkdownBuilder.ESSAY_NOTE)) {
            assertTrue(t.contains(expected), "thiếu: " + expected);
        }
        assertTrue(t.stream().anyMatch(s -> s.contains("Phần I – Câu 3")), "ghi chú cách đọc chỉ định câu: " + t);
        assertTrue(t.stream().anyMatch(s -> s.contains("đếm theo ý")), "ghi chú câu Đúng – Sai đếm theo ý");
    }

    /** Các khối openxml là XML hợp lệ (cân thẻ), chữ được thoát. */
    @Test
    void khoiOpenxmlHopLeVaThoatChu() throws Exception {
        List<SpecCell> cells = List.of(new SpecCell(51, 1, "I", QuestionType.MCQ, "A < B & \"C\"", "x > y",
                outcomes(Difficulty.EASY, "So sánh <a> & b"), Difficulty.EASY, null));
        ExamViewDto view = new ExamViewDto(22, "MT3", "Đề", "teacher", ExamStatus.DRAFT, 9,
                LocalDateTime.of(2026, 11, 18, 8, 0), "ready", 0,
                List.of(new PartDto("I", 1, 1, List.of(mcq(51, 1, Difficulty.EASY)))), "matrix");
        String md = ExamSpecBuilder.build(view, cells, header(), ExamMarkdownBuilder.DEFAULT_SCORING, TODAY);
        Matcher blocks = Pattern.compile("(?s)```\\{=openxml}\\n(.*?)\\n```").matcher(md);
        int n = 0;
        while (blocks.find()) {
            n++;
            String wrapped = "<root xmlns:w=\"" + DocxPackage.W + "\" xmlns:r=\"" + DocxPackage.R + "\">"
                    + blocks.group(1) + "</root>";
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.newDocumentBuilder().parse(new ByteArrayInputStream(wrapped.getBytes(StandardCharsets.UTF_8)));
        }
        assertTrue(n > 0, "có khối openxml");
        assertTrue(md.contains("A &lt; B &amp; &quot;C&quot;") && md.contains("So sánh &lt;a&gt; &amp; b"), md);
        int fences = md.split("```", -1).length - 1;
        assertEquals(2 * n, fences, "mỗi khối openxml đúng một cặp dấu ```, không có khối thô lạ");
    }

    @Test
    void thamSoContentSpec() {
        assertSame(ExportContent.SPEC, ExportContent.parse("spec"));
        assertSame(ExportContent.SPEC, ExportContent.parse(" SPEC "));
        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ExportContent.parse("dac-ta"));
        assertTrue(ex.getMessage().contains("spec"), ex.getMessage());
    }

    // ---------------------------------------------------------------- docx thật (pandoc)

    private static Optional<String> pandoc() {
        Optional<String> found = PandocTestSupport.find();
        assumeTrue(found.isPresent(), "máy không có pandoc: bỏ qua");
        return found;
    }

    /** File thật: một section A4 ngang, chân trang "Bản đặc tả", có bảng; không có đề, đáp án, mã đề. */
    @ParameterizedTest
    @EnumSource(EquationFormat.class)
    void docxMotSectionNgangTrang(EquationFormat format) throws Exception {
        byte[] file = new PandocRunner(pandoc().get()).markdownToDocx(markdown(), format,
                new ExamDocxOptions(null, ExportContent.SPEC)).content();
        String xml = PandocTestSupport.documentXml(file);
        assertEquals(1, xml.split("<w:sectPr", -1).length - 1, "một section");
        assertTrue(xml.contains("w:orient=\"landscape\""), "ngang trang");
        Matcher pgSz = Pattern.compile("<w:pgSz [^>]*/>").matcher(xml);
        assertTrue(pgSz.find(), "có khổ trang");
        String size = pgSz.group();
        assertTrue(size.contains("w:w=\"" + ExamPage.LANDSCAPE_WIDTH_TWIPS + "\"")
                && size.contains("w:h=\"" + ExamPage.LANDSCAPE_HEIGHT_TWIPS + "\""), "A4 ngang: " + size);
        for (String expected : List.of(ExamSpecBuilder.TITLE, "4 (C1–C4)", "2 (C1a, C1b)", "Tổng số điểm")) {
            assertTrue(xml.contains(expected), "docx thiếu: " + expected);
        }
        for (String absent : List.of("PHẦN I.", "ĐÁP ÁN", "HẾT", "Mã phách")) {
            assertFalse(xml.contains(absent), "bản đặc tả không có: " + absent);
        }
        String footer = PandocTestSupport.entry(file, "word/footerAnswer.xml");
        assertTrue(footer.contains(">" + ExamReferenceDoc.SPEC_FOOTER_LEFT + "<"), footer);
        assertTrue(footer.contains("w:pos=\"" + ExamPage.LANDSCAPE_TEXT_WIDTH_TWIPS + "\""),
                "tab phải của chân trang ở lề phải trang ngang");
        assertFalse(footer.contains("Mã đề") || footer.contains("Đáp án"), footer);
    }
}
