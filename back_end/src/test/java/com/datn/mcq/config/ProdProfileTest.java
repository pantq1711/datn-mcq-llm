package com.datn.mcq.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-5: profile {@code prod} (application-prod.yaml) tắt Swagger/OpenAPI (SecurityConfig đang permitAll các đường này)
 * và đặt giới hạn upload, tắt máy chủ nhẹ nhàng. Cùng DB test; không đổi hành vi của profile mặc định
 * (OpenApiContractTest vẫn kiểm /v3/api-docs trả 200 khi không có profile prod).
 */
@SpringBootTest
@ActiveProfiles("prod")
@Import(TestSafetyConfiguration.class)
class ProdProfileTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Environment environment;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void openApiVaSwaggerUiBiTatOProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void gioiHanUploadVaTatMayChuNheNhang() {
        assertEquals("10MB", environment.getProperty("spring.servlet.multipart.max-file-size"));
        assertEquals("12MB", environment.getProperty("spring.servlet.multipart.max-request-size"));
        assertEquals("graceful", environment.getProperty("server.shutdown"));
    }
}
