package com.datn.mcq.assignment;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A2 (docs/design/cau-co-hinh.md mục 4.5): gỡ {@code ASSIGN_HAS_FIGURE}. Đầu cuối qua HTTP thật: giáo viên giao đề có câu có
 * hình (ảnh gắn TRƯỚC khi giao, dữ liệu tự tạo) → 201; học sinh bắt đầu làm: {@code parts} có {@code images}, không có ảnh
 * lời giải, tải ảnh được (không JWT); nộp; sau khi đóng và hết ân hạn với {@code showSolutions}: {@code review} có ảnh lời
 * giải và tải được; thống kê câu (item_stat) tính cho câu có hình như mọi câu.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class AssignmentFigureFlowTest extends AssignmentTestBase {

    private static List<Long> imageIds(JsonNode question) {
        List<Long> ids = new ArrayList<>();
        if (question.has("images")) {
            for (JsonNode img : question.get("images")) {
                ids.add(img.get("id").asLong());
            }
        }
        return ids;
    }

    private static String imageUrl(JsonNode question, long imageId) {
        for (JsonNode img : question.get("images")) {
            if (img.get("id").asLong() == imageId) {
                return img.get("url").asText();
            }
        }
        throw new AssertionError("Không có ảnh " + imageId + " trong " + question);
    }

    private void loadsImage(String url) throws Exception {
        byte[] body = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray();
        assertArrayEquals(FAKE_PNG, body);
    }

    @Test
    void deCoCauHinhGiaoDuocHocSinhLamVaXemLaiCoHinhLoiGiai() throws Exception {
        long stem = attachImage(mcqItem1, "stem", 1);
        long optionC = attachImage(mcqItem1, "option_c", 2);
        long solution = attachImage(mcqItem1, "solution", 3);
        long statementB = attachImage(tfItem, "statement_b", 1);

        // Giáo viên giao bài: đề có câu có hình không còn bị chặn.
        JsonNode a = assign(true, true);
        int assignmentId = a.get("assignmentId").asInt();
        assertEquals(4, a.get("questionCount").asInt());

        // Học sinh bắt đầu làm: có hình đề và phương án, không có hình lời giải.
        JsonNode started = json(start(a.get("publicCode").asText(), "8A1", 1).andExpect(status().isCreated()));
        String token = started.get("attemptToken").asText();
        JsonNode parts = started.get("parts");
        JsonNode mcq1 = parts.get(0).get("items").get(0);
        assertEquals(List.of(stem, optionC), imageIds(mcq1));
        assertEquals(List.of(statementB), imageIds(parts.get(1).get("items").get(0)));
        assertFalse(parts.get(0).get("items").get(1).has("images"), "câu không hình: không có images");
        assertFalse(started.toString().contains("/question-images/" + solution + "?"), "không lộ ảnh lời giải");
        loadsImage(imageUrl(mcq1, stem));
        loadsImage(imageUrl(mcq1, optionC));
        loadsImage(imageUrl(parts.get(1).get("items").get(0), statementB));

        // Nộp bài như mọi đề.
        submit(token, sampleAnswers()).andExpect(status().isOk());

        // Đóng, hết ân hạn, showSolutions bật: phần xem lại có hình lời giải và tải được.
        closeAndPassGrace(assignmentId);
        JsonNode released = json(current(token).andExpect(status().isOk()));
        assertTrue(released.get("resultsReleased").asBoolean());
        JsonNode reviewMcq1 = released.get("review").get(0).get("items").get(0);
        assertEquals(List.of(stem, optionC, solution), imageIds(reviewMcq1));
        loadsImage(imageUrl(reviewMcq1, solution));
        loadsImage(imageUrl(reviewMcq1, stem));

        // Test khóa: item_stat (p, D) tính cho câu có hình theo vị trí và đáp án, không dùng chữ đề hay hình.
        JsonNode stats = json(asTeacher(get(base() + "/" + assignmentId + "/item-stats")).andExpect(status().isOk()));
        assertEquals(1, stats.get("n").asInt());
        JsonNode first = stats.get("items").get(0);
        assertEquals(mcqSlot1, first.get("slotId").asInt());
        assertEquals(mcqItem1, first.get("bankItemId").asInt());
        assertEquals(0, new BigDecimal("1").compareTo(first.get("p").decimalValue()), first.toString());
        assertFalse(stats.toString().contains("question-images"), "thống kê không mang ảnh");
        assertEquals(4, count("SELECT count(*) FROM item_stat WHERE assignment_id = ?", assignmentId));
    }
}
