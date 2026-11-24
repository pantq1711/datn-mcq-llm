package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.dto.ParsedSaDto;
import com.datn.mcq.generation.dto.ParsedTfDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2 (thiết kế G0): đầu ra LLM không được mang token hình {@code ⟦img:<id>⟧}. Token bị xóa ở MỌI trường văn bản của cả
 * ba dạng câu, ở cả chế độ sửa (production) và chế độ chặt (experiment), để LLM không thể gắn id ảnh của người khác
 * vào câu. Văn bản còn lại giữ nguyên.
 */
class ImageTokenStripParserTest {

    private static final String PREFIX = "⟦img:";

    private static void assertClean(String field, String value) {
        assertFalse(value.contains(PREFIX), field + " còn token: " + value);
        assertFalse(value.contains("⟦"), field + " còn ký tự ⟦: " + value);
    }

    @Test
    void mcqXoaTokenOMoiTruong() {
        String json = "{\"question_text\": " + SimpleJson.quote("Cho tam giác $ABC$ như hình.\n⟦img:7⟧\nTính $BC$.")
                + ", \"option_a\": " + SimpleJson.quote("⟦img:8⟧ Đáp án $5$")
                + ", \"option_b\": " + SimpleJson.quote("$6$ ⟦img:99999⟧")
                + ", \"option_c\": " + SimpleJson.quote("$7$")
                + ", \"option_d\": " + SimpleJson.quote("$8$ ⟦img:12")
                + ", \"correct_option\": \"A\""
                + ", \"explanation\": " + SimpleJson.quote("Xem hình ⟦img:1⟧ ở trên.") + "}";
        for (boolean repair : new boolean[]{true, false}) {
            ParsedMcqDto p = McqJsonParser.parse(json, repair);
            assertTrue(p.parseSuccess(), "repair=" + repair);
            for (String[] f : new String[][]{{"questionText", p.questionText()}, {"optionA", p.optionA()},
                    {"optionB", p.optionB()}, {"optionC", p.optionC()}, {"optionD", p.optionD()},
                    {"explanation", p.explanation()}}) {
                assertClean(f[0] + " (repair=" + repair + ")", f[1]);
            }
            assertTrue(p.questionText().contains("Cho tam giác $ABC$ như hình."), p.questionText());
            assertTrue(p.questionText().contains("Tính $BC$"), p.questionText());
            assertTrue(p.optionA().contains("Đáp án $5$"), p.optionA());
            assertEquals("$7$", p.optionC());
        }
    }

    @Test
    void saXoaTokenOCauDanLoiGiaiVaDapSo() {
        String json = "{\"question_text\": " + SimpleJson.quote("Tính độ dài $BC$ trong hình ⟦img:3⟧.")
                + ", \"answer\": " + SimpleJson.quote("5 ⟦img:4⟧")
                + ", \"explanation\": " + SimpleJson.quote("Áp dụng định lý ⟦img:5⟧.") + "}";
        for (boolean repair : new boolean[]{true, false}) {
            ParsedSaDto p = SaJsonParser.parse(json, repair);
            assertTrue(p.parseSuccess(), "repair=" + repair);
            assertClean("questionText (repair=" + repair + ")", p.questionText());
            assertClean("answer (repair=" + repair + ")", p.answer());
            assertClean("explanation (repair=" + repair + ")", p.explanation());
            assertTrue(p.questionText().contains("Tính độ dài $BC$ trong hình"), p.questionText());
            assertEquals("5", p.answer().strip());
        }
    }

    @Test
    void tfXoaTokenOCauDanBonYVaLoiGiai() {
        String json = "{\"question_text\": " + SimpleJson.quote("Cho hình thang ⟦img:2⟧ như hình vẽ.")
                + ", \"statement_a\": " + SimpleJson.quote("Ý a ⟦img:11⟧")
                + ", \"statement_b\": " + SimpleJson.quote("⟦img:12⟧ Ý b")
                + ", \"statement_c\": " + SimpleJson.quote("Ý c")
                + ", \"statement_d\": " + SimpleJson.quote("Ý d ⟦img:13⟧")
                + ", \"explanation\": " + SimpleJson.quote("Lời giải ⟦img:14⟧")
                + ", \"label_a\": \"T\", \"label_b\": \"F\", \"label_c\": \"T\", \"label_d\": \"F\"}";
        for (boolean repair : new boolean[]{true, false}) {
            ParsedTfDto p = TfJsonParser.parse(json, repair);
            assertTrue(p.parseSuccess(), "repair=" + repair);
            List<String> all = new ArrayList<>(p.statements());
            all.add(p.questionText());
            all.add(p.explanation());
            for (String s : all) {
                assertClean("repair=" + repair, s);
            }
            assertEquals(4, p.statements().size());
            assertTrue(p.statements().get(1).contains("Ý b"), p.statements().get(1));
            assertTrue(p.questionText().contains("Cho hình thang"), p.questionText());
        }
    }

    @Test
    void vanBanKhongCoTokenKhongDoi() {
        String json = "{\"question_text\": " + SimpleJson.quote("Giá trị của $x^2$ khi $x = 3$ là:")
                + ", \"option_a\": " + SimpleJson.quote("$9$") + ", \"option_b\": " + SimpleJson.quote("$6$")
                + ", \"option_c\": " + SimpleJson.quote("[img:1] giữ nguyên") + ", \"option_d\": " + SimpleJson.quote("$3$")
                + ", \"correct_option\": \"A\", \"explanation\": " + SimpleJson.quote("Bình phương.") + "}";
        ParsedMcqDto p = McqJsonParser.parse(json, true);
        assertTrue(p.parseSuccess());
        assertEquals("[img:1] giữ nguyên", p.optionC(), "ngoặc vuông thường không phải token");
        assertEquals("$9$", p.optionA());
    }
}
