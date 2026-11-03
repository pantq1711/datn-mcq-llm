package com.datn.mcq.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-27 (soát ngoài): log của {@link ApiExceptionHandler}. 4xx ghi WARN (mã trạng thái, mã lỗi, đường dẫn, userId nếu có;
 * KHÔNG ghi thân request, mật khẩu, token hay nội dung thông báo lỗi); 5xx ghi ERROR kèm stacktrace; ngoại lệ không lường
 * trước trả 500 {@code INTERNAL_ERROR} chung (không lộ chi tiết) nhưng lỗi khung (JSON hỏng, sai phương thức...) vẫn giữ mã 4xx.
 */
class ApiExceptionHandlerLoggingTest {

    @RestController
    static class Controller {
        @GetMapping("/t/bad")
        public String bad() {
            throw new IllegalArgumentException("bi-mat-trong-thong-bao");
        }

        @GetMapping("/t/coded")
        public String coded() {
            throw ApiErrorException.conflict("SLOT_KEPT", "Câu đã được Giữ.");
        }

        @GetMapping("/t/boom")
        public String boom() {
            throw new IllegalStateException("khong-phai-409", new RuntimeException("nguyen-nhan"));
        }

        @GetMapping("/t/npe")
        public String npe() {
            String s = null;
            return s.trim();
        }

        @PostMapping("/t/body")
        public String body(@RequestBody Map<String, String> body) {
            throw new IllegalArgumentException("sai");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new Controller())
            .setControllerAdvice(new ApiExceptionHandler()).build();

    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logs = new ListAppender<>();
        logs.start();
        logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        logger.addAppender(logs);
        Jwt jwt = Jwt.withTokenValue("token-bi-mat-xyz").header("alg", "none").subject("77").claim("role", "teacher").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        SecurityContextHolder.clearContext();
    }

    private String all() {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent e : logs.list) {
            sb.append(e.getLevel()).append(' ').append(e.getFormattedMessage()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void loi4xxGhiWarnCoMaDuongDanVaUserIdKhongLoThongBaoHayToken() throws Exception {
        mockMvc.perform(get("/t/bad")).andExpect(status().isBadRequest());

        assertEquals(1, logs.list.size(), all());
        ILoggingEvent event = logs.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        String line = event.getFormattedMessage();
        assertTrue(line.contains("400") && line.contains("/t/bad") && line.contains("77"), line);
        assertFalse(all().contains("bi-mat-trong-thong-bao"), "không ghi nội dung thông báo lỗi: " + all());
        assertFalse(all().contains("token-bi-mat-xyz"), "không ghi token");
    }

    @Test
    void loiCoMaGhiMaLoiVaKhongGhiThanRequest() throws Exception {
        mockMvc.perform(get("/t/coded")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_KEPT"));
        assertTrue(all().contains("SLOT_KEPT") && all().contains("409") && all().contains("/t/coded"), all());

        logs.list.clear();
        mockMvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"hunter2-mat-khau\"}"))
                .andExpect(status().isBadRequest());
        assertFalse(all().contains("hunter2-mat-khau"), "không ghi thân request/mật khẩu: " + all());
        assertTrue(all().contains("/t/body"), all());
    }

    @Test
    void ngoaiLeKhongLuongTruocTra500InternalErrorChungVaGhiErrorKemStacktrace() throws Exception {
        mockMvc.perform(get("/t/npe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error").isString());

        List<ILoggingEvent> errors = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
        assertEquals(1, errors.size(), all());
        assertNotNull(errors.get(0).getThrowableProxy(), "ERROR phải kèm stacktrace");
        assertTrue(errors.get(0).getFormattedMessage().contains("/t/npe"), errors.get(0).getFormattedMessage());
    }

    @Test
    void thongBaoCua500KhongLoChiTietNoiBo() throws Exception {
        String body = mockMvc.perform(get("/t/npe")).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("NullPointer") && body.contains("trim"), body);
        assertFalse(body.contains("String.trim"), body);
    }

    @Test
    void loiKhungVanGiuMa4xxKhongThanh500() throws Exception {
        mockMvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{khong-phai-json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/t/bad")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/t/khong-co-duong-nay")).andExpect(status().isNotFound());
        assertTrue(logs.list.stream().noneMatch(e -> e.getLevel() == Level.ERROR), "lỗi khung là 4xx, không ghi ERROR: " + all());
    }
}
