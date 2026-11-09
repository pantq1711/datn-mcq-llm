package com.datn.mcq.bankimport.mtef;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Kiểm thử phương thức làm sạch chuỗi LaTeX {@link MtefToLatex#clean(String)}.
 */
class MtefCleanTest {

    @Test
    void dauXuongDongKhongBiXoaVaGachCheoDungLeBiLoaiBo() {
        // Nhóm A: \\ xuống dòng phải giữ nguyên
        assertEquals("x\\\\", MtefToLatex.clean("x\\\\"));
        assertEquals("x\\\\", MtefToLatex.clean("x\\\\ "));
        assertEquals("x\\\\", MtefToLatex.clean("x\\\\\\ "));
        // Dấu \ đứng lẻ và các lệnh khoảng trắng cuối phải bị loại bỏ
        assertEquals("220^{\\circ}", MtefToLatex.clean("220^{\\circ}\\"));
        assertEquals("220^{\\circ}", MtefToLatex.clean("220^{\\circ}\\ "));
        assertEquals("a", MtefToLatex.clean("a\\quad "));
        assertEquals("a", MtefToLatex.clean("a\\,"));
        assertEquals("a", MtefToLatex.clean("a\\;"));
    }
}

