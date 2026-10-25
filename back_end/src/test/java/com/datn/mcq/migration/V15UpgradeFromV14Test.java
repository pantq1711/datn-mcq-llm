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

/**
 * V15 (W1-4, sinh theo bài): exam_matrix.kind ('matrix' | 'lesson'). Ma trận cũ giữ nguyên với kind = 'matrix'; giá
 * trị khác bị CHECK chặn. Chạy trên schema tạm {@value #SCHEMA}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V15UpgradeFromV14Test {

    private static final String SCHEMA = "v15_upgrade_test";
    private static final String S = SCHEMA + ".";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V15UpgradeFromV14Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V15UpgradeFromV14Test.after");
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
    void v15ThemKindGiuMaTranCuLaMatrixVaChanGiaTriLa() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V15UpgradeFromV14Test.migrate");
        assertEquals(14, flyway("14").migrate().migrationsExecuted);
        int subject = insert("INSERT INTO " + S + "subject (name) VALUES ('Toán')");
        int teacher = insert("INSERT INTO " + S + "users (name, email, role) VALUES ('GV', 'gv.v15@test', 'teacher')");
        int old = insert("INSERT INTO " + S + "exam_matrix (name, subject_id, created_by) VALUES ('Cũ', ?, ?)", subject,
                teacher);

        assertEquals(1, flyway("15").migrate().migrationsExecuted);

        assertEquals("matrix", jdbc.queryForObject("SELECT kind FROM " + S + "exam_matrix WHERE id = ?", String.class,
                old));
        assertEquals("lesson", jdbc.queryForObject("INSERT INTO " + S + "exam_matrix (name, subject_id, created_by,"
                + " kind) VALUES ('Bài', ?, ?, 'lesson') RETURNING kind", String.class, subject, teacher));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO " + S + "exam_matrix (name,"
                + " subject_id, created_by, kind) VALUES ('Sai', ?, ?, 'khac')", subject, teacher));
    }
}
