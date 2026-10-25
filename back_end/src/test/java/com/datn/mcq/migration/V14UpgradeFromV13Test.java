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
 * V14 (W1-3, vết QC): quality_check_result.solver_reasoning (lời giải thích của Solver, chỉ dòng answer_check, tối đa
 * 4000 ký tự) và generated_question.solver_error (lý do Solver lỗi với câu, tối đa 1000 ký tự). Dữ liệu cũ giữ nguyên
 * với giá trị NULL. Chạy trên schema tạm {@value #SCHEMA}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V14UpgradeFromV13Test {

    private static final String SCHEMA = "v14_upgrade_test";
    private static final String S = SCHEMA + ".";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V14UpgradeFromV13Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V14UpgradeFromV13Test.after");
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

    @Test
    void v14ThemCotVetQcGiuDuLieuCuVaChanQuaDai() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V14UpgradeFromV13Test.migrate");
        assertEquals(13, flyway("13").migrate().migrationsExecuted);
        int subject = insert("INSERT INTO " + S + "subject (name) VALUES ('Toán')");
        int tpl = insert("INSERT INTO " + S + "question_template (subject_id, source_question, is_active)"
                + " VALUES (?, 'Mẫu', TRUE)", subject);
        int run = insert("INSERT INTO " + S + "experiment_run (subject_id, strategy, model_name, temperature,"
                + " prompt_text, purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
        int q = insert("INSERT INTO " + S + "generated_question (template_id, run_id, question_text, option_a,"
                + " option_b, option_c, option_d, correct_option, raw_llm_output, parse_success, status)"
                + " VALUES (?, ?, 'Đề', '1', '2', '3', '4', 'B', '{}', TRUE, 'generated')", tpl, run);
        int oldFormat = insert("INSERT INTO " + S + "quality_check_result (question_id, check_type, passed)"
                + " VALUES (?, 'format', TRUE)", q);
        int oldSolver = insert("INSERT INTO " + S + "quality_check_result (question_id, check_type, passed,"
                + " judge_answer, judge_model) VALUES (?, 'answer_check', TRUE, 'B', 'm')", q);

        assertEquals(1, flyway("14").migrate().migrationsExecuted);

        assertNull(jdbc.queryForObject("SELECT solver_reasoning FROM " + S + "quality_check_result WHERE id = ?",
                String.class, oldFormat));
        assertNull(jdbc.queryForObject("SELECT solver_reasoning FROM " + S + "quality_check_result WHERE id = ?",
                String.class, oldSolver), "kết quả Solver cũ không có lời giải thích");
        assertNull(jdbc.queryForObject("SELECT solver_error FROM " + S + "generated_question WHERE id = ?",
                String.class, q));

        // solver_reasoning: chỉ dòng answer_check, tối đa 4000 ký tự.
        jdbc.update("UPDATE " + S + "quality_check_result SET solver_reasoning = ? WHERE id = ?", "x".repeat(4000),
                oldSolver);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE " + S
                + "quality_check_result SET solver_reasoning = ? WHERE id = ?", "x".repeat(4001), oldSolver));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE " + S
                + "quality_check_result SET solver_reasoning = 'r' WHERE id = ?", oldFormat),
                "dòng format không có lời giải thích của Solver");

        // solver_error: tối đa 1000 ký tự.
        jdbc.update("UPDATE " + S + "generated_question SET solver_error = ? WHERE id = ?", "e".repeat(1000), q);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE " + S
                + "generated_question SET solver_error = ? WHERE id = ?", "e".repeat(1001), q));
    }
}
