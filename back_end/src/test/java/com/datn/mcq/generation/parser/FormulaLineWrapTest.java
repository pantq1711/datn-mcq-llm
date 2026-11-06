package com.datn.mcq.generation.parser;

import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.service.QcSummaryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dòng công thức của lời giải nằm ngoài $ (4/11, đo đề 16 ô 37): quy tắc "mỗi bước một dòng" làm model viết dòng
 * {@code P = -5x^2(x - 3)} không có $. Parser bọc NGUYÊN dòng bằng $...$; dòng đã bị chia bởi $ giữa biểu thức
 * ({@code P $= ... \cdot$ (-3)}) thì không tự sửa mà bị bộ kiểm tra định dạng báo lỗi để câu được sinh lại.
 */
class FormulaLineWrapTest {

    /** Lời giải thật của đề 16 ô 37 (câu Phần I số 1), nguyên văn dòng 2 và dòng 3 là hai dòng cần xử lý. */
    private static final String CELL_37_LINE_2 = "P = -5x^2(x - 3)";
    private static final String CELL_37_LINE_3 = "P $= (-5x^2) \\cdot x + (-5x^2) \\cdot$ (-3)";
    private static final String CELL_37_LINE_4 = "P = -5x^3 + 15x^2.";

    @Test
    void dongCongThucKhongCoDollarBiBocNguyenDong() {
        LlmOutputRepair.FormulaLineRepair r = LlmOutputRepair.wrapFormulaLines(
                "Ta có:\n" + CELL_37_LINE_2 + "\n" + CELL_37_LINE_4);
        assertEquals("Ta có:\n$P = -5x^2(x - 3)$\n$P = -5x^3 + 15x^2$.", r.text(),
                "bọc cả dòng, dấu chấm cuối nằm ngoài $");
        assertEquals(2, r.wrapped());
    }

    @Test
    void dongBatDauBangDauBangCungBocNguyenDong() {
        LlmOutputRepair.FormulaLineRepair r = LlmOutputRepair.wrapFormulaLines(
                "Ta có $4x^2 - 16 = 4(x^2 - 4)$\n= 4(x^2 - 2^2)\n= 4(x - 2)(x + 2).");
        assertEquals("Ta có $4x^2 - 16 = 4(x^2 - 4)$\n$= 4(x^2 - 2^2)$\n$= 4(x - 2)(x + 2)$.", r.text());
        assertEquals(2, r.wrapped());
    }

    @Test
    void dongCoLenhLaTeXKhongBocTungManh() {
        LlmOutputRepair.FormulaLineRepair r = LlmOutputRepair.wrapFormulaLines(
                "P = (-5x^2) \\cdot x + (-5x^2) \\cdot (-3)");
        assertEquals("$P = (-5x^2) \\cdot x + (-5x^2) \\cdot (-3)$", r.text());
        assertEquals(1, r.wrapped());
    }

    @Test
    void dongCoChuTiengVietHoacTuTiengAnhKhongDuocBoc() {
        String text = "Ta có P = 5x^2 nên kết quả đúng.\nVậy x = 2 là nghiệm.\nTa có x^2 = 4\nDo đó x = 2";
        LlmOutputRepair.FormulaLineRepair r = LlmOutputRepair.wrapFormulaLines(text);
        assertEquals(text, r.text(), "dòng có chữ (kể cả \"Ta\", \"Do\" không dấu) không phải dòng công thức");
        assertEquals(0, r.wrapped());
    }

    @Test
    void dongDaCoDollarVaDongKhongCoKyHieuToanGiuNguyen() {
        String text = "Ta có:\n$(11x - 6)^2 = (11x)^2$\n$= 121x^2 - 132x + 36$.\nKết quả là 5\n\n12";
        LlmOutputRepair.FormulaLineRepair r = LlmOutputRepair.wrapFormulaLines(text);
        assertEquals(text, r.text(), "dòng đã có $, dòng chỉ có số hoặc chữ không bị đụng");
        assertEquals(0, r.wrapped());
    }

    // ---------- công thức $…$ trải qua nhiều dòng (đo đề 17 ô I-1) ----------

    @Test
    void dongNamGiuaMotCapDollarMoQuaDongKhongBiBocThem() {
        // Một cặp $ mở ở dòng 2 và đóng ở dòng 4: dòng 3 nằm TRONG công thức, bọc thêm sẽ lồng $ vào $.
        String text = "Ta có:\n$P = 6x^2 \\cdot x\n= 6x^3 - 24x\n= 6x^3$.";
        LlmOutputRepair.FormulaLineRepair r = LlmOutputRepair.wrapFormulaLines(text);
        assertEquals(text, r.text());
        assertEquals(0, r.wrapped());
    }

    @Test
    void vanBanCoDollarKepGiuNguyen() {
        String text = "Ta có:\n$$\nx = 2\n$$";
        assertEquals(text, LlmOutputRepair.wrapFormulaLines(text).text(), "công thức riêng dòng $$: không chắc, giữ nguyên");
    }

    @Test
    void dongCongThucCoDollarLeLaDongBiChia() {
        // Đề 17 ô I-1: "$= 6x^2 \cdot x - 6x^2 \cdot 4" mở ở dòng 2, "= 6x^3 - 24x^2$." đóng ở dòng 3. Word tách theo dòng nên
        // mỗi dòng có một $ lẻ và hiện chữ "$".
        assertTrue(LlmOutputRepair.hasBrokenFormulaLine(
                "$P = 6x^2(x - 4)$\n$= 6x^2 \\cdot x - 6x^2 \\cdot 4\n= 6x^3 - 24x^2$."));
        assertTrue(LlmOutputRepair.hasBrokenFormulaLine("$P = 6x^2(x - 4)$\n$= 6x^2 \\cdot x - 6x^2 \\cdot 4$\n= 6x^3 - 24x^2$."));
        assertFalse(LlmOutputRepair.hasBrokenFormulaLine("Giá là 5$ cho mỗi món"), "dòng có chữ: bộ kiểm tra LaTeX lo");
    }

    // ---------- dòng đã bị chia bởi $ giữa biểu thức ----------

    @Test
    void dongBiChiaBoiDollarGiuaBieuThucDuocNhanRa() {
        assertTrue(LlmOutputRepair.hasBrokenFormulaLine(
                "Ta có:\n" + CELL_37_LINE_2 + "\n" + CELL_37_LINE_3 + "\n" + CELL_37_LINE_4), "dòng 3 của ô 37");
        assertTrue(LlmOutputRepair.hasBrokenFormulaLine("$x$ = 3"));
        assertTrue(LlmOutputRepair.hasBrokenFormulaLine("$a + b$ = $c$ + 1"));
    }

    @Test
    void dongBinhThuongKhongBiCoiLaBiChia() {
        assertFalse(LlmOutputRepair.hasBrokenFormulaLine("Gọi số đo các góc $H$, $I$, $K$ lần lượt là $2x$, $3x$, $4x$ (với $x > 0$)."));
        assertFalse(LlmOutputRepair.hasBrokenFormulaLine("$= 121x^2 - 132x + 36$."));
        assertFalse(LlmOutputRepair.hasBrokenFormulaLine("$P = -5x^3 + 15x^2$."));
        assertFalse(LlmOutputRepair.hasBrokenFormulaLine("$x$, $y$ và $z$"));
        assertFalse(LlmOutputRepair.hasBrokenFormulaLine("Ta có $2x$ cm"), "dòng có chữ không phải dòng công thức");
        assertFalse(LlmOutputRepair.hasBrokenFormulaLine("P = -5x^2(x - 3)"), "không có $ nào: việc của bước bọc");
        assertFalse(LlmOutputRepair.hasBrokenFormulaLine(null));
    }

    // ---------- qua parser (chế độ production) và bộ kiểm tra định dạng ----------

    private static String mcqJson(String explanationJsonValue) {
        return "{\"question_text\": \"Thu gọn $P = -5x^2(x - 3)$, ta được\", \"option_a\": \"$5x^3 - 15x^2$\","
                + " \"option_b\": \"$-5x^3 - 15x^2$\", \"option_c\": \"$-5x^3 + 15x^2$\","
                + " \"option_d\": \"$5x^3 + 15x^2$\", \"correct_option\": \"C\","
                + " \"explanation\": \"" + explanationJsonValue + "\"}";
    }

    @Test
    void parserBocDongCongThucVaGhiCanhBaoKhongBatCo() {
        ParsedMcqDto p = McqJsonParser.parse(
                mcqJson("Ta có:\\nP = -5x^2(x - 3)\\nP = -5x^3 + 15x^2."), true);
        assertTrue(p.parseSuccess(), p.parseError());
        assertEquals("Ta có:\n$P = -5x^2(x - 3)$\n$P = -5x^3 + 15x^2$.", p.explanation());
        String warning = "explanation: đã tự bọc $ quanh 2 dòng công thức nằm ngoài $";
        assertTrue(p.parseWarnings().contains(warning), p.parseWarnings().toString());
        assertTrue(QcSummaryService.isAutoFixedWarning(warning), "tự sửa chắc chắn: không bật cờ");
    }

    @Test
    void parserKhongDungToiDongCongThucKhiChiDoChuaNghiemLoaiKhac() {
        ParsedMcqDto strict = McqJsonParser.parse(
                mcqJson("Ta có:\\nP = -5x^2(x - 3)"), false);
        assertEquals("Ta có:\nP = -5x^2(x - 3)", strict.explanation(), "chế độ chặt (không sửa) giữ nguyên");
    }

    @Test
    void doOTamSauMuoiBaDongBiChiaLaLoiDinhDangKhiLaDauRaLlm() {
        String explanation = "Ta có:\n" + CELL_37_LINE_2 + "\n" + CELL_37_LINE_3 + "\n" + CELL_37_LINE_4;
        ParsedMcqDto p = McqJsonParser.parse(
                mcqJson(explanation.replace("\\", "\\\\").replace("\n", "\\n")), true);
        assertTrue(p.parseSuccess(), p.parseError());
        assertTrue(LlmOutputRepair.hasBrokenFormulaLine(p.explanation()),
                "parser không tự sửa dòng không chắc: " + p.explanation());
        FormatChecker checker = new FormatChecker();
        FormatChecker.FormatCheckOutcome llm = checker.check(input(p).withLlmOutput(true));
        assertFalse(llm.passed());
        assertTrue(llm.errors().contains("explanation: " + LlmOutputRepair.MSG_FORMULA_LINE_BROKEN), llm.errors().toString());
        FormatChecker.FormatCheckOutcome teacher = checker.check(input(p).withLlmOutput(false));
        assertFalse(teacher.errors().stream().anyMatch(e -> e.contains(LlmOutputRepair.MSG_FORMULA_LINE_BROKEN)),
                "chữ giáo viên viết hoặc sửa không bị chặn bởi lỗi này");
    }

    @Test
    void lopLoiDinhDangKhongXayRaVoiLoiGiaiBinhThuong() {
        ParsedMcqDto p = McqJsonParser.parse(mcqJson("Ta có $P = -5x^3 + 15x^2$."), true);
        FormatChecker.FormatCheckOutcome llm = new FormatChecker().check(input(p).withLlmOutput(true));
        assertTrue(llm.passed(), llm.errors().toString());
    }

    private static QuestionQcInput input(ParsedMcqDto p) {
        return new QuestionQcInput(1, true, p.questionText(), p.optionA(), p.optionB(), p.optionC(), p.optionD(),
                p.correctOption(), p.explanation(), true, false, null).withParseWarnings(p.parseWarnings());
    }
}
