package com.datn.mcq.bank;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-3: ngân hàng câu có lọc, phân trang, sắp xếp, số đề đã dùng, chi tiết kèm nguồn gốc ({@code GET /api/bank-items},
 * {@code GET /api/bank-items/{id}}, {@code GET /api/bank-items/topics}) qua HTTP thật trên datn_mcq_test.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class BankSearchTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    private int toan;
    private int ly;
    private int teacher1;
    private int teacher2;
    private int admin;
    private int m1;
    private int m2;
    private int m3;
    private int m4;
    private int m5;
    private int sa1;
    private int tf1;
    private int editedItem;
    private int editedLlm;
    private int editedVersion;
    private int editedExam;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "BankSearchTest.setUp");
        toan = insert("INSERT INTO subject (name) VALUES ('Toán')");
        ly = insert("INSERT INTO subject (name) VALUES ('Vật lí')");
        teacher1 = insert("INSERT INTO users (name, email, role) VALUES ('Cô An', 'an@test', 'teacher')");
        teacher2 = insert("INSERT INTO users (name, email, role) VALUES ('Thầy Bình', 'binh@test', 'teacher')");
        admin = insert("INSERT INTO users (name, email, role) VALUES ('Quản trị', 'qt@test', 'admin')");

        m1 = mcq(toan, 8, "Đa thức", "Đơn thức", "easy", "Bậc của đơn thức $2x^3$ là", "2026-10-10 08:00:00");
        m2 = mcq(toan, 8, "Đa thức", null, "medium", "Cộng hai đa thức $x+1$ và $x+2$", "2026-10-11 08:00:00");
        m3 = mcq(toan, 9, "Căn bậc hai", null, "easy", "Tính $\\sqrt{9}$", "2026-10-12 08:00:00");
        m4 = mcq(ly, 8, "Chuyển động", null, "easy", "Vận tốc là đại lượng nào", "2026-10-13 08:00:00");
        m5 = mcq(toan, 8, "Phần trăm", null, "hard", "Giảm 20% giá, còn lại bao nhiêu", "2026-10-14 08:00:00");
        sa1 = sa(toan, 8, "Tứ giác", "medium", "Tổng bốn góc của tứ giác là bao nhiêu độ", "360", "2026-10-15 08:00:00");
        tf1 = tf(toan, 8, "Hằng đẳng thức", new String[] {"easy", "easy", "medium", "hard"}, "2026-10-16 08:00:00");

        // E1 (GV1, 20/10): m1. E2 (GV2, 22/10): m1, m2. E3 (GV1, 23/10): m1.
        exam(teacher1, "E1", "2026-10-20 09:00:00", m1);
        exam(teacher2, "E2", "2026-10-22 09:00:00", m1, m2);
        exam(teacher1, "E3", "2026-10-23 09:00:00", m1);
        setUpEditedItem();
    }

    /** Câu Giữ ở chế độ giáo viên sau khi giáo viên sửa: câu LLM gốc q0, bản sửa q1, bank_item từ q1, review assisted. */
    private void setUpEditedItem() {
        int tpl = insert("INSERT INTO question_template (subject_id, source_question, is_active, topic, grade_level)"
                + " VALUES (?, 'Mẫu', TRUE, 'Đa thức', 8)", toan);
        int run = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose, prompt_version) VALUES (?, 'zero-shot', 'gemini-x', 0.7, 'p', 'production',"
                + " 'matrix-mcq-v1-draft')", toan);
        editedLlm = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, llm_difficulty,"
                + " created_at) VALUES (?, ?, 'Câu gốc', '$1$', '$2$', '$3$', '$4$', 'B', 'RAW-BI-MAT', TRUE,"
                + " 'qc_passed', 'easy', TIMESTAMP '2026-10-21 10:00:00')", tpl, run);
        editedVersion = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, llm_difficulty,"
                + " final_difficulty, edited_from_question_id, created_at) VALUES (?, ?, 'Câu đã sửa', '$1$', '$2$',"
                + " '$9$', '$4$', 'C', '', TRUE, 'in_bank', 'easy', 'easy', ?, TIMESTAMP '2026-10-21 10:05:00')",
                tpl, run, editedLlm);
        int review = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, review_mode,"
                + " reviewer_answer, answer_correct, format_ok, difficulty_label, decision, final_option,"
                + " reviewed_at) VALUES (?, ?, 'production', 'assisted', 'C', TRUE, TRUE, 'easy', 'approved_as_is',"
                + " 'C', TIMESTAMP '2026-10-21 10:10:00')", editedVersion, teacher1);
        editedItem = insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, difficulty, created_at) VALUES (?, ?, 'Câu đã sửa', '$1$',"
                + " '$2$', '$9$', '$4$', 'C', 'easy', TIMESTAMP '2026-10-21 10:10:00')", editedVersion, review);
        int matrix = insert("INSERT INTO exam_matrix (name, subject_id, created_by) VALUES ('M', ?, ?)", toan, teacher1);
        int cell = insert("INSERT INTO exam_matrix_cell (matrix_id, position, part, question_type, topic, difficulty,"
                + " question_count, template_id) VALUES (?, 1, 'I', 'mcq', 'Đa thức', 'easy', 1, ?)", matrix, tpl);
        editedExam = insert("INSERT INTO exam (exam_code, title, subject_id, created_by, mode, matrix_id, created_at)"
                + " VALUES ('ED1', 'Đề có câu sửa', ?, ?, 'teacher', ?, TIMESTAMP '2026-10-21 11:00:00')", toan,
                teacher1, matrix);
        int slot = insert("INSERT INTO exam_slot (exam_id, cell_id, slot_no, question_type, state, bank_item_id,"
                + " generated_question_id) VALUES (?, ?, 1, 'mcq', 'edited', ?, ?)", editedExam, cell, editedItem,
                editedVersion);
        jdbc.update("INSERT INTO exam_slot_action (slot_id, action, generated_question_id, edit_distance, actor_id,"
                + " changed_fields) VALUES (?, 'edit', ?, 4, ?, '[\"option_c\", \"correct_option\"]'::jsonb)", slot,
                editedVersion, teacher1);
    }

    // ---------- tiện ích ----------

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private int template(int subject, Integer grade, String topic) {
        return insert("INSERT INTO question_template (subject_id, source_question, is_active, topic, grade_level)"
                + " VALUES (?, 'Mẫu', TRUE, ?, ?)", subject, topic, grade);
    }

    private int run(int subject, String type) {
        return insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose,"
                + " question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', ?)", subject, type);
    }

    private int review(int question, String type) {
        return insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type, format_ok,"
                + " decision) VALUES (?, ?, 'production', ?, TRUE, 'approved_as_is')", question, teacher1, type);
    }

    private int mcq(int subject, Integer grade, String topic, String content, String difficulty, String text,
                    String createdAt) {
        int tpl = template(subject, grade, topic);
        if (content != null) {
            int matrix = insert("INSERT INTO exam_matrix (name, subject_id, created_by) VALUES ('M', ?, ?)", subject,
                    teacher1);
            jdbc.update("INSERT INTO exam_matrix_cell (matrix_id, position, part, question_type, topic, content,"
                    + " difficulty, question_count, template_id) VALUES (?, 1, 'I', 'mcq', ?, ?, ?, 1, ?)", matrix,
                    topic, content, difficulty, tpl);
        }
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, status, final_difficulty) VALUES (?, ?, ?,"
                + " '$1$', '$2$', '$3$', '$4$', 'B', '{}', 'in_bank', ?)", tpl, run(subject, "mcq"), text, difficulty);
        int review = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production',"
                + " 'B', TRUE, TRUE, ?, 'approved_as_is', 'B')", q, teacher1, difficulty);
        return insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, difficulty, created_at) VALUES (?, ?, ?, '$1$', '$2$', '$3$',"
                + " '$4$', 'B', ?, ?::timestamp)", q, review, text, difficulty, createdAt);
    }

    private int sa(int subject, Integer grade, String topic, String difficulty, String text, String answer,
                   String createdAt) {
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text, sa_answer,"
                + " raw_llm_output, status, final_difficulty) VALUES (?, ?, 'sa', ?, ?, '{}', 'in_bank', ?)",
                template(subject, grade, topic), run(subject, "sa"), text, answer, difficulty);
        int review = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type,"
                + " answer_correct, format_ok, difficulty_label, decision, reviewer_sa_kind, reviewer_sa_answer,"
                + " final_sa_answer) VALUES (?, ?, 'production', 'sa', TRUE, TRUE, ?, 'approved_as_is', 'number', ?,"
                + " ?)", q, teacher1, difficulty, answer, answer);
        return insert("INSERT INTO bank_item (source_question_id, review_id, question_type, question_text, sa_answer,"
                + " difficulty, created_at) VALUES (?, ?, 'sa', ?, ?, ?, ?::timestamp)", q, review, text, answer,
                difficulty, createdAt);
    }

    private int tf(int subject, Integer grade, String topic, String[] levels, String createdAt) {
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text,"
                + " raw_llm_output, status) VALUES (?, ?, 'tf', 'Cho biểu thức $P = (x+1)^2$.', '{}', 'in_bank')",
                template(subject, grade, topic), run(subject, "tf"));
        int review = review(q, "tf");
        int item = insert("INSERT INTO bank_item (source_question_id, review_id, question_type, question_text,"
                + " created_at) VALUES (?, ?, 'tf', 'Cho biểu thức $P = (x+1)^2$.', ?::timestamp)", q, review,
                createdAt);
        String[] keys = {"a", "b", "c", "d"};
        for (int i = 0; i < 4; i++) {
            jdbc.update("INSERT INTO bank_item_statement (bank_item_id, position, statement_text, label, difficulty)"
                    + " VALUES (?, ?, ?, 'T', ?)", item, keys[i], "Ý " + keys[i] + " của câu đúng sai", levels[i]);
        }
        return item;
    }

    private void exam(int owner, String code, String createdAt, int... bankItems) {
        int exam = insert("INSERT INTO exam (exam_code, title, subject_id, created_by, created_at) VALUES (?, ?, ?, ?,"
                + " ?::timestamp)", code, "Đề " + code, toan, owner, createdAt);
        int pos = 1;
        for (int item : bankItems) {
            jdbc.update("INSERT INTO exam_question (exam_id, bank_item_id, position) VALUES (?, ?, ?)", exam, item,
                    pos++);
        }
    }

    private ResultActions as(int userId, boolean isAdmin, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(isAdmin ? AuthTestSupport.admin(userId) : AuthTestSupport.teacher(userId)));
    }

    private JsonNode list(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/bank-items");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return json(as(teacher1, false, request).andExpect(status().isOk()));
    }

    private ResultActions listRaw(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/bank-items");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return as(teacher1, false, request);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<Integer> ids(JsonNode page) {
        List<Integer> out = new ArrayList<>();
        page.get("items").forEach(n -> out.add(n.get("id").asInt()));
        return out;
    }

    private static Set<Integer> idSet(JsonNode page) {
        return new HashSet<>(ids(page));
    }

    private static JsonNode byId(JsonNode page, int id) {
        for (JsonNode n : page.get("items")) {
            if (n.get("id").asInt() == id) {
                return n;
            }
        }
        throw new AssertionError("Không thấy bank_item id=" + id);
    }

    // ---------- lọc ----------

    @Test
    void khongLocTraTrangMoiNhatTruoc() throws Exception {
        JsonNode page = list();
        assertEquals(0, page.get("page").asInt());
        assertEquals(20, page.get("size").asInt(), "size mặc định");
        assertEquals(8, page.get("totalItems").asInt());
        assertEquals(1, page.get("totalPages").asInt());
        assertEquals(List.of(editedItem, tf1, sa1, m5, m4, m3, m2, m1).stream().sorted((a, b) -> b - a).toList(),
                ids(page), "mới nhất trước = id giảm dần, như endpoint cũ");
    }

    @Test
    void locTheoMonKhoiDangCauMucDoVaChuDe() throws Exception {
        assertEquals(Set.of(m4), idSet(list("subjectId", String.valueOf(ly))));
        assertEquals(Set.of(m3), idSet(list("gradeLevel", "9")));
        assertEquals(Set.of(sa1), idSet(list("questionType", "sa")));
        assertEquals(Set.of(tf1), idSet(list("questionType", "tf")));
        assertEquals(Set.of(m1, m2, editedItem), idSet(list("topic", "  đa THỨC ")),
                "chủ đề không phân biệt hoa thường, bỏ khoảng trắng hai đầu");
        assertEquals(Set.of(m1), idSet(list("content", "đơn thức")));
        assertEquals(Set.of(m1, editedItem, m3, m4, tf1), idSet(list("difficulty", "easy")),
                "tf tính khi có ít nhất một ý ở mức đó");
        assertEquals(Set.of(m2, sa1, tf1), idSet(list("difficulty", "medium")));
        assertEquals(Set.of(m1, editedItem), idSet(list("subjectId", String.valueOf(toan), "gradeLevel", "8",
                "difficulty", "easy", "questionType", "mcq")), "nhiều bộ lọc cùng lúc");
    }

    @Test
    void locLevelsChiChoCauTfVaKhopDungThuTu() throws Exception {
        assertEquals(Set.of(tf1), idSet(list("questionType", "tf", "levels", "easy,easy,medium,hard")));
        assertEquals(Set.of(), idSet(list("questionType", "tf", "levels", "hard,medium,easy,easy")));
        listRaw("questionType", "mcq", "levels", "easy,easy,medium,hard").andExpect(status().isBadRequest());
        listRaw("levels", "easy,easy").andExpect(status().isBadRequest());
    }

    @Test
    void timTheoNoiDungKhongHieuNhamKyTuDacBiet() throws Exception {
        assertEquals(Set.of(m1), idSet(list("q", "đơn thức")), "không phân biệt hoa thường");
        assertEquals(Set.of(sa1), idSet(list("q", "360")), "tìm cả đáp số");
        assertEquals(Set.of(tf1), idSet(list("q", "Ý c của")), "tìm cả các ý");
        assertEquals(Set.of(m5), idSet(list("q", "20%")));
        assertEquals(Set.of(m5), idSet(list("q", "%")), "dấu % là ký tự thường, không phải ký tự đại diện");
        assertEquals(Set.of(), idSet(list("q", "_")), "dấu _ là ký tự thường");
        listRaw("q", "x".repeat(101)).andExpect(status().isBadRequest());
    }

    @Test
    void nguonCauChiCoAiHienTai() throws Exception {
        assertEquals(8, list("origin", "ai").get("totalItems").asInt());
        assertEquals(0, list("origin", "import").get("totalItems").asInt(), "chỗ để dành cho nhập từ Word");
        listRaw("origin", "khac").andExpect(status().isBadRequest());
        JsonNode item = byId(list(), m1);
        assertEquals("ai", item.get("origin").get("type").asText());
        assertEquals("blind", item.get("origin").get("reviewMode").asText());
        assertFalse(item.get("origin").get("edited").asBoolean());
        JsonNode edited = byId(list(), editedItem);
        assertEquals("assisted", edited.get("origin").get("reviewMode").asText());
        assertTrue(edited.get("origin").get("edited").asBoolean(), "câu được giáo viên sửa trước khi Giữ");
    }

    // ---------- phân trang, sắp xếp, dùng ----------

    @Test
    void phanTrangVaThamSoSai() throws Exception {
        JsonNode first = list("size", "3", "page", "0");
        assertEquals(3, first.get("items").size());
        assertEquals(8, first.get("totalItems").asInt());
        assertEquals(3, first.get("totalPages").asInt());
        JsonNode last = list("size", "3", "page", "2");
        assertEquals(2, last.get("items").size());
        assertEquals(0, list("size", "3", "page", "3").get("items").size());
        listRaw("page", "-1").andExpect(status().isBadRequest());
        listRaw("size", "0").andExpect(status().isBadRequest());
        listRaw("size", "51").andExpect(status().isBadRequest());
        listRaw("sort", "ngau_nhien").andExpect(status().isBadRequest());
        listRaw("gradeLevel", "5").andExpect(status().isBadRequest());
        listRaw("gradeLevel", "10").andExpect(status().isBadRequest());
        listRaw("questionType", "essay").andExpect(status().isBadRequest());
        listRaw("difficulty", "kho").andExpect(status().isBadRequest());
        listRaw("usage", "nhieu").andExpect(status().isBadRequest());
    }

    @Test
    void sapXepTheoNgayVaTheoMucDung() throws Exception {
        List<Integer> oldest = ids(list("sort", "oldest"));
        assertEquals(m1, oldest.get(0));
        assertEquals(editedItem, oldest.get(oldest.size() - 1));

        List<Integer> least = ids(list("sort", "least_used"));
        // 0 lần dùng trước (cũ id nhỏ trước), rồi m2 (1 lần), cuối cùng m1 (3 lần) và câu đã sửa (1 lần, 21/10).
        assertEquals(List.of(m3, m4, m5, sa1, tf1), least.subList(0, 5).stream().sorted().toList());
        assertTrue(least.indexOf(m2) < least.indexOf(m1), "m2 dùng 1 lần, m1 dùng 3 lần");
        assertEquals(m1, least.get(least.size() - 1), "dùng nhiều nhất xếp cuối");
        assertTrue(least.indexOf(editedItem) < least.indexOf(m2), "cùng 1 lần dùng: lần dùng gần nhất cũ hơn trước");

        List<Integer> recent = ids(list("sort", "recently_used"));
        assertEquals(m1, recent.get(0), "dùng gần nhất 23/10");
        assertEquals(m2, recent.get(1), "22/10");
        assertEquals(editedItem, recent.get(2), "21/10");

        assertEquals(Set.of(m1, m2, editedItem), idSet(list("usage", "used")));
        assertEquals(Set.of(m3, m4, m5, sa1, tf1), idSet(list("usage", "unused")));
    }

    @Test
    void moiCauCoSoDeDaDungVaLanDungGanNhat() throws Exception {
        JsonNode page = list();
        JsonNode i1 = byId(page, m1);
        assertEquals(3, i1.get("usageCount").asInt(), "E1, E2, E3 (đề của mọi giáo viên)");
        assertTrue(i1.get("lastUsedAt").asText().startsWith("2026-10-23T09:00"), i1.get("lastUsedAt").asText());
        assertEquals(1, byId(page, m2).get("usageCount").asInt());
        JsonNode unused = byId(page, m3);
        assertEquals(0, unused.get("usageCount").asInt());
        assertTrue(unused.get("lastUsedAt").isNull());
        // Một đề dùng cùng câu ở hai chỗ chỉ tính một đề.
        int dup = insert("INSERT INTO exam (exam_code, title, subject_id, created_by) VALUES ('E9', 'Đề 9', ?, ?)",
                toan, teacher1);
        jdbc.update("INSERT INTO exam_question (exam_id, bank_item_id, position) VALUES (?, ?, 1)", dup, m3);
        assertEquals(1, byId(list(), m3).get("usageCount").asInt());
    }

    @Test
    void cauCoDayDuCacKeyCuVaKeyMoi() throws Exception {
        JsonNode item = byId(list(), m1);
        for (String key : List.of("id", "sourceQuestionId", "reviewId", "questionText", "optionA", "optionB", "optionC",
                "optionD", "correctOption", "explanation", "difficulty", "createdAt", "questionType", "subjectId",
                "subjectName", "gradeLevel", "topic", "content", "origin", "usageCount", "lastUsedAt")) {
            assertTrue(item.has(key), "thiếu key " + key);
        }
        assertEquals("Toán", item.get("subjectName").asText());
        assertEquals(8, item.get("gradeLevel").asInt());
        assertEquals("Đa thức", item.get("topic").asText());
        assertEquals("Đơn thức", item.get("content").asText());
        assertEquals("360", byId(list(), sa1).get("saAnswer").asText());
        JsonNode tf = byId(list(), tf1);
        assertEquals(4, tf.get("statements").size());
        assertEquals("a", tf.get("statements").get(0).get("key").asText());
    }

    // ---------- chi tiết ----------

    @Test
    void chiTietCoNguonGocBanSuaVaNguoiGiu() throws Exception {
        JsonNode d = json(as(teacher1, false, get("/api/bank-items/" + editedItem)).andExpect(status().isOk()));
        assertEquals(editedItem, d.get("id").asInt());
        assertEquals("C", d.get("correctOption").asText());
        JsonNode p = d.get("provenance");
        assertEquals(editedLlm, p.get("llmQuestionId").asInt());
        assertEquals("matrix-mcq-v1-draft", p.get("promptVersion").asText());
        assertEquals("gemini-x", p.get("model").asText());
        JsonNode versions = p.get("versions");
        assertEquals(2, versions.size());
        assertEquals(editedLlm, versions.get(0).get("questionId").asInt());
        assertEquals("llm", versions.get(0).get("kind").asText());
        JsonNode edit = versions.get(1);
        assertEquals(editedVersion, edit.get("questionId").asInt());
        assertEquals("teacher_edit", edit.get("kind").asText());
        assertEquals(teacher1, edit.get("editedBy").get("id").asInt());
        assertEquals("Cô An", edit.get("editedBy").get("name").asText());
        assertEquals("option_c", edit.get("changedFields").get(0).asText());
        assertEquals(4, edit.get("editDistance").asInt());
        JsonNode review = p.get("review");
        assertEquals("assisted", review.get("mode").asText());
        assertEquals("approved_as_is", review.get("decision").asText());
        assertEquals(teacher1, review.get("reviewer").get("id").asInt());
        assertEquals("Cô An", review.get("reviewer").get("name").asText());
        assertTrue(review.get("reviewedAt").asText().startsWith("2026-10-21T10:10"));
        assertFalse(d.toString().contains("RAW-BI-MAT"), "không lộ raw_llm_output");
        assertFalse(d.toString().toLowerCase().contains("rawllmoutput"));
    }

    @Test
    void chiTietChiLietKeDeCuaChinhNguoiGoi() throws Exception {
        JsonNode mine = json(as(teacher1, false, get("/api/bank-items/" + m1)).andExpect(status().isOk()));
        assertEquals(3, mine.get("usage").get("usageCount").asInt(), "đếm cả đề của giáo viên khác");
        List<String> codes = new ArrayList<>();
        mine.get("usage").get("myExams").forEach(e -> codes.add(e.get("examCode").asText()));
        assertEquals(List.of("E3", "E1"), codes, "chỉ đề của mình, mới nhất trước");

        JsonNode other = json(as(teacher2, false, get("/api/bank-items/" + m1)).andExpect(status().isOk()));
        assertEquals(3, other.get("usage").get("usageCount").asInt());
        assertEquals(1, other.get("usage").get("myExams").size());
        assertEquals("E2", other.get("usage").get("myExams").get(0).get("examCode").asText());

        JsonNode adminView = json(as(admin, true, get("/api/bank-items/" + m1)).andExpect(status().isOk()));
        assertEquals(3, adminView.get("usage").get("usageCount").asInt());
        assertEquals(0, adminView.get("usage").get("myExams").size());
        assertFalse(json(as(teacher2, false, get("/api/bank-items/" + m1))).toString().contains("E1"),
                "không lộ mã đề của giáo viên khác");
    }

    @Test
    void chiTietCauChamMuVaCauTf() throws Exception {
        JsonNode blind = json(as(teacher1, false, get("/api/bank-items/" + m3)).andExpect(status().isOk()));
        assertEquals("blind", blind.get("provenance").get("review").get("mode").asText());
        assertEquals(1, blind.get("provenance").get("versions").size());
        assertEquals("llm", blind.get("provenance").get("versions").get(0).get("kind").asText());
        assertEquals(0, blind.get("usage").get("usageCount").asInt());
        assertTrue(blind.get("usage").get("lastUsedAt").isNull());

        JsonNode tf = json(as(teacher1, false, get("/api/bank-items/" + tf1)).andExpect(status().isOk()));
        assertEquals(4, tf.get("statements").size());
        assertEquals("easy", tf.get("statements").get(0).get("difficulty").asText());
    }

    @Test
    void chiTietKhongCoTra404() throws Exception {
        as(teacher1, false, get("/api/bank-items/999999")).andExpect(status().isNotFound());
    }

    // ---------- quyền ----------

    @Test
    void adminVaGiaoVienDocDuocChuaDangNhapThi401() throws Exception {
        as(admin, true, get("/api/bank-items")).andExpect(status().isOk());
        as(admin, true, get("/api/bank-items/topics")).andExpect(status().isOk());
        as(teacher1, false, get("/api/bank-items/topics")).andExpect(status().isOk());
        mockMvc.perform(get("/api/bank-items")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bank-items/" + m1)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bank-items/topics")).andExpect(status().isUnauthorized());
    }

    @Test
    void khongCoDuongSuaHayXoaCauNganHang() throws Exception {
        for (MockHttpServletRequestBuilder r : List.of(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/bank-items/" + m1),
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/bank-items/" + m1),
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bank-items"))) {
            int code = as(teacher1, false, r).andReturn().getResponse().getStatus();
            assertTrue(code == 403 || code == 404 || code == 405, "HTTP " + code);
        }
        assertEquals(8, jdbc.queryForObject("SELECT count(*) FROM bank_item", Integer.class));
    }

    // ---------- danh sách chủ đề ----------

    @Test
    void danhSachChuDeKemSoCau() throws Exception {
        JsonNode topics = json(as(teacher1, false, get("/api/bank-items/topics").param("subjectId",
                String.valueOf(toan))).andExpect(status().isOk()));
        JsonNode da = null;
        for (JsonNode t : topics) {
            if ("Đa thức".equals(t.get("topic").asText()) && t.get("gradeLevel").asInt() == 8) {
                da = t;
            }
        }
        assertEquals(3, da.get("count").asInt(), "m1, m2 và câu đã sửa");
        assertEquals(toan, da.get("subjectId").asInt());
        for (JsonNode t : topics) {
            assertEquals(toan, t.get("subjectId").asInt(), "lọc theo môn");
        }
    }
}
