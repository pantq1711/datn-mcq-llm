package com.datn.mcq.auth;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
 * D3 (soát backend trước deploy): giới hạn đăng nhập theo IP, độc lập với khóa email + IP và chạy TRƯỚC bcrypt, để một IP
 * không dùng 20+ email khác nhau làm CPU kiệt sức vì bcrypt. Mặc định 20 lần mỗi phút mỗi IP; IPv6 gom theo tiền tố /64.
 * Bộ đếm nằm trong bộ nhớ nên mỗi test dùng IP riêng.
 */
@SpringBootTest(properties = {
        "app.security.login-ip-limit.max-attempts=20",
        "app.security.login-ip-limit.window=1m"
})
@Import(TestSafetyConfiguration.class)
class LoginIpRateLimitTest {

    private static final String PASSWORD = "matkhau-dung-1";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "LoginIpRateLimitTest.setUp");
        Mockito.clearInvocations(passwordEncoder);
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
    }

    private static String ghost() {
        return "ghost." + UUID.randomUUID().toString().substring(0, 8) + "@test.vn";
    }

    private int bcryptCalls() {
        return Mockito.mockingDetails(passwordEncoder).getInvocations().size();
    }

    @Test
    void lanThu21TuCungIpVoiEmailKhacNhauBiChanVaKhongGoiBcrypt() throws Exception {
        for (int i = 0; i < 20; i++) {
            login(ghost(), "sai", "10.20.0.1").andExpect(status().isUnauthorized());
        }
        int callsAfter20 = bcryptCalls();
        assertTrue(callsAfter20 > 0, "20 lần đầu có so mật khẩu (bộ đếm spy hoạt động)");

        MockHttpServletResponse blocked = login(ghost(), "sai", "10.20.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.error").exists())
                .andReturn().getResponse();
        int retryAfter = Integer.parseInt(blocked.getHeader(HttpHeaders.RETRY_AFTER));
        assertTrue(retryAfter >= 1 && retryAfter <= 60, "Retry-After trong cửa sổ 1 phút: " + retryAfter);
        assertEquals(callsAfter20, bcryptCalls(), "lần bị chặn không được gọi bcrypt");

        login(ghost(), "sai", "10.20.0.2").andExpect(status().isUnauthorized()); // IP khác không bị ảnh hưởng
    }

    @Test
    void dangNhapDungCungTinhVaoHanMucIp() throws Exception {
        String email = "gv." + UUID.randomUUID().toString().substring(0, 8) + "@test.vn";
        AuthTestSupport.insertUser(jdbc, "GV", email, "teacher", PASSWORD, true);
        for (int i = 0; i < 20; i++) {
            login(email, PASSWORD, "10.20.0.3").andExpect(status().isOk());
        }
        login(email, PASSWORD, "10.20.0.3").andExpect(status().isTooManyRequests());
    }

    @Test
    void ipv6GomTheoTienTo64() throws Exception {
        for (int i = 0; i < 20; i++) {
            login(ghost(), "sai", "2001:db8:aaaa:1:0:0:0:" + (i + 1)).andExpect(status().isUnauthorized());
        }
        // cùng /64 (4 nhóm đầu) nhưng khác phần máy: vẫn bị chặn
        login(ghost(), "sai", "2001:db8:aaaa:1:ffff:eeee:dddd:cccc").andExpect(status().isTooManyRequests());
        // /64 khác thì không
        login(ghost(), "sai", "2001:db8:aaaa:2:0:0:0:1").andExpect(status().isUnauthorized());
    }

    @Test
    void khoaEmailVaIpHienCoVanDocLap() throws Exception {
        String email = "gv." + UUID.randomUUID().toString().substring(0, 8) + "@test.vn";
        AuthTestSupport.insertUser(jdbc, "GV", email, "teacher", PASSWORD, true);
        for (int i = 0; i < 5; i++) {
            login(email, "sai-" + i, "10.20.0.4").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD, "10.20.0.4").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        // IP khác (chưa chạm hạn mức IP) vẫn đăng nhập được
        login(email, PASSWORD, "10.20.0.5").andExpect(status().isOk());
    }
}
