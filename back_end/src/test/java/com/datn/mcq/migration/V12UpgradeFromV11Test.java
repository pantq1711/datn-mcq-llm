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
 * V12 (W1-1): refresh_token.revoke_reason nhận thêm 'email_changed' (admin đổi email thì thu hồi phiên); giá trị lạ vẫn
 * bị chặn, dữ liệu cũ giữ nguyên. Chạy trên schema tạm {@value #SCHEMA}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V12UpgradeFromV11Test {

    private static final String SCHEMA = "v12_upgrade_test";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V12UpgradeFromV11Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V12UpgradeFromV11Test.after");
    }

    private void dropSchema(String stage) {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, stage);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).createSchemas(true)
                .locations("classpath:db/migration").target(target).load();
    }

    @Test
    void v12ChoPhepLyDoEmailChanged() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V12UpgradeFromV11Test.migrate");
        assertEquals(11, flyway("11").migrate().migrationsExecuted);
        int user = jdbc.queryForObject("INSERT INTO " + SCHEMA + ".users (name, email, role) VALUES ('GV', 'gv@test.vn',"
                + " 'teacher') RETURNING id", Integer.class);
        String insert = "INSERT INTO " + SCHEMA + ".refresh_token (user_id, token_hash, family_id, expires_at,"
                + " family_expires_at, revoked_at, revoke_reason) VALUES (?, ?, gen_random_uuid(),"
                + " now() + interval '7 days', now() + interval '30 days', now(), ?)";
        jdbc.update(insert, user, "a".repeat(64), "logout");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, user, "b".repeat(64),
                "email_changed"), "trước V12 chưa có lý do email_changed");

        assertEquals(1, flyway("12").migrate().migrationsExecuted);

        jdbc.update(insert, user, "c".repeat(64), "email_changed");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, user, "d".repeat(64), "vi-sao"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".refresh_token", Integer.class),
                "dữ liệu cũ giữ nguyên");
    }
}
