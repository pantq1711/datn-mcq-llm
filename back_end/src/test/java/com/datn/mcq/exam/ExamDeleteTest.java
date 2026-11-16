package com.datn.mcq.exam;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NV-01, NV-55 (rà soát 15/11): giáo viên xóa đề của mình (xóa mềm, {@code exam.deleted_at}); hệ quả trên ma trận (xóa mềm
 * khi chỉ còn đề đã xóa, sửa cấu trúc vẫn 409), tài liệu (xóa mềm khi chỉ còn đề đã xóa, trần 50 không đếm, tải lại thì khôi
 * phục) và số "đã dùng" của câu ngân hàng. Không xóa vật lý dòng nào của đề. HTTP thật, LLM và Solver giả.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamDeleteTest extends ExamFlowTestBase {

    private static final String TOPIC = "Đa thức";
    private static final String LESSON_TEXT = "Bài 3. Hằng đẳng thức đáng nhớ: bình phương của một tổng bằng bình phương"
            + " số thứ nhất cộng hai lần tích hai số cộng bình phương số thứ hai.";

    private int sha;

    // ---------- tiện ích ----------

    /** Ma trận một ô mcq dễ, ngân hàng có sẵn một câu khớp: đề tạo ra lấy câu ngân hàng, không xếp job sinh câu. */
    private int[] bankOnlyExam() throws Exception {
        int bankItem = bankMcq(TOPIC, "easy", "Câu ngân hàng " + (++sha));
        int matrix = createMatrix(mcqCell(TOPIC, "Đơn thức", 1));
        JsonNode built = build(matrix);
        assertEquals(1, built.get("fromBank").asInt());
        assertEquals(0, built.get("jobsQueued").asInt());
        return new int[] {built.get("examId").asInt(), matrix, bankItem};
    }

    private ResultActions deleteExam(int examId) throws Exception {
        return perform(delete("/api/exams/" + examId));
    }

    private Object deletedAt(String table, int id) {
        return jdbc.queryForObject("SELECT deleted_at FROM " + table + " WHERE id = ?", Object.class, id);
    }

    private List<Integer> myExamIds() throws Exception {
        List<Integer> out = new ArrayList<>();
        json(perform(get("/api/exams?size=50")).andExpect(status().isOk())).get("items")
                .forEach(e -> out.add(e.get("examId").asInt()));
        return out;
    }

    private List<Integer> myMatrixIds() throws Exception {
        List<Integer> out = new ArrayList<>();
        json(perform(get("/api/exam-matrices")).andExpect(status().isOk())).forEach(m -> out.add(m.get("id").asInt()));
        return out;
    }

    private List<Integer> myMaterialIds() throws Exception {
        List<Integer> out = new ArrayList<>();
        json(perform(get("/api/materials?size=50")).andExpect(status().isOk())).get("items")
                .forEach(m -> out.add(m.get("id").asInt()));
        return out;
    }

    private String matrixBody(String name, String... cells) {
        return "{\"name\": \"" + name + "\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"cells\": ["
                + String.join(",", cells) + "]}";
    }

    private ResultActions upload(byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/materials")
                .file(new MockMultipartFile("file", "bai3.txt", "text/plain", bytes))
                .with(AuthTestSupport.teacher(teacher)));
    }

    /** Tài liệu chèn thẳng (không qua trích văn bản), sha256 khác nhau. */
    private int material(String tag) {
        return insert("INSERT INTO lesson_material (owner_id, file_name, mime_type, size_bytes, sha256, extract_method,"
                + " extracted_text, char_count) VALUES (?, ?, 'text/plain', 10, ?, 'plain', 'Noi dung bai hoc', 16)",
                teacher, tag + ".txt", String.format("%064x", ++sha + 1000));
    }

    /** Gắn tài liệu vào phạm vi cả đề (như tạo đề có tài liệu). */
    private void attach(int examId, int materialId) {
        int ctx = insert("INSERT INTO exam_generation_context (exam_id) VALUES (?)", examId);
        jdbc.update("INSERT INTO exam_context_material (context_id, material_id) VALUES (?, ?)", ctx, materialId);
    }

    // ---------- DELETE /api/exams/{id} ----------

    @Test
    void chuDeXoaDuocDeBienMatMoiThaoTacTra404NhungDongVanCon() throws Exception {
        int[] built = bankOnlyExam();
        int examId = built[0];
        int slot = slotId(examId, cellId(built[1], 1), 1);
        int slots = count("SELECT count(*) FROM exam_slot WHERE exam_id = ?", examId);
        assertTrue(myExamIds().contains(examId));

        deleteExam(examId).andExpect(status().isNoContent());

        assertNotNull(deletedAt("exam", examId), "xóa mềm: đặt deleted_at");
        assertEquals(1, count("SELECT count(*) FROM exam WHERE id = ?", examId), "không xóa vật lý đề");
        assertEquals(slots, count("SELECT count(*) FROM exam_slot WHERE exam_id = ?", examId), "chỗ còn nguyên");
        assertFalse(myExamIds().contains(examId), "không hiện ở đề của tôi");
        assertEquals(0, json(perform(get("/api/exams"))).get("totalItems").asInt());
        assertEquals(0, json(perform(get("/api/exams?kind=matrix"))).get("totalItems").asInt());

        String base = "/api/exams/" + examId;
        perform(get(base)).andExpect(status().isNotFound());
        perform(get(base + "/progress")).andExpect(status().isNotFound());
        perform(get(base + "/export/docx")).andExpect(status().isNotFound());
        perform(get(base + "/slots/" + slot + "/qc-trace")).andExpect(status().isNotFound());
        perform(get(base + "/cells/" + cellId(built[1], 1))).andExpect(status().isNotFound());
        postJson(base + "/slots/" + slot + "/keep", "{}").andExpect(status().isNotFound());
        postJson(base + "/slots/" + slot + "/replace", "{\"source\": \"bank\"}").andExpect(status().isNotFound());
        perform(put(base + "/slots/" + slot + "/question").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        deleteExam(examId).andExpect(status().isNotFound());
    }

    @Test
    void deCuaNguoiKhacTra404VaKhongBiXoa() throws Exception {
        int examId = bankOnlyExam()[0];
        performAs(otherTeacher, delete("/api/exams/" + examId)).andExpect(status().isNotFound());
        assertNull(deletedAt("exam", examId));
        perform(delete("/api/exams/99999999")).andExpect(status().isNotFound());
    }

    @Test
    void deDangSoanTra409VaKhongBiXoa() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, "Đơn thức", 1));
        int examId = build(matrix).get("examId").asInt();
        assertEquals(1, count("SELECT count(*) FROM generation_job WHERE job_type = 'generate' AND status = 'pending'"));

        deleteExam(examId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_GENERATING"));
        assertNull(deletedAt("exam", examId));

        jdbc.update("UPDATE generation_job SET status = 'running', started_at = now() WHERE job_type = 'generate'");
        deleteExam(examId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_GENERATING"));
        assertNull(deletedAt("exam", examId));
        assertEquals("running", jdbc.queryForObject("SELECT status FROM generation_job", String.class));
    }

    @Test
    void deDangHoanViHetLuotAiThiXoaDuocVaJobThanhFailed() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, "Đơn thức", 2));
        int examId = build(matrix).get("examId").asInt();
        jdbc.update("UPDATE generation_job SET next_run_at = now() + interval '10 hours',"
                + " last_error = 'quota: hết lượt AI hôm nay' WHERE job_type = 'generate'");

        deleteExam(examId).andExpect(status().isNoContent());

        assertNotNull(deletedAt("exam", examId));
        assertEquals(List.of("failed", "failed"), jdbc.queryForList(
                "SELECT status FROM generation_job ORDER BY id", String.class));
        assertEquals(List.of("exam_deleted", "exam_deleted"), jdbc.queryForList(
                "SELECT last_error FROM generation_job ORDER BY id", String.class));
    }

    @Test
    void deDaSoanXongXoaDuoc() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, "Đơn thức", 1));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        assertEquals(0, count("SELECT count(*) FROM generation_job WHERE job_type = 'generate'"
                + " AND status IN ('pending', 'running')"));
        int actions = count("SELECT count(*) FROM exam_slot_action");
        int questions = count("SELECT count(*) FROM generated_question");

        deleteExam(examId).andExpect(status().isNoContent());
        assertEquals(actions, count("SELECT count(*) FROM exam_slot_action"), "nhật ký thao tác giữ nguyên");
        assertEquals(questions, count("SELECT count(*) FROM generated_question"), "câu đã sinh giữ nguyên");
    }

    // ---------- ma trận ----------

    @Test
    void maTranChiConDeDaXoaThiExamCount0SuaCauTrucVan409SuaTenDuoc() throws Exception {
        int[] built = bankOnlyExam();
        int matrix = built[1];
        deleteExam(built[0]).andExpect(status().isNoContent());

        assertEquals(0, json(perform(get("/api/exam-matrices/" + matrix))).get("examCount").asInt());
        JsonNode summary = null;
        for (JsonNode m : json(perform(get("/api/exam-matrices")))) {
            if (m.get("id").asInt() == matrix) {
                summary = m;
            }
        }
        assertNotNull(summary);
        assertEquals(0, summary.get("examCount").asInt(), "examCount chỉ đếm đề chưa xóa");

        ResultActions structure = perform(put("/api/exam-matrices/" + matrix).contentType(MediaType.APPLICATION_JSON)
                .content(matrixBody("Ma trận thử", mcqCell("Phân thức", "Rút gọn", 2))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATRIX_IN_USE"));
        String message = json(structure).get("error").asText();
        assertTrue(message.contains("Ma trận đã từng có đề") && message.contains("Nhân bản"), message);

        perform(put("/api/exam-matrices/" + matrix).contentType(MediaType.APPLICATION_JSON)
                .content(matrixBody("Tên mới", mcqCell(TOPIC, "Đơn thức", 1)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tên mới"));
    }

    @Test
    void xoaMaTranChiConDeDaXoaLaXoaMemVaBienMatKhoiMoiTruyVan() throws Exception {
        int[] built = bankOnlyExam();
        int examId = built[0];
        int matrix = built[1];
        int cells = count("SELECT count(*) FROM exam_matrix_cell WHERE matrix_id = ?", matrix);
        deleteExam(examId).andExpect(status().isNoContent());

        perform(delete("/api/exam-matrices/" + matrix)).andExpect(status().isNoContent());

        assertNotNull(deletedAt("exam_matrix", matrix), "xóa mềm ma trận");
        assertEquals(cells, count("SELECT count(*) FROM exam_matrix_cell WHERE matrix_id = ?", matrix), "giữ nguyên ô");
        assertEquals(matrix, count("SELECT matrix_id FROM exam WHERE id = ?", examId), "đề đã xóa giữ matrix_id cũ");
        assertFalse(myMatrixIds().contains(matrix));
        perform(get("/api/exam-matrices/" + matrix)).andExpect(status().isNotFound());
        perform(post("/api/exam-matrices/" + matrix + "/duplicate")).andExpect(status().isNotFound());
        perform(put("/api/exam-matrices/" + matrix).contentType(MediaType.APPLICATION_JSON)
                .content(matrixBody("Tên", mcqCell(TOPIC, "Đơn thức", 1)))).andExpect(status().isNotFound());
        perform(delete("/api/exam-matrices/" + matrix)).andExpect(status().isNotFound());
        postJson("/api/exams/build", "{\"matrixId\": " + matrix + "}").andExpect(status().isNotFound());
    }

    @Test
    void xoaMaTranConDeChuaXoaVan409MaTranChuaTungCoDeXoaVatLy() throws Exception {
        int[] built = bankOnlyExam();
        perform(delete("/api/exam-matrices/" + built[1])).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATRIX_IN_USE"));

        int fresh = createMatrix(mcqCell("Tứ giác", null, 1));
        perform(delete("/api/exam-matrices/" + fresh)).andExpect(status().isNoContent());
        assertEquals(0, count("SELECT count(*) FROM exam_matrix WHERE id = ?", fresh), "chưa từng có đề: xóa như cũ");
    }

    // ---------- tài liệu (NV-55) ----------

    @Test
    void taiLieuChiConGanDeDaXoaThiXoaMem() throws Exception {
        int examId = bankOnlyExam()[0];
        int m = material("bai3");
        attach(examId, m);
        perform(delete("/api/materials/" + m)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_IN_USE"));

        deleteExam(examId).andExpect(status().isNoContent());
        perform(delete("/api/materials/" + m)).andExpect(status().isNoContent());

        assertNotNull(deletedAt("lesson_material", m), "xóa mềm tài liệu");
        assertEquals(1, count("SELECT count(*) FROM exam_context_material WHERE material_id = ?", m),
                "dòng gắn tài liệu của đề đã xóa giữ nguyên");
        assertFalse(myMaterialIds().contains(m));
        assertEquals(0, json(perform(get("/api/materials"))).get("totalItems").asInt());
        perform(get("/api/materials/" + m)).andExpect(status().isNotFound());
        perform(delete("/api/materials/" + m)).andExpect(status().isNotFound());
        // Tạo đề với tài liệu đã xóa: như tài liệu không tồn tại.
        int matrix = createMatrix(mcqCell("Tứ giác", null, 1));
        postJson("/api/exams/build", "{\"matrixId\": " + matrix + ", \"materialIds\": [" + m + "]}")
                .andExpect(status().isNotFound());
    }

    @Test
    void taiLieuKhongGanDeNaoVanXoaVatLy() throws Exception {
        int m = material("rieng");
        perform(delete("/api/materials/" + m)).andExpect(status().isNoContent());
        assertEquals(0, count("SELECT count(*) FROM lesson_material WHERE id = ?", m));
    }

    @Test
    void taiLaiDungFileDaXoaMemThiKhoiPhucBanCuTra201() throws Exception {
        byte[] file = LESSON_TEXT.getBytes(StandardCharsets.UTF_8);
        int m = json(upload(file).andExpect(status().isCreated())).get("id").asInt();
        int examId = bankOnlyExam()[0];
        attach(examId, m);
        deleteExam(examId).andExpect(status().isNoContent());
        perform(delete("/api/materials/" + m)).andExpect(status().isNoContent());
        assertFalse(myMaterialIds().contains(m));

        JsonNode again = json(upload(file).andExpect(status().isCreated()));
        assertEquals(m, again.get("id").asInt(), "khôi phục đúng bản cũ (UNIQUE owner_id, sha256)");
        assertNull(deletedAt("lesson_material", m));
        assertTrue(myMaterialIds().contains(m));
        perform(get("/api/materials/" + m)).andExpect(status().isOk());
    }

    @Test
    void tranTaiLieuKhongDemTaiLieuDaXoa() throws Exception {
        int examId = bankOnlyExam()[0];
        int deleted = material("da-xoa");
        attach(examId, deleted);
        deleteExam(examId).andExpect(status().isNoContent());
        perform(delete("/api/materials/" + deleted)).andExpect(status().isNoContent());
        for (int i = 0; i < 49; i++) {
            material("m" + i);
        }
        assertEquals(50, count("SELECT count(*) FROM lesson_material WHERE owner_id = ?", teacher));

        upload(LESSON_TEXT.getBytes(StandardCharsets.UTF_8)).andExpect(status().isCreated());
        upload((LESSON_TEXT + " Thêm.").getBytes(StandardCharsets.UTF_8)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_QUOTA_EXCEEDED"));
    }

    // ---------- thao tác đúng lúc đề đang bị xóa ----------

    /**
     * Một lần xóa đề đang dở (đã đặt deleted_at, giữ khóa hàng đề, chưa commit) chạy song song với {@code action}: thao tác
     * phải chờ lần xóa xong rồi trả 404 như đề không tồn tại, không ghi gì. Trả status HTTP của thao tác.
     */
    private int raceWithDelete(int examId, Callable<Integer> action) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT id FROM exam WHERE id = " + examId + " FOR UPDATE");
                st.execute("UPDATE exam SET deleted_at = now() WHERE id = " + examId);
            }
            Future<Integer> result = pool.submit(action);
            // Chờ tới khi thao tác đứng chờ khóa (đã sửa) hoặc đã chạy xong mà không chờ (chưa sửa: khe hở).
            for (int i = 0; i < 150 && !result.isDone(); i++) {
                if (count("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                        + " AND wait_event_type = 'Lock'") > 0) {
                    break;
                }
                Thread.sleep(100);
            }
            c.commit();
            return result.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Đề hai chỗ mcq đã soạn xong (câu hệ thống sinh), không còn job sinh câu mở. */
    private int[] settledExam() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, "Đơn thức", 2));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrix, 1), 1);
        assertEquals("ready", slotState(slot));
        return new int[] {examId, slot};
    }

    private int openGenerateJobs() {
        return count("SELECT count(*) FROM generation_job WHERE job_type = 'generate' AND status IN ('pending', 'running')");
    }

    @Test
    void doiCauLucDeDangBiXoaChoXongRoiTra404KhongXepJob() throws Exception {
        int[] e = settledExam();
        int actions = count("SELECT count(*) FROM exam_slot_action");
        int status = raceWithDelete(e[0], () -> postJson("/api/exams/" + e[0] + "/slots/" + e[1] + "/replace", "{}")
                .andReturn().getResponse().getStatus());
        assertEquals(404, status);
        assertEquals(0, openGenerateJobs(), "không xếp job sinh câu cho đề đã xóa");
        assertEquals("ready", slotState(e[1]));
        assertEquals(actions, count("SELECT count(*) FROM exam_slot_action"));
    }

    @Test
    void soanLaiChoHongLucDeDangBiXoaChoXongRoiTra404() throws Exception {
        int[] e = settledExam();
        jdbc.update("UPDATE exam_slot SET state = 'failed', failure_reason = 'llm_error', generated_question_id = NULL"
                + " WHERE id = ?", e[1]);
        int status = raceWithDelete(e[0], () -> postJson("/api/exams/" + e[0] + "/slots/" + e[1] + "/replace", "{}")
                .andReturn().getResponse().getStatus());
        assertEquals(404, status);
        assertEquals(0, openGenerateJobs());
        assertEquals("failed", slotState(e[1]));
    }

    @Test
    void giuCauLucDeDangBiXoaChoXongRoiTra404() throws Exception {
        int[] e = settledExam();
        int bankItems = count("SELECT count(*) FROM bank_item");
        int status = raceWithDelete(e[0], () -> postJson("/api/exams/" + e[0] + "/slots/" + e[1] + "/keep", "{}")
                .andReturn().getResponse().getStatus());
        assertEquals(404, status);
        assertEquals("ready", slotState(e[1]));
        assertEquals(bankItems, count("SELECT count(*) FROM bank_item"), "không chép câu vào ngân hàng");
    }

    @Test
    void suaCauLucDeDangBiXoaChoXongRoiTra404() throws Exception {
        int[] e = settledExam();
        int question = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", e[1]);
        int questions = count("SELECT count(*) FROM generated_question");
        int status = raceWithDelete(e[0], () -> perform(put("/api/exams/" + e[0] + "/slots/" + e[1] + "/question")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseQuestionId\": " + question + ", \"questionText\": \"Đề đã sửa: $1+1$ bằng?\"}"))
                .andReturn().getResponse().getStatus());
        assertEquals(404, status);
        assertEquals(questions, count("SELECT count(*) FROM generated_question"), "không tạo bản sửa");
    }

    // ---------- câu ngân hàng ----------

    /**
     * Xếp hạng mềm khi lấy câu ngân hàng cho đề mới (findBankCandidates): đề đã xóa không tính là "đã dùng". Hai câu cùng
     * khớp ô; câu id nhỏ hơn từng nằm trong một đề nay đã xóa: coi như chưa dùng, hòa với câu kia nên thắng theo id.
     */
    @Test
    void deDaXoaKhongTinhVaoXepHangMemKhiLayCauNganHang() throws Exception {
        int usedByDeleted = bankMcq(TOPIC, "easy", "Câu ngân hàng từng dùng");
        int fresh = bankMcq(TOPIC, "easy", "Câu ngân hàng chưa dùng");
        int oldExam = manualExamUsing(teacher, "OLD-1", "2026-10-20 08:00:00", usedByDeleted);
        jdbc.update("UPDATE exam SET deleted_at = now() WHERE id = ?", oldExam);

        int matrix = createMatrix(mcqCell(TOPIC, "Đơn thức", 1));
        int examId = build(matrix).get("examId").asInt();
        assertEquals(usedByDeleted, count("SELECT bank_item_id FROM exam_slot WHERE exam_id = ?", examId),
                "đề đã xóa không làm câu bị xếp sau");

        // Đối chứng: đề còn (chưa xóa) thì câu đó xếp sau câu chưa dùng như cũ.
        jdbc.update("UPDATE exam SET deleted_at = NULL WHERE id = ?", oldExam);
        int other = createMatrix(mcqCell(TOPIC, "Đơn thức", 1));
        deleteExam(examId).andExpect(status().isNoContent());
        int second = build(other).get("examId").asInt();
        assertEquals(fresh, count("SELECT bank_item_id FROM exam_slot WHERE exam_id = ?", second));
    }

    @Test
    void deDaXoaKhongTinhVaoSoDaDungVaDeCuaToi() throws Exception {
        int[] built = bankOnlyExam();
        int bankItem = built[2];
        JsonNode before = json(perform(get("/api/bank-items/" + bankItem)).andExpect(status().isOk()));
        assertEquals(1, before.get("usageCount").asInt());
        assertEquals(1, before.get("usage").get("usageCount").asInt());
        assertEquals(1, before.get("usage").get("myExams").size());

        deleteExam(built[0]).andExpect(status().isNoContent());

        JsonNode after = json(perform(get("/api/bank-items/" + bankItem)).andExpect(status().isOk()));
        assertEquals(0, after.get("usageCount").asInt());
        assertEquals(0, after.get("usage").get("usageCount").asInt());
        assertEquals(0, after.get("usage").get("myExams").size());
        assertTrue(after.get("usage").get("lastUsedAt").isNull());
        assertEquals(1, count("SELECT count(*) FROM bank_item WHERE id = ?", bankItem), "câu vẫn ở ngân hàng");
    }
}
