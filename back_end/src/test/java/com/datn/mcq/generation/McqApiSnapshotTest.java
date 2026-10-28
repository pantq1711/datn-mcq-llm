package com.datn.mcq.generation;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hồi quy MCQ (GĐ2): key JSON của API sinh đồng bộ MCQ so với HEAD 93a1734. Chỉ được thêm questionType (thiết kế đã
 * duyệt, mục 8); kết quả QC của câu mcq giữ đúng bộ key cũ, không có trường SA. (Phần form chấm và nộp đánh giá đã gỡ
 * ngày 25/10.)
 */
@SpringBootTest(properties = "app.experiment.enabled=true")
@Import(TestSafetyConfiguration.class)
class McqApiSnapshotTest {

    private static final Set<String> SUMMARY_KEYS_BEFORE_V4 = Set.of("id", "templateId", "runId", "questionText",
            "optionA", "optionB", "optionC", "optionD", "parseSuccess", "status", "tokenCount", "latencyMs", "createdAt");
    private static final Set<String> QC_REPORT_KEYS_BEFORE_V4 = Set.of("resultingStatus", "formatPassed",
            "formatErrors", "answerCheckPassed", "judgeAnswer", "judgeModel", "solverReasoning", "answerCheckStatus",
            "dedupStatus", "dedup");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int template;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "McqApiSnapshotTest.setUp");
        int subject = insert("INSERT INTO subject (name) VALUES ('Toán Hồi quy MCQ')");
        template = insert("INSERT INTO question_template (subject_id, source_question, is_active, grade_level, topic)"
                + " VALUES (?, 'Mẫu', TRUE, 8, 'Căn bậc hai')", subject);
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private static Set<String> plusQuestionType(Set<String> before) {
        Set<String> expected = new LinkedHashSet<>(before);
        expected.add("questionType");
        return expected;
    }

    @Test
    void apiMcqChiThemQuestionType() throws Exception {
        String generated = "{\"question_text\": \"Kết quả của $\\\\sqrt{(-5)^2}$ là:\", \"option_a\": \"$-5$\","
                + " \"option_b\": \"$5$\", \"option_c\": \"$25$\", \"option_d\": \"$\\\\pm 5$\","
                + " \"correct_option\": \"B\", \"explanation\": \"$\\\\sqrt{a^2} = |a|$.\"}";
        JsonNode resp = call(post("/api/generation/generate-sync").contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\": " + template + ", \"runAnswerCheck\": false, \"rawLlmOutputOverride\": "
                        + SimpleJson.quote(generated) + "}"));
        assertEquals(Set.of("question", "qcReport"), keys(resp));
        assertEquals(plusQuestionType(SUMMARY_KEYS_BEFORE_V4), keys(resp.get("question")));
        assertEquals("mcq", resp.get("question").get("questionType").asText());
        assertEquals(QC_REPORT_KEYS_BEFORE_V4, keys(resp.get("qcReport")), "qcReport mcq không có trường mới");
        int questionId = resp.get("question").get("id").asInt();
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM generated_question g JOIN experiment_run r"
                + " ON r.id = g.run_id WHERE g.id = ?", questionId);
        assertEquals("mcq", run.get("question_type"));
        assertEquals("zero-shot", run.get("strategy"), "thí nghiệm few-shot đã bỏ (25/10): luôn zero-shot");
        assertEquals("sync-v1", run.get("prompt_version"));
    }

    @Test
    void chienLuocFewShotBiTuChoiOCauMcq() throws Exception {
        for (String strategy : new String[] {"few-shot", "few-shot-structured"}) {
            mockMvc.perform(post("/api/generation/generate-sync").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"templateId\": " + template + ", \"strategy\": \"" + strategy
                                    + "\", \"runAnswerCheck\": false, \"rawLlmOutputOverride\": \"{}\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM experiment_run", Integer.class));
    }
}
