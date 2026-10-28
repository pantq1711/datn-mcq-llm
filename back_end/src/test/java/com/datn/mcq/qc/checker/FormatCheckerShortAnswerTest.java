package com.datn.mcq.qc.checker;

import com.datn.mcq.common.util.ShortAnswerValue;
import com.datn.mcq.qc.dto.QuestionQcInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FormatChecker cho câu Trả lời ngắn (bản sửa 1 mục 5, quyết định B):
 * đáp số phải phân tích được ở chế độ STRICT và theo quy tắc LaTeX như các trường khác;
 * không còn quy tắc làm tròn; phân số chưa tối giản chỉ là cảnh báo trong answerWarnings.
 */
class FormatCheckerShortAnswerTest {

    private static final String STEM = "Nghiệm của phương trình $4x + 3 = 0$ là bao nhiêu?";
    private static final String EXPLANATION = "$4x = -3$ nên $x = -\\frac{3}{4}$.";

    private final FormatChecker checker = new FormatChecker();

    private static QuestionQcInput sa(String stem, String explanation, String answer) {
        return QuestionQcInput.shortAnswer(1, true, stem, explanation, answer, true, true, false, null);
    }

    private FormatChecker.FormatCheckOutcome check(String answer) {
        return checker.check(sa(STEM, EXPLANATION, answer));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0,7", "-0.7", "−0,7", "$1,5$", "0", "12,6", "2,75", "1234", "0,33"})
    void dapSoHopLe(String answer) {
        FormatChecker.FormatCheckOutcome outcome = check(answer);
        assertTrue(outcome.passed(), answer + " phải hợp lệ, lỗi: " + outcome.errors());
        assertEquals(List.of(), outcome.answerWarnings());
    }

    @Test
    void khongConQuyTacLamTron() {
        assertTrue(checker.check(sa("Tính $1 : 3$ (không ghi yêu cầu làm tròn).", "Xấp xỉ.", "0,33")).passed());
    }

    @Test
    void dapSoLlmSaiDinhDang() {
        String format = "answer: " + ShortAnswerValue.MSG_SA_ANSWER_FORMAT;
        assertError("05", "answer: " + ShortAnswerValue.MSG_LEADING_ZERO);
        assertError("-0", "answer: " + ShortAnswerValue.MSG_NEGATIVE_ZERO);
        assertError("1 + \\sqrt{2}", format);
        assertError("x = 2", format);
        assertError("1.500", format);
        assertError("1/0", format);
        assertError("-0,75", format);
    }

    /** 27/10 (exam 9 ô 23): phân số, hỗn số bị loại với thông điệp nói rõ cách sửa, thay vì lỗi LaTeX ngoài $. */
    @Test
    void phanSoVaHonSoBiLoaiVoiThongDiepDapAnUuTienHonLoiLatex() {
        for (String answer : List.of("\\frac{11}{4}", "$3\\frac{1}{4}$", "4\\frac{1}{3}")) {
            FormatChecker.FormatCheckOutcome outcome = check(answer);
            assertFalse(outcome.passed(), answer);
            assertEquals(List.of("answer: " + ShortAnswerValue.MSG_SA_ANSWER_FORMAT), outcome.errors(), answer);
        }
    }

    private void assertError(String answer, String expected) {
        FormatChecker.FormatCheckOutcome outcome = check(answer);
        assertFalse(outcome.passed(), answer);
        assertTrue(outcome.errors().contains(expected), answer + ": " + outcome.errors());
    }

    @Test
    void lenhLatexNgoaiCongThucODapSoLaLoiChan() {
        FormatChecker.FormatCheckOutcome outcome = check("\\frac{1}{2}");
        assertFalse(outcome.passed());
        assertTrue(outcome.errors().stream().anyMatch(e -> e.startsWith("answer: ")), outcome.errors().toString());
    }

    @Test
    void phanSoKhongConHopLeNenKhongCoCanhBaoToiGian() {
        for (String answer : List.of("2/4", "$\\frac{6}{8}$")) {
            FormatChecker.FormatCheckOutcome outcome = check(answer);
            assertFalse(outcome.passed(), answer);
            assertEquals(List.of(), outcome.answerWarnings(), answer);
        }
    }

    @Test
    void truongTrongVaDapSoKhongPhaiChuoi() {
        FormatChecker.FormatCheckOutcome blank = checker.check(sa("  ", "", " "));
        assertFalse(blank.passed());
        assertTrue(blank.errors().containsAll(List.of(
                "question_text: nội dung trống", "explanation: nội dung trống", "answer: nội dung trống")),
                blank.errors().toString());

        FormatChecker.FormatCheckOutcome number = checker.check(
                QuestionQcInput.shortAnswer(1, true, STEM, EXPLANATION, "0.5", false, true, false, null));
        assertFalse(number.passed());
        assertTrue(number.errors().contains("answer: phải là chuỗi JSON"), number.errors().toString());
    }

    @Test
    void loiLatexODeChanOLoiGiaiChiCanhBao() {
        FormatChecker.FormatCheckOutcome badStem = checker.check(sa("Tính $\\frac{1}{2$", EXPLANATION, "1"));
        assertFalse(badStem.passed());
        assertTrue(badStem.errors().stream().anyMatch(e -> e.startsWith("question_text: ")));

        FormatChecker.FormatCheckOutcome badExplanation = checker.check(sa(STEM, "Lời giải $\\frac{1}{2$", "1"));
        assertTrue(badExplanation.passed(), badExplanation.errors().toString());
        assertTrue(badExplanation.warnings().stream().anyMatch(w -> w.startsWith("explanation: ")));
    }

    @Test
    void khongKiemLatexKhiMonKhongPhaiToan() {
        QuestionQcInput input = QuestionQcInput.shortAnswer(1, true, "Q", "E", "0,5", true, false, false, null);
        assertTrue(checker.check(input).passed());
    }

    @Test
    void giaiMaLoi() {
        FormatChecker.FormatCheckOutcome outcome = checker.check(
                QuestionQcInput.shortAnswer(1, false, null, null, null, false, true, false, "Khóa trùng: \"answer\""));
        assertFalse(outcome.passed());
        assertEquals(List.of("Không giải mã được JSON hợp lệ từ phản hồi LLM: Khóa trùng: \"answer\""),
                outcome.errors());
    }
}
