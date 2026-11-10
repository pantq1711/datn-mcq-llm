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

    /** L2 (9/11): "\\" giữa các công thức của lời giải thành xuống dòng khi lưu câu; trong công thức giữ nguyên. */
    @Test
    void doubleBackslashBetweenFormulasBecomesANewlineWhenSaving() {
        List<String> warnings = new ArrayList<>();
        String out = LlmOutputRepair.textField("explanation", "$x = 1$\\\\ $y = 2$", true, true, warnings);

        assertEquals("$x = 1$\n$y = 2$", out);
        assertTrue(warnings.isEmpty(), warnings.toString());

        String cases = "$\\begin{cases} x = 1 \\\\ y = 2 \\end{cases}$";
        assertEquals(cases, LlmOutputRepair.textField("explanation", cases, true, true, new ArrayList<>()));
    }

    /** L1 (9/11): lệnh LaTeX mất "\" trong công thức được thêm lại khi lưu câu, cảnh báo thuộc nhóm tự sửa (không bật cờ). */
    @Test
    void missingBackslashInsideMathIsAddedWhenSavingAndDoesNotRaiseTheFlag() {
        List<String> warnings = new ArrayList<>();
        String out = LlmOutputRepair.textField("question_text", "Cho $\\widehat{A} = 70^circ$ và $AB perp CD$.", true,
                true, warnings);

        assertEquals("Cho $\\widehat{A} = 70^\\circ$ và $AB \\perp CD$.", out);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).startsWith(LlmOutputRepair.ESCAPE_WARNING_PREFIX), warnings.get(0));
        assertTrue(com.datn.mcq.qc.service.QcSummaryService.isAutoFixedWarning(warnings.get(0)), warnings.get(0));
    }

    @Test
    void missingBackslashIsNotTouchedOutsideMathOrInNonRepairMode() {
        List<String> warnings = new ArrayList<>();
        assertEquals("Hình circle và frac", LlmOutputRepair.textField("question_text", "Hình circle và frac", true, true,
                warnings));
        assertEquals("$70^circ$", LlmOutputRepair.textField("question_text", "$70^circ$", false, true, warnings));
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
