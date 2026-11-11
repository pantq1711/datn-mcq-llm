package com.datn.mcq.generation.parser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lưu câu (11/11, đề 1 câu 8): lời giải viết từng bước "&= ..." như trong aligned. Bước bọc dòng công thức biến dòng đó thành
 * "$&= ...$" mà texmath không đọc được; dấu "&" phải được bỏ ngay khi lưu, cảnh báo thuộc nhóm tự sửa (không bật cờ).
 */
class AlignmentAmpersandParserTest {

    @Test
    void dongLoiGiaiBatDauBangDauCanLeDuocBocVaBoDauKhiLuu() {
        List<String> warnings = new ArrayList<>();
        String out = LlmOutputRepair.textField("explanation",
                "Ta có đa thức $5x^2 - 10xy$.\\n&= 5x \\cdot x - 5x \\cdot 2y.\\n&= 5x(x - 2y).", true, true, warnings);

        assertEquals("Ta có đa thức $5x^2 - 10xy$.\n$= 5x \\cdot x - 5x \\cdot 2y$.\n$= 5x(x - 2y)$.", out);
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith(LlmOutputRepair.ESCAPE_WARNING_PREFIX + "2 dấu & căn lề")),
                warnings.toString());
    }

    @Test
    void congThucDaCoDauCanLeTrongDollarDuocSuaKhiLuu() {
        List<String> warnings = new ArrayList<>();
        String out = LlmOutputRepair.textField("explanation",
                "Ta có đa thức $5x^2 - 10xy$. $&= 5x \\cdot x - 5x \\cdot 2y$. $&= 5x(x - 2y)$.", true, true, warnings);

        assertEquals("Ta có đa thức $5x^2 - 10xy$. $= 5x \\cdot x - 5x \\cdot 2y$. $= 5x(x - 2y)$.", out);
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void moiTruongAlignedGiuNguyenVaCheDoKhongSuaKhongDoi() {
        String aligned = "$\\begin{aligned} 5x^2 - 10xy &= 5x \\cdot x - 5x \\cdot 2y \\\\ &= 5x(x - 2y) \\end{aligned}$";
        List<String> warnings = new ArrayList<>();
        assertEquals(aligned, LlmOutputRepair.textField("explanation", aligned, true, true, warnings));
        assertTrue(warnings.isEmpty(), warnings.toString());

        assertEquals("$&= 5x$", LlmOutputRepair.textField("explanation", "$&= 5x$", false, true, new ArrayList<>()));
    }
}
