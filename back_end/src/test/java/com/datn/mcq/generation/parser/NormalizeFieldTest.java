package com.datn.mcq.generation.parser;

import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.dto.ParsedTfDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-07 và quy tắc mới (11/11): {@link LlmOutputRepair#normalizeField} là MỘT chuỗi chuẩn hóa dùng chung cho lúc lưu câu sinh
 * (parser), lúc giáo viên sửa ({@code QuestionEditService.Content.text}) và, qua {@code InlineArtifacts}, lúc xuất Word.
 */
class NormalizeFieldTest {

    private static String normalize(String field, String text, boolean tf, List<String> warnings) {
        return LlmOutputRepair.normalizeField(field, text, "explanation".equals(field), tf, warnings);
    }

    @Test
    void congThucHaiDauDolaThanhMotDauDolaKhiSuaCau() {
        List<String> w = new ArrayList<>();
        assertEquals("$x$", normalize("option_a", "$$x$$", false, w));
        assertEquals(1, w.size(), w.toString());
        assertTrue(w.get(0).startsWith("json: đã sửa 1 công thức $$...$$ thành $...$ ở option_a"), w.toString());
    }

    @Test
    void lenhLatexMatBackslashDuocThemLaiNhuLucLuu() {
        List<String> w = new ArrayList<>();
        assertEquals("Góc $70^\\circ$.", normalize("explanation", "Góc $70^circ$.", false, w));
        assertEquals(1, w.size(), w.toString());
        assertTrue(w.get(0).startsWith("json: đã sửa 1 lệnh LaTeX thiếu"), w.toString());
    }

    @Test
    void doubleBackslashGiuaCacCongThucThanhXuongDong() {
        List<String> w = new ArrayList<>();
        assertEquals("$x = 2$\n$y = 3$", normalize("explanation", "$x = 2$\\\\$y = 3$", false, w));
        assertTrue(w.stream().allMatch(x -> x.startsWith("json: đã sửa ")), w.toString());
    }

    @Test
    void dauCanLeNgoaiMoiTruongBiBo() {
        List<String> w = new ArrayList<>();
        assertEquals("$= 5x$", normalize("explanation", "$&= 5x$", false, w));
        assertTrue(w.get(0).startsWith("json: đã sửa 1 dấu & căn lề"), w.toString());
    }

    @Test
    void dongCongThucCuaLoiGiaiDuocBocNguyenDong() {
        List<String> w = new ArrayList<>();
        assertEquals("Ta có:\n$P = -5x^2(x - 3)$", normalize("explanation", "Ta có:\nP = -5x^2(x - 3)", false, w));
        // trường khác lời giải không bọc nguyên dòng
        List<String> w2 = new ArrayList<>();
        assertEquals("Ta có:\nP = -5x^2(x - 3)", normalize("option_a", "Ta có:\nP = -5x^2(x - 3)", false, w2));
    }

    @Test
    void cauDanDuocThemDauHoiVaBoDauHaiChamSauLa() {
        List<String> w = new ArrayList<>();
        assertEquals("Đơn thức $2x^1$ có bậc là", normalize("question_text", "Đơn thức $2x^1$ có bậc là:", false, w));
        assertTrue(w.get(0).startsWith("json: đã sửa dấu câu cuối question_text"), w.toString());
        assertEquals("Hệ số của $3x$ là bao nhiêu?", normalize("question_text", "Hệ số của $3x$ là bao nhiêu", false,
                new ArrayList<>()));
        // câu dẫn đúng/sai không bao giờ thêm "?", chỉ thêm "."
        assertEquals("Xét tính đúng sai của các ý sau.", normalize("question_text", "Xét tính đúng sai của các ý sau",
                true, new ArrayList<>()));
        // ý đúng/sai thiếu dấu kết câu thì thêm "."
        assertEquals("Biểu thức $x + 1$ luôn dương.", normalize("statement_a", "Biểu thức $x + 1$ luôn dương", true,
                new ArrayList<>()));
    }

    @Test
    void brMuiTenVaDoubleBackslashTrongCongThucKhongCoMoiTruong() {
        List<String> w = new ArrayList<>();
        assertEquals("Bước 1\nBước 2", normalize("explanation", "Bước 1<br>Bước 2", false, w));
        assertTrue(w.get(0).startsWith("json: đã sửa "), w.toString());

        w.clear();
        assertEquals("Ta có $x = 1 \\Rightarrow x^2 = 1$ và A ⇒ B.",
                normalize("explanation", "Ta có $x = 1 -> x^2 = 1$ và A -> B.", false, w));
        assertTrue(w.stream().allMatch(x -> x.startsWith("json: đã sửa ")), w.toString());

        w.clear();
        assertEquals("$a$\n$b$", normalize("explanation", "$a \\\\ b$", false, w));
        assertTrue(w.stream().allMatch(x -> x.startsWith("json: đã sửa ")), w.toString());

        w.clear();
        String cases = "$\\begin{cases} x = 1 \\\\ y = 2 \\end{cases}$";
        assertEquals(cases, normalize("explanation", cases, false, w), "có môi trường thì giữ nguyên");
        assertTrue(w.isEmpty(), w.toString());
    }

    @Test
    void damSoCauTraLoiNganLaSoTranKhongDoi() {
        List<String> w = new ArrayList<>();
        assertEquals("12,5", normalize("answer", "12,5", false, w));
        assertTrue(w.isEmpty(), w.toString());
    }

    @Test
    void idempotentTrenMoiDauVao() {
        String[] inputs = {
                "Góc $70^circ$.", "$x = 2$\\\\$y = 3$", "$&= 5x$", "Ta có:\nP = -5x^2(x - 3)", "Bước 1<br>Bước 2",
                "$x -> y$ và a => b", "$a \\\\ b \\\\ c$", "Cho $\\frac{1}{2}$ quên", "Tìm $x$ là:", "5x^{2} + 1"};
        for (String field : new String[] {"question_text", "option_a", "explanation", "statement_b"}) {
            for (String in : inputs) {
                String once = normalize(field, in, field.startsWith("statement"), new ArrayList<>());
                List<String> again = new ArrayList<>();
                assertEquals(once, normalize(field, once, field.startsWith("statement"), again), field + " | " + in);
                assertTrue(again.isEmpty(), field + " | " + in + " | lần hai còn cảnh báo " + again);
            }
        }
    }

    /** Bản sửa của giáo viên phải ra đúng như câu sinh: cùng một văn bản đi qua parser mcq và qua hàm dùng chung. */
    @Test
    void banSuaGiongHetCauSinhQuaParserMcq() {
        String[][] cases = {
                {"Đơn thức $2x^1$ có bậc là:", "$70^circ$", "$x = 2$\\\\$y = 3$", "$&= 5x$", "Bước 1<br>Bước 2<br/>$a \\\\ b$"},
                {"Tìm $x$ biết $x -> 1$", "$x => 2$", "A -> B", "$\\frac{1}{2}$", "Ta có \\frac{1}{2} ngoài"},
        };
        for (String[] c : cases) {
            String json = "{\"question_text\": " + q(c[0]) + ", \"option_a\": " + q(c[1]) + ", \"option_b\": " + q(c[2])
                    + ", \"option_c\": " + q(c[3]) + ", \"option_d\": " + q(c[4]) + ", \"correct_option\": \"A\","
                    + " \"explanation\": " + q(c[4]) + "}";
            ParsedMcqDto parsed = McqJsonParser.parse(json, true);
            assertEquals(parsed.questionText(), normalize("question_text", c[0], false, new ArrayList<>()));
            assertEquals(parsed.optionA(), normalize("option_a", c[1], false, new ArrayList<>()));
            assertEquals(parsed.optionB(), normalize("option_b", c[2], false, new ArrayList<>()));
            assertEquals(parsed.optionC(), normalize("option_c", c[3], false, new ArrayList<>()));
            assertEquals(parsed.optionD(), normalize("option_d", c[4], false, new ArrayList<>()));
            assertEquals(parsed.explanation(), normalize("explanation", c[4], false, new ArrayList<>()));
        }
    }

    @Test
    void banSuaGiongHetYDungSaiQuaParserTf() {
        String json = "{\"question_text\": " + q("Xét các ý sau") + ", \"statement_a\": " + q("$x -> 1$ luôn đúng")
                + ", \"statement_b\": " + q("B<br>C") + ", \"statement_c\": " + q("$70^circ$") + ", \"statement_d\": "
                + q("d") + ", \"label_a\": \"T\", \"label_b\": \"F\", \"label_c\": \"T\", \"label_d\": \"F\","
                + " \"explanation\": " + q("$a \\\\ b$") + "}";
        ParsedTfDto parsed = TfJsonParser.parse(json, true);
        assertEquals(parsed.questionText(), normalize("question_text", "Xét các ý sau", true, new ArrayList<>()));
        assertEquals(parsed.statements().get(0), normalize("statement_a", "$x -> 1$ luôn đúng", true, new ArrayList<>()));
        assertEquals(parsed.statements().get(1), normalize("statement_b", "B<br>C", true, new ArrayList<>()));
        assertEquals(parsed.statements().get(2), normalize("statement_c", "$70^circ$", true, new ArrayList<>()));
        assertEquals(parsed.explanation(), normalize("explanation", "$a \\\\ b$", true, new ArrayList<>()));
    }

    /** Chuỗi JSON của văn bản thô: nhân đôi "\" và thoát nháy, xuống dòng. */
    private static String q(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
