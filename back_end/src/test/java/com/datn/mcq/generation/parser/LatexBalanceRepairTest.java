package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.dto.ParsedSaDto;
import com.datn.mcq.generation.dto.ParsedTfDto;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.service.QcSummaryService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sửa lỗi mô hình viết {@code \balance} thay cho {@code \parallel} (24/11: phát hiện trên VPS 21 câu Ta-lét, 7 câu đã
 * vào ngân hàng; pandoc không hiểu {@code \balance} nên Word in nguyên "$MN \balance BC$").
 */
class LatexBalanceRepairTest {

    @Test
    void parserCheDoSuaDoiBalanceThanhParallelTrongMoiTruongVaGhiCanhBao() {
        String rawMcq = "{\"question_text\": \"Cho $\\\\triangle ABC$, lấy $M \\\\in AB, N \\\\in AC$ sao cho $MN \\\\balance BC$.\","
                + " \"option_a\": \"$MN \\\\balance BC$\","
                + " \"option_b\": \"$MN \\\\balance EF$ và $EF \\\\balance BC$\","
                + " \"option_c\": \"$AB \\\\perp CD$\","
                + " \"option_d\": \"$$PQ \\\\balance BC$$\","
                + " \"correct_option\": \"A\","
                + " \"explanation\": \"Theo định lí Thalès đảo, $MN \\\\balance BC$.\"}";

        ParsedMcqDto strict = McqJsonParser.parse(rawMcq, false);
        assertTrue(strict.parseSuccess());
        assertTrue(strict.questionText().contains("$MN \\balance BC$"), "chế độ chặt không sửa");
        assertEquals("$MN \\balance BC$", strict.optionA());
        assertEquals(List.of(), strict.parseWarnings());

        ParsedMcqDto repaired = McqJsonParser.parse(rawMcq, true);
        assertTrue(repaired.parseSuccess(), repaired.parseError());
        assertEquals("Cho $\\triangle ABC$, lấy $M \\in AB, N \\in AC$ sao cho $MN \\parallel BC$.",
                repaired.questionText());
        assertEquals("$MN \\parallel BC$", repaired.optionA());
        assertEquals("$MN \\parallel EF$ và $EF \\parallel BC$", repaired.optionB());
        assertEquals("$AB \\perp CD$", repaired.optionC());
        assertEquals("$PQ \\parallel BC$", repaired.optionD());
        assertEquals("Theo định lí Thalès đảo, $MN \\parallel BC$.", repaired.explanation());

        List<String> balanceWarnings = repaired.parseWarnings().stream()
                .filter(w -> w.contains("\\balance") && w.contains("\\parallel"))
                .toList();
        assertEquals(5, balanceWarnings.size(), repaired.parseWarnings().toString());
        assertTrue(balanceWarnings.stream().allMatch(w -> w.startsWith(LlmOutputRepair.ESCAPE_WARNING_PREFIX)),
                balanceWarnings.toString());
        assertTrue(balanceWarnings.stream().allMatch(QcSummaryService::isAutoFixedWarning),
                balanceWarnings.toString());
        assertEquals(List.of(),
                new QcSummaryService.QcSummary(true, true, null, false, repaired.parseWarnings()).reviewReasons());

        FormatChecker.FormatCheckOutcome qc = new FormatChecker().check(new QuestionQcInput(
                1, true, repaired.questionText(), repaired.optionA(), repaired.optionB(), repaired.optionC(),
                repaired.optionD(), repaired.correctOption(), repaired.explanation(), true, false, null)
                .withParseWarnings(repaired.parseWarnings()).withLlmOutput(true));
        assertTrue(qc.passed(), qc.errors().toString());
        assertEquals(List.of(), qc.warnings(), "sau khi đổi sang \\parallel không còn cảnh báo lệnh lạ");

        // JSON có "\balance" một dấu gạch (chưa thoát JSON): bước sửa escape giữ "\balance", bước sửa lệnh đổi tiếp sang "\parallel"
        String unescapedJson = "{\"question_text\": \"Cho $MN \\balance BC$.\","
                + " \"option_a\": \"$1$\", \"option_b\": \"$2$\", \"option_c\": \"$3$\", \"option_d\": \"$4$\","
                + " \"correct_option\": \"A\", \"explanation\": \"Vì $MN \\balance BC$.\"}";
        ParsedMcqDto fromUnescaped = McqJsonParser.parse(unescapedJson, true);
        assertTrue(fromUnescaped.parseSuccess(), fromUnescaped.parseError());
        assertEquals("Cho $MN \\parallel BC$.", fromUnescaped.questionText());
        assertEquals("Vì $MN \\parallel BC$.", fromUnescaped.explanation());

        // Mọi trường của câu Đúng/Sai (question_text, statement_a..d, explanation)
        String rawTf = "{\"question_text\": \"Cho hình vẽ có $DE \\\\balance BC$.\","
                + " \"statement_a\": \"$DE \\\\balance BC$.\","
                + " \"statement_b\": \"$EF \\\\balance AB$.\","
                + " \"statement_c\": \"$AD = DB$.\","
                + " \"statement_d\": \"$MN \\\\balance PQ$.\","
                + " \"explanation\": \"a) Đúng vì $DE \\\\balance BC$.\","
                + " \"label_a\": \"T\", \"label_b\": \"F\", \"label_c\": \"T\", \"label_d\": \"F\"}";
        ParsedTfDto tf = TfJsonParser.parse(rawTf, true);
        assertTrue(tf.parseSuccess(), tf.parseError());
        assertEquals("Cho hình vẽ có $DE \\parallel BC$.", tf.questionText());
        assertEquals("$DE \\parallel BC$.", tf.statements().get(0));
        assertEquals("$EF \\parallel AB$.", tf.statements().get(1));
        assertEquals("$MN \\parallel PQ$.", tf.statements().get(3));
        assertEquals("a) Đúng vì $DE \\parallel BC$.", tf.explanation());

        // Mọi trường của câu Trả lời ngắn (question_text, explanation, answer) và normalizeField (giáo viên sửa câu)
        String rawSa = "{\"question_text\": \"Cho $MN \\\\balance BC$, tính $x$.\","
                + " \"explanation\": \"Áp dụng định lí Thalès do $MN \\\\balance BC$.\","
                + " \"answer\": \"$MN \\\\balance BC$\"}";
        ParsedSaDto sa = SaJsonParser.parse(rawSa, true);
        assertTrue(sa.parseSuccess(), sa.parseError());
        assertEquals("Cho $MN \\parallel BC$, tính $x$.", sa.questionText());
        assertEquals("Áp dụng định lí Thalès do $MN \\parallel BC$.", sa.explanation());
        assertEquals("$MN \\parallel BC$", sa.answer());

        List<String> editWarnings = new ArrayList<>();
        assertEquals("Cho $MN \\parallel BC$.",
                LlmOutputRepair.normalizeField("question_text", "Cho $MN \\balance BC$.", false, false, editWarnings));
        assertEquals(1, editWarnings.size(), editWarnings.toString());
        List<String> secondPass = new ArrayList<>();
        assertEquals("Cho $MN \\parallel BC$.",
                LlmOutputRepair.normalizeField("question_text", "Cho $MN \\parallel BC$.", false, false, secondPass));
        assertEquals(List.of(), secondPass, "chạy lần hai phải idempotent");
    }

    @Test
    void balanceNamNgoaiCongThucThiKhongDoi() {
        for (String outside : List.of(
                "Đoạn thẳng MN \\balance BC.",
                "Giá \\$5 \\balance BC và $x = 1$.",
                "Biết $MN \\balance BC thì giá trị $x + 1$ là bao nhiêu?",
                "Ta có:\nMN \\balance BC")) {
            List<String> warnings = new ArrayList<>();
            String out = LlmOutputRepair.textField("explanation", outside, true, true, warnings);
            assertEquals(outside, out, outside);
            assertFalse(warnings.stream().anyMatch(w -> w.contains("\\parallel")), warnings.toString());
        }

        // Cùng một trường vừa có \balance ngoài công thức vừa có \balance trong $...$: chỉ đổi phần trong $...$
        String mixedJson = "{\"question_text\": " + SimpleJson.quote("Ngoài \\balance giữ nguyên, trong $MN \\balance BC$ đổi.")
                + ", \"option_a\": \"$1$\", \"option_b\": \"$2$\", \"option_c\": \"$3$\", \"option_d\": \"$4$\","
                + " \"correct_option\": \"A\", \"explanation\": \"Giải.\"}";
        ParsedMcqDto parsed = McqJsonParser.parse(mixedJson, true);
        assertEquals("Ngoài \\balance giữ nguyên, trong $MN \\parallel BC$ đổi.", parsed.questionText());
        assertEquals(1, parsed.parseWarnings().size(), parsed.parseWarnings().toString());
    }

    @Test
    void chuoiNhuBalancedKhongBiDoiChiDoiDungLenhBalanceTheoSauKhongPhaiChu() {
        for (String keep : List.of(
                "$\\balanced$",
                "$MN \\balanced BC$",
                "$MN \\balancex BC$",
                "$MN \\balanceABC$",
                "$MN \\balanceđ BC$")) {
            List<String> warnings = new ArrayList<>();
            assertEquals(keep, LlmOutputRepair.textField("option_a", keep, true, true, warnings), keep);
            assertEquals(List.of(), warnings, keep);
        }

        // Đúng lệnh \balance theo sau là khoảng trắng, "{", "_", dấu phẩy hoặc "$" kết thúc công thức thì được đổi;
        // \balanced đứng cạnh trong cùng trường vẫn giữ nguyên
        List<String> warnings = new ArrayList<>();
        String input = "$\\balanced$, $MN \\balance BC$, $MN \\balance{BC}$, $a \\balance_1 b$, $MN \\balance$";
        String expected = "$\\balanced$, $MN \\parallel BC$, $MN \\parallel{BC}$, $a \\parallel_1 b$, $MN \\parallel$";
        assertEquals(expected, LlmOutputRepair.textField("option_a", input, true, true, warnings));
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("4"), warnings.toString());
    }
}
