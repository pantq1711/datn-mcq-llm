package com.datn.mcq.catalog.controller;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.repository.QuestionTemplateRepository;
import com.datn.mcq.catalog.repository.SubjectRepository;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V5: POST /api/catalog/templates tạo question_template cho một subject đã tồn tại.
 * Endpoint không tạo subject; subject không tồn tại thì trả 404.
 */
@SpringBootTest(properties = "app.experiment.enabled=true")
@Import(TestSafetyConfiguration.class)
class CatalogTemplateCreationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private QuestionTemplateRepository templateRepository;

    private MockMvc mockMvc;
    private Subject subject;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbcTemplate, "CatalogTemplateCreationTest.setUp");
        subject = subjectRepository.save(new Subject("Toán Đại số 8"));
    }

    private org.springframework.test.web.servlet.ResultActions postTemplate(String json) throws Exception {
        return mockMvc.perform(post("/api/catalog/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void createsTemplateForExistingSubjectWithDefaults() throws Exception {
        postTemplate("""
                {"subjectId": %d, "gradeLevel": 8, "topic": "  Phân thức đại số  ",
                 "learningOutcome": "Rút gọn được phân thức.",
                 "sourceQuestion": "Rút gọn phân thức $\\\\frac{4x^2y}{8xy^3}$."}
                """.formatted(subject.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.subjectId").value(subject.getId()))
                .andExpect(jsonPath("$.gradeLevel").value(8))
                .andExpect(jsonPath("$.topic").value("Phân thức đại số"))
                .andExpect(jsonPath("$.sourceType").value("text"))
                .andExpect(jsonPath("$.isActive").value(true));

        List<QuestionTemplate> saved = templateRepository.findBySubjectIdOrderByIdAsc(subject.getId());
        assertEquals(1, saved.size());
        assertEquals("Rút gọn phân thức $\\frac{4x^2y}{8xy^3}$.", saved.get(0).getSourceQuestion());
        assertEquals(SourceType.TEXT, saved.get(0).getSourceType());
        assertEquals(1, saved.get(0).getVersion());

        mockMvc.perform(get("/api/catalog/templates").param("subjectId", String.valueOf(subject.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topic").value("Phân thức đại số"));
    }

    @Test
    void unknownSubjectReturns404AndCreatesNothing() throws Exception {
        postTemplate("""
                {"subjectId": %d, "sourceQuestion": "Câu mẫu?"}
                """.formatted(subject.getId() + 999))
                .andExpect(status().isNotFound());
        assertTrue(templateRepository.findAll().isEmpty());
    }

    @Test
    void missingSubjectIdOrBlankSourceQuestionReturns400() throws Exception {
        postTemplate("""
                {"sourceQuestion": "Câu mẫu?"}
                """)
                .andExpect(status().isBadRequest());
        postTemplate("""
                {"subjectId": %d, "sourceQuestion": "   "}
                """.formatted(subject.getId()))
                .andExpect(status().isBadRequest());
        assertTrue(templateRepository.findAll().isEmpty());
    }

    @Test
    void gradeLevelOutsideJuniorHighReturns400() throws Exception {
        postTemplate("""
                {"subjectId": %d, "gradeLevel": 10, "sourceQuestion": "Câu mẫu?"}
                """.formatted(subject.getId()))
                .andExpect(status().isBadRequest());
        assertTrue(templateRepository.findAll().isEmpty());
    }

    @Test
    void topicLongerThanColumnReturns400() throws Exception {
        postTemplate("""
                {"subjectId": %d, "topic": "%s", "sourceQuestion": "Câu mẫu?"}
                """.formatted(subject.getId(), "a".repeat(256)))
                .andExpect(status().isBadRequest());
        assertTrue(templateRepository.findAll().isEmpty());
    }
}
