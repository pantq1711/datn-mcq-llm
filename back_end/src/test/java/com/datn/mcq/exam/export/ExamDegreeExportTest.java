package com.datn.mcq.exam.export;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ký hiệu độ trong file xuất: mọi cách viết trong công thức ("^{\circ}", "^\circ", "^{o}", "°") phải ra cùng một dạng, "°"
 * sát số cỡ thường, không phải chỉ số trên cỡ nhỏ tách khỏi số. Phần Markdown không cần pandoc; phần docx chỉ chạy khi máy
 * có pandoc.
 */
class ExamDegreeExportTest {

    private static final ExamScoringProperties SCORING = new ExamScoringProperties(new BigDecimal("0.25"),
            new BigDecimal("0.5"), List.of(BigDecimal.ZERO, new BigDecimal("0.1"), new BigDecimal("0.25"),
            new BigDecimal("0.5"), new BigDecimal("1.0")));
    private static final ExamHeader HEADER = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    private static ExamViewDto view() {
        ExamItemDto item = new ExamItemDto(1, 1, 1, "I", "Tứ giác", QuestionType.MCQ, "ready", "generated", null, 101,
                "Cho $\\widehat{A} = 70^{\\circ}$, $\\widehat{B} = 80^\\circ$. Tính $\\widehat{C}$.",
                "$90^{\\circ}$", "$110^\\circ$", "$120^{o}$", "$130°$", Option.B, null, null,
                "Tổng bốn góc là $360^{\\circ}$, nên $\\widehat{C} = 360^\\circ - 250° = 110^{o}$.", Difficulty.EASY,
                false, List.of());
        return new ExamViewDto(5, "MT2611081200001", "Đề thử", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 11, 8, 12, 0), "ready", 0, List.of(new PartDto("I", 1, 1, List.of(item))), "matrix");
    }

    private static String markdown() {
        return ExamMarkdownBuilder.build(view(), HEADER, SCORING, TODAY);
    }

    @Test
    void everySpellingReachesPandocAsTheSameDegreeSign() {
        String md = markdown();

        assertFalse(md.contains("\\circ"), md);
        assertFalse(md.contains("^{o}") || md.contains("^o"), md);
        assertTrue(md.contains("$90°$") && md.contains("$110°$") && md.contains("$120°$") && md.contains("$130°$"), md);
        assertTrue(md.contains("$\\widehat{A} = 70°$") && md.contains("$\\widehat{B} = 80°$"), md);
        assertTrue(md.contains("$\\widehat{C} = 360° - 250° = 110°$"), md);
    }

    @Test
    void equationExportHasNoSuperscriptRing() throws Exception {
        Optional<String> pandoc = PandocTestSupport.find();
        assumeTrue(pandoc.isPresent(), "máy không có pandoc: bỏ qua");
        PandocRunner.Docx omml = new PandocRunner(pandoc.get()).markdownToDocx(markdown(), EquationFormat.OMML);
        String xml = PandocTestSupport.documentXml(omml.content());

        assertFalse(xml.contains("∘"), "còn vòng tròn chỉ số trên (U+2218)");
        assertFalse(xml.contains("<m:sSup>"), "còn chỉ số trên trong công thức độ");
        assertEquals(10, xml.split("°", -1).length - 1, "mỗi ký hiệu độ là một ký tự ° trong công thức");
    }

    @Test
    void mathTypeExportConvertsEveryFormula() throws Exception {
        Optional<String> pandoc = PandocTestSupport.find();
        assumeTrue(pandoc.isPresent(), "máy không có pandoc: bỏ qua");
        PandocRunner.Docx mathType = new PandocRunner(pandoc.get()).markdownToDocx(markdown(), EquationFormat.MATHTYPE);

        assertEquals(0, mathType.equationsKeptOmml(), "không công thức nào phải giữ dạng Equation");
        assertEquals(0, mathType.equationsScaled());
        assertTrue(mathType.mathTypeObjects() >= 8, "số đối tượng MathType: " + mathType.mathTypeObjects());
    }
}
