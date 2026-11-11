package com.datn.mcq.generation.parser;

import com.datn.mcq.qc.service.QcSummaryService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-02 (rà soát 11/11): lưu câu đổi "$$...$$" về "$...$" (xuất Word không hiểu "$$"); cảnh báo thuộc nhóm tự sửa
 * ("json: đã sửa", không bật cờ "cần xem kỹ").
 */
class DisplayMathDollarsParserTest {

    @Test
    void loiGiaiCoCongThucHaiDauDolaDuocDoiKhiLuuCanhBaoTuSua() {
        List<String> warnings = new ArrayList<>();
        String out = LlmOutputRepair.textField("explanation", "Ta có $$x^2-4=0$$ nên $x = 2$.", true, true, warnings);

        assertEquals("Ta có $x^2-4=0$ nên $x = 2$.", out);
        assertEquals(List.of("json: đã sửa 1 công thức $$...$$ thành $...$ ở explanation"), warnings);
        assertTrue(QcSummaryService.isAutoFixedWarning(warnings.get(0)), "không bật cờ");
    }

    @Test
    void cauDanVaPhuongAnCungDuocDoiCheDoKhongSuaKhongDoi() {
        List<String> warnings = new ArrayList<>();
        assertEquals("a $x$ b $y$", LlmOutputRepair.textField("stem", "a $$x$$ b $y$", true, false, warnings));
        assertEquals(1, warnings.size(), warnings.toString());

        assertEquals("$$x$$", LlmOutputRepair.textField("stem", "$$x$$", false, true, new ArrayList<>()));
    }
}
