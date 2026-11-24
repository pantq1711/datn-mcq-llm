package com.datn.mcq.assignment;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nền chung cho test bài giao trực tuyến: một đề chế độ giáo viên của {@code teacher} gồm 2 câu trắc nghiệm (đáp án B),
 * 1 câu Đúng/Sai (nhãn a-d = T, F, F, T) và 1 câu Trả lời ngắn (đáp số 2,5), mọi chỗ đã Giữ. Dữ liệu chèn thẳng bằng SQL
 * trên datn_mcq_test; không có LLM. Không phải test (tên không khớp mẫu của surefire).
 */
abstract class AssignmentTestBase {

    static final String TOKEN_HEADER = "X-Attempt-Token";

    @Autowired
    WebApplicationContext context;
    @Autowired
    DataSource dataSource;
    @Autowired
    JdbcTemplate jdbc;

    final ObjectMapper objectMapper = new ObjectMapper();
    MockMvc mockMvc;

    int subject;
    int teacher;
    int otherTeacher;
    int admin;
    int exam;
    int mcqItem1;
    int mcqItem2;
    int tfItem;
    int saItem;
    int mcqSlot1;
    int mcqSlot2;
    int tfSlot;
    int saSlot;

    @BeforeEach
    void setUpBase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, getClass().getSimpleName() + ".setUp");
        subject = insert("INSERT INTO subject (name) VALUES ('Toán')");
        teacher = insert("INSERT INTO users (name, email, role) VALUES ('Cô An', 'an.assign@test', 'teacher')");
        otherTeacher = insert("INSERT INTO users (name, email, role) VALUES ('Thầy Bình', 'binh.assign@test', 'teacher')");
        admin = insert("INSERT INTO users (name, email, role) VALUES ('Quản trị', 'admin.assign@test', 'admin')");

        int matrix = insert("INSERT INTO exam_matrix (name, subject_id, created_by) VALUES ('Ma trận giao bài', ?, ?)",
                subject, teacher);
        int mcqTemplate = template("Đa thức");
        int tfTemplate = template("Hằng đẳng thức");
        int saTemplate = template("Phân thức");
        int mcqCell = insert("INSERT INTO exam_matrix_cell (matrix_id, position, part, question_type, topic, difficulty,"
                + " question_count, template_id) VALUES (?, 1, 'I', 'mcq', 'Đa thức', 'easy', 2, ?)", matrix, mcqTemplate);
        int tfCell = insert("INSERT INTO exam_matrix_cell (matrix_id, position, part, question_type, topic,"
                + " statement_difficulties, question_count, template_id) VALUES (?, 2, 'II', 'tf', 'Hằng đẳng thức',"
                + " 'easy,easy,medium,hard', 1, ?)", matrix, tfTemplate);
        int saCell = insert("INSERT INTO exam_matrix_cell (matrix_id, position, part, question_type, topic, difficulty,"
                + " question_count, template_id) VALUES (?, 3, 'III', 'sa', 'Phân thức', 'medium', 1, ?)", matrix,
                saTemplate);

        mcqItem1 = bankMcq(mcqTemplate, "Giá trị của $x^2$ tại $x = 3$ là");
        mcqItem2 = bankMcq(mcqTemplate, "Bậc của đa thức $x^3 + 1$ là");
        tfItem = bankTf(tfTemplate, "Cho biểu thức $P = (x+1)^2$.", "T", "F", "F", "T");
        saItem = bankSa(saTemplate, "Tính $\\frac{5}{2}$ dưới dạng số thập phân.", "2,5");

        exam = insert("INSERT INTO exam (exam_code, title, subject_id, created_by, mode, matrix_id) VALUES ('GB1',"
                + " 'Đề giữa kì I', ?, ?, 'teacher', ?)", subject, teacher, matrix);
        mcqSlot1 = slot(mcqCell, 1, "mcq", mcqItem1);
        mcqSlot2 = slot(mcqCell, 2, "mcq", mcqItem2);
        tfSlot = slot(tfCell, 1, "tf", tfItem);
        saSlot = slot(saCell, 1, "sa", saItem);
    }

    // ---------- dữ liệu ----------

    Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    int template(String topic) {
        return insert("INSERT INTO question_template (subject_id, source_question, is_active, topic) VALUES (?, 'Mẫu',"
                + " TRUE, ?)", subject, topic);
    }

    private int run(String type) {
        return insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose,"
                + " question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', ?)", subject, type);
    }

    int bankMcq(int template, String text) {
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, explanation, raw_llm_output, status, final_difficulty) VALUES"
                + " (?, ?, ?, '$1$', '$2$', '$3$', '$4$', 'B', 'LOI-GIAI-BI-MAT', '{}', 'in_bank', 'easy')", template,
                run("mcq"), text);
        int review = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production',"
                + " 'B', TRUE, TRUE, 'easy', 'approved_as_is', 'B')", q, teacher);
        return insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, explanation, difficulty) VALUES (?, ?, ?, '$1$', '$2$', '$3$',"
                + " '$4$', 'B', 'LOI-GIAI-BI-MAT', 'easy')", q, review, text);
    }

    int bankTf(int template, String text, String... labels) {
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text,"
                + " raw_llm_output, status) VALUES (?, ?, 'tf', ?, '{}', 'in_bank')", template, run("tf"), text);
        int review = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type,"
                + " format_ok, decision) VALUES (?, ?, 'production', 'tf', TRUE, 'approved_as_is')", q, teacher);
        int item = insert("INSERT INTO bank_item (source_question_id, review_id, question_type, question_text,"
                + " explanation) VALUES (?, ?, 'tf', ?, 'LOI-GIAI-BI-MAT')", q, review, text);
        String[] keys = {"a", "b", "c", "d"};
        for (int i = 0; i < 4; i++) {
            jdbc.update("INSERT INTO bank_item_statement (bank_item_id, position, statement_text, label, difficulty)"
                    + " VALUES (?, ?, ?, ?, 'easy')", item, keys[i], "Ý " + keys[i] + " của câu", labels[i]);
        }
        return item;
    }

    int bankSa(int template, String text, String answer) {
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text, sa_answer,"
                + " raw_llm_output, status, final_difficulty) VALUES (?, ?, 'sa', ?, ?, '{}', 'in_bank', 'medium')",
                template, run("sa"), text, answer);
        int review = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type,"
                + " answer_correct, format_ok, difficulty_label, decision, reviewer_sa_kind, reviewer_sa_answer,"
                + " final_sa_answer) VALUES (?, ?, 'production', 'sa', TRUE, TRUE, 'medium', 'approved_as_is', 'number',"
                + " ?, ?)", q, teacher, answer, answer);
        return insert("INSERT INTO bank_item (source_question_id, review_id, question_type, question_text, sa_answer,"
                + " explanation, difficulty) VALUES (?, ?, 'sa', ?, ?, 'LOI-GIAI-BI-MAT', 'medium')", q, review, text,
                answer);
    }

    /** Byte ảnh tự tạo (đầu PNG + vài byte), không phải ảnh bộ đề. */
    static final byte[] FAKE_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 7, 7, 7};

    /**
     * Gắn một ảnh tự tạo ({@link #FAKE_PNG}, sha256 ghi là 64 chữ 'c') vào câu nguồn của câu ngân hàng {@code bankItem};
     * trả id ảnh. Gắn trước hay sau khi giao bài đều được (A2 đã gỡ chặn giao bài có hình).
     */
    long attachImage(int bankItem, String role, int ordinal) {
        int sourceQuestion = jdbc.queryForObject("SELECT source_question_id FROM bank_item WHERE id = ?",
                Integer.class, bankItem);
        int batch = insert("INSERT INTO import_batch (owner_id, display_name, file_sha256, subject_id, grade_level,"
                + " topic, status, question_count, preview, created_at, expires_at) VALUES (?, 'Lô ảnh giả',"
                + " repeat('a', 64), ?, 8, 'Đa thức', 'preview', 1, '{}'::jsonb, CURRENT_TIMESTAMP,"
                + " CURRENT_TIMESTAMP + INTERVAL '24 hours')", teacher, subject);
        return jdbc.queryForObject("INSERT INTO question_image (owner_id, import_batch_id, item_key,"
                + " generated_question_id, role, ordinal, mime, data, byte_size, width_px, height_px, display_width_mm,"
                + " sha256, source_format) VALUES (?, ?, 'q1', ?, ?, ?, 'image/png', ?, ?, 300, 200, 50,"
                + " repeat('c', 64), 'png') RETURNING id", Long.class, teacher, batch, sourceQuestion, role,
                ordinal, FAKE_PNG, FAKE_PNG.length);
    }

    /** Id lượt làm theo lớp + số thứ tự (test chỉ có một bài giao). */
    int attemptId(String classLabel, int seatNo) {
        return count("SELECT id FROM assignment_attempt WHERE class_label = ? AND seat_no = ?", classLabel, seatNo);
    }

    int slot(int cell, int slotNo, String type, int bankItem) {
        return insert("INSERT INTO exam_slot (exam_id, cell_id, slot_no, question_type, state, bank_item_id) VALUES"
                + " (?, ?, ?, ?, 'kept', ?)", exam, cell, slotNo, type, bankItem);
    }

    // ---------- HTTP ----------

    JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    ResultActions asTeacher(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(AuthTestSupport.teacher(teacher)));
    }

    ResultActions asUser(int userId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(AuthTestSupport.teacher(userId)));
    }

    /** Lớp mặc định của bài giao trong test (quyết định 16/11: giáo viên khai lớp + sĩ số): 8A1 và 8A2, mỗi lớp 40 em. */
    static final String DEFAULT_CLASSES = "[{\"classLabel\": \"8A1\", \"size\": 40}, {\"classLabel\": \"8a 2\", \"size\": 40}]";

    static String createBody(int durationMinutes, Instant closesAt, boolean showScore, boolean consent) {
        return createBody(durationMinutes, closesAt, showScore, false, consent, DEFAULT_CLASSES);
    }

    static String createBody(int durationMinutes, Instant closesAt, boolean showScore, boolean showSolutions,
                             boolean consent, String classesJson) {
        return "{\"durationMinutes\": " + durationMinutes + ", \"closesAt\": \"" + closesAt + "\", \"showScore\": "
                + showScore + ", \"showSolutions\": " + showSolutions + ", \"consentConfirmed\": " + consent
                + (classesJson == null ? "" : ", \"classes\": " + classesJson) + "}";
    }

    String base() {
        return "/api/exams/" + exam + "/assignments";
    }

    /** Giao bài 45 phút, đóng sau 2 giờ, lớp mặc định; trả JSON bài giao. */
    JsonNode assign(boolean showScore) throws Exception {
        return assign(showScore, false);
    }

    JsonNode assign(boolean showScore, boolean showSolutions) throws Exception {
        return json(asTeacher(post(base()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(45, Instant.now().plus(2, ChronoUnit.HOURS), showScore, showSolutions, true,
                        DEFAULT_CLASSES)))
                .andExpect(status().isCreated()));
    }

    /**
     * Đóng bài giao và lùi mốc đóng quá ân hạn nộp (2 phút), như khi giờ học đã qua: kết quả đã được mở cho học sinh.
     */
    void closeAndPassGrace(int assignmentId) throws Exception {
        asTeacher(post(base() + "/" + assignmentId + "/close")).andExpect(status().isOk());
        jdbc.update("UPDATE exam_assignment SET closed_at = closed_at - interval '3 minutes',"
                + " closes_at = closes_at - interval '3 minutes' WHERE id = ?", assignmentId);
    }

    ResultActions start(String code, String classLabel, int seatNo) throws Exception {
        return mockMvc.perform(post("/api/public/assignments/" + code + "/attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("classLabel", classLabel, "seatNo", seatNo))));
    }

    /** Bắt đầu một lượt, trả token. */
    String startToken(String code, String classLabel, int seatNo) throws Exception {
        return json(start(code, classLabel, seatNo).andExpect(status().isCreated())).get("attemptToken").asText();
    }

    ResultActions current(String token) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/public/attempts/current");
        return mockMvc.perform(token == null ? request : request.header(TOKEN_HEADER, token));
    }

    ResultActions submit(String token, String answersJson) throws Exception {
        return mockMvc.perform(post("/api/public/attempts/current/submit").header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"answers\": " + answersJson + "}"));
    }

    /** Bài làm: câu 1 đúng (B), câu 2 sai (C), Đúng/Sai đúng 2 ý (a, c; d bỏ trống), Trả lời ngắn đúng (2.5 = 2,5). */
    String sampleAnswers() {
        return "[{\"slotId\": " + mcqSlot1 + ", \"choice\": \"B\"},"
                + " {\"slotId\": " + mcqSlot2 + ", \"choice\": \"C\"},"
                + " {\"slotId\": " + tfSlot + ", \"tf\": {\"a\": \"T\", \"b\": \"T\", \"c\": \"F\"}},"
                + " {\"slotId\": " + saSlot + ", \"text\": \"2.5\"}]";
    }
}
