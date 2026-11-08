package com.datn.mcq.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Chuẩn hóa ký hiệu độ khi xuất Word: mọi cách viết độ TRONG công thức về "°" (U+00B0) sát số, vì "^{\circ}" thành chỉ số
 * trên cỡ nhỏ tách khỏi số (Equation: sSup + "∘"; MathType: ô chỉ số trên cỡ chỉ số). Chuỗi trong test là chuỗi Java:
 * "\\circ" là một dấu \ rồi circ.
 */
class DegreeSymbolsTest {

    private static final String EXPECTED = "$90°$";

    @ParameterizedTest
    @ValueSource(strings = {
            "$90°$",
            "$90^{\\circ}$",
            "$90^\\circ$",
            "$90^{o}$",
            "$90^o$",
            "$90\\degree$",
            "$90^{\\degree}$",
            "$90^\\degree$",
            "$90 ^ { \\circ }$",
            "$90 ^ \\circ$",
            "$90˚$",            // U+02DA ngay sau số
            "$90 ˚$",
            "$90^{°}$",         // U+00B0 làm chỉ số trên
            "$90^°$",
            "$90^{˚}$",         // U+02DA
            "$90^˚$",
            "$90^{∘}$",         // U+2218
            "$90^∘$",
            "$90 ^ { ∘ }$",
            "$90 ^ ˚$",
    })
    void everySpellingBecomesTheSameString(String input) {
        assertEquals(EXPECTED, DegreeSymbols.normalize(input));
    }

    @Test
    void widehatEquationFromTheTask() {
        assertEquals("$\\widehat{A} = 90°$", DegreeSymbols.normalize("$\\widehat{A} = 90^{\\circ}$"));
        assertEquals("$\\widehat{A} = 90°$", DegreeSymbols.normalize("$\\widehat{A} = 90^\\circ$"));
    }

    @Test
    void realWorldMixedSentence() {
        assertEquals("Tứ giác $ABCD$ có $\\widehat{A} = 80°$, $\\widehat{B} = 90°$ và $\\widehat{C} = 100°$.",
                DegreeSymbols.normalize("Tứ giác $ABCD$ có $\\widehat{A} = 80^\\circ$, $\\widehat{B} = 90^{\\circ}$ và "
                        + "$\\widehat{C} = 100^{o}$."));
    }

    @Test
    void severalDegreesInOneFormulaAndInsideFractions() {
        assertEquals("$360° - 170° = 190°$", DegreeSymbols.normalize("$360^{\\circ} - 170^\\circ = 190^{\\circ}$"));
        assertEquals("$\\frac{170°}{3}$", DegreeSymbols.normalize("$\\frac{170^{\\circ}}{3}$"));
        assertEquals("$(x + 10)°$", DegreeSymbols.normalize("$(x + 10)^{\\circ}$"));
    }

    @Test
    void displayMathIsNormalizedToo() {
        assertEquals("$$90°$$", DegreeSymbols.normalize("$$90^{\\circ}$$"));
    }

    @Test
    void decimalNumberBeforeLetterO() {
        assertEquals("$32,5°$", DegreeSymbols.normalize("$32,5^{o}$"));
        assertEquals("$32.5°$", DegreeSymbols.normalize("$32.5^o$"));
    }

    @Test
    void zeroExponentsAreRealPowersAndStay() {
        for (String s : new String[]{"$x^{0}$", "$2^0$", "$a^{0} = 1$", "Tính $10^0$ và $5^{0}$."}) {
            assertSame(s, DegreeSymbols.normalize(s), s);
        }
    }

    @Test
    void letterOSuperscriptChangesOnlyRightAfterANumber() {
        for (String s : new String[]{"$x^{o}$", "$x^o$", "$A^{o}$", "$a_1^o$", "$a_{12}^{o}$", "$90^{or}$", "$90^or$",
                "$(a+b)^o$"}) {
            assertSame(s, DegreeSymbols.normalize(s), s);
        }
    }

    @Test
    void otherCircCommandsAreNotDegrees() {
        for (String s : new String[]{"$f \\circ g$", "$90^\\circC$", "$x^{\\circledR}$", "$\\circ$", "$90 \\circ 2$",
                "$90^\\degreeSign$"}) {
            assertSame(s, DegreeSymbols.normalize(s), s);
        }
    }

    @Test
    void ringAboveAfterANumberAndSuperscriptRingsInRealSentences() {
        assertEquals("$\\widehat{A} = 90°$", DegreeSymbols.normalize("$\\widehat{A} = 90˚$"));
        assertEquals("$32,5°$ và $32.5°$", DegreeSymbols.normalize("$32,5˚$ và $32.5˚$"));
        assertEquals("$(x + 10)°$", DegreeSymbols.normalize("$(x + 10)^{˚}$"));
        assertEquals("$x°$", DegreeSymbols.normalize("$x^{∘}$"));
        assertEquals("$360° - 170° = 190°$", DegreeSymbols.normalize("$360^{∘} - 170˚ = 190^°$"));
        assertEquals("$$90°$$", DegreeSymbols.normalize("$$90˚$$"));
    }

    @Test
    void ringAboveNotAfterANumberStays() {
        for (String s : new String[]{"$x˚$", "$A˚$", "$a_1˚$", "$a_{12}˚$", "$(a+b)˚$", "$˚$"}) {
            assertSame(s, DegreeSymbols.normalize(s), s);
        }
    }

    @Test
    void ringOperatorAloneAndCircWithoutCaretStay() {
        for (String s : new String[]{"$f ∘ g$", "$f∘g$", "$∘$", "$90 ∘ 2$", "$f \\circ g$", "$\\circ$", "$90 \\circ 2$",
                "$(f ∘ g)(x)$"}) {
            assertSame(s, DegreeSymbols.normalize(s), s);
        }
    }

    @Test
    void newSpellingsOutsideFormulasStay() {
        for (String s : new String[]{"góc 90˚ ngoài công thức", "90^{˚}", "90^{∘} và 5^°", "f ∘ g", "giá 5\\$ và 90˚"}) {
            assertSame(s, DegreeSymbols.normalize(s), s);
        }
    }

    @Test
    void outsideFormulasNothingChanges() {
        for (String s : new String[]{"góc 90^{\\circ} không nằm trong công thức", "90^o", "giá 5\\$ và 90^{\\circ}",
                "Góc 90° viết thường ngoài công thức"}) {
            assertSame(s, DegreeSymbols.normalize(s), s);
        }
    }

    @Test
    void escapedDollarDoesNotOpenFormula() {
        assertEquals("giá 5\\$ và $90°$", DegreeSymbols.normalize("giá 5\\$ và $90^{\\circ}$"));
        // "\$" trong công thức không đóng công thức
        assertEquals("$a \\$ 90°$", DegreeSymbols.normalize("$a \\$ 90^{\\circ}$"));
    }

    @Test
    void unclosedDollarLeavesTheRestAlone() {
        String s = "Có $90^{\\circ}$ và $80^{\\circ}";
        assertEquals("Có $90°$ và $80^{\\circ}", DegreeSymbols.normalize(s));
    }

    @Test
    void stringWithoutDegreeSymbolIsReturnedAsTheSameObject() {
        for (String s : new String[]{"", "Bậc của $2x^3$ là", "$P = (x + 1)^2$", "$90°$", "$\\widehat{A}$"}) {
            assertSame(s, DegreeSymbols.normalize(s), s);
        }
        assertEquals(null, DegreeSymbols.normalize(null));
    }
}
