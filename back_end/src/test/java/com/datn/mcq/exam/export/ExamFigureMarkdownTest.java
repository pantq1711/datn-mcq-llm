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
import com.datn.mcq.questionimage.dto.QuestionImageDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O2 (G0 H2, H9, mục 4.2 của cau-co-hinh.md): token {@code ⟦img:<id>⟧} trong file xuất thành ảnh data URI có alt
 * {@code qimg:<id>}, không bao giờ ra Word dưới dạng chữ. Không cần pandoc; byte ảnh tự tạo (không phải ảnh bộ đề).
 */
class ExamFigureMarkdownTest {

    private static final ExamHeader HEADER = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private static final byte[] BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4};
    private static final String B64 = Base64.getEncoder().encodeToString(BYTES);

    private static ExamFigures.Figure fig(long id, int mm) {
        return new ExamFigures.Figure(id, "image/png", BYTES, mm);
    }

    private static ExamItemDto mcq(String stem, String a, String b, String c, String d, String explanation) {
        return new ExamItemDto(1, 1, 1, "I", "Tam giác", QuestionType.MCQ, "ready", "bank", 77, 101, stem, a, b, c, d,
                Option.A, null, null, explanation, Difficulty.EASY, false, List.of());
    }

    private static ExamItemDto tf(String stem, List<ItemStatementDto> statements, String explanation) {
        return new ExamItemDto(2, 2, 1, "II", "Tam giác", QuestionType.TF, "ready", "bank", 78, 102, stem, null, null,
                null, null, null, null, statements, explanation, Difficulty.EASY, false, List.of());
    }

    private static ExamViewDto view(ExamItemDto... items) {
        List<PartDto> parts = new java.util.ArrayList<>();
        for (ExamItemDto i : items) {
            parts.add(new PartDto(i.part(), 1, 1, List.of(i)));
        }
        return new ExamViewDto(5, "MT2611231200001", "Đề hình", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 11, 23, 12, 0), "ready", 0, parts, "matrix");
    }

    private static String build(ExamViewDto view, ExportContent content, Map<Long, ExamFigures.Figure> figures) {
        return ExamMarkdownBuilder.build(view, HEADER, ExamMarkdownBuilder.DEFAULT_SCORING, TODAY, content, figures);
    }

    private static String figureBlock(long id, int mm) {
        return "::: {custom-style=\"ExamFigure\"}\n![qimg:" + id + "](data:image/png;base64," + B64 + "){width=" + mm
                + "mm}\n:::";
    }

    @Test
    void withoutLiteralNewlinesGiuNguyenImagesCuaMucDe() {
        QuestionImageDto image = new QuestionImageDto(5L, "stem", (short) 1, "image/png", 10, 10, (short) 50, "/u");
        ExamItemDto item = mcq("Cho hình.\n⟦img:5⟧", "$1$", "$2$", "$3$", "$4$", null).withImages(List.of(image));

        ExamItemDto clean = ExamMarkdownBuilder.withoutLiteralNewlines(view(item)).parts().get(0).items().get(0);

        assertEquals(List.of(image), clean.images());
        assertEquals("Cho hình.\n⟦img:5⟧", clean.questionText(), "cleanText không đổi token");
    }

    @Test
    void tokenRiengDongOCauDanThanhDoanExamFigureCanGiuaRongToiDa120mm() {
        ExamItemDto item = mcq("Cho tam giác $ABC$ như hình vẽ.\n⟦img:5⟧\nTính $BC$.", "$5$", "$7$", "$9$", "$25$",
                null);

        String md = build(view(item), ExportContent.ALL, Map.of(5L, fig(5, 150)));

        assertTrue(md.contains("::: {custom-style=\"ExamQuestion\"}\n**Câu 1.** Cho tam giác $ABC$ như hình vẽ.\n:::\n\n"
                + figureBlock(5, 120) + "\n\n::: {custom-style=\"ExamQuestionCont\"}\nTính $BC$.\n:::"), md);
        assertFalse(md.contains("⟦img"), md);
        assertEquals(Map.of(5L, 1), ExamFigures.expectedInMarkdown(md));
    }

    @Test
    void beRongAnhCauDanLaDisplayWidthKhiNhoHon120mm() {
        String md = build(view(mcq("Cho hình.\n⟦img:5⟧", "$5$", "$7$", "$9$", "$25$", null)), ExportContent.ALL,
                Map.of(5L, fig(5, 50)));

        assertTrue(md.contains(figureBlock(5, 50)), md);
    }

    @Test
    void anhOPhuongAnEpBoCucHaiCotRongToiDa40mmVaKhongThemDauCham() {
        ExamItemDto item = mcq("Hình nào là hình vuông?", "$5$", "$7$", "⟦img:6⟧", "$25$", null);

        String md = build(view(item), ExportContent.ALL, Map.of(6L, fig(6, 80)));

        String inline = "![qimg:6](data:image/png;base64," + B64 + "){width=40mm}";
        assertTrue(md.contains("**C.** " + inline), md);
        assertFalse(md.contains(inline + "."), "không thêm dấu chấm sau ảnh");
        String two = ExamTabs.token(OptionLayout.TWO_COLUMNS.stopsTwips()[0]);
        assertTrue(md.contains(two), "phương án có ảnh xếp 2 cột");
        assertFalse(md.contains(ExamTabs.token(OptionLayout.FOUR_COLUMNS.stopsTwips()[0])),
                "phương án ngắn có ảnh không xếp 4 cột");
        // không có ảnh thì 4 phương án ngắn vẫn xếp 4 cột như cũ
        String plain = build(view(mcq("Hình nào?", "$5$", "$7$", "$9$", "$25$", null)), ExportContent.ALL, Map.of());
        assertTrue(plain.contains(ExamTabs.token(OptionLayout.FOUR_COLUMNS.stopsTwips()[0])));
    }

    @Test
    void anhOYDungSaThanhDoanRiengSauDongY() {
        List<ItemStatementDto> statements = List.of(
                new ItemStatementDto(StatementKey.A, "Tam giác cân", TruthValue.TRUE, Difficulty.EASY),
                new ItemStatementDto(StatementKey.B, "Hình bên là hình vuông\n⟦img:7⟧", TruthValue.FALSE,
                        Difficulty.EASY));

        String md = build(view(tf("Cho các hình sau.", statements, null)), ExportContent.ALL, Map.of(7L, fig(7, 60)));

        assertTrue(md.contains("**b)** Hình bên là hình vuông.\n:::\n\n" + figureBlock(7, 60)), md);
        assertFalse(md.contains("⟦img"), md);
    }

    @Test
    void anhLoiGiaiChiCoOFileDayDuKhongCoOFileChiDe() {
        ExamItemDto item = mcq("Tính $x$.", "$5$", "$7$", "$9$", "$25$", "Xem hình.\n⟦img:8⟧\nVậy chọn A.");
        Map<Long, ExamFigures.Figure> figures = Map.of(8L, fig(8, 70));

        String all = build(view(item), ExportContent.ALL, figures);
        String exam = build(view(item), ExportContent.EXAM, figures);

        assertTrue(all.contains("Xem hình.\n\n" + figureBlock(8, 70) + "\n\nVậy chọn A."), all);
        assertFalse(exam.contains("qimg:8"), exam);
        assertFalse(exam.contains("⟦img"), exam);
        assertEquals(Map.of(8L, 1), ExamFigures.expectedInMarkdown(all));
        assertEquals(Map.of(), ExamFigures.expectedInMarkdown(exam));
    }

    @Test
    void cungMotAnhDungHaiChoDemHaiLan() {
        ExamItemDto item = mcq("Cho hình.\n⟦img:5⟧", "$5$", "$7$", "$9$", "$25$", "Nhìn lại hình.\n⟦img:5⟧");

        String md = build(view(item), ExportContent.ALL, Map.of(5L, fig(5, 50)));

        assertEquals(Map.of(5L, 2), ExamFigures.expectedInMarkdown(md));
    }

    @Test
    void tokenKhongCoAnhTuongUngThiDungVoiExportImageMissing() {
        ExamItemDto item = mcq("Cho hình.\n⟦img:9⟧", "$5$", "$7$", "$9$", "$25$", null);

        DocumentExportException e = assertThrows(DocumentExportException.class,
                () -> build(view(item), ExportContent.ALL, Map.of(5L, fig(5, 50))));
        assertEquals(500, e.getHttpStatus());
        assertEquals(ExamFigures.MISSING_CODE, e.getCode());
        assertEquals("EXPORT_IMAGE_MISSING", ExamFigures.MISSING_CODE);
    }

    @Test
    void tokenTrongCongThucHoacDapSoKhongRaChuMaBaoLoi() {
        ExamItemDto inMath = mcq("Tính $x + ⟦img:5⟧$.", "$5$", "$7$", "$9$", "$25$", null);
        assertEquals(ExamFigures.MISSING_CODE, assertThrows(DocumentExportException.class,
                () -> build(view(inMath), ExportContent.ALL, Map.of(5L, fig(5, 50)))).getCode());

        ExamItemDto malformed = mcq("Cho hình ⟦img:abc⟧ sau.", "$5$", "$7$", "$9$", "$25$", null);
        assertEquals(ExamFigures.MISSING_CODE, assertThrows(DocumentExportException.class,
                () -> build(view(malformed), ExportContent.ALL, Map.of())).getCode());

        ExamItemDto sa = new ExamItemDto(3, 3, 1, "III", "Tam giác", QuestionType.SA, "ready", "bank", 79, 103,
                "Tính $BC$.", null, null, null, null, null, "⟦img:5⟧", null, null, Difficulty.EASY, false, List.of());
        assertEquals(ExamFigures.MISSING_CODE, assertThrows(DocumentExportException.class,
                () -> build(view(sa), ExportContent.ALL, Map.of(5L, fig(5, 50)))).getCode());
    }

    @Test
    void deKhongCoTokenGiuNguyenMarkdownNhuTruoc() {
        ExamItemDto item = mcq("Cho $x = 2$. Tính $x^2$.", "$1$", "$2$", "$3$", "$4$", "Ta có $x^2 = 4$.");

        assertEquals(ExamMarkdownBuilder.build(view(item), HEADER, ExamMarkdownBuilder.DEFAULT_SCORING, TODAY,
                        ExportContent.ALL),
                build(view(item), ExportContent.ALL, Map.of(5L, fig(5, 50))));
    }

    @Test
    void idAnhCanNapTheoNoiDungXuat() {
        List<ItemStatementDto> statements = List.of(
                new ItemStatementDto(StatementKey.A, "Ý a\n⟦img:13⟧", TruthValue.TRUE, Difficulty.EASY));
        ExamViewDto v = view(mcq("Cho hình.\n⟦img:11⟧", "⟦img:12⟧", "$7$", "$9$", "$25$", "Lời giải\n⟦img:14⟧\n⟦img:11⟧"),
                tf("Câu dẫn", statements, "Giải\n⟦img:15⟧"));

        assertEquals(Map.of(11L, 2, 12L, 1, 13L, 1, 14L, 1, 15L, 1), ExamFigures.referenced(v, ExportContent.ALL));
        assertEquals(Map.of(11L, 1, 12L, 1, 13L, 1), ExamFigures.referenced(v, ExportContent.EXAM));
        assertEquals(Map.of(), ExamFigures.referenced(v, ExportContent.SPEC));
    }

    @Test
    void tongDungLuongTinhTheoSoLanChenVaLoiNhanCoSoMb() {
        assertEquals(3_000_000L, ExamFigures.totalBytes(Map.of(1L, 2, 2L, 1), Map.of(1L, 1_000_000, 2L, 1_000_000)));

        DocumentExportException e = ExamFigures.tooLarge(9_500_000L, 8L * 1024 * 1024);
        assertEquals(422, e.getHttpStatus());
        assertEquals("EXPORT_IMAGES_TOO_LARGE", e.getCode());
        assertEquals("Tổng hình trong đề quá lớn để xuất Word (9,1 MB, tối đa 8 MB). Hãy bỏ bớt câu có hình.",
                e.getMessage());
    }
}
