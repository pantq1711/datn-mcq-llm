package com.datn.mcq.auth;

import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W1-1: tài khoản ADMIN đầu tiên tạo từ biến môi trường (ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME) khi khởi động; chỉ
 * tạo khi chưa có admin nào đang hoạt động, không bao giờ ghi đè mật khẩu. Bean lấy theo tên để test không phụ thuộc
 * lớp main.
 */
@SpringBootTest(properties = {
        "app.security.bootstrap-admin.email=  Admin.Dau@Test.VN ",
        "app.security.bootstrap-admin.password=mat-khau-admin-dai",
        "app.security.bootstrap-admin.name=Quản trị đầu tiên"})
@Import(TestSafetyConfiguration.class)
class AdminBootstrapTest {

    @Autowired
    private ApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TestDataCleaner.cleanAll(dataSource, jdbc, "AdminBootstrapTest.setUp");
    }

    private void runBootstrap() throws Exception {
        context.getBean("adminBootstrap", ApplicationRunner.class).run(new DefaultApplicationArguments());
    }

    @Test
    void taoMotAdminTuBienMoiTruongVaKhongGhiDe() throws Exception {
        runBootstrap();
        Map<String, Object> admin = jdbc.queryForMap("SELECT * FROM users WHERE role = 'admin'");
        assertEquals("admin.dau@test.vn", admin.get("email"), "email chuẩn hóa chữ thường, bỏ khoảng trắng");
        assertEquals("Quản trị đầu tiên", admin.get("name"));
        assertEquals(Boolean.TRUE, admin.get("enabled"));
        String hash = (String) admin.get("password_hash");
        assertTrue(hash.startsWith("{bcrypt}"), hash);
        assertTrue(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("mat-khau-admin-dai", hash));
        assertNotEquals("mat-khau-admin-dai", hash);

        jdbc.update("UPDATE users SET password_hash = '{bcrypt}doi-boi-admin' WHERE id = ?", admin.get("id"));
        runBootstrap();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'admin'", Integer.class));
        assertEquals("{bcrypt}doi-boi-admin", jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?",
                String.class, admin.get("id")), "không ghi đè mật khẩu khi đã có admin");
    }

    @Test
    void daCoAdminKhacThiKhongTaoThem() throws Exception {
        jdbc.update("INSERT INTO users (name, email, role, password_hash) VALUES ('Admin cũ', 'cu@test.vn', 'admin',"
                + " '{bcrypt}x')");
        runBootstrap();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'admin'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'admin.dau@test.vn'",
                Integer.class));
    }

    @Test
    void emailDaThuocTaiKhoanKhacThiKhongChiemQuyen() throws Exception {
        jdbc.update("INSERT INTO users (name, email, role) VALUES ('GV', 'admin.dau@test.vn', 'teacher')");
        runBootstrap();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'admin'", Integer.class));
        assertEquals("teacher", jdbc.queryForObject("SELECT role FROM users WHERE email = 'admin.dau@test.vn'",
                String.class));
    }
}
