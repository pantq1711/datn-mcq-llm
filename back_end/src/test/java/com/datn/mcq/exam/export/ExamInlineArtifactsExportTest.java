package com.datn.mcq.exam.export;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 11/11: câu đã lưu từ trước có thể còn "&lt;br&gt;", mũi tên "-&gt;" / "=&gt;" viết bằng chữ, hoặc "\\" trong công thức không có
 * môi trường: file xuất (cùng bộ chuẩn hóa với lúc lưu và lúc sửa) phải sửa cả câu cũ. Không cần pandoc.
 */
class ExamInlineArtifactsExportTest {

    private static final ExamScoringProperties SCORING = new ExamScoringProperties(new BigDecimal("0.25"),
            new BigDecimal("0.5"), List.of(BigDecimal.ZERO, new BigDecimal("0.1"), new BigDecimal("0.25"),
            new BigDecimal("0.5"), new BigDecimal("1.0")));
    private static final ExamHeader HEADER = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    private static ExamViewDto view(String stem, String optionA, String explanation) {
        ExamItemDto item = new ExamItemDto(1, 1, 1, "I", "Đa thức", QuestionType.MCQ, "ready", "generated", null, 101,
                stem, optionA, "$2$", "$3$", "$4$", Option.B, null, null, explanation, Difficulty.EASY, false, List.of());
        return new ExamViewDto(5, "MT2611111200001", "Đề thử", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 11, 11, 12, 0), "ready", 0, List.of(new PartDto("I", 1, 1, List.of(item))), "matrix");
    }

    @Test
    void brNgoaiCongThucThanhXuongDongThatTrongFileXuat() {
        String md = ExamMarkdownBuilder.build(view("Cho đa thức.<br>Tìm bậc.", "$1$", "Bước 1<br/>Bước 2<br />Bước 3"),
                HEADER, SCORING, TODAY);

        assertFalse(md.contains("\\<br"), "thẻ <br> ngoài công thức còn nguyên (bị thoát thành chữ): " + md);
        assertTrue(md.contains("Bước 1\n\nBước 2\n\nBước 3"), md);
    }

    @Test
    void muiTenNgoaiCongThucThanhDauSuyRaVaTrongCongThucThanhRightarrow() {
        String md = ExamMarkdownBuilder.build(view("Cho $x = 1 -> y$.", "$a => b$", "Ta có x = 1 -> x^2 = 1, A => B."),
                HEADER, SCORING, TODAY);

        assertTrue(md.contains("$x = 1 \\Rightarrow y$"), md);
        assertTrue(md.contains("$a \\Rightarrow b$"), md);
        assertTrue(md.contains("⇒"), md);
        assertFalse(md.contains("-\\>"), md);
        assertFalse(md.contains("=\\>"), md);
    }

    @Test
    void gachGachVaMuiTenNguocGiuNguyenTrongFileXuat() {
        String md = ExamMarkdownBuilder.build(view("Ví dụ a --> b và c <- d.", "$1$", "Giải."), HEADER, SCORING, TODAY);

        // ngoài công thức, "<" và ">" được thoát bằng "\" khi xuất
        assertTrue(md.contains("a --\\> b") && md.contains("c \\<- d"), md);
        assertFalse(md.contains("⇒"), md);
    }

    @Test
    void doubleBackslashTrongCongThucKhongCoMoiTruongTachThanhCacCongThucRieng() {
        String md = ExamMarkdownBuilder.build(view("Cho $a \\\\ b$.", "$1$", "Ta có $x = 1 \\\\ y = 2$ nên xong."),
                HEADER, SCORING, TODAY);

        assertFalse(md.contains("\\\\"), md);
        assertTrue(md.contains("$x = 1$\n\n$y = 2$"), md);
    }

    @Test
    void congThucCoMoiTruongGiuNguyenKhiXuat() {
        String cases = "$\\begin{cases} x = 1 \\\\ y = 2 \\end{cases}$";
        String md = ExamMarkdownBuilder.build(view("Cho " + cases + ".", "$1$", "Giải."), HEADER, SCORING, TODAY);

        assertTrue(md.contains(cases), md);
    }
}
