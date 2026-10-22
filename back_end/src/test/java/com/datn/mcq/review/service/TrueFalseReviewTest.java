package com.datn.mcq.review.service;

import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chấm mù câu Đúng/Sai (GĐ3): đủ 4 ý, label_correct từng ý, answer_correct NULL, undecidable không được
 * approved_as_is, approved_with_edit cần finalLabel đủ 4 ý, rejected không có finalLabel, edit_distance tính trên
 * đề dẫn và 4 ý, vào ngân hàng với 4 ý, rollback toàn bộ khi lỗi. Dữ liệu ghi bằng SQL; gọi qua HTTP thật.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class TrueFalseReviewTest {

    private static final String STEM = "Cho biểu thức $P = x + 1$.";
    private static final List<String> STATEMENTS = List.of("Ý a: $P$ xác định với mọi $x$.", "Ý b: $P$ có bậc $1$.",
            "Ý c: Tại $x = 1$ thì $P = 3$.", "Ý d: $P > 0$ với mọi $x > -1$.");
    private static final List<String> LLM_LABELS = List.of("T", "T", "F", "T");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TeacherReviewService teacherReviewService;

    private MockMvc mockMvc;
    private int template;
    private int teacher;
    private int tfRun;
    private int tfQuestion;
    private int mcqQuestion;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "TrueFalseReviewTest.setUp");
        int subject = insert("INSERT INTO subject (name) VALUES ('Toán Chấm TF')");
        teacher = insert("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.tfreview@test', 'teacher')");
        template = insert("INSERT INTO question_template (subject_id, source_question, is_active, grade_level, topic)"
                + " VALUES (?, 'Mẫu', TRUE, 8, 'Biểu thức')", subject);
        tfRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose,"
                + " prompt_version, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', 'tf-v0-draft',"
                + " 'tf')", subject);
        tfQuestion = tfQuestion();
        int mcqRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'few-shot-structured', 'm', 0.7, 'p', 'production')", subject);
        mcqQuestion = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, created_at)"
                + " VALUES (?, ?, 'Tính $1+1$', '$1$', '$2$', '$3$', '$4$', 'B', '{}', TRUE, 'qc_passed', now())",
                template, mcqRun);
    }

    private int tfQuestion() {
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text, explanation,"
                + " raw_llm_output, parse_success, status, created_at) VALUES (?, ?, 'tf', ?, 'LỜI GIẢI BÍ MẬT', '{}',"
                + " TRUE, 'qc_passed', now())", template, tfRun, STEM);
        String[] levels = {"easy", "easy", "medium", "hard"};
        for (int i = 0; i < 4; i++) {
            jdbc.update("INSERT INTO generated_question_statement (question_id, position, statement_text, llm_label,"
                    + " llm_difficulty) VALUES (?, ?, ?, ?, ?)", q, "abcd".substring(i, i + 1), STATEMENTS.get(i),
                    LLM_LABELS.get(i), levels[i]);
        }
        return q;
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private static String statements(String... items) {
        return "\"statements\": [" + String.join(", ", items) + "]";
    }

    private static String st(String key, String reviewer, String level, String fin) {
        return "{\"key\": \"" + key + "\", \"reviewerLabel\": " + (reviewer == null ? "null" : "\"" + reviewer + "\"")
                + ", \"difficultyLabel\": " + (level == null ? "null" : "\"" + level + "\"")
                + (fin != null ? ", \"finalLabel\": \"" + fin + "\"" : "") + "}";
    }

    private static final String FOUR_ASIS = statements(st("a", "T", "easy", null), st("b", "T", "easy", null),
            st("c", "F", "medium", null), st("d", "T", "hard", null));

    private ResultActions submit(int questionId, String fields) throws Exception {
        String body = "{\"reviewerId\": " + teacher + ", \"formatOk\": true, \"questionType\": \"tf\", " + fields + "}";
        return mockMvc.perform(post("/api/reviews/questions/" + questionId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertRejected(int questionId, String fields, String message) throws Exception {
        String body = submit(questionId, fields)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(message))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("BÍ MẬT"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review_statement", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM bank_item", Integer.class));
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, questionId));
    }

    @Test
    void requestKhongHopLeTra400() throws Exception {
        String need4 = "Phải gửi đủ 4 ý a, b, c, d (mỗi ý một lần)";
        assertRejected(tfQuestion, "\"decision\": \"approved_as_is\"", need4);
        assertRejected(tfQuestion, "\"decision\": \"approved_as_is\", " + statements(st("a", "T", "easy", null),
                st("b", "T", "easy", null), st("c", "F", "medium", null)), need4);
        assertRejected(tfQuestion, "\"decision\": \"approved_as_is\", " + statements(st("a", "T", "easy", null),
                st("b", "T", "easy", null), st("c", "F", "medium", null), st("c", "T", "hard", null)), need4);
        assertRejected(tfQuestion, "\"decision\": \"approved_as_is\", \"reviewerAnswer\": \"A\", " + FOUR_ASIS,
                "Trường reviewerAnswer không áp dụng cho câu đúng/sai.");
        assertRejected(tfQuestion, "\"decision\": \"approved_as_is\", \"difficultyLabel\": \"easy\", " + FOUR_ASIS,
                "Trường difficultyLabel không áp dụng cho câu đúng/sai.");
        assertRejected(tfQuestion, "\"decision\": \"approved_as_is\", \"reviewerSaKind\": \"number\", " + FOUR_ASIS,
                "Trường reviewerSaKind không áp dụng cho câu đúng/sai.");
        assertRejected(tfQuestion, "\"decision\": \"approved_as_is\", " + statements(st("a", "T", "easy", null),
                st("b", "T", "easy", null), st("c", null, "medium", null), st("d", "T", "hard", null)),
                "Ý c: phải chọn reviewerLabel (T, F, undecidable)");
        assertRejected(tfQuestion, "\"decision\": \"approved_as_is\", " + statements(st("a", "T", null, null),
                st("b", "T", "easy", null), st("c", "F", "medium", null), st("d", "T", "hard", null)),
                "Ý a: phải chọn difficultyLabel");
        assertRejected(tfQuestion, "\"decision\": \"approved_as_is\", " + statements(st("a", "T", "easy", null),
                st("b", "T", "easy", null), st("c", "undecidable", "medium", null), st("d", "T", "hard", null)),
                "Khi có ý là 'undecidable', không được chọn 'approved_as_is'");
        assertRejected(tfQuestion, "\"decision\": \"approved_as_is\", " + statements(st("a", "T", "easy", null),
                st("b", "T", "easy", "F"), st("c", "F", "medium", null), st("d", "T", "hard", null)),
                "Khi chọn approved_as_is, finalLabel của ý b phải trùng reviewerLabel");
        assertRejected(tfQuestion, "\"decision\": \"approved_with_edit\", " + statements(st("a", "T", "easy", "T"),
                st("b", "T", "easy", "T"), st("c", "undecidable", "medium", "F"), st("d", "T", "hard", null)),
                "Khi chọn approved_with_edit, bắt buộc chọn finalLabel cho ý d");
        assertRejected(tfQuestion, "\"decision\": \"rejected\", \"reasonCodes\": [\"content_unclear\"], "
                + statements(st("a", "T", "easy", "T"), st("b", "T", "easy", null), st("c", "F", "medium", null),
                st("d", "T", "hard", null)), "Khi chọn 'rejected', finalLabel của các ý phải để trống");
        assertRejected(tfQuestion, "\"decision\": \"rejected\", " + FOUR_ASIS,
                "Khi chọn 'rejected', phải có ít nhất 1 lý do trong reasonCodes");
        assertRejected(tfQuestion, "\"decision\": \"rejected\", \"reasonCodes\": [\"answer_ambiguous\"], " + FOUR_ASIS,
                "Mã lý do 'answer_ambiguous' không áp dụng cho dạng câu này.");
        assertRejected(tfQuestion, "\"decision\": \"rejected\", \"reasonCodes\": [\"options_indistinct\"], " + FOUR_ASIS,
                "Mã lý do 'options_indistinct' không áp dụng cho dạng câu này.");
        assertRejected(tfQuestion, "\"decision\": \"approved_with_edit\", " + statements(st("a", "T", "easy", "T"),
                "{\"key\": \"b\", \"reviewerLabel\": \"T\", \"difficultyLabel\": \"easy\", \"finalLabel\": \"T\","
                        + " \"editedText\": \"Ý có \\\\frac{1}{2} ngoài công thức\"}",
                st("c", "F", "medium", "F"), st("d", "T", "hard", "T")),
                "Bản sửa còn lỗi định dạng công thức LaTeX: statement_b: "
                        + "Có lệnh LaTeX nằm ngoài cặp $...$ (thiếu dấu $ bao quanh công thức)");
        // Sai dạng câu: form MCQ cho câu tf, form tf cho câu mcq.
        String mcqBody = "{\"reviewerId\": " + teacher + ", \"reviewerAnswer\": \"B\", \"formatOk\": true,"
                + " \"difficultyLabel\": \"easy\", \"decision\": \"approved_as_is\"}";
        mockMvc.perform(post("/api/reviews/questions/" + tfQuestion).contentType(MediaType.APPLICATION_JSON)
                        .content(mcqBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dữ liệu đánh giá không khớp dạng câu hỏi."));
        assertRejected(mcqQuestion, "\"decision\": \"approved_as_is\", " + FOUR_ASIS,
                "Dữ liệu đánh giá không khớp dạng câu hỏi.");
    }

    @Test
    void tuChoiVoiYKhongKetLuanDuoc() throws Exception {
        String body = submit(tfQuestion, "\"decision\": \"rejected\", \"reasonCodes\": [\"statement_undecidable\"], "
                + statements(st("a", "T", "easy", null), st("b", "F", "easy", null),
                st("c", "undecidable", "medium", null), st("d", "T", "hard", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionStatus").value("rejected"))
                .andExpect(jsonPath("$.statements[2].reviewerLabel").value("undecidable"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("labelCorrect"));
        assertFalse(body.contains("answerCorrect"));
        Map<String, Object> review = jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?", tfQuestion);
        assertNull(review.get("answer_correct"));
        int reviewId = ((Number) review.get("id")).intValue();
        assertEquals(List.of(true, false, false, true), jdbc.queryForList("SELECT label_correct FROM"
                + " teacher_review_statement WHERE review_id = ? ORDER BY position", Boolean.class, reviewId));
        assertEquals(2L, teacherReviewService.countMatchedStatements(reviewId));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM teacher_review_statement WHERE review_id = ?"
                + " AND final_label IS NULL", Integer.class, reviewId));
        assertEquals("statement_undecidable", jdbc.queryForObject("SELECT reason_code FROM teacher_review_reason",
                String.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM bank_item", Integer.class));
    }

    @Test
    void duyetCoSuaVaoNganHangVoiBonY() throws Exception {
        String editedStem = "Cho biểu thức $P = x + 1$ với $x$ là số thực.";
        String editedC = "Ý c: Tại $x = 1$ thì $P = 2$.";
        String body = submit(tfQuestion, "\"decision\": \"approved_with_edit\", \"editedQuestionText\": "
                + "\"" + editedStem.replace("\\", "\\\\") + "\", " + statements(st("a", "T", "easy", "T"),
                st("b", "F", "medium", "T"),
                "{\"key\": \"c\", \"reviewerLabel\": \"F\", \"difficultyLabel\": \"medium\", \"finalLabel\": \"T\","
                        + " \"editedText\": \"" + editedC + "\"}",
                st("d", "undecidable", "hard", "T")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.questionStatus").value("in_bank"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("labelCorrect"));

        Map<String, Object> review = jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?", tfQuestion);
        int expectedDistance = levenshtein(STEM, editedStem) + levenshtein(STATEMENTS.get(2), editedC);
        assertEquals(expectedDistance, ((Number) review.get("edit_distance")).intValue());
        assertEquals(editedStem, review.get("edited_question_text"));
        int reviewId = ((Number) review.get("id")).intValue();
        assertEquals(editedC, jdbc.queryForObject("SELECT edited_statement_text FROM teacher_review_statement"
                + " WHERE review_id = ? AND position = 'c'", String.class, reviewId));
        assertEquals(List.of(true, false, true, false), jdbc.queryForList("SELECT label_correct FROM"
                + " teacher_review_statement WHERE review_id = ? ORDER BY position", Boolean.class, reviewId));

        Map<String, Object> bank = jdbc.queryForMap("SELECT * FROM bank_item WHERE source_question_id = ?", tfQuestion);
        assertEquals(editedStem, bank.get("question_text"));
        assertEquals("LỜI GIẢI BÍ MẬT", bank.get("explanation"));
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM bank_item_statement WHERE bank_item_id = ?"
                + " ORDER BY position", bank.get("id"));
        assertEquals(List.of(STATEMENTS.get(0), STATEMENTS.get(1), editedC, STATEMENTS.get(3)),
                rows.stream().map(r -> r.get("statement_text")).toList());
        assertEquals(List.of("T", "T", "T", "T"), rows.stream().map(r -> r.get("label")).toList());
        assertEquals(List.of("easy", "medium", "medium", "hard"), rows.stream().map(r -> r.get("difficulty")).toList());
        assertEquals(List.of("easy", "medium", "medium", "hard"), jdbc.queryForList("SELECT final_difficulty FROM"
                + " generated_question_statement WHERE question_id = ? ORDER BY position", String.class, tfQuestion));
    }

    private static int levenshtein(String a, String b) {
        return com.datn.mcq.common.util.TextDistanceUtils.levenshteinDistance(a, b);
    }

    @Test
    void loiKhiGoKhoiNganHangThiRollbackToanBo() throws Exception {
        submit(tfQuestion, "\"decision\": \"approved_as_is\", " + FOUR_ASIS).andExpect(status().isOk());
        int bankItem = jdbc.queryForObject("SELECT id FROM bank_item WHERE source_question_id = ?", Integer.class,
                tfQuestion);
        int exam = insert("INSERT INTO exam (exam_code, title, subject_id, created_by) VALUES ('TF-1', 'Đề',"
                + " (SELECT subject_id FROM experiment_run WHERE id = ?), ?)", tfRun, teacher);
        jdbc.update("INSERT INTO exam_question (exam_id, bank_item_id, position) VALUES (?, ?, 1)", exam, bankItem);
        Map<String, Object> reviewBefore = jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?",
                tfQuestion);
        List<Map<String, Object>> statementsBefore = jdbc.queryForList("SELECT * FROM teacher_review_statement"
                + " ORDER BY position");
        List<String> levelsBefore = jdbc.queryForList("SELECT final_difficulty FROM generated_question_statement"
                + " WHERE question_id = ? ORDER BY position", String.class, tfQuestion);

        submit(tfQuestion, "\"decision\": \"rejected\", \"reasonCodes\": [\"content_inaccurate\"], "
                + statements(st("a", "F", "hard", null), st("b", "F", "hard", null), st("c", "T", "hard", null),
                st("d", "F", "hard", null)))
                .andExpect(status().isConflict());

        assertEquals(reviewBefore, jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?", tfQuestion));
        assertEquals(statementsBefore, jdbc.queryForList("SELECT * FROM teacher_review_statement ORDER BY position"));
        assertEquals(levelsBefore, jdbc.queryForList("SELECT final_difficulty FROM generated_question_statement"
                + " WHERE question_id = ? ORDER BY position", String.class, tfQuestion));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review_reason", Integer.class));
        assertEquals("in_bank", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, tfQuestion));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM bank_item_statement WHERE bank_item_id = ?",
                Integer.class, bankItem));
        assertEquals(List.of("easy", "easy", "medium", "hard"), levelsBefore, "mức của lần duyệt trước được giữ");
    }
}
