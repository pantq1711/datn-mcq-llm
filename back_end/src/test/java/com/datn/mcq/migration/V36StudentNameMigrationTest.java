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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V36 (quyết định 25/11/2026): chế độ nhận diện theo bài giao và họ tên học sinh. Nâng cấp V35 → V36 trên dữ liệu cũ:
 * <ul>
 *   <li>bài giao cũ nhận {@code identity_mode = 'seat'} (hành vi không đổi); bài giao chèn sau V36 không ghi chế độ thì là
 *       {@code 'name'}; giá trị khác bị CHECK chặn;</li>
 *   <li>lượt cũ giữ nguyên {@code seat_no}, {@code student_name} NULL;</li>
 *   <li>{@code seat_no} hết NOT NULL; mỗi lượt có đúng một trong hai ({@code seat_no} hoặc {@code student_name});
 *       {@code student_name} 2–60 ký tự;</li>
 *   <li>có index nhận diện cho chế độ tên.</li>
 * </ul>
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V36StudentNameMigrationTest {

    private static final String SCHEMA = "v36_student_name_test";
    private static final String S = SCHEMA + ".";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V36StudentNameMigrationTest.before");
    }

    @AfterEach
    void after() {
        dropSchema("V36StudentNameMigrationTest.after");
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

    private int id(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private int assignment(int exam, int teacher, String code, String modeColumn, String modeValue) {
        return id("INSERT INTO " + S + "exam_assignment (exam_id, created_by, public_code, title, duration_minutes,"
                + " closes_at, show_score, scoring, consent_confirmed_at" + modeColumn + ") VALUES (?, ?, ?, 'Đề',"
                + " 45, now() + interval '1 day', TRUE, '{}'::jsonb, now()" + modeValue + ")", exam, teacher, code);
    }

    private int attempt(int assignment, Integer seat, String name, String tokenChar) {
        return id("INSERT INTO " + S + "assignment_attempt (assignment_id, class_label, seat_no, student_name,"
                + " token_hash, started_at, deadline_at) VALUES (?, '8A1', ?, ?, repeat(?, 64), now(),"
                + " now() + interval '45 minutes')", assignment, seat, name, tokenChar);
    }

    @Test
    void nangCapGiuBaiGiaoCuOCheDoSoThuTuVaThemHoTen() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V36StudentNameMigrationTest.seed");
        assertEquals(35, flyway("35").migrate().migrationsExecuted);

        int subject = id("INSERT INTO " + S + "subject (name) VALUES ('Toán')");
        int teacher = id("INSERT INTO " + S + "users (name, email, role) VALUES ('GV', 'gv36@test.local', 'teacher')");
        int exam = id("INSERT INTO " + S + "exam (exam_code, title, subject_id, created_by) VALUES ('V36', 'Đề', ?, ?)",
                subject, teacher);
        int old = id("INSERT INTO " + S + "exam_assignment (exam_id, created_by, public_code, title, duration_minutes,"
                + " closes_at, show_score, scoring, consent_confirmed_at) VALUES (?, ?, 'AAAAAAAAAA', 'Đề', 45,"
                + " now() + interval '1 day', TRUE, '{}'::jsonb, now())", exam, teacher);
        jdbc.update("INSERT INTO " + S + "assignment_class (assignment_id, class_label, roster_size, position)"
                + " VALUES (?, '8A1', 40, 1)", old);
        int oldAttempt = id("INSERT INTO " + S + "assignment_attempt (assignment_id, class_label, seat_no, token_hash,"
                + " started_at, deadline_at) VALUES (?, '8A1', 12, repeat('a', 64), now(), now() + interval '45 minutes')",
                old);

        assertEquals(1, flyway("36").migrate().migrationsExecuted);

        // Dữ liệu cũ: chế độ số thứ tự, lượt giữ nguyên.
        assertEquals("seat", jdbc.queryForObject("SELECT identity_mode FROM " + S + "exam_assignment WHERE id = ?",
                String.class, old));
        Map<String, Object> row = jdbc.queryForMap("SELECT seat_no, student_name FROM " + S
                + "assignment_attempt WHERE id = ?", oldAttempt);
        assertEquals(12, ((Number) row.get("seat_no")).intValue());
        assertNull(row.get("student_name"));

        // Bài giao mới không ghi chế độ: 'name'. Ghi rõ 'seat' được. Giá trị lạ bị chặn.
        int fresh = assignment(exam, teacher, "BBBBBBBBBB", "", "");
        assertEquals("name", jdbc.queryForObject("SELECT identity_mode FROM " + S + "exam_assignment WHERE id = ?",
                String.class, fresh));
        assignment(exam, teacher, "CCCCCCCCCC", ", identity_mode", ", 'seat'");
        assertThrows(DataIntegrityViolationException.class,
                () -> assignment(exam, teacher, "DDDDDDDDDD", ", identity_mode", ", 'both'"));
        assertThrows(DataIntegrityViolationException.class,
                () -> assignment(exam, teacher, "EEEEEEEEEE", ", identity_mode", ", NULL"));
        jdbc.update("INSERT INTO " + S + "assignment_class (assignment_id, class_label, roster_size, position)"
                + " VALUES (?, '8A1', 40, 1)", fresh);

        // Lượt chế độ tên: seat_no NULL, có họ tên.
        int named = attempt(fresh, null, "Nguyễn Văn An", "b");
        assertEquals("Nguyễn Văn An", jdbc.queryForObject("SELECT student_name FROM " + S
                + "assignment_attempt WHERE id = ?", String.class, named));
        // Không có cả hai, hoặc có cả hai: bị chặn.
        assertThrows(DataIntegrityViolationException.class, () -> attempt(fresh, null, null, "c"));
        assertThrows(DataIntegrityViolationException.class, () -> attempt(fresh, 3, "Trần Bình", "d"));
        // Độ dài 2..60 ký tự (ký tự có dấu tính là một).
        assertThrows(DataIntegrityViolationException.class, () -> attempt(fresh, null, "A", "e"));
        assertThrows(DataIntegrityViolationException.class, () -> attempt(fresh, null, "Ữ".repeat(61), "f"));
        attempt(fresh, null, "Ữ".repeat(60), "1");
        attempt(fresh, null, "Ơn", "2");
        // Số thứ tự vẫn bị chặn ngoài 1..99.
        assertThrows(DataIntegrityViolationException.class, () -> attempt(old, 100, null, "3"));

        // Index nhận diện cho chế độ tên.
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname = ? AND tablename ="
                + " 'assignment_attempt' AND indexname = 'idx_aa_identity_name'", Integer.class, SCHEMA) == 1);

        // Xóa bài giao xóa luôn lượt có họ tên (dây chuyền V32).
        jdbc.update("DELETE FROM " + S + "exam_assignment WHERE id = ?", fresh);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + S + "assignment_attempt WHERE student_name IS"
                + " NOT NULL", Integer.class));
    }
}
