package com.datn.mcq.exam.export;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * O2 (mục 4.2 của cau-co-hinh.md) với pandoc thật: ảnh data URI vào docx dưới {@code --sandbox} ở cả Equation và MathType,
 * {@code wp:docPr descr} lấy từ alt {@code qimg:<id>}, đếm lại sau pandoc và sau MathType; pandoc bỏ ảnh mà vẫn exit 0 thì
 * {@code EXPORT_IMAGE_MISSING}, không trả file. Ảnh tự vẽ bằng ImageIO. Thiếu pandoc thì bỏ qua.
 */
class ExamFigureDocxTest {

    private static PandocRunner runner;

    @BeforeAll
    static void findPandoc() {
        var path = PandocTestSupport.find();
        assumeTrue(path.isPresent(), "máy không có pandoc");
        runner = new PandocRunner(path.get());
    }

    static byte[] png(int w, int h, Color color) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(color);
        g.drawOval(2, 2, w - 4, h - 4);
        g.drawLine(0, h - 1, w - 1, 0);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static ExamViewDto figureExam() {
        ExamItemDto mcq = new ExamItemDto(1, 1, 1, "I", "Tam giác", QuestionType.MCQ, "ready", "bank", 77, 101,
                "Cho tam giác $ABC$ vuông tại $A$ như hình vẽ, $AB = 3$, $AC = 4$.\n⟦img:5⟧\nTính $BC$.",
                "$5$", "$\\sqrt{7}$", "⟦img:6⟧", "$25$", Option.A, null, null,
                "Theo định lí Pythagore $BC^2 = AB^2 + AC^2 = 25$.\n⟦img:5⟧\nVậy $BC = 5$.", Difficulty.EASY, false,
                List.of());
        return new ExamViewDto(5, "MT2611231200001", "Đề hình", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 11, 23, 12, 0), "ready", 0, List.of(new PartDto("I", 1, 1, List.of(mcq))),
                "matrix");
    }

    private static String figureMarkdown() {
        Map<Long, ExamFigures.Figure> figures = Map.of(
                5L, new ExamFigures.Figure(5, "image/png", png(120, 80, Color.BLUE), 60),
                6L, new ExamFigures.Figure(6, "image/png", png(60, 60, Color.RED), 30));
        return ExamMarkdownBuilder.build(figureExam(), ExamHeader.blank("Đề hình"), ExamMarkdownBuilder.DEFAULT_SCORING,
                LocalDate.of(2026, 11, 23), ExportContent.ALL, figures);
    }

    private static int count(String s, String regex) {
        Matcher m = Pattern.compile(regex).matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static void assertFigures(byte[] docx) throws IOException {
        String xml = PandocTestSupport.documentXml(docx);
        assertEquals(3, count(xml, "<w:drawing>"), "3 lần chèn ảnh (ảnh 5 dùng hai chỗ)");
        assertEquals(2, count(xml, "<wp:docPr[^>]*descr=\"qimg:5\""));
        assertEquals(1, count(xml, "<wp:docPr[^>]*descr=\"qimg:6\""));
        assertTrue(!xml.contains("⟦img") && !xml.contains("qimg:5]"), "không có token dạng chữ");
        assertTrue(PandocTestSupport.entry(docx, "word/_rels/document.xml.rels").contains("media/"), "ảnh nằm trong word/media");
    }

    /** Các đoạn {@code <w:p>} của document.xml có chứa ảnh với {@code descr="qimg:<id>"}. */
    private static List<String> paragraphsWithImage(String xml, long id) {
        List<String> out = new java.util.ArrayList<>();
        Matcher m = Pattern.compile("<w:p[ >].*?</w:p>", Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            if (m.group().contains("descr=\"qimg:" + id + "\"")) {
                out.add(m.group());
            }
        }
        return out;
    }

    /**
     * P4 (chạy thử 24/11 bằng Word thật): ảnh đứng riêng một đoạn không được thành "figure" của pandoc, vì khi đó alt
     * {@code qimg:<id>} in ra thành dòng chú thích dưới hình và đoạn ảnh mất kiểu ExamFigure (căn giữa). {@code descr} vẫn giữ
     * để đếm lại.
     */
    private static void assertNoFigureCaption(byte[] docx) throws IOException {
        String xml = PandocTestSupport.documentXml(docx);
        assertEquals(0, count(xml, "<w:t[^>]*>[^<]*qimg:"), "không có chữ qimg: trong nội dung");
        assertEquals(0, count(xml, "w:val=\"(ImageCaption|CaptionedFigure|Caption)\""), "không có kiểu chú thích hình");
        List<String> block = paragraphsWithImage(xml, 5);
        assertEquals(2, block.size(), "ảnh 5 riêng dòng ở câu dẫn và lời giải");
        for (String p : block) {
            assertTrue(p.contains("<w:pStyle w:val=\"ExamFigure\"/>"), "đoạn ảnh khối có kiểu ExamFigure: " + p);
        }
        List<String> option = paragraphsWithImage(xml, 6);
        assertEquals(1, option.size());
        assertTrue(option.get(0).contains("<w:pStyle w:val=\"ExamOption\"/>"), "ảnh phương án nằm trong đoạn phương án");
        assertEquals(2, count(xml, "<wp:docPr[^>]*descr=\"qimg:5\""));
        assertEquals(1, count(xml, "<wp:docPr[^>]*descr=\"qimg:6\""));
    }

    @Test
    void anhRiengDongKhongThanhFigureKhongChuThichQimgVaGiuKieuExamFigure() throws IOException {
        for (EquationFormat format : EquationFormat.values()) {
            PandocRunner.Docx docx = runner.markdownToDocx(figureMarkdown(), format, ExamDocxOptions.DEFAULT);
            assertNoFigureCaption(docx.content());
        }
    }

    /**
     * Đề có ảnh ở cả 4 phương án (file Word nhập ở lần chạy thử không có câu như vậy): bố cục 2 cột, mỗi dòng 2 ảnh. Ghi
     * docx ra {@code target/figure-docx/} để mở bằng Word thật khi kiểm tay (không ảnh hưởng kết quả test).
     */
    @Test
    void anhOCaBonPhuongAnXep2CotVaGhiFileDeKiemBangWord() throws IOException {
        ExamItemDto mcq = new ExamItemDto(1, 1, 1, "I", "Tọa độ", QuestionType.MCQ, "ready", "bank", 78, 102,
                "Đồ thị nào dưới đây là đồ thị của hàm số $y = 2x$?\n⟦img:5⟧", "⟦img:11⟧", "⟦img:12⟧", "⟦img:13⟧",
                "⟦img:14⟧", Option.B, null, null, "Đồ thị đi qua $O$ và điểm $(1; 2)$.", Difficulty.EASY, false, List.of());
        ExamViewDto view = new ExamViewDto(7, "MT2611241200001", "Đề ảnh phương án", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 11, 24, 8, 0), "ready", 0, List.of(new PartDto("I", 1, 1, List.of(mcq))), "matrix");
        Map<Long, ExamFigures.Figure> figures = Map.of(
                5L, new ExamFigures.Figure(5, "image/png", png(240, 160, Color.BLUE), 60),
                11L, new ExamFigures.Figure(11, "image/png", png(200, 200, Color.RED), 50),
                12L, new ExamFigures.Figure(12, "image/png", png(200, 200, Color.GREEN), 50),
                13L, new ExamFigures.Figure(13, "image/png", png(200, 200, Color.MAGENTA), 50),
                14L, new ExamFigures.Figure(14, "image/png", png(200, 200, Color.ORANGE), 50));
        String md = ExamMarkdownBuilder.build(view, ExamHeader.blank("Đề ảnh phương án"),
                ExamMarkdownBuilder.DEFAULT_SCORING, LocalDate.of(2026, 11, 24), ExportContent.ALL, figures);

        byte[] docx = runner.markdownToDocx(md, EquationFormat.OMML, ExamDocxOptions.DEFAULT).content();
        String xml = PandocTestSupport.documentXml(docx);
        for (long id : new long[] {11, 12, 13, 14}) {
            List<String> p = paragraphsWithImage(xml, id);
            assertEquals(1, p.size());
            assertTrue(p.get(0).contains("<w:pStyle w:val=\"ExamOption\"/>"), "ảnh phương án " + id);
        }
        assertEquals(paragraphsWithImage(xml, 11), paragraphsWithImage(xml, 12), "A và B cùng một dòng (2 cột)");
        assertEquals(paragraphsWithImage(xml, 13), paragraphsWithImage(xml, 14), "C và D cùng một dòng (2 cột)");
        assertEquals(0, count(xml, "<w:t[^>]*>[^<]*qimg:"));
        java.nio.file.Path out = java.nio.file.Path.of("target", "figure-docx", "anh-phuong-an.docx");
        java.nio.file.Files.createDirectories(out.getParent());
        java.nio.file.Files.write(out, docx);
    }

    @Test
    void anhDataUriVaoDocxEquationDuoiSandboxDescrLayTuAlt() throws IOException {
        PandocRunner.Docx docx = runner.markdownToDocx(figureMarkdown(), EquationFormat.OMML, ExamDocxOptions.DEFAULT);

        assertFigures(docx.content());
    }

    @Test
    void anhGiuNguyenSauBuocMathTypeVaKhongCongThucNaoGiuOmml() throws IOException {
        PandocRunner.Docx docx = runner.markdownToDocx(figureMarkdown(), EquationFormat.MATHTYPE,
                ExamDocxOptions.DEFAULT);

        assertEquals(0, docx.equationsKeptOmml(), "Kept-Omml = 0");
        assertTrue(docx.mathTypeObjects() > 0);
        assertFigures(docx.content());
    }

    @Test
    void pandocBoAnhMaVanExit0ThiBaoExportImageMissingKhongTraFile() {
        // ảnh dạng file: dưới --sandbox pandoc báo PandocResourceNotFound, exit 0 và docx thiếu ảnh (mục 2b)
        String md = figureMarkdown().replace("![qimg:6](data:", "![qimg:6](fig.png#data:");

        for (EquationFormat format : EquationFormat.values()) {
            DocumentExportException e = assertThrows(DocumentExportException.class,
                    () -> runner.markdownToDocx(md, format, ExamDocxOptions.DEFAULT), format.name());
            assertEquals(500, e.getHttpStatus());
            assertEquals(ExamFigures.MISSING_CODE, e.getCode());
        }
    }

    @Test
    void deKhongCoHinhKhongCoDrawingNaoTuFileMau() throws IOException {
        ExamItemDto plain = new ExamItemDto(1, 1, 1, "I", "Đa thức", QuestionType.MCQ, "ready", "generated", null, 101,
                "Tính $1 + 1$.", "$1$", "$2$", "$3$", "$4$", Option.B, null, null, "Ta có $1 + 1 = 2$.",
                Difficulty.EASY, false, List.of());
        ExamViewDto view = new ExamViewDto(6, "MT1", "Đề", "teacher", ExamStatus.DRAFT, 3, LocalDateTime.now(), "ready", 0,
                List.of(new PartDto("I", 1, 1, List.of(plain))), "matrix");
        String md = ExamMarkdownBuilder.build(view, ExamHeader.blank("Đề"));

        for (EquationFormat format : EquationFormat.values()) {
            byte[] docx = runner.markdownToDocx(md, format, ExamDocxOptions.DEFAULT).content();
            assertEquals(0, count(PandocTestSupport.documentXml(docx), "<w:drawing>"), format.name());
        }
    }

    @Test
    void demDrawingLechKyVongThiNemExportImageMissing() {
        byte[] docx = runner.markdownToDocx(figureMarkdown(), EquationFormat.OMML, ExamDocxOptions.DEFAULT).content();

        ExamFigures.verifyDocx(docx, Map.of(5L, 2, 6L, 1), "kiểm");
        assertEquals(ExamFigures.MISSING_CODE, assertThrows(DocumentExportException.class,
                () -> ExamFigures.verifyDocx(docx, Map.of(5L, 2, 6L, 2), "kiểm")).getCode());
        assertEquals(ExamFigures.MISSING_CODE, assertThrows(DocumentExportException.class,
                () -> ExamFigures.verifyDocx(docx, Map.of(5L, 2), "kiểm")).getCode(), "drawing thừa cũng là lệch");
    }
}
