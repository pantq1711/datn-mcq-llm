package com.datn.mcq.exam;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-1: giáo viên chỉ thấy và thao tác trên ma trận, đề của chính mình. Tài nguyên của người khác trả 404 giống hệt id
 * không tồn tại (không lộ id nào có thật), và bị chặn TRƯỚC bước đối soát (reconcile có ghi DB). Kèm GET /api/exams:
 * danh sách đề của người gọi, phân trang, có số chỗ theo trạng thái.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamOwnershipTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int subject;
    private int teacherA;
    private int teacherB;
    private int matrixA;
    private int matrixB;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "ExamOwnershipTest.setUp");
        subject = jdbc.queryForObject("INSERT INTO subject (name) VALUES ('Toán') RETURNING id", Integer.class);
        teacherA = AuthTestSupport.insertUser(jdbc, "GV A", "gv.a@test.vn", "teacher", "matkhau-dung-1", true);
        teacherB = AuthTestSupport.insertUser(jdbc, "GV B", "gv.b@test.vn", "teacher", "matkhau-dung-1", true);
        matrixA = createMatrix(teacherA, "Ma trận A");
        matrixB = createMatrix(teacherB, "Ma trận B");
    }

    private ResultActions postJson(String url, String body, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(post(url).with(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions getAs(String url, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(get(url).with(who));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private int createMatrix(int teacher, String name) throws Exception {
        String body = "{\"name\": \"" + name + "\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"cells\": ["
                + "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 5,"
                + " \"topic\": \"Đa thức\", \"content\": \"Đơn thức\", \"learningOutcomes\": {\"easy\": \"Nhận biết.\"}}]}";
        return json(postJson("/api/exam-matrices", body, AuthTestSupport.teacher(teacher))
                .andExpect(status().isOk())).get("id").asInt();
    }

    private int build(int teacher, int matrixId) throws Exception {
        return json(postJson("/api/exams/build", "{\"matrixId\": " + matrixId + "}", AuthTestSupport.teacher(teacher))
                .andExpect(status().isOk())).get("examId").asInt();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private List<Integer> slots(int examId) {
        return jdbc.queryForList("SELECT id FROM exam_slot WHERE exam_id = ? ORDER BY slot_no", Integer.class, examId);
    }

    /** Làm hỏng job của mọi chỗ trong đề: nếu đối soát chạy, nó sẽ xếp thêm job sinh lại. */
    private void failJobs(int examId) {
        jdbc.update("UPDATE generation_job SET status = 'failed', last_error = 'giả lập' WHERE exam_slot_id IN"
                + " (SELECT id FROM exam_slot WHERE exam_id = ?)", examId);
    }

    @Test
    void chuSoHuuLayTuTokenKhongLayTuBody() throws Exception {
        assertEquals(teacherA, jdbc.queryForObject("SELECT created_by FROM exam_matrix WHERE id = ?", Integer.class,
                matrixA));
        int exam = json(postJson("/api/exams/build", "{\"matrixId\": " + matrixA + ", \"teacherId\": " + teacherB + "}",
                AuthTestSupport.teacher(teacherA)).andExpect(status().isOk())).get("examId").asInt();
        assertEquals(teacherA, jdbc.queryForObject("SELECT created_by FROM exam WHERE id = ?", Integer.class, exam),
                "teacherId trong body bị bỏ qua (đã gỡ khỏi hợp đồng API)");
    }

    @Test
    void giaoVienKhacKhongXemKhongThaoTacDuocDeVaKhongKichHoatDoiSoat() throws Exception {
        int examA = build(teacherA, matrixA);
        int slotA = slots(examA).get(0);
        int cellA = jdbc.queryForObject("SELECT cell_id FROM exam_slot WHERE id = ?", Integer.class, slotA);
        failJobs(examA);
        int jobsBefore = count("generation_job");
        int actionsBefore = count("exam_slot_action");
        int runsBefore = count("experiment_run");

        RequestPostProcessor b = AuthTestSupport.teacher(teacherB);
        String notFound = "Không tìm thấy đề id=" + examA;
        getAs("/api/exams/" + examA, b).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value(notFound));
        getAs("/api/exams/" + examA + "/progress", b).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(notFound));
        getAs("/api/exams/" + examA + "/cells/" + cellA, b).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(notFound));
        getAs("/api/exams/" + examA + "/export/docx", b).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(notFound));
        postJson("/api/exams/" + examA + "/slots/" + slotA + "/keep", "{}", b).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(notFound));
        postJson("/api/exams/" + examA + "/slots/" + slotA + "/replace", "{\"instruction\": \"đổi đi\"}", b)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value(notFound));

        // Giống hệt id không tồn tại.
        getAs("/api/exams/999999", b).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Không tìm thấy đề id=999999"));

        assertEquals(jobsBefore, count("generation_job"), "không đối soát đề của người khác (không xếp job)");
        assertEquals(actionsBefore, count("exam_slot_action"));
        assertEquals(runsBefore, count("experiment_run"));
        assertEquals(List.of("generating"), jdbc.queryForList("SELECT DISTINCT state FROM exam_slot WHERE exam_id = ?",
                String.class, examA));

        // Đối chứng: chính chủ xem tiến độ thì đối soát chạy và xếp job sinh lại.
        getAs("/api/exams/" + examA + "/progress", AuthTestSupport.teacher(teacherA)).andExpect(status().isOk());
        assertTrue(count("generation_job") > jobsBefore, "chủ sở hữu vẫn đối soát bình thường");
    }

    @Test
    void maTranCuaNguoiKhacTra404VaKhongTaoDeTuDo() throws Exception {
        RequestPostProcessor b = AuthTestSupport.teacher(teacherB);
        getAs("/api/exam-matrices/" + matrixA, b).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Không tìm thấy ma trận id=" + matrixA));
        JsonNode list = json(getAs("/api/exam-matrices", b).andExpect(status().isOk()));
        assertEquals(1, list.size());
        assertEquals(matrixB, list.get(0).get("id").asInt());

        int examsBefore = count("exam");
        postJson("/api/exams/build", "{\"matrixId\": " + matrixA + "}", b).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Không tìm thấy ma trận id=" + matrixA));
        assertEquals(examsBefore, count("exam"));
        getAs("/api/exam-matrices/" + matrixA, AuthTestSupport.teacher(teacherA)).andExpect(status().isOk());
    }

    @Test
    void adminKhongDungChucNangGiaoVien() throws Exception {
        int examA = build(teacherA, matrixA);
        int admin = AuthTestSupport.insertUser(jdbc, "Admin", "admin.own@test.vn", "admin", "matkhau-admin-12", true);
        getAs("/api/exams/" + examA, AuthTestSupport.admin(admin)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        getAs("/api/exams", AuthTestSupport.admin(admin)).andExpect(status().isForbidden());
    }

    // ---------- GET /api/exams ----------

    private int bankItem() {
        int tpl = jdbc.queryForObject("INSERT INTO question_template (subject_id, source_question, is_active, topic)"
                + " VALUES (?, 'Mẫu', TRUE, 'Khác') RETURNING id", Integer.class, subject);
        int run = jdbc.queryForObject("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature,"
                + " prompt_text, purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production') RETURNING id",
                Integer.class, subject);
        int q = generatedQuestion(tpl, run, "in_bank");
        int review = jdbc.queryForObject("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose,"
                + " reviewer_answer, answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?,"
                + " 'production', 'B', TRUE, TRUE, 'easy', 'approved_as_is', 'B') RETURNING id", Integer.class, q,
                teacherA);
        return jdbc.queryForObject("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a,"
                + " option_b, option_c, option_d, correct_option, difficulty) VALUES (?, ?, 'Câu ngân hàng', '$1$', '$2$',"
                + " '$3$', '$4$', 'B', 'easy') RETURNING id", Integer.class, q, review);
    }

    private int generatedQuestion(int tpl, int run, String statusValue) {
        return jdbc.queryForObject("INSERT INTO generated_question (template_id, run_id, question_text, option_a,"
                + " option_b, option_c, option_d, correct_option, raw_llm_output, parse_success, status, final_difficulty)"
                + " VALUES (?, ?, 'Câu sinh ra', '$1$', '$2$', '$3$', '$4$', 'B', '{}', TRUE, ?, 'easy') RETURNING id",
                Integer.class, tpl, run, statusValue);
    }

    @Test
    void danhSachDeCuaToiCoPhanTrangVaSoChoTheoTrangThai() throws Exception {
        int older = build(teacherA, matrixA);
        int newer = build(teacherA, matrixA);
        build(teacherB, matrixB);

        // Đề mới: 5 chỗ = 1 sẵn sàng (ngân hàng), 1 sẵn sàng cần xem kỹ (câu sinh chưa có QC), 1 đã Giữ, 1 hỏng,
        // 1 đang sinh.
        List<Integer> s = slots(newer);
        int bank = bankItem();
        int tpl = jdbc.queryForObject("SELECT template_id FROM exam_matrix_cell WHERE matrix_id = ?", Integer.class,
                matrixA);
        int run = jdbc.queryForObject("SELECT run_id FROM exam_slot WHERE id = ?", Integer.class, s.get(1));
        int generated = generatedQuestion(tpl, run, "generated");
        jdbc.update("UPDATE exam_slot SET state = 'ready', bank_item_id = ? WHERE id = ?", bank, s.get(0));
        jdbc.update("UPDATE exam_slot SET state = 'ready', generated_question_id = ? WHERE id = ?", generated, s.get(1));
        jdbc.update("UPDATE exam_slot SET state = 'kept', bank_item_id = ? WHERE id = ?", bank, s.get(2));
        jdbc.update("UPDATE exam_slot SET state = 'failed' WHERE id = ?", s.get(3));
        failJobs(older);
        int jobsBefore = count("generation_job");

        JsonNode page = json(getAs("/api/exams", AuthTestSupport.teacher(teacherA)).andExpect(status().isOk()));
        assertEquals(2, page.get("totalItems").asInt());
        assertEquals(1, page.get("totalPages").asInt());
        assertEquals(0, page.get("page").asInt());
        assertEquals(10, page.get("size").asInt(), "mặc định 10 đề mỗi trang");
        JsonNode items = page.get("items");
        assertEquals(List.of(newer, older), List.of(items.get(0).get("examId").asInt(), items.get(1).get("examId").asInt()),
                "mới nhất trước");

        JsonNode first = items.get(0);
        assertEquals(matrixA, first.get("matrixId").asInt());
        assertEquals("Ma trận A", first.get("title").asText());
        assertEquals("teacher", first.get("mode").asText());
        assertEquals("draft", first.get("status").asText());
        assertTrue(first.hasNonNull("examCode") && first.hasNonNull("createdAt"));
        assertEquals(5, first.get("totalSlots").asInt());
        assertEquals(2, first.get("readySlots").asInt());
        assertEquals(1, first.get("needsReviewSlots").asInt());
        assertEquals(1, first.get("keptSlots").asInt());
        assertEquals(1, first.get("failedSlots").asInt());
        assertEquals(1, first.get("generatingSlots").asInt());
        assertEquals(5, items.get(1).get("generatingSlots").asInt());

        assertEquals(jobsBefore, count("generation_job"), "danh sách chỉ đọc, không đối soát");

        JsonNode p0 = json(getAs("/api/exams?page=0&size=1", AuthTestSupport.teacher(teacherA)).andExpect(status().isOk()));
        assertEquals(1, p0.get("items").size());
        assertEquals(2, p0.get("totalPages").asInt());
        assertEquals(newer, p0.get("items").get(0).get("examId").asInt());
        JsonNode p1 = json(getAs("/api/exams?page=1&size=1", AuthTestSupport.teacher(teacherA)).andExpect(status().isOk()));
        assertEquals(older, p1.get("items").get(0).get("examId").asInt());

        JsonNode mineB = json(getAs("/api/exams", AuthTestSupport.teacher(teacherB)).andExpect(status().isOk()));
        assertEquals(1, mineB.get("totalItems").asInt());
    }

    @Test
    void thamSoPhanTrangSaiTra400() throws Exception {
        RequestPostProcessor a = AuthTestSupport.teacher(teacherA);
        getAs("/api/exams?size=0", a).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        getAs("/api/exams?size=51", a).andExpect(status().isBadRequest());
        getAs("/api/exams?page=-1", a).andExpect(status().isBadRequest());
    }
}
