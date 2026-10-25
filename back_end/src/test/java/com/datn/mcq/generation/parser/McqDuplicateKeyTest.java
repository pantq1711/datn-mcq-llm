package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.Option;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.QuestionQcInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dữ liệu thật (generated_question id=8): model trả hai khóa "option_c", khóa thứ hai đáng lẽ là "correct_option".
 * Khóa trùng ở bất kỳ đối tượng nào là lỗi parse; không lấy giá trị đầu hay giá trị sau.
 */
class McqDuplicateKeyTest {

    private static final String DUPLICATE_OPTION_C = "{\"question_text\": \"Nghiệm của phương trình $2x = 6$ là:\","
            + " \"option_a\": \"$x = 2$\", \"option_b\": \"$x = 3$\", \"option_c\": \"$x = 4$\","
            + " \"option_d\": \"$x = 12$\", \"option_c\": \"C\", \"correct_option\": \"B\","
            + " \"explanation\": \"Chia hai vế cho $2$ được $x = 3$.\"}";

    private static final String VALID = "{\"question_text\": \"Nghiệm của phương trình $2x = 6$ là:\","
            + " \"option_a\": \"$x = 2$\", \"option_b\": \"$x = 3$\", \"option_c\": \"$x = 4$\","
            + " \"option_d\": \"$x = 12$\", \"correct_option\": \"B\","
            + " \"explanation\": \"Chia hai vế cho $2$ được $x = 3$.\"}";

    @Test
    void duplicateRootKeyIsAParseErrorNamingTheKey() {
        ParsedMcqDto parsed = McqJsonParser.parse(DUPLICATE_OPTION_C);

        assertFalse(parsed.parseSuccess());
        assertTrue(parsed.parseError().contains("\"option_c\""), parsed.parseError());
        // Không giữ giá trị nào của khóa trùng.
        assertNull(parsed.optionC());
        assertNull(parsed.correctOption());
    }

    @Test
    void duplicateRootKeyMakesTheQuestionFailFormatWithTheKeyInTheMessage() {
        ParsedMcqDto parsed = McqJsonParser.parse(DUPLICATE_OPTION_C);

        FormatChecker.FormatCheckOutcome outcome = new FormatChecker().check(qcInput(parsed));

        assertFalse(outcome.passed());
        assertTrue(outcome.errors().stream().anyMatch(e -> e.contains("\"option_c\"")), () -> outcome.errors().toString());
    }

    @Test
    void validJsonBehavesAsBefore() {
        ParsedMcqDto parsed = McqJsonParser.parse(VALID);

        assertTrue(parsed.parseSuccess());
        assertNull(parsed.parseError());
        assertEquals("$x = 4$", parsed.optionC());
        assertEquals(Option.B, parsed.correctOption());
        assertTrue(new FormatChecker().check(qcInput(parsed)).passed());
    }

    @Test
    void sameKeyInDifferentObjectsIsNotADuplicate() {
        String json = "{\"a\": {\"x\": 1}, \"b\": {\"x\": 2}, \"x\": [ {\"x\": 3}, {\"x\": 4} ]}";
        assertEquals(3, SimpleJson.parseObject(json).size());
    }

    @Test
    void duplicateKeyInNestedObjectIsRejectedTheSameWay() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SimpleJson.parseObject("{\"meta\": {\"k\": 1, \"k\": 2}}"));
        assertTrue(ex.getMessage().contains("\"k\""), ex.getMessage());

        IllegalArgumentException inArray = assertThrows(IllegalArgumentException.class,
                () -> SimpleJson.parseObject("{\"items\": [ {\"k\": 1, \"k\": 1} ]}"));
        assertTrue(inArray.getMessage().contains("\"k\""), inArray.getMessage());

        String nestedInMcq = VALID.replace("\"correct_option\": \"B\",",
                "\"correct_option\": \"B\", \"meta\": {\"note\": \"a\", \"note\": \"b\"},");
        ParsedMcqDto parsed = McqJsonParser.parse(nestedInMcq);
        assertFalse(parsed.parseSuccess());
        assertTrue(parsed.parseError().contains("\"note\""), parsed.parseError());
    }

    private static QuestionQcInput qcInput(ParsedMcqDto parsed) {
        return new QuestionQcInput(8, parsed.parseSuccess(), parsed.questionText(), parsed.optionA(),
                parsed.optionB(), parsed.optionC(), parsed.optionD(), parsed.correctOption(),
                parsed.explanation(), true, false, parsed.parseError());
    }
}
