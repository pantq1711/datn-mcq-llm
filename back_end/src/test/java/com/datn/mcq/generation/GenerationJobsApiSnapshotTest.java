package com.datn.mcq.generation;

import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/generation/jobs trả thẳng entity GenerationJob. GĐ2 mở questionType (mặc định mcq) theo yêu cầu đã duyệt;
 * targetStatementDifficulties chỉ có với tf nên bị bỏ khỏi JSON của job mcq. Các key cũ giữ nguyên.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class GenerationJobsApiSnapshotTest {

    /** Key mà endpoint trả trước V4 (getter của entity GenerationJob ở HEAD 93a1734). */
    private static final Set<String> OLD_KEYS = Set.of(
            "id", "runId", "templateId", "questionId", "targetDifficulty", "status", "attempt", "nextRunAt",
            "lastError", "tokenCount", "latencyMs", "costUsd", "createdAt", "updatedAt");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "GenerationJobsApiSnapshotTest.setUp");
        int subject = insert("INSERT INTO subject (name) VALUES ('Toán Job')");
        int template = insert("INSERT INTO question_template (subject_id, source_question, is_active)"
                + " VALUES (?, 'Mẫu', TRUE)", subject);
        int run = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'few-shot-structured', 'm', 0.7, 'p', 'production')", subject);
        insert("INSERT INTO generation_job (run_id, template_id, target_difficulty) VALUES (?, ?, 'easy')",
                run, template);
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    @Test
    void jobMcqGiuKeyCuVaThemQuestionType() throws Exception {
        String body = mockMvc.perform(get("/api/generation/jobs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode jobs = new ObjectMapper().readTree(body);
        assertEquals(1, jobs.size());
        Set<String> keys = new LinkedHashSet<>();
        jobs.get(0).fieldNames().forEachRemaining(keys::add);
        Set<String> expected = new LinkedHashSet<>(OLD_KEYS);
        expected.add("questionType");
        // W1-2 (V13): loại job luôn có trong JSON; job cũ và job sinh câu là "generate".
        expected.add("jobType");
        assertEquals(expected, keys);
        assertEquals("mcq", jobs.get(0).get("questionType").asText());
        assertEquals("generate", jobs.get(0).get("jobType").asText());
        assertEquals("easy", jobs.get(0).get("targetDifficulty").asText());
    }
}
