package com.datn.mcq.generation.parser;

import com.datn.mcq.generation.dto.ParsedSaDto;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Giải mã JSON câu Trả lời ngắn: 3 trường, chặn khóa trùng, \n theo nghĩa đen chỉ đổi ở trường văn bản. */
class SaJsonParserTest {

    @Test
    void giaiMaDuBaTruong() {
        ParsedSaDto p = SaJsonParser.parse("""
                {"question_text": "Nghiệm của phương trình $4x + 3 = 0$ là bao nhiêu?",
                 "explanation": "$4x = -3$ nên $x = -\\\\frac{3}{4}$.",
                 "answer": " $-\\\\frac{3}{4}$ "}
                """);
        assertTrue(p.parseSuccess());
        assertNull(p.parseError());
        assertEquals("Nghiệm của phương trình $4x + 3 = 0$ là bao nhiêu?", p.questionText());
        assertEquals("$4x = -3$ nên $x = -\\frac{3}{4}$.", p.explanation());
        assertEquals("$-\\frac{3}{4}$", p.answer());
        assertTrue(p.answerIsString());
    }

    @Test
    void catRaoMarkdown() {
        ParsedSaDto p = SaJsonParser.parse("```json\n{\"question_text\": \"Q\", \"explanation\": \"E\", \"answer\": \"5\"}\n```");
        assertTrue(p.parseSuccess());
        assertEquals("5", p.answer());
    }

    @Test
    void khoaTrungLaLoiGiaiMa() {
        ParsedSaDto p = SaJsonParser.parse(
                "{\"question_text\": \"Q\", \"explanation\": \"E\", \"answer\": \"5\", \"answer\": \"6\"}");
        assertFalse(p.parseSuccess());
        assertTrue(p.parseError().contains("answer"), p.parseError());
        assertNull(p.answer());
    }

    @Test
    void rongHoacKhongPhaiJson() {
        assertFalse(SaJsonParser.parse("   ").parseSuccess());
        assertFalse(SaJsonParser.parse(null).parseSuccess());
        assertFalse(SaJsonParser.parse("không phải JSON").parseSuccess());
    }

    @Test
    void xuongDongTheoNghiaDenChiDoiOTruongVanBan() {
        // Trong JSON: "\\n" giải mã thành hai ký tự \ và n.
        ParsedSaDto p = SaJsonParser.parse(
                "{\"question_text\": \"Dòng 1\\\\nDòng 2 với $x \\\\neq 0$\", \"explanation\": \"B1\\\\nB2\","
                        + " \"answer\": \"5\\\\n\"}");
        assertTrue(p.parseSuccess());
        assertEquals("Dòng 1\nDòng 2 với $x \\neq 0$", p.questionText());
        assertEquals("B1\nB2", p.explanation());
        assertEquals("5\\n", p.answer(), "đáp số chỉ trim, không đổi \\n");
    }

    @Test
    void chuTiengVietDangToHopSauXuongDong() {
        String nfd = Normalizer.normalize("nếu", Normalizer.Form.NFD);
        ParsedSaDto p = SaJsonParser.parse(
                "{\"question_text\": \"A\\\\n" + nfd + " x\", \"explanation\": \"E\", \"answer\": \"1\"}");
        assertEquals("A\n" + nfd + " x", p.questionText());
    }

    @Test
    void dapSoLaSoJsonThiDanhDauKhongPhaiChuoi() {
        ParsedSaDto p = SaJsonParser.parse("{\"question_text\": \"Q\", \"explanation\": \"E\", \"answer\": 0.5}");
        assertTrue(p.parseSuccess());
        assertFalse(p.answerIsString());
        assertEquals("0.5", p.answer());
    }

    @Test
    void thieuTruong() {
        ParsedSaDto p = SaJsonParser.parse("{\"question_text\": \"Q\"}");
        assertTrue(p.parseSuccess());
        assertNull(p.answer());
        assertNull(p.explanation());
    }
}
