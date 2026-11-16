package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NV-31 (quyết định 16/11, mục 3a): {@code app.bank.keep-visibility = private} thì mọi câu được Giữ (kể cả câu không cờ)
 * vào ngân hàng riêng của người Giữ ({@code visible_to_user_id}); người khác không thấy, không được tự lấy vào đề. API xem đề
 * báo cấu hình ({@code bankKeepVisibility}), kết quả Giữ báo câu vào ngân hàng nào ({@code bankVisibility}).
 */
@SpringBootTest(properties = "app.bank.keep-visibility=private")
@Import(TestSafetyConfiguration.class)
class BankKeepVisibilityPrivateTest extends ExamFlowTestBase {

    private static final String TOPIC = "Đa thức";

    @Test
    void giuCauKhongCoVaoNganHangRiengCuaNguoiGiu() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrix, 1), 1);
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals("private", view.get("bankKeepVisibility").asText());
        assertEquals(false, view.get("parts").get(0).get("items").get(0).get("needsReview").asBoolean(),
                "câu sạch, không cờ");

        JsonNode kept = json(perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep"))
                .andExpect(status().isOk()));
        assertEquals("private", kept.get("bankVisibility").asText());
        int bankItem = kept.get("bankItemId").asInt();
        assertEquals(teacher, count("SELECT visible_to_user_id FROM bank_item WHERE id = ?", bankItem));

        // người khác không thấy và không được tự lấy vào đề
        JsonNode theirs = json(performAs(otherTeacher, get("/api/bank-items")).andExpect(status().isOk()));
        assertEquals(0, theirs.get("totalItems").asInt());
        int otherMatrix = json(performAs(otherTeacher, post("/api/exam-matrices")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Ma trận B\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"cells\": ["
                        + mcqCell(TOPIC, null, 1) + "]}")).andExpect(status().isOk())).get("id").asInt();
        JsonNode built = json(performAs(otherTeacher, post("/api/exams/build")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"matrixId\": " + otherMatrix + "}")).andExpect(status().isOk()));
        assertEquals(0, built.get("fromBank").asInt());

        // chính người Giữ: danh sách có thẻ riêng
        JsonNode mine = json(perform(get("/api/bank-items")).andExpect(status().isOk()));
        assertEquals("private", mine.get("items").get(0).get("visibility").asText());
    }
}
