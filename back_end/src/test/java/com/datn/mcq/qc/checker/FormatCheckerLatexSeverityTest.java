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
    void fatalLatexErrorStillFailsFormat() {
        FormatChecker.FormatCheckOutcome outcome =
                formatChecker.check(mathInput("Cho tam giác đều $ABC. Tính góc $B$."));
        assertFalse(outcome.passed());
        assertTrue(outcome.errors().stream().anyMatch(e -> e.contains("số dấu $ bị lẻ")), () -> outcome.errors().toString());
    }
}
