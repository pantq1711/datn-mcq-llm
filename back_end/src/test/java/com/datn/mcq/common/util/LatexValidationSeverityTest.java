package com.datn.mcq.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phân loại mức độ của kết quả kiểm tra LaTeX (quyết định 6, lượt 2026-10-21):
 * ký tự điều khiển, số dấu $ lẻ, ngoặc không cân bằng, lệnh nằm ngoài $...$ là LỖI (fatal);
 * lệnh nằm ngoài danh sách cho phép chỉ là CẢNH BÁO, không làm câu bị format_failed.
 */
class LatexValidationSeverityTest {

    private static boolean containsPart(List<String> messages, String part) {
        return messages.stream().anyMatch(m -> m.contains(part));
    }

    @Test
    void commandOutsideAllowlistIsOnlyAWarning() {
        LatexValidationRules.Result result = LatexValidationRules.check("Cho $\\widehat{ABC} = 60^\\circ$");
        assertTrue(result.errors().isEmpty(), () -> "Không được là lỗi: " + result.errors());
        assertTrue(containsPart(result.warnings(), "\\widehat"), () -> "Phải có cảnh báo: " + result.warnings());
        assertFalse(result.hasErrors());
    }

    @Test
    void misspelledCommandWarningKeepsSuggestion() {
        LatexValidationRules.Result result = LatexValidationRules.check("$\\fracc{1}{2}$");
        assertTrue(result.errors().isEmpty(), () -> "Không được là lỗi: " + result.errors());
        assertTrue(containsPart(result.warnings(), "gợi ý: \\frac"), () -> "Thiếu gợi ý: " + result.warnings());
    }

    @Test
    void controlCharacterStaysFatal() {
        LatexValidationRules.Result result = LatexValidationRules.check("Rút gọn $\f" + "rac{1}{2}$");
        assertTrue(result.hasErrors());
        assertTrue(containsPart(result.errors(), "ký tự điều khiển"), () -> result.errors().toString());
        assertFalse(containsPart(result.warnings(), "ký tự điều khiển"));
    }

    @Test
    void oddDollarCountStaysFatal() {
        LatexValidationRules.Result result = LatexValidationRules.check("Giải phương trình $2x + 3 = 0");
        assertTrue(containsPart(result.errors(), "số dấu $ bị lẻ"), () -> result.errors().toString());
    }

    @Test
    void unbalancedBracesStayFatal() {
        LatexValidationRules.Result result = LatexValidationRules.check("Tính $\\frac{x + 1{x - 1}$");
        assertTrue(containsPart(result.errors(), "ngoặc { } không cân bằng"), () -> result.errors().toString());
    }

    @Test
    void commandOutsideMathStaysFatal() {
        LatexValidationRules.Result result = LatexValidationRules.check("A = \\frac{1}{x}");
        assertTrue(containsPart(result.errors(), "nằm ngoài cặp $...$"), () -> result.errors().toString());
    }

    @Test
    void fatalErrorAndWarningAreReportedSeparatelyInTheSameText() {
        LatexValidationRules.Result result = LatexValidationRules.check("Cho $\\alpha + 1$ và $x + 2");
        assertTrue(containsPart(result.errors(), "số dấu $ bị lẻ"), () -> result.errors().toString());
        assertFalse(containsPart(result.errors(), "\\alpha"), () -> result.errors().toString());
        assertTrue(containsPart(result.warnings(), "\\alpha"), () -> result.warnings().toString());
    }

    @Test
    void validTextHasNeitherErrorsNorWarnings() {
        LatexValidationRules.Result result = LatexValidationRules.check("Rút gọn $\\frac{4x^2y}{8xy^3}$ với $x \\neq 0$");
        assertTrue(result.errors().isEmpty(), () -> result.errors().toString());
        assertTrue(result.warnings().isEmpty(), () -> result.warnings().toString());
    }

    @Test
    void legacyValidateStillListsEveryFindingIncludingWarnings() {
        // validate() giữ hành vi cũ (liệt kê mọi phát hiện) để không phải sửa test đã duyệt;
        // nơi quyết định format_failed phải dùng check().errors().
        List<String> all = LatexValidationRules.validate("Cho $\\alpha + 1$ và $x + 2");
        assertTrue(containsPart(all, "số dấu $ bị lẻ"));
        assertTrue(containsPart(all, "\\alpha"));
    }
}
