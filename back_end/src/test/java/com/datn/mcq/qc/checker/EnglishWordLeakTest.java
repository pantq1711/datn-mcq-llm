package com.datn.mcq.qc.checker;

import com.datn.mcq.enums.Option;
import com.datn.mcq.qc.dto.QuestionQcInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Từ tiếng Anh đứng riêng thường gặp (and, or, the, is, true, false) nằm ngoài $...$ trong câu do LLM sinh (2/11, thẩm
 * định UX): chỉ gắn cờ (cảnh báo, câu không bị loại).
 */
class EnglishWordLeakTest {

    private final FormatChecker checker = new FormatChecker();

    @Test
    void timDuocTuTiengAnhNgoaiCongThucKhongPhanBietHoaThuong() {
        assertEquals(List.of("and"), EnglishWordLeak.find("Cho $a$ and $b$ là hai số."));
        assertEquals(List.of("the", "is"), EnglishWordLeak.find("The kết quả is đúng."));
        assertEquals(List.of("or", "true", "false"), EnglishWordLeak.find("a OR b: True/False, false."),
                "đứng riêng giữa dấu câu vẫn là từ riêng; mỗi từ chỉ báo một lần");
    }

    @Test
    void tuTrongCongThucHoacLaMotPhanCuaTuKhacKhongBiBat() {
        for (String text : List.of("Cho $x \\text{ and } y$.", "Tính $a$ hoặc $b$.", "Chất liệu theo cách khác.",
                "Đúng hoặc sai?", "This orchestra isolated thesis.", "Hai điểm $A$, $B$ thuộc đường thẳng.", "", "   ")) {
            assertEquals(List.of(), EnglishWordLeak.find(text), text);
        }
        assertEquals(List.of(), EnglishWordLeak.find(null));
    }

    @Test
    void mcqDauRaLlmGanCoOMoiTruongVanQuaFormat() {
        QuestionQcInput mcq = new QuestionQcInput(1, true, "Cho $a$ and $b$.", "$1$", "$2$ or $3$", "$3$", "$4$", Option.B,
                "Kết quả is $2$.", true, false, null).withLlmOutput(true);
        FormatChecker.FormatCheckOutcome out = checker.check(mcq);
        assertTrue(out.passed(), out.errors().toString());
        assertEquals(List.of("question_text: có từ tiếng Anh ngoài công thức (and)",
                "option_b: có từ tiếng Anh ngoài công thức (or)", "explanation: có từ tiếng Anh ngoài công thức (is)"),
                out.warnings());
    }

    @Test
    void saVaTfDauRaLlmCungDuocGanCo() {
        QuestionQcInput sa = QuestionQcInput.shortAnswer(1, true, "Tính $1 + 1$ and $2$.", "Ta có the kết quả.", "2", true,
                true, false, null).withLlmOutput(true);
        FormatChecker.FormatCheckOutcome saOut = checker.check(sa);
        assertTrue(saOut.passed(), saOut.errors().toString());
        assertEquals(List.of("question_text: có từ tiếng Anh ngoài công thức (and)",
                "explanation: có từ tiếng Anh ngoài công thức (the)"), saOut.warnings());

        QuestionQcInput tf = QuestionQcInput.trueFalse(1, true, "Cho $P$.", List.of("Ý một.", "Ý hai true.", "Ý ba.", "Ý bốn."),
                List.of("T", "F", "T", "F"), "Lời giải.", true, false, null).withLlmOutput(true);
        FormatChecker.FormatCheckOutcome tfOut = checker.check(tf);
        assertTrue(tfOut.passed(), tfOut.errors().toString());
        assertEquals(List.of("statement_b: có từ tiếng Anh ngoài công thức (true)"), tfOut.warnings());
    }

    @Test
    void chuGiaoVienVietKhongBiGanCo() {
        QuestionQcInput mcq = new QuestionQcInput(1, true, "Cho $a$ and $b$.", "$1$", "$2$", "$3$", "$4$", Option.B,
                "Kết quả is $2$.", true, false, null);
        FormatChecker.FormatCheckOutcome out = checker.check(mcq);
        assertFalse(out.warnings().stream().anyMatch(w -> w.contains("tiếng Anh")), out.warnings().toString());
    }
}
