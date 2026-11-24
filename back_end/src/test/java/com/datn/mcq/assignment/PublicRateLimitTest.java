package com.datn.mcq.assignment;

import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Giới hạn tần suất của endpoint công khai (quyết định 16/11, AN-03): theo IP CHỈ khi tạo lượt mới; mở lại và nộp đếm theo
 * lượt làm, hai bộ đếm riêng; trang chào không giới hạn. Bộ đếm nằm trong bộ nhớ nên mỗi test dùng IP riêng; hạn mức đặt
 * thấp để test nhanh.
 */
@SpringBootTest(properties = {
        "app.public-limit.start-per-ip-per-minute=3",
        "app.public-limit.resume-per-attempt-per-minute=4",
        "app.public-limit.submit-per-attempt-per-minute=3",
        "app.public-limit.image-per-attempt-per-minute=3",
        "app.security.cors.allowed-origins=http://localhost:5173"
})
@Import(TestSafetyConfiguration.class)
class PublicRateLimitTest extends AssignmentTestBase {

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String ip) {
        return request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    private MockHttpServletRequestBuilder startRequest(String code, int seat) {
        return post("/api/public/assignments/" + code + "/attempts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"classLabel\": \"8A1\", \"seatNo\": " + seat + "}");
    }

    private String startFrom(String code, int seat, String ip) throws Exception {
        return json(mockMvc.perform(from(startRequest(code, seat), ip)).andExpect(status().isCreated()))
                .get("attemptToken").asText();
    }

    @Test
    void startIsLimitedPerIpAndBlockedCallsCreateNothing() throws Exception {
        String code = assign(true).get("publicCode").asText();
        for (int seat = 1; seat <= 3; seat++) {
            mockMvc.perform(from(startRequest(code, seat), "10.20.0.1")).andExpect(status().isCreated());
        }
        MvcResult blocked = mockMvc.perform(from(startRequest(code, 4), "10.20.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andReturn();
        long retryAfter = Long.parseLong(blocked.getResponse().getHeader("Retry-After"));
        assertTrue(retryAfter >= 1 && retryAfter <= 60, "Retry-After = " + retryAfter);
        assertEquals(3, count("SELECT count(*) FROM assignment_attempt"));

        // IP khác (lớp khác, trường khác) không bị ảnh hưởng.
        mockMvc.perform(from(startRequest(code, 4), "10.20.0.2")).andExpect(status().isCreated());
    }

    /**
     * Frontend chạy khác origin (Vite dev, hoặc tên miền tách riêng) chỉ đọc được Retry-After của phản hồi 429 khi header
     * này nằm trong Access-Control-Expose-Headers; thiếu thì trang học sinh không biết phải chờ bao lâu trước khi thử lại.
     */
    @Test
    void retryAfterIsExposedToCrossOriginCallers() throws Exception {
        String code = assign(true).get("publicCode").asText();
        String origin = "http://localhost:5173";
        for (int seat = 1; seat <= 3; seat++) {
            mockMvc.perform(from(startRequest(code, seat), "10.20.4.1").header("Origin", origin))
                    .andExpect(status().isCreated());
        }
        MvcResult blocked = mockMvc.perform(from(startRequest(code, 4), "10.20.4.1").header("Origin", origin))
                .andExpect(status().isTooManyRequests())
                .andReturn();
        assertEquals(origin, blocked.getResponse().getHeader("Access-Control-Allow-Origin"));
        assertTrue(Long.parseLong(blocked.getResponse().getHeader("Retry-After")) >= 1);
        String exposed = String.join(",", blocked.getResponse().getHeaders("Access-Control-Expose-Headers"));
        assertTrue(java.util.Arrays.stream(exposed.split(",")).map(String::trim)
                        .anyMatch("Retry-After"::equalsIgnoreCase),
                "Access-Control-Expose-Headers = " + exposed);
    }

    @Test
    void infoIsNotLimited() throws Exception {
        String code = assign(true).get("publicCode").asText();
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(from(get("/api/public/assignments/" + code), "10.20.1.1")).andExpect(status().isOk());
        }
    }

    /**
     * Một máy tải lại liên tục chỉ bị chặn mở lại của CHÍNH lượt đó: bạn cùng IP vẫn mở lại và nộp được, và chính lượt đó
     * vẫn nộp được (bộ đếm nộp tách khỏi bộ đếm mở lại).
     */
    @Test
    void resumeAndSubmitAreCountedPerAttemptWithSeparateCounters() throws Exception {
        String code = assign(true).get("publicCode").asText();
        String noisy = startFrom(code, 1, "10.20.2.1");
        String classmate = startFrom(code, 2, "10.20.2.1");

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(from(get("/api/public/attempts/current").header(TOKEN_HEADER, noisy), "10.20.2.1"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(from(get("/api/public/attempts/current").header(TOKEN_HEADER, noisy), "10.20.2.1"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));

        mockMvc.perform(from(get("/api/public/attempts/current").header(TOKEN_HEADER, classmate), "10.20.2.1"))
                .andExpect(status().isOk());
        submitFrom(classmate, "10.20.2.1").andExpect(status().isOk());
        submitFrom(noisy, "10.20.2.1").andExpect(status().isOk());

        // Bộ đếm nộp của lượt: lần 2, 3 là 409 (đã nộp), lần 4 bị chặn.
        submitFrom(noisy, "10.20.2.1").andExpect(status().isConflict());
        submitFrom(noisy, "10.20.2.1").andExpect(status().isConflict());
        submitFrom(noisy, "10.20.2.1").andExpect(status().isTooManyRequests());
        assertEquals(2, count("SELECT count(*) FROM assignment_attempt WHERE submitted_at IS NOT NULL"));
    }

    private org.springframework.test.web.servlet.ResultActions submitFrom(String token, String ip) throws Exception {
        return mockMvc.perform(from(post("/api/public/attempts/current/submit").header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"answers\": " + sampleAnswers() + "}"), ip));
    }

    /**
     * S1 (cau-co-hinh.md mục 4.1): tải ảnh có bộ đếm thứ tư theo lượt làm; vượt thì 429 kèm Retry-After. URL ký sai bị 404
     * trước khi đếm nên không làm lượt bị chặn; lượt khác cùng IP vẫn tải được.
     */
    @Test
    void imageLoadsAreLimitedPerAttempt() throws Exception {
        String code = assign(true).get("publicCode").asText();
        long stem = attachImage(mcqItem1, "stem", 1);
        String url = json(mockMvc.perform(from(startRequest(code, 1), "10.20.5.1")).andExpect(status().isCreated()))
                .get("parts").get(0).get("items").get(0).get("images").get(0).get("url").asText();
        String classmateUrl = json(mockMvc.perform(from(startRequest(code, 2), "10.20.5.1"))
                .andExpect(status().isCreated()))
                .get("parts").get(0).get("items").get(0).get("images").get(0).get("url").asText();
        assertTrue(url.startsWith("/api/public/question-images/" + stem + "?"), url);

        String badSig = url.substring(0, url.length() - 1) + (url.endsWith("0") ? "1" : "0");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(from(get(badSig), "10.20.5.1")).andExpect(status().isNotFound());
        }
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(from(get(url), "10.20.5.1")).andExpect(status().isOk());
        }
        MvcResult blocked = mockMvc.perform(from(get(url), "10.20.5.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andReturn();
        assertTrue(Long.parseLong(blocked.getResponse().getHeader("Retry-After")) >= 1);

        mockMvc.perform(from(get(classmateUrl), "10.20.5.1")).andExpect(status().isOk());
    }

    /** Token sai trả 401 và không tính vào bộ đếm nào (token 32 byte, không dò được). */
    @Test
    void wrongTokensAreRejectedWithoutTouchingAnyCounter() throws Exception {
        assign(true);
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(from(get("/api/public/attempts/current").header(TOKEN_HEADER, "sai-" + i), "10.20.3.1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ATTEMPT_TOKEN_INVALID"));
        }
    }
}
