package com.datn.mcq.bank.controller;

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/bank-items sau khi đổi từ entity sang BankItemDto (quyết định 7 ngày 22/10):
 * 12 key cũ của câu mcq giữ nguyên tên và giá trị (kể cả key có giá trị null), chỉ thêm questionType;
 * saAnswer chỉ có ở câu sa, statements chỉ có ở câu tf. Kiểm qua HTTP thật trên datn_mcq_test.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class BankItemsSnapshotTest {

    /** Key mà endpoint trả trước V4 (getter của entity BankItem ở HEAD 93a1734). */
    private static final List<String> OLD_KEYS = List.of(
            "id", "sourceQuestionId", "reviewId", "questionText", "optionA", "optionB", "optionC", "optionD",
            "correctOption", "explanation", "difficulty", "createdAt");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int mcqItem;
    private int saItem;
    private int tfItem;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "BankItemsSnapshotTest.setUp");
        int subject = insert("INSERT INTO subject (name) VALUES ('Toán Ngân hàng')");
        int teacher = insert("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.bank@test', 'teacher')");
        int template = insert("INSERT INTO question_template (subject_id, source_question, is_active)"
                + " VALUES (?, 'Mẫu', TRUE)", subject);
        int mcqRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'few-shot-structured', 'm', 0.7, 'p', 'production')", subject);
        int saRun = typedRun(subject, "sa");
        int tfRun = typedRun(subject, "tf");

        int mcq = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, status, final_difficulty)"
                + " VALUES (?, ?, 'Tính $1+1$', '$1$', '$2$', '$3$', '$4$', 'B', '{}', 'in_bank', 'easy')",
                template, mcqRun);
        int mcqReview = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production',"
                + " 'B', TRUE, TRUE, 'easy', 'approved_as_is', 'B')", mcq, teacher);
        mcqItem = insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, explanation, difficulty, created_at)"
                + " VALUES (?, ?, 'Tính $1+1$', '$1$', '$2$', '$3$', '$4$', 'B', NULL, 'easy',"
                + " TIMESTAMP '2026-10-22 10:00:00')", mcq, mcqReview);

        int sa = insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text, sa_answer,"
                + " raw_llm_output, status, final_difficulty) VALUES (?, ?, 'sa', 'Nghiệm của $4x+3=0$?',"
                + " '$-\\frac{3}{4}$', '{}', 'in_bank', 'medium')", template, saRun);
        int saReview = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type,"
                + " answer_correct, format_ok, difficulty_label, decision, reviewer_sa_kind, reviewer_sa_answer,"
                + " final_sa_answer) VALUES (?, ?, 'production', 'sa', TRUE, TRUE, 'medium', 'approved_as_is',"
                + " 'number', '-0,75', '-0,75')", sa, teacher);
        saItem = insert("INSERT INTO bank_item (source_question_id, review_id, question_type, question_text,"
                + " sa_answer, explanation, difficulty) VALUES (?, ?, 'sa', 'Nghiệm của $4x+3=0$?', '-0,75',"
                + " 'Lời giải AI', 'medium')", sa, saReview);

        int tf = insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text,"
                + " raw_llm_output, status) VALUES (?, ?, 'tf', 'Cho $P = x + 1$.', '{}', 'in_bank')", template, tfRun);
        int tfReview = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type,"
                + " format_ok, decision) VALUES (?, ?, 'production', 'tf', TRUE, 'approved_as_is')", tf, teacher);
        tfItem = insert("INSERT INTO bank_item (source_question_id, review_id, question_type, question_text)"
                + " VALUES (?, ?, 'tf', 'Cho $P = x + 1$.')", tf, tfReview);
        // chèn lệch thứ tự để kiểm sắp theo vị trí a-d
        String[][] statements = {{"c", "Tại $x=1$ thì $P=3$.", "F", "medium"}, {"a", "P xác định với mọi x.", "T", "easy"},
                {"d", "P > 0 với mọi x > -1.", "T", "hard"}, {"b", "P có bậc 1.", "T", "easy"}};
        for (String[] s : statements) {
            jdbc.update("INSERT INTO bank_item_statement (bank_item_id, position, statement_text, label, difficulty)"
                    + " VALUES (?, ?, ?, ?, ?)", tfItem, s[0], s[1], s[2], s[3]);
        }
    }

    private int typedRun(int subject, String type) {
        return insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose,"
                + " prompt_version, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', ?, ?)",
                subject, type + "-v0-draft", type);
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private JsonNode items() throws Exception {
        String body = mockMvc.perform(get("/api/bank-items"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(body);
        assertTrue(root.isArray());
        return root;
    }

    private static JsonNode byId(JsonNode items, int id) {
        for (JsonNode n : items) {
            if (n.path("id").asInt() == id) {
                return n;
            }
        }
        throw new AssertionError("Không thấy bank_item id=" + id);
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    @Test
    void mcqGiuNguyen12KeyCuVaChiThemQuestionType() throws Exception {
        JsonNode mcq = byId(items(), mcqItem);
        Set<String> expected = new LinkedHashSet<>(OLD_KEYS);
        expected.add("questionType");
        assertEquals(expected, keys(mcq));
        assertTrue(mcq.get("explanation").isNull(), "explanation null vẫn phải có key");
        assertEquals("Tính $1+1$", mcq.get("questionText").asText());
        assertEquals("$1$", mcq.get("optionA").asText());
        assertEquals("$4$", mcq.get("optionD").asText());
        assertEquals("B", mcq.get("correctOption").asText());
        assertEquals("easy", mcq.get("difficulty").asText());
        assertEquals("mcq", mcq.get("questionType").asText());
        assertTrue(mcq.get("createdAt").asText().startsWith("2026-10-22T10:00"), mcq.get("createdAt").asText());
    }

    /** GĐ2: tham số lọc tùy chọn ?questionType=; không truyền thì trả mọi dạng như trước. */
    @Test
    void locTheoDangCau() throws Exception {
        for (String[] c : new String[][] {{"mcq", String.valueOf(mcqItem)}, {"sa", String.valueOf(saItem)},
                {"tf", String.valueOf(tfItem)}}) {
            String body = mockMvc.perform(get("/api/bank-items").param("questionType", c[0]))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            JsonNode items = objectMapper.readTree(body);
            assertEquals(1, items.size(), "lọc " + c[0]);
            assertEquals(Integer.parseInt(c[1]), items.get(0).get("id").asInt());
            assertEquals(c[0], items.get(0).get("questionType").asText());
        }
        assertEquals(3, items().size());
        mockMvc.perform(get("/api/bank-items").param("questionType", "essay")).andExpect(status().isBadRequest());
    }

    @Test
    void saCoSaAnswerTfCoStatementsTheoThuTu() throws Exception {
        JsonNode items = items();
        assertEquals(List.of(tfItem, saItem, mcqItem),
                List.of(items.get(0).get("id").asInt(), items.get(1).get("id").asInt(), items.get(2).get("id").asInt()),
                "Mới nhất trước, như trước V4");

        JsonNode sa = byId(items, saItem);
        Set<String> saExpected = new LinkedHashSet<>(OLD_KEYS);
        saExpected.add("questionType");
        saExpected.add("saAnswer");
        assertEquals(saExpected, keys(sa));
        assertEquals("sa", sa.get("questionType").asText());
        assertEquals("-0,75", sa.get("saAnswer").asText());
        assertTrue(sa.get("optionA").isNull() && sa.get("correctOption").isNull());

        JsonNode tf = byId(items, tfItem);
        Set<String> tfExpected = new LinkedHashSet<>(OLD_KEYS);
        tfExpected.add("questionType");
        tfExpected.add("statements");
        assertEquals(tfExpected, keys(tf));
        assertTrue(tf.get("difficulty").isNull());
        JsonNode statements = tf.get("statements");
        assertEquals(4, statements.size());
        assertEquals(List.of("a", "b", "c", "d"), List.of(statements.get(0).get("key").asText(),
                statements.get(1).get("key").asText(), statements.get(2).get("key").asText(),
                statements.get(3).get("key").asText()));
        assertEquals(Set.of("key", "text", "label", "difficulty"), keys(statements.get(2)));
        assertEquals("F", statements.get(2).get("label").asText());
        assertEquals("medium", statements.get(2).get("difficulty").asText());
        assertEquals("Tại $x=1$ thì $P=3$.", statements.get(2).get("text").asText());
    }
}
