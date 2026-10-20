package com.datn.mcq.generation.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatexFormatValidatorTest {

    @Test
    void unescapedNeqInJsonDecodedAsNewlineInsideMathShouldBeReportedAsError() {
        String decodedFromJson = "Điều kiện xác định: $x \neq 0$";
        List<String> errors = LatexFormatValidator.validate(decodedFromJson);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("xuống dòng bên trong $...$")));
    }

    @Test
    void newlineOutsideMathShouldBeValid() {
        String validExplanation = "Bước 1: Điều kiện $x \\neq 0$.\nBước 2: Rút gọn $\\frac{x^2}{x} = x$.";
        List<String> errors = LatexFormatValidator.validate(validExplanation);
        assertTrue(errors.isEmpty(), () -> "Không được báo lỗi khi \\n nằm ngoài $...$, nhưng gặp: " + errors);
    }

    @Test
    void latexCommandOutsideMathShouldBeReportedAsError() {
        List<String> errors = LatexFormatValidator.validate("A = \\frac{1}{x}");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("lệnh LaTeX nằm ngoài cặp $...$")));
    }

    @Test
    void escapedDollarOutsideMathShouldBeValid() {
        List<String> errors = LatexFormatValidator.validate("Giá 5 \\$ và biểu thức $\\frac{1}{x}$");
        assertTrue(errors.isEmpty(), () -> "Dấu \\$ ngoài công thức phải hợp lệ, nhưng gặp: " + errors);
    }

    @Test
    void controlCharactersFormFeedOrTabShouldBeReportedAsError() {
        List<String> formFeedErrors = LatexFormatValidator.validate("Rút gọn $\f" + "rac{1}{2}$");
        assertFalse(formFeedErrors.isEmpty());
        assertTrue(formFeedErrors.stream().anyMatch(e -> e.contains("ký tự điều khiển")));

        List<String> tabErrors = LatexFormatValidator.validate("Biểu thức $\t" + "imes x$");
        assertFalse(tabErrors.isEmpty());
        assertTrue(tabErrors.stream().anyMatch(e -> e.contains("ký tự điều khiển")));
    }

    @Test
    void oddDollarCountShouldBeReportedAsError() {
        List<String> errors = LatexFormatValidator.validate("Giải phương trình $2x + 3 = 0");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("số dấu $ bị lẻ")));
    }

    @Test
    void unbalancedBracesInsideMathShouldBeReportedAsError() {
        List<String> unclosedErrors = LatexFormatValidator.validate("Tính $\\frac{x + 1{x - 1}$");
        assertFalse(unclosedErrors.isEmpty());
        assertTrue(unclosedErrors.stream().anyMatch(e -> e.contains("ngoặc { } không cân bằng")));

        List<String> extraCloseErrors = LatexFormatValidator.validate("Tính $\\frac{1}{2}}$");
        assertFalse(extraCloseErrors.isEmpty());
        assertTrue(extraCloseErrors.stream().anyMatch(e -> e.contains("dấu } thừa")));
    }

    @Test
    void blankOrNullTextShouldBeReportedAsError() {
        assertTrue(LatexFormatValidator.validate(null).stream().anyMatch(e -> e.contains("Nội dung trống")));
        assertTrue(LatexFormatValidator.validate("   ").stream().anyMatch(e -> e.contains("Nội dung trống")));
    }
}
