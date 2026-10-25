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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trường thừa khi nộp đánh giá (lượt cuối sau GĐ4): một quy tắc chung cho mcq, sa, tf. Trường thuộc dạng câu khác
 * mà khác null thì 400 "Dữ liệu đánh giá không khớp dạng câu hỏi.", kể cả khi body có questionType; mang giá trị null
 * thì bỏ qua. Trường của dạng câu: mcq có reviewerAnswer, finalOption, editedOptionA..D; sa có reviewerSaKind,
 * reviewerSaAnswer, finalSaAnswer; tf có statements; difficultyLabel cấp câu là của mcq và sa.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ReviewForeignFieldsTest {

    private static final String MISMATCH = "Dữ liệu đánh giá không khớp dạng câu hỏi.";

    private static final String MCQ_BODY = "\"questionType\": \"mcq\", \"reviewerAnswer\": \"B\","
            + " \"difficultyLabel\": \"easy\", \"decision\": \"approved_as_is\", \"finalOption\": \"B\"";
    private static final String SA_BODY = "\"questionType\": \"sa\", \"difficultyLabel\": \"medium\","
            + " \"decision\": \"approved_as_is\", \"reviewerSaKind\": \"number\", \"reviewerSaAnswer\": \"-0,75\"";
    private static final String TF_BODY = "\"questionType\": \"tf\", \"decision\": \"approved_as_is\","
            + " \"statements\": [" + st("a", "T", "easy") + ", " + st("b", "T", "easy") + ", " + st("c", "F", "medium")
            + ", " + st("d", "T", "hard") + "]";

    private static final List<String> MCQ_FIELDS = List.of("\"reviewerAnswer\": \"A\"", "\"finalOption\": \"A\"",
            "\"editedOptionA\": \"x\"", "\"editedOptionB\": \"x\"", "\"editedOptionC\": \"x\"",
            "\"editedOptionD\": \"x\"");
    private static final List<String> SA_FIELDS = List.of("\"reviewerSaKind\": \"number\"",
            "\"reviewerSaAnswer\": \"1\"", "\"finalSaAnswer\": \"1\"");
    private static final List<String> TF_FIELDS = List.of("\"statements\": []",
            "\"statements\": [" + st("a", "T", "easy") + "]");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private int teacher;
    private int mcqQuestion;
    private int saQuestion;
    private int tfQuestion;

    private static String st(String key, String label, String level) {
        return "{\"key\": \"" + key + "\", \"reviewerLabel\": \"" + label + "\", \"difficultyLabel\": \"" + level + "\"}";
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "ReviewForeignFieldsTest.setUp");
        int subject = insert("INSERT INTO subject (name) VALUES ('Toán Trường Thừa')");
        teacher = insert("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.foreign@test', 'teacher')");
        int template = insert("INSERT INTO question_template (subject_id, source_question, is_active, grade_level,"
                + " topic) VALUES (?, 'Mẫu', TRUE, 8, 'Chủ đề')", subject);
        int mcqRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'few-shot-structured', 'm', 0.7, 'p', 'production')", subject);
        mcqQuestion = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, llm_difficulty,"
                + " created_at) VALUES (?, ?, 'Tính $1+1$', '$1$', '$2$', '$3$', '$4$', 'B', '{}', TRUE, 'qc_passed',"
                + " 'easy', now())", template, mcqRun);
        int saRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose, prompt_version, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production',"
                + " 'sa-v0-draft', 'sa')", subject);
        saQuestion = insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text,"
                + " sa_answer, explanation, raw_llm_output, parse_success, status, llm_difficulty, created_at)"
                + " VALUES (?, ?, 'sa', 'Nghiệm của $4x + 3 = 0$?', '-0,75', 'E', '{}', TRUE, 'qc_passed', 'medium',"
                + " now())", template, saRun);
        int tfRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose, prompt_version, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production',"
                + " 'tf-v0-draft', 'tf')", subject);
        tfQuestion = insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text,"
                + " explanation, raw_llm_output, parse_success, status, created_at) VALUES (?, ?, 'tf',"
                + " 'Cho $P = x + 1$.', 'E', '{}', TRUE, 'qc_passed', now())", template, tfRun);
        String[] labels = {"T", "T", "F", "T"};
        for (int i = 0; i < 4; i++) {
            jdbc.update("INSERT INTO generated_question_statement (question_id, position, statement_text, llm_label,"
                    + " llm_difficulty) VALUES (?, ?, ?, ?, 'easy')", tfQuestion, "abcd".substring(i, i + 1),
                    "Ý số " + (i + 1), labels[i]);
        }
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private ResultActions submit(int questionId, String fields) throws Exception {
        String body = "{\"reviewerId\": " + teacher + ", \"formatOk\": true, " + fields + "}";
        return mockMvc.perform(post("/api/reviews/questions/" + questionId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertMismatch(int questionId, String body, String extra) throws Exception {
        submit(questionId, body + ", " + extra)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(MISMATCH));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class), extra);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM bank_item", Integer.class), extra);
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, questionId), extra);
    }

    private static String nulls(List<String> fields) {
        return String.join(", ", fields.stream().map(f -> f.substring(0, f.indexOf(':')) + ": null").toList());
    }

    // ---------- trường khác null: 400, kể cả khi có questionType ----------

    @Test
    void mcqKemTruongSaHoacStatements() throws Exception {
        for (String f : SA_FIELDS) {
            assertMismatch(mcqQuestion, MCQ_BODY, f);
        }
        for (String f : TF_FIELDS) {
            assertMismatch(mcqQuestion, MCQ_BODY, f);
        }
    }

    @Test
    void saKemStatementsHoacTruongMcq() throws Exception {
        for (String f : TF_FIELDS) {
            assertMismatch(saQuestion, SA_BODY, f);
        }
        for (String f : MCQ_FIELDS) {
            assertMismatch(saQuestion, SA_BODY, f);
        }
    }

    @Test
    void tfKemTruongMcqHoacSa() throws Exception {
        for (String f : MCQ_FIELDS) {
            assertMismatch(tfQuestion, TF_BODY, f);
        }
        assertMismatch(tfQuestion, TF_BODY, "\"difficultyLabel\": \"easy\"");
        for (String f : SA_FIELDS) {
            assertMismatch(tfQuestion, TF_BODY, f);
        }
    }

    @Test
    void khongCoQuestionTypeVanDungQuyTacChung() throws Exception {
        assertMismatch(saQuestion, SA_BODY.replace("\"questionType\": \"sa\", ", ""), "\"finalOption\": \"A\"");
        assertMismatch(tfQuestion, TF_BODY.replace("\"questionType\": \"tf\", ", ""), "\"reviewerSaAnswer\": \"1\"");
        assertMismatch(mcqQuestion, MCQ_BODY.replace("\"questionType\": \"mcq\", ", ""), "\"statements\": []");
    }

    // ---------- trường mang giá trị null: bỏ qua ----------

    @Test
    void mcqKemTruongDangKhacMangNullDuocChap() throws Exception {
        submit(mcqQuestion, MCQ_BODY + ", " + nulls(SA_FIELDS) + ", " + nulls(TF_FIELDS.subList(0, 1)))
                .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class));
    }

    @Test
    void saKemTruongDangKhacMangNullDuocChap() throws Exception {
        submit(saQuestion, SA_BODY + ", " + nulls(MCQ_FIELDS) + ", " + nulls(TF_FIELDS.subList(0, 1)))
                .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class));
    }

    @Test
    void tfKemTruongDangKhacMangNullDuocChap() throws Exception {
        submit(tfQuestion, TF_BODY + ", " + nulls(MCQ_FIELDS) + ", \"difficultyLabel\": null, " + nulls(SA_FIELDS))
                .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM teacher_review_statement", Integer.class));
    }
}
