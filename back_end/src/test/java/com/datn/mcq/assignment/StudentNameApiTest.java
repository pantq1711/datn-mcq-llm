package com.datn.mcq.assignment;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quyết định 25/11/2026 (thay quyết định 16/11 "không thu họ tên"): chế độ nhận diện theo từng bài giao
 * ({@code identity_mode} = 'name' | 'seat', bài mới mặc định 'name'); học sinh nhập họ tên; báo trùng lớp + tên (hoặc lớp +
 * số) TRƯỚC khi tạo lượt, học sinh xác nhận thì mới tạo; họ tên chỉ trả cho chủ bài giao và chính học sinh đó, không ghi log,
 * xóa theo bài giao.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class StudentNameApiTest extends AssignmentTestBase {

    private String token(JsonNode view) {
        return view.get("attemptToken").asText();
    }

    private JsonNode created(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return json(actions.andExpect(status().isCreated()));
    }

    // ---------- chế độ của bài giao ----------

    @Test
    void newAssignmentDefaultsToNameModeAndThePublicInfoTellsTheMode() throws Exception {
        JsonNode byDefault = assignMode(null, true, false);
        assertEquals("name", byDefault.get("identityMode").asText());
        assertEquals("name", jdbc.queryForObject("SELECT identity_mode FROM exam_assignment WHERE id = ?", String.class,
                byDefault.get("assignmentId").asInt()));
        JsonNode info = json(mockMvc.perform(get("/api/public/assignments/" + byDefault.get("publicCode").asText()))
                .andExpect(status().isOk()));
        assertEquals("name", info.get("identityMode").asText());

        JsonNode seat = assignMode(SEAT, true, false);
        assertEquals("seat", seat.get("identityMode").asText());
        assertEquals("seat", json(mockMvc.perform(get("/api/public/assignments/" + seat.get("publicCode").asText()))
                .andExpect(status().isOk())).get("identityMode").asText());
        assertEquals("name", assignMode(NAME, true, false).get("identityMode").asText());

        // T2 cũng cho biết chế độ.
        JsonNode list = json(asTeacher(get(base())).andExpect(status().isOk()));
        assertEquals(3, list.size());
        assertTrue(list.get(0).has("identityMode"));

        for (String bad : new String[] {"NAME", "ten", "", "both"}) {
            asTeacher(post(base()).contentType(MediaType.APPLICATION_JSON)
                    .content(createBody(45, Instant.now().plus(2, ChronoUnit.HOURS), true, false, true, DEFAULT_CLASSES,
                            bad)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertEquals(3, count("SELECT count(*) FROM exam_assignment"));
    }

    // ---------- bắt đầu ở chế độ họ tên ----------

    @Test
    void startInNameModeNormalizesAndStoresOnlyTheName() throws Exception {
        String code = assignMode(NAME, true, false).get("publicCode").asText();
        // Dạng tách dấu (NFD) + khoảng trắng thừa, kể cả khoảng trắng không ngắt; kèm seatNo (bị bỏ qua ở chế độ tên).
        String typed = "  Nguyễn   Văn An\t";
        Map<String, Object> body = new HashMap<>();
        body.put("classLabel", "8a1");
        body.put("studentName", typed);
        body.put("seatNo", 5);
        JsonNode view = created(startBody(code, body));
        assertEquals("Nguyễn Văn An", view.get("studentName").asText());
        assertEquals("name", view.get("identityMode").asText());
        assertEquals("8A1", view.get("classLabel").asText());
        assertFalse(view.has("seatNo"), view.toString());
        assertFalse(view.get("duplicateSeat").asBoolean());
        assertTrue(view.has("parts"));

        Map<String, Object> row = jdbc.queryForMap("SELECT student_name, seat_no FROM assignment_attempt");
        assertEquals("Nguyễn Văn An", row.get("student_name"));
        assertNull(row.get("seat_no"));

        // P3 của chính em có họ tên.
        JsonNode again = json(current(token(view)).andExpect(status().isOk()));
        assertEquals("Nguyễn Văn An", again.get("studentName").asText());
        assertEquals("name", again.get("identityMode").asText());
    }

    @Test
    void startInNameModeRejectsMissingOrMalformedNames() throws Exception {
        String code = assignMode(NAME, true, false).get("publicCode").asText();
        String tooLong = "A".repeat(61);
        for (String bad : new String[] {"", "   ", "A", " B ", tooLong, "Lan\u0007Anh", "Lan​Anh", "Lan\u0000"}) {
            String response = startName(code, "8A1", bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST")).andReturn().getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(bad.strip().length() >= 4 && response.contains(bad.strip()),
                    "không lặp lại tên trong lỗi: " + response);
        }
        // Chỉ có số thứ tự, không có họ tên.
        start(code, "8A1", 3).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEquals(0, count("SELECT count(*) FROM assignment_attempt"));

        // Đúng 2 và đúng 60 ký tự (tính theo ký tự, kể cả chữ có dấu) thì nhận.
        created(startName(code, "8A1", "Ơn"));
        created(startName(code, "8A1", "Ữ".repeat(60)));
        assertEquals(2, count("SELECT count(*) FROM assignment_attempt"));
    }

    // ---------- báo trùng trước khi bắt đầu ----------

    @Test
    void duplicateNameIsReportedBeforeAnyAttemptOrClockStarts() throws Exception {
        JsonNode a = assignMode(NAME, true, false);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        String first = token(created(startName(code, "8A1", "Nguyễn Văn An")));

        // Cùng lớp, khác hoa thường và khoảng trắng: trùng → 409, không tạo lượt.
        startName(code, "8a1", "  nguyễn  VĂN an ").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_IDENTITY_TAKEN"));
        startBody(code, Map.of("classLabel", "8A1", "studentName", "NGUYỄN VĂN AN", "confirmDuplicate", false))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_IDENTITY_TAKEN"));
        assertEquals(1, count("SELECT count(*) FROM assignment_attempt"));

        // Giữ dấu: không dấu là tên khác. Khác lớp: không trùng.
        assertFalse(created(startName(code, "8A1", "Nguyen Van An")).get("duplicateSeat").asBoolean());
        assertFalse(created(startName(code, "8A2", "Nguyễn Văn An")).get("duplicateSeat").asBoolean());

        // Xác nhận "Đúng là em, vẫn làm": tạo lượt mới, cả hai lượt bị đánh dấu, lượt cũ không bị ghi đè.
        JsonNode confirmed = created(startNameConfirmed(code, "8a1", "  nguyễn  VĂN an "));
        assertTrue(confirmed.get("duplicateSeat").asBoolean());
        assertEquals("nguyễn VĂN an", confirmed.get("studentName").asText(), "lưu như học sinh gõ, đã gộp khoảng trắng");
        assertTrue(json(current(first).andExpect(status().isOk())).get("duplicateSeat").asBoolean());
        assertEquals(4, count("SELECT count(*) FROM assignment_attempt"));

        JsonNode board = json(asTeacher(get(base() + "/" + id)).andExpect(status().isOk()));
        assertEquals(2, board.get("duplicateCount").asInt());
        int flagged = 0;
        for (JsonNode row : board.get("attempts")) {
            boolean sameName = row.get("classLabel").asText().equals("8A1")
                    && row.get("studentName").asText().toLowerCase(java.util.Locale.ROOT).equals("nguyễn văn an");
            assertEquals(sameName, row.get("duplicate").asBoolean(), row.toString());
            flagged += row.get("duplicate").asBoolean() ? 1 : 0;
        }
        assertEquals(2, flagged);
    }

    @Test
    void scoreboardInNameModeShowsNamesSortedByClassThenGivenName() throws Exception {
        JsonNode a = assignMode(NAME, true, false);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        for (String[] s : new String[][] {{"8A2", "Trần Văn Bình"}, {"8A1", "Phạm Thị Lan"}, {"8A1", "Nguyễn Văn An"},
                {"8A1", "Lê Văn Cường"}, {"8A1", "Đỗ Minh Anh"}, {"8A1", "Bùi Thị An"}}) {
            created(startName(code, s[0], s[1]));
        }
        JsonNode rows = json(asTeacher(get(base() + "/" + id)).andExpect(status().isOk())).get("attempts");
        List<String> order = new ArrayList<>();
        for (JsonNode row : rows) {
            order.add(row.get("classLabel").asText() + " " + row.get("studentName").asText());
            assertFalse(row.has("seatNo"), row.toString());
        }
        // Lớp theo thứ tự khai lúc giao (8A1, 8A2); trong lớp theo tên (chữ cuối) rồi họ và tên đệm, như sổ điểm.
        assertEquals(List.of("8A1 Bùi Thị An", "8A1 Nguyễn Văn An", "8A1 Đỗ Minh Anh", "8A1 Lê Văn Cường",
                "8A1 Phạm Thị Lan", "8A2 Trần Văn Bình"), order);
    }

    // ---------- bài giao chế độ số thứ tự ----------

    @Test
    void seatModeKeepsItsBehaviourAndNeverStoresAName() throws Exception {
        JsonNode a = assignMode(SEAT, true, false);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        JsonNode view = created(startBody(code, Map.of("classLabel", "8A1", "seatNo", 7, "studentName",
                "Nguyễn Văn An")));
        assertEquals(7, view.get("seatNo").asInt());
        assertEquals("seat", view.get("identityMode").asText());
        assertFalse(view.has("studentName"), view.toString());
        assertEquals(0, count("SELECT count(*) FROM assignment_attempt WHERE student_name IS NOT NULL"));

        // Chỉ có họ tên, không có số: 400 như trước.
        startName(code, "8A1", "Nguyễn Văn An").andExpect(status().isBadRequest());
        // Trùng số: báo trước, xác nhận thì tạo.
        start(code, "8A1", 7).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_IDENTITY_TAKEN"));
        assertTrue(created(startConfirmed(code, "8A1", 7)).get("duplicateSeat").asBoolean());

        JsonNode rows = json(asTeacher(get(base() + "/" + id)).andExpect(status().isOk())).get("attempts");
        assertEquals(2, rows.size());
        for (JsonNode row : rows) {
            assertEquals(7, row.get("seatNo").asInt());
            assertFalse(row.has("studentName"));
            assertTrue(row.get("duplicate").asBoolean());
        }
    }

    // ---------- quyền riêng tư ----------

    @Test
    void studentNameGoesOnlyToTheOwnerAndToThatStudentAndNeverToTheLog(CapturedOutput output) throws Exception {
        JsonNode a = assignMode(NAME, true, true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        String mine = token(created(startName(code, "8A1", "Đinh Zyxwq")));
        String other = token(created(startName(code, "8A1", "Lò Qopvu")));
        startName(code, "8A1", "đinh zyxwq").andExpect(status().isConflict());   // 409 có ghi WARN
        startName(code, "8A1", "Q").andExpect(status().isBadRequest());            // 400 có ghi WARN

        // P1 (trang chào) không có tên ai.
        String info = mockMvc.perform(get("/api/public/assignments/" + code)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(info.contains("Zyxwq") || info.contains("Qopvu"), info);
        // P3 của em chỉ có tên em.
        String p3 = json(current(mine).andExpect(status().isOk())).toString();
        assertTrue(p3.contains("Zyxwq"));
        assertFalse(p3.contains("Qopvu"), p3);
        // P4 không trả tên.
        String p4 = json(submit(other, sampleAnswers()).andExpect(status().isOk())).toString();
        assertFalse(p4.contains("Qopvu"), p4);
        // T2 (danh sách) không có bảng điểm nên không có tên.
        String t2 = json(asTeacher(get(base())).andExpect(status().isOk())).toString();
        assertFalse(t2.contains("Zyxwq") || t2.contains("Qopvu"), t2);
        // Giáo viên khác: 404, không có tên.
        String foreign = asUser(otherTeacher, get(base() + "/" + id)).andExpect(status().isNotFound()).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(foreign.contains("Zyxwq"), foreign);
        // Chủ bài giao thấy cả hai.
        String t3 = json(asTeacher(get(base() + "/" + id)).andExpect(status().isOk())).toString();
        assertTrue(t3.contains("Đinh Zyxwq") && t3.contains("Lò Qopvu"), t3);

        // Sau khi đóng: kết quả, xem lại của học sinh và phân tích câu không có tên người khác.
        closeAndPassGrace(id);
        String released = json(current(other).andExpect(status().isOk())).toString();
        assertFalse(released.contains("Zyxwq"), released);
        String stats = json(asTeacher(get(base() + "/" + id + "/item-stats")).andExpect(status().isOk())).toString();
        assertFalse(stats.contains("Zyxwq") || stats.contains("Qopvu"), stats);

        assertFalse(output.getAll().contains("Zyxwq") || output.getAll().contains("Qopvu"),
                "họ tên không được ghi vào log");
    }

    @Test
    void deletingTheAssignmentDeletesTheStudentNames() throws Exception {
        JsonNode a = assignMode(NAME, true, false);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        submit(token(created(startName(code, "8A1", "Nguyễn Văn An"))), sampleAnswers()).andExpect(status().isOk());
        created(startName(code, "8A2", "Trần Thị Bình"));
        assertEquals(2, count("SELECT count(*) FROM assignment_attempt WHERE student_name IS NOT NULL"));

        asTeacher(delete(base() + "/" + id)).andExpect(status().isNoContent());
        assertEquals(0, count("SELECT count(*) FROM assignment_attempt"));
        assertEquals(0, count("SELECT count(*) FROM attempt_answer"));
        assertEquals(0, count("SELECT count(*) FROM exam_assignment WHERE id = ?", id));
    }
}
