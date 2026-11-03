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
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    /** F-09: chờ worker xong job đang chạy khi docker stop (grace 40 giây, pha tắt Spring 30 giây). */
    @Test
    void proChoWorkerDungDuLauDeJobDangChayKipXong() {
        assertEquals(28000L, environment.getProperty("app.worker.shutdown-await-ms", Long.class));
    }

    /** F-11: giới hạn trích tài liệu ở prod đổi được bằng biến môi trường (compose không phải sửa file), mặc định giữ nguyên. */
    @Test
    void gioiHanTrichTaiLieuOProdDungBienMoiTruongKemMacDinh() throws Exception {
        String yaml;
        try (var in = new org.springframework.core.io.ClassPathResource("application-prod.yaml").getInputStream()) {
            yaml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(yaml.contains("max-concurrent-extractions: ${MATERIAL_MAX_CONCURRENT:1}"), yaml);
        assertTrue(yaml.contains("pandoc-heap-mb: ${MATERIAL_PANDOC_HEAP_MB:256}"), yaml);
        assertTrue(yaml.contains("queue-wait: ${MATERIAL_QUEUE_WAIT:30s}"), yaml);
        assertEquals(1, environment.getProperty("app.materials.max-concurrent-extractions", Integer.class));
        assertEquals(256, environment.getProperty("app.materials.pandoc-heap-mb", Integer.class));
        assertEquals("30s", environment.getProperty("app.materials.queue-wait"));
    }

    @Test
    void gioiHanUploadVaTatMayChuNheNhang() {
        assertEquals("10MB", environment.getProperty("spring.servlet.multipart.max-file-size"));
        assertEquals("12MB", environment.getProperty("spring.servlet.multipart.max-request-size"));
        assertEquals("graceful", environment.getProperty("server.shutdown"));
    }
}
