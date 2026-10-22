package com.datn.mcq.common.util;

import com.datn.mcq.common.util.ShortAnswerValue.Mode;
import com.datn.mcq.common.util.ShortAnswerValue.ParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bộ phân tích đáp số SA (bản sửa 1 thiết kế, quyết định A-E ngày 22/10 và hai bổ sung):
 * 22 ca của bảng so đáp số, các ca dấu chấm hàng nghìn, phân số chưa tối giản, \dfrac, \tfrac, √, ·,
 * giới hạn độ dài và giới hạn số dưới căn; chạy ở cả STRICT và LENIENT.
 */
class ShortAnswerValueTest {

    private static ParseResult parse(String raw, Mode mode) {
        return ShortAnswerValue.parse(raw, mode);
    }

    private static String key(String raw, Mode mode) {
        ParseResult r = parse(raw, mode);
        assertTrue(r.ok(), "'" + raw + "' (" + mode + ") phải phân tích được, lỗi: " + r.error());
        assertNull(r.error());
        return r.value().canonicalKey();
    }

    private static void assertUnparseable(String raw, Mode mode, String expectedError) {
        ParseResult r = parse(raw, mode);
        assertFalse(r.ok(), "'" + raw + "' (" + mode + ") không được phân tích được");
        assertNull(r.value());
        assertEquals(expectedError, r.error(), "thông báo cho '" + raw + "' (" + mode + ")");
    }

    // ---------- bảng ca bằng nhau (dòng 1-12): cùng khóa chuẩn ở cả hai chế độ ----------

    static Stream<Arguments> equalPairs() {
        return Stream.of(
                Arguments.of("1", "0,5", "0.5", "1/2"),
                Arguments.of("2", "1/2", "\\frac{1}{2}", "1/2"),
                Arguments.of("3", "$0{,}5$", "$\\frac{1}{2}$", "1/2"),
                Arguments.of("4", "-0,75", "−3/4", "-3/4"),
                Arguments.of("5", "-\\frac{3}{4}", "\\frac{-3}{4}", "-3/4"),
                Arguments.of("6", "2\\sqrt{3}", "\\sqrt{12}", "2·√3"),
                Arguments.of("7", "\\frac{\\sqrt{3}}{2}", "0,5\\sqrt{3}", "(1/2)·√3"),
                Arguments.of("8", "\\frac{2}{\\sqrt{3}}", "\\frac{2\\sqrt{3}}{3}", "(2/3)·√3"),
                Arguments.of("9", "\\sqrt{4}", "2", "2"),
                Arguments.of("10", "0,50", "0,5", "1/2"),
                Arguments.of("11", "2/4", "1/2", "1/2"),
                Arguments.of("12", " - 1 2 , 5 ", "-12,5", "-25/2"),
                // bổ sung
                Arguments.of("b1", "350 000", "350000", "350000"),
                Arguments.of("b2", "350 000", "350000", "350000"),
                Arguments.of("b3", "\\dfrac{1}{2}", "\\tfrac{1}{2}", "1/2"),
                Arguments.of("b4", "√12", "√{12}", "2·√3"),
                Arguments.of("b5", "2·√3", "2\\cdot\\sqrt{3}", "2·√3"),
                Arguments.of("b6", "\\frac{1}{2}\\sqrt{3}", "\\frac{\\sqrt{3}}{2}", "(1/2)·√3"),
                Arguments.of("b7", "$$-0,5$$", "-\\frac{1}{2}", "-1/2"),
                Arguments.of("b8", "1.5", "1,5", "3/2"),
                Arguments.of("b9", "1,500", "1.5", "3/2"),
                Arguments.of("b10", "1.2345", "1,2345", "2469/2000"),
                Arguments.of("b11", "1.25", "\\frac{5}{4}", "5/4"),
                Arguments.of("b12", "-√3", "-\\sqrt{3}", "-1·√3"),
                Arguments.of("b13", "\\frac{\\sqrt{8}}{\\sqrt{2}}", "2", "2"),
                Arguments.of("b14", "\\sqrt{1000000000000}", "1000000", "1000000"),
                Arguments.of("b15", "123456789012345678901234567890", "123456789012345678901234567890",
                        "123456789012345678901234567890"));
    }

    @ParameterizedTest(name = "dòng {0}: {1} = {2}")
    @MethodSource("equalPairs")
    void bangNhauTheoGiaTriOCaHaiCheDo(String row, String a, String b, String expectedKey) {
        for (Mode mode : Mode.values()) {
            assertEquals(expectedKey, key(a, mode), "dòng " + row + ", " + mode + ", A");
            assertEquals(expectedKey, key(b, mode), "dòng " + row + ", " + mode + ", B");
            ShortAnswerValue va = parse(a, mode).value();
            ShortAnswerValue vb = parse(b, mode).value();
            assertTrue(va.sameValue(vb), "dòng " + row + ": " + a + " phải bằng " + b);
            assertTrue(vb.sameValue(va), "dòng " + row + ": phép so phải đối xứng");
        }
    }

    // ---------- dòng 13-14: khác giá trị ----------

    static Stream<Arguments> differentPairs() {
        return Stream.of(
                Arguments.of("13", "0,33", "1/3"),
                Arguments.of("14", "1,41", "\\sqrt{2}"),
                Arguments.of("k1", "2\\sqrt{3}", "3\\sqrt{2}"),
                Arguments.of("k2", "-0,5", "0,5"),
                Arguments.of("k3", "\\sqrt{2}", "\\sqrt{3}"));
    }

    @ParameterizedTest(name = "dòng {0}: {1} ≠ {2}")
    @MethodSource("differentPairs")
    void khacGiaTri(String row, String a, String b) {
        for (Mode mode : Mode.values()) {
            ShortAnswerValue va = parse(a, mode).value();
            ShortAnswerValue vb = parse(b, mode).value();
            assertFalse(va.sameValue(vb), "dòng " + row + ", " + mode);
            assertFalse(va.canonicalKey().equals(vb.canonicalKey()), "dòng " + row + ": khóa phải khác");
        }
    }

    // ---------- dòng 15-16: hợp lệ với giáo viên (LENIENT), sai định dạng với LLM (STRICT) ----------

    static Stream<Arguments> lenientOnly() {
        return Stream.of(
                Arguments.of("15", "05", "5", ShortAnswerValue.MSG_LEADING_ZERO),
                Arguments.of("15b", "-05", "-5", ShortAnswerValue.MSG_LEADING_ZERO),
                Arguments.of("15c", "007,5", "15/2", ShortAnswerValue.MSG_LEADING_ZERO),
                Arguments.of("15d", "\\frac{05}{2}", "5/2", ShortAnswerValue.MSG_LEADING_ZERO),
                Arguments.of("15e", "\\sqrt{012}", "2·√3", ShortAnswerValue.MSG_LEADING_ZERO),
                Arguments.of("16", "-0", "0", ShortAnswerValue.MSG_NEGATIVE_ZERO),
                Arguments.of("16b", "-0,0", "0", ShortAnswerValue.MSG_NEGATIVE_ZERO),
                Arguments.of("16c", "-\\frac{0}{3}", "0", ShortAnswerValue.MSG_NEGATIVE_ZERO),
                Arguments.of("16d", "\\frac{-0}{3}", "0", ShortAnswerValue.MSG_NEGATIVE_ZERO));
    }

    @ParameterizedTest(name = "dòng {0}: {1}")
    @MethodSource("lenientOnly")
    void chiHopLeVoiCheDoRong(String row, String raw, String lenientKey, String strictError) {
        assertEquals(lenientKey, key(raw, Mode.LENIENT), "dòng " + row);
        assertUnparseable(raw, Mode.STRICT, strictError);
    }

    @Test
    void soKhongVaSoThapPhanBatDauBangKhongVanHopLeVoiLlm() {
        assertEquals("0", key("0", Mode.STRICT));
        assertEquals("1/2", key("0,5", Mode.STRICT));
        assertEquals("-1/2", key("-0,5", Mode.STRICT));
        assertEquals("0", key("0,0", Mode.STRICT));
    }

    // ---------- dòng 17-22 và các ca mới: không phân tích được ở cả hai chế độ ----------

    static Stream<Arguments> unparseable() {
        String u = ShortAnswerValue.MSG_UNPARSEABLE;
        return Stream.of(
                Arguments.of("17", "1/0", ShortAnswerValue.MSG_ZERO_DENOMINATOR),
                Arguments.of("17b", "\\frac{1}{0}", ShortAnswerValue.MSG_ZERO_DENOMINATOR),
                Arguments.of("18", "1 + \\sqrt{2}", u),
                Arguments.of("19", "\\sqrt{-3}", u),
                Arguments.of("19b", "\\sqrt{0}", u),
                Arguments.of("20", "1.000,5", ShortAnswerValue.MSG_THOUSANDS),
                Arguments.of("21", "x = 2", u),
                Arguments.of("21b", "2 cm", u),
                Arguments.of("21c", "\\pi", u),
                Arguments.of("21d", "2^3", u),
                Arguments.of("22", "$5", u),
                // dấu chấm theo sau đúng 3 chữ số: có thể là phân cách hàng nghìn
                Arguments.of("n1", "1.500", ShortAnswerValue.MSG_THOUSANDS),
                Arguments.of("n2", "-12.000", ShortAnswerValue.MSG_THOUSANDS),
                Arguments.of("n3", "350.000", ShortAnswerValue.MSG_THOUSANDS),
                Arguments.of("n4", "1.500.000", ShortAnswerValue.MSG_THOUSANDS),
                Arguments.of("n5", "$2.000$", ShortAnswerValue.MSG_THOUSANDS),
                // áp đúng nguyên văn quy tắc đã duyệt, kể cả khi phần nguyên là 0 (xem báo cáo GĐ2)
                Arguments.of("n6", "0.125", ShortAnswerValue.MSG_THOUSANDS),
                // dấu + đứng đầu không được nhận (quyết định C)
                Arguments.of("n7", "+5", u),
                Arguments.of("n8", "", u),
                Arguments.of("n9", "   ", u),
                Arguments.of("n10", "--3", u),
                Arguments.of("n11", ",5", u),
                Arguments.of("n12", "5,", u),
                Arguments.of("n13", "1/2√3", u),
                Arguments.of("n14", "\\sqrt3", u),
                Arguments.of("n15", "\\frac{1}{2", u),
                Arguments.of("n16", "$$5$", u),
                Arguments.of("n17", "1,5,6", u),
                Arguments.of("n18", "√-4", u),
                Arguments.of("n19", "\\sqrt{2.5}", u),
                Arguments.of("n20", "1" + "0".repeat(100), ShortAnswerValue.MSG_TOO_LONG),
                Arguments.of("n21", "\\sqrt{1000000000001}", ShortAnswerValue.MSG_RADICAND_TOO_LARGE),
                Arguments.of("n22", "\\frac{1}{\\sqrt{0}}", u));
    }

    @ParameterizedTest(name = "dòng {0}: ''{1}''")
    @MethodSource("unparseable")
    void khongPhanTichDuocOCaHaiCheDo(String row, String raw, String expectedError) {
        for (Mode mode : Mode.values()) {
            assertUnparseable(raw, mode, expectedError);
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void nullKhongPhanTichDuoc(Mode mode) {
        assertUnparseable(null, mode, ShortAnswerValue.MSG_UNPARSEABLE);
    }

    @Test
    void dungMot100KyTuVanDuoc() {
        String hundred = "1" + "0".repeat(99);
        assertEquals(100, hundred.length());
        assertEquals(hundred, key(hundred, Mode.STRICT));
    }

    // ---------- phân số chưa tối giản: hợp lệ, chỉ cảnh báo ----------

    static Stream<Arguments> unreducedFractions() {
        return Stream.of(
                Arguments.of("2/4", "1/2", true),
                Arguments.of("\\frac{6}{8}", "3/4", true),
                Arguments.of("$-\\frac{6}{8}$", "-3/4", true),
                Arguments.of("\\frac{2\\sqrt{3}}{6}", "(1/3)·√3", true),
                Arguments.of("\\frac{4}{2}", "2", true),
                Arguments.of("\\frac{3}{4}", "3/4", false),
                Arguments.of("-3/4", "-3/4", false),
                Arguments.of("\\frac{2}{\\sqrt{3}}", "(2/3)·√3", false),
                Arguments.of("0,5", "1/2", false),
                Arguments.of("\\sqrt{12}", "2·√3", false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreducedFractions")
    void phanSoChuaToiGianChiCanhBao(String raw, String expectedKey, boolean warn) {
        for (Mode mode : Mode.values()) {
            ParseResult r = parse(raw, mode);
            assertTrue(r.ok(), raw + " phải hợp lệ ở " + mode);
            assertEquals(expectedKey, r.value().canonicalKey());
            assertEquals(warn ? List.of(ShortAnswerValue.WARN_UNREDUCED_FRACTION) : List.of(), r.warnings(),
                    "cảnh báo cho " + raw + " (" + mode + ")");
        }
    }

    @Test
    void ketQuaLoiKhongCoCanhBao() {
        assertEquals(List.of(), parse("2/0", Mode.LENIENT).warnings());
    }
}
