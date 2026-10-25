package com.datn.mcq.auth;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-1 đợt 2: quản lý tài khoản /api/admin/users (chỉ ADMIN). Admin tạo giáo viên (không có đăng ký tự do), sửa tên,
 * bật/khóa, đặt lại mật khẩu; khóa hoặc đặt lại mật khẩu thì thu hồi refresh token của người đó. Không tự khóa mình,
 * không khóa admin đang hoạt động cuối cùng. Không bao giờ trả mật khẩu băm.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class AdminUserApiTest {

    private static final String TEACHER_PASSWORD = "matkhau-gv-1";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int admin;
    private int teacher;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "AdminUserApiTest.setUp");
        admin = AuthTestSupport.insertUser(jdbc, "Quản trị", "admin.users@test.vn", "admin", "matkhau-admin-12", true);
        teacher = AuthTestSupport.insertUser(jdbc, "GV cũ", "gv.cu.users@test.vn", "teacher", TEACHER_PASSWORD, true);
    }

    private RequestPostProcessor asAdmin() {
        return AuthTestSupport.admin(admin);
    }

    private ResultActions postJson(String url, String body, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(post(url).with(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions patchJson(String url, String body, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(patch(url).with(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
    }

    /** Đăng nhập giáo viên cũ, trả refresh token (cookie). */
    private String teacherRefreshToken() throws Exception {
        return login("gv.cu.users@test.vn", TEACHER_PASSWORD).andExpect(status().isOk()).andReturn().getResponse()
                .getCookie(AuthTestSupport.REFRESH_COOKIE).getValue();
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(AuthTestSupport.REFRESH_COOKIE, token)));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static void assertNoSecret(JsonNode node) {
        String text = node.toString();
        assertFalse(text.contains("password") || text.contains("{bcrypt}"), "không trả mật khẩu/băm: " + text);
    }

    @Test
    void adminTaoGiaoVienVaGiaoVienDangNhapDuoc() throws Exception {
        JsonNode created = json(postJson("/api/admin/users", "{\"name\": \"  Nguyễn Văn Mới \", \"email\":"
                + " \" GV.Moi@Test.VN \", \"password\": \"matkhau-moi-1\"}", asAdmin())
                .andExpect(status().isCreated()));
        assertEquals("Nguyễn Văn Mới", created.get("name").asText());
        assertEquals("gv.moi@test.vn", created.get("email").asText(), "email chuẩn hóa chữ thường");
        assertEquals("teacher", created.get("role").asText(), "admin chỉ tạo tài khoản giáo viên");
        assertTrue(created.get("enabled").asBoolean());
        assertTrue(created.get("hasPassword").asBoolean());
        assertTrue(created.hasNonNull("createdAt"));
        assertNoSecret(created);

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class,
                created.get("id").asInt());
        assertTrue(hash.startsWith("{bcrypt}"), hash);
        assertTrue(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("matkhau-moi-1", hash));

        login("gv.moi@test.vn", "matkhau-moi-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("teacher"));
    }

    @Test
    void taoGiaoVienDuLieuSaiHoacEmailTrung() throws Exception {
        int before = count("SELECT count(*) FROM users");
        Map<String, Integer> cases = Map.of(
                "{\"email\": \"a@test.vn\", \"password\": \"matkhau-dai-1\"}", 400,
                "{\"name\": \"  \", \"email\": \"a@test.vn\", \"password\": \"matkhau-dai-1\"}", 400,
                "{\"name\": \"A\", \"email\": \"khong-phai-email\", \"password\": \"matkhau-dai-1\"}", 400,
                "{\"name\": \"A\", \"password\": \"matkhau-dai-1\"}", 400,
                "{\"name\": \"A\", \"email\": \"a@test.vn\", \"password\": \"ngan\"}", 400,
                "{\"name\": \"A\", \"email\": \"a@test.vn\"}", 400,
                "{\"name\": \"Trùng\", \"email\": \" GV.CU.users@TEST.vn\", \"password\": \"matkhau-dai-1\"}", 409);
        for (Map.Entry<String, Integer> c : cases.entrySet()) {
            postJson("/api/admin/users", c.getKey(), asAdmin()).andExpect(status().is(c.getValue()))
                    .andExpect(jsonPath("$.error").exists());
        }
        assertEquals(before, count("SELECT count(*) FROM users"), "lỗi thì không tạo tài khoản");
    }

    @Test
    void danhSachTaiKhoanLocTheoVaiTroVaKhongLoMatKhau() throws Exception {
        jdbc.update("INSERT INTO users (name, email, role) VALUES ('NC', 'nc.users@test.vn', 'researcher')");
        JsonNode all = json(mockMvc.perform(get("/api/admin/users").with(asAdmin())).andExpect(status().isOk()));
        assertEquals(3, all.size());
        assertNoSecret(all);
        List<String> roles = new ArrayList<>();
        all.forEach(u -> roles.add(u.get("role").asText()));
        assertEquals(List.of("admin", "teacher", "researcher"), roles, "theo id tăng dần");
        assertFalse(all.get(2).get("hasPassword").asBoolean(), "tài khoản cũ chưa có mật khẩu");

        JsonNode teachers = json(mockMvc.perform(get("/api/admin/users?role=teacher").with(asAdmin()))
                .andExpect(status().isOk()));
        assertEquals(1, teachers.size());
        assertEquals(teacher, teachers.get(0).get("id").asInt());
    }

    @Test
    void suaTenVaKhoaThiThuHoiPhienRoiMoLai() throws Exception {
        String refreshToken = teacherRefreshToken();

        JsonNode renamed = json(patchJson("/api/admin/users/" + teacher, "{\"name\": \" Tên mới \"}", asAdmin())
                .andExpect(status().isOk()));
        assertEquals("Tên mới", renamed.get("name").asText());
        assertTrue(renamed.get("enabled").asBoolean());
        assertNoSecret(renamed);

        JsonNode locked = json(patchJson("/api/admin/users/" + teacher, "{\"enabled\": false}", asAdmin())
                .andExpect(status().isOk()));
        assertFalse(locked.get("enabled").asBoolean());
        assertEquals("Tên mới", locked.get("name").asText(), "chỉ đổi trường được gửi");
        assertEquals("admin_disabled", jdbc.queryForObject("SELECT revoke_reason FROM refresh_token WHERE token_hash = ?",
                String.class, AuthTestSupport.sha256Hex(refreshToken)));
        refresh(refreshToken).andExpect(status().isUnauthorized());
        login("gv.cu.users@test.vn", TEACHER_PASSWORD).andExpect(status().isUnauthorized());

        patchJson("/api/admin/users/" + teacher, "{\"enabled\": true}", asAdmin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        login("gv.cu.users@test.vn", TEACHER_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void suaDuLieuSaiHoacKhongTonTai() throws Exception {
        patchJson("/api/admin/users/" + teacher, "{\"name\": \"  \"}", asAdmin()).andExpect(status().isBadRequest());
        patchJson("/api/admin/users/" + teacher, "{}", asAdmin()).andExpect(status().isBadRequest());
        patchJson("/api/admin/users/999999", "{\"enabled\": false}", asAdmin()).andExpect(status().isNotFound());
        postJson("/api/admin/users/999999/reset-password", "{\"newPassword\": \"matkhau-dai-1\"}", asAdmin())
                .andExpect(status().isNotFound());
    }

    @Test
    void khongTuKhoaChinhMinh() throws Exception {
        patchJson("/api/admin/users/" + admin, "{\"enabled\": false}", asAdmin()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Không thể tự khóa tài khoản của mình"));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT enabled FROM users WHERE id = ?", Boolean.class, admin));
        patchJson("/api/admin/users/" + admin, "{\"name\": \"Đổi tên mình được\"}", asAdmin())
                .andExpect(status().isOk());
    }

    @Test
    void khongKhoaAdminDangHoatDongCuoiCung() throws Exception {
        int other = AuthTestSupport.insertUser(jdbc, "Admin 2", "admin2.users@test.vn", "admin", "matkhau-admin-12",
                true);
        // Hai admin đang hoạt động: khóa admin kia được.
        patchJson("/api/admin/users/" + other, "{\"enabled\": false}", asAdmin()).andExpect(status().isOk());
        patchJson("/api/admin/users/" + other, "{\"enabled\": true}", asAdmin()).andExpect(status().isOk());

        // Admin hiện tại đã bị khóa trong DB (access token còn hạn): khóa nốt admin kia sẽ không còn admin nào.
        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", admin);
        patchJson("/api/admin/users/" + other, "{\"enabled\": false}", asAdmin()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Không thể khóa admin đang hoạt động cuối cùng"));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT enabled FROM users WHERE id = ?", Boolean.class, other));
    }

    @Test
    void datLaiMatKhauThuHoiPhienVaDangNhapBangMatKhauMoi() throws Exception {
        String refreshToken = teacherRefreshToken();
        postJson("/api/admin/users/" + teacher + "/reset-password", "{\"newPassword\": \"ngan\"}", asAdmin())
                .andExpect(status().isBadRequest());
        assertEquals(1, count("SELECT count(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL", teacher),
                "đặt lại thất bại thì không thu hồi gì");

        mockMvc.perform(post("/api/admin/users/" + teacher + "/reset-password").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\": \"matkhau-moi-2\"}"))
                .andExpect(status().isNoContent());
        assertEquals("password_changed", jdbc.queryForObject("SELECT revoke_reason FROM refresh_token"
                + " WHERE token_hash = ?", String.class, AuthTestSupport.sha256Hex(refreshToken)));
        refresh(refreshToken).andExpect(status().isUnauthorized());
        login("gv.cu.users@test.vn", TEACHER_PASSWORD).andExpect(status().isUnauthorized());
        login("gv.cu.users@test.vn", "matkhau-moi-2").andExpect(status().isOk());
        assertTrue(count("SELECT count(*) FROM users WHERE id = ? AND password_changed_at IS NOT NULL", teacher) == 1);
    }

    /** Tài khoản cũ (chưa mật khẩu) được admin đặt mật khẩu để đăng nhập; chưa có email thì báo 400 rõ ràng. */
    @Test
    void datMatKhauChoTaiKhoanCu() throws Exception {
        int legacy = jdbc.queryForObject("INSERT INTO users (name, email, role) VALUES ('GV mặc định',"
                + " 'teacher.legacy@test.vn', 'teacher') RETURNING id", Integer.class);
        int noEmail = jdbc.queryForObject("INSERT INTO users (name, role) VALUES ('GV không email', 'teacher')"
                + " RETURNING id", Integer.class);
        postJson("/api/admin/users/" + legacy + "/reset-password", "{\"newPassword\": \"matkhau-moi-3\"}", asAdmin())
                .andExpect(status().isNoContent());
        login("teacher.legacy@test.vn", "matkhau-moi-3").andExpect(status().isOk());
        postJson("/api/admin/users/" + noEmail + "/reset-password", "{\"newPassword\": \"matkhau-moi-3\"}", asAdmin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Tài khoản chưa có email nên không đặt mật khẩu được"));
    }

    // ---------- đổi email (PATCH email) ----------

    private String revokeReason(String refreshToken) {
        return jdbc.queryForObject("SELECT revoke_reason FROM refresh_token WHERE token_hash = ?", String.class,
                AuthTestSupport.sha256Hex(refreshToken));
    }

    @Test
    void doiEmailThuHoiPhienRoiDangNhapBangEmailMoi() throws Exception {
        String refreshToken = teacherRefreshToken();

        JsonNode changed = json(patchJson("/api/admin/users/" + teacher, "{\"email\": \"  GV.Email.Moi@Test.VN \"}",
                asAdmin()).andExpect(status().isOk()));
        assertEquals("gv.email.moi@test.vn", changed.get("email").asText(), "chuẩn hóa chữ thường, bỏ khoảng trắng");
        assertEquals("GV cũ", changed.get("name").asText(), "chỉ đổi trường được gửi");
        assertTrue(changed.get("enabled").asBoolean());
        assertNoSecret(changed);
        assertEquals("gv.email.moi@test.vn", jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class,
                teacher));

        assertEquals("email_changed", revokeReason(refreshToken), "đổi email thì thu hồi phiên của người đó");
        refresh(refreshToken).andExpect(status().isUnauthorized());
        login("gv.cu.users@test.vn", TEACHER_PASSWORD).andExpect(status().isUnauthorized());
        login("gv.email.moi@test.vn", TEACHER_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("gv.email.moi@test.vn"));
    }

    @Test
    void guiLaiEmailHienTaiKhongThuHoiPhien() throws Exception {
        String refreshToken = teacherRefreshToken();
        patchJson("/api/admin/users/" + teacher, "{\"email\": \" GV.CU.users@Test.vn \"}", asAdmin())
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("gv.cu.users@test.vn"));
        refresh(refreshToken).andExpect(status().isOk());
    }

    @Test
    void doiEmailTrungHoacKhongHopLe() throws Exception {
        String refreshToken = teacherRefreshToken();
        patchJson("/api/admin/users/" + teacher, "{\"email\": \" ADMIN.Users@test.vn\"}", asAdmin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email đã được dùng: admin.users@test.vn"));
        patchJson("/api/admin/users/" + teacher, "{\"email\": \"khong-phai-email\"}", asAdmin())
                .andExpect(status().isBadRequest());
        patchJson("/api/admin/users/" + teacher, "{\"email\": \"   \"}", asAdmin())
                .andExpect(status().isBadRequest());
        // Lỗi email thì không đổi các trường khác gửi kèm.
        patchJson("/api/admin/users/" + teacher, "{\"name\": \"Tên khác\", \"email\": \"admin.users@test.vn\"}",
                asAdmin()).andExpect(status().isConflict());

        Map<String, Object> row = jdbc.queryForMap("SELECT name, email FROM users WHERE id = ?", teacher);
        assertEquals("gv.cu.users@test.vn", row.get("email"));
        assertEquals("GV cũ", row.get("name"));
        refresh(refreshToken).andExpect(status().isOk());
    }

    /** Tài khoản cũ không có email: admin thêm email rồi đặt mật khẩu, giáo viên đăng nhập được. */
    @Test
    void taiKhoanCuKhongEmailThemEmailRoiDatMatKhau() throws Exception {
        int noEmail = jdbc.queryForObject("INSERT INTO users (name, role) VALUES ('GV không email', 'teacher')"
                + " RETURNING id", Integer.class);
        postJson("/api/admin/users/" + noEmail + "/reset-password", "{\"newPassword\": \"matkhau-moi-4\"}", asAdmin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Tài khoản chưa có email nên không đặt mật khẩu được"));

        patchJson("/api/admin/users/" + noEmail, "{\"email\": \"GV.Khong.Email@Test.vn\"}", asAdmin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("gv.khong.email@test.vn"))
                .andExpect(jsonPath("$.hasPassword").value(false));
        postJson("/api/admin/users/" + noEmail + "/reset-password", "{\"newPassword\": \"matkhau-moi-4\"}", asAdmin())
                .andExpect(status().isNoContent());
        login("gv.khong.email@test.vn", "matkhau-moi-4").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(noEmail));
    }

    @Test
    void giaoVienKhongGoiDuocApiQuanTri() throws Exception {
        RequestPostProcessor asTeacher = AuthTestSupport.teacher(teacher);
        mockMvc.perform(get("/api/admin/users").with(asTeacher)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        postJson("/api/admin/users", "{\"name\": \"A\", \"email\": \"x@test.vn\", \"password\": \"matkhau-dai-1\"}",
                asTeacher).andExpect(status().isForbidden());
        patchJson("/api/admin/users/" + teacher, "{\"enabled\": false}", asTeacher).andExpect(status().isForbidden());
        assertEquals(0, count("SELECT count(*) FROM users WHERE email = 'x@test.vn'"));
    }
}
