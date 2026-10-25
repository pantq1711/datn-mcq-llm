package com.datn.mcq.migration;

import com.datn.mcq.config.TestSafetyConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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
 * V11 (W1-1): users có mật khẩu/trạng thái, vai trò admin, email duy nhất (không phân biệt hoa thường), bảng
 * refresh_token, exam_matrix.created_by. Ma trận cũ: lấy người tạo đề dùng ma trận; ma trận chưa tạo đề thì giao cho
 * giáo viên có id nhỏ nhất (H7), chưa có giáo viên nào thì tạo giáo viên mặc định. Chạy trên schema tạm {@value #SCHEMA}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V11UpgradeFromV10Test {

    private static final String SCHEMA = "v11_upgrade_test";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V11UpgradeFromV10Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V11UpgradeFromV10Test.after");
    }

    private void dropSchema(String stage) {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, stage);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).createSchemas(true)
                .locations("classpath:db/migration").target(target).load();
    }

    private int insert(String sql, Object... args) {
        return jdbc.queryForObject(sql.replace("{s}", SCHEMA) + " RETURNING id", Integer.class, args);
    }

    private int matrix(int subject, String name) {
        return insert("INSERT INTO {s}.exam_matrix (name, subject_id) VALUES (?, ?)", name, subject);
    }

    private Map<String, Object> user(int id) {
        return jdbc.queryForMap("SELECT * FROM " + SCHEMA + ".users WHERE id = ?", id);
    }

    @Test
    void v11GanChuSoHuuMaTranCuVaGiuUserCu() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V11UpgradeFromV10Test.migrate");
        assertEquals(10, flyway("10").migrate().migrationsExecuted);
        int subject = insert("INSERT INTO {s}.subject (name) VALUES ('Toán')");
        int researcher = insert("INSERT INTO {s}.users (name, email, role) VALUES ('NC', 'nc@test.vn', 'researcher')");
        int teacher1 = insert("INSERT INTO {s}.users (name, email, role) VALUES ('GV1', 'GV1@Test.VN', 'teacher')");
        int teacher2 = insert("INSERT INTO {s}.users (name, email, role) VALUES ('GV2', '  ', 'teacher')");
        int used = matrix(subject, "Đã tạo đề");
        int unused = matrix(subject, "Chưa tạo đề");
        insert("INSERT INTO {s}.exam (exam_code, title, subject_id, created_by, matrix_id, mode) VALUES ('E1', 'Đề', ?,"
                + " ?, ?, 'teacher')", subject, teacher2, used);

        assertEquals(1, flyway("11").migrate().migrationsExecuted);

        assertEquals(teacher2, jdbc.queryForObject("SELECT created_by FROM " + SCHEMA + ".exam_matrix WHERE id = ?",
                Integer.class, used), "lấy người tạo đề dùng ma trận");
        assertEquals(teacher1, jdbc.queryForObject("SELECT created_by FROM " + SCHEMA + ".exam_matrix WHERE id = ?",
                Integer.class, unused), "ma trận chưa tạo đề giao cho giáo viên id nhỏ nhất");
        assertEquals("NO", jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema = ?"
                + " AND table_name = 'exam_matrix' AND column_name = 'created_by'", String.class, SCHEMA));

        Map<String, Object> u1 = user(teacher1);
        assertEquals("GV1@Test.VN", u1.get("email"), "không đổi email đã có");
        assertNull(u1.get("password_hash"), "user cũ chưa có mật khẩu (không đăng nhập được tới khi admin đặt)");
        assertEquals(Boolean.TRUE, u1.get("enabled"));
        assertNull(user(teacher2).get("email"), "email rỗng chuyển thành NULL");
        assertEquals("researcher", user(researcher).get("role"));

        insert("INSERT INTO {s}.users (name, email, role, password_hash) VALUES ('Admin', 'admin@test.vn', 'admin',"
                + " '{bcrypt}x')");
        assertThrows(DataIntegrityViolationException.class, () ->
                insert("INSERT INTO {s}.users (name, email, role) VALUES ('Trùng', ' gv1@test.vn', 'teacher')"),
                "email duy nhất không phân biệt hoa thường");
        assertThrows(DataIntegrityViolationException.class, () ->
                insert("INSERT INTO {s}.users (name, role, password_hash) VALUES ('Không email', 'teacher', 'x')"),
                "có mật khẩu thì phải có email");
        assertThrows(DataIntegrityViolationException.class, () ->
                insert("INSERT INTO {s}.users (name, email, role) VALUES ('Lạ', 'la@test.vn', 'superuser')"));

        int token = insert("INSERT INTO {s}.refresh_token (user_id, token_hash, family_id, expires_at,"
                + " family_expires_at) VALUES (?, ?, gen_random_uuid(), now() + interval '7 days',"
                + " now() + interval '30 days')", researcher, "a".repeat(64));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE " + SCHEMA + ".refresh_token"
                + " SET revoked_at = now() WHERE id = ?", token), "thu hồi phải kèm lý do");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE " + SCHEMA + ".refresh_token"
                + " SET revoked_at = now(), revoke_reason = 'vi-sao' WHERE id = ?", token));
        jdbc.update("DELETE FROM " + SCHEMA + ".users WHERE id = ?", researcher);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".refresh_token", Integer.class),
                "xóa user thì xóa luôn refresh token");
    }

    @Test
    void v11ChuaCoGiaoVienThiTaoGiaoVienMacDinhChoMaTranCu() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V11UpgradeFromV10Test.default");
        assertEquals(10, flyway("10").migrate().migrationsExecuted);
        int subject = insert("INSERT INTO {s}.subject (name) VALUES ('Toán')");
        int orphan = matrix(subject, "Mồ côi");

        assertEquals(1, flyway("11").migrate().migrationsExecuted);

        Integer owner = jdbc.queryForObject("SELECT created_by FROM " + SCHEMA + ".exam_matrix WHERE id = ?",
                Integer.class, orphan);
        Map<String, Object> u = user(owner);
        assertEquals("teacher", u.get("role"));
        assertEquals("teacher.math@ptit.edu.vn", u.get("email"), "trùng giáo viên mặc định của UserService");
        assertNull(u.get("password_hash"));
    }

    @Test
    void v11KhongCoMaTranThiKhongTaoUserNao() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V11UpgradeFromV10Test.empty");
        assertEquals(11, flyway("11").migrate().migrationsExecuted);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".users", Integer.class));
    }

    @Test
    void v11DungLaiKhiEmailTrung() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V11UpgradeFromV10Test.dup");
        assertEquals(10, flyway("10").migrate().migrationsExecuted);
        insert("INSERT INTO {s}.users (name, email, role) VALUES ('A', 'trung@test.vn', 'teacher')");
        insert("INSERT INTO {s}.users (name, email, role) VALUES ('B', ' TRUNG@test.vn', 'teacher')");
        FlywayException ex = assertThrows(FlywayException.class, () -> flyway("11").migrate());
        assertTrue(String.valueOf(ex.getMessage()).contains("email trùng"), ex.getMessage());
    }
}
