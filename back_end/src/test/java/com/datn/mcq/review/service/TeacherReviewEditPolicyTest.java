package com.datn.mcq.review.service;

import com.datn.mcq.bank.entity.BankItem;
import com.datn.mcq.bank.repository.BankItemRepository;
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
import com.datn.mcq.review.entity.TeacherReview;
import com.datn.mcq.review.repository.TeacherReviewRepository;
import com.datn.mcq.user.entity.UserAccount;
import com.datn.mcq.user.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quyết định 2 (22/10): edit_distance chỉ tính trên question_text và 4 phương án; request có editedExplanation
 * khác null bị từ chối 400 với thông điệp trung tính; teacher_review.edited_explanation luôn NULL.
 * Ngân hàng vẫn chép lời giải LLM như trước (không đổi cách chép lời giải).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class TeacherReviewEditPolicyTest {

    private static final String LLM_EXPLANATION = "LỜI GIẢI GỐC BÍ MẬT: nhân hai vế với $2$.";

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
    private TeacherReviewRepository teacherReviewRepository;
    @Autowired
    private BankItemRepository bankItemRepository;
    @Autowired
    private UserAccountRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private QuestionTemplate template;
    private UserAccount teacher;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbcTemplate, "TeacherReviewEditPolicyTest.setUp");
        Subject subject = subjectRepository.save(new Subject("Toán Sửa Bài"));
        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(subject.getId());
        tpl.setGradeLevel(8);
        tpl.setTopic("Sửa bài");
        tpl.setSourceType(SourceType.TEXT);
        tpl.setSourceQuestion("Câu mẫu?");
        tpl.setIsActive(true);
        template = templateRepository.save(tpl);
        teacher = userRepository.save(new UserAccount("GV Sửa Bài", "edit.test@ptit.edu.vn", UserRole.TEACHER));
    }

    private GeneratedQuestion question(Purpose purpose, String explanation) {
        ExperimentRun run = new ExperimentRun();
        run.setSubjectId(template.getSubjectId());
        run.setPurpose(purpose);
        run.setStrategy(Strategy.FEW_SHOT_STRUCTURED);
        run.setModelName("gemini-test");
        run.setTemperature(0.7);
        run.setPromptText("Test prompt");
        run.setStatus("completed");
        run.setStartedAt(LocalDateTime.now());
        run = runRepository.save(run);

        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setTemplateId(template.getId());
        gq.setRunId(run.getId());
        gq.setQuestionText("Nghiệm của $\\frac{x}{2} = 1$ là:");
        gq.setOptionA("$x = 2$");
        gq.setOptionB("$x = 1$");
        gq.setOptionC("$x = 4$");
        gq.setOptionD("$x = 0$");
        gq.setCorrectOption(Option.A);
        gq.setExplanation(explanation);
        gq.setRawLlmOutput("{}");
        gq.setLlmDifficulty(Difficulty.EASY);
        gq.setParseSuccess(true);
        gq.setStatus(QuestionStatus.QC_PASSED);
        gq.setCreatedAt(LocalDateTime.now());
        return questionRepository.save(gq);
    }

    private String body(Purpose purpose, String decision, String extraJson) {
        String answer = decision.equals("rejected")
                ? "\"reviewerAnswer\": \"none\", \"reasonCodes\": [\"content_unclear\"]"
                : "\"reviewerAnswer\": \"A\", \"finalOption\": \"A\"";
        return "{\"reviewerId\": " + teacher.getId() + ", \"reviewPurpose\": \"" + purpose.getDbValue() + "\", "
                + answer + ", \"formatOk\": true, \"difficultyLabel\": \"easy\", \"decision\": \"" + decision + "\""
                + extraJson + "}";
    }

    @Test
    void anyNonNullEditedExplanationIsRejectedWithNeutralMessage() throws Exception {
        GeneratedQuestion prod = question(Purpose.PRODUCTION, LLM_EXPLANATION);
        GeneratedQuestion exp = question(Purpose.EXPERIMENT, LLM_EXPLANATION);

        for (GeneratedQuestion q : List.of(prod, exp)) {
            Purpose purpose = runRepository.findById(q.getRunId()).orElseThrow().getPurpose();
            for (String decision : List.of("approved_with_edit", "approved_as_is", "rejected")) {
                for (String edited : List.of("\"Lời giải mới $x = 2$.\"", "\"\"")) {
                    String json = mockMvc.perform(post("/api/reviews/questions/" + q.getId())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body(purpose, decision, ", \"editedExplanation\": " + edited)))
                            .andExpect(status().isBadRequest())
                            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
                    String message = objectMapper.readTree(json).get("error").asText();
                    assertFalse(message.contains("BÍ MẬT"), "Không được lộ lời giải gốc: " + message);
                    assertFalse(message.contains("gốc"), "Thông điệp phải trung tính: " + message);
                }
            }
            assertTrue(teacherReviewRepository.findByQuestionIdAndReviewerId(q.getId(), teacher.getId()).isEmpty());
            assertEquals(QuestionStatus.QC_PASSED, questionRepository.findById(q.getId()).orElseThrow().getStatus());
        }
        assertTrue(bankItemRepository.findBySourceQuestionId(prod.getId()).isEmpty());
    }

    @Test
    void editDistanceCountsOnlyQuestionTextAndOptionsAndEditedExplanationStaysNull() throws Exception {
        GeneratedQuestion prod = question(Purpose.PRODUCTION, LLM_EXPLANATION);

        // Thêm 3 ký tự " gì" vào thân câu và 1 ký tự vào phương án A: khoảng cách = 4.
        mockMvc.perform(post("/api/reviews/questions/" + prod.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Purpose.PRODUCTION, "approved_with_edit",
                                ", \"editedQuestionText\": \"Nghiệm của $\\\\frac{x}{2} = 1$ là gì:\","
                                        + " \"editedOptionA\": \"$x = 2.$\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.editDistance").value(4));

        TeacherReview review = teacherReviewRepository
                .findByQuestionIdAndReviewerId(prod.getId(), teacher.getId()).orElseThrow();
        assertEquals(4, review.getEditDistance());
        assertNull(review.getEditedExplanation(), "teacher_review.edited_explanation phải luôn NULL");

        BankItem item = bankItemRepository.findBySourceQuestionId(prod.getId()).orElseThrow();
        assertEquals(LLM_EXPLANATION, item.getExplanation(), "Ngân hàng vẫn chép lời giải LLM như trước");
    }

    @Test
    void hiddenExplanationNeverBlocksOrLeaksThroughEditValidation() throws Exception {
        // Lời giải gốc có lỗi LaTeX fatal (số dấu $ lẻ); giáo viên không thấy và không sửa được lời giải,
        // nên bản sửa hợp lệ của đề không được bị chặn vì lời giải ẩn.
        GeneratedQuestion prod = question(Purpose.PRODUCTION, "Lời giải hỏng $x = 2");

        mockMvc.perform(post("/api/reviews/questions/" + prod.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Purpose.PRODUCTION, "approved_with_edit",
                                ", \"editedQuestionText\": \"Nghiệm của $\\\\frac{x}{2} = 1$ bằng:\"")))
                .andExpect(status().isOk());

        assertNull(teacherReviewRepository.findByQuestionIdAndReviewerId(prod.getId(), teacher.getId())
                .orElseThrow().getEditedExplanation());
        // Việc 2 (22/10): lời giải còn lỗi định dạng nghiêm trọng thì ngân hàng lưu NULL.
        assertNull(bankItemRepository.findBySourceQuestionId(prod.getId()).orElseThrow().getExplanation());
    }

    @Test
    void approvedAsIsAlsoKeepsEditedExplanationNull() throws Exception {
        GeneratedQuestion prod = question(Purpose.PRODUCTION, LLM_EXPLANATION);

        mockMvc.perform(post("/api/reviews/questions/" + prod.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Purpose.PRODUCTION, "approved_as_is", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edited").value(false));

        TeacherReview review = teacherReviewRepository
                .findByQuestionIdAndReviewerId(prod.getId(), teacher.getId()).orElseThrow();
        assertNull(review.getEditedExplanation());
        assertNull(review.getEditDistance());
    }
}
