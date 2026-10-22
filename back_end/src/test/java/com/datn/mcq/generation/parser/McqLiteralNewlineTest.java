package com.datn.mcq.generation.parser;

import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.QuestionQcInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Việc 1 (22/10): model viết "\\n" trong JSON nên chuỗi sau khi giải mã chứa "\n" theo nghĩa đen
 * (dấu \ + chữ n). Ngoài công thức thì đổi thành ký tự xuống dòng; trong $...$ và $$...$$ giữ nguyên.
 * Chuỗi trong test là chuỗi ĐÃ giải mã JSON: "\\n" trong mã Java là hai ký tự \ và n.
 */
class McqLiteralNewlineTest {

    @Test
    void literalNewlineOutsideMathBecomesRealNewline() {
        assertEquals("a:\n$x=3$\nVậy", McqJsonParser.normalizeLiteralNewlines("a:\\n$x=3$\\nVậy"));
    }

    @Test
    void literalNewlineAtTheStartIsConverted() {
        assertEquals("\nVậy", McqJsonParser.normalizeLiteralNewlines("\\nVậy"));
    }

    @Test
    void commandsInsideInlineOrDisplayMathAreKept() {
        assertEquals("$a \\neq 0$", McqJsonParser.normalizeLiteralNewlines("$a \\neq 0$"));
        assertEquals("$a \\ne 0$ và $\\nu$, $\\not\\in$, $\\nabla f$",
                McqJsonParser.normalizeLiteralNewlines("$a \\ne 0$ và $\\nu$, $\\not\\in$, $\\nabla f$"));
        assertEquals("$$x \\neq 1$$ và\nTiếp", McqJsonParser.normalizeLiteralNewlines("$$x \\neq 1$$ và\\nTiếp"));
    }

    @Test
    void textWithoutLiteralNewlineIsUnchanged() {
        String text = "Rút gọn $\\frac{4x^2y}{8xy^3}$ với $x \\neq 0$.\nDòng thật.";
        assertEquals(text, McqJsonParser.normalizeLiteralNewlines(text));
        assertNull(McqJsonParser.normalizeLiteralNewlines(null));
    }

    @Test
    void escapedDollarDoesNotOpenMathAndEscapedBackslashIsKept() {
        assertEquals("Giá \\$5\nhết", McqJsonParser.normalizeLiteralNewlines("Giá \\$5\\nhết"));
        assertEquals("a\\\\nb", McqJsonParser.normalizeLiteralNewlines("a\\\\nb"));
    }

    @Test
    void knownCommandStartingWithNOutsideMathIsLeftForTheValidatorToReport() {
        // \neq nằm ngoài $...$ là lỗi thật (thiếu dấu $), không được che thành xuống dòng + "eq".
        assertEquals("Điều kiện x \\neq 0", McqJsonParser.normalizeLiteralNewlines("Điều kiện x \\neq 0"));
    }

    @Test
    void vietnameseWordsStartingWithNAreNotMistakenForCommands() {
        // Cả cụm chữ cái sau "\" là "nuôi"/"nếu"/"nên", không phải tên lệnh \nu, \ne.
        assertEquals("Bước 1:\nuôi", McqJsonParser.normalizeLiteralNewlines("Bước 1:\\nuôi"));
        assertEquals("a\nếu", McqJsonParser.normalizeLiteralNewlines("a\\nếu"));
        assertEquals("Do đó\nên chọn B", McqJsonParser.normalizeLiteralNewlines("Do đó\\nên chọn B"));
    }

    @Test
    void decomposedVietnameseLettersAreStillOneWord() {
        // Dạng tổ hợp (NFD): "nếu" = n, e, dấu mũ, dấu sắc, u. Dấu kết hợp không phải chữ cái nhưng thuộc cùng chữ,
        // nên không được đọc thành lệnh \ne.
        String nfdNeu = java.text.Normalizer.normalize("nếu", java.text.Normalizer.Form.NFD);
        String nfdNen = java.text.Normalizer.normalize("nên", java.text.Normalizer.Form.NFD);
        // "\n" theo nghĩa đen dùng chữ n đầu từ, nên sau khi đổi còn xuống dòng + phần sau chữ n.
        assertEquals("a\n" + nfdNeu.substring(1), McqJsonParser.normalizeLiteralNewlines("a\\" + nfdNeu));
        assertEquals("Do đó\n" + nfdNen.substring(1), McqJsonParser.normalizeLiteralNewlines("Do đó\\" + nfdNen));
    }

    @Test
    void questionLikeId4PassesFormatAfterParsing() {
        // JSON thô như model trả: \\n trong JSON giải mã thành "\n" theo nghĩa đen.
        String raw = "{\"question_text\": \"Nghiệm của phương trình $3x - 9 = 0$ là:\","
                + " \"option_a\": \"$x = -3$\", \"option_b\": \"$x = 3$\", \"option_c\": \"$x = 9$\","
                + " \"option_d\": \"$x = 0$\", \"correct_option\": \"B\","
                + " \"explanation\": \"Ta biến đổi như sau:\\\\n$3x = 9$\\\\n$x = 9 : 3$\\\\n$x = 3$\\\\nVậy nghiệm là $x = 3$.\"}";

        ParsedMcqDto parsed = McqJsonParser.parse(raw);

        assertTrue(parsed.parseSuccess());
        assertEquals("Ta biến đổi như sau:\n$3x = 9$\n$x = 9 : 3$\n$x = 3$\nVậy nghiệm là $x = 3$.",
                parsed.explanation());
        FormatChecker.FormatCheckOutcome outcome = new FormatChecker().check(new QuestionQcInput(
                4, true, parsed.questionText(), parsed.optionA(), parsed.optionB(), parsed.optionC(),
                parsed.optionD(), parsed.correctOption(), parsed.explanation(), true, false));
        assertTrue(outcome.passed(), () -> "Phải qua format: " + outcome.errors());
    }

    @Test
    void literalNewlineBeforeVietnameseWordInsideJsonIsConverted() {
        String raw = "{\"question_text\": \"Cho $x = 2$.\\\\nếu $y = x + 1$ thì $y$ bằng:\","
                + " \"option_a\": \"$1$\", \"option_b\": \"$2$\", \"option_c\": \"$3$\", \"option_d\": \"$4$\","
                + " \"correct_option\": \"C\", \"explanation\": \"Bước 1:\\\\nuôi giá trị $x = 2$ vào.\"}";

        ParsedMcqDto parsed = McqJsonParser.parse(raw);

        assertEquals("Cho $x = 2$.\nếu $y = x + 1$ thì $y$ bằng:", parsed.questionText());
        assertEquals("Bước 1:\nuôi giá trị $x = 2$ vào.", parsed.explanation());
    }
}
