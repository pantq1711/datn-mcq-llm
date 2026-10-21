package com.datn.mcq.review.controller;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.repository.QuestionTemplateRepository;
import com.datn.mcq.catalog.repository.SubjectRepository;
import com.datn.mcq.bank.repository.BankItemRepository;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.CheckType;
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
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hợp đồng chấm mù (quyết định 21/10 mục 2 và 5, docs/ai-development-rules.md):
 * <ul>
 *   <li>Danh sách chấm của cả production và experiment loại duplicate và format_failed;
 *       experiment KHÔNG lọc theo kết quả answer_check.</li>
 *   <li>Detail/submit của câu duplicate hoặc format_failed trả 404 "Không tìm thấy câu hỏi", giống câu không tồn tại.</li>
 *   <li>List/detail/submit không trả đáp án LLM, lời giải, độ khó LLM, status, latexValid, latexErrors hay kết quả QC.
 *       Riêng response submit production được giữ questionStatus và bankItemId.</li>
 * </ul>
 * Kiểm qua HTTP thật trên datn_mcq_test, quét đệ quy mọi key của JSON.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class BlindReviewContractTest {

    private static final String NOT_FOUND = "Không tìm thấy câu hỏi";

    /** Key không bao giờ được xuất hiện ở bất kỳ tầng nào của JSON chấm mù. */
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "correctOption", "correct_option", "answerCorrect", "answer_correct",
            "explanation", "llmDifficulty", "llm_difficulty", "difficultyJudgeModel",
            "status", "latexValid", "latexErrors", "latexWarnings",
            "judgeAnswer", "judge_answer", "judgeModel", "judge_model",
            "similarityScore", "similarity_score", "comparedToQuestionId", "thresholdUsed",
            "dedup", "dedupStatus", "answerCheckStatus", "answerCheckPassed", "qcReport",
            "formatPassed", "formatErrors", "solverReasoning", "strategy", "rawLlmOutput");

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
    private QualityCheckResultRepository qcrRepository;
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

    private GeneratedQuestion prodQcPassed;
    private GeneratedQuestion prodGenerated;
    private GeneratedQuestion prodDuplicate;
    private GeneratedQuestion prodFormatFailed;
    private GeneratedQuestion expQcPassed;
    private GeneratedQuestion expGeneratedSolverDisagreed;
    private GeneratedQuestion expGeneratedNoAnswerCheck;
    private GeneratedQuestion expDuplicate;
    private GeneratedQuestion expFormatFailed;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbcTemplate, "BlindReviewContractTest.setUp");
        Subject subject = subjectRepository.save(new Subject("Toán Chấm Mù"));
        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(subject.getId());
        tpl.setGradeLevel(8);
        tpl.setTopic("Hợp đồng chấm mù");
        tpl.setSourceType(SourceType.TEXT);
        tpl.setSourceQuestion("Câu mẫu?");
        tpl.setIsActive(true);
        template = templateRepository.save(tpl);
        teacher = userRepository.save(new UserAccount("GV Chấm Mù", "blind.test@ptit.edu.vn", UserRole.TEACHER));

        ExperimentRun productionRun = run(subject, Purpose.PRODUCTION);
        ExperimentRun experimentRun = run(subject, Purpose.EXPERIMENT);

        prodQcPassed = question(productionRun, QuestionStatus.QC_PASSED, "Tính $1 + 1$");
        prodGenerated = question(productionRun, QuestionStatus.GENERATED, "Tính $1 + 2$");
        prodDuplicate = question(productionRun, QuestionStatus.DUPLICATE, "Tính $1 + 3$");
        prodFormatFailed = question(productionRun, QuestionStatus.FORMAT_FAILED, "Tính $1 + 4$");
        expQcPassed = question(experimentRun, QuestionStatus.QC_PASSED, "Tính $2 + 1$");
        expGeneratedSolverDisagreed = question(experimentRun, QuestionStatus.GENERATED, "Tính $2 + 2$");
        expGeneratedNoAnswerCheck = question(experimentRun, QuestionStatus.GENERATED, "Tính $2 + 3$");
        expDuplicate = question(experimentRun, QuestionStatus.DUPLICATE, "Tính $2 + 4$");
        expFormatFailed = question(experimentRun, QuestionStatus.FORMAT_FAILED, "Tính $2 + 5$");

        // Solver lệch đáp án: kết quả answer_check hợp lệ nhưng không khớp (câu vẫn ở generated).
        QualityCheckResult disagreed = new QualityCheckResult();
        disagreed.setQuestionId(expGeneratedSolverDisagreed.getId());
        disagreed.setCheckType(CheckType.ANSWER_CHECK);
        disagreed.setPassed(false);
        disagreed.setJudgeAnswer(AnswerChoice.C);
        disagreed.setJudgeModel("solver-test-model");
        disagreed.setCheckedAt(LocalDateTime.now());
        qcrRepository.save(disagreed);
    }

    private ExperimentRun run(Subject subject, Purpose purpose) {
        ExperimentRun r = new ExperimentRun();
        r.setSubjectId(subject.getId());
        r.setPurpose(purpose);
        r.setStrategy(Strategy.FEW_SHOT_STRUCTURED);
        r.setModelName("gemini-test");
        r.setTemperature(0.7);
        r.setPromptText("Test prompt");
        r.setStatus("completed");
        r.setStartedAt(LocalDateTime.now());
        return runRepository.save(r);
    }

    private GeneratedQuestion question(ExperimentRun r, QuestionStatus questionStatus, String stem) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setTemplateId(template.getId());
        gq.setRunId(r.getId());
        gq.setQuestionText(stem);
        gq.setOptionA("$1$");
        gq.setOptionB("$2$");
        gq.setOptionC("$3$");
        gq.setOptionD("$4$");
        gq.setCorrectOption(Option.B);
        gq.setExplanation("LỜI GIẢI BÍ MẬT CỦA LLM");
        gq.setRawLlmOutput("{}");
        gq.setLlmDifficulty(Difficulty.HARD);
        gq.setParseSuccess(true);
        gq.setStatus(questionStatus);
        gq.setCreatedAt(LocalDateTime.now());
        return questionRepository.save(gq);
    }

    private String approveBody(Purpose purpose) {
        return """
                {"reviewerId": %d, "reviewPurpose": "%s", "reviewerAnswer": "B", "formatOk": true,
                 "difficultyLabel": "easy", "decision": "approved_as_is", "finalOption": "B"}
                """.formatted(teacher.getId(), purpose.getDbValue());
    }

    private String rejectBody(Purpose purpose) {
        return """
                {"reviewerId": %d, "reviewPurpose": "%s", "reviewerAnswer": "none", "formatOk": false,
                 "difficultyLabel": "easy", "decision": "rejected", "reasonCodes": ["answer_none_correct"]}
                """.formatted(teacher.getId(), purpose.getDbValue());
    }

    private Purpose purposeOf(GeneratedQuestion q) {
        return runRepository.findById(q.getRunId()).orElseThrow().getPurpose();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Tập mọi tên key ở mọi tầng của JSON (object lồng nhau, mảng). */
    private static Set<String> allKeys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        collectKeys(node, keys);
        return keys;
    }

    private static void collectKeys(JsonNode node, Set<String> keys) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                keys.add(name);
                collectKeys(node.get(name), keys);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectKeys(child, keys);
            }
        }
    }

    private void assertNoForbiddenKeys(String json, Set<String> extraForbidden) throws Exception {
        Set<String> keys = allKeys(objectMapper.readTree(json));
        Set<String> leaked = new TreeSet<>(keys);
        Set<String> forbidden = new HashSet<>(FORBIDDEN_KEYS);
        forbidden.addAll(extraForbidden);
        leaked.retainAll(forbidden);
        assertTrue(leaked.isEmpty(), "Rò rỉ key " + leaked + " trong: " + json);
        assertFalse(json.contains("LỜI GIẢI BÍ MẬT"), "Rò rỉ lời giải LLM trong: " + json);
    }

    private Set<Integer> listedIds(String json) throws Exception {
        Set<Integer> ids = new TreeSet<>();
        for (JsonNode item : objectMapper.readTree(json)) {
            ids.add(item.get("id").asInt());
        }
        return ids;
    }

    // ---------- Danh sách ----------

    // Sửa 2026-10-21 (quyết định 1 lượt 22/10): danh sách production chỉ gồm câu qc_passed chưa được chấm;
    // trước đây câu production generated cũng nằm trong danh sách. Experiment giữ nguyên chính sách.
    @Test
    void listShowsOnlyQcPassedForProductionAndAllButDuplicateOrFormatFailedForExperiment() throws Exception {
        GeneratedQuestion prodInBank = question(prodQcPassedRun(), QuestionStatus.QC_PASSED, "Tính $1 + 5$");
        prodInBank.setStatus(QuestionStatus.IN_BANK);
        prodInBank.setFinalDifficulty(Difficulty.EASY); // chk_gq_in_bank_complete
        prodInBank = questionRepository.save(prodInBank);
        GeneratedQuestion prodRejected = question(prodQcPassedRun(), QuestionStatus.REJECTED, "Tính $1 + 6$");

        Set<Integer> ids = listedIds(body(mockMvc.perform(get("/api/reviews/questions"))
                .andExpect(status().isOk()).andReturn()));

        for (GeneratedQuestion shown : List.of(prodQcPassed, expQcPassed,
                expGeneratedSolverDisagreed, expGeneratedNoAnswerCheck)) {
            assertTrue(ids.contains(shown.getId()), "Thiếu câu id=" + shown.getId());
        }
        for (GeneratedQuestion hidden : List.of(prodGenerated, prodInBank, prodRejected,
                prodDuplicate, prodFormatFailed, expDuplicate, expFormatFailed)) {
            assertFalse(ids.contains(hidden.getId()), "Câu id=" + hidden.getId() + " không được vào danh sách chấm");
        }
    }

    @Test
    void productionQuestionLeavesTheListOnceItHasBeenReviewed() throws Exception {
        GeneratedQuestion toReject = question(prodQcPassedRun(), QuestionStatus.QC_PASSED, "Tính $1 + 7$");

        mockMvc.perform(post("/api/reviews/questions/" + prodQcPassed.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(approveBody(Purpose.PRODUCTION)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/reviews/questions/" + toReject.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(rejectBody(Purpose.PRODUCTION)))
                .andExpect(status().isOk());

        Set<Integer> ids = listedIds(body(mockMvc.perform(get("/api/reviews/questions"))
                .andExpect(status().isOk()).andReturn()));
        assertFalse(ids.contains(prodQcPassed.getId()), "Câu đã duyệt không còn trong danh sách");
        assertFalse(ids.contains(toReject.getId()), "Câu đã từ chối không còn trong danh sách");
    }

    private ExperimentRun prodQcPassedRun() {
        return runRepository.findById(prodQcPassed.getRunId()).orElseThrow();
    }

    @Test
    void experimentListIsNotFilteredByAnswerCheckOutcome() throws Exception {
        Set<Integer> ids = listedIds(body(mockMvc.perform(get("/api/reviews/questions"))
                .andExpect(status().isOk()).andReturn()));
        assertTrue(ids.contains(expGeneratedSolverDisagreed.getId()), "Câu Solver lệch vẫn phải được chấm (RQ2b)");
        assertTrue(ids.contains(expGeneratedNoAnswerCheck.getId()), "Câu chưa có answer_check vẫn phải được chấm");
    }

    @Test
    void statusQueryParameterCannotBeUsedToInferQcResults() throws Exception {
        Set<Integer> unfiltered = listedIds(body(mockMvc.perform(get("/api/reviews/questions"))
                .andExpect(status().isOk()).andReturn()));
        for (String probe : List.of("qc_passed", "generated", "duplicate", "format_failed", "in_bank")) {
            Set<Integer> probed = listedIds(body(mockMvc.perform(get("/api/reviews/questions").param("status", probe))
                    .andExpect(status().isOk()).andReturn()));
            assertEquals(unfiltered, probed, "Lọc ?status=" + probe + " không được làm đổi tập câu");
        }
    }

    @Test
    void listItemsNeverContainSensitiveKeys() throws Exception {
        assertNoForbiddenKeys(body(mockMvc.perform(get("/api/reviews/questions"))
                .andExpect(status().isOk()).andReturn()), Set.of("questionStatus"));
    }

    // ---------- Chi tiết ----------

    @Test
    void detailNeverContainsSensitiveKeysForBothPurposes() throws Exception {
        for (GeneratedQuestion q : List.of(prodQcPassed, prodGenerated, expQcPassed, expGeneratedSolverDisagreed)) {
            String json = body(mockMvc.perform(get("/api/reviews/questions/" + q.getId()))
                    .andExpect(status().isOk()).andReturn());
            assertNoForbiddenKeys(json, Set.of("questionStatus"));
            assertEquals(purposeOf(q).getDbValue(), objectMapper.readTree(json).get("reviewPurpose").asText());
        }
    }

    @Test
    void detailOfDuplicateOrFormatFailedIs404LikeAMissingQuestion() throws Exception {
        String missing = body(mockMvc.perform(get("/api/reviews/questions/" + Integer.MAX_VALUE))
                .andExpect(status().isNotFound()).andReturn());
        assertEquals(NOT_FOUND, objectMapper.readTree(missing).get("error").asText());

        for (GeneratedQuestion hidden : List.of(prodDuplicate, prodFormatFailed, expDuplicate, expFormatFailed)) {
            String json = body(mockMvc.perform(get("/api/reviews/questions/" + hidden.getId()))
                    .andExpect(status().isNotFound()).andReturn());
            assertEquals(missing, json, "Câu ẩn phải trả y hệt câu không tồn tại");
        }
    }

    // ---------- Nộp đánh giá ----------

    @Test
    void submitForDuplicateOrFormatFailedIs404AndChangesNothing() throws Exception {
        for (GeneratedQuestion hidden : List.of(prodDuplicate, prodFormatFailed, expDuplicate, expFormatFailed)) {
            Purpose purpose = purposeOf(hidden);
            for (String requestBody : List.of(approveBody(purpose), rejectBody(purpose))) {
                String json = body(mockMvc.perform(post("/api/reviews/questions/" + hidden.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                        .andExpect(status().isNotFound()).andReturn());
                assertEquals(NOT_FOUND, objectMapper.readTree(json).get("error").asText());
            }
            assertTrue(teacherReviewRepository.findByQuestionIdAndReviewerId(hidden.getId(), teacher.getId()).isEmpty(),
                    "Không được lưu review cho câu id=" + hidden.getId());
            assertEquals(hidden.getStatus(), questionRepository.findById(hidden.getId()).orElseThrow().getStatus());
            assertTrue(bankItemRepository.findBySourceQuestionId(hidden.getId()).isEmpty());
        }
    }

    @Test
    void productionSubmitResponseKeepsQuestionStatusAndBankItemButNoQcData() throws Exception {
        String json = body(mockMvc.perform(post("/api/reviews/questions/" + prodQcPassed.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(approveBody(Purpose.PRODUCTION)))
                .andExpect(status().isOk()).andReturn());
        assertNoForbiddenKeys(json, Set.of());
        JsonNode node = objectMapper.readTree(json);
        assertEquals("in_bank", node.get("questionStatus").asText());
        assertTrue(node.get("bankItemId").isNumber());
    }

    @Test
    void experimentSubmitIsAllowedRegardlessOfAnswerCheckAndHidesQuestionStatus() throws Exception {
        for (GeneratedQuestion q : List.of(expGeneratedSolverDisagreed, expGeneratedNoAnswerCheck, expQcPassed)) {
            String json = body(mockMvc.perform(post("/api/reviews/questions/" + q.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(approveBody(Purpose.EXPERIMENT)))
                    .andExpect(status().isOk()).andReturn());
            assertNoForbiddenKeys(json, Set.of("questionStatus"));
            assertTrue(teacherReviewRepository.findByQuestionIdAndReviewerId(q.getId(), teacher.getId()).isPresent());
            assertEquals(q.getStatus(), questionRepository.findById(q.getId()).orElseThrow().getStatus(),
                    "Chấm experiment không đổi trạng thái câu");
        }
    }

    @Test
    void approvingProductionQuestionNotYetQcPassedIsRefusedWithoutRevealingQcStatus() throws Exception {
        String json = body(mockMvc.perform(post("/api/reviews/questions/" + prodGenerated.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(approveBody(Purpose.PRODUCTION)))
                .andExpect(status().isBadRequest()).andReturn());
        String message = objectMapper.readTree(json).get("error").asText();
        for (String hint : List.of("generated", "qc_passed", "trạng thái", "QC", "kiểm tra chất lượng", "dedup")) {
            assertFalse(message.contains(hint), "Thông báo lỗi không được nhắc '" + hint + "': " + message);
        }
        assertTrue(teacherReviewRepository.findByQuestionIdAndReviewerId(prodGenerated.getId(), teacher.getId()).isEmpty());
        assertEquals(QuestionStatus.GENERATED, questionRepository.findById(prodGenerated.getId()).orElseThrow().getStatus());
        assertTrue(bankItemRepository.findBySourceQuestionId(prodGenerated.getId()).isEmpty());
    }
}
