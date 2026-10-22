package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.generation.dto.ParsedTfDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Giải mã JSON câu Đúng/Sai: 10 trường, chặn khóa trùng, \n theo nghĩa đen chỉ đổi ở trường văn bản. */
class TfJsonParserTest {

    static String tfJson(String stem, String a, String b, String c, String d, String explanation,
                         String la, String lb, String lc, String ld) {
        return "{\"question_text\": " + SimpleJson.quote(stem)
                + ", \"statement_a\": " + SimpleJson.quote(a) + ", \"statement_b\": " + SimpleJson.quote(b)
                + ", \"statement_c\": " + SimpleJson.quote(c) + ", \"statement_d\": " + SimpleJson.quote(d)
                + ", \"explanation\": " + SimpleJson.quote(explanation)
                + ", \"label_a\": " + SimpleJson.quote(la) + ", \"label_b\": " + SimpleJson.quote(lb)
                + ", \"label_c\": " + SimpleJson.quote(lc) + ", \"label_d\": " + SimpleJson.quote(ld) + "}";
    }

    @Test
    void giaiMaDuMuoiTruongTheoThuTuAD() {
        ParsedTfDto p = TfJsonParser.parse(tfJson("Cho $P = x + 1$.", " Ý a ", "Ý b", "Ý c", "Ý d", "Lời giải",
                " T ", "F", "t", "f"));
        assertTrue(p.parseSuccess());
        assertEquals("Cho $P = x + 1$.", p.questionText());
        assertEquals(List.of("Ý a", "Ý b", "Ý c", "Ý d"), p.statements());
        assertEquals("Lời giải", p.explanation());
        assertEquals(List.of("T", "F", "t", "f"), p.labels(), "nhãn chỉ trim; kiểm giá trị ở FormatChecker");
    }

    @Test
    void khoaTrungLaLoiGiaiMa() {
        String json = tfJson("S", "a", "b", "c", "d", "E", "T", "F", "T", "F")
                .replace("}", ", \"label_c\": \"F\"}");
        ParsedTfDto p = TfJsonParser.parse(json);
        assertFalse(p.parseSuccess());
        assertTrue(p.parseError().contains("label_c"), p.parseError());
        assertEquals(List.of(), p.statements());
        assertEquals(List.of(), p.labels());
    }

    @Test
    void xuongDongTheoNghiaDenChiDoiOVanBan() {
        ParsedTfDto p = TfJsonParser.parse(tfJson("Dòng 1\\nDòng 2", "a\\nb với $x \\neq 0$", "b", "c", "d",
                "B1\\nB2", "T\\n", "F", "T", "F"));
        assertEquals("Dòng 1\nDòng 2", p.questionText());
        assertEquals("a\nb với $x \\neq 0$", p.statements().get(0));
        assertEquals("B1\nB2", p.explanation());
        assertEquals("T\\n", p.labels().get(0), "nhãn không đổi \\n");
    }

    @Test
    void thieuTruongGiuNull() {
        ParsedTfDto p = TfJsonParser.parse("{\"question_text\": \"S\", \"statement_a\": \"a\", \"label_b\": \"F\"}");
        assertTrue(p.parseSuccess());
        assertEquals(Arrays.asList("a", null, null, null), p.statements());
        assertEquals(Arrays.asList(null, "F", null, null), p.labels());
    }

    @Test
    void rongHoacKhongPhaiJson() {
        assertFalse(TfJsonParser.parse(null).parseSuccess());
        assertFalse(TfJsonParser.parse("  ").parseSuccess());
        assertFalse(TfJsonParser.parse("```json\nkhông phải JSON\n```").parseSuccess());
    }
}
