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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V6: tài liệu OpenAPI sinh từ springdoc (dependency có sẵn trong pom).
 * Chặn việc schema form chấm chứa đáp án LLM, lời giải, độ khó LLM hoặc kết quả answer_check/dedup.
 * Xuất file khi chạy với -Dopenapi.export.path=đường_dẫn; mặc định không ghi file.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class OpenApiContractTest {

    private static final List<String> FORBIDDEN_IN_REVIEW_FORM = List.of(
            "correctOption", "correct_option", "explanation", "llmDifficulty", "llm_difficulty",
            "answerCorrect", "answer_correct", "judgeAnswer", "judge_answer", "judgeModel",
            "similarityScore", "similarity_score", "comparedToQuestionId", "thresholdUsed",
            "strategy", "difficultyJudgeModel",
            // Quyết định 21/10 mục 2: API chấm không trả status, latexValid, latexErrors (cả hai purpose).
            "status", "latexValid", "latexErrors", "dedupStatus", "answerCheckStatus",
            // GĐ2 (V4): đáp số SA của LLM, kết quả Solver SA và cảnh báo định dạng đáp số.
            "saAnswer", "sa_answer", "judgeSaKind", "judge_sa_kind", "judgeSaAnswer", "judge_sa_answer",
            "answerWarnings", "qcReport",
            // GĐ3 (V4): nhãn và mức của LLM cho từng ý TF, kết quả Solver TF, đúng/sai từng ý do server tính.
            "label", "llmLabel", "llm_label", "difficulty", "finalDifficulty", "final_difficulty",
            "labelCorrect", "label_correct", "judgeLabel", "judge_label", "tfJudgements");

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

    @Test
    void reviewFormSchemasNeverExposeAnswerKeyLlmDifficultyOrQcScores() throws Exception {
        JsonNode schemas = objectMapper.readTree(apiDocs()).path("components").path("schemas");
        for (String dto : List.of("BlindQuestionReviewDto", "ReviewQuestionListItemDto", "SubmitReviewResponse")) {
            assertFalse(schemas.path(dto).path("properties").isMissingNode(), "Thiếu schema " + dto);
            // GĐ2: quét cả schema lồng nhau (qua $ref, items, allOf/anyOf/oneOf), không chỉ thuộc tính cấp ngoài.
            for (String schemaName : reachableSchemas(schemas, dto)) {
                JsonNode properties = schemas.path(schemaName).path("properties");
                for (String field : FORBIDDEN_IN_REVIEW_FORM) {
                    assertFalse(properties.has(field),
                            dto + " (qua schema " + schemaName + ") không được có trường " + field);
                }
            }
        }
    }

    /** Form chấm phải cho frontend biết dạng câu (mcq, tf, sa) để hiển thị đúng form. */
    @Test
    void reviewFormDeclaresQuestionType() throws Exception {
        JsonNode schemas = objectMapper.readTree(apiDocs()).path("components").path("schemas");
        for (String dto : List.of("BlindQuestionReviewDto", "ReviewQuestionListItemDto")) {
            assertTrue(schemas.path(dto).path("properties").has("questionType"), dto + " thiếu questionType");
        }
    }

    /** GĐ3: form chấm TF có 4 ý, mỗi ý chỉ có key và text (không có nhãn hay mức của LLM). */
    @Test
    void reviewFormStatementsOnlyHaveKeyAndText() throws Exception {
        JsonNode schemas = objectMapper.readTree(apiDocs()).path("components").path("schemas");
        JsonNode statements = schemas.path("BlindQuestionReviewDto").path("properties").path("statements");
        assertFalse(statements.isMissingNode(), "BlindQuestionReviewDto thiếu statements");
        String ref = statements.path("items").path("$ref").asText();
        assertTrue(ref.startsWith("#/components/schemas/"), "statements phải là mảng schema riêng: " + statements);
        JsonNode item = schemas.path(ref.substring("#/components/schemas/".length())).path("properties");
        Set<String> keys = new LinkedHashSet<>();
        item.fieldNames().forEachRemaining(keys::add);
        assertEquals(Set.of("key", "text"), keys);
    }

    private static Set<String> reachableSchemas(JsonNode schemas, String root) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>(List.of(root));
        while (!todo.isEmpty()) {
            String name = todo.pop();
            if (seen.add(name)) {
                collectRefs(schemas.path(name), todo);
            }
        }
        return seen;
    }

    private static void collectRefs(JsonNode node, Deque<String> todo) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual() && ref.asText().startsWith("#/components/schemas/")) {
                todo.push(ref.asText().substring("#/components/schemas/".length()));
            }
            node.elements().forEachRemaining(child -> collectRefs(child, todo));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectRefs(child, todo));
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
