package com.datn.mcq.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm tra cú pháp bề mặt của công thức LaTeX (phạm vi toán THCS).
 * Các test này KHÔNG bảo đảm KaTeX render được công thức; chúng chỉ kiểm tra các quy ước của đồ án.
 */
class LatexValidationRulesTest {

    private static void assertValid(String text) {
        List<String> errors = LatexValidationRules.validate(text);
        assertTrue(errors.isEmpty(), () -> "Phải hợp lệ nhưng gặp: " + errors + " với: " + text);
    }

    private static void assertError(String text, String expectedPart) {
        List<String> errors = LatexValidationRules.validate(text);
        assertFalse(errors.isEmpty(), () -> "Phải báo lỗi cho: " + text);
        assertTrue(errors.stream().anyMatch(e -> e.contains(expectedPart)),
                () -> "Phải có lỗi chứa \"" + expectedPart + "\" nhưng gặp: " + errors + " với: " + text);
    }

    // ---------- Công thức rỗng ----------

    @Test
    void emptyFormulaShouldBeReported() {
        assertError("Tính $ $ ra", "công thức rỗng");
        assertError("Tính $$   $$ ra", "công thức rỗng");
        assertFalse(LatexValidationRules.validate("Tính $$ ra").isEmpty());
    }

    // ---------- \frac ----------

    @Test
    void fracWithMissingOrEmptyArgumentsShouldBeReported() {
        assertError("$\\frac{1}$", "\\frac thiếu đối số");
        assertError("$\\frac$", "\\frac thiếu đối số");
        assertError("$\\frac1$", "\\frac thiếu đối số");
        assertError("$\\frac{}{2}$", "\\frac có đối số rỗng");
        assertError("$\\frac{1}{ }$", "\\frac có đối số rỗng");
    }

    @Test
    void fracWithSingleTokenArgumentsIsValidTexSyntax() {
        assertValid("$\\frac12$");
        assertValid("$\\frac1 2$");
        assertValid("$\\frac{1}2$");
        assertValid("$\\frac x{y}$");
        assertValid("$\\frac{\\sqrt2}{3}$");
        assertValid("$\\frac{x^2}{x} = x$");
    }

    // ---------- \sqrt ----------

    @Test
    void sqrtWithMissingOrEmptyArgumentShouldBeReported() {
        assertError("$\\sqrt$", "\\sqrt thiếu đối số");
        assertError("$\\sqrt[3]$", "\\sqrt thiếu đối số");
        assertError("$\\sqrt{}$", "\\sqrt có đối số rỗng");
    }

    @Test
    void validSqrtShouldBeKept() {
        assertValid("$\\sqrt{16} = 4$");
        assertValid("$\\sqrt[3]{8} = 2$");
        assertValid("$\\sqrt2$");
    }

    // ---------- Lệnh sai chính tả / ngoài allowlist ----------

    @Test
    void misspelledCommandShouldBeReportedWithSuggestion() {
        assertError("$\\fracc{1}{2}$", "không hỗ trợ hoặc có thể sai chính tả");
        assertError("$\\fracc{1}{2}$", "gợi ý: \\frac");
    }

    @Test
    void unknownCommandFarFromAllowlistHasNoSuggestion() {
        List<String> errors = LatexValidationRules.validate("$\\foobarbaz{1}$");
        assertTrue(errors.stream().anyMatch(e -> e.contains("không hỗ trợ hoặc có thể sai chính tả")), () -> errors.toString());
        assertTrue(errors.stream().noneMatch(e -> e.contains("gợi ý")), () -> errors.toString());
    }

    @Test
    void commandsOfJuniorHighSchoolMathAreAccepted() {
        assertValid("$x \\le 2$ và $x \\ge 1$ và $x \\leq 2$ và $x \\geq 1$ và $x \\neq 0$");
        assertValid("$a \\pm b$ và $a \\cdot b$ và $a \\times b$ và $a \\div b$");
        assertValid("$1, 2, \\dots, n$ và $\\pi$");
        assertValid("$A \\Rightarrow B$ và $A \\Leftrightarrow B$");
    }

    // ---------- \left / \right ----------

    @Test
    void unpairedLeftOrRightShouldBeReported() {
        assertError("$\\left( x$", "\\left không có \\right tương ứng");
        assertError("$x \\right)$", "\\right không có \\left tương ứng");
        assertError("$\\left x \\right)$", "dấu phân cách");
    }

    @Test
    void pairedLeftRightShouldBeValid() {
        assertValid("$\\left(\\frac{1}{2}\\right)^2$");
        assertValid("$\\left[ x \\right]$");
        assertValid("$\\left| x \\right|$");
        assertValid("$\\left\\{ x \\right\\}$");
    }

    @Test
    void rightDotIsAnInvisibleClosingDelimiter() {
        assertValid("$\\left\\{ x = 1 \\right.$");
    }

    // ---------- môi trường begin/end ----------

    @Test
    void systemOfEquationsWithRightDotShouldBeValid() {
        assertValid("$\\left\\{\\begin{matrix} 2x + y = 5 \\\\ x - y = 1 \\end{matrix}\\right.$");
    }

    @Test
    void systemOfEquationsWithoutRightShouldBeReported() {
        assertError("$\\left\\{\\begin{matrix} 2x + y = 5 \\\\ x - y = 1 \\end{matrix}$",
                "\\left không có \\right tương ứng");
    }

    @Test
    void supportedEnvironmentsShouldBeValid() {
        assertValid("$\\begin{cases} x = 1 \\\\ y = 2 \\end{cases}$");
        assertValid("$\\begin{aligned} x &= 1 \\\\ y &= 2 \\end{aligned}$");
    }

    @Test
    void unpairedOrMismatchedEnvironmentShouldBeReported() {
        assertError("$\\begin{matrix} a$", "\\begin{matrix} thiếu \\end{matrix}");
        assertError("$a \\end{matrix}$", "\\end{matrix} không có \\begin tương ứng");
        assertError("$\\begin{cases} a \\end{aligned}$", "\\end{aligned} không có \\begin tương ứng");
    }

    @Test
    void unsupportedEnvironmentShouldBeReported() {
        assertError("$\\begin{array} a \\end{array}$", "môi trường array không hỗ trợ");
    }

    // ---------- $ và $$ , xuống dòng ----------

    @Test
    void displayMathAndInlineMathShouldBeDistinguished() {
        assertValid("$$\\frac{1}{2}$$ và $x$");
        assertValid("$a$ và $b$");
        assertError("$$x + 1$ ra", "số dấu $ bị lẻ");
    }

    @Test
    void multilineFormulaIsAllowedInDisplayMathAndEnvironments() {
        assertValid("$$x\n= 1$$");
        assertValid("$\\begin{aligned}\nx &= 1 \\\\\ny &= 2\n\\end{aligned}$");
    }

    @Test
    void newlineInsideInlineMathIsStillReported() {
        assertError("Điều kiện $x \neq 0$", "xuống dòng bên trong $...$");
    }

    // ---------- Lỗi thoát JSON: \n \r \t \b \f bị giải mã thành ký tự điều khiển ----------

    private static final String LF = "\n";

    @Test
    void neqDecodedAsLineFeedPlusEqShouldBeReportedInEveryMathContext() {
        String decoded = LF + "eq 0";
        assertError("Điều kiện $x " + decoded + "$", "xuống dòng bên trong $...$");
        assertError("$$x " + decoded + "$$", "có thể do \\neq chưa thoát trong JSON");
        assertError("$\\begin{aligned} x " + decoded + " \\end{aligned}$", "có thể do \\neq chưa thoát trong JSON");
        assertError("$\\begin{cases} x " + decoded + " \\end{cases}$", "có thể do \\neq chưa thoát trong JSON");
        assertError("$\\begin{matrix} x " + decoded + " \\end{matrix}$", "có thể do \\neq chưa thoát trong JSON");
    }

    @Test
    void notinDecodedAsLineFeedPlusOtinShouldBeReportedInDisplayMath() {
        assertError("$$x " + LF + "otin A$$", "có thể do \\notin chưa thoát trong JSON");
    }

    @Test
    void otherJsonEscapeVictimsDecodedAsControlCharactersShouldBeReportedInEveryMathContext() {
        // \right -> CR + "ight" (có trong corpus), \times -> TAB, \begin -> BS, \frac -> FF
        String[] victims = {"\r" + "ight)", "\t" + "imes", "\b" + "egin{matrix}", "\f" + "rac{1}{2}"};
        for (String victim : victims) {
            assertError("$$x " + victim + "$$", "ký tự điều khiển");
            assertError("$\\begin{aligned} x " + victim + " \\end{aligned}$", "ký tự điều khiển");
            assertError("Tính $x " + victim + "$", "ký tự điều khiển");
        }
    }

    @Test
    void realMultilineFormulaWithoutDecodedCommandIsStillValid() {
        assertValid("$$x &= 1 \\\\" + LF + "y &= 2$$");
        assertValid("$\\begin{aligned}" + LF + "x &= 1 \\\\" + LF + "y &= 2" + LF + "\\end{aligned}$");
        // dòng bắt đầu bằng một chữ cái đơn hoặc tiền tố ngẫu nhiên không phải lệnh bắt đầu bằng n
        assertValid("$$a = 1" + LF + "e = 2$$");
        assertValid("$$a = 1" + LF + "xy = 2" + LF + "eqx = 3$$");
    }

    // ---------- Phủ allowlist ----------

    @Test
    void everyAllowedCommandIsAcceptedInAFormula() {
        String[] plainCommands = {
                "cdot", "times", "div", "pm", "neq", "ne", "le", "leq", "ge", "geq",
                "Rightarrow", "Leftrightarrow", "rightarrow", "leftrightarrow",
                "dots", "ldots", "cdots", "pi", "angle", "triangle", "circ", "infty",
                "in", "notin", "cup", "cap", "sin", "cos", "tan", "cot", "quad"};
        for (String command : plainCommands) {
            assertValid("$a \\" + command + " b$");
        }
        assertValid("$\\frac{a}{b}$");
        assertValid("$\\sqrt{a}$");
        assertValid("$\\left( a \\right)$");
        assertValid("$\\begin{matrix} a \\end{matrix}$");
        assertValid("$\\text{ cm}$");
    }

    @Test
    void escapedSymbolsInsideMultilineEnvironmentsShouldNotAffectBalance() {
        assertValid("$\\left\\{\\begin{matrix} \\{ a \\} \\\\ \\$ b \\end{matrix}\\right.$");
        assertValid("$\\begin{cases} \\frac12 \\\\ \\{ x \\} \\end{cases}$");
    }

    // ---------- Ký hiệu thoát không được nhầm là cú pháp ----------

    @Test
    void escapedSymbolsShouldNotAffectBalance() {
        assertValid("Giá 5 \\$ và $x$");
        assertValid("$a \\$ b$");
        assertValid("$x \\in \\{1; 2\\}$");
        assertValid("$\\}$");
        assertValid("$a \\\\ b$");
    }

    @Test
    void realUnbalancedBraceIsStillReported() {
        assertError("$x}$", "dấu } thừa");
        assertError("$\\frac{1}{2}}$ và $\\frac{1}{2}}$", "dấu } thừa");
        assertEquals(2, LatexValidationRules.validate("$\\frac{1}{2}}$ và $\\frac{1}{2}}$").stream()
                .filter(e -> e.contains("dấu } thừa")).count());
    }

    @Test
    void commandOutsideMathIsStillReported() {
        assertError("A = \\frac{1}{x}", "lệnh LaTeX nằm ngoài cặp $...$");
    }
}
