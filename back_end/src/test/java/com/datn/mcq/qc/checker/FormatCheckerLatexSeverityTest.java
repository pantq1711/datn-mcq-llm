package com.datn.mcq.qc.checker;

import com.datn.mcq.enums.Option;
import com.datn.mcq.qc.dto.QuestionQcInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cảnh báo LaTeX (lệnh ngoài danh sách cho phép) không được làm câu thành format_failed;
 * lỗi fatal vẫn làm format check thất bại.
 */
class FormatCheckerLatexSeverityTest {

    private final FormatChecker formatChecker = new FormatChecker();

    private static QuestionQcInput mathInput(String questionText) {
        return new QuestionQcInput(
                1,
                true,
                questionText,
                "$30^\\circ$",
                "$45^\\circ$",
                "$60^\\circ$",
                "$90^\\circ$",
                Option.C,
                "Tam giác đều có mỗi góc bằng $60^\\circ$.",
                true,
                false
        );
    }

    @Test
    void commandOutsideAllowlistPassesFormatWithWarning() {
        FormatChecker.FormatCheckOutcome outcome =
                formatChecker.check(mathInput("Cho tam giác đều $ABC$. Tính $\\widehat{ABC}$."));
        assertTrue(outcome.passed(), () -> "Cảnh báo không được làm format_failed: " + outcome.errors());
        assertTrue(outcome.errors().isEmpty(), () -> outcome.errors().toString());
        assertTrue(outcome.warnings().stream().anyMatch(w -> w.startsWith("question_text:") && w.contains("\\widehat")),
                () -> outcome.warnings().toString());
    }

    @Test
    void balancedUnsupportedEnvironmentPassesFormatWithWarning() {
        FormatChecker.FormatCheckOutcome outcome = formatChecker.check(
                mathInput("Cho $\\begin{array}{cc} 1 & 2 \\\\ 3 & 4 \\end{array}$. Chọn đáp án:"));
        assertTrue(outcome.passed(), () -> "Môi trường lạ nhưng cân bằng không được làm format_failed: " + outcome.errors());
        assertTrue(outcome.warnings().stream().anyMatch(w -> w.contains("array")), () -> outcome.warnings().toString());
    }

    // Việc 2 (22/10): lỗi LaTeX trong explanation chỉ là cảnh báo; đề và 4 phương án vẫn là lỗi nghiêm trọng.
    @Test
    void fatalLatexErrorInExplanationIsOnlyAWarning() {
        QuestionQcInput input = new QuestionQcInput(1, true, "Cho tam giác đều $ABC$. Tính góc $B$.",
                "$30^\\circ$", "$45^\\circ$", "$60^\\circ$", "$90^\\circ$", Option.C,
                "Lời giải hỏng $x = 2", true, false);
        FormatChecker.FormatCheckOutcome outcome = formatChecker.check(input);
        assertTrue(outcome.passed(), () -> "Lỗi ở lời giải không được làm format_failed: " + outcome.errors());
        assertTrue(outcome.warnings().stream().anyMatch(w -> w.startsWith("explanation:") && w.contains("số dấu $ bị lẻ")),
                () -> outcome.warnings().toString());
    }

    @Test
    void fatalLatexErrorInAnOptionStillFailsFormat() {
        QuestionQcInput input = new QuestionQcInput(1, true, "Cho tam giác đều $ABC$. Tính góc $B$.",
                "$30^\\circ", "$45^\\circ$", "$60^\\circ$", "$90^\\circ$", Option.C,
                "Tam giác đều có mỗi góc bằng $60^\\circ$.", true, false);
        FormatChecker.FormatCheckOutcome outcome = formatChecker.check(input);
        assertFalse(outcome.passed());
        assertTrue(outcome.errors().stream().anyMatch(e -> e.startsWith("option_a:")), () -> outcome.errors().toString());
    }

    @Test
    void fatalLatexErrorStillFailsFormat() {
        FormatChecker.FormatCheckOutcome outcome =
                formatChecker.check(mathInput("Cho tam giác đều $ABC. Tính góc $B$."));
        assertFalse(outcome.passed());
        assertTrue(outcome.errors().stream().anyMatch(e -> e.contains("số dấu $ bị lẻ")), () -> outcome.errors().toString());
    }
}
