package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.questionimage.RecordingStatementInspector;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G0 H3 (O1): xem đề ({@code GET /api/exams/{id}}) và kết quả thao tác trên một chỗ (Giữ) trả {@code images} cho chỗ lấy từ
 * ngân hàng mà câu nguồn có hình: URL ký ngắn hạn cho đúng người xem đề. Chỗ khác (câu sinh) và đề không có chỗ ngân hàng
 * giữ nguyên JSON (không có khóa {@code images}) và không phát sinh truy vấn ảnh. Truy vấn ảnh không nạp cột {@code data}.
 *
 * <p>Test gán thẳng {@code bank_item_id} vào chỗ như {@code ReplaceFromBankTest} làm (từ X1 tạo đề và Đổi câu cũng chọn câu
 * có hình, xem ExamFigureBankTest); LLM sinh câu và Solver giả, không gọi mạng.
 */
@SpringBootTest(properties = {
        "app.question-images.url-signing-key=dGVzdC1vbmx5LXF1ZXN0aW9uLWltYWdlLXVybC1zaWduaW5nLWtleS1ub3QtcHJvZA==",
        "app.question-images.url-ttl=1h",
        RecordingStatementInspector.PROPERTY
})
@Import(TestSafetyConfiguration.class)
class ExamViewImagesTest extends ExamFlowTestBase {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47};

    /** Đề hai chỗ mcq đã sinh xong; chỗ 1 được gán câu ngân hàng có hình. Trả {examId, slot có hình, slot sinh, ảnh}. */
    private int[] examWithBankFigureSlot() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", null, 2));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        int figureSlot = slotId(examId, cellId(matrix, 1), 1);
        int generatedSlot = slotId(examId, cellId(matrix, 1), 2);
        assertEquals("ready", slotState(figureSlot));
        assertEquals("ready", slotState(generatedSlot));
        int item = bankMcq("Đa thức", "easy", "Cho hình vẽ sau.\n⟦img:1⟧");
        attachImageToBankItem(item);
        jdbc.update("UPDATE exam_slot SET bank_item_id = ?, generated_question_id = NULL WHERE id = ?", item, figureSlot);
        int imageId = count("SELECT qi.id FROM question_image qi JOIN bank_item bi ON bi.source_question_id ="
                + " qi.generated_question_id WHERE bi.id = ?", item);
        return new int[] {examId, figureSlot, generatedSlot, imageId};
    }

    private static JsonNode itemOfSlot(JsonNode view, int slotId) {
        for (JsonNode part : view.get("parts")) {
            for (JsonNode item : part.get("items")) {
                if (item.get("slotId").asInt() == slotId) {
                    return item;
                }
            }
        }
        throw new AssertionError("Đề không có chỗ " + slotId);
    }

    private JsonNode view(int examId) throws Exception {
        return json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
    }

    private void assertSignedFor(JsonNode image, int viewer, int imageId) throws Exception {
        String url = image.get("url").asText();
        assertEquals(imageId, image.get("id").asInt());
        assertTrue(url.startsWith("/api/question-images/" + imageId + "?exp="), url);
        assertTrue(url.contains("&u=" + viewer + "&sig="), url);
        mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(PNG_BYTES));
    }

    @Test
    void chiChoCoCauNganHangCoHinhCoImagesKyChoNguoiXemDe() throws Exception {
        int[] e = examWithBankFigureSlot();

        JsonNode view = view(e[0]);
        JsonNode figure = itemOfSlot(view, e[1]);
        assertEquals("bank", figure.get("source").asText());
        assertEquals(1, figure.get("images").size());
        assertSignedFor(figure.get("images").get(0), teacher, e[3]);
        assertEquals("stem", figure.get("images").get(0).get("role").asText());
        assertTrue(figure.get("questionText").asText().contains("⟦img:"), "token vẫn trong đề để giao diện đặt ảnh");

        assertFalse(itemOfSlot(view, e[2]).has("images"), "chỗ câu sinh: không có khóa images");
    }

    @Test
    void ketQuaGiuCauCuaChoCoHinhCungKemImages() throws Exception {
        int[] e = examWithBankFigureSlot();

        JsonNode kept = json(postJson("/api/exams/" + e[0] + "/slots/" + e[1] + "/keep", "{}")
                .andExpect(status().isOk()));
        assertEquals(1, kept.get("images").size());
        assertSignedFor(kept.get("images").get(0), teacher, e[3]);
    }

    @Test
    void deKhongCoChoNganHangKhongChayTruyVanAnhVaKhongDoiJson() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", null, 2));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);

        RecordingStatementInspector.clear();
        JsonNode view = view(examId);
        perform(get("/api/exams")).andExpect(status().isOk());

        assertEquals(java.util.List.of(), RecordingStatementInspector.imageQueries(),
                "không có chỗ ngân hàng: không truy vấn question_image");
        assertFalse(view.toString().contains("\"images\""));
    }

    @Test
    void truyVanAnhCuaXemDeVaDanhSachDeKhongNapCotData() throws Exception {
        int[] e = examWithBankFigureSlot();

        RecordingStatementInspector.clear();
        view(e[0]);
        assertEquals(1, RecordingStatementInspector.imageQueries().size(), "một truy vấn ảnh theo lô cho cả đề");

        RecordingStatementInspector.clear();
        perform(get("/api/exams")).andExpect(status().isOk());
        assertEquals(java.util.List.of(), RecordingStatementInspector.imageQueries(),
                "danh sách đề chỉ cần số đếm: không truy vấn ảnh");
        RecordingStatementInspector.clear();
        view(e[0]);
        for (String sql : RecordingStatementInspector.imageQueries()) {
            assertFalse(RecordingStatementInspector.selectsDataColumn(sql), "không nạp cột data: " + sql);
        }
    }

    @Test
    void nguoiKhongPhaiChuDeKhongXemDuocDeNenKhongCoUrlAnh() throws Exception {
        int[] e = examWithBankFigureSlot();

        String body = performAs(otherTeacher, get("/api/exams/" + e[0])).andExpect(status().isNotFound()).andReturn()
                .getResponse().getContentAsString();
        assertFalse(body.contains("/api/question-images/"));
    }
}
