package com.datn.mcq.exam;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NV-21 / NV-30 / NV-28 (quyết định 16/11, mục 3): cấu hình mặc định {@code app.bank.keep-visibility = shared} giữ hành vi
 * cũ; {@code GET /api/bank-items} trả {@code visibility} từng câu; "Gỡ khỏi ngân hàng" ({@code DELETE /api/bank-items/{id}})
 * là ẩn mềm: câu không còn trong ngân hàng, không được chọn cho đề mới, nhưng đề cũ có câu đó vẫn hiển thị. Chỉ người Giữ
 * câu (teacher_review.reviewer_id) hoặc admin được gỡ.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class BankRemoveTest extends ExamFlowTestBase {

    private static final String TOPIC = "Đa thức";

    /** Đề một chỗ, sinh xong, Giữ câu; trả {examId, slotId, bankItemId}. */
    private int[] keptExam() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrix, 1), 1);
        JsonNode kept = json(perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep"))
                .andExpect(status().isOk()));
        return new int[] {examId, slot, kept.get("bankItemId").asInt()};
    }

    // ---------- cấu hình mặc định, visibility ----------

    @Test
    void macDinhGiuCauKhongCoVaoNganHangChungVaBaoRo() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals("shared", view.get("bankKeepVisibility").asText());
        int slot = slotId(examId, cellId(matrix, 1), 1);
        JsonNode kept = json(perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep"))
                .andExpect(status().isOk()));
        assertEquals("shared", kept.get("bankVisibility").asText());
        assertNull(jdbc.queryForObject("SELECT visible_to_user_id FROM bank_item WHERE id = ?", Integer.class,
                kept.get("bankItemId").asInt()));
    }

    @Test
    void danhSachNganHangTraVisibilityTungCau() throws Exception {
        int shared = bankMcq(TOPIC, "easy", "Câu chung");
        int priv = bankMcq(TOPIC, "easy", "Câu riêng");
        jdbc.update("UPDATE bank_item SET visible_to_user_id = ? WHERE id = ?", teacher, priv);
        JsonNode page = json(perform(get("/api/bank-items")).andExpect(status().isOk()));
        assertEquals(2, page.get("totalItems").asInt());
        for (JsonNode item : page.get("items")) {
            assertEquals(item.get("id").asInt() == shared ? "shared" : "private", item.get("visibility").asText());
        }
        JsonNode detail = json(perform(get("/api/bank-items/" + priv)).andExpect(status().isOk()));
        assertEquals("private", detail.get("visibility").asText());
    }

    // ---------- gỡ khỏi ngân hàng ----------

    @Test
    void nguoiGiuGoCauKhoiNganHangDeCuVanHienCau() throws Exception {
        int[] e = keptExam();
        JsonNode detail = json(perform(get("/api/bank-items/" + e[2])).andExpect(status().isOk()));
        assertEquals(true, detail.get("canRemove").asBoolean());

        perform(delete("/api/bank-items/" + e[2])).andExpect(status().isNoContent());

        assertNotNull(jdbc.queryForObject("SELECT removed_at FROM bank_item WHERE id = ?", Object.class, e[2]));
        assertEquals(teacher, count("SELECT removed_by FROM bank_item WHERE id = ?", e[2]));
        assertEquals(1, count("SELECT count(*) FROM bank_item WHERE id = ?", e[2]), "ẩn mềm, không xóa vật lý");
        // không còn trong ngân hàng
        assertEquals(0, json(perform(get("/api/bank-items")).andExpect(status().isOk())).get("totalItems").asInt());
        perform(get("/api/bank-items/" + e[2])).andExpect(status().isNotFound());
        assertEquals(0, json(perform(get("/api/bank-items/topics")).andExpect(status().isOk())).size());
        // đề cũ vẫn hiển thị câu
        JsonNode view = json(perform(get("/api/exams/" + e[0])).andExpect(status().isOk()));
        JsonNode item = view.get("parts").get(0).get("items").get(0);
        assertEquals(e[2], item.get("bankItemId").asInt());
        assertNotNull(item.get("questionText").asText(null));
        // gỡ lần nữa: không còn trong ngân hàng
        perform(delete("/api/bank-items/" + e[2])).andExpect(status().isNotFound());
    }

    @Test
    void cauDaGoKhongDuocChonChoDeMoiVaDoiLayTuNganHang() throws Exception {
        int removed = bankMcq(TOPIC, "easy", "Câu sẽ gỡ");
        perform(delete("/api/bank-items/" + removed)).andExpect(status().isNoContent());
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        JsonNode built = build(matrix);
        assertEquals(0, built.get("fromBank").asInt(), "câu đã gỡ không vào đề mới");
        int examId = built.get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrix, 1), 1);
        postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{\"source\": \"bank\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BANK_EXHAUSTED"));
    }

    @Test
    void giaoVienKhacKhongGoDuocCauChungCuaNguoiKhac() throws Exception {
        int item = bankMcq(TOPIC, "easy", "Câu chung của cô An");
        JsonNode detail = json(performAs(otherTeacher, get("/api/bank-items/" + item)).andExpect(status().isOk()));
        assertEquals(false, detail.get("canRemove").asBoolean());
        performAs(otherTeacher, delete("/api/bank-items/" + item)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BANK_ITEM_NOT_OWNER"));
        assertNull(jdbc.queryForObject("SELECT removed_at FROM bank_item WHERE id = ?", Object.class, item));
    }

    @Test
    void cauRiengCuaNguoiKhacLa404() throws Exception {
        int item = bankMcq(TOPIC, "easy", "Câu riêng của cô An");
        jdbc.update("UPDATE bank_item SET visible_to_user_id = ? WHERE id = ?", teacher, item);
        performAs(otherTeacher, delete("/api/bank-items/" + item)).andExpect(status().isNotFound());
        perform(delete("/api/bank-items/999999")).andExpect(status().isNotFound());
        assertNull(jdbc.queryForObject("SELECT removed_at FROM bank_item WHERE id = ?", Object.class, item));
    }

    @Test
    void adminGoDuocMoiCauChung() throws Exception {
        int admin = AuthTestSupport.insertUser(jdbc, "Quản trị", "admin.remove@test", "admin", "matkhau-admin-1", true);
        int item = bankMcq(TOPIC, "easy", "Câu chung");
        JsonNode detail = json(mockMvc.perform(get("/api/bank-items/" + item).with(AuthTestSupport.admin(admin)))
                .andExpect(status().isOk()));
        assertEquals(true, detail.get("canRemove").asBoolean());
        mockMvc.perform(delete("/api/bank-items/" + item).with(AuthTestSupport.admin(admin)))
                .andExpect(status().isNoContent());
        assertEquals(admin, count("SELECT removed_by FROM bank_item WHERE id = ?", item));
    }

    @Test
    void chuaDangNhapLa401() throws Exception {
        int item = bankMcq(TOPIC, "easy", "Câu chung");
        mockMvc.perform(delete("/api/bank-items/" + item)).andExpect(status().isUnauthorized());
    }
}
