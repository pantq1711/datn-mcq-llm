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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-1: refresh token xoay vòng. Mỗi lần refresh cấp token mới và thu hồi token cũ ('rotated'); gửi lại token đã thu
 * hồi bị coi là dùng lại và cả chuỗi (family) bị thu hồi ('reuse_detected'). Đăng xuất và đổi mật khẩu thu hồi token.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class RefreshRotationTest {

    private static final String PASSWORD = "matkhau-dung-1";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int teacher;

    /** Kết quả đăng nhập hoặc refresh: access token và giá trị cookie refresh. */
    record Session(String accessToken, String refreshToken) {}

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "RefreshRotationTest.setUp");
        teacher = AuthTestSupport.insertUser(jdbc, "GV", "gv.refresh@test.vn", "teacher", PASSWORD, true);
    }

    private Session login() throws Exception {
        MockHttpServletResponse r = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"gv.refresh@test.vn\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        return session(r);
    }

    private Session session(MockHttpServletResponse r) throws Exception {
        JsonNode body = objectMapper.readTree(r.getContentAsString(StandardCharsets.UTF_8));
        Cookie cookie = r.getCookie(AuthTestSupport.REFRESH_COOKIE);
        assertNotNull(cookie, "phải có cookie refresh");
        return new Session(body.get("accessToken").asText(), cookie.getValue());
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie(AuthTestSupport.REFRESH_COOKIE, refreshToken)));
    }

    private Map<String, Object> row(String refreshToken) {
        return jdbc.queryForMap("SELECT * FROM refresh_token WHERE token_hash = ?",
                AuthTestSupport.sha256Hex(refreshToken));
    }

    private int activeTokens() {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, teacher);
    }

    private static void assertClearsCookie(MockHttpServletResponse r) {
        String setCookie = r.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "phải xóa cookie refresh");
        assertTrue(setCookie.startsWith(AuthTestSupport.REFRESH_COOKIE + "=;") && setCookie.contains("Max-Age=0")
                && setCookie.contains("Path=/api/auth"), setCookie);
    }

    @Test
    void refreshCapCapMoiVaThuHoiTokenCu() throws Exception {
        Session first = login();
        MockHttpServletResponse r = refresh(first.refreshToken()).andExpect(status().isOk()).andReturn().getResponse();
        Session second = session(r);
        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertTrue(r.getHeader(HttpHeaders.SET_COOKIE).contains("HttpOnly"));
        JsonNode body = objectMapper.readTree(r.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(teacher, body.get("user").get("id").asInt());
        assertEquals(900, body.get("expiresIn").asInt());

        Map<String, Object> old = row(first.refreshToken());
        Map<String, Object> fresh = row(second.refreshToken());
        assertEquals("rotated", old.get("revoke_reason"));
        assertNotNull(old.get("revoked_at"));
        assertEquals(fresh.get("id"), old.get("replaced_by_id"));
        assertEquals(old.get("family_id"), fresh.get("family_id"), "cùng một chuỗi đăng nhập");
        assertEquals(old.get("family_expires_at"), fresh.get("family_expires_at"), "không kéo dài hạn tuyệt đối");
        assertNull(fresh.get("revoked_at"));
        assertEquals(1, activeTokens());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + second.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void dungLaiTokenDaXoayThuHoiCaChuoi() throws Exception {
        Session first = login();
        Session second = session(refresh(first.refreshToken()).andExpect(status().isOk()).andReturn().getResponse());
        Session otherDevice = login();

        MockHttpServletResponse reuse = refresh(first.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALID")).andReturn().getResponse();
        assertClearsCookie(reuse);

        assertEquals("reuse_detected", row(second.refreshToken()).get("revoke_reason"),
                "token mới nhất của chuỗi cũng bị thu hồi");
        refresh(second.refreshToken()).andExpect(status().isUnauthorized());
        assertNull(row(otherDevice.refreshToken()).get("revoked_at"), "phiên ở thiết bị khác không bị ảnh hưởng");
        refresh(otherDevice.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void tokenKhongTonTaiHetHanHoacThieuCookieTra401() throws Exception {
        refresh("token-bia-dat").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALID"));

        Session s = login();
        jdbc.update("UPDATE refresh_token SET issued_at = now() - interval '8 days', expires_at = now() - interval"
                + " '1 day' WHERE token_hash = ?", AuthTestSupport.sha256Hex(s.refreshToken()));
        refresh(s.refreshToken()).andExpect(status().isUnauthorized());

        Session capped = login();
        jdbc.update("UPDATE refresh_token SET family_expires_at = now() - interval '1 minute' WHERE token_hash = ?",
                AuthTestSupport.sha256Hex(capped.refreshToken()));
        refresh(capped.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void taiKhoanBiKhoaKhongRefreshDuoc() throws Exception {
        Session s = login();
        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", teacher);
        refresh(s.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void dangXuatThuHoiTokenVaGoiLaiVanTra204() throws Exception {
        Session s = login();
        MockHttpServletResponse r = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(AuthTestSupport.REFRESH_COOKIE, s.refreshToken())))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertClearsCookie(r);
        assertEquals("logout", row(s.refreshToken()).get("revoke_reason"));
        refresh(s.refreshToken()).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(AuthTestSupport.REFRESH_COOKIE, s.refreshToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
        assertEquals("logout", row(s.refreshToken()).get("revoke_reason"), "gọi lại không đổi lý do thu hồi");
    }

    @Test
    void doiMatKhauThuHoiMoiPhien() throws Exception {
        Session a = login();
        Session b = login();
        String auth = "Bearer " + a.accessToken();

        mockMvc.perform(post("/api/auth/password").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"sai\", \"newPassword\": \"matkhau-moi-2\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Mật khẩu hiện tại không đúng"));
        mockMvc.perform(post("/api/auth/password").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"ngan\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(2, activeTokens(), "đổi mật khẩu thất bại thì không thu hồi gì");

        MockHttpServletResponse r = mockMvc.perform(post("/api/auth/password").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"matkhau-moi-2\"}"))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertClearsCookie(r);
        assertEquals(0, activeTokens());
        assertEquals("password_changed", row(b.refreshToken()).get("revoke_reason"));
        assertNotNull(jdbc.queryForObject("SELECT password_changed_at FROM users WHERE id = ?", Object.class, teacher));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"gv.refresh@test.vn\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"gv.refresh@test.vn\", \"password\": \"matkhau-moi-2\"}"))
                .andExpect(status().isOk());
    }

    /** Hai lần refresh đồng thời cùng một token: khóa hàng (FOR UPDATE) nên chỉ một lần thành công. */
    @Test
    void haiRefreshDongThoiChiMotThanhCong() throws Exception {
        Session s = login();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return refresh(s.refreshToken()).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            assertEquals(1, statuses.stream().filter(code -> code == 200).count(), statuses.toString());
            assertEquals(1, statuses.stream().filter(code -> code == 401).count(), statuses.toString());
        } finally {
            pool.shutdownNow();
        }
        assertTrue(activeTokens() <= 1, "không bao giờ có hai token còn hiệu lực cùng chuỗi");
    }
}
