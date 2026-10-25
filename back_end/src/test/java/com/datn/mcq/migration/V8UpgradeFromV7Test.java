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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V8 (ngày 23/10): quality_check_result.errors (JSONB mảng chuỗi, chỉ dòng format chưa qua, NULL khi passed) và
 * generation_job.prompt_note (ghi chú thêm vào prompt của job). Nâng cấp database đang ở V7 có dữ liệu mà không đổi
 * dữ liệu cũ. Chạy Flyway trên schema tạm {@value #SCHEMA} trong datn_mcq_test, do test tạo và xóa.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V8UpgradeFromV7Test {

    private static final String SCHEMA = "v8_upgrade_test";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V8UpgradeFromV7Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V8UpgradeFromV7Test.after");
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
        return jdbc.queryForObject(sql.replace("§", SCHEMA + ".") + " RETURNING id", Integer.class, args);
    }

    private void exec(String sql, Object... args) {
        jdbc.update(sql.replace("§", SCHEMA + "."), args);
    }

    private List<Map<String, Object>> rows(String table) {
        return jdbc.queryForList("SELECT * FROM " + SCHEMA + "." + table + " ORDER BY id");
    }

    @Test
    void v8ThemErrorsVaPromptNoteKhongDoiDuLieuV7() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V8UpgradeFromV7Test.migrateV7");
        assertEquals(7, flyway("7").migrate().migrationsExecuted);

        int subject = insert("INSERT INTO §subject (name) VALUES ('Toán V8')");
        int template = insert("INSERT INTO §question_template (subject_id, source_question, is_active)"
                + " VALUES (?, 'Mẫu', TRUE)", subject);
        int run = insert("INSERT INTO §experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
        int passed = insert("INSERT INTO §generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status) VALUES (?, ?, 'Tính $1+1$',"
                + " '1', '2', '3', '4', 'B', '{}', TRUE, 'qc_passed')", template, run);
        int failed = insert("INSERT INTO §generated_question (template_id, run_id, raw_llm_output, parse_success,"
                + " status) VALUES (?, ?, 'không phải JSON', FALSE, 'format_failed')", template, run);
        exec("INSERT INTO §quality_check_result (question_id, check_type, passed, warnings, checked_at)"
                + " VALUES (?, 'format', TRUE, '[\"w\"]'::jsonb, now())", passed);
        exec("INSERT INTO §quality_check_result (question_id, check_type, passed, checked_at)"
                + " VALUES (?, 'format', FALSE, now())", failed);
        exec("INSERT INTO §quality_check_result (question_id, check_type, passed, judge_answer, judge_model,"
                + " checked_at) VALUES (?, 'answer_check', TRUE, 'B', 'solver', now())", passed);
        exec("INSERT INTO §generation_job (run_id, template_id, question_id, target_difficulty, status, attempt)"
                + " VALUES (?, ?, ?, 'easy', 'done', 1)", run, template, failed);
        List<Map<String, Object>> qcBefore = rows("quality_check_result");
        List<Map<String, Object>> jobsBefore = rows("generation_job");

        assertEquals(1, flyway("8").migrate().migrationsExecuted);

        List<Map<String, Object>> qcAfter = rows("quality_check_result");
        for (int i = 0; i < qcBefore.size(); i++) {
            Map<String, Object> after = new java.util.LinkedHashMap<>(qcAfter.get(i));
            assertEquals(null, after.remove("errors"), "dòng cũ có errors = NULL");
            assertEquals(qcBefore.get(i).toString(), after.toString());
        }
        List<Map<String, Object>> jobsAfter = rows("generation_job");
        Map<String, Object> job = new java.util.LinkedHashMap<>(jobsAfter.get(0));
        assertEquals(null, job.remove("prompt_note"));
        assertEquals(jobsBefore.get(0).toString(), job.toString());

        exec("UPDATE §quality_check_result SET errors = '[\"question_text: số dấu $ lẻ\"]'::jsonb"
                + " WHERE question_id = ? AND check_type = 'format'", failed);
        assertThrows(DataIntegrityViolationException.class, () -> exec("UPDATE §quality_check_result SET errors ="
                + " '[\"x\"]'::jsonb WHERE question_id = ? AND check_type = 'format'", passed), "dòng passed");
        assertThrows(DataIntegrityViolationException.class, () -> exec("UPDATE §quality_check_result SET errors ="
                + " '[\"x\"]'::jsonb WHERE question_id = ? AND check_type = 'answer_check'", passed), "dòng khác format");
        assertThrows(DataIntegrityViolationException.class, () -> exec("UPDATE §quality_check_result SET errors ="
                + " '\"x\"'::jsonb WHERE question_id = ? AND check_type = 'format'", failed), "không phải mảng");
        exec("UPDATE §generation_job SET prompt_note = 'Đây là câu thứ 1/2 của ô này.'");
    }
}
