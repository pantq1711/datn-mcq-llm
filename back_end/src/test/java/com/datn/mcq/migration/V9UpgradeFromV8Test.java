package com.datn.mcq.migration;

import com.datn.mcq.config.TestSafetyConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9 (ngày 23/10): exam_slot_action.instruction (yêu cầu bổ sung của giáo viên khi Đổi câu, chỉ dòng 'replace', 1-500
 * ký tự). Nâng cấp từ V8 trên schema tạm {@value #SCHEMA} trong datn_mcq_test, do test tạo và xóa.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V9UpgradeFromV8Test {

    private static final String SCHEMA = "v9_upgrade_test";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V9UpgradeFromV8Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V9UpgradeFromV8Test.after");
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
    void v9ThemCotInstructionChoNhatKyDoiCau() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V9UpgradeFromV8Test.migrate");
        assertEquals(8, flyway("8").migrate().migrationsExecuted);
        assertEquals(1, flyway("9").migrate().migrationsExecuted);

        Map<String, Object> column = jdbc.queryForMap("SELECT data_type, is_nullable FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = 'exam_slot_action' AND column_name = 'instruction'", SCHEMA);
        assertEquals("text", column.get("data_type"));
        assertEquals("YES", column.get("is_nullable"));
        String check = jdbc.queryForObject("SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c JOIN pg_namespace n"
                + " ON n.oid = c.connamespace WHERE n.nspname = ? AND c.conname = 'chk_esa_instruction'", String.class,
                SCHEMA);
        assertTrue(check.contains("replace") && check.contains("500"), check);
    }
}
