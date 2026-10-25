package com.datn.mcq.migration;

import com.datn.mcq.config.TestSafetyConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V13 (W1-2, giáo viên sửa câu trong đề): generated_question.edited_from_question_id (bản sửa trỏ bản trước, cùng dạng
 * câu, mỗi bản có tối đa một bản sửa tiếp), generation_job.job_type (generate / answer_check), exam_slot_action.
 * changed_fields (chỉ dòng edit). Dữ liệu cũ giữ nguyên. Chạy trên schema tạm {@value #SCHEMA}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V13UpgradeFromV12Test {

    private static final String SCHEMA = "v13_upgrade_test";
    private static final String S = SCHEMA + ".";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V13UpgradeFromV12Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V13UpgradeFromV12Test.after");
    }

    private void dropSchema(String stage) {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, stage);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).createSchemas(true)
                .locations("classpath:db/migration").target(target).load();
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private String gqInsert(String type) {
        return "INSERT INTO " + S + "generated_question (template_id, run_id, question_type, question_text, option_a,"
                + " option_b, option_c, option_d, correct_option, sa_answer, raw_llm_output, parse_success, status,"
                + " edited_from_question_id) VALUES (?, ?, '" + type + "', 'Đề', "
                + ("mcq".equals(type) ? "'1', '2', '3', '4', 'B', NULL" : "NULL, NULL, NULL, NULL, NULL, '5'")
                + ", '', TRUE, 'generated', ?) RETURNING id";
    }

    @Test
    void v13ThemCotSuaCauVaLoaiJobGiuDuLieuCu() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V13UpgradeFromV12Test.migrate");
        assertEquals(12, flyway("12").migrate().migrationsExecuted);
        int user = insert("INSERT INTO " + S + "users (name, email, role) VALUES ('GV', 'gv@test.vn', 'teacher')");
        int subject = insert("INSERT INTO " + S + "subject (name) VALUES ('Toán')");
        int tpl = insert("INSERT INTO " + S + "question_template (subject_id, source_question, is_active)"
                + " VALUES (?, 'Mẫu', TRUE)", subject);
        int run = insert("INSERT INTO " + S + "experiment_run (subject_id, strategy, model_name, temperature,"
                + " prompt_text, purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
        int saRun = insert("INSERT INTO " + S + "experiment_run (subject_id, strategy, model_name, temperature,"
                + " prompt_text, purpose, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', 'sa')",
                subject);
        int llm = insert("INSERT INTO " + S + "generated_question (template_id, run_id, question_text, option_a,"
                + " option_b, option_c, option_d, correct_option, raw_llm_output, parse_success, status)"
                + " VALUES (?, ?, 'Đề', '1', '2', '3', '4', 'B', '{}', TRUE, 'qc_passed')", tpl, run);
        int oldJob = insert("INSERT INTO " + S + "generation_job (run_id, template_id, question_id, status)"
                + " VALUES (?, ?, ?, 'done')", run, tpl, llm);
        int matrix = insert("INSERT INTO " + S + "exam_matrix (name, subject_id, created_by) VALUES ('M', ?, ?)",
                subject, user);
        int cell = insert("INSERT INTO " + S + "exam_matrix_cell (matrix_id, position, part, question_type, topic,"
                + " difficulty, question_count, template_id) VALUES (?, 1, 'I', 'mcq', 'Đa thức', 'easy', 1, ?)",
                matrix, tpl);
        int exam = insert("INSERT INTO " + S + "exam (exam_code, title, subject_id, created_by, mode, matrix_id)"
                + " VALUES ('MT1', 'Đề', ?, ?, 'teacher', ?)", subject, user, matrix);
        int slot = insert("INSERT INTO " + S + "exam_slot (exam_id, cell_id, slot_no, question_type, state,"
                + " generated_question_id) VALUES (?, ?, 1, 'mcq', 'ready', ?)", exam, cell, llm);
        int oldAction = insert("INSERT INTO " + S + "exam_slot_action (slot_id, action, generated_question_id)"
                + " VALUES (?, 'replace', ?)", slot, llm);

        assertEquals(1, flyway("13").migrate().migrationsExecuted);

        assertEquals("generate", jdbc.queryForObject("SELECT job_type FROM " + S + "generation_job WHERE id = ?",
                String.class, oldJob), "job cũ là job sinh câu");
        assertNull(jdbc.queryForObject("SELECT edited_from_question_id FROM " + S + "generated_question WHERE id = ?",
                Integer.class, llm), "câu cũ là đầu ra LLM");
        assertNull(jdbc.queryForObject("SELECT changed_fields::text FROM " + S + "exam_slot_action WHERE id = ?",
                String.class, oldAction));

        // Bản sửa: cùng dạng câu với bản trước; mỗi bản có tối đa một bản sửa tiếp.
        int v1 = jdbc.queryForObject(gqInsert("mcq"), Integer.class, tpl, run, llm);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.queryForObject(gqInsert("mcq"), Integer.class, tpl, run, llm), "hai bản sửa cùng một bản");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.queryForObject(gqInsert("sa"), Integer.class, tpl, saRun, v1), "khác dạng câu");

        // job_type
        String job = "INSERT INTO " + S + "generation_job (run_id, template_id, question_id, exam_slot_id, job_type)"
                + " VALUES (?, ?, ?, ?, ?)";
        jdbc.update(job, run, tpl, v1, null, "answer_check");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(job, run, tpl, null, null,
                "answer_check"), "job Solver phải có câu");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(job, run, tpl, v1, slot,
                "answer_check"), "job Solver không gắn chỗ");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(job, run, tpl, null, null, "khac"));

        // changed_fields
        String action = "INSERT INTO " + S + "exam_slot_action (slot_id, action, generated_question_id, edit_distance,"
                + " changed_fields) VALUES (?, ?, ?, ?, ?::jsonb)";
        jdbc.update(action, slot, "edit", v1, 3, "[\"question_text\"]");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(action, slot, "keep", v1, null,
                "[\"question_text\"]"), "changed_fields chỉ cho dòng edit");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(action, slot, "edit", v1, 3, null),
                "dòng edit phải có changed_fields");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(action, slot, "edit", v1, 3,
                "{\"a\": 1}"), "changed_fields là mảng");
    }
}
