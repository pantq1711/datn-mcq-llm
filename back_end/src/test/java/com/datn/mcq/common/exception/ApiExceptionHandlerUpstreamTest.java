package com.datn.mcq.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V3: lỗi 429 và 503 từ dịch vụ ngoài đi ra HTTP với đúng mã, không còn 500.
 */
class ApiExceptionHandlerUpstreamTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/test/rate-limited")
        public String rateLimited() {
            throw new UpstreamServiceException(429, "Gemini đang giới hạn tần suất gọi (HTTP 429).");
        }

        @GetMapping("/test/unavailable")
        public String unavailable() {
            throw new UpstreamServiceException(503, "Gemini tạm thời không phục vụ (HTTP 503).");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void upstream429IsReturnedAs429WithErrorMessage() throws Exception {
        mockMvc.perform(get("/test/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Gemini đang giới hạn tần suất gọi (HTTP 429)."));
    }

    @Test
    void upstream503IsReturnedAs503WithErrorMessage() throws Exception {
        mockMvc.perform(get("/test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Gemini tạm thời không phục vụ (HTTP 503)."));
    }
}
