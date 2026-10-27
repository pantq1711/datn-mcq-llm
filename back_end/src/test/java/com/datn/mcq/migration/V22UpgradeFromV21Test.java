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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V22 (W2-4b, A): generation_job thêm claim_seq INT NOT NULL DEFAULT 0, chk_gj_claim_seq (claim_seq >= 0). Chạy trên schema
 * tạm {@value #SCHEMA}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V22UpgradeFromV21Test {

    private static final String SCHEMA = "v22_upgrade_test";
    private static final String S = SCHEMA + ".";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private int run;
    private int template;

    @BeforeEach
    void before() {
        dropSchema("V22UpgradeFromV21Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V22UpgradeFromV21Test.after");
    }

    private void dropSchema(String stage) {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, stage);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).createSchemas(true)
                .locations("classpath:db/migration").target(target).load();
    }

    private int id(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private void migrateTo21AndSeed() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V22UpgradeFromV21Test.migrate");
        assertEquals(21, flyway("21").migrate().migrationsExecuted);
        int subject = id("INSERT INTO " + S + "subject (name) VALUES ('Toán')");
        template = id("INSERT INTO " + S + "question_template (subject_id, source_question, is_active, topic)"
                + " VALUES (?, 'Mẫu', TRUE, 'Chủ đề')", subject);
        run = id("INSERT INTO " + S + "experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
    }

    @Test
    void dongCuCoClaimSeqBangKhongVaSoAmBiTuChoi() {
        migrateTo21AndSeed();
        int job = id("INSERT INTO " + S + "generation_job (run_id, template_id, target_difficulty)"
                + " VALUES (?, ?, 'easy')", run, template);

        assertEquals(1, flyway("22").migrate().migrationsExecuted);

        assertEquals(0, jdbc.queryForObject("SELECT claim_seq FROM " + S + "generation_job WHERE id = ?",
                Integer.class, job));
        jdbc.update("UPDATE " + S + "generation_job SET claim_seq = 2 WHERE id = ?", job);
        assertEquals(2, jdbc.queryForObject("SELECT claim_seq FROM " + S + "generation_job WHERE id = ?",
                Integer.class, job));

        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE " + S + "generation_job SET claim_seq = -1 WHERE id = ?", job));
        assertTrue(String.valueOf(ex.getMessage()).contains("chk_gj_claim_seq"), ex.getMessage());
    }
}

