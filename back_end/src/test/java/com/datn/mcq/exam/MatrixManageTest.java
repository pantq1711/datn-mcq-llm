package com.datn.mcq.exam;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-4 bước 3: sửa, xóa, nhân bản ma trận. Ma trận chưa có đề: sửa/xóa tự do. Đã có đề: tên, nguồn, ghi chú vẫn sửa được;
 * sửa cấu trúc (môn, khối, ô) hoặc xóa trả 409 MATRIX_IN_USE (đề đã tạo đọc ô sống để sinh lại, nên không được đổi ô). Nhân
 * bản độc lập với bản gốc. Chủ sở hữu: người khác và ma trận ẩn của sinh theo bài đều 404.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class MatrixManageTest extends ExamFlowTestBase {

    private static final String TOPIC = "Đa thức";

    private String body(String name, String extra, String... cells) {
        return "{\"name\": \"" + name + "\", \"subjectId\": " + subject + ", \"gradeLevel\": 8"
                + (extra == null ? "" : ", " + extra) + ", \"cells\": [" + String.join(",", cells) + "]}";
    }

    private String url(int matrix) {
        return "/api/exam-matrices/" + matrix;
    }

    private JsonNode update(int matrix, String body) throws Exception {
        return json(perform(put(url(matrix)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()));
    }

    private JsonNode getMatrix(int matrix) throws Exception {
        return json(perform(get(url(matrix))).andExpect(status().isOk()));
    }

    private int templates() {
        return count("SELECT count(*) FROM question_template");
    }

    private int lessonMatrix() throws Exception {
        int examId = json(postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject + ", \"lesson\": \"Bài 1\","
                + " \"cells\": [{\"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 1}]}")
                .andExpect(status().isOk())).get("examId").asInt();
        return count("SELECT matrix_id FROM exam WHERE id = ?", examId);
    }

    // ---------- sửa ma trận chưa có đề ----------

    @Test
    void suaMaTranChuaCoDeThayToanBoOVaDonTemplateCu() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, "Đơn thức", 2), mcqCell("Tứ giác", null, 1));
        assertEquals(2, templates());
        JsonNode before = getMatrix(matrix);
        assertEquals(0, before.get("examCount").asInt());

        JsonNode after = update(matrix, body("Tên mới", "\"source\": \"SGK\", \"notes\": [\"ghi chú mới\"]",
                mcqCell("Phân thức", "Rút gọn", 3)));
        assertEquals("Tên mới", after.get("name").asText());
        assertEquals("SGK", after.get("source").asText());
        assertEquals("ghi chú mới", after.get("notes").get(0).asText());
        assertEquals(1, after.get("cells").size());
        assertEquals(1, after.get("cells").get(0).get("position").asInt());
        assertEquals("Phân thức", after.get("cells").get(0).get("topic").asText());
        assertEquals(3, after.get("cells").get(0).get("questionCount").asInt());
        assertEquals(3, after.get("totals").get("totalCommands").asInt());
        assertEquals(1, templates(), "template của hai ô cũ không còn ai dùng nên bị dọn, chỉ còn template ô mới");
        assertEquals("Phân thức", jdbc.queryForObject("SELECT t.topic FROM question_template t JOIN exam_matrix_cell c"
                + " ON c.template_id = t.id WHERE c.matrix_id = ?", String.class, matrix));
        assertEquals(after.get("id").asInt(), matrix, "cùng id ma trận");
    }

    @Test
    void suaMaTranKhongDonTemplateConDuocCauDaSinhThamChieu() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        int template = count("SELECT template_id FROM exam_matrix_cell WHERE matrix_id = ?", matrix);
        bankMcq(template, TOPIC, "easy", "Câu nguồn", "Câu ngân hàng", new String[] {"$1$", "$2$", "$3$", "$4$"});

        update(matrix, body("Ma trận thử", null, mcqCell("Phân thức", null, 1)));
        assertEquals(1, count("SELECT count(*) FROM question_template WHERE id = ?", template),
                "template còn câu sinh ra tham chiếu thì giữ lại (vết nguồn gốc câu ngân hàng)");
        assertEquals(2, templates());
    }

    @Test
    void suaDuLieuSaiTra400VaKhongDoiGi() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        JsonNode before = getMatrix(matrix);
        perform(put(url(matrix)).contentType(MediaType.APPLICATION_JSON).content(body("Tên", null)))
                .andExpect(status().isBadRequest());
        perform(put(url(matrix)).contentType(MediaType.APPLICATION_JSON).content(body(" ", null, mcqCell(TOPIC, null, 1))))
                .andExpect(status().isBadRequest());
        perform(put(url(matrix)).contentType(MediaType.APPLICATION_JSON)
                .content(body("Tên", null, mcqCell(TOPIC, null, 0)))).andExpect(status().isBadRequest());
        perform(put(url(matrix)).contentType(MediaType.APPLICATION_JSON)
                .content(body("Tên", null, mcqCell(TOPIC, null, 1)).replace("\"subjectId\": " + subject,
                        "\"subjectId\": 999999"))).andExpect(status().isNotFound());
        assertEquals(before, getMatrix(matrix));
        assertEquals(1, templates());
    }

    // ---------- sửa ma trận đã có đề ----------

    @Test
    void maTranDaCoDeChoSuaTenNguonGhiChuNhungKhongSuaO() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, "Đơn thức", 1));
        int examId = build(matrix).get("examId").asInt();
        JsonNode before = getMatrix(matrix);
        assertEquals(1, before.get("examCount").asInt());
        int cell = before.get("cells").get(0).get("id").asInt();
        int template = before.get("cells").get(0).get("templateId").asInt();

        JsonNode renamed = update(matrix, body("Tên khác", "\"source\": \"Nguồn\", \"notes\": [\"n\"]",
                mcqCell(TOPIC, "Đơn thức", 1)));
        assertEquals("Tên khác", renamed.get("name").asText());
        assertEquals("Nguồn", renamed.get("source").asText());
        assertEquals(cell, renamed.get("cells").get(0).get("id").asInt(), "ô giữ nguyên id");
        assertEquals(template, renamed.get("cells").get(0).get("templateId").asInt(), "template giữ nguyên");
        assertEquals(1, renamed.get("examCount").asInt());

        // Khoảng trắng thừa không phải thay đổi cấu trúc (so sau chuẩn hóa).
        update(matrix, body("Tên khác", null, mcqCell("  " + TOPIC + " ", " Đơn thức ", 1)));

        // Đề đã tạo vẫn xem và Đổi bình thường.
        perform(get("/api/exams/" + examId)).andExpect(status().isOk());
        int slot = count("SELECT id FROM exam_slot WHERE exam_id = ?", examId);
        settle(examId);
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/replace")).andExpect(status().isOk());
    }

    @Test
    void maTranDaCoDeSuaCauTrucTra409MatrixInUseVaKhongDoiGi() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, "Đơn thức", 2));
        build(matrix);
        JsonNode before = getMatrix(matrix);

        List<String> changes = List.of(
                body("Ma trận thử", null, mcqCell("Chủ đề khác", "Đơn thức", 2)),
                body("Ma trận thử", null, mcqCell(TOPIC, "Nội dung khác", 2)),
                body("Ma trận thử", null, mcqCell(TOPIC, "Đơn thức", 3)),
                body("Ma trận thử", null, mcqCell(TOPIC, "Đơn thức", 2), mcqCell(TOPIC, "Đơn thức", 1)),
                body("Ma trận thử", null, mcqCell(TOPIC, "Đơn thức", 2).replace("\"easy\": \"Nhận biết.\"",
                        "\"easy\": \"Yêu cầu khác.\"")),
                body("Ma trận thử", null, mcqCell(TOPIC, "Đơn thức", 2).replace("\"difficulty\": \"easy\"",
                        "\"difficulty\": \"medium\"").replace("\"easy\": \"Nhận biết.\"", "\"medium\": \"Hiểu.\"")),
                body("Ma trận thử", null, mcqCell(TOPIC, "Đơn thức", 2)).replace("\"gradeLevel\": 8",
                        "\"gradeLevel\": 9"));
        for (String change : changes) {
            JsonNode error = json(perform(put(url(matrix)).contentType(MediaType.APPLICATION_JSON).content(change))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATRIX_IN_USE")));
            assertTrue(error.get("error").asText().contains("1"), "thông báo nêu số đề: " + error);
        }
        assertEquals(before, getMatrix(matrix), "ma trận nguyên vẹn");
        assertEquals(1, templates());
    }

    // ---------- xóa ----------

    @Test
    void xoaMaTranChuaCoDeXoaOMaTranVaTemplate() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1), mcqCell("Tứ giác", null, 1));
        perform(delete(url(matrix))).andExpect(status().isNoContent());
        assertEquals(0, count("SELECT count(*) FROM exam_matrix WHERE id = ?", matrix));
        assertEquals(0, count("SELECT count(*) FROM exam_matrix_cell"));
        assertEquals(0, templates());
        perform(get(url(matrix))).andExpect(status().isNotFound());
        perform(get("/api/exam-matrices")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void xoaMaTranDaCoDeTra409MatrixInUseKhongXoaGi() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        build(matrix);
        perform(delete(url(matrix))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATRIX_IN_USE"));
        assertEquals(1, count("SELECT count(*) FROM exam_matrix WHERE id = ?", matrix));
        assertEquals(1, count("SELECT count(*) FROM exam_matrix_cell"));
        assertEquals(1, templates());
    }

    @Test
    void xoaMaTranGiuTemplateConCauThamChieu() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        int template = count("SELECT template_id FROM exam_matrix_cell WHERE matrix_id = ?", matrix);
        bankMcq(template, TOPIC, "easy", "Câu nguồn", "Câu ngân hàng", new String[] {"$1$", "$2$", "$3$", "$4$"});
        perform(delete(url(matrix))).andExpect(status().isNoContent());
        assertEquals(1, count("SELECT count(*) FROM question_template WHERE id = ?", template));
    }

    // ---------- nhân bản ----------

    @Test
    void nhanBanTaoMaTranMoiGiongBanGocVaDocLap() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, "Đơn thức", 2), mcqCell("Tứ giác", null, 1));
        update(matrix, body("Gốc", "\"source\": \"SGK\", \"notes\": [\"ghi chú\"]", mcqCell(TOPIC, "Đơn thức", 2),
                mcqCell("Tứ giác", null, 1)));
        build(matrix); // bản gốc đã có đề: vẫn nhân bản được
        JsonNode original = getMatrix(matrix);

        JsonNode copy = json(perform(post(url(matrix) + "/duplicate")).andExpect(status().isOk()));
        int copyId = copy.get("id").asInt();
        assertNotEquals(matrix, copyId);
        assertEquals("Gốc (bản sao)", copy.get("name").asText());
        assertEquals(0, copy.get("examCount").asInt());
        assertEquals("SGK", copy.get("source").asText());
        assertEquals(original.get("notes"), copy.get("notes"));
        assertEquals(original.get("totals"), copy.get("totals"));
        assertEquals(2, copy.get("cells").size());
        for (int i = 0; i < 2; i++) {
            JsonNode a = original.get("cells").get(i);
            JsonNode b = copy.get("cells").get(i);
            for (String field : List.of("position", "part", "questionType", "topic", "content", "learningOutcomes",
                    "difficulty", "questionCount")) {
                assertEquals(a.get(field), b.get(field), field);
            }
            assertNotEquals(a.get("id"), b.get("id"));
            assertNotEquals(a.get("templateId"), b.get("templateId"), "mỗi ô bản sao có template riêng");
        }
        assertEquals(4, templates());
        assertEquals(teacher, count("SELECT created_by FROM exam_matrix WHERE id = ?", copyId));
        assertEquals("matrix", jdbc.queryForObject("SELECT kind FROM exam_matrix WHERE id = ?", String.class, copyId));

        // Độc lập: sửa bản sao không đổi bản gốc; xóa bản sao không đụng bản gốc và template của nó.
        update(copyId, body("Bản sao đã sửa", null, mcqCell("Phân thức", null, 5)));
        assertEquals(original, getMatrix(matrix));
        perform(delete(url(copyId))).andExpect(status().isNoContent());
        assertEquals(original, getMatrix(matrix));
        assertEquals(2, templates());
        // Sửa/xóa bản gốc (đã có đề) cũng không ảnh hưởng bản sao khác.
        JsonNode second = json(perform(post(url(matrix) + "/duplicate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Bản của tôi\"}")).andExpect(status().isOk()));
        assertEquals("Bản của tôi", second.get("name").asText());
        perform(delete(url(matrix))).andExpect(status().isConflict());
        perform(get(url(second.get("id").asInt()))).andExpect(status().isOk());
    }

    @Test
    void nhanBanCatTenDaiVeToiDaBaTramNamMuoiLamKyTu() throws Exception {
        int matrix = json(postJson("/api/exam-matrices", body("x".repeat(255), null, mcqCell(TOPIC, null, 1)))
                .andExpect(status().isOk())).get("id").asInt();
        JsonNode copy = json(perform(post(url(matrix) + "/duplicate")).andExpect(status().isOk()));
        assertTrue(copy.get("name").asText().length() <= 255, "độ dài " + copy.get("name").asText().length());
        assertTrue(copy.get("name").asText().endsWith("(bản sao)"));
    }

    @Test
    void nhanBanTenQuaDaiTra400() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        perform(post(url(matrix) + "/duplicate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + "y".repeat(256) + "\"}")).andExpect(status().isBadRequest());
        assertEquals(1, count("SELECT count(*) FROM exam_matrix"));
    }

    // ---------- số đề trong danh sách ----------

    @Test
    void danhSachVaChiTietCoExamCount() throws Exception {
        int used = createMatrix(mcqCell(TOPIC, null, 1));
        int unused = createMatrix(mcqCell("Tứ giác", null, 1));
        build(used);
        build(used);
        JsonNode list = json(perform(get("/api/exam-matrices")).andExpect(status().isOk()));
        assertEquals(2, list.size());
        for (JsonNode m : list) {
            assertEquals(m.get("id").asInt() == used ? 2 : 0, m.get("examCount").asInt());
        }
        assertEquals(0, getMatrix(unused).get("examCount").asInt());
    }

    // ---------- chủ sở hữu ----------

    @Test
    void giaoVienKhacVaMaTranAn404ChoCaBaThaoTac() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        build(matrix);
        String update = body("Chiếm đoạt", null, mcqCell("Khác", null, 1));
        performAs(otherTeacher, put(url(matrix)).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isNotFound());
        performAs(otherTeacher, delete(url(matrix))).andExpect(status().isNotFound());
        performAs(otherTeacher, post(url(matrix) + "/duplicate")).andExpect(status().isNotFound());
        assertEquals("Ma trận thử", getMatrix(matrix).get("name").asText());
        assertEquals(1, count("SELECT count(*) FROM exam_matrix"));

        int hidden = lessonMatrix();
        perform(put(url(hidden)).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isNotFound());
        perform(delete(url(hidden))).andExpect(status().isNotFound());
        perform(post(url(hidden) + "/duplicate")).andExpect(status().isNotFound());
        assertEquals("lesson", jdbc.queryForObject("SELECT kind FROM exam_matrix WHERE id = ?", String.class, hidden));
    }

    @Test
    void adminBiTuChoiBaThaoTacMoi() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        int admin = AuthTestSupport.insertUser(jdbc, "Admin", "admin.matrix@test", "admin", "matkhau-admin-12", true);
        mockMvc.perform(put(url(matrix)).with(AuthTestSupport.admin(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(body("x", null, mcqCell(TOPIC, null, 1)))).andExpect(status().isForbidden());
        mockMvc.perform(delete(url(matrix)).with(AuthTestSupport.admin(admin))).andExpect(status().isForbidden());
        mockMvc.perform(post(url(matrix) + "/duplicate").with(AuthTestSupport.admin(admin)))
                .andExpect(status().isForbidden());
    }

    // ---------- K-5 với ngoại lệ thật của PostgreSQL ----------

    private org.springframework.http.ResponseEntity<java.util.Map<String, String>> handle(Runnable violation) {
        try {
            violation.run();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            return new com.datn.mcq.common.exception.ApiExceptionHandler().handleDataIntegrity(ex);
        }
        throw new AssertionError("câu lệnh phải vi phạm ràng buộc");
    }

    @Test
    void loiRangBuocThatCuaPostgresDuocAnhXaDungMaHttp() {
        String insert = "INSERT INTO exam (exam_code, title, subject_id, created_by, mode, matrix_id) VALUES (?, 'x', ?,"
                + " ?, 'teacher', ?)";
        var fk = handle(() -> jdbc.update(insert, "FK1", subject, teacher, 999999));
        assertEquals(409, fk.getStatusCode().value());
        assertEquals("FOREIGN_KEY_CONFLICT", fk.getBody().get("code"));

        int matrix = jdbc.queryForObject("INSERT INTO exam_matrix (name, subject_id, created_by) VALUES ('M', ?, ?)"
                + " RETURNING id", Integer.class, subject, teacher);
        jdbc.update(insert, "DUP1", subject, teacher, matrix);
        var unique = handle(() -> jdbc.update(insert, "DUP1", subject, teacher, matrix));
        assertEquals(409, unique.getStatusCode().value());
        assertEquals("UNIQUE_CONFLICT", unique.getBody().get("code"));

        var check = handle(() -> jdbc.update("INSERT INTO exam_matrix (name, subject_id, created_by, kind) VALUES ('K', ?,"
                + " ?, 'khong-co')", subject, teacher));
        assertEquals(500, check.getStatusCode().value());
        assertEquals("DATA_INTEGRITY", check.getBody().get("code"));
    }

    // ---------- tranh chấp: tạo đề và xóa ma trận đồng thời không thành 500 ----------

    /** Đợi tới khi một phiên khác đang chờ khóa (tối đa 15 giây). */
    private void awaitLockWait() throws Exception {
        for (int i = 0; i < 150; i++) {
            if (count("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                    + " AND wait_event_type = 'Lock'") > 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("không có phiên nào chờ khóa");
    }

    /** Một lần xóa ma trận đang dở (chưa commit) thì tạo đề chờ rồi nhận 404 (ma trận không còn), không phải 500. */
    @Test
    void taoDeLucDangXoaMaTranChoXongRoiTra404() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT id FROM exam_matrix WHERE id = " + matrix + " FOR UPDATE");
                st.execute("DELETE FROM exam_matrix_cell WHERE matrix_id = " + matrix);
                st.execute("DELETE FROM exam_matrix WHERE id = " + matrix);
            }
            Future<Integer> build = pool.submit(() -> postJson("/api/exams/build", "{\"matrixId\": " + matrix + "}")
                    .andReturn().getResponse().getStatus());
            awaitLockWait();
            c.commit();
            assertEquals(404, build.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, count("SELECT count(*) FROM exam"));
    }

    /** Một lần tạo đề đang dở (đã ghi đề, chưa commit) thì xóa ma trận chờ rồi nhận 409 MATRIX_IN_USE, không phải 500. */
    @Test
    void xoaMaTranLucDangTaoDeChoXongRoiTra409() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("INSERT INTO exam (exam_code, title, subject_id, created_by, mode, matrix_id) VALUES"
                        + " ('RACE-DEL', 'Đề đang tạo', " + subject + ", " + teacher + ", 'teacher', " + matrix + ")");
            }
            Future<List<Object>> delete = pool.submit(() -> {
                var response = perform(delete(url(matrix))).andReturn().getResponse();
                List<Object> out = new ArrayList<>();
                out.add(response.getStatus());
                out.add(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                return out;
            });
            awaitLockWait();
            c.commit();
            List<Object> result = delete.get(30, TimeUnit.SECONDS);
            assertEquals(409, result.get(0));
            assertTrue(result.get(1).toString().contains("MATRIX_IN_USE"), result.get(1).toString());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, count("SELECT count(*) FROM exam_matrix WHERE id = ?", matrix));
    }
}
