package com.datn.mcq.review.service;

import com.datn.mcq.bank.entity.BankItem;
import com.datn.mcq.bank.repository.BankItemRepository;
import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.repository.QuestionTemplateRepository;
import com.datn.mcq.catalog.repository.SubjectRepository;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.ReasonCode;
import com.datn.mcq.enums.ReviewDecision;
import com.datn.mcq.enums.SourceType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.enums.UserRole;
import com.datn.mcq.exam.entity.Exam;
import com.datn.mcq.exam.entity.ExamQuestion;
import com.datn.mcq.exam.repository.ExamQuestionRepository;
import com.datn.mcq.exam.repository.ExamRepository;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.repository.ExperimentRunRepository;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.review.dto.BlindQuestionReviewDto;
import com.datn.mcq.review.dto.SubmitReviewRequest;
import com.datn.mcq.review.dto.SubmitReviewResponse;
import com.datn.mcq.review.entity.TeacherReview;
import com.datn.mcq.review.repository.TeacherReviewRepository;
import com.datn.mcq.user.entity.UserAccount;
import com.datn.mcq.user.repository.UserAccountRepository;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.datn.mcq.config.TestSafetyConfiguration;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestSafetyConfiguration.class)
class TeacherReviewSecurityAndIntegrityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TeacherReviewService teacherReviewService;

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
    private BankItemRepository bankItemRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamQuestionRepository examQuestionRepository;

    @Autowired
    private TeacherReviewRepository teacherReviewRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Subject testSubject;
    private QuestionTemplate testTemplate;
    private UserAccount testTeacher;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        cleanTestDatabaseFixtures();

        testSubject = subjectRepository.save(new Subject("Toán Kiểm Thử"));

        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(testSubject.getId());
        tpl.setGradeLevel(8);
        tpl.setTopic("Kiểm Thử Nghiệp Vụ");
        tpl.setSourceType(SourceType.TEXT);
        tpl.setSourceQuestion("Câu hỏi mẫu cho kiểm thử?");
        tpl.setIsActive(true);
        testTemplate = templateRepository.save(tpl);

        testTeacher = userRepository.save(
                new UserAccount("Giáo Viên Test", "teacher.test@ptit.edu.vn", UserRole.TEACHER));
    }

    /**
     * Dọn sạch dữ liệu các bảng nghiệp vụ trong database test theo đúng thứ tự khóa ngoại (con -> cha).
     * Kiểm tra lại Guard trước khi xóa để bảo đảm chỉ tác động lên 'datn_mcq_test'.
     * Không gắn @Transactional lên lớp test để giữ nguyên khả năng kiểm chứng transaction rollback thật của service.
     */
    private void cleanTestDatabaseFixtures() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(
                dataSource, "TeacherReviewSecurityAndIntegrityTest.cleanTestDatabaseFixtures");

        jdbcTemplate.update("DELETE FROM exam_question");
        jdbcTemplate.update("UPDATE exam SET parent_exam_id = NULL WHERE parent_exam_id IS NOT NULL");
        jdbcTemplate.update("DELETE FROM exam");
        jdbcTemplate.update("DELETE FROM bank_item");
        jdbcTemplate.update("DELETE FROM teacher_review_reason");
        jdbcTemplate.update("DELETE FROM teacher_review");
        jdbcTemplate.update("DELETE FROM label_assignment");
        jdbcTemplate.update("DELETE FROM dedup_gold_label");
        jdbcTemplate.update("DELETE FROM dedup_gold_pair");
        jdbcTemplate.update("DELETE FROM quality_check_result");
        jdbcTemplate.update("DELETE FROM question_embedding");
        jdbcTemplate.update("DELETE FROM generation_job");
        jdbcTemplate.update("DELETE FROM generated_question");
        jdbcTemplate.update("DELETE FROM experiment_run");
        jdbcTemplate.update("DELETE FROM question_template");
        jdbcTemplate.update("DELETE FROM subject");
        jdbcTemplate.update("DELETE FROM users");
    }

    private String generateUniqueExamCode() {
        // Bảng exam.exam_code giới hạn VARCHAR(20) NOT NULL UNIQUE -> "EX-" (3) + 12 hex = 15 ký tự
        return "EX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private ExperimentRun createRun(Purpose purpose) {
        ExperimentRun run = new ExperimentRun();
        run.setSubjectId(testSubject.getId());
        run.setPurpose(purpose);
        run.setStrategy(Strategy.FEW_SHOT_STRUCTURED);
        run.setModelName("gemini-test");
        run.setTemperature(0.7);
        run.setPromptText("Test prompt");
        run.setStatus("completed");
        run.setStartedAt(LocalDateTime.now());
        return runRepository.save(run);
    }

    private GeneratedQuestion createQuestion(Integer runId, QuestionStatus status, Option correctOption) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setTemplateId(testTemplate.getId());
        gq.setRunId(runId);
        gq.setQuestionText("Biểu thức $\\frac{x}{2} = 1$ có nghiệm là:");
        gq.setOptionA("$x = 2$");
        gq.setOptionB("$x = 1$");
        gq.setOptionC("$x = 4$");
        gq.setOptionD("$x = 0$");
        gq.setCorrectOption(correctOption);
        gq.setExplanation("Nhân cả hai vế với 2: $x = 2$. Do đó phương án A đúng.");
        gq.setRawLlmOutput("{\"question_text\":\"...\"}");
        gq.setLlmDifficulty(Difficulty.EASY);
        gq.setParseSuccess(true);
        gq.setStatus(status);
        gq.setCreatedAt(LocalDateTime.now());
        return questionRepository.save(gq);
    }

    @Test
    void blindReviewForExperimentMustHideExplanationStatusAndQcResultsInJsonPayload() throws Exception {
        ExperimentRun expRun = createRun(Purpose.EXPERIMENT);
        GeneratedQuestion q = createQuestion(expRun.getId(), QuestionStatus.QC_PASSED, Option.A);

        // Kiểm tra qua MockMvc HTTP thật
        mockMvc.perform(get("/api/reviews/questions/" + q.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewPurpose").value("experiment"))
                .andExpect(jsonPath("$.explanation").doesNotExist())
                .andExpect(jsonPath("$.correctOption").doesNotExist())
                .andExpect(jsonPath("$.correct_option").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.latexValid").doesNotExist())
                .andExpect(jsonPath("$.latexErrors").doesNotExist());
    }

    @Test
    void reviewForProductionMustHideExplanationAndCorrectOptionInInitialBlindForm() throws Exception {
        ExperimentRun prodRun = createRun(Purpose.PRODUCTION);
        GeneratedQuestion q = createQuestion(prodRun.getId(), QuestionStatus.QC_PASSED, Option.A);

        // Theo quyết định Q-1: form ban đầu cho production cũng tuyệt đối không trả explanation.
        // Sửa 2026-10-21 (quyết định 21/10 mục 2): API chấm không trả status, latexValid, latexErrors
        // cho cả production (trước đây test này kỳ vọng production có ba trường đó).
        mockMvc.perform(get("/api/reviews/questions/" + q.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewPurpose").value("production"))
                .andExpect(jsonPath("$.explanation").doesNotExist())
                .andExpect(jsonPath("$.correctOption").doesNotExist())
                .andExpect(jsonPath("$.correct_option").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.latexValid").doesNotExist())
                .andExpect(jsonPath("$.latexErrors").doesNotExist());
    }

    @Test
    void submitReviewResponseMustNeverExposeAnswerCorrectInSerializedJson() throws Exception {
        ExperimentRun prodRun = createRun(Purpose.PRODUCTION);
        GeneratedQuestion q = createQuestion(prodRun.getId(), QuestionStatus.QC_PASSED, Option.A);

        SubmitReviewRequest req = new SubmitReviewRequest(
                testTeacher.getId(),
                Purpose.PRODUCTION,
                AnswerChoice.A,
                true,
                Difficulty.EASY,
                ReviewDecision.APPROVED_AS_IS,
                Option.A,
                null, null, null, null, null, null,
                List.of(),
                null
        );

        String requestBody = objectMapper.writeValueAsString(req);

        // Kiểm tra qua MockMvc HTTP POST thật
        mockMvc.perform(post("/api/reviews/questions/" + q.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerCorrect").doesNotExist())
                .andExpect(jsonPath("$.answer_correct").doesNotExist())
                .andExpect(jsonPath("$.explanation").doesNotExist());
    }

    @Test
    void listQuestionsForReviewMustNotLeakExperimentQcStatusViaFilter() throws Exception {
        ExperimentRun expRun = createRun(Purpose.EXPERIMENT);
        GeneratedQuestion expQcPassed = createQuestion(expRun.getId(), QuestionStatus.QC_PASSED, Option.A);
        GeneratedQuestion expFormatFailed = createQuestion(expRun.getId(), QuestionStatus.FORMAT_FAILED, Option.B);

        ExperimentRun prodRun = createRun(Purpose.PRODUCTION);
        GeneratedQuestion prodQcPassed = createQuestion(prodRun.getId(), QuestionStatus.QC_PASSED, Option.C);
        GeneratedQuestion prodFormatFailed = createQuestion(prodRun.getId(), QuestionStatus.FORMAT_FAILED, Option.D);

        // Sửa 2026-10-21 (quyết định 21/10 mục 2 và 5): trước đây production lọc được theo ?status=
        // và câu format_failed vẫn vào danh sách. Nay: tham số status không làm đổi tập câu của bất kỳ purpose nào,
        // format_failed bị loại khỏi vòng chấm của cả hai purpose, không item nào có status hay latexValid.
        for (String probe : List.of("qc_passed", "format_failed")) {
            mockMvc.perform(get("/api/reviews/questions").param("status", probe))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + expQcPassed.getId() + ")]").exists())
                    .andExpect(jsonPath("$[?(@.id == " + prodQcPassed.getId() + ")]").exists())
                    .andExpect(jsonPath("$[?(@.id == " + expFormatFailed.getId() + ")]").doesNotExist())
                    .andExpect(jsonPath("$[?(@.id == " + prodFormatFailed.getId() + ")]").doesNotExist())
                    .andExpect(jsonPath("$[*].status").doesNotExist())
                    .andExpect(jsonPath("$[*].latexValid").doesNotExist())
                    // Toàn bộ payload danh sách không có correctOption hay explanation
                    .andExpect(jsonPath("$[*].explanation").doesNotExist())
                    .andExpect(jsonPath("$[*].correctOption").doesNotExist())
                    .andExpect(jsonPath("$[*].correct_option").doesNotExist());
        }
    }

    @Test
    void cannotPromoteExperimentQuestionToBankItemByFakingReviewPurpose() {
        ExperimentRun expRun = createRun(Purpose.EXPERIMENT);
        GeneratedQuestion q = createQuestion(expRun.getId(), QuestionStatus.QC_PASSED, Option.A);

        // Client cố ý gửi reviewPurpose = PRODUCTION cho câu thuộc experiment
        SubmitReviewRequest fakedReq = new SubmitReviewRequest(
                testTeacher.getId(),
                Purpose.PRODUCTION,
                AnswerChoice.A,
                true,
                Difficulty.EASY,
                ReviewDecision.APPROVED_AS_IS,
                Option.A,
                null, null, null, null, null, null,
                List.of(),
                null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> teacherReviewService.submitReview(q.getId(), fakedReq));
        assertTrue(ex.getMessage().contains("reviewPurpose") || ex.getMessage().contains("nguồn gốc"));

        // Xác minh không có bản ghi nào lọt vào bank_item
        Optional<BankItem> itemOpt = bankItemRepository.findBySourceQuestionId(q.getId());
        assertTrue(itemOpt.isEmpty(), "Câu hỏi experiment không được phép chui vào bank_item");
    }

    @Test
    void cannotApproveQuestionIntoBankIfQcNotPassed() {
        ExperimentRun prodRun = createRun(Purpose.PRODUCTION);
        // Sửa 2026-10-21: câu format_failed nay không vào vòng chấm (404, xem BlindReviewContractTest),
        // nên dùng câu generated (chưa qua QC) để giữ ý của test. Thông báo lỗi không được nhắc trạng thái QC.
        GeneratedQuestion q = createQuestion(prodRun.getId(), QuestionStatus.GENERATED, Option.A);

        SubmitReviewRequest req = new SubmitReviewRequest(
                testTeacher.getId(),
                Purpose.PRODUCTION,
                AnswerChoice.A,
                true,
                Difficulty.EASY,
                ReviewDecision.APPROVED_AS_IS,
                Option.A,
                null, null, null, null, null, null,
                List.of(),
                null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> teacherReviewService.submitReview(q.getId(), req));
        assertTrue(ex.getMessage().contains("chưa đủ điều kiện để duyệt vào ngân hàng"), ex.getMessage());
        assertFalse(ex.getMessage().contains("generated"), "Không được lộ trạng thái QC: " + ex.getMessage());

        Optional<BankItem> itemOpt = bankItemRepository.findBySourceQuestionId(q.getId());
        assertTrue(itemOpt.isEmpty(), "Câu chưa qua QC không được tạo bank_item");
    }

    @Test
    void repeatedReviewSubmissionForExperimentMustBeRejectedToProtectCohenKappaLabels() {
        ExperimentRun expRun = createRun(Purpose.EXPERIMENT);
        GeneratedQuestion q = createQuestion(expRun.getId(), QuestionStatus.QC_PASSED, Option.A);

        SubmitReviewRequest req1 = new SubmitReviewRequest(
                testTeacher.getId(),
                Purpose.EXPERIMENT,
                AnswerChoice.A,
                true,
                Difficulty.EASY,
                ReviewDecision.APPROVED_AS_IS,
                Option.A,
                null, null, null, null, null, null,
                List.of(),
                null
        );

        teacherReviewService.submitReview(q.getId(), req1);

        // Nộp lần 2 cùng người chấm cho cùng câu thực nghiệm
        SubmitReviewRequest req2 = new SubmitReviewRequest(
                testTeacher.getId(),
                Purpose.EXPERIMENT,
                AnswerChoice.B,
                true,
                Difficulty.MEDIUM,
                ReviewDecision.REJECTED,
                null,
                null, null, null, null, null, null,
                List.of(ReasonCode.CONTENT_INACCURATE),
                null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> teacherReviewService.submitReview(q.getId(), req2));
        assertTrue(ex.getMessage().contains("Cohen's Kappa") || ex.getMessage().contains("ghi đè"));
    }

    @Test
    void switchingToRejectedInProductionMustCleanUpBankItemIfUnused() {
        ExperimentRun prodRun = createRun(Purpose.PRODUCTION);
        GeneratedQuestion q = createQuestion(prodRun.getId(), QuestionStatus.QC_PASSED, Option.A);

        // 1. Duyệt approved -> vào bank_item
        SubmitReviewRequest approveReq = new SubmitReviewRequest(
                testTeacher.getId(),
                Purpose.PRODUCTION,
                AnswerChoice.A,
                true,
                Difficulty.EASY,
                ReviewDecision.APPROVED_AS_IS,
                Option.A,
                null, null, null, null, null, null,
                List.of(),
                null
        );
        teacherReviewService.submitReview(q.getId(), approveReq);

        Optional<BankItem> itemOpt = bankItemRepository.findBySourceQuestionId(q.getId());
        assertTrue(itemOpt.isPresent(), "Phải có bank_item sau khi approved");

        // 2. Giáo viên đổi quyết định sang REJECTED -> bank_item phải được dọn dẹp
        SubmitReviewRequest rejectReq = new SubmitReviewRequest(
                testTeacher.getId(),
                Purpose.PRODUCTION,
                AnswerChoice.NONE,
                false,
                Difficulty.EASY,
                ReviewDecision.REJECTED,
                null,
                null, null, null, null, null, null,
                List.of(ReasonCode.ANSWER_NONE_CORRECT),
                null
        );
        teacherReviewService.submitReview(q.getId(), rejectReq);

        Optional<BankItem> itemAfterReject = bankItemRepository.findBySourceQuestionId(q.getId());
        assertTrue(itemAfterReject.isEmpty(), "bank_item mồ côi phải được xóa khi đổi sang REJECTED");

        GeneratedQuestion reloaded = questionRepository.findById(q.getId()).orElseThrow();
        assertEquals(QuestionStatus.REJECTED, reloaded.getStatus());
    }

    @Test
    void cannotRejectQuestionIfBankItemIsAlreadyUsedInExam() {
        ExperimentRun prodRun = createRun(Purpose.PRODUCTION);
        GeneratedQuestion q = createQuestion(prodRun.getId(), QuestionStatus.QC_PASSED, Option.A);

        SubmitReviewRequest approveReq = new SubmitReviewRequest(
                testTeacher.getId(),
                Purpose.PRODUCTION,
                AnswerChoice.A,
                true,
                Difficulty.EASY,
                ReviewDecision.APPROVED_AS_IS,
                Option.A,
                null, null, null, null, null, null,
                List.of(),
                null
        );
        teacherReviewService.submitReview(q.getId(), approveReq);

        BankItem item = bankItemRepository.findBySourceQuestionId(q.getId()).orElseThrow();

        // Tạo đề thi và gán bank_item này vào đề thi (exam_code tối đa 20 ký tự)
        Exam exam = new Exam();
        exam.setExamCode(generateUniqueExamCode());
        exam.setTitle("Đề thi thử nghiệm");
        exam.setSubjectId(testSubject.getId());
        exam.setCreatedBy(testTeacher.getId());
        exam.setStatus(ExamStatus.DRAFT);
        exam = examRepository.save(exam);

        ExamQuestion eq = new ExamQuestion();
        eq.setExamId(exam.getId());
        eq.setBankItemId(item.getId());
        eq.setPosition(1);
        eq.setOptionOrder("ABCD");
        examQuestionRepository.save(eq);

        // Cố tình đổi sang REJECTED khi câu đã có trong đề thi
        SubmitReviewRequest rejectReq = new SubmitReviewRequest(
                testTeacher.getId(),
                Purpose.PRODUCTION,
                AnswerChoice.NONE,
                false,
                Difficulty.EASY,
                ReviewDecision.REJECTED,
                null,
                null, null, null, null, null, null,
                List.of(ReasonCode.ANSWER_NONE_CORRECT),
                null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> teacherReviewService.submitReview(q.getId(), rejectReq));
        assertTrue(ex.getMessage().contains("exam") || ex.getMessage().contains("đề thi"));

        // Xác minh bank_item vẫn nguyên vẹn
        assertTrue(bankItemRepository.findBySourceQuestionId(q.getId()).isPresent());
    }

    /**
     * F4: response sau khi nộp review EXPERIMENT không được chứa trạng thái QC hay đáp án LLM.
     * Kiểm tra theo sự tồn tại của key trong JSON (không dùng jsonPath().doesNotExist(),
     * vì hàm đó vẫn cho qua khi key có mặt với giá trị null).
     */
    @Test
    void submitResponseForExperimentMustNotContainQcStatusOrLlmAnswerKeys() throws Exception {
        ExperimentRun expRun = createRun(Purpose.EXPERIMENT);
        // Đáp án LLM là A, người chấm chọn B -> answer_correct phải được lưu là FALSE
        GeneratedQuestion q = createQuestion(expRun.getId(), QuestionStatus.QC_PASSED, Option.A);

        SubmitReviewRequest req = new SubmitReviewRequest(
                testTeacher.getId(),
                Purpose.EXPERIMENT,
                AnswerChoice.B,
                true,
                Difficulty.MEDIUM,
                ReviewDecision.APPROVED_AS_IS,
                Option.B,
                null, null, null, null, null, null,
                List.of(),
                null
        );

        String body = mockMvc.perform(post("/api/reviews/questions/" + q.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        for (String forbidden : List.of(
                "questionStatus", "status", "answerCorrect", "answer_correct",
                "correctOption", "correct_option", "explanation",
                "llmDifficulty", "judgeAnswer", "judgeModel", "similarityScore", "latexValid")) {
            assertFalse(json.has(forbidden), "Response experiment không được chứa key '" + forbidden + "': " + body);
        }
        assertEquals("experiment", json.get("reviewPurpose").asText());
        assertEquals("B", json.get("reviewerAnswer").asText());

        // Dữ liệu phân tích vẫn được lưu đúng trong DB
        TeacherReview saved = teacherReviewRepository
                .findByQuestionIdAndReviewerId(q.getId(), testTeacher.getId())
                .orElseThrow();
        assertEquals(Purpose.EXPERIMENT, saved.getReviewPurpose());
        assertEquals(AnswerChoice.B, saved.getReviewerAnswer());
        assertFalse(saved.getAnswerCorrect(), "Server phải tự tính answer_correct = FALSE (B khác đáp án LLM A)");
        assertEquals(Difficulty.MEDIUM, saved.getDifficultyLabel());
        assertEquals(ReviewDecision.APPROVED_AS_IS, saved.getDecision());
        assertEquals(Option.B, saved.getFinalOption());
        assertEquals(json.get("reviewId").asInt(), saved.getId());

        // Chấm experiment không đổi trạng thái câu hỏi và không tạo bank_item
        assertEquals(QuestionStatus.QC_PASSED, questionRepository.findById(q.getId()).orElseThrow().getStatus());
        assertTrue(bankItemRepository.findBySourceQuestionId(q.getId()).isEmpty());
    }

    /**
     * F4: hợp đồng response PRODUCTION giữ nguyên, vẫn trả questionStatus sau khi duyệt.
     */
    @Test
    void submitResponseForProductionStillContainsQuestionStatus() throws Exception {
        ExperimentRun prodRun = createRun(Purpose.PRODUCTION);
        GeneratedQuestion q = createQuestion(prodRun.getId(), QuestionStatus.QC_PASSED, Option.A);

        SubmitReviewRequest req = new SubmitReviewRequest(
                testTeacher.getId(),
                Purpose.PRODUCTION,
                AnswerChoice.A,
                true,
                Difficulty.EASY,
                ReviewDecision.APPROVED_AS_IS,
                Option.A,
                null, null, null, null, null, null,
                List.of(),
                null
        );

        mockMvc.perform(post("/api/reviews/questions/" + q.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewPurpose").value("production"))
                .andExpect(jsonPath("$.questionStatus").value("in_bank"));
    }
}
