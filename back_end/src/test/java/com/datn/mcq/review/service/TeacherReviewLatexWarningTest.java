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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cảnh báo LaTeX (lệnh ngoài danh sách cho phép) không được chặn APPROVED_WITH_EDIT
 * và không làm latexValid = false; lỗi fatal vẫn chặn bản sửa.
 * Không thêm trường mới vào JSON của form chấm.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class TeacherReviewLatexWarningTest {

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

    private MockMvc mockMvc;
    private QuestionTemplate template;
    private ExperimentRun productionRun;
    private UserAccount teacher;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbcTemplate, "TeacherReviewLatexWarningTest.setUp");

        Subject subject = subjectRepository.save(new Subject("Toán Kiểm Thử LaTeX"));
        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(subject.getId());
        tpl.setGradeLevel(8);
        tpl.setTopic("Cảnh báo LaTeX");
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

        teacher = userRepository.save(new UserAccount("GV LaTeX", "latex.test@ptit.edu.vn", UserRole.TEACHER));
    }

    private GeneratedQuestion createQcPassedQuestion(String questionText) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setTemplateId(template.getId());
        gq.setRunId(productionRun.getId());
        gq.setQuestionText(questionText);
        gq.setOptionA("$30^\\circ$");
        gq.setOptionB("$45^\\circ$");
        gq.setOptionC("$60^\\circ$");
        gq.setOptionD("$90^\\circ$");
        gq.setCorrectOption(Option.C);
        gq.setExplanation("Tam giác đều có mỗi góc bằng $60^\\circ$.");
        gq.setRawLlmOutput("{}");
        gq.setLlmDifficulty(Difficulty.EASY);
        gq.setParseSuccess(true);
        gq.setStatus(QuestionStatus.QC_PASSED);
        gq.setCreatedAt(LocalDateTime.now());
        return questionRepository.save(gq);
    }

    private String approveWithEditBody(String editedQuestionTextJson) {
        return """
                {"reviewerId": %d, "reviewPurpose": "production", "reviewerAnswer": "C",
                 "formatOk": true, "difficultyLabel": "easy", "decision": "approved_with_edit",
                 "finalOption": "C", "editedQuestionText": %s}
                """.formatted(teacher.getId(), editedQuestionTextJson);
    }

    @Test
    void reviewFormTreatsWarningOnlyQuestionAsLatexValid() throws Exception {
        GeneratedQuestion q = createQcPassedQuestion("Cho tam giác đều $ABC$. Tính $\\widehat{ABC}$.");

        mockMvc.perform(get("/api/reviews/questions/" + q.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latexValid").value(true))
                .andExpect(jsonPath("$.latexErrors").isArray())
                .andExpect(jsonPath("$.latexErrors").isEmpty())
                .andExpect(jsonPath("$.latexWarnings").doesNotExist())
                .andExpect(jsonPath("$.explanation").doesNotExist())
                .andExpect(jsonPath("$.correctOption").doesNotExist());
    }

    @Test
    void approvedWithEditIsNotBlockedByWarningOnlyEdit() throws Exception {
        GeneratedQuestion q = createQcPassedQuestion("Cho tam giác đều $ABC$. Tính góc $B$.");

        mockMvc.perform(post("/api/reviews/questions/" + q.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveWithEditBody("\"Cho tam giác đều $ABC$. Tính $\\\\widehat{ABC}$.\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("approved_with_edit"))
                .andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.questionStatus").value("in_bank"));

        assertEquals(QuestionStatus.IN_BANK, questionRepository.findById(q.getId()).orElseThrow().getStatus());
    }

    @Test
    void approvedWithEditIsStillBlockedByFatalLatexError() throws Exception {
        GeneratedQuestion q = createQcPassedQuestion("Cho tam giác đều $ABC$. Tính góc $B$.");

        mockMvc.perform(post("/api/reviews/questions/" + q.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveWithEditBody("\"Cho tam giác đều $ABC. Tính góc $B$.\"")))
                .andExpect(status().isBadRequest());

        assertEquals(QuestionStatus.QC_PASSED, questionRepository.findById(q.getId()).orElseThrow().getStatus());
    }
}
