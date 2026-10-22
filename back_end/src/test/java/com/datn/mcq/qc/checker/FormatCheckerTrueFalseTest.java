package com.datn.mcq.qc.checker;

import com.datn.mcq.qc.dto.QuestionQcInput;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FormatChecker cho câu Đúng/Sai (GĐ3): đề dẫn, 4 ý, lời giải không trống; 4 nhãn T/F; 4 ý khác nhau sau chuẩn hóa;
 * LaTeX ở đề dẫn và ý là lỗi chặn, ở lời giải chỉ cảnh báo; 4 nhãn giống nhau chỉ cảnh báo trong answerWarnings.
 */
class FormatCheckerTrueFalseTest {

    private static final String STEM = "Cho biểu thức $P = \\frac{x^2 - 1}{x^2 - x}$.";
    private static final List<String> STATEMENTS = List.of(
            "Điều kiện xác định của $P$ là $x \\neq 0$ và $x \\neq 1$.",
            "Với điều kiện xác định, $P = \\frac{x+1}{x}$.",
            "Tại $x = 2$ thì $P = 2$.",
            "Có đúng hai giá trị nguyên của $x$ để $P$ nhận giá trị nguyên.");
    private static final List<String> LABELS = List.of("T", "T", "F", "F");

    private final FormatChecker checker = new FormatChecker();

    private static QuestionQcInput tf(String stem, List<String> statements, List<String> labels, String explanation) {
        return QuestionQcInput.trueFalse(1, true, stem, statements, labels, explanation, true, false, null);
    }

    @Test
    void cauHopLe() {
        FormatChecker.FormatCheckOutcome out = checker.check(tf(STEM, STATEMENTS, LABELS, "Lời giải từng ý."));
        assertTrue(out.passed(), out.errors().toString());
        assertEquals(List.of(), out.answerWarnings());
    }

    @Test
    void nhanThuongVanHopLeNhanLaBiChan() {
        assertTrue(checker.check(tf(STEM, STATEMENTS, List.of("t", " f ", "T", "F"), "E")).passed());
        FormatChecker.FormatCheckOutcome out = checker.check(tf(STEM, STATEMENTS, List.of("Đúng", "true", "T", "F"), "E"));
        assertFalse(out.passed());
        assertEquals(List.of("label_a: phải là T hoặc F", "label_b: phải là T hoặc F"), out.errors());
    }

    @Test
    void truongTrong() {
        FormatChecker.FormatCheckOutcome out = checker.check(tf(" ", Arrays.asList("a", " ", null, "d"),
                Arrays.asList("T", null, "F", ""), ""));
        assertFalse(out.passed());
        assertEquals(List.of("question_text: nội dung trống", "statement_b: nội dung trống",
                "statement_c: nội dung trống", "explanation: nội dung trống", "label_b: nội dung trống",
                "label_d: nội dung trống"), out.errors());
    }

    @Test
    void thieuYHoacThieuNhan() {
        FormatChecker.FormatCheckOutcome out = checker.check(tf(STEM, List.of("a", "b"), List.of("T"), "E"));
        assertFalse(out.passed());
        assertTrue(out.errors().contains("statement_c: nội dung trống"), out.errors().toString());
        assertTrue(out.errors().contains("label_d: nội dung trống"), out.errors().toString());
    }

    @Test
    void bonYPhaiKhacNhauSauChuanHoa() {
        FormatChecker.FormatCheckOutcome out = checker.check(tf(STEM,
                List.of("Tại $x = 2$ thì $P = 2$.", "Ý b", "  Tại $x = 2$ thì $P = 2$.  ", "Ý d"), LABELS, "E"));
        assertFalse(out.passed());
        assertEquals(List.of("4 ý a, b, c, d có nội dung trùng lặp nhau"), out.errors());
    }

    @Test
    void latexODeDanVaYLaLoiChanOLoiGiaiChiCanhBao() {
        FormatChecker.FormatCheckOutcome badStem = checker.check(tf("Cho $\\frac{1}{2$.", STATEMENTS, LABELS, "E"));
        assertFalse(badStem.passed());
        assertTrue(badStem.errors().stream().anyMatch(e -> e.startsWith("question_text: ")));

        FormatChecker.FormatCheckOutcome badStatement = checker.check(tf(STEM,
                List.of(STATEMENTS.get(0), "Ý có \\frac{1}{2} ngoài công thức", STATEMENTS.get(2), STATEMENTS.get(3)),
                LABELS, "E"));
        assertFalse(badStatement.passed());
        assertTrue(badStatement.errors().stream().anyMatch(e -> e.startsWith("statement_b: ")),
                badStatement.errors().toString());

        FormatChecker.FormatCheckOutcome badExplanation = checker.check(tf(STEM, STATEMENTS, LABELS, "Giải $\\frac{1}{2$"));
        assertTrue(badExplanation.passed(), badExplanation.errors().toString());
        assertTrue(badExplanation.warnings().stream().anyMatch(w -> w.startsWith("explanation: ")));
    }

    @Test
    void bonNhanGiongNhauChiCanhBao() {
        FormatChecker.FormatCheckOutcome allTrue = checker.check(tf(STEM, STATEMENTS, List.of("T", "t", "T", "T"), "E"));
        assertTrue(allTrue.passed());
        assertEquals(List.of("label: 4 ý cùng nhãn T"), allTrue.answerWarnings());
        FormatChecker.FormatCheckOutcome allFalse = checker.check(tf(STEM, STATEMENTS, List.of("F", "F", "F", "F"), "E"));
        assertEquals(List.of("label: 4 ý cùng nhãn F"), allFalse.answerWarnings());
    }

    @Test
    void giaiMaLoi() {
        FormatChecker.FormatCheckOutcome out = checker.check(QuestionQcInput.trueFalse(1, false, null, List.of(),
                List.of(), null, true, false, "Khóa trùng: \"label_c\""));
        assertFalse(out.passed());
        assertEquals(List.of("Không giải mã được JSON hợp lệ từ phản hồi LLM: Khóa trùng: \"label_c\""), out.errors());
    }
}
