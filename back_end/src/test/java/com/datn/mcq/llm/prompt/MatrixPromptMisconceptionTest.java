package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prompt ma trận mcq v10: phương án nhiễu gắn với lỗi sai của học sinh (13/11, PA1, thiết kế mục 3, 4). */
class MatrixPromptMisconceptionTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static MatrixCellSpec spec(QuestionType type) {
        return switch (type) {
            case MCQ -> new MatrixCellSpec("Toán", 8, "Hằng đẳng thức", null, Map.of(Difficulty.MEDIUM, "Khai triển được."),
                    QuestionType.MCQ, Difficulty.MEDIUM, null);
            case SA -> new MatrixCellSpec("Toán", 8, "Hằng đẳng thức", null, Map.of(Difficulty.MEDIUM, "Tính nhanh."),
                    QuestionType.SA, Difficulty.MEDIUM, null);
            case TF -> new MatrixCellSpec("Toán", 8, "Hằng đẳng thức", null, Map.of(Difficulty.EASY, "Nhận biết."),
                    QuestionType.TF, null, List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD));
        };
    }

    @Test
    void promptMcqCoQuyTacLoiSaiVaQuyTacKhacGiaTri() {
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec(QuestionType.MCQ));
        assertTrue(p.contains(MatrixPromptDraftV0.MCQ_MISCONCEPTION_RULES), p);
        for (String part : List.of(
                "Mỗi phương án sai phải là KẾT QUẢ của đúng MỘT lỗi sai thường gặp của học sinh lớp đó khi làm chính bài này",
                "Hai phương án sai không được xuất phát từ cùng một lỗi.",
                "Lỗi phải nằm trong PHẠM VI KIẾN THỨC ở trên",
                "Mỗi phương án sai phải KHÁC GIÁ TRỊ với đáp án đúng; không dùng cách viết khác (phân tích chưa triệt để, "
                        + "đổi thứ tự nhân tử, đặt nhân tử chung khác) của cùng một biểu thức",
                "phương án đúng ghi null",
                "đủ 8 trường",
                "\"explanation\", \"distractor_errors\" (đối tượng A/B/C/D, mỗi khóa là câu mô tả lỗi sai dẫn tới phương án đó;"
                        + " khóa của phương án đúng là null).")) {
            assertTrue(p.contains(part), "Prompt thiếu: " + part);
        }
        assertTrue(p.indexOf(MatrixPromptDraftV0.MCQ_MISCONCEPTION_RULES) > p.indexOf(MatrixPromptDraftV0.MCQ_OPTION_RULES),
                "ngay sau quy tắc phương án");
        assertTrue(p.indexOf(MatrixPromptDraftV0.MCQ_MISCONCEPTION_RULES) > p.indexOf("PHẠM VI KIẾN THỨC"),
                "khối phạm vi kiến thức của v9 vẫn đứng trước");
        assertFalse(p.contains("đủ 7 trường"));
        assertTrue(MatrixPromptDraftV0.asksDistractorErrors(p));
        assertEquals(McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON_V10, McqProductionSchema.forPrompt(p));
    }

    @Test
    void saTfVaPromptSinhLeKhongDoi() {
        for (QuestionType t : List.of(QuestionType.SA, QuestionType.TF)) {
            String p = MatrixPromptDraftV0.buildGenerationPrompt(spec(t));
            assertFalse(p.contains("distractor_errors"), t.name());
            assertFalse(p.contains(MatrixPromptDraftV0.MCQ_DISTINCT_VALUE_RULE), t.name());
        }
        String sync = new PromptBuilder().buildGenerationPrompt("Toán", 8, null, null, null, Difficulty.EASY);
        assertFalse(MatrixPromptDraftV0.asksDistractorErrors(sync));
        assertEquals(McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON, McqProductionSchema.forPrompt(sync),
                "job cũ và prompt 7 trường giữ schema cũ");
        assertEquals(McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON, McqProductionSchema.forPrompt(null));
        assertEquals("matrix-mcq-v10-draft", MatrixPromptDraftV0.promptVersion(QuestionType.MCQ));
        assertEquals("matrix-sa-v12-draft", MatrixPromptDraftV0.promptVersion(QuestionType.SA), "sa của #75, PA1 không đổi");
        assertEquals("matrix-tf-v12-draft", MatrixPromptDraftV0.promptVersion(QuestionType.TF), "tf của #75, PA1 không đổi");
    }

    @Test
    void schemaV10DistractorErrorsDungCuoiBayTruongCuGiuNguyen() throws Exception {
        JsonNode v7 = JSON.readTree(McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON);
        JsonNode v10 = JSON.readTree(McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON_V10);
        List<String> expected = new ArrayList<>(texts(v7.get("propertyOrdering")));
        expected.add("distractor_errors");
        assertEquals(expected, texts(v10.get("propertyOrdering")));
        assertEquals(expected, texts(v10.get("required")));
        v7.get("properties").fieldNames().forEachRemaining(f ->
                assertEquals(v7.get("properties").get(f), v10.get("properties").get(f), f));
        JsonNode de = v10.get("properties").get("distractor_errors");
        assertEquals("OBJECT", de.get("type").asText());
        assertEquals(List.of("A", "B", "C", "D"), texts(de.get("propertyOrdering")));
        for (String letter : List.of("A", "B", "C", "D")) {
            assertTrue(de.get("properties").get(letter).get("nullable").asBoolean(), letter);
        }
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
