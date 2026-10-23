package com.datn.mcq.review.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Việc 2 (22/10): khi chép vào bank_item, lời giải còn lỗi định dạng LaTeX nghiêm trọng thì lưu NULL;
 * lời giải hợp lệ (kể cả chỉ có cảnh báo) được giữ nguyên. Hàm thuần, không cần DB.
 */
class BankExplanationPolicyTest {

    @Test
    void explanationWithFatalLatexErrorIsDroppedForMathSubjects() {
        assertNull(TeacherReviewService.explanationForBank("Toán Đại số 8", "Lời giải hỏng $x = 2"));
        assertNull(TeacherReviewService.explanationForBank("Toán", "Rút gọn $\f" + "rac{1}{2}$")); // \f = form feed
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
