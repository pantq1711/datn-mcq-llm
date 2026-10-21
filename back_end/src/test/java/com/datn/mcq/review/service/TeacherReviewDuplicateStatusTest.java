package com.datn.mcq.review.service;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.repository.QuestionTemplateRepository;
import com.datn.mcq.catalog.repository.SubjectRepository;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.SourceType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.enums.UserRole;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.repository.ExperimentRunRepository;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.review.repository.TeacherReviewRepository;
import com.datn.mcq.user.entity.UserAccount;
import com.datn.mcq.user.repository.UserAccountRepository;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quyết định 3 (2026-10-21), phần production: câu duplicate là trạng thái cuối, không vào danh sách chấm
 * và không bị TeacherReviewService ghi đè trạng thái. Phần experiment và format_failed: xem BlindReviewContractTest.
 * Sửa 2026-10-21 (quyết định 21/10): nộp đánh giá cho câu duplicate trả 404 "Không tìm thấy câu hỏi"
 * thay vì 409, để không tiết lộ việc câu bị loại vì trùng.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class TeacherReviewDuplicateStatusTest {

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
    @Autowired
    private ExperimentRunRepository runRepository;
    @Autowired
    private GeneratedQuestionRepository questionRepository;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private TeacherReviewRepository teacherReviewRepository;

    private MockMvc mockMvc;
    private QuestionTemplate template;
    private ExperimentRun productionRun;
    private UserAccount teacher;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbcTemplate, "TeacherReviewDuplicateStatusTest.setUp");
        Subject subject = subjectRepository.save(new Subject("Toán Trùng Lặp"));
        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(subject.getId());
        tpl.setGradeLevel(8);
        tpl.setTopic("Dedup");
        tpl.setSourceType(SourceType.TEXT);
        tpl.setSourceQuestion("Câu mẫu?");
        tpl.setIsActive(true);
        template = templateRepository.save(tpl);

        ExperimentRun run = new ExperimentRun();
        run.setSubjectId(subject.getId());
        run.setPurpose(Purpose.PRODUCTION);
        run.setStrategy(Strategy.FEW_SHOT_STRUCTURED);
        run.setModelName("gemini-test");
        run.setTemperature(0.7);
        run.setPromptText("Test prompt");
        run.setStatus("completed");
        run.setStartedAt(LocalDateTime.now());
        productionRun = runRepository.save(run);

        teacher = userRepository.save(new UserAccount("GV Dedup", "dedup.test@ptit.edu.vn", UserRole.TEACHER));
    }

    private GeneratedQuestion question(QuestionStatus status) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setTemplateId(template.getId());
        gq.setRunId(productionRun.getId());
        gq.setQuestionText("Kết quả của phép tính $\\sqrt{(-5)^2}$ là gì?");
        gq.setOptionA("$-5$");
        gq.setOptionB("$5$");
        gq.setOptionC("$-25$");
        gq.setOptionD("$25$");
        gq.setCorrectOption(Option.B);
        gq.setExplanation("Ta có $\\sqrt{A^2} = |A|$.");
        gq.setRawLlmOutput("{}");
        gq.setLlmDifficulty(Difficulty.EASY);
        gq.setParseSuccess(true);
        gq.setStatus(status);
        gq.setCreatedAt(LocalDateTime.now());
        return questionRepository.save(gq);
    }

    @Test
    void productionDuplicateIsNotListedForReview() throws Exception {
        GeneratedQuestion duplicate = question(QuestionStatus.DUPLICATE);
        GeneratedQuestion passed = question(QuestionStatus.QC_PASSED);

        mockMvc.perform(get("/api/reviews/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + passed.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + duplicate.getId() + ")]").doesNotExist());

        mockMvc.perform(get("/api/reviews/questions").param("status", "duplicate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + duplicate.getId() + ")]").doesNotExist());
    }

    @Test
    void rejectingProductionDuplicateIsRefusedAndStatusIsKept() throws Exception {
        GeneratedQuestion duplicate = question(QuestionStatus.DUPLICATE);

        mockMvc.perform(post("/api/reviews/questions/" + duplicate.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId": %d, "reviewerAnswer": "B", "formatOk": true,
                                 "difficultyLabel": "easy", "decision": "rejected",
                                 "reasonCodes": ["content_inaccurate"]}
                                """.formatted(teacher.getId())))
                .andExpect(status().isNotFound());

        assertEquals(QuestionStatus.DUPLICATE, questionRepository.findById(duplicate.getId()).orElseThrow().getStatus());
        assertTrue(teacherReviewRepository.findByQuestionIdAndReviewerId(duplicate.getId(), teacher.getId()).isEmpty());
    }

    @Test
    void approvingProductionDuplicateIsRefusedAndStatusIsKept() throws Exception {
        GeneratedQuestion duplicate = question(QuestionStatus.DUPLICATE);

        mockMvc.perform(post("/api/reviews/questions/" + duplicate.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId": %d, "reviewerAnswer": "B", "formatOk": true,
                                 "difficultyLabel": "easy", "decision": "approved_as_is", "finalOption": "B"}
                                """.formatted(teacher.getId())))
                .andExpect(status().isNotFound());

        assertEquals(QuestionStatus.DUPLICATE, questionRepository.findById(duplicate.getId()).orElseThrow().getStatus());
        assertTrue(teacherReviewRepository.findByQuestionIdAndReviewerId(duplicate.getId(), teacher.getId()).isEmpty());
    }
}
