package com.datn.mcq.generation.parser;

import com.datn.mcq.enums.Option;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McqJsonParserTest {

    @Test
    void shouldParseCleanJsonAndStripMarkdownCodeFence() {
        String fencedJson = """
                ```json
                {
                  "question_text": "Rút gọn phân thức $\\\\frac{4x^2y}{8xy^3}$ với $x \\\\neq 0, y \\\\neq 0$:",
                  "option_a": "$\\\\frac{x}{2y^2}$",
                  "option_b": "$\\\\frac{2x}{y^2}$",
                  "option_c": "$\\\\frac{x}{4y^2}$",
                  "option_d": "$\\\\frac{x}{2y}$",
                  "correct_option": "A",
                  "explanation": "Chia cả tử và mẫu cho $4xy$."
                }
                ```
                """;
        ParsedMcqDto dto = McqJsonParser.parse(fencedJson);
        assertTrue(dto.parseSuccess());
        assertEquals(Option.A, dto.correctOption());
        assertEquals("$\\frac{x}{2y^2}$", dto.optionA());
        assertTrue(LatexFormatValidator.validate(dto.questionText()).isEmpty());
    }

    @Test
    void shouldFailParseWhenUnescapedBackslashGeOrCdotInJson() {
        String invalidEscapeJson = """
                {
                  "question_text": "Giải bất phương trình $x \\ge 2$",
                  "option_a": "$x \\ge 2$",
                  "option_b": "$x < 2$",
                  "option_c": "$x = 2$",
                  "option_d": "$x > 0$",
                  "correct_option": "A",
                  "explanation": "Giải trực tiếp."
                }
                """;
        ParsedMcqDto dto = McqJsonParser.parse(invalidEscapeJson);
        assertFalse(dto.parseSuccess());
        assertNotNull(dto.parseError());
        assertTrue(dto.parseError().contains("Invalid \\escape"));
    }
}

