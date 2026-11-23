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
