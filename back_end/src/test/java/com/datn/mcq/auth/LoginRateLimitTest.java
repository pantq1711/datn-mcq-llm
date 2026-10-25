package com.datn.mcq.auth;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-1 (nếu kịp): giới hạn đăng nhập sai theo cặp email + IP. Sai 5 lần trong 15 phút thì khóa 15 phút: trả 429
 * TOO_MANY_ATTEMPTS kèm Retry-After, kể cả khi nhập đúng mật khẩu. Đăng nhập đúng thì xóa bộ đếm. Mỗi test dùng email
 * riêng vì bộ đếm nằm trong bộ nhớ của ứng dụng.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class LoginRateLimitTest {

    private static final String PASSWORD = "matkhau-dung-1";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private String email;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "LoginRateLimitTest.setUp");
        email = "rl." + UUID.randomUUID().toString().substring(0, 8) + "@test.vn";
        AuthTestSupport.insertUser(jdbc, "GV", email, "teacher", PASSWORD, true);
    }

    private ResultActions login(String loginEmail, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + loginEmail + "\", \"password\": \"" + password + "\"}"));
    }

    @Test
    void saiNamLanThiKhoaKeCaMatKhauDung() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(email, "sai-" + i, "10.0.0.1").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        MockHttpServletResponse locked = login(email, "sai-tiep", "10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.error").exists())
                .andReturn().getResponse();
        int retryAfter = Integer.parseInt(locked.getHeader(HttpHeaders.RETRY_AFTER));
        assertTrue(retryAfter > 0 && retryAfter <= 900, "Retry-After tính bằng giây, tối đa 15 phút: " + retryAfter);

        login(email, PASSWORD, "10.0.0.1").andExpect(status().isTooManyRequests());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_token", Integer.class),
                "đang khóa thì không cấp phiên");
    }

    @Test
    void khoaTheoCapEmailVaIp() throws Exception {
        for (int i = 0; i < 6; i++) {
            login(email, "sai-" + i, "10.0.0.2");
        }
        login(email, PASSWORD, "10.0.0.2").andExpect(status().isTooManyRequests());
        login(email, PASSWORD, "10.0.0.3").andExpect(status().isOk());
    }

    @Test
    void dangNhapDungThiXoaBoDem() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(email, "sai-" + i, "10.0.0.4").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD, "10.0.0.4").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login(email, "sai-lai-" + i, "10.0.0.4").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD, "10.0.0.4").andExpect(status().isOk());
    }

    @Test
    void emailKhongTonTaiCungBiGioiHanNhuNhau() throws Exception {
        String ghost = "khong.co." + UUID.randomUUID().toString().substring(0, 8) + "@test.vn";
        for (int i = 0; i < 5; i++) {
            login(ghost, "sai", "10.0.0.5").andExpect(status().isUnauthorized());
        }
        login(ghost, "sai", "10.0.0.5").andExpect(status().isTooManyRequests());
        login(" " + ghost.toUpperCase() + " ", "sai", "10.0.0.5").andExpect(status().isTooManyRequests());
    }
}
