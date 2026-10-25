package com.datn.mcq.auth;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-1: access token (JWT HS256) qua filter chain thật. Lỗi 401/403 trả JSON cùng format {"error": ...} của dự án,
 * thêm "code" để frontend phân biệt token hết hạn với token sai (H9).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class AccessTokenTest {

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
        TestDataCleaner.cleanAll(dataSource, jdbc, "AccessTokenTest.setUp");
        teacher = AuthTestSupport.insertUser(jdbc, "GV", "gv.token@test.vn", "teacher", "matkhau-dung-1", true);
    }

    private String loginToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"gv.token@test.vn\", \"password\": \"matkhau-dung-1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void tokenHopLeGoiDuocApiMe() throws Exception {
        me(loginToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(teacher))
                .andExpect(jsonPath("$.email").value("gv.token@test.vn"))
                .andExpect(jsonPath("$.role").value("teacher"));
    }

    @Test
    void khongCoTokenTra401JsonVaWwwAuthenticate() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/exams")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tokenHetHanTra401TokenExpired() throws Exception {
        Instant now = Instant.now();
        String expired = AuthTestSupport.signedToken(AuthTestSupport.testKey(), String.valueOf(teacher), "TEACHER",
                AuthTestSupport.ISSUER, now.minusSeconds(3600), now.minusSeconds(600));
        me(expired).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void tokenSaiKhoaSaiIssuerHoacKhongKyTra401TokenInvalid() throws Exception {
        Instant now = Instant.now();
        byte[] otherKey = "mot-khoa-khac-hoan-toan-dai-hon-32-byte-nhe".getBytes(StandardCharsets.UTF_8);
        String wrongKey = AuthTestSupport.signedToken(otherKey, String.valueOf(teacher), "TEACHER",
                AuthTestSupport.ISSUER, now, now.plusSeconds(600));
        String wrongIssuer = AuthTestSupport.signedToken(AuthTestSupport.testKey(), String.valueOf(teacher), "TEACHER",
                "ke-gia-mao", now, now.plusSeconds(600));
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String unsigned = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(("{\"sub\":\"" + teacher + "\",\"role\":\"ADMIN\",\"iss\":\"datn-mcq\",\"exp\":"
                + now.plusSeconds(600).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8)) + ".";
        for (String token : new String[]{wrongKey, wrongIssuer, unsigned, "khong-phai-jwt"}) {
            me(token).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        }
    }

    @Test
    void giaoVienGoiApiAdminTra403Json() throws Exception {
        String token = loginToken();
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Bạn không có quyền thực hiện thao tác này"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
