package com.datn.mcq.review.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Khi chép vào bank_item, lời giải luôn được giữ nguyên (đợt A bổ sung 4, ngày 23/10; thay quy tắc việc 2 ngày 22/10
 * từng lưu NULL khi lời giải còn lỗi LaTeX nghiêm trọng). Lỗi LaTeX trong lời giải chỉ là cảnh báo. Hàm thuần.
 */
class BankExplanationPolicyTest {

    @Test
    void explanationWithLatexErrorIsKeptForMathSubjects() {
        assertEquals("Lời giải hỏng $x = 2", TeacherReviewService.explanationForBank("Toán Đại số 8",
                "Lời giải hỏng $x = 2"));
        String outsideMath = "Ta có \\frac{5x + 3}{y} khi $y \\neq 0$.";
        assertEquals(outsideMath, TeacherReviewService.explanationForBank("Toán", outsideMath),
                "lệnh LaTeX ngoài $...$ (đề 2, chỗ 20)");
    }

    @Test
    void validExplanationIsKept() {
        String valid = "Nhân hai vế với $2$ được $x = 2$.\nVậy chọn A.";
        assertEquals(valid, TeacherReviewService.explanationForBank("Toán Đại số 8", valid));
    }

    @Test
    void explanationWithOnlyWarningsIsKept() {
        String warningOnly = "Ta có $\\widehat{ABC} = 60^\\circ$.";
        assertEquals(warningOnly, TeacherReviewService.explanationForBank("Toán Hình 8", warningOnly));
    }

    @Test
    void nullOrBlankStaysAsIsAndNonMathSubjectsAreNotValidated() {
        assertNull(TeacherReviewService.explanationForBank("Toán", null));
        assertEquals("Có $ lẻ", TeacherReviewService.explanationForBank("Ngữ văn", "Có $ lẻ"));
    }
}
