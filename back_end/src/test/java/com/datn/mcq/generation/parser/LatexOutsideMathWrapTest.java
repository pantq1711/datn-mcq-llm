package com.datn.mcq.generation.parser;

import com.datn.mcq.enums.Option;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.dto.ParsedSaDto;
import com.datn.mcq.generation.dto.ParsedTfDto;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.QuestionQcInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lệnh LaTeX nằm NGOÀI cặp $...$ (2/11, thẩm định UX đề 10: Phần I câu 2 có lời giải "\frac{5x + y}{xy}" không nằm trong
 * $). Ở chế độ sửa của parser (run production): đoạn là một biểu thức liền mạch, không có chữ tiếng Việt hay từ tiếng Anh,
 * thì tự bọc $...$ và ghi cảnh báo; không chắc thì giữ nguyên để bước kiểm định dạng báo lỗi (sinh lại).
 */
class LatexOutsideMathWrapTest {

    private static LlmOutputRepair.WrapRepair wrap(String text) {
        return LlmOutputRepair.wrapLatexOutsideMath(text);
    }

    // ---------- hàm thuần ----------

    @Test
    void bieuThucLienMachDuocBoc() {
        LlmOutputRepair.WrapRepair r = wrap("Ta có \\frac{a}{b} = 2");
        assertEquals("Ta có $\\frac{a}{b} = 2$", r.text());
        assertEquals(1, r.wrapped());
    }

    @Test
    void chuDaNamTrongDolaKhongDoi() {
        for (String text : List.of("Ta có $\\frac{a}{b} = 2$", "Cho $x^{2} + 1$ và $y_{1}$.", "Không có công thức.", "",
                "Tính $\\sqrt{3}$, rồi $\\frac{1}{2}$.")) {
            LlmOutputRepair.WrapRepair r = wrap(text);
            assertEquals(text, r.text());
            assertEquals(0, r.wrapped(), text);
        }
        assertEquals(null, wrap(null).text());
    }

    @Test
    void dauChamCauVaDauPhayDungCuoiNamNgoaiCapDola() {
        assertEquals("Biểu thức rút gọn là $\\frac{5x + y}{xy}$.", wrap("Biểu thức rút gọn là \\frac{5x + y}{xy}.").text());
        assertEquals("Ta có $(\\frac{1}{2})$, suy ra x.", wrap("Ta có (\\frac{1}{2}), suy ra x.").text());
    }

    @Test
    void mu_vaChiSoTrongNgoacNhonNgoaiDolaCungBoc() {
        assertEquals("Giá trị $x^{2} + 1$ là số dương.", wrap("Giá trị x^{2} + 1 là số dương.").text());
        assertEquals("Ta có $a_{1} + a_{2}$ nên đúng.", wrap("Ta có a_{1} + a_{2} nên đúng.").text());
    }

    @Test
    void chuTiengVietVaTuTiengAnhChanDoanBoc() {
        LlmOutputRepair.WrapRepair vi = wrap("Ta có \\frac{1}{2} và \\sqrt{3} nên đúng");
        assertEquals("Ta có $\\frac{1}{2}$ và $\\sqrt{3}$ nên đúng", vi.text());
        assertEquals(2, vi.wrapped());
        assertEquals("so $\\frac{1}{2}$ and $\\frac{3}{4}$", wrap("so \\frac{1}{2} and \\frac{3}{4}").text());
    }

    @Test
    void nhieuLenhLienTiepChiBocMotLan() {
        LlmOutputRepair.WrapRepair r = wrap("Kết quả \\frac{1}{2} + \\frac{1}{3} = \\frac{5}{6}");
        assertEquals("Kết quả $\\frac{1}{2} + \\frac{1}{3} = \\frac{5}{6}$", r.text());
        assertEquals(1, r.wrapped());
    }

    @Test
    void khongSinhDauDolaDoiKhiSatMotCongThucKhac() {
        assertEquals("Ta có $x$ $\\frac{1}{2}$", wrap("Ta có $x$\\frac{1}{2}").text());
        assertEquals("$\\frac{1}{2}$ $y$ là", wrap("\\frac{1}{2}$y$ là").text());
    }

    @Test
    void dauDolaThoatKhongPhaiNganCach() {
        assertEquals("Giá 5\\$ và $\\frac{1}{2}$.", wrap("Giá 5\\$ và \\frac{1}{2}.").text());
    }

    /** "\n" (gạch rồi n) là xuống dòng của JSON chưa giải mã hết, không phải lệnh LaTeX. */
    @Test
    void gachNKhongPhaiLenh() {
        for (String text : List.of("Dòng một\\nDòng hai", "Bước 1:\\nTính tiếp", "Kết quả\\n\\nxong", "a\\n b")) {
            LlmOutputRepair.WrapRepair r = wrap(text);
            assertEquals(text, r.text());
            assertEquals(0, r.wrapped(), text);
        }
        assertEquals("Ta có $x \\neq 0$ nên đúng", wrap("Ta có x \\neq 0 nên đúng").text(), "\\neq vẫn là lệnh");
    }

    @Test
    void khongChacThiGiuNguyen() {
        for (String text : List.of(
                "Ta có \\frac{a}{b = 2",          // ngoặc nhọn lệch
                "Ta có \\foo{a} nên đúng",         // lệnh lạ
                "Cho $x = 1 và \\frac{1}{2}",      // số dấu $ lẻ: không biết đâu là trong/ngoài
                "Đơn vị là \\text{km}",            // \text do bước gỡ vỏ lo
                "Ta có \\begin{cases} x \\end{cases}")) {
            LlmOutputRepair.WrapRepair r = wrap(text);
            assertEquals(text, r.text(), text);
            assertEquals(0, r.wrapped(), text);
        }
    }

    @Test
    void canhBaoCoTenTruong() {
        assertEquals("explanation: đã tự bọc $ quanh công thức LaTeX nằm ngoài $",
                LlmOutputRepair.wrapWarning("explanation", 1));
        assertEquals("statement_b: đã tự bọc $ quanh 2 công thức LaTeX nằm ngoài $",
                LlmOutputRepair.wrapWarning("statement_b", 2));
    }

    // ---------- parser (mọi trường văn bản) ----------

    @Test
    void mcqLoiGiaiDe10DuocBocVaQuaFormat() {
        String raw = "{\"question_text\": \"Rút gọn $\\\\frac{1}{x} + \\\\frac{1}{y}$.\","
                + " \"option_a\": \"$\\\\frac{x + y}{xy}$\", \"option_b\": \"$\\\\frac{2}{xy}$\","
                + " \"option_c\": \"$\\\\frac{1}{xy}$\", \"option_d\": \"$x + y$\", \"correct_option\": \"A\","
                + " \"explanation\": \"Quy đồng ta được \\\\frac{5x + y}{xy} khi $xy \\\\neq 0$.\"}";
        ParsedMcqDto p = McqJsonParser.parse(raw, true);
        assertTrue(p.parseSuccess(), p.parseError());
        assertEquals("Quy đồng ta được $\\frac{5x + y}{xy}$ khi $xy \\neq 0$.", p.explanation());
        assertTrue(p.parseWarnings().contains("explanation: đã tự bọc $ quanh công thức LaTeX nằm ngoài $"),
                p.parseWarnings().toString());

        FormatChecker.FormatCheckOutcome out = new FormatChecker().check(new QuestionQcInput(1, true, p.questionText(),
                p.optionA(), p.optionB(), p.optionC(), p.optionD(), p.correctOption(), p.explanation(), true, false, null)
                .withParseWarnings(p.parseWarnings()).withLlmOutput(true));
        assertTrue(out.passed(), out.errors().toString());
        assertEquals(p.parseWarnings(), out.answerWarnings());

        ParsedMcqDto strict = McqJsonParser.parse(raw);
        assertEquals("Quy đồng ta được \\frac{5x + y}{xy} khi $xy \\neq 0$.", strict.explanation(), "giải mã chặt không sửa");
    }

    @Test
    void moiTruongCuaMcqSaVaTfDuocBoc() {
        ParsedMcqDto mcq = McqJsonParser.parse("{\"question_text\": \"Tính \\\\sqrt{9} ngay.\","
                + " \"option_a\": \"\\\\frac{1}{2}\", \"option_b\": \"2\", \"option_c\": \"x^{2}\","
                + " \"option_d\": \"4\", \"correct_option\": \"A\", \"explanation\": \"Đúng \\\\frac{1}{2}.\"}", true);
        assertTrue(mcq.parseSuccess(), mcq.parseError());
        assertEquals("Tính $\\sqrt{9}$ ngay.", mcq.questionText());
        assertEquals("$\\frac{1}{2}$", mcq.optionA());
        assertEquals("$x^{2}$", mcq.optionC());
        assertEquals("Đúng $\\frac{1}{2}$.", mcq.explanation());
        assertEquals("2", mcq.optionB());
        assertEquals(Option.A, mcq.correctOption());

        ParsedSaDto sa = SaJsonParser.parse("{\"question_text\": \"Tính \\\\sqrt{9} ngay.\","
                + " \"explanation\": \"Ta có \\\\frac{a}{b} = 2\", \"answer\": \"3\"}", true);
        assertEquals("Tính $\\sqrt{9}$ ngay.", sa.questionText());
        assertEquals("Ta có $\\frac{a}{b} = 2$", sa.explanation());
        assertEquals("3", sa.answer());

        ParsedTfDto tf = TfJsonParser.parse("{\"question_text\": \"Cho \\\\frac{1}{x} nhé.\","
                + " \"statement_a\": \"Giá trị x^{2} + 1 dương.\", \"statement_b\": \"Đúng.\","
                + " \"statement_c\": \"Sai.\", \"statement_d\": \"Đúng.\","
                + " \"explanation\": \"Ta có \\\\frac{a}{b} = 2\","
                + " \"label_a\": \"T\", \"label_b\": \"T\", \"label_c\": \"F\", \"label_d\": \"T\"}", true);
        assertEquals("Cho $\\frac{1}{x}$ nhé.", tf.questionText());
        assertEquals("Giá trị $x^{2} + 1$ dương.", tf.statements().get(0));
        assertEquals("Ta có $\\frac{a}{b} = 2$", tf.explanation());
    }

    @Test
    void dapAnCauTraLoiNganKhongBiBocDeLoiDangDapAnVanRoRang() {
        ParsedSaDto sa = SaJsonParser.parse("{\"question_text\": \"Tính.\", \"explanation\": \"Giải.\","
                + " \"answer\": \"\\\\frac{11}{4}\"}", true);
        assertEquals("\\frac{11}{4}", sa.answer());
        assertFalse(sa.parseWarnings().stream().anyMatch(w -> w.startsWith("answer: đã tự bọc")), sa.parseWarnings().toString());
    }

    @Test
    void khongChacThiGiuNguyenVaBuocFormatBaoLoiSinhLai() {
        ParsedMcqDto p = McqJsonParser.parse("{\"question_text\": \"Tính \\\\foo{3} nhé.\","
                + " \"option_a\": \"$1$\", \"option_b\": \"$2$\", \"option_c\": \"$3$\", \"option_d\": \"$4$\","
                + " \"correct_option\": \"A\", \"explanation\": \"Ta có \\\\frac{a}{b = 2\"}", true);
        assertEquals("Tính \\foo{3} nhé.", p.questionText());
        assertEquals("Ta có \\frac{a}{b = 2", p.explanation());
        assertTrue(p.parseWarnings().isEmpty(), p.parseWarnings().toString());
        FormatChecker.FormatCheckOutcome out = new FormatChecker().check(new QuestionQcInput(1, true, p.questionText(),
                p.optionA(), p.optionB(), p.optionC(), p.optionD(), p.correctOption(), p.explanation(), true, false,
                null).withLlmOutput(true));
        assertFalse(out.passed());
        assertTrue(out.errors().stream().anyMatch(e -> e.startsWith("question_text: ") && e.contains("nằm ngoài cặp $")),
                out.errors().toString());
        assertTrue(out.errors().stream().anyMatch(e -> e.startsWith("explanation: ") && e.contains("nằm ngoài cặp $")),
                "lời giải của đầu ra LLM: LaTeX ngoài $ không bọc được là lỗi định dạng (sinh lại): " + out.errors());
    }

    @Test
    void loiGiaiCuaGiaoVienLaTeXNgoaiDolaChiCanhBaoNhuCu() {
        FormatChecker.FormatCheckOutcome out = new FormatChecker().check(new QuestionQcInput(1, true, "Tính $1 + 1$.",
                "$1$", "$2$", "$3$", "$4$", Option.B, "Ta có \\foo{a} nên đúng", true, false, null));
        assertTrue(out.passed(), out.errors().toString());
        assertTrue(out.warnings().stream().anyMatch(w -> w.startsWith("explanation: ") && w.contains("nằm ngoài cặp $")),
                out.warnings().toString());
    }

    @Test
    void khongPhaiDauRaLlmThiParserChatKhongSua() {
        ParsedTfDto tf = TfJsonParser.parse("{\"question_text\": \"Cho \\\\frac{1}{x} nhé.\","
                + " \"statement_a\": \"A.\", \"statement_b\": \"B.\", \"statement_c\": \"C.\", \"statement_d\": \"D.\","
                + " \"explanation\": \"E.\", \"label_a\": \"T\", \"label_b\": \"T\", \"label_c\": \"F\","
                + " \"label_d\": \"T\"}", false);
        assertEquals("Cho \\frac{1}{x} nhé.", tf.questionText());
    }
}
