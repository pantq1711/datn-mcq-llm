package com.datn.mcq.exam.export;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.DistractorCheck;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamViewDtos.DistractorErrorDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xuất Word (Markdown): "Phân tích phương án nhiễu" sau lời giải câu mcq (13/11, PA1, thiết kế mục 7). Chỉ lỗi khớp hoặc chưa
 * kiểm; chỉ ở phần lời giải chi tiết; tắt được bằng tham số.
 */
class ExamDistractorNotesTest {

    private static final ExamScoringProperties SCORING = new ExamScoringProperties(new BigDecimal("0.25"),
            new BigDecimal("0.5"), List.of(BigDecimal.ZERO, new BigDecimal("0.1"), new BigDecimal("0.25"),
            new BigDecimal("0.5"), new BigDecimal("1.0")));
    private static final ExamHeader HEADER = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    static Map<String, DistractorErrorDto> errors() {
        Map<String, DistractorErrorDto> m = new LinkedHashMap<>();
        m.put("A", new DistractorErrorDto("Nhầm $(a+b)^2 = a^2 + b^2$", DistractorCheck.MATCH));
        m.put("C", new DistractorErrorDto("Sai dấu hạng tử giữa (KHÔNG IN)", DistractorCheck.MISMATCH));
        m.put("D", new DistractorErrorDto("Quên nhân đôi tích.", DistractorCheck.UNCHECKED));
        return m;
    }

    static ExamItemDto mcq(Map<String, DistractorErrorDto> errors) {
        return new ExamItemDto(1, 1, 1, "I", "Hằng đẳng thức", QuestionType.MCQ, "ready", "generated", null, 101,
                "Khai triển $(x+1)^2$ được", "$x^2 + 1$", "$x^2 + 2x + 1$", "$x^2 - 2x + 1$", "$x^2 + x + 1$", Option.B,
                null, null, "Dùng hằng đẳng thức.", Difficulty.EASY, false, List.of()).withDistractorErrors(errors);
    }

    static ExamViewDto view(ExamItemDto item) {
        return new ExamViewDto(5, "MT2611131200001", "Đề giữa kì I", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 11, 13, 12, 0), "ready", 0, List.of(new PartDto("I", 1, 1, List.of(item))),
                "matrix");
    }

    @Test
    void coDongPhanTichSauLoiGiaiChiLoiKhopHoacChuaKiem() {
        String md = ExamMarkdownBuilder.build(view(mcq(errors())), HEADER, SCORING, TODAY);
        String expected = "**Câu 1. Chọn B.**\n\nDùng hằng đẳng thức.\n\n"
                + "*Phân tích phương án nhiễu:* A – Nhầm $(a+b)^2 = a^2 + b^2$; D – Quên nhân đôi tích.";
        assertTrue(md.contains(expected), md);
        assertFalse(md.contains("KHÔNG IN"), "không in mismatch");
        String exam = md.substring(0, md.indexOf(ExamMarkdownBuilder.ANSWER_TITLE));
        assertFalse(exam.contains(ExamMarkdownBuilder.DISTRACTOR_NOTES_LABEL), "không lọt vào phần đề");
        String answers = md.substring(md.indexOf(ExamMarkdownBuilder.ANSWER_TITLE), md.indexOf(ExamMarkdownBuilder.SOLUTION_TITLE));
        assertFalse(answers.contains(ExamMarkdownBuilder.DISTRACTOR_NOTES_LABEL), "bảng đáp án không đổi");
        assertEquals(1, md.split(ExamMarkdownBuilder.DISTRACTOR_NOTES_LABEL, -1).length - 1);
    }

    @Test
    void tatBangThamSoThiGiongHetCauKhongCoLoi() {
        String off = ExamMarkdownBuilder.build(view(mcq(errors())), HEADER, SCORING, TODAY, false);
        assertFalse(off.contains(ExamMarkdownBuilder.DISTRACTOR_NOTES_LABEL));
        assertEquals(ExamMarkdownBuilder.build(view(mcq(null)), HEADER, SCORING, TODAY), off,
                "tắt thì file y như câu không có lỗi");
    }

    @Test
    void khongConLoiNaoDeInThiKhongCoDong() {
        Map<String, DistractorErrorDto> m = new LinkedHashMap<>();
        m.put("A", new DistractorErrorDto("x", DistractorCheck.MISMATCH));
        m.put("C", new DistractorErrorDto("y", DistractorCheck.UNKNOWN));
        assertNull(ExamMarkdownBuilder.distractorNotes(mcq(m)));
        assertFalse(ExamMarkdownBuilder.build(view(mcq(m)), HEADER, SCORING, TODAY)
                .contains(ExamMarkdownBuilder.DISTRACTOR_NOTES_LABEL));
    }
}
