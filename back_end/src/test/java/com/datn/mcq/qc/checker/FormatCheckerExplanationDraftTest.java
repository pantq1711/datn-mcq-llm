package com.datn.mcq.qc.checker;

import com.datn.mcq.enums.Option;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.dto.ParsedSaDto;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.qc.dto.QuestionQcInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lời giải còn phần nháp/tự sửa (31/10, thẩm định đề 5-7: câu 82 viết "Xin lỗi, tính lại…" rồi đổi dữ kiện). Cả 3 dạng câu:
 * đầu ra của LLM bị format_failed kèm lý do; câu do giáo viên viết hoặc sửa chỉ bị cảnh báo, không bị chặn.
 *
 * <p>Hai nhánh: cụm chắc chắn là nháp ("xin lỗi", "sorry", "wait", "nhầm" dạng hẹp, "à không") thì chặn; "tính lại",
 * "làm lại", "sửa lại" cũng gặp trong lời giải bình thường nên không chặn, chỉ cảnh báo
 * ({@link ExplanationDraftLeak#SUSPECT_WARNING}) và bật lý do "cần xem kỹ" {@code explanation_draft_suspect}.
 *
 * <p>Quy tắc "nhầm" (chọn ưu tiên không chặn nhầm): chỉ khớp "tôi nhầm", "em nhầm", "bị nhầm" (không khi theo sau là "lẫn")
 * hoặc "nhầm" ngay trước dấu "," hay "."; "nhầm lẫn thường gặp" và "nhầm" đứng giữa câu thì không khớp. Cụm "à không" so
 * có dấu, đứng riêng một từ, vì bỏ dấu thì trùng "a không âm" (a là biến).
 */
class FormatCheckerExplanationDraftTest {

    private static final String MESSAGE = "Lời giải còn chứa phần nháp/tự sửa; chỉ ghi lời giải cuối cùng.";
    private static final List<String> STATEMENTS = List.of("Ý một.", "Ý hai.", "Ý ba.", "Ý bốn.");
    private static final List<String> LABELS = List.of("T", "F", "T", "F");

    private final FormatChecker checker = new FormatChecker();

    private static QuestionQcInput mcq(String explanation) {
        return new QuestionQcInput(1, true, "Tính $1 + 1$.", "$1$", "$2$", "$3$", "$4$", Option.B, explanation, true, false);
    }

    private static QuestionQcInput sa(String explanation) {
        return QuestionQcInput.shortAnswer(1, true, "Tính $1 + 1$.", explanation, "2", true, true, false, null);
    }

    private static QuestionQcInput tf(String explanation) {
        return QuestionQcInput.trueFalse(1, true, "Cho biểu thức $P = x^2$.", STATEMENTS, LABELS, explanation, true, false,
                null);
    }

    private static final List<Function<String, QuestionQcInput>> ALL_TYPES = List.of(
            FormatCheckerExplanationDraftTest::mcq, FormatCheckerExplanationDraftTest::sa,
            FormatCheckerExplanationDraftTest::tf);

    @Test
    void dauRaLlmCoNhapThiFormatFailedKemLyDoCaBaDang() {
        for (Function<String, QuestionQcInput> make : ALL_TYPES) {
            QuestionQcInput input = make.apply("Ta có $1 + 1 = 3$. À không, $1 + 1 = 2$.").withLlmOutput(true);
            FormatChecker.FormatCheckOutcome out = checker.check(input);
            assertFalse(out.passed(), input.questionType().name());
            assertEquals(List.of(MESSAGE), out.errors(), input.questionType().name());
        }
    }

    @Test
    void cauTfXinLoiDaBiQuyTacTfBatThiKhongBaoHaiLan() {
        FormatChecker.FormatCheckOutcome out = checker.check(tf("Xin lỗi, tính lại biểu thức. Ý a đúng.").withLlmOutput(true));
        assertFalse(out.passed());
        assertEquals(1, out.errors().size(), out.errors().toString());
        assertTrue(out.errors().get(0).startsWith("explanation: lời giải tự sửa lại đề hoặc ý"), out.errors().toString());
    }

    @Test
    void khongPhaiDauRaLlmChiCanhBaoKhongChan() {
        for (Function<String, QuestionQcInput> make : ALL_TYPES) {
            QuestionQcInput input = make.apply("Xin lỗi, tính lại: $1 + 1 = 2$.");
            FormatChecker.FormatCheckOutcome out = checker.check(input);
            assertTrue(out.passed(), input.questionType().name() + ": " + out.errors());
            assertTrue(out.errors().isEmpty(), out.errors().toString());
            assertEquals(1, out.warnings().stream().filter(MESSAGE::equals).count(),
                    input.questionType().name() + ": " + out.warnings());
        }
    }

    @Test
    void loiGiaiSachVoiCoLlmVanDatKhongCanhBao() {
        for (Function<String, QuestionQcInput> make : ALL_TYPES) {
            QuestionQcInput input = make.apply("Khai triển $(x + 1)^2 = x^2 + 2x + 1$ nên chọn B.").withLlmOutput(true);
            FormatChecker.FormatCheckOutcome out = checker.check(input);
            assertTrue(out.passed(), input.questionType().name() + ": " + out.errors());
            assertFalse(out.warnings().contains(MESSAGE), out.warnings().toString());
        }
    }

    @Test
    void moiCumTrongDanhSachDeuBiBatKhongPhanBietHoaThuongVaDau() {
        for (String phrase : List.of("Xin lỗi, ta sai.", "XIN LỖI", "xin loi", "À không, đáp án là 4.", "à không",
                "Wait, recompute.", "WAIT", "Sorry about that.", "sorry")) {
            assertTrue(ExplanationDraftLeak.detect(phrase), "phải bị chặn: " + phrase);
        }
    }

    /** "tính lại", "làm lại", "sửa lại" cũng gặp trong lời giải bình thường: không chặn, chỉ nghi ngờ (cảnh báo + cần xem kỹ). */
    @Test
    void cumTinhLaiLamLaiSuaLaiChiNghiNgoKhongChan() {
        for (String phrase : List.of("Ta tính lại nhé.", "TÍNH LẠI", "tinh lai", "Làm lại bước 2.", "lam lai", "Sửa lại đề.",
                "sua lai", "Thử tính lại bằng cách khác: $2 + 2 = 4$.")) {
            assertFalse(ExplanationDraftLeak.detect(phrase), "không được chặn: " + phrase);
            assertTrue(ExplanationDraftLeak.suspect(phrase), "phải bị nghi ngờ: " + phrase);
        }
        for (String text : List.of("Khai triển $(x + 1)^2 = x^2 + 2x + 1$.", "Xin lỗi, tính lại.", "")) {
            assertFalse(ExplanationDraftLeak.suspect(text), "không nghi ngờ (sạch hoặc đã bị chặn): " + text);
        }
        assertFalse(ExplanationDraftLeak.suspect(null));
    }

    @Test
    void cumNghiNgoDauRaLlmDatFormatNhungCoCanhBaoCaBaDang() {
        for (Function<String, QuestionQcInput> make : ALL_TYPES) {
            QuestionQcInput input = make.apply("Thử tính lại bằng cách khác: $1 + 1 = 2$.").withLlmOutput(true);
            FormatChecker.FormatCheckOutcome out = checker.check(input);
            assertTrue(out.passed(), input.questionType().name() + ": " + out.errors());
            assertTrue(out.errors().isEmpty(), out.errors().toString());
            assertEquals(1, out.warnings().stream().filter(ExplanationDraftLeak.SUSPECT_WARNING::equals).count(),
                    input.questionType().name() + ": " + out.warnings());
            assertFalse(out.warnings().contains(MESSAGE), "cụm nghi ngờ không dùng thông điệp của nhánh chặn");
        }
    }

    @Test
    void cumNghiNgoCuaGiaoVienCungChiCanhBao() {
        for (Function<String, QuestionQcInput> make : ALL_TYPES) {
            FormatChecker.FormatCheckOutcome out = checker.check(make.apply("Làm lại bằng hằng đẳng thức: $1 + 1 = 2$."));
            assertTrue(out.passed(), out.errors().toString());
            assertTrue(out.warnings().contains(ExplanationDraftLeak.SUSPECT_WARNING), out.warnings().toString());
        }
    }

    @Test
    void cumChanKemCumNghiNgoChiBaoMotCanhBaoChan() {
        FormatChecker.FormatCheckOutcome out = checker.check(mcq("Xin lỗi, tính lại: $1 + 1 = 2$.").withLlmOutput(true));
        assertFalse(out.passed());
        assertEquals(List.of(MESSAGE), out.errors());
        assertFalse(out.warnings().contains(ExplanationDraftLeak.SUSPECT_WARNING), out.warnings().toString());
    }

    @Test
    void cumNhamChiKhopCacDangDaChon() {
        for (String text : List.of("Tôi nhầm ở bước trên.", "tôi nhầm lẫn ở bước 2", "Em nhầm dấu.", "Ta bị nhầm ở đây.",
                "Ở bước 2 nhầm, phải là $x$.", "Nhầm. Đáp án là 4.", "toi nham o buoc 2")) {
            assertTrue(ExplanationDraftLeak.detect(text), "phải bị bắt: " + text);
        }
        for (String text : List.of("Học sinh hay mắc nhầm lẫn thường gặp khi bỏ dấu ngoặc.", "Đây là nhầm lẫn thường gặp.",
                "Dễ bị nhầm lẫn giữa hai công thức.", "Hai biểu thức này dễ nhầm với nhau.")) {
            assertFalse(ExplanationDraftLeak.detect(text), "không được chặn nhầm: " + text);
        }
    }

    @Test
    void cumAKhongChiKhopKhiLaTuDungRieng() {
        assertTrue(ExplanationDraftLeak.detect("À không, ta chọn lại."));
        for (String text : List.of("Cho a không âm, ta có $a = 4$.", "Biến đổi mà không đổi giá trị.",
                "Chọn B và không chọn C.", "Cho $a$ không âm.")) {
            assertFalse(ExplanationDraftLeak.detect(text), "không được chặn nhầm: " + text);
        }
    }

    @Test
    void khongCoLoiGiaiThiKhongBat() {
        assertFalse(ExplanationDraftLeak.detect(null));
        assertFalse(ExplanationDraftLeak.detect("   "));
    }

    @Test
    void luongSinhMcqVaSaDanhDauDauRaLlm() {
        String draft = "Xin lỗi, tính lại: $1 + 1 = 2$.";
        QuestionQcInput mcq = GenerationService.mcqQcInput(7,
                new ParsedMcqDto(true, "Tính $1 + 1$.", "$1$", "$2$", "$3$", "$4$", Option.B, draft, null), true, false);
        assertTrue(mcq.llmOutput(), "câu mcq do LLM sinh (worker và sinh đồng bộ) luôn được kiểm");
        assertFalse(checker.check(mcq).passed());
        QuestionQcInput sa = GenerationService.shortAnswerQcInput(7,
                new ParsedSaDto(true, "Tính $1 + 1$.", draft, "2", true, null), true, false);
        assertTrue(sa.llmOutput(), "câu sa do LLM sinh luôn được kiểm");
        assertFalse(checker.check(sa).passed());
    }
}
