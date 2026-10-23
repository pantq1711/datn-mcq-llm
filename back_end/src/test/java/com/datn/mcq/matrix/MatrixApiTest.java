package com.datn.mcq.matrix;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ma trận đề qua API JSON (V6, phần C mục 1): tạo, kiểm tra dữ liệu, mỗi ô tạo một question_template, tổng số câu theo
 * phần và số lệnh hỏi theo mức; mẫu "Giữa kì I – Toán 8" đọc từ docs/sample/matrix_giuaki1_toan8.json.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class MatrixApiTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int subject;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "MatrixApiTest.setUp");
        subject = jdbc.queryForObject("INSERT INTO subject (name) VALUES ('Toán') RETURNING id", Integer.class);
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static final String MCQ_CELL = "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\","
            + " \"questionCount\": 2, \"topic\": \"Đa thức\", \"content\": \"Đơn thức, đa thức\","
            + " \"learningOutcomes\": {\"easy\": \"Nhận biết được đơn thức, đa thức.\"}}";
    private static final String TF_CELL = "{\"part\": \"II\", \"questionType\": \"tf\", \"statementDifficulties\":"
            + " [\"easy\", \"easy\", \"medium\", \"hard\"], \"questionCount\": 1, \"topic\": \"Hằng đẳng thức\","
            + " \"learningOutcomes\": {\"easy\": \"Nhận biết.\", \"medium\": \"Khai triển.\", \"hard\": \"Vận dụng.\"}}";
    private static final String SA_CELL = "{\"part\": \"III\", \"questionType\": \"sa\", \"difficulty\": \"medium\","
            + " \"questionCount\": 1, \"topic\": \"Tứ giác\", \"learningOutcomes\": {\"medium\": \"Tính được góc.\"}}";

    private String body(String... cells) {
        return "{\"name\": \"Đề thử\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"notes\": [\"ghi chú 1\"],"
                + " \"cells\": [" + String.join(", ", cells) + "]}";
    }

    @Test
    void taoMaTranMoiOMotTemplate() throws Exception {
        JsonNode m = json(postJson("/api/exam-matrices", body(MCQ_CELL, TF_CELL, SA_CELL)).andExpect(status().isOk()));
        assertNotNull(m.get("id"));
        assertEquals("Đề thử", m.get("name").asText());
        assertEquals("[\"ghi chú 1\"]", m.get("notes").toString());
        assertEquals(3, m.get("cells").size());
        JsonNode tf = m.get("cells").get(1);
        assertEquals("II", tf.get("part").asText());
        assertEquals("[\"easy\",\"easy\",\"medium\",\"hard\"]", tf.get("statementDifficulties").toString());
        assertTrue(tf.get("difficulty") == null, "tf không có mức cấp câu");
        assertEquals("{\"I\":2,\"II\":1,\"III\":1}", m.get("totals").get("questionsByPart").toString());
        assertEquals("{\"easy\":4,\"medium\":2,\"hard\":1}", m.get("totals").get("commandsByLevel").toString());
        assertEquals(7, m.get("totals").get("totalCommands").asInt());

        for (JsonNode cell : m.get("cells")) {
            Map<String, Object> tpl = jdbc.queryForMap("SELECT * FROM question_template WHERE id = ?",
                    cell.get("templateId").asInt());
            assertEquals(subject, tpl.get("subject_id"));
            assertEquals(8, tpl.get("grade_level"));
            assertEquals(cell.get("topic").asText(), tpl.get("topic"));
            assertTrue(((String) tpl.get("source_question")).contains(cell.get("topic").asText()));
        }
        JsonNode again = json(mockMvc.perform(get("/api/exam-matrices/" + m.get("id").asInt())).andExpect(status().isOk()));
        assertEquals(m, again);
        assertEquals(1, json(mockMvc.perform(get("/api/exam-matrices")).andExpect(status().isOk())).size());
    }

    @Test
    void duLieuKhongHopLeTra400() throws Exception {
        Map<String, String> cases = new java.util.LinkedHashMap<>();
        cases.put(body(), "Ma trận phải có ít nhất một ô");
        cases.put(body(MCQ_CELL.replace("\"part\": \"I\"", "\"part\": \"II\"")),
                "Ô 1: phần II chỉ chứa câu đúng/sai (tf)");
        cases.put(body(MCQ_CELL.replace("\"difficulty\": \"easy\", ", "")), "Ô 1: câu mcq phải có difficulty");
        cases.put(body(TF_CELL.replace("[\"easy\", \"easy\", \"medium\", \"hard\"]", "[\"easy\", \"hard\"]")),
                "Ô 1: câu đúng/sai phải có statementDifficulties gồm đúng 4 mức");
        cases.put(body(TF_CELL.replace("\"questionCount\": 1", "\"questionCount\": 1, \"difficulty\": \"easy\"")),
                "Ô 1: câu đúng/sai không dùng difficulty");
        cases.put(body(SA_CELL.replace("\"medium\": \"Tính được góc.\"", "\"easy\": \"Tính được góc.\"")),
                "Ô 1: thiếu yêu cầu cần đạt cho mức medium");
        cases.put(body(SA_CELL.replace("\"medium\": \"Tính được góc.\"", "\"medium\": \"x\", \"rat kho\": \"y\"")),
                "Ô 1: learningOutcomes chỉ nhận khóa easy, medium, hard");
        cases.put(body(MCQ_CELL.replace("\"questionCount\": 2", "\"questionCount\": 0")),
                "Ô 1: questionCount phải từ 1 đến 50");
        cases.put(body(MCQ_CELL.replace("\"topic\": \"Đa thức\"", "\"topic\": \"  \"")), "Ô 1: topic không được để trống");
        cases.put("{\"subjectId\": " + subject + ", \"cells\": [" + MCQ_CELL + "]}", "name không được để trống");
        for (Map.Entry<String, String> c : cases.entrySet()) {
            postJson("/api/exam-matrices", c.getKey()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(c.getValue()));
        }
        postJson("/api/exam-matrices", body(MCQ_CELL).replace("\"subjectId\": " + subject, "\"subjectId\": 999999"))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM exam_matrix", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM question_template", Integer.class),
                "lỗi thì không để lại template");
    }

    @Test
    void mauGiuaKi1Toan8() throws Exception {
        JsonNode samples = json(mockMvc.perform(get("/api/exam-matrices/samples")).andExpect(status().isOk()));
        assertEquals(1, samples.size());
        assertEquals("giuaki1-toan8", samples.get(0).get("key").asText());
        assertEquals("Giữa kì I – Toán 8", samples.get(0).get("name").asText());

        JsonNode m = json(postJson("/api/exam-matrices/samples/giuaki1-toan8", "{}").andExpect(status().isOk()));
        assertEquals("Giữa kì I – Toán 8", m.get("name").asText());
        assertEquals("giuaki1-toan8", m.get("sampleKey").asText());
        assertEquals(subject, m.get("subjectId").asInt(), "bỏ trống subjectId thì dùng môn Toán");
        assertEquals(11, m.get("cells").size());
        assertEquals("{\"I\":12,\"II\":2,\"III\":4}", m.get("totals").get("questionsByPart").toString());
        assertEquals("{\"easy\":16,\"medium\":5,\"hard\":3}", m.get("totals").get("commandsByLevel").toString());
        assertEquals(24, m.get("totals").get("totalCommands").asInt());
        JsonNode sample = objectMapper.readTree(Files.readString(sampleFile(), StandardCharsets.UTF_8));
        assertEquals(sample.get("notes"), m.get("notes"), "giữ nguyên notes của mẫu");
        assertEquals(sample.get("source").asText(), m.get("source").asText());
        JsonNode first = m.get("cells").get(0);
        assertEquals("Đa thức", first.get("topic").asText());
        assertEquals("Đơn thức, đa thức", first.get("content").asText());
        assertEquals("Nhận biết được đơn thức, đa thức. Xác định được bậc của đa thức.",
                first.get("learningOutcomes").get("easy").asText());

        postJson("/api/exam-matrices/samples/khong-co", "{}").andExpect(status().isNotFound());
    }

    @Test
    void banChepMauTrongClasspathGiongHetDocsSample() throws Exception {
        String docs = Files.readString(sampleFile(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        try (var in = getClass().getResourceAsStream("/samples/matrix_giuaki1_toan8.json")) {
            assertNotNull(in, "thiếu samples/matrix_giuaki1_toan8.json trong classpath");
            String classpath = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertEquals(docs, classpath, "chép lại docs/sample/matrix_giuaki1_toan8.json vào src/main/resources/samples");
        }
    }

    private static Path sampleFile() {
        for (Path p : List.of(Path.of("../docs/sample/matrix_giuaki1_toan8.json"),
                Path.of("docs/sample/matrix_giuaki1_toan8.json"))) {
            if (Files.exists(p)) {
                return p;
            }
        }
        throw new IllegalStateException("Không tìm thấy docs/sample/matrix_giuaki1_toan8.json");
    }
}
