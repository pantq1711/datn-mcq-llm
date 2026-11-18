package com.datn.mcq.assignment;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phân tích câu của bài giao (PR B, docs/design/lam-bai-truc-tuyen.md mục 5 và 14): B1 {@code GET …/item-stats}, B2
 * {@code POST …/item-stats/recompute}. Chỉ tính lượt đã nộp và không trùng lớp + số thứ tự (cùng quy tắc với bảng điểm);
 * chỉ có sau khi bài đóng VÀ hết ân hạn nộp 2 phút; xóa lượt sau khi đã tính thì thống kê bị vô hiệu và lần đọc sau tính lại.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ItemStatApiTest extends AssignmentTestBase {

    private String stats(int assignmentId) {
        return base() + "/" + assignmentId + "/item-stats";
    }

    private JsonNode readStats(int assignmentId) throws Exception {
        return json(asTeacher(get(stats(assignmentId))).andExpect(status().isOk()));
    }

    /** Một lượt nộp chỉ trả lời câu trắc nghiệm 1 bằng {@code choice} (các câu khác bỏ trống). */
    private String submitMcq1(String code, String classLabel, int seat, String choice) throws Exception {
        String token = startToken(code, classLabel, seat);
        submit(token, "[{\"slotId\": " + mcqSlot1 + ", \"choice\": \"" + choice + "\"}]").andExpect(status().isOk());
        return token;
    }

    private Map<Integer, JsonNode> bySlot(JsonNode stats) {
        Map<Integer, JsonNode> out = new HashMap<>();
        for (JsonNode item : stats.get("items")) {
            out.put(item.get("slotId").asInt(), item);
        }
        return out;
    }

    private static void assertDec(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), "mong đợi " + expected + ": " + actual);
    }

    private int attemptId(int assignmentId, String classLabel, int seat, int index) {
        return jdbc.queryForList("SELECT id FROM assignment_attempt WHERE assignment_id = ? AND class_label = ?"
                + " AND seat_no = ? ORDER BY id", Integer.class, assignmentId, classLabel, seat).get(index);
    }

    @Test
    void chiCoSauKhiDongVaHetAnHan() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        submitMcq1(a.get("publicCode").asText(), "8A1", 1, "B");

        asTeacher(get(stats(id))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSIGNMENT_OPEN"));
        asTeacher(post(stats(id) + "/recompute")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_OPEN"));

        // Vừa đóng: lượt có hạn không muộn hơn lúc đóng còn nộp được trong 2 phút ân hạn, nên chưa tính.
        asTeacher(post(base() + "/" + id + "/close")).andExpect(status().isOk());
        JsonNode notReady = json(asTeacher(get(stats(id))).andExpect(status().isConflict()));
        assertEquals("STATS_NOT_READY", notReady.get("code").asText());
        assertEquals(1, notReady.get("errors").size(), "errors[0] là lúc thống kê có (readyAt): " + notReady);
        assertEquals(0, count("SELECT count(*) FROM item_stat"));
        assertEquals(0, count("SELECT count(*) FROM exam_assignment WHERE stats_computed_at IS NOT NULL"));

        jdbc.update("UPDATE exam_assignment SET closed_at = closed_at - interval '3 minutes',"
                + " closes_at = closes_at - interval '3 minutes' WHERE id = ?", id);
        JsonNode s = readStats(id);
        assertEquals(id, s.get("assignmentId").asInt());
        assertEquals(1, s.get("n").asInt());
        assertEquals(0, s.get("groupSize").asInt());
        assertNotNull(s.get("computedAt").asText(null));
        assertEquals(4, count("SELECT count(*) FROM item_stat WHERE assignment_id = ?", id));
        assertEquals(1, count("SELECT count(*) FROM exam_assignment WHERE stats_computed_at IS NOT NULL"));

        // Thứ tự trang đề, đủ trường; đáp án để giáo viên in đậm.
        JsonNode items = s.get("items");
        assertEquals(4, items.size());
        assertEquals(mcqSlot1, items.get(0).get("slotId").asInt());
        assertEquals("I", items.get(0).get("part").asText());
        assertEquals(1, items.get(0).get("number").asInt());
        assertEquals("mcq", items.get(0).get("questionType").asText());
        assertEquals(mcqItem1, items.get(0).get("bankItemId").asInt());
        assertEquals("B", items.get(0).get("answerKey").asText());
        assertEquals("TFFT", items.get(2).get("answerKey").asText());
        assertEquals("2,5", items.get(3).get("answerKey").asText());
        assertTrue(items.get(0).get("d").isNull(), "k = 0 thì D = null");
        assertFalse(items.get(0).get("slotChanged").asBoolean());

        // T2 có statsComputedAt.
        JsonNode list = json(asTeacher(get(base())).andExpect(status().isOk()));
        assertNotNull(list.get(0).get("statsComputedAt").asText(null));
    }

    /** Yêu cầu 1, 2 và 4: lượt trùng (kể cả trùng với lượt bỏ dở) và lượt chưa nộp không tính; n &lt; 20 báo chưa đủ mẫu. */
    @Test
    void boLuotTrungVaLuotChuaNopVaBaoMauNho() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        submitMcq1(code, "8A1", 1, "B");            // hợp lệ, đúng
        submitMcq1(code, "8A2", 1, "A");            // hợp lệ, sai
        submitMcq1(code, "8A1", 2, "C");            // trùng (cả hai đã nộp)
        submitMcq1(code, "8A1", 2, "C");
        submitMcq1(code, "8A1", 3, "C");            // trùng với một lượt bỏ dở
        startToken(code, "8A1", 3);
        startToken(code, "8A1", 4);                 // chưa nộp
        closeAndPassGrace(id);

        JsonNode s = readStats(id);
        assertEquals(2, s.get("n").asInt());
        assertEquals(1, s.get("groupSize").asInt());
        assertEquals(3, s.get("excluded").get("duplicate").asInt(), "lượt đã nộp bị loại vì trùng: " + s);
        assertEquals(2, s.get("excluded").get("notSubmitted").asInt());
        assertFalse(s.get("sampleSufficient").asBoolean());
        assertEquals(20, s.get("minSampleSize").asInt());
        assertTrue(s.get("sampleNote").asText().contains("20"), s.get("sampleNote").asText());

        JsonNode m1 = bySlot(s).get(mcqSlot1);
        assertDec("0.5", m1.get("p"));
        assertDec("1.0", m1.get("d"));
        assertEquals("[\"insufficient_sample\"]", m1.get("flags").toString());
        JsonNode counts = m1.get("choiceCounts");
        assertEquals(0, counts.get("C").get("all").asInt(), "lựa chọn của lượt trùng không được đếm");
        assertEquals(1, counts.get("A").get("all").asInt());
        assertEquals(1, counts.get("A").get("low").asInt());
        assertEquals(1, counts.get("B").get("high").asInt());
    }

    /** Yêu cầu 3: xóa lượt (một hoặc nhiều) sau khi đã tính thì thống kê bị vô hiệu; lần đọc sau tính lại. */
    @Test
    void xoaLuotSauKhiTinhThiVoHieuVaTinhLai() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        submitMcq1(code, "8A1", 1, "B");
        submitMcq1(code, "8A1", 2, "A");
        submitMcq1(code, "8A1", 2, "B");
        closeAndPassGrace(id);
        assertEquals(1, readStats(id).get("n").asInt(), "cặp trùng số 2 bị loại");

        // Xóa lượt nhầm của cặp trùng: lượt còn lại hết cờ trùng và được tính.
        asTeacher(delete(base() + "/" + id + "/attempts/" + attemptId(id, "8A1", 2, 0)))
                .andExpect(status().isNoContent());
        assertEquals(0, count("SELECT count(*) FROM item_stat WHERE assignment_id = ?", id));
        assertEquals(0, count("SELECT count(*) FROM exam_assignment WHERE id = ? AND stats_computed_at IS NOT NULL", id));
        JsonNode after = readStats(id);
        assertEquals(2, after.get("n").asInt());
        assertEquals(0, after.get("excluded").get("duplicate").asInt());
        assertDec("1.0", bySlot(after).get(mcqSlot1).get("p"));

        // Xóa nhiều lượt cũng vô hiệu.
        asTeacher(post(base() + "/" + id + "/attempts/bulk-delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"attemptIds\": [" + attemptId(id, "8A1", 1, 0) + "]}")).andExpect(status().isOk());
        assertEquals(0, count("SELECT count(*) FROM item_stat WHERE assignment_id = ?", id));
        assertEquals(1, readStats(id).get("n").asInt());
    }

    @Test
    void tinhLaiChoCungKetQua() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        submitMcq1(code, "8A1", 1, "B");
        submit(startToken(code, "8A1", 2), sampleAnswers()).andExpect(status().isOk());
        submitMcq1(code, "8A1", 3, "D");
        closeAndPassGrace(id);

        JsonNode first = readStats(id);
        JsonNode again = json(asTeacher(post(stats(id) + "/recompute")).andExpect(status().isOk()));
        assertEquals(first.get("items").toString(), again.get("items").toString());
        assertEquals(first.get("n").asInt(), again.get("n").asInt());
        assertEquals(again.get("computedAt").asText(), readStats(id).get("computedAt").asText(),
                "đọc sau khi tính lại không tính thêm lần nữa");
    }

    @Test
    void danhDauChoDaDoiCauSauKhiGiao() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        submitMcq1(a.get("publicCode").asText(), "8A1", 1, "B");
        closeAndPassGrace(id);
        int other = bankMcq(template("Đa thức khác"), "Câu thay thế");
        jdbc.update("UPDATE exam_slot SET bank_item_id = ? WHERE id = ?", other, mcqSlot2);

        Map<Integer, JsonNode> items = bySlot(readStats(id));
        assertTrue(items.get(mcqSlot2).get("slotChanged").asBoolean());
        assertEquals(mcqItem2, items.get(mcqSlot2).get("bankItemId").asInt(), "câu học sinh đã làm");
        assertFalse(items.get(mcqSlot1).get("slotChanged").asBoolean());
    }

    @Test
    void xoaBaiGiaoXoaThongKe() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        submitMcq1(a.get("publicCode").asText(), "8A1", 1, "B");
        closeAndPassGrace(id);
        readStats(id);
        assertEquals(4, count("SELECT count(*) FROM item_stat"));
        asTeacher(delete(base() + "/" + id)).andExpect(status().isNoContent());
        assertEquals(0, count("SELECT count(*) FROM item_stat"));
    }

    @Test
    void chiChuDeXemDuoc() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        closeAndPassGrace(id);
        mockMvc.perform(get(stats(id))).andExpect(status().isUnauthorized());
        mockMvc.perform(post(stats(id) + "/recompute")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(stats(id)).with(AuthTestSupport.admin(admin))).andExpect(status().isForbidden());
        asUser(otherTeacher, get(stats(id))).andExpect(status().isNotFound());
        asUser(otherTeacher, post(stats(id) + "/recompute")).andExpect(status().isNotFound());
        assertEquals(0, count("SELECT count(*) FROM exam_assignment WHERE stats_computed_at IS NOT NULL"));
    }

    /** Ràng buộc của V33. */
    @Test
    void rangBuocItemStat() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String insert = "INSERT INTO item_stat (assignment_id, slot_id, bank_item_id, question_type, n, group_size, p, d,"
                + " choice_counts, flags, computed_at) VALUES (?, ?, ?, 'mcq', ?, ?, ?, ?, '{}'::jsonb, '[]'::jsonb,"
                + " now())";
        jdbc.update(insert, id, mcqSlot1, mcqItem1, 1, 0, 1, null);
        for (Object[] bad : new Object[][] {
                {mcqSlot2, 0, 0, 0, null},            // n = 0
                {mcqSlot2, 2, 0, 0.5, 0.1},           // k = 0 mà có D
                {mcqSlot2, 2, 1, 0.5, null},          // k > 0 mà D null
                {mcqSlot2, 2, 2, 0.5, 0.1},           // 2k > n
                {mcqSlot2, 2, 1, 1.5, 0.1}}) {        // p > 1
            try {
                jdbc.update(insert, id, bad[0], mcqItem2, bad[1], bad[2], bad[3], bad[4]);
                throw new AssertionError("phải bị CHECK chặn: " + java.util.Arrays.toString(bad));
            } catch (org.springframework.dao.DataIntegrityViolationException expected) {
                // đúng
            }
        }
    }
}
