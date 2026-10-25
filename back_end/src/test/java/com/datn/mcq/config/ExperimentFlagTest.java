package com.datn.mcq.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-2 (quyết định 25/10): công cụ thí nghiệm (/api/generation/*, /api/catalog/templates) chỉ tồn tại khi
 * app.experiment.enabled=true; mặc định tắt nên người có đủ quyền (admin) cũng nhận 404 chứ không phải 200/400, và
 * OpenAPI không liệt kê. Trường hợp bật cờ được kiểm trong SecurityRouteAccessTest và các test sinh câu.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExperimentFlagTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private int teacher;
    private int admin;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "ExperimentFlagTest.setUp");
        teacher = AuthTestSupport.insertUser(jdbc, "GV", "gv.flag@test.vn", "teacher", "matkhau-dung-1", true);
        admin = AuthTestSupport.insertUser(jdbc, "Admin", "admin.flag@test.vn", "admin", "matkhau-admin-12", true);
    }

    private void expectNotFound(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                RequestPostProcessor who) throws Exception {
        mockMvc.perform(request.with(who)).andExpect(status().isNotFound());
    }

    @Test
    void cheTatMacDinhAdminGoiGenerationNhan404() throws Exception {
        RequestPostProcessor as = AuthTestSupport.admin(admin);
        expectNotFound(get("/api/generation/jobs"), as);
        expectNotFound(post("/api/generation/generate-sync").contentType(MediaType.APPLICATION_JSON).content("{}"), as);
        expectNotFound(post("/api/generation/jobs/enqueue").contentType(MediaType.APPLICATION_JSON).content("{}"), as);
        expectNotFound(post("/api/generation/jobs/process-next"), as);
    }

    @Test
    void cheTatMacDinhTemplatesNhan404() throws Exception {
        expectNotFound(get("/api/catalog/templates"), AuthTestSupport.teacher(teacher));
        expectNotFound(get("/api/catalog/templates"), AuthTestSupport.admin(admin));
        expectNotFound(post("/api/catalog/templates").contentType(MediaType.APPLICATION_JSON).content("{}"),
                AuthTestSupport.admin(admin));
    }

    @Test
    void danhSachMonVanHoatDongKhiTatCo() throws Exception {
        mockMvc.perform(get("/api/catalog/subjects").with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isOk());
    }

    @Test
    void openApiKhongLietKeCongCuThiNghiemKhiTatCo() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode paths = new ObjectMapper().readTree(body).path("paths");
        List<String> experimental = List.of("/api/generation", "/api/catalog/templates");
        paths.fieldNames().forEachRemaining(p -> experimental.forEach(prefix ->
                assertFalse(p.startsWith(prefix), "Đường thí nghiệm không được lộ khi tắt cờ: " + p)));
    }
}
