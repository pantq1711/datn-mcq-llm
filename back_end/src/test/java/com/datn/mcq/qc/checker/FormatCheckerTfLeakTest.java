package com.datn.mcq.qc.checker;

import com.datn.mcq.enums.Option;
import com.datn.mcq.generation.dto.ParsedTfDto;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.qc.dto.QuestionQcInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lời giải tf tự thú nhận gán nhãn theo mẫu chỉ làm câu format_failed khi câu là ĐẦU RA CỦA LLM; câu do giáo viên viết hoặc sửa
 * (và chạy lại QC câu đã lưu) không bị chặn vì chữ của giáo viên là thẩm quyền.
 */
class FormatCheckerTfLeakTest {

    private static final List<String> STATEMENTS = List.of("Ý một.", "Ý hai.", "Ý ba.", "Ý bốn.");
    private static final List<String> LABELS = List.of("T", "F", "T", "F");
    private static final String ADMISSION = "Ý b sai nhưng theo mẫu nhãn yêu cầu gán nhãn F.";

    private final FormatChecker checker = new FormatChecker();

    private static QuestionQcInput tf(String explanation) {
        return QuestionQcInput.trueFalse(1, true, "Cho biểu thức $P = x^2$.", STATEMENTS, LABELS, explanation, true, false,
                null);
    }

    @Test
    void daulaLlmVaTuThuNhanThiFormatFailedKemLyDo() {
        FormatChecker.FormatCheckOutcome out = checker.check(tf(ADMISSION).withLlmOutput(true));
        assertFalse(out.passed());
        assertEquals(1, out.errors().size(), out.errors().toString());
        assertTrue(out.errors().get(0).startsWith("explanation: lời giải nhắc tới mẫu nhãn"), out.errors().toString());
    }

    @Test
    void tuSuaDeCungLaLoi() {
        FormatChecker.FormatCheckOutcome out = checker.check(
                tf("Xin lỗi, ta làm lại biểu thức. Ý a đúng, ý b sai.").withLlmOutput(true));
        assertFalse(out.passed());
        assertTrue(out.errors().get(0).startsWith("explanation: lời giải tự sửa lại đề hoặc ý"), out.errors().toString());
    }

    @Test
    void khongPhaiDauRaLlmThiKhongBiChan() {
        assertTrue(checker.check(tf(ADMISSION)).passed(), "câu giáo viên viết hoặc sửa không bị chặn");
        assertFalse(tf(ADMISSION).llmOutput());
    }

    @Test
    void loiGiaiSachVoiCoLlmVanDat() {
        assertTrue(checker.check(tf("Ý a đúng vì ...; ý b sai vì thiếu hạng tử $2AB$.").withLlmOutput(true)).passed());
    }

    @Test
    void coDauRaLlmDuocGiuQuaWithParseWarnings() {
        QuestionQcInput input = tf(ADMISSION).withLlmOutput(true).withParseWarnings(List.of("w"));
        assertTrue(input.llmOutput());
        assertEquals(List.of("w"), input.parseWarnings());
        assertFalse(checker.check(input).passed());
    }

    @Test
    void chiApChoCauDungSai() {
        QuestionQcInput mcq = new QuestionQcInput(1, true, "Đề", "$1$", "$2$", "$3$", "$4$", Option.B, ADMISSION, true, false)
                .withLlmOutput(true);
        assertTrue(checker.check(mcq).passed(), "mcq không áp quy tắc này");
        QuestionQcInput sa = QuestionQcInput.shortAnswer(1, true, "Tính $1 + 1$.", ADMISSION, "2", true, true, false, null)
                .withLlmOutput(true);
        assertTrue(checker.check(sa).passed(), "sa không áp quy tắc này");
    }

    @Test
    void luongSinhTfDanhDauDauRaLlm() {
        ParsedTfDto parsed = new ParsedTfDto(true, "Cho biểu thức $P = x^2$.", STATEMENTS, ADMISSION, LABELS, null);
        QuestionQcInput input = GenerationService.trueFalseQcInput(7, parsed, true, false);
        assertTrue(input.llmOutput(), "câu tf do LLM sinh (worker và sinh đồng bộ) luôn được kiểm");
        assertFalse(checker.check(input).passed());
    }
}
