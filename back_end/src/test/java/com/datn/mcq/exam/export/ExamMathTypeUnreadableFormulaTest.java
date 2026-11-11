package com.datn.mcq.exam.export;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Xuất MathType khi có công thức pandoc/texmath không đọc được (11/11, đề 1 trên soande.app: "Số m:oMath (199) khác số
 * LaTeX (201)", cả file rơi về Equation). Công thức hỏng được ghi dạng chữ, các công thức còn lại vẫn thành MathType.
 * Cần pandoc; máy không có thì bỏ qua. Dữ liệu đề dựng tay, không gọi Gemini, không cần DB.
 */
class ExamMathTypeUnreadableFormulaTest {

    private static final ExamScoringProperties SCORING = new ExamScoringProperties(new BigDecimal("0.25"),
            new BigDecimal("0.5"), List.of(BigDecimal.ZERO, new BigDecimal("0.1"), new BigDecimal("0.25"),
            new BigDecimal("0.5"), new BigDecimal("1.0")));
    private static final ExamHeader HEADER = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, 45);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    /** Hai công thức lệch của đề 1 (câu 8, lời giải), nguyên văn trong file "Công thức Word". */
    static final String BAD_1 = "&= 5x \\cdot x - 5x \\cdot 2y";
    static final String BAD_2 = "&= 5x(x - 2y)";

    private static Optional<String> pandoc() {
        return PandocTestSupport.find();
    }

    private static int count(String xml, String regex) {
        return xml.split(regex, -1).length - 1;
    }

    // ---------------------------------------------------------------- dữ liệu đề 18 câu

    private static ExamItemDto mcq(int no, String stem, List<String> o, Option correct, String explanation) {
        return new ExamItemDto(no, 1, no, "I", "Đa thức", QuestionType.MCQ, "ready", "generated", null, 500 + no, stem,
                o.get(0), o.get(1), o.get(2), o.get(3), correct, null, null, explanation, Difficulty.EASY, false, List.of());
    }

    private static ExamItemDto tf(int no, String stem, String explanation) {
        String[] texts = {"Đồ thị hàm số $y = 2x + 1$ đi qua điểm $(0; 1)$", "Hệ số góc bằng $\\frac{1}{2}$",
                "Hàm số đồng biến trên $\\mathbb{R}$", "Với $x = 2$ thì $y = 5$"};
        TruthValue[] labels = {TruthValue.TRUE, TruthValue.FALSE, TruthValue.TRUE, TruthValue.TRUE};
        List<ItemStatementDto> statements = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            statements.add(new ItemStatementDto(StatementKey.values()[i], texts[i], labels[i], Difficulty.EASY));
        }
        return new ExamItemDto(20 + no, 2, no, "II", "Hàm số", QuestionType.TF, "ready", "generated", null, 600 + no, stem,
                null, null, null, null, null, null, statements, explanation, null, false, List.of());
    }

    private static ExamItemDto sa(int no, String stem, String answer, String explanation) {
        return new ExamItemDto(40 + no, 3, no, "III", "Tứ giác", QuestionType.SA, "ready", "generated", null, 700 + no,
                stem, null, null, null, null, null, answer, null, explanation, Difficulty.MEDIUM, false, List.of());
    }

    /**
     * Đề 18 câu (14 + 2 + 2) như đề giữa kì Toán 8; {@code explanation8} là lời giải câu 8 phần I (để thay bằng lời giải có
     * công thức hỏng).
     */
    static ExamViewDto exam18(String explanation8) {
        List<ExamItemDto> part1 = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            String explanation = i == 8 ? explanation8
                    : "Ta có $" + i + "x^2 - " + (2 * i) + "xy = " + i + "x(x - 2y)$, nên chọn đáp án đúng.";
            part1.add(mcq(i, "Phân tích đa thức $" + i + "x^2 - " + (2 * i) + "xy$ thành nhân tử ta được",
                    List.of("$" + i + "x(x - 2y)$", "$" + i + "x(x + 2y)$", "$x(" + i + "x - y)$",
                            "$\\frac{" + i + "}{2}x(x - y)$"), Option.A, explanation));
        }
        List<ExamItemDto> part2 = List.of(
                tf(1, "Cho hàm số $y = 2x + 1$.", "a) Thay $x = 0$ được $y = 1$.\nb) Hệ số góc là $2$.\n"
                        + "c) Vì $2 > 0$ nên hàm số đồng biến.\nd) Thay $x = 2$ được $y = 5$."),
                tf(2, "Cho hàm số $y = 2x + 1$.", "Xét từng ý theo định nghĩa hàm số bậc nhất $y = ax + b$."));
        List<ExamItemDto> part3 = List.of(
                sa(1, "Tính giá trị của $A = x^2 - 2x + 1$ tại $x = 11$.", "100", "Ta có $A = (x - 1)^2 = 10^2 = 100$."),
                sa(2, "Tứ giác $ABCD$ có $\\widehat{A} = 70^\\circ$, $\\widehat{B} = 80^\\circ$, $\\widehat{C} = 100^\\circ$. "
                        + "Tính số đo góc $D$ (độ).", "110", "Tổng bốn góc là $360^\\circ$ nên $\\widehat{D} = 110^\\circ$."));
        return new ExamViewDto(1, "MT2611111200001", "Giữa kì I – Toán 8", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 11, 11, 8, 0), "ready", 0,
                List.of(new PartDto("I", 14, 14, part1), new PartDto("II", 2, 2, part2), new PartDto("III", 2, 2, part3)),
                "matrix");
    }

    /** Lời giải câu 8 của đề không có công thức hỏng. */
    static final String CLEAN_EXPLANATION_8 = "Ta có đa thức $5x^2 - 10xy$. $= 5x \\cdot x - 5x \\cdot 2y$. $= 5x(x - 2y)$.";

    // ---------------------------------------------------------------- công thức hỏng ở mức PandocRunner

    /**
     * Markdown có đúng hai công thức lệch của đề 1 (không qua bước chuẩn hóa của ExamMarkdownBuilder): file vẫn là MathType,
     * không công thức nào phải giữ Equation; hai công thức hỏng nằm lại dạng chữ, đúng chỗ.
     */
    @Test
    void haiCongThucTexmathKhongDocDuocVanXuatMathType() throws Exception {
        assumeTrue(pandoc().isPresent(), "máy không có pandoc: bỏ qua");
        String md = "**Câu 8. Chọn D.**\n\nTa có đa thức $5x^2 - 10xy$. $" + BAD_1 + "$. $" + BAD_2 + "$.\n\n"
                + "**Câu 9.** Tính $\\frac{1}{2} + x^2$.\n";

        PandocRunner.Docx out = new PandocRunner(pandoc().get()).markdownToDocx(md, EquationFormat.MATHTYPE);
        String xml = PandocTestSupport.documentXml(out.content());

        assertEquals(0, out.equationsKeptOmml(), "không công thức nào giữ Equation");
        assertEquals(2, out.equationsFailed(), "hai công thức pandoc không đọc được");
        assertEquals(List.of(BAD_1, BAD_2), out.failedLatex());
        assertEquals(2, out.mathTypeObjects(), "hai công thức đọc được thành MathType");
        assertEquals(2, count(xml, "ProgID=\"Equation.DSMT4\""));
        assertEquals(0, count(xml, "<m:oMath[ >]"), "không còn Equation");
        assertTrue(xml.contains("$&amp;= 5x \\cdot x - 5x \\cdot 2y$") && xml.contains("$&amp;= 5x(x - 2y)$"),
                "công thức hỏng nằm lại dạng chữ");
        assertTrue(xml.indexOf("Ta có đa thức") < xml.indexOf("5x \\cdot x") && xml.indexOf("5x \\cdot x") < xml
                .indexOf("Câu 9."), "đúng thứ tự");
    }

    /** Lượt kiểm: đoạn có mã đánh dấu mà không có m:oMath là công thức hỏng; thiếu mã đánh dấu thì không đoán. */
    @Test
    void docDocxKiemTimCongThucHong() throws Exception {
        assumeTrue(pandoc().isPresent(), "máy không có pandoc: bỏ qua");
        byte[] probe = new PandocRunner(pandoc().get()).markdownToDocx(
                "MTPROBE0Q $x^2$\n\nMTPROBE1Q $" + BAD_1 + "$\n\nMTPROBE2Q $\\frac{1}$\n\nMTPROBE3Q $$y = 1$$\n");

        assertEquals(java.util.Set.of(1, 2), PandocRunner.unreadableInProbe(probe, 4));
        IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> PandocRunner.unreadableInProbe(probe, 5));
        assertTrue(ex.getMessage().contains("4/5"), ex.getMessage());
    }

    // ---------------------------------------------------------------- công thức hỏng đi qua ExamMarkdownBuilder

    /** Gốc lỗi đề 1: "&" căn lề trong lời giải đã lưu được bỏ khi xuất (không sửa DB), cho cả Equation và MathType. */
    @Test
    void loiGiaiDe1DaLuuDuocChuanHoaKhiXuat() {
        String stored = "Ta có đa thức $5x^2 - 10xy$. $" + BAD_1 + "$. $" + BAD_2 + "$.";
        String md = ExamMarkdownBuilder.build(exam18(stored), HEADER, SCORING, TODAY);

        assertFalse(md.contains("$&"), "không còn dấu căn lề ngoài môi trường");
        assertTrue(md.contains("$= 5x \\cdot x - 5x \\cdot 2y$") && md.contains("$= 5x(x - 2y)$"), md);
    }

    @Test
    void loiGiaiDe1DaLuuXuatMathTypeDuCongThuc() throws Exception {
        assumeTrue(pandoc().isPresent(), "máy không có pandoc: bỏ qua");
        String stored = "Ta có đa thức $5x^2 - 10xy$. $" + BAD_1 + "$. $" + BAD_2 + "$.";
        String md = ExamMarkdownBuilder.build(exam18(stored), HEADER, SCORING, TODAY);

        PandocRunner.Docx out = new PandocRunner(pandoc().get()).markdownToDocx(md, EquationFormat.MATHTYPE);
        assertEquals(0, out.equationsKeptOmml());
        assertEquals(0, out.equationsFailed());
        assertFalse(PandocTestSupport.documentXml(out.content()).contains("<m:oMath"));
    }

    /**
     * Hai công thức texmath không đọc được mà bước chuẩn hóa không sửa ("\frac{1}" ở phương án câu 3, "x^" ở lời giải câu
     * 12): file vẫn là MathType, Kept-Omml = 0, Failed = 2, vị trí là câu 3 và câu 12 phần I.
     */
    @Test
    void congThucHongKhongSuaDuocVanXuatMathTypeVaBaoDungCau() throws Exception {
        assumeTrue(pandoc().isPresent(), "máy không có pandoc: bỏ qua");
        ExamViewDto exam = withBrokenFormulas(exam18(CLEAN_EXPLANATION_8));
        String md = ExamMarkdownBuilder.build(exam, HEADER, SCORING, TODAY);

        PandocRunner.Docx out = new PandocRunner(pandoc().get()).markdownToDocx(md, EquationFormat.MATHTYPE);
        String xml = PandocTestSupport.documentXml(out.content());
        assertEquals(0, out.equationsKeptOmml(), "không công thức nào giữ Equation");
        assertEquals(2, out.equationsFailed());
        assertEquals(0, count(xml, "<m:oMath[ >]"));
        assertTrue(out.mathTypeObjects() > 100, "các công thức còn lại vẫn là MathType: " + out.mathTypeObjects());
        assertTrue(xml.contains("$\\frac{1}$") && xml.contains("$x^$"), "công thức hỏng ở dạng chữ");

        List<ExamExportService.FailedFormula> located = ExamExportService.locateFailed(exam, out.failedLatex());
        assertEquals(List.of("I.3", "I.12"), located.stream().map(ExamExportService.FailedFormula::position).toList());
        assertEquals(List.of(503, 512), located.stream().map(ExamExportService.FailedFormula::questionId).toList());
        assertEquals(List.of("option_d", "explanation"),
                located.stream().map(ExamExportService.FailedFormula::field).toList());
        assertEquals(List.of(1, 1), located.stream().map(ExamExportService.FailedFormula::ordinal).toList());
    }

    /** Xuất Equation không đổi gì: không báo số công thức hỏng (null), pandoc tự ghi công thức hỏng dạng chữ như cũ. */
    @Test
    void xuatEquationKhongBaoCongThucHong() throws Exception {
        assumeTrue(pandoc().isPresent(), "máy không có pandoc: bỏ qua");
        String md = ExamMarkdownBuilder.build(withBrokenFormulas(exam18(CLEAN_EXPLANATION_8)), HEADER, SCORING, TODAY);

        PandocRunner.Docx out = new PandocRunner(pandoc().get()).markdownToDocx(md, EquationFormat.OMML);
        assertEquals(null, out.equationsFailed());
        assertTrue(out.failedLatex().isEmpty());
    }

    /** Câu 3 có phương án D "\frac{1}" (thiếu mẫu số), câu 12 có lời giải "x^" (thiếu số mũ). */
    private static ExamViewDto withBrokenFormulas(ExamViewDto exam) {
        PartDto part1 = exam.parts().get(0);
        List<ExamItemDto> items = new ArrayList<>(part1.items());
        ExamItemDto c3 = items.get(2);
        items.set(2, new ExamItemDto(c3.slotId(), c3.cellId(), c3.slotNo(), c3.part(), c3.topic(), c3.questionType(),
                c3.state(), c3.source(), c3.bankItemId(), c3.questionId(), c3.questionText(), c3.optionA(), c3.optionB(),
                c3.optionC(), "$\\frac{1}$", c3.correctOption(), c3.saAnswer(), c3.statements(), c3.explanation(),
                c3.difficulty(), c3.needsReview(), c3.reviewReasons()));
        ExamItemDto c12 = items.get(11);
        items.set(11, new ExamItemDto(c12.slotId(), c12.cellId(), c12.slotNo(), c12.part(), c12.topic(),
                c12.questionType(), c12.state(), c12.source(), c12.bankItemId(), c12.questionId(), c12.questionText(),
                c12.optionA(), c12.optionB(), c12.optionC(), c12.optionD(), c12.correctOption(), c12.saAnswer(),
                c12.statements(), "Bình phương: $x^$ rồi rút gọn.", c12.difficulty(), c12.needsReview(),
                c12.reviewReasons()));
        List<PartDto> parts = new ArrayList<>(exam.parts());
        parts.set(0, new PartDto(part1.part(), part1.required(), part1.ready(), items));
        return new ExamViewDto(exam.examId(), exam.examCode(), exam.title(), exam.mode(), exam.status(), exam.matrixId(),
                exam.createdAt(), exam.buildStatus(), exam.needsReviewCount(), parts, exam.kind());
    }

    // ---------------------------------------------------------------- thời gian xuất đề 18 câu

    /**
     * Đo thời gian xuất MathType đề 18 câu không có công thức hỏng (1 lần chạy nóng + 3 lần đo). Không khẳng định ngưỡng thời
     * gian (máy chạy test khác nhau); số đo in ra với tiền tố [DO-THOI-GIAN] để so trước/sau.
     */
    @Test
    void xuatDe18CauKhongCoCongThucHongDoThoiGian() throws Exception {
        assumeTrue(pandoc().isPresent(), "máy không có pandoc: bỏ qua");
        PandocRunner runner = new PandocRunner(pandoc().get());
        String md = ExamMarkdownBuilder.build(exam18(CLEAN_EXPLANATION_8), HEADER, SCORING, TODAY);

        PandocRunner.Docx warm = runner.markdownToDocx(md, EquationFormat.MATHTYPE);
        assertEquals(0, warm.equationsKeptOmml());
        long[] ms = new long[3];
        for (int i = 0; i < ms.length; i++) {
            long t0 = System.nanoTime();
            PandocRunner.Docx out = runner.markdownToDocx(md, EquationFormat.MATHTYPE);
            ms[i] = (System.nanoTime() - t0) / 1_000_000;
            assertEquals(0, out.equationsKeptOmml());
            assertEquals(0, out.equationsFailed(), "không có công thức hỏng");
            assertTrue(out.failedLatex().isEmpty());
            assertFalse(PandocTestSupport.documentXml(out.content()).contains("<m:oMath"));
        }
        long[] sorted = ms.clone();
        Arrays.sort(sorted);
        System.out.println("[DO-THOI-GIAN] xuat MathType de 18 cau, " + warm.mathTypeObjects() + " cong thuc: "
                + Arrays.toString(ms) + " ms, trung vi " + sorted[1] + " ms");
    }
}
