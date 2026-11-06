package com.datn.mcq.common.util;

import com.datn.mcq.common.util.LiteralNewlines.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "\n" theo nghĩa đen (dấu \ rồi chữ n) lọt vào lời giải do Gemini sinh. Chuỗi trong test là chuỗi ĐÃ giải mã JSON:
 * "\\n" trong mã Java là hai ký tự \ và n; "\n" là xuống dòng thật.
 */
class LiteralNewlinesTest {

    /** Lời giải thật của đề 19, Phần III câu 4 (DB datn_mcq_e2e, slot 330, câu 398), nguyên văn. */
    private static final String REAL_EXPLANATION =
            "Tổng các góc trong tứ giác $EFGH$ bằng $360^{\\circ}$.\n$\\widehat{E} + \\widehat{F} + \\widehat{G} + "
                    + "\\widehat{H} = 360^{\\circ}$ $.\\n$ $190^{\\circ} + 2\\widehat{H} + \\widehat{H} = 360^{\\circ}$ $.\\n$ "
                    + "$3\\widehat{H} = 360^{\\circ} - 190^{\\circ}$ $.\\n$ $3\\widehat{H} = 170^{\\circ}$ $.\\n\\widehat{H} = "
                    + "\\frac{170^{\\circ}}{3}$.";

    private static String text(String s) {
        return LiteralNewlines.repair(s).text();
    }

    @Test
    void realExplanationOfExam19LosesEveryLiteralNewlineAndKeepsDollarsPaired() {
        Result r = LiteralNewlines.repair(REAL_EXPLANATION);
        String out = r.text();

        assertFalse(out.contains("\\n"), out);
        assertEquals(0, countUnescapedDollars(out) % 2, "số dấu $ phải chẵn: " + out);
        assertTrue(out.contains("\\widehat{H} = \\frac{170^{\\circ}}{3}"), out);
        assertTrue(r.notable());
        assertEquals(4, r.insideMath());
        assertEquals(3, r.emptyPairs());
        assertEquals(
                "Tổng các góc trong tứ giác $EFGH$ bằng $360^{\\circ}$.\n$\\widehat{E} + \\widehat{F} + \\widehat{G} + "
                        + "\\widehat{H} = 360^{\\circ}$ . $190^{\\circ} + 2\\widehat{H} + \\widehat{H} = 360^{\\circ}$ . "
                        + "$3\\widehat{H} = 360^{\\circ} - 190^{\\circ}$ . $3\\widehat{H} = 170^{\\circ}$ $. "
                        + "\\widehat{H} = \\frac{170^{\\circ}}{3}$.",
                out);
    }

    @Test
    void latexCommandsStartingWithNAreKept() {
        for (String cmd : LiteralNewlines.N_COMMANDS) {
            String in = "$a \\" + cmd + " b$";
            assertEquals(in, text(in), cmd);
        }
        assertEquals("$a \\neq 0$", text("$a \\neq 0$"));
        assertEquals("$x \\notin A$", text("$x \\notin A$"));
        assertEquals("$\\nabla f$", text("$\\nabla f$"));
    }

    @Test
    void literalNewlineOutsideMathBecomesRealNewline() {
        Result r = LiteralNewlines.repair("\\nTa có $x = 1$.\\nVậy x = 1");
        assertEquals("\nTa có $x = 1$.\nVậy x = 1", r.text());
        assertEquals(2, r.outsideMath());
        assertFalse(r.notable(), "\\n ngoài công thức là việc bình thường, không báo");
    }

    @Test
    void literalNewlineInsideMathBecomesOneSpaceAndKeepsFollowingLetters() {
        assertEquals("$x +1$", text("$x\\n+1$"));
        assertEquals("$ Ta$", text("$\\nTa$"));
        assertTrue(LiteralNewlines.repair("$x\\n+1$").notable());
    }

    @Test
    void emptyOrPunctuationOnlyPairsLoseTheirDollars() {
        assertEquals("a . b", text("a $ .\\n$ b"));
        assertEquals("a , b", text("a $,\\n$ b"));
    }

    @Test
    void vietnameseWordAfterBackslashNIsNotACommandEvenInComposedForm() {
        assertEquals("Bước 1:\nuôi", text("Bước 1:\\nuôi"));
        String nfdNeu = java.text.Normalizer.normalize("nếu", java.text.Normalizer.Form.NFD);
        assertEquals("a\n" + nfdNeu.substring(1), text("a\\" + nfdNeu));
    }

    @Test
    void escapedDollarAndDoubleBackslashAreLeftAlone() {
        assertEquals("Giá \\$5\nhết", text("Giá \\$5\\nhết"));
        assertEquals("a\\\\nb", text("a\\\\nb"));
    }

    @Test
    void textWithoutLiteralNewlineIsReturnedUnchanged() {
        String s = "Rút gọn $\\frac{4x^2y}{8xy^3}$ với $x \\neq 0$.\nDòng thật.";
        assertSame(s, LiteralNewlines.repair(s).text());
        assertNull(LiteralNewlines.repair(null).text());
        assertEquals("", LiteralNewlines.repair("").text());
    }

    /** Chuỗi hợp lệ có $ cạnh dấu câu/khoảng trắng nhưng KHÔNG có "\n" chữ: trả về đúng đối tượng chuỗi ban đầu. */
    @Test
    void validDollarLayoutsWithoutLiteralNewlineAreReturnedAsTheSameObject() {
        String[] inputs = {
                "Tính $x$, $y$.", "$a$. $b$", "$x$ $y$", "$A$; $B$: $C$", "Cho $a$ và $b$ .", "$x$\n$y$"
        };
        for (String in : inputs) {
            Result r = LiteralNewlines.repair(in);
            assertSame(in, r.text(), in);
            assertEquals(0, r.emptyPairs(), in);
            assertFalse(r.notable(), in);
        }
    }

    /** Cùng các chuỗi đó nằm trong trường có "\n" chữ khác: chỉ "\n" bị đổi, mọi cặp $ còn lại giữ nguyên từng ký tự. */
    @Test
    void otherDollarPairsOfAFieldWithALiteralNewlineAreNotTouched() {
        assertEquals("Tính $x$, $y$.\nVậy $a$. $b$ và $x$ $y$; $A$; $B$: $C$ $x$\n$y$",
                text("Tính $x$, $y$.\\nVậy $a$. $b$ và $x$ $y$; $A$; $B$: $C$ $x$\n$y$"));
        // "\n" trong một công thức: chỉ đoạn đó được xét, các cặp $ kế bên (kể cả cách nhau bằng dấu câu) giữ nguyên
        assertEquals("$a$ . $b$, $c +1$. $x$ $y$", text("$a$ $.\\n$ $b$, $c\\n+1$. $x$ $y$"));
    }

    @Test
    void warningOnlyWhenSomethingInsideMathWasFixed() {
        assertNull(LiteralNewlines.warning("explanation", LiteralNewlines.repair("a\\nb")));
        String w = LiteralNewlines.warning("explanation", LiteralNewlines.repair("$x\\n+1$"));
        assertTrue(w.startsWith("json: đã sửa "), w);
        assertTrue(w.endsWith("explanation"), w);
    }

    private static int countUnescapedDollars(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\\') {
                i++;
            } else if (s.charAt(i) == '$') {
                n++;
            }
        }
        return n;
    }
}
