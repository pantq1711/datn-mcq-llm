package com.datn.mcq.generation.service;

import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.generation.dto.QuestionEditInput;
import com.datn.mcq.generation.dto.QuestionEditInput.StatementEdit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-07: bản giáo viên sửa ({@code QuestionEditService.Content.fromInput}) đi qua ĐÚNG chuỗi chuẩn hóa như lúc lưu câu sinh
 * (không còn thiếu LiteralNewlines, MissingLatexBackslash, AlignmentAmpersand, wrapFormulaLines, StemPunctuation), nên web và
 * Word hiện cùng một chữ. Không cần DB.
 */
class QuestionEditNormalizeTest {

    private static QuestionEditService.Content mcq(String stem, String a, String explanation, List<String> repairs) {
        return QuestionEditService.Content.fromInput(QuestionType.MCQ,
                new QuestionEditInput(stem, a, "$2$", "$3$", "$4$", "B", null, null, explanation), repairs);
    }

    @Test
    void goCircThieuBackslashDuocSuaNhuCauSinh() {
        List<String> repairs = new ArrayList<>();
        QuestionEditService.Content c = mcq("Cho $\\widehat{A} = 70^circ$. Tìm $x$.", "$70^circ$",
                "Ta có $90^circ$.", repairs);

        assertEquals("Cho $\\widehat{A} = 70^\\circ$. Tìm $x$.", c.questionText);
        assertEquals("$70^\\circ$", c.optionA);
        assertEquals("Ta có $90^\\circ$.", c.explanation);
        assertTrue(repairs.stream().allMatch(r -> r.startsWith("json: đã sửa ")), repairs.toString());
        assertTrue(repairs.stream().anyMatch(r -> r.contains("lệnh LaTeX thiếu")), repairs.toString());
    }

    @Test
    void doubleBackslashGiuaCongThucThanhXuongDong() {
        List<String> repairs = new ArrayList<>();
        QuestionEditService.Content c = mcq("Tìm $x$.", "$1$", "$x = 2$\\\\$y = 3$", repairs);

        assertEquals("$x = 2$\n$y = 3$", c.explanation);
        assertTrue(repairs.stream().allMatch(r -> r.startsWith("json: đã sửa ")), repairs.toString());
    }

    @Test
    void dauCanLeNgoaiMoiTruongBiBoKhiSua() {
        List<String> repairs = new ArrayList<>();
        QuestionEditService.Content c = mcq("Tìm $x$.", "$1$", "$&= 5x$", repairs);

        assertEquals("$= 5x$", c.explanation);
    }

    @Test
    void dongCongThucCuaLoiGiaiDuocBocKhiSua() {
        QuestionEditService.Content c = mcq("Tìm $x$.", "$1$", "Ta có:\nP = -5x^2(x - 3)", new ArrayList<>());

        assertEquals("Ta có:\n$P = -5x^2(x - 3)$", c.explanation);
    }

    @Test
    void cauDanDuocChuanHoaDauCauKhiSua() {
        QuestionEditService.Content c = mcq("Đơn thức $2x^1$ có bậc là:", "$1$", "Giải thích.", new ArrayList<>());
        assertEquals("Đơn thức $2x^1$ có bậc là", c.questionText);

        QuestionEditService.Content c2 = mcq("Hệ số của $3x$ là bao nhiêu", "$1$", "Giải thích.", new ArrayList<>());
        assertEquals("Hệ số của $3x$ là bao nhiêu?", c2.questionText);
    }

    @Test
    void yDungSaiVaCauDanTfDuocChuanHoa() {
        List<String> repairs = new ArrayList<>();
        QuestionEditService.Content c = QuestionEditService.Content.fromInput(QuestionType.TF,
                new QuestionEditInput("Xét các ý sau", null, null, null, null, null, null, List.of(
                        new StatementEdit("a", "$70^circ$ là góc nhọn", "T"), new StatementEdit("b", "$b$.", "F"),
                        new StatementEdit("c", "$c$.", "T"), new StatementEdit("d", "$d$.", "F")), "Giải $x -> y$."),
                repairs);

        assertEquals("Xét các ý sau.", c.questionText, "câu dẫn tf thêm '.', không thêm '?'");
        assertEquals("$70^\\circ$ là góc nhọn.", c.statements.get(0));
        assertEquals("Giải $x \\Rightarrow y$.", c.explanation);
        assertTrue(repairs.stream().allMatch(r -> r.startsWith("json: đã sửa ")), repairs.toString());
    }

    @Test
    void quyTacMoiBrMuiTenVaDoubleBackslashTrongCongThuc() {
        List<String> repairs = new ArrayList<>();
        QuestionEditService.Content c = mcq("Tìm $x$.", "$a \\\\ b$", "Bước 1<br>Bước 2, A -> B.", repairs);

        assertEquals("$a$\n$b$", c.optionA);
        assertEquals("Bước 1\nBước 2, A ⇒ B.", c.explanation);
    }

    @Test
    void damSoSaGiuNguyenLaSoTran() {
        QuestionEditService.Content c = QuestionEditService.Content.fromInput(QuestionType.SA,
                new QuestionEditInput("Tính $x$.", null, null, null, null, null, "12,5", null, "Giải."), new ArrayList<>());

        assertEquals("12,5", c.saAnswer);
    }
}
