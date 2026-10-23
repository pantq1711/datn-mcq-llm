package com.datn.mcq.exam.export;

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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Markdown của đề để pandoc chuyển sang Word (ngày 23/10, đợt 3, việc 3). Không cần pandoc. */
class ExamMarkdownBuilderTest {

    private static ExamItemDto mcq(int slotNo, String state, String text, String a, String b, String c, String d,
                                   Option correct, String explanation) {
        return new ExamItemDto(slotNo, 1, slotNo, "I", "Đa thức", QuestionType.MCQ, state, text == null ? null : "generated",
                null, text == null ? null : 100 + slotNo, text, a, b, c, d, correct, null, null, explanation,
                Difficulty.EASY, false, List.of());
    }

    private static ExamItemDto tf(String text, List<ItemStatementDto> statements, String explanation) {
        return new ExamItemDto(9, 2, 1, "II", "Hằng đẳng thức", QuestionType.TF, "kept", "bank", 7, null, text, null,
                null, null, null, null, null, statements, explanation, null, false, List.of());
    }

    private static ExamItemDto sa(String state, String text, String answer, String explanation) {
        return new ExamItemDto(10, 3, 1, "III", "Tứ giác", QuestionType.SA, state, text == null ? null : "generated",
                null, text == null ? null : 300, text, null, null, null, null, null, answer, null, explanation,
                Difficulty.MEDIUM, true, List.of("solver_inconsistent"));
    }

    private static ItemStatementDto st(StatementKey key, String text, TruthValue label) {
        return new ItemStatementDto(key, text, label, Difficulty.EASY);
    }

    private static ExamViewDto exam() {
        List<ExamItemDto> part1 = List.of(
                mcq(1, "ready", "Bậc của đơn thức $2x^3$ là:", "$1$", "$3$", "$0$", "$2$", Option.B,
                        "Số mũ của $x$ là $3$."),
                mcq(2, "failed", null, null, null, null, null, null, null),
                mcq(3, "generating", null, null, null, null, null, null, null));
        List<ExamItemDto> part2 = List.of(tf("Cho biểu thức $P = (x + 1)^2$.", List.of(
                st(StatementKey.A, "$P = x^2 + 2x + 1$.", TruthValue.TRUE),
                st(StatementKey.B, "Với $x = 0$ thì $P = 0$.", TruthValue.FALSE),
                st(StatementKey.C, "$P \\geq 0$ với mọi $x$.", TruthValue.TRUE),
                st(StatementKey.D, "Giá trị nhỏ nhất của $P$ là $1$.", TruthValue.FALSE)),
                "a) Khai triển.\nb) Thay $x = 0$ được $P = 1$."));
        List<ExamItemDto> part3 = List.of(sa("ready", "Tứ giác $ABCD$ có ba góc $80^\\circ$, $90^\\circ$, $100^\\circ$."
                + " Góc còn lại bằng bao nhiêu độ?", "90", "Tổng bốn góc là $360^\\circ$."));
        return new ExamViewDto(5, "MT2610231200001", "Đề giữa kì I", "teacher", ExamStatus.DRAFT, 3,
                LocalDateTime.of(2026, 10, 23, 12, 0), "partial", 1, List.of(
                        new PartDto("I", 3, 1, part1), new PartDto("II", 1, 1, part2), new PartDto("III", 1, 1, part3)));
    }

    @Test
    void dungCauTrucDeDapAnLoiGiai() {
        String expected = """
                # Đề giữa kì I

                **Mã đề:** MT2610231200001

                ## PHẦN I. Câu trắc nghiệm nhiều phương án lựa chọn

                **Câu 1.** Bậc của đơn thức $2x^3$ là:

                A. $1$

                B. $3$

                C. $0$

                D. $2$

                **Câu 2.** \\[Chưa có câu – cần sinh lại\\]

                **Câu 3.** \\[Chưa có câu – đang sinh\\]

                ## PHẦN II. Câu trắc nghiệm đúng sai

                **Câu 1.** Cho biểu thức $P = (x + 1)^2$.

                a\\) $P = x^2 + 2x + 1$.

                b\\) Với $x = 0$ thì $P = 0$.

                c\\) $P \\geq 0$ với mọi $x$.

                d\\) Giá trị nhỏ nhất của $P$ là $1$.

                ## PHẦN III. Câu trắc nghiệm trả lời ngắn

                **Câu 1.** Tứ giác $ABCD$ có ba góc $80^\\circ$, $90^\\circ$, $100^\\circ$. Góc còn lại bằng bao nhiêu độ?

                ```{=openxml}
                <w:p><w:r><w:br w:type="page"/></w:r></w:p>
                ```

                # ĐÁP ÁN

                ## PHẦN I

                Câu 1: B

                Câu 2: —

                Câu 3: —

                ## PHẦN II

                Câu 1: ĐSĐS

                ## PHẦN III

                Câu 1: 90

                # LỜI GIẢI

                ## PHẦN I

                **Câu 1.** Số mũ của $x$ là $3$.

                **Câu 2.** —

                **Câu 3.** —

                ## PHẦN II

                **Câu 1.** a\\) Khai triển.

                b\\) Thay $x = 0$ được $P = 1$.

                ## PHẦN III

                **Câu 1.** Tổng bốn góc là $360^\\circ$.
                """;
        assertEquals(expected, ExamMarkdownBuilder.build(exam()));
    }

    @Test
    void thoatKyTuMarkdownNgoaiCongThucGiuNguyenTrongCongThuc() {
        assertEquals("a\\*b\\_c \\#1 \\[x\\] $a_1 * b_2$ 5\\$ \\<br\\> \\@ref", ExamMarkdownBuilder.inline(
                "a*b_c #1 [x] $a_1 * b_2$ 5\\$ <br> @ref"));
        assertEquals("$x + 1$ và $y$", ExamMarkdownBuilder.inline("$ x + 1 $ và $y$"), "bỏ khoảng trắng sát dấu $");
        assertEquals("Giá \\$5 thôi", ExamMarkdownBuilder.inline("Giá $5 thôi"), "$ lẻ là chữ thường");
        assertEquals("\\\\frac12", ExamMarkdownBuilder.inline("\\frac12"), "LaTeX ngoài $ giữ nguyên chữ");
        assertEquals("1\\. Tính", ExamMarkdownBuilder.inline("1. Tính"), "không thành danh sách đánh số");
    }
}
