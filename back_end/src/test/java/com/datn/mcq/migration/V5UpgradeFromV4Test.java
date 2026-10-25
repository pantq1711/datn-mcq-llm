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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V5 (quyết định Q8 ngày 22/10): thêm quality_check_result.warnings (JSONB, cho phép NULL), chỉ dùng ở dòng
 * check_type = 'format'. Nâng cấp một database đang ở V4 có dữ liệu mcq, sa, tf mà không đổi dữ liệu cũ.
 *
 * <p>Chạy Flyway riêng trên schema tạm {@value #SCHEMA} trong datn_mcq_test, do chính test tạo và xóa sau khi guard
 * xác nhận đang ở datn_mcq_test.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V5UpgradeFromV4Test {

    private static final String SCHEMA = "v5_upgrade_test";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void dropLeftoverSchema() {
        dropSchema("V5UpgradeFromV4Test.before");
    }

    @AfterEach
    void dropTempSchema() {
        dropSchema("V5UpgradeFromV4Test.after");
    }

    private void dropSchema(String stage) {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, stage);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql.replace("§", SCHEMA + ".") + " RETURNING id", Integer.class, args);
    }

    private void exec(String sql, Object... args) {
        jdbc.update(sql.replace("§", SCHEMA + "."), args);
    }

    private List<Map<String, Object>> qcRows() {
        return jdbc.queryForList("SELECT id, question_id, check_type, passed, similarity_score, compared_to_question_id,"
                + " threshold_used, judge_answer, judge_model, question_type, judge_sa_kind, judge_sa_answer, checked_at"
                + " FROM " + SCHEMA + ".quality_check_result ORDER BY id");
    }

    @Test
    void v5ThemCotWarningsKhongDoiDuLieuV4() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V5UpgradeFromV4Test.migrateV4");
        assertEquals(4, flyway("4").migrate().migrationsExecuted);

        int subject = insert("INSERT INTO §subject (name) VALUES ('Toán nâng cấp V5')");
        int template = insert("INSERT INTO §question_template (subject_id, source_question, version, is_active)"
                + " VALUES (?, 'Câu mẫu', 1, TRUE)", subject);
        int mcqRun = insert("INSERT INTO §experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
        int saRun = insert("INSERT INTO §experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose, prompt_version, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production',"
                + " 'sa-v0-draft', 'sa')", subject);
        int tfRun = insert("INSERT INTO §experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose, prompt_version, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production',"
                + " 'tf-v0-draft', 'tf')", subject);
        int mcq = insert("INSERT INTO §generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status) VALUES (?, ?, 'Tính $1+1$',"
                + " '1', '2', '3', '4', 'B', '{}', TRUE, 'qc_passed')", template, mcqRun);
        int sa = insert("INSERT INTO §generated_question (template_id, run_id, question_type, question_text, sa_answer,"
                + " raw_llm_output, parse_success, status, llm_difficulty) VALUES (?, ?, 'sa', 'Giải $2x = 6$', '3',"
                + " '{}', TRUE, 'generated', 'easy')", template, saRun);
        int tf = insert("INSERT INTO §generated_question (template_id, run_id, question_type, question_text,"
                + " raw_llm_output, parse_success, status) VALUES (?, ?, 'tf', 'Cho $P = x$.', '{}', TRUE, 'qc_passed')",
                template, tfRun);

        exec("INSERT INTO §quality_check_result (question_id, check_type, passed, checked_at) VALUES (?, 'format', TRUE,"
                + " now())", mcq);
        exec("INSERT INTO §quality_check_result (question_id, check_type, passed, similarity_score,"
                + " compared_to_question_id, threshold_used, checked_at) VALUES (?, 'dedup', TRUE, 0.4, ?, 0.9, now())",
                mcq, sa);
        exec("INSERT INTO §quality_check_result (question_id, check_type, passed, judge_answer, judge_model,"
                + " checked_at) VALUES (?, 'answer_check', TRUE, 'B', 'solver', now())", mcq);
        exec("INSERT INTO §quality_check_result (question_id, question_type, check_type, passed, checked_at)"
                + " VALUES (?, 'sa', 'format', TRUE, now())", sa);
        exec("INSERT INTO §quality_check_result (question_id, question_type, check_type, passed, judge_model,"
                + " judge_sa_kind, judge_sa_answer, checked_at) VALUES (?, 'sa', 'answer_check', FALSE, 'solver',"
                + " 'unparseable', '2 hoặc 3', now())", sa);
        exec("INSERT INTO §quality_check_result (question_id, question_type, check_type, passed, checked_at)"
                + " VALUES (?, 'tf', 'format', TRUE, now())", tf);
        List<Map<String, Object>> before = qcRows();

        assertEquals(1, flyway("5").migrate().migrationsExecuted);

        assertEquals(before, qcRows(), "các cột cũ của quality_check_result không đổi");
        assertEquals(before.size(), jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".quality_check_result"
                + " WHERE warnings IS NULL", Integer.class), "dòng cũ có warnings = NULL");

        // Chỉ dòng format được có warnings, và phải là mảng JSON.
        exec("UPDATE §quality_check_result SET warnings = '[\"label: 4 ý cùng nhãn T\"]'::jsonb"
                + " WHERE question_id = ? AND check_type = 'format'", tf);
        assertEquals("[\"label: 4 ý cùng nhãn T\"]", jdbc.queryForObject("SELECT warnings::text FROM " + SCHEMA
                + ".quality_check_result WHERE question_id = ? AND check_type = 'format'", String.class, tf));
        assertThrows(DataIntegrityViolationException.class, () -> exec("UPDATE §quality_check_result SET warnings ="
                + " '[\"x\"]'::jsonb WHERE question_id = ? AND check_type = 'answer_check'", sa));
        assertThrows(DataIntegrityViolationException.class, () -> exec("UPDATE §quality_check_result SET warnings ="
                + " '{\"x\": 1}'::jsonb WHERE question_id = ? AND check_type = 'format'", mcq));
        assertNull(jdbc.queryForObject("SELECT warnings FROM " + SCHEMA + ".quality_check_result WHERE question_id = ?"
                + " AND check_type = 'format'", Object.class, mcq));
    }
}
