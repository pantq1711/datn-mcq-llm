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
import java.util.List;

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
            "status", "latexValid", "latexErrors", "dedupStatus", "answerCheckStatus");

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
            JsonNode properties = schemas.path(dto).path("properties");
            assertFalse(properties.isMissingNode(), "Thiếu schema " + dto);
            for (String field : FORBIDDEN_IN_REVIEW_FORM) {
                assertFalse(properties.has(field), dto + " không được có trường " + field);
            }
        }
    }

    @Test
    void newEndpointsAreDocumented() throws Exception {
        JsonNode paths = objectMapper.readTree(apiDocs()).path("paths");
        assertTrue(paths.path("/api/catalog/templates").has("post"), "Thiếu POST /api/catalog/templates");
    }

    /**
     * V4 (22/10): backend chưa có xác thực/phân quyền (Q-6), nên không được có endpoint công khai nào
     * kích hoạt chạy lại QC (gọi Solver tính phí cho câu tùy ý).
     */
    @Test
    void noPublicEndpointCanTriggerQcRerun() throws Exception {
        JsonNode paths = objectMapper.readTree(apiDocs()).path("paths");
        paths.fieldNames().forEachRemaining(path ->
                assertFalse(path.toLowerCase().contains("rerun") || path.toLowerCase().contains("/qc"),
                        "Không được mở endpoint chạy lại QC khi chưa có xác thực: " + path));
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
