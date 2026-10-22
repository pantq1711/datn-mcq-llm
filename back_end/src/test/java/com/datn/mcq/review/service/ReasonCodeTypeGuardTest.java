package com.datn.mcq.review.service;

import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V4 thêm mã lý do statement_undecidable (chỉ tf) và answer_ambiguous (chỉ sa). Trước V4, request chấm câu mcq
 * chứa hai mã này bị từ chối 400 vì enum không có; sau V4 câu mcq vẫn phải nhận 400 và không ghi gì vào DB.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ReasonCodeTypeGuardTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private int question;
    private int teacher;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "ReasonCodeTypeGuardTest.setUp");
        int subject = insert("INSERT INTO subject (name) VALUES ('Toán Lý do')");
        teacher = insert("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.reason@test', 'teacher')");
        int template = insert("INSERT INTO question_template (subject_id, source_question, is_active, grade_level, topic)"
                + " VALUES (?, 'Mẫu', TRUE, 8, 'Phân thức')", subject);
        int run = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'few-shot-structured', 'm', 0.7, 'p', 'production')", subject);
        question = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, created_at)"
                + " VALUES (?, ?, 'Tính $1+1$', '$1$', '$2$', '$3$', '$4$', 'B', '{}', TRUE, 'qc_passed', now())",
                template, run);
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    @ParameterizedTest
    @ValueSource(strings = {"statement_undecidable", "answer_ambiguous"})
    void mcqKhongDungDuocMaLyDoCuaTfSa(String code) throws Exception {
        String body = """
                {"reviewerId": %d, "reviewPurpose": "production", "reviewerAnswer": "none", "formatOk": true,
                 "difficultyLabel": "easy", "decision": "rejected", "reasonCodes": ["content_unclear", "%s"]}
                """.formatted(teacher, code);
        mockMvc.perform(post("/api/reviews/questions/" + question)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Mã lý do '" + code + "' không áp dụng cho dạng câu này."));

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review_reason", Integer.class));
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, question));
    }

    @ParameterizedTest
    @ValueSource(strings = {"content_unclear", "answer_none_correct", "options_indistinct"})
    void mcqVanDungDuocMaLyDoCu(String code) throws Exception {
        String body = """
                {"reviewerId": %d, "reviewPurpose": "production", "reviewerAnswer": "none", "formatOk": true,
                 "difficultyLabel": "easy", "decision": "rejected", "reasonCodes": ["%s"]}
                """.formatted(teacher, code);
        mockMvc.perform(post("/api/reviews/questions/" + question)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionStatus").value("rejected"));
        assertEquals(code, jdbc.queryForObject("SELECT reason_code FROM teacher_review_reason", String.class));
    }
}
