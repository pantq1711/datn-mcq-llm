package com.datn.mcq.exam.export;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Câu đã lưu từ trước có thể còn "\n" chữ trong công thức: file xuất (Equation lẫn MathType cùng đi qua
 * {@link ExamMarkdownBuilder#build}) phải được sửa để pandoc đọc đủ công thức. Không cần pandoc.
 */
class ExamLiteralNewlinesExportTest {

    private static final ExamScoringProperties SCORING = new ExamScoringProperties(new BigDecimal("0.25"),
            new BigDecimal("0.5"), List.of(BigDecimal.ZERO, new BigDecimal("0.1"), new BigDecimal("0.25"),
            new BigDecimal("0.5"), new BigDecimal("1.0")));
    private static final ExamHeader HEADER = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    /** Lời giải thật của đề 19, Phần III câu 4 (xem LiteralNewlinesTest). */
    private static final String REAL_EXPLANATION =
            "Tổng các góc trong tứ giác $EFGH$ bằng $360^{\\circ}$.\n$\\widehat{E} + \\widehat{F} + \\widehat{G} + "
                    + "\\widehat{H} = 360^{\\circ}$ $.\\n$ $190^{\\circ} + 2\\widehat{H} + \\widehat{H} = 360^{\\circ}$ $.\\n$ "
                    + "$3\\widehat{H} = 360^{\\circ} - 190^{\\circ}$ $.\\n$ $3\\widehat{H} = 170^{\\circ}$ $.\\n\\widehat{H} = "
                    + "\\frac{170^{\\circ}}{3}$.";

    private static ExamViewDto view(String stem, String optionA, String explanation) {
        ExamItemDto item = new ExamItemDto(1, 1, 1, "I", "Tứ giác", QuestionType.MCQ, "ready", "generated", null, 101,
                stem, optionA, "$2$", "$3$", "$4$", com.datn.mcq.enums.Option.B, null, null, explanation, Difficulty.EASY,
                false, List.of());
        return new ExamViewDto(5, "MT2611061200001", "Đề thử", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 11, 6, 12, 0), "ready", 0, List.of(new PartDto("I", 1, 1, List.of(item))), "matrix");
    }

    @Test
    void storedLiteralNewlinesInsideFormulasAreFixedInTheExportedMarkdown() {
        String md = ExamMarkdownBuilder.build(view("Cho $x\\n+1 = 2$. Tìm $x$.", "$1\\n$", REAL_EXPLANATION), HEADER,
                SCORING, TODAY);

        assertFalse(md.contains("\\n"), "còn \\n chữ trong file xuất");
        assertFalse(md.contains("\\\\n"), "còn \\n chữ (đã thoát) trong file xuất");
        assertTrue(md.contains("$x +1 = 2$"), md);
        assertTrue(md.contains("\\widehat{H} = \\frac{170°}{3}"), md);
    }

    @Test
    void commandsStartingWithNSurviveExport() {
        String md = ExamMarkdownBuilder.build(view("Cho $x \\neq 0$ và $y \\notin A$.", "$\\nabla f$", "Vì $x \\neq 0$."),
                HEADER, SCORING, TODAY);

        assertTrue(md.contains("\\neq"), md);
        assertTrue(md.contains("\\notin"), md);
        assertTrue(md.contains("\\nabla"), md);
    }
}
