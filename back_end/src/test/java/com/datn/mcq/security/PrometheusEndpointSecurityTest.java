package com.datn.mcq.security;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Giám sát (08/11): {@code GET /actuator/prometheus} mở không cần JWT để Prometheus trong mạng nội bộ scrape (Caddy chặn
 * /actuator/* từ Internet). Chỉ đúng đường này được mở; mọi đường actuator khác vẫn chỉ ADMIN.
 */
// @SpringBootTest mặc định tắt xuất metric và application.yaml của test chỉ lộ health,info; bật riêng Prometheus
// cho test này. Dòng exposure ở application.yaml chính được kiểm khi chạy stack Docker local (kế hoạch mục 5).
@SpringBootTest(properties = {
        "management.prometheus.metrics.export.enabled=true",
        "management.endpoints.web.exposure.include=health,info,prometheus"})
@Import(TestSafetyConfiguration.class)
class PrometheusEndpointSecurityTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private int teacher;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "PrometheusEndpointSecurityTest.setUp");
        teacher = AuthTestSupport.insertUser(jdbc, "GV", "gv.prom@test.vn", "teacher", "matkhau-dung-1", true);
    }

    @Test
    void prometheusCongKhaiTrongMangNoiBoVaCoSoLieuJvm() throws Exception {
        MockHttpServletResponse r = mockMvc.perform(get("/actuator/prometheus")).andReturn().getResponse();
        assertEquals(200, r.getStatus());
        String body = r.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("jvm_memory_used_bytes"), "Phải có số liệu JVM chuẩn của Micrometer");
        assertTrue(body.contains("datn_gemini_daily_quota"), "Phải có gauge hạn mức Gemini");
        assertTrue(body.contains("datn_jobs{status=\"pending\"}") || body.contains("datn_jobs{status=\"pending\","),
                "Phải có gauge số job theo trạng thái");
    }

    @Test
    void chiMoDungDuongPrometheusVoiMethodGet() throws Exception {
        assertEquals(401, mockMvc.perform(post("/actuator/prometheus")).andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get("/actuator/env")).andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get("/actuator/metrics")).andReturn().getResponse().getStatus());
    }

    @Test
    void giaoVienKhongVaoDuocActuatorKhac() throws Exception {
        assertEquals(403, mockMvc.perform(get("/actuator/metrics").with(AuthTestSupport.teacher(teacher)))
                .andReturn().getResponse().getStatus());
    }
}
