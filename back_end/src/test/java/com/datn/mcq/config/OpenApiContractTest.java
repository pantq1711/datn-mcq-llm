package com.datn.mcq.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V6: tài liệu OpenAPI sinh từ springdoc (dependency có sẵn trong pom).
 * Từ 25/10 form chấm mù đã gỡ nên không còn kiểm schema ẩn đáp án; test còn lại kiểm các endpoint được ghi trong tài liệu.
 * Xuất file khi chạy với -Dopenapi.export.path=đường_dẫn; mặc định không ghi file.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class OpenApiContractTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private String apiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Form chấm mù đã gỡ (25/10): không còn đường hay schema nào của /api/reviews. */
    @Test
    void blindReviewFormIsGone() throws Exception {
        JsonNode doc = objectMapper.readTree(apiDocs());
        doc.path("paths").fieldNames().forEachRemaining(path ->
                assertFalse(path.startsWith("/api/reviews"), "Form chấm mù đã gỡ: " + path));
        JsonNode schemas = doc.path("components").path("schemas");
        for (String dto : new String[] {"BlindQuestionReviewDto", "ReviewQuestionListItemDto", "SubmitReviewResponse"}) {
            assertTrue(schemas.path(dto).isMissingNode(), "Schema chấm mù phải biến mất: " + dto);
        }
    }

    @Test
    void newEndpointsAreDocumented() throws Exception {
        JsonNode paths = objectMapper.readTree(apiDocs()).path("paths");
        assertTrue(paths.path("/api/catalog/templates").has("post"), "Thiếu POST /api/catalog/templates");
    }

    /**
     * Quy tắc QC-RERUN (22/10): backend chưa có xác thực/phân quyền (Q-6), nên không được có endpoint công khai nào
     * kích hoạt chạy lại QC (gọi Solver tính phí cho câu tùy ý).
     */
    @Test
    void noPublicEndpointCanTriggerQcRerun() throws Exception {
        JsonNode paths = objectMapper.readTree(apiDocs()).path("paths");
        paths.fieldNames().forEachRemaining(path -> {
            String p = path.toLowerCase();
            assertFalse(p.contains("rerun"), "Không được mở endpoint chạy lại QC khi chưa có xác thực: " + path);
            if (p.contains("/qc")) {
                // W1-3: ngoại lệ duy nhất là vết QC theo ô đề, CHỈ ĐỌC (GET), không gọi Solver và không đối soát.
                assertTrue(p.matches("/api/exams/\\{[a-z]+}/slots/\\{[a-z]+}/qc-trace"),
                        "Không được mở endpoint chạy lại QC khi chưa có xác thực: " + path);
                java.util.List<String> methods = new java.util.ArrayList<>();
                paths.path(path).fieldNames().forEachRemaining(methods::add);
                assertEquals(java.util.List.of("get"), methods, "vết QC chỉ được là GET: " + path);
            }
        });
    }

    /** W1-1: Swagger có nút Authorize (bearer JWT); API đăng nhập công khai; người dùng hiện tại không lộ thành tham số. */
    @Test
    void bearerJwtSecuritySchemeIsDocumented() throws Exception {
        JsonNode doc = objectMapper.readTree(apiDocs());
        JsonNode scheme = doc.path("components").path("securitySchemes").path("bearerAuth");
        assertEquals("http", scheme.path("type").asText());
        assertEquals("bearer", scheme.path("scheme").asText());
        assertEquals("JWT", scheme.path("bearerFormat").asText());
        assertTrue(doc.path("security").toString().contains("bearerAuth"), "mặc định mọi API cần bearer");

        JsonNode login = doc.path("paths").path("/api/auth/login").path("post");
        assertFalse(login.isMissingNode(), "Thiếu POST /api/auth/login");
        assertTrue(login.has("security") && login.path("security").isEmpty(), "đăng nhập không cần token: " + login);
        assertFalse(doc.path("paths").path("/api/exams").path("get").isMissingNode(), "Thiếu GET /api/exams");

        JsonNode view = doc.path("paths").path("/api/exams/{examId}").path("get").path("parameters");
        Set<String> names = new LinkedHashSet<>();
        view.forEach(p -> names.add(p.path("name").asText()));
        assertEquals(Set.of("examId"), names, "người dùng hiện tại lấy từ token, không phải tham số");
    }

    @Test
    void exportOpenApiWhenRequested() throws Exception {
        String target = System.getProperty("openapi.export.path");
        assumeTrue(target != null && !target.isBlank(), "Không yêu cầu xuất OpenAPI");
        JsonNode doc = objectMapper.readTree(apiDocs());
        Path path = Path.of(target);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc) + "\n",
                StandardCharsets.UTF_8);
    }
}
