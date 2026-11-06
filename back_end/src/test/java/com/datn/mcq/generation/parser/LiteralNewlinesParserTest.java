package com.datn.mcq.generation.parser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "\n" chữ trong công thức được sửa ở bước lưu câu (chế độ sửa của parser), ghi cảnh báo tự sửa, không bật cờ. */
class LiteralNewlinesParserTest {

    @Test
    void repairModeFixesNewlineInsideMathAndRecordsAnAutoFixWarning() {
        List<String> warnings = new ArrayList<>();
        String out = LlmOutputRepair.textField("explanation", "Ta có $x\\n+1 = 2$.", true, true, warnings);

        assertEquals("Ta có $x +1 = 2$.", out);
        assertEquals(1, warnings.size(), warnings.toString());
        // cùng tiền tố với các cảnh báo tự sửa khác nên QcSummaryService không bật "cần xem kỹ"
        assertTrue(warnings.get(0).startsWith(LlmOutputRepair.ESCAPE_WARNING_PREFIX), warnings.get(0));
    }

    @Test
    void repairModeTurnsEmptyDollarPairsIntoPlainPunctuation() {
        List<String> warnings = new ArrayList<>();
        String out = LlmOutputRepair.textField("explanation", "$a = 1$ $.\\n$ $b = 2$", true, true, warnings);

        assertFalse(out.contains("\\n"), out);
        assertFalse(out.contains("$ $"), out);
        assertEquals("$a = 1$ . $b = 2$", out);
    }

    @Test
    void newlineOutsideMathIsFixedWithoutAWarning() {
        List<String> warnings = new ArrayList<>();
        String out = LlmOutputRepair.textField("explanation", "Bước 1\\nBước 2", true, true, warnings);

        assertEquals("Bước 1\nBước 2", out);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void commandsStartingWithNStayIntactInRepairMode() {
        List<String> warnings = new ArrayList<>();
        String out = LlmOutputRepair.textField("option_a", "$x \\neq 0$ và $y \\notin A$", true, true, warnings);

        assertEquals("$x \\neq 0$ và $y \\notin A$", out);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void nonRepairModeIsNotTouchedByTheNewStep() {
        List<String> warnings = new ArrayList<>();
        String out = LlmOutputRepair.textField("explanation", "Ta có $x\\n+1$.", false, true, warnings);

        // chế độ không sửa (experiment): chỉ còn bước đổi "\n" ngoài công thức như trước, "\n" trong công thức giữ nguyên
        assertEquals("Ta có $x\\n+1$.", out);
        assertTrue(warnings.isEmpty());
    }
}
