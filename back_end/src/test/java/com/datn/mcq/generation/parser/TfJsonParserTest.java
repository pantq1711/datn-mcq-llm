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

    // ---------- tiền tố nhãn ở đầu ý (GĐ4) ----------

    private static ParsedTfDto withStatements(String a, String b, String c, String d) {
        return TfJsonParser.parse(tfJson("S", a, b, c, d, "E", "T", "F", "T", "F"));
    }

    @Test
    void boTienToKhopViTriKemCanhBao() {
        ParsedTfDto p = withStatements("a) Ý một", "B. Ý hai", "( c ) Ý ba", "d : Ý bốn");
        assertEquals(List.of("Ý một", "Ý hai", "Ý ba", "Ý bốn"), p.statements());
        assertEquals(List.of(
                "statement_a: đã bỏ tiền tố \"a)\" ở đầu ý",
                "statement_b: đã bỏ tiền tố \"B.\" ở đầu ý",
                "statement_c: đã bỏ tiền tố \"( c )\" ở đầu ý",
                "statement_d: đã bỏ tiền tố \"d :\" ở đầu ý"), p.parseWarnings());
    }

    @Test
    void boTienToKhongCanKhoangTrangSauVaBoKhoangTrangDau() {
        ParsedTfDto p = withStatements("  (A)Ý một", "b)Ý hai", "C:Ý ba", "Ý bốn");
        assertEquals(List.of("Ý một", "Ý hai", "Ý ba", "Ý bốn"), p.statements());
        assertEquals(3, p.parseWarnings().size(), p.parseWarnings().toString());
    }

    @Test
    void khongBoKhiChuCaiKhongKhopViTri() {
        ParsedTfDto p = withStatements("b) Ý một", "a. Ý hai", "(d) Ý ba", "c: Ý bốn");
        assertEquals(List.of("b) Ý một", "a. Ý hai", "(d) Ý ba", "c: Ý bốn"), p.statements());
        assertEquals(List.of(), p.parseWarnings());
    }

    @Test
    void khongBoKhiKhongPhaiTienTo() {
        ParsedTfDto p = withStatements("a là số thực dương.", "$b) = 2$", "c = 3 với mọi x.", "Đồ thị cắt trục hoành.");
        assertEquals(List.of("a là số thực dương.", "$b) = 2$", "c = 3 với mọi x.", "Đồ thị cắt trục hoành."),
                p.statements());
        assertEquals(List.of(), p.parseWarnings());
    }

    @Test
    void tienToChamChiBoKhiSauDauChamKhongPhaiChuCai() {
        ParsedTfDto p = withStatements("a.b = 0 với mọi $x$.", "b.Điều kiện xác định", "c. Điều kiện", "d.  $x = 2$");
        assertEquals(List.of("a.b = 0 với mọi $x$.", "b.Điều kiện xác định", "Điều kiện", "$x = 2$"), p.statements());
        assertEquals(List.of(
                "statement_c: đã bỏ tiền tố \"c.\" ở đầu ý",
                "statement_d: đã bỏ tiền tố \"d.\" ở đầu ý"), p.parseWarnings());
        ParsedTfDto q = withStatements("a. Điều kiện", "B .C > 0", "c.5 là số dương", "d)Ý bốn");
        assertEquals(List.of("Điều kiện", "B .C > 0", "5 là số dương", "Ý bốn"), q.statements());
        assertEquals(3, q.parseWarnings().size(), q.parseWarnings().toString());
    }

    @Test
    void chiBoMotLan() {
        ParsedTfDto p = withStatements("a) a) Ý một", "Ý hai", "Ý ba", "Ý bốn");
        assertEquals("a) Ý một", p.statements().get(0));
        assertEquals(1, p.parseWarnings().size());
    }

    @Test
    void khongCoTienToThiKhongCanhBao() {
        ParsedTfDto p = withStatements("Ý một", "Ý hai", "Ý ba", "Ý bốn");
        assertEquals(List.of(), p.parseWarnings());
        assertEquals(List.of(), TfJsonParser.parse(null).parseWarnings());
    }
}
