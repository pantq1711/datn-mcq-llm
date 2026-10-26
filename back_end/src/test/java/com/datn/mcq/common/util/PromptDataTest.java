package com.datn.mcq.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** W2-3 bước 5b: dữ liệu của giáo viên/tài liệu đưa vào prompt không được chứa ranh giới khối {@code <<<} và {@code >>>}. */
class PromptDataTest {

    @Test
    void boRanhGioiKheVaKhongTaoRanhGioiMoiKhiGhepLai() {
        assertEquals("Dùng ngữ cảnh mua sắm", PromptData.neutralize("Dùng <<<ngữ cảnh>>> mua sắm"));
        String tricky = "<<<<<< a >>>>>> <<>>>> <<<>>>";
        assertFalse(PromptData.neutralize(tricky).contains("<<<"));
        assertFalse(PromptData.neutralize(tricky).contains(">>>"));
        assertFalse(PromptData.neutralize("<<<<>>>>").contains("<<<"), "ghép lại sau khi bỏ cũng không ra ranh giới");
    }

    @Test
    void giuNguyenCongThucVaKyTuThuongVaChiuNull() {
        String text = "Cho $a<b$ và $c>d$, $x \\ge y$; a << b, c >> d.";
        assertEquals(text, PromptData.neutralize(text));
        assertEquals("", PromptData.neutralize(null));
    }
}
