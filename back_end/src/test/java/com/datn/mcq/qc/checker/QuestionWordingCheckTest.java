package com.datn.mcq.qc.checker;

import com.datn.mcq.enums.Option;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.service.QcSummaryService.QcSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Văn phong câu hỏi (góp ý giáo viên Toán 3/11): thuật ngữ sai dễ gặp ("cạnh biên" thay vì "cạnh bên") và hướng dẫn nhập đáp
 * án trong câu dẫn của câu Trả lời ngắn ("(Nhập đáp án dưới dạng…)"). Chỉ áp cho đầu ra LLM; chỉ cảnh báo (bật cờ "cần xem
 * kỹ"), câu không bị loại.
 */
class QuestionWordingCheckTest {

    private final FormatChecker checker = new FormatChecker();

    @Test
    void timCanhBienKhongPhanBietHoaThuongVaKhoangTrang() {
        assertEquals(List.of("cạnh biên"), QuestionWordingCheck.wrongTerms("Cho hình thang có hai cạnh biên bằng nhau.").stream()
                .map(QuestionWordingCheck.WrongTerm::wrong).toList());
        assertEquals(1, QuestionWordingCheck.wrongTerms("Hai Cạnh  biên của hình thang cân.").size());
        assertEquals(1, QuestionWordingCheck.wrongTerms("hai cạnh\nbiên").size());
        assertEquals("cạnh bên", QuestionWordingCheck.wrongTerms("cạnh biên").get(0).correct());
    }

    @Test
    void thuatNguDungHoacTuKhacKhongBiBat() {
        for (String text : List.of("Hai cạnh bên của hình thang.", "Cạnh bên $AD$ và cạnh đáy $BC$.", "biên độ, biên giới",
                "Cạnh bên và biên của bảng số liệu.", "Hình có cạnh biênn.", "", "  ")) {
            assertEquals(List.of(), QuestionWordingCheck.wrongTerms(text), text);
        }
        assertEquals(List.of(), QuestionWordingCheck.wrongTerms(null));
    }

    @Test
    void danhSachThuatNguSaiDeMoRong() {
        assertTrue(QuestionWordingCheck.WRONG_TERMS.stream().anyMatch(t -> t.wrong().equals("cạnh biên")));
        for (QuestionWordingCheck.WrongTerm t : QuestionWordingCheck.WRONG_TERMS) {
            assertFalse(t.wrong().equalsIgnoreCase(t.correct()), t.toString());
        }
    }

    @Test
    void nhanDienHuongDanNhapDapAn() {
        for (String text : List.of("Tính $2 + 3$. (Nhập đáp án dưới dạng số thập phân)",
                "Tính giá trị của biểu thức. Nhập đáp án là một số nguyên.",
                "Tính $x$. (nhập  kết quả vào ô trống)", "Giá trị là bao nhiêu? (Nhập đáp số)")) {
            assertTrue(QuestionWordingCheck.hasAnswerInputHint(text), text);
        }
        for (String text : List.of("Tính $2 + 3$.", "Đáp án của bài toán là số nào?", "Số thập phân nhập từ bàn phím.",
                "Phép nhập khẩu hàng hóa.", "", "  ")) {
            assertFalse(QuestionWordingCheck.hasAnswerInputHint(text), text);
        }
        assertFalse(QuestionWordingCheck.hasAnswerInputHint(null));
    }

    @Test
    void mcqLlmCanhBienBiGanCoOMoiTruongNhungVanQuaFormat() {
        QuestionQcInput mcq = new QuestionQcInput(1, true, "Hình thang cân có hai cạnh biên", "$1$.", "Cạnh biên bằng nhau.",
                "$3$.", "$4$.", Option.B, "Hai cạnh biên của hình thang cân bằng nhau.", true, false, null)
                .withLlmOutput(true);
        FormatChecker.FormatCheckOutcome out = checker.check(mcq);
        assertTrue(out.passed(), out.errors().toString());
        assertEquals(List.of(
                "question_text: dùng sai thuật ngữ \"cạnh biên\" (SGK: \"cạnh bên\")",
                "option_b: dùng sai thuật ngữ \"cạnh biên\" (SGK: \"cạnh bên\")",
                "explanation: dùng sai thuật ngữ \"cạnh biên\" (SGK: \"cạnh bên\")"), out.warnings());
    }

    @Test
    void saLlmHuongDanNhapDapAnBiGanCoVaCanhBienOLoiGiai() {
        QuestionQcInput sa = QuestionQcInput.shortAnswer(1, true, "Tính $2 + 3$. (Nhập đáp án dưới dạng số nguyên)",
                "Hai cạnh biên.", "5", true, true, false, null).withLlmOutput(true);
        FormatChecker.FormatCheckOutcome out = checker.check(sa);
        assertTrue(out.passed(), out.errors().toString());
        assertEquals(List.of("question_text: có hướng dẫn nhập đáp án trong câu dẫn",
                "explanation: dùng sai thuật ngữ \"cạnh biên\" (SGK: \"cạnh bên\")"), out.warnings());
    }

    @Test
    void huongDanNhapDapAnChiKiemOCauTraLoiNgan() {
        QuestionQcInput mcq = new QuestionQcInput(1, true, "Tính $2 + 3$. (Nhập đáp án)", "$1$.", "$2$.", "$3$.", "$4$.",
                Option.B, "Lời giải.", true, false, null).withLlmOutput(true);
        assertEquals(List.of(), checker.check(mcq).warnings());
    }

    @Test
    void tfLlmCanhBienOYBiGanCo() {
        QuestionQcInput tf = QuestionQcInput.trueFalse(1, true, "Cho hình thang cân $ABCD$.",
                List.of("Ý một.", "Hai cạnh biên bằng nhau.", "Ý ba.", "Ý bốn."), List.of("T", "T", "T", "F"), "Lời giải.", true,
                false, null).withLlmOutput(true);
        FormatChecker.FormatCheckOutcome out = checker.check(tf);
        assertTrue(out.passed(), out.errors().toString());
        assertEquals(List.of("statement_b: dùng sai thuật ngữ \"cạnh biên\" (SGK: \"cạnh bên\")"), out.warnings());
    }

    @Test
    void chuGiaoVienVietKhongBiGanCo() {
        QuestionQcInput sa = QuestionQcInput.shortAnswer(1, true, "Cạnh biên. (Nhập đáp án)", "Cạnh biên.", "5", true, true,
                false, null);
        assertEquals(List.of(), checker.check(sa).warnings());
        QuestionQcInput mcq = new QuestionQcInput(1, true, "Cạnh biên", "$1$.", "$2$.", "$3$.", "$4$.", Option.B, "Cạnh biên.",
                true, false, null);
        assertEquals(List.of(), checker.check(mcq).warnings());
    }

    /**
     * Góp ý giáo viên 4/11: phương án mcq viết quan hệ bằng chữ giữa hai tên đối tượng in hoa ("AD vuông góc với BC") thì gợi ý
     * dùng ký hiệu. Chỉ để đo, KHÔNG bật cờ.
     */
    @Test
    void nhanDienPhuongAnVietQuanHeBangChuGiuaHaiTenDoiTuong() {
        for (String text : List.of("$AD$ vuông góc với $BC$.", "AD vuông góc với BC.", "AB song song với CD", "Đoạn $MN$ lớn hơn $PQ$.",
                "$AH$ NHỎ HƠN $BC$.", "Đường thẳng AB  song\nsong với CD.", "A vuông góc với B", "$A'B'$ song song với $CD$.")) {
            assertEquals(1, QuestionWordingCheck.symbolSuggestedPhrases(text).size(), text);
        }
        assertEquals(List.of("vuông góc với"), QuestionWordingCheck.symbolSuggestedPhrases("$AD$ vuông góc với $BC$."));
        assertEquals(List.of("song song với"), QuestionWordingCheck.symbolSuggestedPhrases("AB song song với CD"));
        assertEquals(List.of("lớn hơn"), QuestionWordingCheck.symbolSuggestedPhrases("MN lớn hơn PQ"));
        assertEquals(List.of("nhỏ hơn"), QuestionWordingCheck.symbolSuggestedPhrases("MN nhỏ hơn PQ"));
    }

    @Test
    void phuongAnDungKyHieuHoacKhongGiuaHaiTenDoiTuongKhongBiBat() {
        for (String text : List.of("$AD \\perp BC$.", "$AD \\parallel BC$.", "$x > 3$.", "Hai đường thẳng $AB$ và $CD$ song song với nhau.",
                "Hai đường thẳng vuông góc với nhau.", "Số $a$ lớn hơn 3.", "Cạnh đáy lớn hơn cạnh bên.", "vuông góc với BC",
                "AD vuông góc với", "ADBC vuông góc với BCDE", "", "  ")) {
            assertEquals(List.of(), QuestionWordingCheck.symbolSuggestedPhrases(text), text);
        }
        assertEquals(List.of(), QuestionWordingCheck.symbolSuggestedPhrases(null));
    }

    @Test
    void mcqLlmPhuongAnVietBangChuBiGanCanhBaoSymbolSuggestedNhungVanQuaFormat() {
        QuestionQcInput mcq = new QuestionQcInput(1, true, "Cho hình thang $ABCD$. Khẳng định nào đúng?", "$AD$ vuông góc với $BC$.",
                "$AB \\parallel CD$.", "$AB$ song song với $CD$.", "$AB = CD$.", Option.B, "Lời giải.", true, false, null)
                .withLlmOutput(true);
        FormatChecker.FormatCheckOutcome out = checker.check(mcq);
        assertTrue(out.passed(), out.errors().toString());
        assertEquals(List.of(
                "option_a: wording_symbol_suggested (dùng chữ \"vuông góc với\" thay vì ký hiệu)",
                "option_c: wording_symbol_suggested (dùng chữ \"song song với\" thay vì ký hiệu)"), out.warnings());
    }

    @Test
    void canhBaoSymbolSuggestedChiChoPhuongAnMcqCuaDauRaLlm() {
        String text = "$AD$ vuông góc với $BC$.";
        // đề dẫn, lời giải của mcq: không kiểm
        QuestionQcInput mcq = new QuestionQcInput(1, true, text, "$1$.", "$2$.", "$3$.", "$4$.", Option.B, text, true, false, null)
                .withLlmOutput(true);
        assertEquals(List.of(), checker.check(mcq).warnings());
        // chữ giáo viên viết: không kiểm
        QuestionQcInput teacher = new QuestionQcInput(1, true, "Đề.", text, "$2$.", "$3$.", "$4$.", Option.B, "Lời giải.", true, false,
                null);
        assertEquals(List.of(), checker.check(teacher).warnings());
        // sa và tf: không kiểm (chỉ phương án mcq)
        QuestionQcInput sa = QuestionQcInput.shortAnswer(1, true, text, text, "5", true, true, false, null).withLlmOutput(true);
        assertEquals(List.of(), checker.check(sa).warnings());
        QuestionQcInput tf = QuestionQcInput.trueFalse(1, true, "Cho hình thang cân $ABCD$.", List.of(text, text, text, text),
                List.of("T", "T", "T", "F"), "Lời giải.", true, false, null).withLlmOutput(true);
        assertEquals(List.of(), checker.check(tf).warnings());
    }

    /** Cảnh báo chỉ để đo: KHÔNG bật cờ "cần xem kỹ", khác các cảnh báo văn phong khác (bật cờ). */
    @Test
    void canhBaoSymbolSuggestedKhongBatCoCanXemKy() {
        String w = QuestionWordingCheck.symbolSuggestedWarning("option_a", "vuông góc với");
        assertEquals("option_a: wording_symbol_suggested (dùng chữ \"vuông góc với\" thay vì ký hiệu)", w);
        assertTrue(QuestionWordingCheck.isSymbolSuggestedWarning(w));
        assertFalse(QuestionWordingCheck.isSymbolSuggestedWarning("question_text: dùng sai thuật ngữ \"cạnh biên\" (SGK: \"cạnh bên\")"));
        assertFalse(QuestionWordingCheck.isSymbolSuggestedWarning(null));
        assertEquals(List.of(), new QcSummary(true, true, null, false, List.of(w)).reviewReasons());
        assertEquals(List.of("warning: question_text: có hướng dẫn nhập đáp án trong câu dẫn"),
                new QcSummary(true, true, null, false, List.of(w, "question_text: có hướng dẫn nhập đáp án trong câu dẫn"))
                        .reviewReasons());
    }

    @Test
    void canhBaoNayBatCoCanXemKy() {
        assertEquals(List.of("warning: question_text: dùng sai thuật ngữ \"cạnh biên\" (SGK: \"cạnh bên\")"),
                new QcSummary(true, true, null, false,
                        List.of("question_text: dùng sai thuật ngữ \"cạnh biên\" (SGK: \"cạnh bên\")")).reviewReasons());
        assertEquals(List.of("warning: question_text: có hướng dẫn nhập đáp án trong câu dẫn"),
                new QcSummary(true, true, null, false,
                        List.of("question_text: có hướng dẫn nhập đáp án trong câu dẫn")).reviewReasons());
    }
}
