package com.datn.mcq.matrix;

import com.datn.mcq.config.AuthTestSupport;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-5 (E6): trên DB mới migrate xong (chỉ có môn do V17 seed) giáo viên tạo được ma trận bằng JSON với subjectId của
 * "Toán", và "tạo từ mẫu" dùng lại môn đó thay vì sinh môn trùng. Test dọn hết dữ liệu rồi chạy lại đúng nội dung V17 để
 * mô phỏng DB vừa migrate (TestDataCleaner xóa bảng subject giữa các test).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class SubjectSeedMatrixTest {

    private static final String MCQ_CELL = "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\","
            + " \"questionCount\": 2, \"topic\": \"Đa thức\", \"content\": \"Đơn thức, đa thức\","
            + " \"learningOutcomes\": {\"easy\": \"Nhận biết được đơn thức, đa thức.\"}}";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int teacher;
    private int toan;

    @BeforeEach
    void setUp() throws IOException {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "SubjectSeedMatrixTest.setUp");
        try (var in = getClass().getResourceAsStream("/db/migration/V17__seed_subjects.sql")) {
            jdbc.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        teacher = jdbc.queryForObject("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.seed@test', 'teacher')"
                + " RETURNING id", Integer.class);
        toan = jdbc.queryForObject("SELECT id FROM subject WHERE name = 'Toán'", Integer.class);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(AuthTestSupport.teacher(teacher)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<String> subjectNames() {
        return new ArrayList<>(jdbc.queryForList("SELECT name FROM subject ORDER BY name", String.class));
    }

    @Test
    void taoMaTranBangJsonVoiSubjectIdCuaToanTrenDbMoiMigrate() throws Exception {
        assertEquals(List.of("Giáo dục công dân", "Toán"), subjectNames());
        JsonNode subjects = json(perform(get("/api/catalog/subjects")).andExpect(status().isOk()));
        assertEquals(2, subjects.size());

        String body = "{\"name\": \"Đề thử\", \"subjectId\": " + toan + ", \"gradeLevel\": 8, \"cells\": [" + MCQ_CELL + "]}";
        JsonNode m = json(perform(post("/api/exam-matrices").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()));

        assertEquals(toan, m.get("subjectId").asInt());
        assertEquals(1, m.get("cells").size());
    }

    @Test
    void taoTuMauSauSeedDungLaiMonToanKhongSinhMonTrung() throws Exception {
        JsonNode m = json(perform(post("/api/exam-matrices/samples/giuaki1-toan8").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isOk()));

        assertEquals(toan, m.get("subjectId").asInt(), "dùng lại môn Toán do V17 seed");
        assertEquals(List.of("Giáo dục công dân", "Toán"), subjectNames(), "không sinh môn trùng");
    }
}
