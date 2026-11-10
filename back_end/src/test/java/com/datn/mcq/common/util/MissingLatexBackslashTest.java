package com.datn.mcq.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lệnh LaTeX mất dấu "\" TRONG công thức ("$70^circ$" hiện "70circ", đề 22 câu 458). Chuỗi trong test là chuỗi Java:
 * "\\circ" là một dấu \ rồi circ.
 */
class MissingLatexBackslashTest {

    private static String fix(String s) {
        return MissingLatexBackslash.repair(s).text();
    }

    @Test
    void degreeSignFromTheTask() {
        assertEquals("$70^\\circ$", fix("$70^circ$"));
    }

    @Test
    void commandsThatAreAlreadyCorrectAreLeftAsTheSameObject() {
        String[] inputs = {"$\\frac{1}{2}$", "$70^\\circ$", "$AB \\perp CD$", "$x \\times y$", "$\\sqrt{2}$", "$\\Delta ABC$",
                "$a \\leq b$", "$\\dfrac{a}{b}$", "$\\widehat{A}$", "Không có công thức frac{1}{2}"};
        for (String in : inputs) {
            assertSame(in, MissingLatexBackslash.repair(in).text(), in);
            assertEquals(0, MissingLatexBackslash.repair(in).fixed(), in);
        }
    }

    @Test
    void missingBackslashBeforeFracIsAdded() {
        assertEquals("$\\frac{1}{2}$", fix("$frac{1}{2}$"));
    }

    @Test
    void perpAndTimesFromTheTask() {
        assertEquals("$AB \\perp CD$", fix("$AB perp CD$"));
        assertEquals("$x \\times y$", fix("$x times y$"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"circ", "cdot", "times", "perp", "parallel", "leq", "geq", "neq", "infty", "angle", "triangle",
            "Delta"})
    void everyStandaloneWordOfTheListGetsABackslash(String word) {
        assertEquals("$a \\" + word + " b$", fix("$a " + word + " b$"));
        assertEquals("$a^\\" + word + "$", fix("$a^" + word + "$"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"frac", "dfrac", "sqrt", "widehat", "overline"})
    void bracedCommandsGetABackslashOnlyBeforeABraceOrBracket(String word) {
        assertEquals("$\\" + word + "{a}$", fix("$" + word + "{a}$"));
        assertEquals("$\\" + word + "[3]{a}$", fix("$" + word + "[3]{a}$"));
        assertEquals("$" + word + " a$", fix("$" + word + " a$"), "không có { hoặc [ ngay sau thì giữ nguyên");
        assertEquals("$" + word + "$", fix("$" + word + "$"), "đứng một mình thì giữ nguyên");
    }

    @Test
    void wordsOutsideFormulasAreNotTouched() {
        String[] inputs = {"Hình circle có tâm O", "frac{1}{2} nằm ngoài", "Tam giác triangle và angle", "a times b",
                "Cho $x$ và circ, cdot, perp"};
        for (String in : inputs) {
            assertSame(in, MissingLatexBackslash.repair(in).text(), in);
        }
        assertEquals("Hình circle và $\\frac{1}{2}$", fix("Hình circle và $frac{1}{2}$"));
    }

    @Test
    void shortWordsThatAreProductsOfVariablesAreNotTouched() {
        String[] inputs = {"$pin$", "$a in B$", "$pi$", "$a pm b$", "$a div b$", "$A cap B$", "$A cup B$", "$le$", "$ge$",
                "$ne$", "$x in y$"};
        for (String in : inputs) {
            assertSame(in, MissingLatexBackslash.repair(in).text(), in);
        }
    }

    @Test
    void wordsThatMerelyContainAListedWordAreNotTouched() {
        String[] inputs = {"$circle$", "$angles$", "$Deltax$", "$xcirc$", "$timesa$", "$trianglex$", "$fracs{a}$",
                "$xfrac{a}{b}$"};
        for (String in : inputs) {
            assertSame(in, MissingLatexBackslash.repair(in).text(), in);
        }
    }

    @Test
    void digitsBeforeTheWordAreAllowed() {
        assertEquals("$70\\circ$", fix("$70circ$"));
        assertEquals("$2\\cdot 3$", fix("$2cdot 3$"));
    }

    @Test
    void textGroupsInsideFormulasAreNotTouched() {
        String in = "$x \\text{ times } y \\text{angle}$";
        assertSame(in, MissingLatexBackslash.repair(in).text());
        assertEquals("$a \\text{ times } \\times b$", fix("$a \\text{ times } times b$"));
    }

    @Test
    void displayMathAndSeveralFormulasAreAllFixed() {
        assertEquals("$$\\frac{a}{b}$$", fix("$$frac{a}{b}$$"));
        assertEquals("$a \\times b$ times $c \\cdot d$", fix("$a times b$ times $c cdot d$"));
    }

    @Test
    void formulaWithoutClosingDollarIsNotTouched() {
        String in = "Giá $5 times 3";
        assertSame(in, MissingLatexBackslash.repair(in).text());
        assertSame("Giá \\$5 times 3 và $x$", MissingLatexBackslash.repair("Giá \\$5 times 3 và $x$").text());
    }

    @Test
    void escapedDollarDoesNotOpenAFormula() {
        assertEquals("Giá \\$5 times và $a \\times b$", fix("Giá \\$5 times và $a times b$"));
    }

    @Test
    void countAndWarning() {
        MissingLatexBackslash.Result r = MissingLatexBackslash.repair("$70^circ$ và $a times b$ và $AB perp CD$");
        assertEquals(3, r.fixed());
        String w = MissingLatexBackslash.warning("explanation", r);
        // cùng tiền tố với các cảnh báo tự sửa khác nên không bật "cần xem kỹ"
        assertTrue(w.startsWith("json: đã sửa "), w);
        assertTrue(w.contains("circ") && w.contains("times") && w.contains("perp"), w);
        assertTrue(w.endsWith("explanation"), w);
        assertNull(MissingLatexBackslash.warning("explanation", MissingLatexBackslash.repair("$x$")));
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertNull(MissingLatexBackslash.repair(null).text());
        assertEquals("", fix(""));
    }
}
