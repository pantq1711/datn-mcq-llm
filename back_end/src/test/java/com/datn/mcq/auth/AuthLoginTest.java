package com.datn.mcq.auth;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-1: đăng nhập POST /api/auth/login qua filter chain thật. Sai email hay sai mật khẩu đều trả cùng một 401 để không
 * dò được email nào có tài khoản; refresh token chỉ nằm trong cookie HttpOnly, DB chỉ lưu bản băm.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class AuthLoginTest {

    private static final String BAD_CREDENTIALS = "Email hoặc mật khẩu không đúng";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int teacher;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "AuthLoginTest.setUp");
        teacher = AuthTestSupport.insertUser(jdbc, "Nguyễn Văn A", "gv.toan@test.vn", "teacher", "matkhau-dung-1",
                true);
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = "{\"email\": " + quote(email) + ", \"password\": " + quote(password) + "}";
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void dangNhapDungTraAccessTokenVaCookieRefresh() throws Exception {
        MockHttpServletResponse response = login("gv.toan@test.vn", "matkhau-dung-1")
                .andExpect(status().isOk()).andReturn().getResponse();
        JsonNode body = json(response);
        assertEquals("Bearer", body.get("tokenType").asText());
        assertEquals(900, body.get("expiresIn").asInt(), "access token mặc định 15 phút");
        assertEquals(teacher, body.get("user").get("id").asInt());
        assertEquals("Nguyễn Văn A", body.get("user").get("name").asText());
        assertEquals("gv.toan@test.vn", body.get("user").get("email").asText());
        assertEquals("teacher", body.get("user").get("role").asText());
        assertFalse(body.has("refreshToken"), "refresh token không nằm trong JSON (H3: cookie HttpOnly)");

        SignedJWT jwt = SignedJWT.parse(body.get("accessToken").asText());
        assertTrue(jwt.verify(new MACVerifier(AuthTestSupport.testKey())), "ký HS256 bằng khóa cấu hình");
        assertEquals("HS256", jwt.getHeader().getAlgorithm().getName());
        assertEquals(String.valueOf(teacher), jwt.getJWTClaimsSet().getSubject());
        assertEquals("TEACHER", jwt.getJWTClaimsSet().getStringClaim("role"));
        assertEquals(AuthTestSupport.ISSUER, jwt.getJWTClaimsSet().getIssuer());
        assertNotNull(jwt.getJWTClaimsSet().getJWTID());
        long ttl = jwt.getJWTClaimsSet().getExpirationTime().getTime() - jwt.getJWTClaimsSet().getIssueTime().getTime();
        assertEquals(900_000L, ttl);
        assertNull(jwt.getJWTClaimsSet().getClaim("email"), "không đưa email vào token");

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith(AuthTestSupport.REFRESH_COOKIE + "="), setCookie);
        assertTrue(setCookie.contains("Path=/api/auth"), setCookie);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), setCookie);
        assertTrue(setCookie.contains("Secure"), "app.security.cookie-secure mặc định true: " + setCookie);
        assertTrue(setCookie.contains("Max-Age=604800"), "refresh 7 ngày: " + setCookie);

        String raw = response.getCookie(AuthTestSupport.REFRESH_COOKIE).getValue();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM refresh_token WHERE user_id = ?", teacher);
        assertEquals(AuthTestSupport.sha256Hex(raw), row.get("token_hash"), "DB chỉ lưu SHA-256 của token");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?", Integer.class,
                raw), "không lưu token gốc");
        assertNull(row.get("revoked_at"));
        assertNotNull(row.get("family_id"));
        assertNotNull(jdbc.queryForObject("SELECT last_login_at FROM users WHERE id = ?", Date.class, teacher));
    }

    @Test
    void emailKhongPhanBietHoaThuongVaKhoangTrang() throws Exception {
        login("  GV.Toan@Test.VN ", "matkhau-dung-1").andExpect(status().isOk());
    }

    @Test
    void saiMatKhauVaEmailKhongTonTaiTraCungMot401() throws Exception {
        login("gv.toan@test.vn", "sai-mat-khau").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(BAD_CREDENTIALS))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("khong.co@test.vn", "matkhau-dung-1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(BAD_CREDENTIALS))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_token", Integer.class));
    }

    @Test
    void taiKhoanBiKhoaHoacChuaCoMatKhauKhongDangNhapDuoc() throws Exception {
        AuthTestSupport.insertUser(jdbc, "GV khóa", "gv.khoa@test.vn", "teacher", "matkhau-dung-1", false);
        jdbc.update("INSERT INTO users (name, email, role) VALUES ('GV cũ', 'gv.cu@test.vn', 'teacher')");
        AuthTestSupport.insertUser(jdbc, "Nghiên cứu", "nc@test.vn", "researcher", "matkhau-dung-1", true);

        for (String email : new String[]{"gv.khoa@test.vn", "gv.cu@test.vn", "nc@test.vn"}) {
            login(email, "matkhau-dung-1").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value(BAD_CREDENTIALS))
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_token", Integer.class));
    }

    @Test
    void adminDangNhapCoRoleAdmin() throws Exception {
        AuthTestSupport.insertUser(jdbc, "Quản trị", "admin@test.vn", "admin", "matkhau-admin-12", true);
        MockHttpServletResponse response = login("admin@test.vn", "matkhau-admin-12")
                .andExpect(status().isOk()).andReturn().getResponse();
        JsonNode body = json(response);
        assertEquals("admin", body.get("user").get("role").asText());
        assertEquals("ADMIN", SignedJWT.parse(body.get("accessToken").asText()).getJWTClaimsSet()
                .getStringClaim("role"));
    }

    @Test
    void thieuEmailHoacMatKhauTra400() throws Exception {
        login(null, "x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        login("gv.toan@test.vn", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
    }
}
