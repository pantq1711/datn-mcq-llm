package com.datn.mcq.generation.parser;

import com.datn.mcq.enums.Option;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.qc.service.QcSummaryService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trường {@code distractor_errors} của câu mcq (13/11, PA1, docs/design/phuong-an-nhieu-loi-sai.md mục 3, H-5). Thiếu hay hỏng
 * chỉ là cảnh báo tự sửa: câu vẫn giải mã thành công, cảnh báo không bật "cần xem kỹ".
 */
class DistractorErrorsParserTest {

    private static String mcq(String distractorJson) {
        return "{\"question_text\": \"Khai triển $(x+1)^2$\", \"option_a\": \"$x^2 + 1$\", \"option_b\": \"$x^2 + 2x + 1$\","
                + " \"option_c\": \"$x^2 - 2x + 1$\", \"option_d\": \"$x^2 + x + 1$\", \"explanation\": \"Dùng hằng đẳng thức.\","
                + " \"correct_option\": \"B\"" + (distractorJson == null ? "" : ", \"distractor_errors\": " + distractorJson)
                + "}";
    }

    private static void allAutoFixed(ParsedMcqDto dto) {
        for (String w : dto.parseWarnings()) {
            assertTrue(QcSummaryService.isAutoFixedWarning(w), "cảnh báo tự sửa, không bật cờ: " + w);
        }
    }

    @Test
    void duLoiThiLuuNguyenKhongCanhBao() {
        ParsedMcqDto dto = McqJsonParser.parse(mcq("{\"A\": \"Nhầm $(a+b)^2 = a^2 + b^2$\", \"B\": null,"
                + " \"C\": \"Quên đổi dấu khi bỏ ngoặc\", \"D\": \" Cộng   hệ số sai \"}"), true, true);
        assertTrue(dto.parseSuccess());
        assertEquals(Arrays.asList("Nhầm $(a+b)^2 = a^2 + b^2$", null, "Quên đổi dấu khi bỏ ngoặc", "Cộng hệ số sai"),
                new java.util.ArrayList<>(dto.distractorErrors().values()));
        assertEquals(List.of("A", "B", "C", "D"), List.copyOf(dto.distractorErrors().keySet()));
        assertEquals(List.of(), dto.parseWarnings());
    }

    @Test
    void thieuHanTruongThiNullKemCanhBaoKhiPromptCoYeuCau() {
        ParsedMcqDto dto = McqJsonParser.parse(mcq(null), true, true);
        assertTrue(dto.parseSuccess(), "không format_failed");
        assertNull(dto.distractorErrors());
        assertEquals(List.of("json: đã sửa thiếu distractor_errors (không có phân tích phương án nhiễu)"),
                dto.parseWarnings());
        allAutoFixed(dto);
        // không phải đối tượng: như thiếu
        ParsedMcqDto notObject = McqJsonParser.parse(mcq("\"A sai\""), true, true);
        assertNull(notObject.distractorErrors());
        assertEquals(List.of(DistractorErrorsParser.MISSING_WARNING), notObject.parseWarnings());
        // prompt cũ (7 trường) không yêu cầu: không cảnh báo
        assertEquals(List.of(), McqJsonParser.parse(mcq(null), true).parseWarnings());
        assertEquals(List.of(), McqJsonParser.parse(mcq(null), true, false).parseWarnings());
    }

    @Test
    void thieuMotPhuongAnVaChuOPhuongAnDung() {
        ParsedMcqDto dto = McqJsonParser.parse(mcq("{\"A\": \"Quên hạng tử $2x$\", \"B\": \"Không có lỗi\","
                + " \"C\": \"  \", \"D\": \"Nhầm hệ số của $x$\"}"), true, true);
        Map<String, String> e = dto.distractorErrors();
        assertEquals("Quên hạng tử $2x$", e.get("A"));
        assertNull(e.get("B"), "phương án đúng luôn null");
        assertNull(e.get("C"));
        assertEquals("Nhầm hệ số của $x$", e.get("D"));
        assertEquals(List.of("json: đã sửa distractor_errors bỏ lỗi ghi ở phương án đúng B",
                "json: đã sửa distractor_errors thiếu lỗi của phương án C"), dto.parseWarnings());
        allAutoFixed(dto);
    }

    @Test
    void loiDaiTu16Den30TuGiuKemCanhBaoQua30TuBoKhongCatChu() {
        String w20 = words(20);
        String w30 = words(30);
        String w31 = words(31);
        ParsedMcqDto dto = McqJsonParser.parse(mcq("{\"A\": \"" + w20 + "\", \"B\": null, \"C\": \"" + w30
                + "\", \"D\": \"" + w31 + "\"}"), true, true);
        Map<String, String> e = dto.distractorErrors();
        assertEquals(w20, e.get("A"), "giữ nguyên chữ");
        assertEquals(w30, e.get("C"));
        assertNull(e.get("D"), "quá 30 từ thì bỏ, không cắt");
        assertEquals(List.of("json: đã sửa distractor_errors phương án A dài 20 từ (quá 15)",
                "json: đã sửa distractor_errors phương án C dài 30 từ (quá 15)",
                "json: đã sửa distractor_errors bỏ lỗi của phương án D vì dài 31 từ (quá 30)"), dto.parseWarnings());
        allAutoFixed(dto);
        // đúng 15 từ: không cảnh báo
        ParsedMcqDto ok = McqJsonParser.parse(mcq("{\"A\": \"" + words(15) + "\", \"B\": null, \"C\": \"x\","
                + " \"D\": \"y\"}"), true, true);
        assertEquals(List.of(), ok.parseWarnings());
    }

    @Test
    void congThucLienNhauLaMotTu() {
        assertEquals(2, DistractorErrorsParser.wordCount("Nhầm $(a+b)^2 = a^2 + b^2$"));
        assertEquals(5, DistractorErrorsParser.wordCount("Quên đổi dấu khi $-(x-1)$"));
        assertEquals(0, DistractorErrorsParser.wordCount("  "));
    }

    @Test
    void congThucHongThiBoNhuThieu() {
        ParsedMcqDto dto = McqJsonParser.parse(mcq("{\"A\": \"Nhầm $x^2\", \"B\": null, \"C\": \"Sai dấu\","
                + " \"D\": \"Dùng $\\\\fooo{x}$ thay hệ số\"}"), true, true);
        assertNull(dto.distractorErrors().get("D"));
        assertEquals("Sai dấu", dto.distractorErrors().get("C"));
        assertTrue(dto.parseWarnings().contains("json: đã sửa distractor_errors bỏ lỗi của phương án D vì công thức hỏng"),
                dto.parseWarnings().toString());
        allAutoFixed(dto);
    }

    @Test
    void cheDoChatDocTruongKhongCanhBao() {
        ParsedMcqDto dto = McqJsonParser.parse(mcq("{\"A\": \"Sai dấu\", \"B\": \"x\", \"C\": \"\", \"D\": \"Sai hệ số\"}"));
        assertEquals("Sai dấu", dto.distractorErrors().get("A"));
        assertNull(dto.distractorErrors().get("B"));
        assertNull(dto.distractorErrors().get("C"));
        assertEquals(List.of(), dto.parseWarnings());
        assertNull(McqJsonParser.parse(mcq(null)).distractorErrors());
    }

    @Test
    void khongCoLoiNaoDungDuocThiNull() {
        ParsedMcqDto dto = McqJsonParser.parse(mcq("{\"A\": null, \"B\": null, \"C\": \"\", \"D\": null}"), true, true);
        assertNull(dto.distractorErrors());
        assertTrue(dto.parseSuccess());
        assertEquals(Option.B, dto.correctOption());
    }

    private static String words(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(i > 0 ? " " : "").append("từ");
        }
        return sb.toString();
    }
}
