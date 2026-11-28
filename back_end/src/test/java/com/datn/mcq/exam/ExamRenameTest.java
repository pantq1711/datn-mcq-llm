package com.datn.mcq.exam;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đợt 3 (BE-3): PATCH /api/exams/{id} body {@code {name}} đổi tên đề. Chỉ chủ đề (người khác, đề không có hoặc đã xóa:
 * 404); tên được trim, rỗng 400, tối đa 255 ký tự. Đổi được ở mọi trạng thái đề (kể cả đang soạn). Bài giao đã có giữ
 * tên chụp lúc giao ({@code exam_assignment.title}, V32); danh sách, trang đề đọc tên mới.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamRenameTest extends ExamFlowTestBase {

    private int exam;

    @BeforeEach
    void setUp() throws Exception {
        exam = build(createMatrix(mcqCell("Đa thức", null, 2))).get("examId").asInt();
    }

    private ResultActions rename(int userId, int examId, String body) throws Exception {
        return performAs(userId, patch("/api/exams/" + examId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String title(int examId) {
        return jdbc.queryForObject("SELECT title FROM exam WHERE id = ?", String.class, examId);
    }

    @Test
    void chuDeDoiTenDuocKeCaKhiDeDangSoanTenDuocTrim() throws Exception {
        assertEquals("generating", progress(exam).get("status").asText(), "đề đang soạn vẫn đổi tên được");

        JsonNode renamed = json(rename(teacher, exam, "{\"name\": \"  Giữa kì I – 8A1  \"}").andExpect(status().isOk()));
        assertEquals(exam, renamed.get("examId").asInt());
        assertEquals("Giữa kì I – 8A1", renamed.get("title").asText());
        assertEquals("Giữa kì I – 8A1", title(exam));

        assertEquals("Giữa kì I – 8A1", json(perform(get("/api/exams/" + exam)).andExpect(status().isOk()))
                .get("title").asText());
        assertEquals("Giữa kì I – 8A1", json(perform(get("/api/exams")).andExpect(status().isOk()))
                .get("items").get(0).get("title").asText());
    }

    @Test
    void tenRongHoacQuaDaiTra400VaKhongDoiGi() throws Exception {
        String before = title(exam);
        rename(teacher, exam, "{\"name\": \"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        rename(teacher, exam, "{\"name\": \"\"}").andExpect(status().isBadRequest());
        rename(teacher, exam, "{}").andExpect(status().isBadRequest());
        rename(teacher, exam, "{\"name\": \"" + "ữ".repeat(256) + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("255")));
        assertEquals(before, title(exam));

        rename(teacher, exam, "{\"name\": \"" + "ữ".repeat(255) + "\"}").andExpect(status().isOk());
        assertEquals("ữ".repeat(255), title(exam));
    }

    @Test
    void deCuaNguoiKhacDeDaXoaHoacKhongCoTra404AdminTra403() throws Exception {
        String before = title(exam);
        rename(otherTeacher, exam, "{\"name\": \"Tên lạ\"}").andExpect(status().isNotFound());
        rename(teacher, 999_999, "{\"name\": \"Tên lạ\"}").andExpect(status().isNotFound());
        int admin = insert("INSERT INTO users (name, email, role) VALUES ('Quản trị', 'admin.rename@test', 'admin')");
        mockMvc.perform(patch("/api/exams/" + exam).with(AuthTestSupport.admin(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Tên lạ\"}"))
                .andExpect(status().isForbidden());
        assertEquals(before, title(exam));

        jdbc.update("UPDATE exam SET deleted_at = now() WHERE id = ?", exam);
        rename(teacher, exam, "{\"name\": \"Tên lạ\"}").andExpect(status().isNotFound());
        assertEquals(before, title(exam));
    }

    @Test
    void baiGiaoDaCoGiuTenChupLucGiao() throws Exception {
        int assignment = insert("INSERT INTO exam_assignment (exam_id, created_by, public_code, title, duration_minutes,"
                + " closes_at, show_score, scoring, consent_confirmed_at) VALUES (?, ?, 'ABCDEFGHJK', ?, 45,"
                + " now() + interval '1 day', TRUE, ?::jsonb, now())", exam, teacher, title(exam),
                "{\"mcq\": 0.25, \"sa\": 0.5, \"tf\": [0, 0.1, 0.25, 0.5, 1.0]}");
        jdbc.update("INSERT INTO assignment_class (assignment_id, class_label, roster_size, position)"
                + " VALUES (?, '8A1', 40, 1)", assignment);
        String snapshot = title(exam);

        rename(teacher, exam, "{\"name\": \"Tên mới sau khi giao\"}").andExpect(status().isOk());

        assertEquals(snapshot, jdbc.queryForObject("SELECT title FROM exam_assignment WHERE id = ?", String.class,
                assignment));
        JsonNode assignments = json(perform(get("/api/exams/" + exam + "/assignments")).andExpect(status().isOk()));
        assertEquals(snapshot, assignments.get(0).get("title").asText(), "bài giao hiện tên lúc giao");
        assertEquals(snapshot, json(mockMvc.perform(get("/api/public/assignments/ABCDEFGHJK"))
                .andExpect(status().isOk())).get("title").asText(), "trang học sinh hiện tên lúc giao");
        assertEquals("Tên mới sau khi giao", title(exam));
    }
}
