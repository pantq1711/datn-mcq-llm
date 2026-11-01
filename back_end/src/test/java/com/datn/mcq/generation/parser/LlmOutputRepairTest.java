package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.Option;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.dto.ParsedSaDto;
import com.datn.mcq.generation.dto.ParsedTfDto;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.QuestionQcInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sửa đầu ra LLM ở chế độ sửa của parser (run production, ngày 23/10): escape LaTeX chưa nhân đôi trong chuỗi JSON và
 * "$" quên đóng ở cuối trường. Fixture lấy từ lần chạy thật exam 1 (generated_question id=20 và id=24).
 */
class LlmOutputRepairTest {

    private static String decode(String json) {
        return (String) SimpleJson.parseObject(json).get("v");
    }

    private static LlmOutputRepair.EscapeRepair repair(String jsonStringContent) {
        return LlmOutputRepair.repairLatexEscapes("{\"v\": \"" + jsonStringContent + "\"}");
    }

    // ---------- escape LaTeX (mục 2) ----------

    @Test
    void lenhLaTeXMotDauGachDuocNhanDoi() {
        Map<String, String> cases = Map.of(
                "$75^\\circ$", "$75^\\circ$",
                "$\\frac{1}{2}$", "$\\frac{1}{2}$",
                "$2 \\times 3$", "$2 \\times 3$",
                "$x \\neq 1$", "$x \\neq 1$",
                "$5 \\text{cm}$", "$5 \\text{cm}$",
                "$\\beta + \\right)$", "$\\beta + \\right)$",
                "$\\tan x$", "$\\tan x$",
                "$\\underline{x}$", "$\\underline{x}$");
        for (Map.Entry<String, String> c : cases.entrySet()) {
            LlmOutputRepair.EscapeRepair r = repair(c.getKey());
            assertTrue(r.changed(), c.getKey());
            assertEquals(c.getValue(), decode(r.json()), c.getKey());
        }
        assertEquals("json: đã sửa 1 escape LaTeX (\\circ)", repair("$75^\\circ$").warning());
        assertEquals("json: đã sửa 3 escape LaTeX (\\frac, \\times)",
                repair("$\\frac{1}{2} \\times \\frac{3}{4}$").warning());
    }

    @Test
    void escapeHopLeGiuNguyen() {
        for (String ok : List.of("$\\\\frac{1}{2}$", "Câu một.\\nCâu hai.", "Dòng 1\\nnếu x > 0", "a\\t1",
                "Nói \\\"có\\\"", "\\u00e9", "a\\/b", "x\\n2", "\\nuôi", "\\n")) {
            LlmOutputRepair.EscapeRepair r = repair(ok);
            assertFalse(r.changed(), ok);
            assertEquals("{\"v\": \"" + ok + "\"}", r.json(), ok);
            assertNull(r.warning());
        }
        assertEquals("Câu một.\nCâu hai.", decode(repair("Câu một.\\nCâu hai.").json()), "\\n thật vẫn là xuống dòng");
    }

    @Test
    void dauGachTruocKyTuKhongPhaiEscapeHopLe() {
        assertEquals("Giá \\$5", decode(repair("Giá \\$5").json()), "\\$ thành dấu đô la thường của LaTeX");
        assertEquals("$a\\,b$", decode(repair("$a\\,b$").json()));
        assertEquals("$\\{1; 2\\}$", decode(repair("$\\{1; 2\\}$").json()));
        assertEquals("$\\ge 2$", decode(repair("$\\ge 2$").json()));
    }

    @Test
    void lenhBatDauBangNChiTheoDanhSach() {
        for (String cmd : LlmOutputRepair.N_COMMANDS) {
            assertEquals("$\\" + cmd + " x$", decode(repair("$\\" + cmd + " x$").json()), cmd);
        }
        assertEquals("$\\neq$", decode(repair("$\\neq$").json()), "lệnh ở cuối công thức");
        assertEquals("\nhà", decode(repair("\\nhà").json()), "chữ khác danh sách thì vẫn là xuống dòng");
        assertEquals("\nnot2", decode(repair("\\nnot2").json()));
    }

    @Test
    void khongDungNgoaiChuoiJson() {
        LlmOutputRepair.EscapeRepair r = LlmOutputRepair.repairLatexEscapes("{\"a\": \"$x \\le 1$\", \"b\": 2}");
        assertEquals("{\"a\": \"$x \\\\le 1$\", \"b\": 2}", r.json());
    }

    // ---------- tự đóng $ (mục 3) ----------

    @Test
    void tuDongDauDoLaThieuOCuoiTruong() {
        assertEquals("Hằng đẳng thức $(a + b)^3 = a^3 + 3a^2b + 3ab^2 + b^3$.",
                LlmOutputRepair.closeTrailingDollar("Hằng đẳng thức $(a + b)^3 = a^3 + 3a^2b + 3ab^2 + b^3.").text());
        assertEquals("Giá trị của $A$ tại $x = -2024$ bằng $25$.",
                LlmOutputRepair.closeTrailingDollar("Giá trị của $A$ tại $x = -2024$ bằng $25.").text());
        assertEquals("$3\\sqrt{2}$", LlmOutputRepair.closeTrailingDollar("$3\\sqrt{2}").text());
        assertEquals("Bằng $\\frac{1}{2}$ ?!", LlmOutputRepair.closeTrailingDollar("Bằng $\\frac{1}{2} ?!").text());
        assertTrue(LlmOutputRepair.closeTrailingDollar("bằng $25.").closed());
    }

    @Test
    void khongDongKhiLeOGiuaCauHoacChanHoacDoLaThoat() {
        for (String keep : List.of(
                "Biết $x = 2 thì giá trị của biểu thức là 5.",
                "Cho $x = 2 cho y.",
                "Đã đủ $x$ và $y$.",
                "Giá là \\$5 và $x$.",
                "Giá trị là $.",
                "Không có công thức.")) {
            LlmOutputRepair.DollarRepair r = LlmOutputRepair.closeTrailingDollar(keep);
            assertFalse(r.closed(), keep);
            assertEquals(keep, r.text(), keep);
        }
    }

    // ---------- fixture thật: generated_question id=20 (MCQ Tứ giác) ----------

    static final String RAW_ID20 = "{\"question_text\": \"Cho tứ giác $EFGH$ là một hình thang cân có hai đáy là $EH$"
            + " và $FG$. Biết số đo góc $E$ bằng $75^\\circ$. Số đo của góc $H$ là bao nhiêu?\", \"option_a\":"
            + " \"$75^\\\\circ$\", \"option_b\": \"$105^\\\\circ$\", \"option_c\": \"$90^\\\\circ$\", \"option_d\":"
            + " \"$120^\\\\circ$\", \"correct_option\": \"A\", \"explanation\": \"Hai góc kề một đáy của hình thang cân"
            + " bằng nhau nên góc $H$ bằng $75^\\\\circ$.\", \"subject\": \"Toán\", \"grade\": \"8\"}";

    @Test
    void fixtureId20GiaiMaDuocVaCircDungNghia() {
        assertFalse(McqJsonParser.parse(RAW_ID20).parseSuccess(), "giải mã chặt vẫn từ chối như trước");
        ParsedMcqDto p = McqJsonParser.parse(RAW_ID20, true);
        assertTrue(p.parseSuccess(), p.parseError());
        assertTrue(p.questionText().contains("bằng $75^\\circ$. Số đo"), p.questionText());
        assertEquals("$75^\\circ$", p.optionA());
        assertEquals(Option.A, p.correctOption());
        assertEquals(List.of("json: đã sửa 1 escape LaTeX (\\circ)"), p.parseWarnings());
        FormatChecker.FormatCheckOutcome out = new FormatChecker().check(new QuestionQcInput(20, true,
                p.questionText(), p.optionA(), p.optionB(), p.optionC(), p.optionD(), p.correctOption(),
                p.explanation(), true, false, null).withParseWarnings(p.parseWarnings()));
        assertTrue(out.passed(), out.errors().toString());
        assertEquals(p.parseWarnings(), out.answerWarnings());
    }

    // ---------- fixture thật: generated_question id=24 (TF Hằng đẳng thức, dựng từ 2 ý thật) ----------

    static final String RAW_ID24 = "{\"question_text\": \"Cho biểu thức $A = (x + 1)^3 - x^3 - 3x^2 - 3x$.\","
            + " \"statement_a\": \"Biểu thức $(x + 1)^3$ là lập phương của một tổng.\","
            + " \"statement_b\": \"Khai triển theo hằng đẳng thức: $(a + b)^3 = a^3 + 3a^2b + 3ab^2 + b^3.\","
            + " \"statement_c\": \"Rút gọn được $A = 1$.\","
            + " \"statement_d\": \"Giá trị của biểu thức $A$ tại $x = -2024$ bằng $25.\","
            + " \"explanation\": \"a) Đúng. b) Đúng. c) Đúng. d) Sai vì $A = 1$.\","
            + " \"label_a\": \"T\", \"label_b\": \"T\", \"label_c\": \"T\", \"label_d\": \"F\"}";

    @Test
    void fixtureId24QuaFormatVoiHaiCanhBaoTuDong() {
        ParsedTfDto strict = TfJsonParser.parse(RAW_ID24);
        FormatChecker checker = new FormatChecker();
        assertFalse(checker.check(QuestionQcInput.trueFalse(24, true, strict.questionText(), strict.statements(),
                strict.labels(), strict.explanation(), true, false, null)).passed(), "giải mã chặt: lẻ $ như trước");

        ParsedTfDto p = TfJsonParser.parse(RAW_ID24, true);
        assertEquals("Khai triển theo hằng đẳng thức: $(a + b)^3 = a^3 + 3a^2b + 3ab^2 + b^3$.", p.statements().get(1));
        assertEquals("Giá trị của biểu thức $A$ tại $x = -2024$ bằng $25$.", p.statements().get(3));
        assertEquals(List.of("statement_b: đã tự đóng $", "statement_d: đã tự đóng $"), p.parseWarnings());
        FormatChecker.FormatCheckOutcome out = checker.check(QuestionQcInput.trueFalse(24, true, p.questionText(),
                p.statements(), p.labels(), p.explanation(), true, false, null, p.parseWarnings()));
        assertTrue(out.passed(), out.errors().toString());
        assertEquals(List.of("statement_b: đã tự đóng $", "statement_d: đã tự đóng $"), out.answerWarnings());
    }

    @Test
    void leDoLaOGiuaCauVanFormatFailed() {
        String raw = "{\"question_text\": \"Biết $x = 2 thì giá trị của biểu thức $x + 1$ là bao nhiêu?\","
                + " \"option_a\": \"$1$\", \"option_b\": \"$2$\", \"option_c\": \"$3$\", \"option_d\": \"$4$\","
                + " \"correct_option\": \"C\", \"explanation\": \"Thay $x = 2$.\"}";
        ParsedMcqDto p = McqJsonParser.parse(raw, true);
        assertTrue(p.parseSuccess());
        assertEquals(List.of(), p.parseWarnings());
        FormatChecker.FormatCheckOutcome out = new FormatChecker().check(new QuestionQcInput(1, true, p.questionText(),
                p.optionA(), p.optionB(), p.optionC(), p.optionD(), p.correctOption(), p.explanation(), true, false,
                null));
        assertFalse(out.passed());
    }

    @Test
    void dapSoSaTuDongVaChiTrim() {
        ParsedSaDto p = SaJsonParser.parse("{\"question_text\": \"Tính cạnh huyền.\", \"explanation\": \"Pythagore.\","
                + " \"answer\": \"$3\\sqrt{2}\"}", true);
        assertTrue(p.parseSuccess(), p.parseError());
        assertEquals("$3\\sqrt{2}$", p.answer());
        assertEquals(List.of("json: đã sửa 1 escape LaTeX (\\sqrt)", "answer: đã tự đóng $"), p.parseWarnings());
    }

    // ---------- gỡ \text{...} ngoài $ (27/10: đơn vị đo viết ngoài công thức, exam 10 ô 25) ----------

    private static String fixText(String in, List<String> warnings) {
        return LlmOutputRepair.textField("f", in, true, false, warnings);
    }

    private static String textWarning(int n) {
        return "json: đã sửa " + n + " \\text{} ngoài $ (đã gỡ vỏ) ở f";
    }

    @Test
    void goVoTextNgoaiDolaVaGiuMotDauCach() {
        Object[][] cases = {
                {"$40$ \\text{km/h}", "$40$ km/h", 1},
                {"$6$ \\text{ km}", "$6$ km", 1},
                {"$6$\\text{km}", "$6$ km", 1},
                {"$s = 40 \\cdot 2,5 = 100$ (\\text{km})", "$s = 40 \\cdot 2,5 = 100$ (km)", 1},
                {"$45$ \\text{ phút} = $\\frac{45}{60}$ \\text{ giờ}", "$45$ phút = $\\frac{45}{60}$ giờ", 2},
                {"Giá \\$5 \\text{km}", "Giá \\$5 km", 1},
        };
        for (Object[] c : cases) {
            List<String> warnings = new ArrayList<>();
            assertEquals(c[1], fixText((String) c[0], warnings), (String) c[0]);
            assertEquals(List.of(textWarning((Integer) c[2])), warnings, (String) c[0]);
        }
    }

    @Test
    void textTrongDolaHoacKhongPhaiLenhTextHoacHongThiGiuNguyen() {
        for (String keep : List.of(
                "$40\\text{ km/h}$",
                "\\textbf{A}",
                "\\textit{km}",
                "\\text{a \\frac{1}{2}}",
                "\\text{km",
                "Đã đủ $x$ và $y$.")) {
            List<String> warnings = new ArrayList<>();
            assertEquals(keep, fixText(keep, warnings), keep);
            assertEquals(List.of(), warnings, keep);
        }
    }

    @Test
    void cauMcqDonViNgoaiDolaQuaFormatSauKhiGo() {
        String raw = "{\"question_text\": \"Vận tốc là $40$ \\\\text{km/h}. Tính quãng đường trong $2,5$ \\\\text{giờ}.\","
                + " \"option_a\": \"$100$ \\\\text{km}\", \"option_b\": \"$110$ \\\\text{km}\","
                + " \"option_c\": \"$90$ \\\\text{km}\", \"option_d\": \"$120$ \\\\text{km}\","
                + " \"correct_option\": \"A\","
                + " \"explanation\": \"Ta có $s = 40 \\\\cdot 2,5 = 100$ (\\\\text{km}).\"}";
        FormatChecker checker = new FormatChecker();
        ParsedMcqDto strict = McqJsonParser.parse(raw);
        assertTrue(strict.questionText().contains("$40$ \\text{km/h}"), "giải mã chặt không đổi");
        assertFalse(checker.check(new QuestionQcInput(1, true, strict.questionText(), strict.optionA(),
                strict.optionB(), strict.optionC(), strict.optionD(), strict.correctOption(), strict.explanation(),
                true, false, null)).passed());

        ParsedMcqDto p = McqJsonParser.parse(raw, true);
        assertTrue(p.parseSuccess(), p.parseError());
        assertEquals("Vận tốc là $40$ km/h. Tính quãng đường trong $2,5$ giờ.", p.questionText());
        assertEquals("$100$ km", p.optionA());
        assertEquals("$120$ km", p.optionD());
        assertEquals("Ta có $s = 40 \\cdot 2,5 = 100$ (km).", p.explanation());
        FormatChecker.FormatCheckOutcome out = checker.check(new QuestionQcInput(1, true, p.questionText(),
                p.optionA(), p.optionB(), p.optionC(), p.optionD(), p.correctOption(), p.explanation(), true, false,
                null).withParseWarnings(p.parseWarnings()));
        assertTrue(out.passed(), out.errors().toString());
        assertTrue(p.parseWarnings().contains("json: đã sửa 2 \\text{} ngoài $ (đã gỡ vỏ) ở question_text"),
                p.parseWarnings().toString());
        assertTrue(p.parseWarnings().contains("json: đã sửa 1 \\text{} ngoài $ (đã gỡ vỏ) ở option_b"),
                p.parseWarnings().toString());
    }

    // ---------- ký hiệu độ bị viết thành ^\bullet / ^\triangle (31/10: thẩm định nội dung, câu 40, 42, 44, 47, 55, 57) ----------

    @Test
    void kyHieuDoBulletHoacTriangleSauChuSoThanhCirc() {
        Object[][] cases = {
                {"$75^\\bullet$", "$75^{\\circ}$", 1},
                {"$85^{\\triangle}$", "$85^{\\circ}$", 1},
                {"$360 ^ \\bullet$", "$360^{\\circ}$", 1},
                {"$90^{\\bullet}$", "$90^{\\circ}$", 1},
                {"$\\widehat{A} = 60^ { \\triangle }$", "$\\widehat{A} = 60^{\\circ}$", 1},
                {"$x^{2}$ và $(x+10)^{\\circ}$ và $95^\\bullet$", "$x^{2}$ và $(x+10)^{\\circ}$ và $95^{\\circ}$", 1},
                {"Góc $A = 75^\\bullet$, góc $B = 85^{\\triangle}$.", "Góc $A = 75^{\\circ}$, góc $B = 85^{\\circ}$.", 2},
                {"$$30^\\bullet$$", "$$30^{\\circ}$$", 1},
        };
        for (Object[] c : cases) {
            LlmOutputRepair.DegreeRepair r = LlmOutputRepair.fixDegreeSymbol((String) c[0]);
            assertEquals(c[1], r.text(), (String) c[0]);
            assertEquals(c[2], r.fixed(), (String) c[0]);
        }
    }

    @Test
    void bulletTriangleNgoaiNguCanhDoGiuNguyen() {
        for (String keep : List.of(
                "$\\triangle ABC$",
                "$a^\\bullet$",
                "$a^{\\triangle}$",
                "$(2x)^\\bullet$",
                "$2x^\\bullet$ sai chữ cái",
                "$75^\\bulletx$",
                "$75^{\\circ}$",
                "$75^\\triangleq 1$",
                "75^\\bullet",
                "Giá \\$5, góc 75^\\bullet và $x$.",
                "$75 \\bullet 2$",
                "$\\triangle ABC$ có góc $A = 75^{\\circ}$")) {
            LlmOutputRepair.DegreeRepair r = LlmOutputRepair.fixDegreeSymbol(keep);
            assertEquals(keep, r.text(), keep);
            assertEquals(0, r.fixed(), keep);
        }
        assertNull(LlmOutputRepair.fixDegreeSymbol(null).text());
    }

    @Test
    void textFieldSuaKyHieuDoVaGhiCanhBao() {
        List<String> warnings = new ArrayList<>();
        assertEquals("Biết $\\widehat{A} = 75^{\\circ}$ và $\\triangle ABC$ cân.",
                LlmOutputRepair.textField("question_text", "Biết $\\widehat{A} = 75^\\bullet$ và $\\triangle ABC$ cân.",
                        true, true, warnings));
        assertEquals(List.of("json: đã sửa 1 ký hiệu độ (^\\bullet, ^\\triangle thành ^{\\circ}) ở question_text"),
                warnings);

        List<String> strictWarnings = new ArrayList<>();
        assertEquals("$75^\\bullet$", LlmOutputRepair.textField("option_a", "$75^\\bullet$", false, true, strictWarnings));
        assertEquals(List.of(), strictWarnings, "giải mã chặt không sửa");
    }

    @Test
    void cauMcqKyHieuDoSaiDuocSuaKhiParseOCheDoSua() {
        String raw = "{\"question_text\": \"Cho hình thang cân $EFGH$, góc $E$ bằng $75^\\\\bullet$.\","
                + " \"option_a\": \"$75^\\\\bullet$\", \"option_b\": \"$105^{\\\\triangle}$\", \"option_c\": \"$90^\\\\circ$\","
                + " \"option_d\": \"$120^\\\\circ$\", \"correct_option\": \"A\", \"explanation\": \"Góc bằng $75^\\\\bullet$.\"}";
        ParsedMcqDto strict = McqJsonParser.parse(raw);
        assertEquals("$75^\\bullet$", strict.optionA(), "giải mã chặt giữ nguyên");
        ParsedMcqDto p = McqJsonParser.parse(raw, true);
        assertTrue(p.parseSuccess(), p.parseError());
        assertEquals("Cho hình thang cân $EFGH$, góc $E$ bằng $75^{\\circ}$.", p.questionText());
        assertEquals("$75^{\\circ}$", p.optionA());
        assertEquals("$105^{\\circ}$", p.optionB());
        assertEquals("Góc bằng $75^{\\circ}$.", p.explanation());
        assertTrue(p.parseWarnings().contains("json: đã sửa 1 ký hiệu độ (^\\bullet, ^\\triangle thành ^{\\circ}) ở option_b"),
                p.parseWarnings().toString());
    }

    @Test
    void goVoTextOMoiTruongCuaSaVaTf() {
        ParsedSaDto sa = SaJsonParser.parse("{\"question_text\": \"Tính quãng đường.\","
                + " \"explanation\": \"Đi $2$ \\\\text{giờ}.\", \"answer\": \"$6$ \\\\text{ km}\"}", true);
        assertTrue(sa.parseSuccess(), sa.parseError());
        assertEquals("$6$ km", sa.answer());
        assertEquals("Đi $2$ giờ.", sa.explanation());
        assertTrue(sa.parseWarnings().contains("json: đã sửa 1 \\text{} ngoài $ (đã gỡ vỏ) ở answer"),
                sa.parseWarnings().toString());

        ParsedTfDto tf = TfJsonParser.parse("{\"question_text\": \"Cho $v = 40$ \\\\text{km/h}.\","
                + " \"statement_a\": \"Đi $2$ \\\\text{giờ} được $80$ \\\\text{km}.\","
                + " \"statement_b\": \"Đúng.\", \"statement_c\": \"Sai.\", \"statement_d\": \"Đúng.\","
                + " \"explanation\": \"a) $v \\\\cdot t$ (\\\\text{km}).\","
                + " \"label_a\": \"T\", \"label_b\": \"T\", \"label_c\": \"F\", \"label_d\": \"T\"}", true);
        assertTrue(tf.parseSuccess(), tf.parseError());
        assertEquals("Cho $v = 40$ km/h.", tf.questionText());
        assertEquals("Đi $2$ giờ được $80$ km.", tf.statements().get(0));
        assertEquals("a) $v \\cdot t$ (km).", tf.explanation());
    }
}
