package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-4 bước 1: sinh theo bài (POST /api/exams/build-by-lesson). Đề theo bài là "ma trận một bài" ẩn (exam_matrix.kind =
 * 'lesson') dùng lại nguyên luồng tạo đề theo ma trận: ngân hàng trước, sinh qua hàng đợi, QC, Giữ/Đổi/Sửa, vết QC.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class LessonBuildTest extends ExamFlowTestBase {

    private static final String LESSON = "Bài 3. Hằng đẳng thức đáng nhớ";

    private static String mcq(String level, int count) {
        return "{\"questionType\": \"mcq\", \"difficulty\": \"" + level + "\", \"questionCount\": " + count + "}";
    }

    private static final String TF = "{\"questionType\": \"tf\", \"questionCount\": 1}";
    private static final String SA = "{\"questionType\": \"sa\", \"difficulty\": \"medium\", \"questionCount\": 1}";

    private String lessonBody(String lesson, String extra, String... cells) {
        return "{\"subjectId\": " + subject + ", \"gradeLevel\": 8, \"lesson\": \"" + lesson + "\""
                + (extra == null ? "" : ", " + extra) + ", \"cells\": [" + String.join(",", cells) + "]}";
    }

    private JsonNode buildLesson(String body) throws Exception {
        return json(postJson("/api/exams/build-by-lesson", body).andExpect(status().isOk()));
    }

    private int lessonMatrixId(int examId) {
        return count("SELECT matrix_id FROM exam WHERE id = ?", examId);
    }

    // ---------- chạy hết luồng ----------

    @Test
    void sinhTheoBaiChayHetLuongTuHangDoiDenXemDe() throws Exception {
        JsonNode built = buildLesson(lessonBody(LESSON, null, mcq("easy", 2), TF, SA));
        int examId = built.get("examId").asInt();
        assertEquals("lesson", built.get("kind").asText());
        assertTrue(built.get("matrixId").isNull(), "ma trận ẩn không lộ id");
        assertEquals(4, built.get("slots").asInt());
        assertEquals(0, built.get("fromBank").asInt());
        assertEquals(4, built.get("jobsQueued").asInt());
        assertEquals(LESSON, jdbc.queryForObject("SELECT title FROM exam WHERE id = ?", String.class, examId),
                "không gửi title thì lấy tên bài");

        settle(examId);
        assertEquals("ready", progress(examId).get("status").asText());

        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals("lesson", view.get("kind").asText());
        assertTrue(view.get("matrixId").isNull());
        assertEquals(2, view.get("parts").get(0).get("items").size());
        for (JsonNode part : view.get("parts")) {
            for (JsonNode item : part.get("items")) {
                assertTrue(item.hasNonNull("questionText"), "chỗ đã có câu: " + item);
                assertEquals(LESSON, item.get("topic").asText());
            }
        }
        assertEquals(1, view.get("parts").get(1).get("items").size());
        assertEquals(1, view.get("parts").get(2).get("items").size());
    }

    @Test
    void giuDoiVaVetQcChayDuocTrenDeTheoBai() throws Exception {
        int examId = buildLesson(lessonBody(LESSON, null, mcq("easy", 2))).get("examId").asInt();
        settle(examId);
        int first = count("SELECT id FROM exam_slot WHERE exam_id = ? AND slot_no = 1", examId);
        int second = count("SELECT id FROM exam_slot WHERE exam_id = ? AND slot_no = 2", examId);

        perform(post("/api/exams/" + examId + "/slots/" + first + "/keep")).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("kept"));
        perform(post("/api/exams/" + examId + "/slots/" + second + "/replace")).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("generating"));
        settle(examId);
        assertEquals("ready", slotState(second));
        perform(get("/api/exams/" + examId + "/slots/" + second + "/qc-trace")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lineages.length()").value(2));
    }

    // ---------- ngân hàng trước ----------

    @Test
    void layCauNganHangTruocRoiMoiSinhPhanThieu() throws Exception {
        bankMcq(LESSON, "easy", "Câu ngân hàng một");
        bankMcq(LESSON, "easy", "Câu ngân hàng hai");
        JsonNode built = buildLesson(lessonBody(LESSON, null, mcq("easy", 3)));
        assertEquals(2, built.get("fromBank").asInt());
        assertEquals(1, built.get("jobsQueued").asInt());
    }

    /**
     * Ô theo bài không nhập nội dung thì không lọc theo nội dung: câu ngân hàng sinh từ ô ma trận (có nội dung) cùng chủ
     * đề dùng lại được. Ô ma trận thường không nhập nội dung vẫn chỉ khớp ô không có nội dung (hành vi cũ giữ nguyên).
     */
    @Test
    void oTheoBaiKhongNhapNoiDungKhopCauSinhTuOMaTranCoNoiDung() throws Exception {
        int matrix = createMatrix(mcqCell(LESSON, "Bình phương của một tổng", 1));
        int template = count("SELECT template_id FROM exam_matrix_cell WHERE id = ?", cellId(matrix, 1));
        int item = bankMcq(template, LESSON, "easy", "Câu nguồn", "Câu ngân hàng từ ô ma trận",
                new String[] {"$1$", "$2$", "$3$", "$4$"});

        int viaMatrix = build(createMatrix(mcqCell(LESSON, null, 1))).get("examId").asInt();
        assertEquals(List.of(), jdbc.queryForList("SELECT bank_item_id FROM exam_slot WHERE exam_id = ? AND"
                + " bank_item_id IS NOT NULL", Integer.class, viaMatrix), "ma trận thường: giữ hành vi cũ");

        int viaLesson = buildLesson(lessonBody(LESSON, null, mcq("easy", 1))).get("examId").asInt();
        assertEquals(List.of(item), jdbc.queryForList("SELECT bank_item_id FROM exam_slot WHERE exam_id = ?",
                Integer.class, viaLesson));
    }

    @Test
    void oTheoBaiCoNoiDungThiChiKhopNoiDungDo() throws Exception {
        int matrix = createMatrix(mcqCell(LESSON, "Bình phương của một tổng", 1));
        int template = count("SELECT template_id FROM exam_matrix_cell WHERE id = ?", cellId(matrix, 1));
        bankMcq(template, LESSON, "easy", "Câu nguồn", "Câu ngân hàng từ ô ma trận",
                new String[] {"$1$", "$2$", "$3$", "$4$"});
        JsonNode built = buildLesson(lessonBody(LESSON, "\"content\": \"Hiệu hai bình phương\"", mcq("easy", 1)));
        assertEquals(0, built.get("fromBank").asInt());
    }

    // ---------- ma trận ẩn ----------

    @Test
    void maTranTheoBaiAnKhoiDanhSachVaMoiDuongDanTheoId() throws Exception {
        int examId = buildLesson(lessonBody(LESSON, null, mcq("easy", 1))).get("examId").asInt();
        int hidden = lessonMatrixId(examId);
        assertEquals("lesson", jdbc.queryForObject("SELECT kind FROM exam_matrix WHERE id = ?", String.class, hidden));

        perform(get("/api/exam-matrices")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        perform(get("/api/exam-matrices/" + hidden)).andExpect(status().isNotFound());
        postJson("/api/exams/build", "{\"matrixId\": " + hidden + "}").andExpect(status().isNotFound());
    }

    @Test
    void maTranThuongVanHienVaCoKindMatrix() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        assertEquals("matrix", jdbc.queryForObject("SELECT kind FROM exam_matrix WHERE id = ?", String.class, matrix));
        perform(get("/api/exam-matrices")).andExpect(jsonPath("$.length()").value(1));
        assertEquals("matrix", build(matrix).get("kind").asText());
    }

    // ---------- lọc danh sách ----------

    @Test
    void locDanhSachDeTheoLoai() throws Exception {
        build(createMatrix(mcqCell("Đa thức", null, 1)));
        buildLesson(lessonBody(LESSON, null, mcq("easy", 1)));

        perform(get("/api/exams")).andExpect(jsonPath("$.totalItems").value(2));
        JsonNode lesson = json(perform(get("/api/exams?kind=lesson")).andExpect(status().isOk()));
        assertEquals(1, lesson.get("totalItems").asInt());
        assertEquals("lesson", lesson.get("items").get(0).get("kind").asText());
        assertTrue(lesson.get("items").get(0).get("matrixId").isNull());
        JsonNode matrix = json(perform(get("/api/exams?kind=matrix")).andExpect(status().isOk()));
        assertEquals(1, matrix.get("totalItems").asInt());
        assertEquals("matrix", matrix.get("items").get(0).get("kind").asText());
        perform(get("/api/exams?kind=khac")).andExpect(status().isBadRequest());
    }

    // ---------- yêu cầu cần đạt mặc định, tf mặc định ----------

    @Test
    void yeuCauCanDatMacDinhTheoMucVaGhiDeDuoc() throws Exception {
        int examId = buildLesson(lessonBody(LESSON, "\"learningOutcomes\": {\"hard\": \"Tự đặt yêu cầu khó.\"}",
                mcq("easy", 1), mcq("hard", 1))).get("examId").asInt();
        int matrix = lessonMatrixId(examId);
        String easy = jdbc.queryForObject("SELECT learning_outcome_easy FROM exam_matrix_cell WHERE matrix_id = ? AND"
                + " position = 1", String.class, matrix);
        assertTrue(easy.contains(LESSON), "mặc định nêu tên bài: " + easy);
        assertEquals("Tự đặt yêu cầu khó.", jdbc.queryForObject("SELECT learning_outcome_hard FROM exam_matrix_cell"
                + " WHERE matrix_id = ? AND position = 2", String.class, matrix));
        assertEquals(null, jdbc.queryForObject("SELECT learning_outcome_medium FROM exam_matrix_cell WHERE matrix_id = ?"
                + " AND position = 1", String.class, matrix), "chỉ điền các mức ô dùng");
    }

    @Test
    void tfKhongNeuMucThiMacDinhBonYDeDenKho() throws Exception {
        int examId = buildLesson(lessonBody(LESSON, null, TF)).get("examId").asInt();
        assertEquals("easy,easy,medium,hard", jdbc.queryForObject("SELECT statement_difficulties FROM exam_matrix_cell"
                + " WHERE matrix_id = ?", String.class, lessonMatrixId(examId)));
        assertEquals("II", jdbc.queryForObject("SELECT part FROM exam_matrix_cell WHERE matrix_id = ?", String.class,
                lessonMatrixId(examId)));
    }

    @Test
    void templateCuaOGhiTenBaiVaNoiDung() throws Exception {
        int examId = buildLesson(lessonBody(LESSON, "\"content\": \"Bình phương của một tổng\"", mcq("easy", 1)))
                .get("examId").asInt();
        String source = jdbc.queryForObject("SELECT t.source_question FROM question_template t JOIN exam_matrix_cell c"
                + " ON c.template_id = t.id WHERE c.matrix_id = ?", String.class, lessonMatrixId(examId));
        assertTrue(source.contains(LESSON) && source.contains("Bình phương của một tổng"), source);
        assertEquals(LESSON, jdbc.queryForObject("SELECT t.topic FROM question_template t JOIN exam_matrix_cell c ON"
                + " c.template_id = t.id WHERE c.matrix_id = ?", String.class, lessonMatrixId(examId)));
    }

    // ---------- kiểm tra dữ liệu: lỗi thì không tạo gì ----------

    private void assertRejected(String body, int expectedStatus, String messagePart) throws Exception {
        String error = json(postJson("/api/exams/build-by-lesson", body).andExpect(status().is(expectedStatus)))
                .get("error").asText();
        assertTrue(error.contains(messagePart), "thông báo '" + error + "' phải có '" + messagePart + "'");
        assertEquals(0, count("SELECT count(*) FROM exam_matrix"), "không tạo ma trận");
        assertEquals(0, count("SELECT count(*) FROM question_template"), "không tạo template");
        assertEquals(0, count("SELECT count(*) FROM exam"), "không tạo đề");
        assertEquals(0, count("SELECT count(*) FROM generation_job"), "không xếp job");
    }

    @Test
    void duLieuSaiTraBon400VaKhongTaoGi() throws Exception {
        assertRejected(lessonBody(" ", null, mcq("easy", 1)), 400, "lesson");
        assertRejected(lessonBody(LESSON, null), 400, "ít nhất một ô");
        assertRejected(lessonBody(LESSON, null, "{\"questionCount\": 1}"), 400, "Ô 1");
        assertRejected(lessonBody(LESSON, null, "{\"questionType\": \"mcq\", \"questionCount\": 1}"), 400,
                "difficulty");
        assertRejected(lessonBody(LESSON, null, mcq("easy", 0)), 400, "questionCount");
        assertRejected(lessonBody(LESSON, null, mcq("easy", 1),
                "{\"questionType\": \"tf\", \"difficulty\": \"easy\", \"questionCount\": 1}"), 400, "Ô 2");
        assertRejected(lessonBody(LESSON, null, "{\"questionType\": \"tf\", \"statementDifficulties\": [\"easy\"],"
                + " \"questionCount\": 1}"), 400, "4 mức");
        assertRejected(lessonBody(LESSON, "\"learningOutcomes\": {\"rat-kho\": \"x\"}", mcq("easy", 1)), 400,
                "learningOutcomes");
        assertRejected(lessonBody(LESSON, "\"title\": \"" + "x".repeat(256) + "\"", mcq("easy", 1)), 400, "title");
        assertRejected(lessonBody("y".repeat(256), null, mcq("easy", 1)), 400, "lesson");
        assertRejected(lessonBody(LESSON, null, mcq("easy", 1)).replace("\"gradeLevel\": 8", "\"gradeLevel\": 10"),
                400, "gradeLevel");
    }

    @Test
    void monKhongTonTaiTra404VaKhongTaoGi() throws Exception {
        assertRejected(lessonBody(LESSON, null, mcq("easy", 1)).replace("\"subjectId\": " + subject,
                "\"subjectId\": 999999"), 404, "môn học");
    }

    @Test
    void tranTongSauMuoiCauMoiLanSinhTheoBai() throws Exception {
        // D1: trần mặc định 60 (app.generation-limits.max-questions-per-exam), trước đây cố định 50.
        assertRejected(lessonBody(LESSON, null, mcq("easy", 30), mcq("medium", 31)), 400, "60");
        JsonNode ok = buildLesson(lessonBody(LESSON, null, mcq("easy", 30), mcq("medium", 30)));
        assertEquals(60, ok.get("slots").asInt());
    }

    // ---------- quyền sở hữu ----------

    @Test
    void giaoVienKhacKhongThayDeTheoBaiCuaToi() throws Exception {
        int examId = buildLesson(lessonBody(LESSON, null, mcq("easy", 1))).get("examId").asInt();
        performAs(otherTeacher, get("/api/exams/" + examId)).andExpect(status().isNotFound());
        performAs(otherTeacher, get("/api/exams?kind=lesson")).andExpect(jsonPath("$.totalItems").value(0));
        // Đề theo bài thuộc người tạo: người khác không đổi/giữ được.
        int slot = count("SELECT id FROM exam_slot WHERE exam_id = ?", examId);
        performAs(otherTeacher, post("/api/exams/" + examId + "/slots/" + slot + "/replace"))
                .andExpect(status().isNotFound());
    }
}
