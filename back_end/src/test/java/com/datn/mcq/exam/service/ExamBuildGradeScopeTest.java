package com.datn.mcq.exam.service;

import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.llm.prompt.MatrixCellSpec;
import com.datn.mcq.llm.prompt.MatrixPromptDraftV0;
import com.datn.mcq.matrix.entity.ExamMatrix;
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Khối "PHẠM VI KIẾN THỨC" (8/11) có trong mọi đường sinh câu của giáo viên: tạo đề theo ma trận, tạo đề theo bài học,
 * sinh lại tự động, Đổi câu → Soạn câu mới (có hoặc không có hướng dẫn thêm), ô có tài liệu. Cả sáu đường dựng thông số ô bằng
 * {@link ExamBuildService#spec} (lớp lấy từ ma trận) rồi dựng prompt bằng {@link MatrixPromptDraftV0}, nên test dựng đúng
 * hai bước đó.
 */
class ExamBuildGradeScopeTest {

    private static final String HEADER = "PHẠM VI KIẾN THỨC:";
    private static final String TOAN_8_LINE = "- Toán 8: KHÔNG dùng bất đẳng thức";

    private static ExamMatrix matrix(String kind, Integer grade) {
        ExamMatrix m = new ExamMatrix();
        m.setKind(kind);
        m.setName("Ma trận thử");
        m.setGradeLevel(grade);
        return m;
    }

    private static ExamMatrixCell cell(QuestionType type) {
        ExamMatrixCell c = new ExamMatrixCell();
        c.setQuestionType(type);
        c.setTopic("Biểu thức");
        c.setContent("Giá trị của biểu thức");
        c.setDifficulty(Difficulty.HARD);
        return c;
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @Test
    void moiDuongSinhCauLayLopTuMaTranVaCoKhoiPhamVi() {
        for (String kind : List.of("exam", ExamMatrix.KIND_LESSON)) {
            for (QuestionType type : List.of(QuestionType.MCQ, QuestionType.SA)) {
                MatrixCellSpec spec = ExamBuildService.spec(cell(type), matrix(kind, 8), new Subject("Toán"), null);
                assertEquals(8, spec.gradeLevel(), kind);
                String p = MatrixPromptDraftV0.buildGenerationPrompt(spec);
                assertEquals(1, count(p, HEADER), kind + "/" + type);
                assertTrue(p.contains("Chỉ dùng kiến thức của lớp 8 đã học đến hết bài"), p);
                assertTrue(p.contains(TOAN_8_LINE), p);
            }
        }
    }

    @Test
    void doiCauSoanCauMoiCoVaKhongCoHuongDanThemVanCoDungMotKhoiTruocKhoiCuaGiaoVien() {
        MatrixCellSpec withExtra = ExamBuildService.spec(cell(QuestionType.MCQ), matrix("exam", 8), new Subject("Toán"),
                "Dùng ngữ cảnh mua sắm");
        String teacher = MatrixPromptDraftV0.teacherBlock("Chỉ dùng số nguyên");
        String doc = "[Tài liệu 1, đoạn 1]\nBình phương của một tổng là $(a+b)^2 = a^2 + 2ab + b^2$.";
        for (String note : new String[] {null, "Đây là câu thứ 1/2 của ô này.", "Đây là câu thứ 1/2 của ô này.\n" + teacher}) {
            for (String material : new String[] {null, doc}) {
                String p = MatrixPromptDraftV0.buildGenerationPrompt(withExtra, List.of(), note, material);
                assertEquals(1, count(p, HEADER), "note=" + note + ", tài liệu=" + (material != null));
                assertTrue(p.contains(TOAN_8_LINE));
                // khối phạm vi là quy tắc của hệ thống: đứng trước tài liệu, hướng dẫn thêm và yêu cầu của lượt Đổi câu
                assertTrue(p.indexOf(HEADER) < p.indexOf("Yêu cầu:"), p);
                if (material != null) {
                    assertTrue(p.indexOf(HEADER) < p.indexOf("<<<TÀI_LIỆU"), p);
                }
                assertTrue(p.indexOf(HEADER) < p.indexOf("Dùng ngữ cảnh mua sắm"), p);
                if (note != null && note.contains("Chỉ dùng số nguyên")) {
                    assertTrue(p.indexOf(HEADER) < p.indexOf("Chỉ dùng số nguyên"), p);
                }
            }
        }
        MatrixCellSpec without = ExamBuildService.spec(cell(QuestionType.MCQ), matrix("exam", 8), new Subject("Toán"), null);
        assertEquals(1, count(MatrixPromptDraftV0.buildGenerationPrompt(without, List.of(), teacher), HEADER));
    }

    @Test
    void maTranKhongCoLopThiKhongCoKhoiVaKhongLoi() {
        for (String kind : List.of("exam", ExamMatrix.KIND_LESSON)) {
            MatrixCellSpec spec = ExamBuildService.spec(cell(QuestionType.MCQ), matrix(kind, null), new Subject("Toán"), null);
            String p = MatrixPromptDraftV0.buildGenerationPrompt(spec);
            assertFalse(p.contains(HEADER), kind);
            assertFalse(p.contains("lớp null"), kind);
        }
    }
}
