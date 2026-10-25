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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V17 (W1-5): seed môn "Toán" và "Giáo dục công dân", idempotent. Chạy trên schema tạm {@value #SCHEMA}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V17UpgradeFromV16Test {

    private static final String SCHEMA = "v17_upgrade_test";
    private static final String S = SCHEMA + ".";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V17UpgradeFromV16Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V17UpgradeFromV16Test.after");
    }

    private void dropSchema(String stage) {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, stage);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).createSchemas(true)
                .locations("classpath:db/migration").target(target).load();
    }

    private List<String> subjectNames() {
        return jdbc.queryForList("SELECT name FROM " + S + "subject ORDER BY name", String.class);
    }

    /** Chạy lại đúng nội dung V17 trên schema tạm (search_path đặt về schema đó trong cùng một kết nối). */
    private void rerunSeed() throws IOException {
        String sql;
        try (var in = getClass().getResourceAsStream("/db/migration/V17__seed_subjects.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        jdbc.execute((java.sql.Connection c) -> {
            try (var st = c.createStatement()) {
                st.execute("SET search_path TO " + SCHEMA);
                try {
                    st.execute(sql);
                } finally {
                    st.execute("SET search_path TO DEFAULT");
                }
            }
            return null;
        });
    }

    @Test
    void dbTrongCoDungHaiMonVaChayLaiKhongThemBanTrung() throws Exception {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V17UpgradeFromV16Test.empty");
        assertEquals(16, flyway("16").migrate().migrationsExecuted);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + S + "subject", Integer.class));

        assertEquals(1, flyway("17").migrate().migrationsExecuted);
        assertEquals(List.of("Giáo dục công dân", "Toán"), subjectNames());

        rerunSeed();
        assertEquals(List.of("Giáo dục công dân", "Toán"), subjectNames(), "chạy lại không thêm bản trùng");
    }

    @Test
    void monDaCoKhongBiTrungKeKhacHoaThuongVaGiuNguyenId() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V17UpgradeFromV16Test.existing");
        assertEquals(16, flyway("16").migrate().migrationsExecuted);
        int toan = jdbc.queryForObject("INSERT INTO " + S + "subject (name) VALUES ('Toán') RETURNING id", Integer.class);
        int gdcd = jdbc.queryForObject("INSERT INTO " + S + "subject (name) VALUES ('giáo dục công dân') RETURNING id",
                Integer.class);

        assertEquals(1, flyway("17").migrate().migrationsExecuted);

        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM " + S + "subject", Integer.class));
        assertEquals("Toán", jdbc.queryForObject("SELECT name FROM " + S + "subject WHERE id = ?", String.class, toan));
        assertEquals("giáo dục công dân", jdbc.queryForObject("SELECT name FROM " + S + "subject WHERE id = ?",
                String.class, gdcd), "tên và id của môn đã có không bị đổi");
    }
}
