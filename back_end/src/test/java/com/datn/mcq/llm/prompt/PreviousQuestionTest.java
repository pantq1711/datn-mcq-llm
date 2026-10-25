package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Danh sách câu đã sinh trong prompt sa/tf (độ đa dạng, Q1-Q2 ngày 22/10): tối đa 10 câu, mỗi mục (đề, đề dẫn, một ý)
 * cắt tối đa 200 ký tự, không kèm đáp số hay nhãn; danh sách rỗng thì prompt giữ nguyên như prompt gốc.
 */
class PreviousQuestionTest {

    private static final List<Difficulty> LEVELS =
            List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD);

    @Test
    void catMoiMucToiDa200KyTuVaThuKhoangTrang() {
        assertEquals("Giải $2x - 8 = 0$.", PreviousQuestion.clip("  Giải $2x - 8 = 0$.  "));
        assertEquals("Dòng 1 Dòng 2", PreviousQuestion.clip("Dòng 1\n\n  Dòng 2"));
        String exact = "ệ".repeat(200);
        assertEquals(exact, PreviousQuestion.clip(exact), "đúng 200 ký tự thì không cắt");
        String longText = "ệ".repeat(250);
        assertEquals("ệ".repeat(200) + "…", PreviousQuestion.clip(longText));
        assertEquals("", PreviousQuestion.clip(null));
    }

    @Test
    void danhSachRongThiKhongCoDoan() {
        assertEquals("", PreviousQuestion.section(List.of(), false));
        assertEquals("", PreviousQuestion.section(null, true));
    }

    @Test
    void doanSaChiCoDeToiDa10Cau() {
        List<PreviousQuestion> items = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            items.add(new PreviousQuestion("Giải $2x - " + (2 * i) + " = 0$.", List.of()));
        }
        String section = PreviousQuestion.section(items, false);
        assertTrue(section.contains(PreviousQuestion.HEADER), section);
        assertTrue(section.contains("\n1. Giải $2x - 2 = 0$.\n"), section);
        assertTrue(section.contains("\n10. Giải $2x - 20 = 0$.\n"), section);
        assertFalse(section.contains("11."), "chỉ 10 câu đầu");
        assertFalse(section.contains("a) "), "câu sa không có ý");
    }

    @Test
    void doanTfCoDeDanVa4Y() {
        String longStatement = "Ý rất dài " + "x".repeat(300);
        String section = PreviousQuestion.section(List.of(new PreviousQuestion("Cho phương trình $2x - 6 = 0$.",
                List.of("Ý một.", "Ý hai.", "Ý ba.", longStatement))), true);
        assertTrue(section.contains("\n1. Đề dẫn: Cho phương trình $2x - 6 = 0$.\n"
                + "   a) Ý một.\n   b) Ý hai.\n   c) Ý ba.\n   d) " + PreviousQuestion.clip(longStatement) + "\n"), section);
        assertEquals(201, PreviousQuestion.clip(longStatement).length());
    }

    @Test
    void promptSaVaTfChenDanhSachSauCauNguon() {
        List<PreviousQuestion> sa = List.of(new PreviousQuestion("Giải $2x - 8 = 0$.", List.of()));
        String saPrompt = SaPromptDraftV0.buildGenerationPrompt("Toán", 8, null, null, "Giải $2x - 6 = 0$.",
                Difficulty.EASY, sa);
        assertTrue(saPrompt.contains(PreviousQuestion.section(sa, false)), saPrompt);
        assertTrue(saPrompt.indexOf(SaPromptDraftV0.NEW_PROBLEM_RULE) < saPrompt.indexOf(PreviousQuestion.HEADER));
        assertTrue(saPrompt.indexOf(PreviousQuestion.HEADER) < saPrompt.indexOf("Quy tắc đáp số"));
        assertEquals(SaPromptDraftV0.buildGenerationPrompt("Toán", 8, null, null, "Giải $2x - 6 = 0$.",
                Difficulty.EASY), SaPromptDraftV0.buildGenerationPrompt("Toán", 8, null, null, "Giải $2x - 6 = 0$.",
                Difficulty.EASY, List.of()), "danh sách rỗng thì giống prompt gốc");

        List<PreviousQuestion> tf = List.of(new PreviousQuestion("Cho $P = x + 1$.",
                List.of("Ý một.", "Ý hai.", "Ý ba.", "Ý bốn.")));
        String tfPrompt = TfPromptDraftV0.buildGenerationPrompt("Toán", 8, null, null, "Giải $2x - 6 = 0$.", LEVELS,
                tf);
        assertTrue(tfPrompt.contains(PreviousQuestion.section(tf, true)), tfPrompt);
        assertTrue(tfPrompt.indexOf(TfPromptDraftV0.NEW_PROBLEM_RULE) < tfPrompt.indexOf(PreviousQuestion.HEADER));
        assertTrue(tfPrompt.indexOf(PreviousQuestion.HEADER) < tfPrompt.indexOf("Mức độ mục tiêu"));
        assertFalse(TfPromptDraftV0.buildGenerationPrompt("Toán", 8, null, null, "Giải $2x - 6 = 0$.", LEVELS)
                .contains(PreviousQuestion.HEADER));
    }

    @Test
    void phienBanPromptV1() {
        assertEquals("sa-v1-draft", SaPromptDraftV0.PROMPT_VERSION);
        assertEquals("tf-v2-draft", TfPromptDraftV0.PROMPT_VERSION);
    }
}
