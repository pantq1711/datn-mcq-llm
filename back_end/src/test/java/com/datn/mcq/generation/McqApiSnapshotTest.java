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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hồi quy MCQ (GĐ2): key JSON của các API MCQ so với HEAD 93a1734. Chỉ được thêm questionType (thiết kế đã duyệt,
 * mục 8); kết quả QC và phản hồi nộp đánh giá của câu mcq giữ đúng bộ key cũ, không có trường SA.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class McqApiSnapshotTest {

    private static final Set<String> SUMMARY_KEYS_BEFORE_V4 = Set.of("id", "templateId", "runId", "questionText",
            "optionA", "optionB", "optionC", "optionD", "parseSuccess", "status", "tokenCount", "latencyMs", "createdAt");
    private static final Set<String> QC_REPORT_KEYS_BEFORE_V4 = Set.of("resultingStatus", "formatPassed",
            "formatErrors", "answerCheckPassed", "judgeAnswer", "judgeModel", "solverReasoning", "answerCheckStatus",
            "dedupStatus", "dedup");
    private static final Set<String> LIST_ITEM_KEYS_BEFORE_V4 = Set.of("id", "templateId", "reviewPurpose",
            "subjectName", "gradeLevel", "topic", "questionText", "createdAt");
    private static final Set<String> FORM_KEYS_BEFORE_V4 = Set.of("id", "templateId", "reviewPurpose", "subjectName",
            "gradeLevel", "topic", "questionText", "optionA", "optionB", "optionC", "optionD", "createdAt");
    private static final Set<String> SUBMIT_KEYS_BEFORE_V4 = Set.of("reviewId", "questionId", "reviewPurpose",
            "reviewerAnswer", "formatOk", "difficultyLabel", "decision", "finalOption", "edited", "editDistance",
            "questionStatus", "bankItemId", "reviewedAt");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int template;
    private int teacher;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "McqApiSnapshotTest.setUp");
        int subject = insert("INSERT INTO subject (name) VALUES ('Toán Hồi quy MCQ')");
        teacher = insert("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.mcq@test', 'teacher')");
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

    /**
     * Quyết định 4 sau GĐ2: body mcq có trường SA mang giá trị null không bị chặn; chỉ chặn khi khác null.
     */
    @Test
    void truongSaNullTrongBodyMcqKhongBiChan() throws Exception {
        int run = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES ((SELECT subject_id FROM question_template WHERE id = ?), 'few-shot-structured',"
                + " 'm', 0.7, 'p', 'production')", template);
        String insertMcq = "INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, created_at)"
                + " VALUES (?, ?, ?, '$1$', '$2$', '$3$', '$4$', 'B', '{}', TRUE, 'qc_passed', now())";
        int q1 = insert(insertMcq, template, run, "Tính $1+1$");
        int q2 = insert(insertMcq, template, run, "Tính $2+2$");
        String withNulls = "{\"reviewerId\": " + teacher + ", \"reviewerAnswer\": \"B\", \"formatOk\": true,"
                + " \"difficultyLabel\": \"easy\", \"decision\": \"approved_as_is\", \"questionType\": null,"
                + " \"reviewerSaKind\": null, \"reviewerSaAnswer\": null, \"finalSaAnswer\": null, \"statements\": null}";
        mockMvc.perform(post("/api/reviews/questions/" + q1).contentType(MediaType.APPLICATION_JSON).content(withNulls))
                .andExpect(status().isOk());
        String withValue = "{\"reviewerId\": " + teacher + ", \"reviewerAnswer\": \"B\", \"formatOk\": true,"
                + " \"difficultyLabel\": \"easy\", \"decision\": \"approved_as_is\", \"reviewerSaAnswer\": \"2\"}";
        mockMvc.perform(post("/api/reviews/questions/" + q2).contentType(MediaType.APPLICATION_JSON).content(withValue))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review WHERE question_id = ?",
                Integer.class, q2));
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
        assertEquals("few-shot-structured", run.get("strategy"));
        assertEquals("sync-v1", run.get("prompt_version"));

        jdbc.update("UPDATE generated_question SET status = 'qc_passed' WHERE id = ?", questionId);
        JsonNode list = call(get("/api/reviews/questions"));
        assertEquals(plusQuestionType(LIST_ITEM_KEYS_BEFORE_V4), keys(list.get(0)));
        JsonNode form = call(get("/api/reviews/questions/" + questionId));
        assertEquals(plusQuestionType(FORM_KEYS_BEFORE_V4), keys(form));
        assertEquals("mcq", form.get("questionType").asText());

        JsonNode submitted = call(post("/api/reviews/questions/" + questionId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewerId\": " + teacher + ", \"reviewerAnswer\": \"B\", \"formatOk\": true,"
                        + " \"difficultyLabel\": \"easy\", \"decision\": \"approved_as_is\"}"));
        assertEquals(SUBMIT_KEYS_BEFORE_V4, keys(submitted), "phản hồi nộp mcq giữ đúng bộ key cũ");
        assertEquals("in_bank", submitted.get("questionStatus").asText());
    }
}
