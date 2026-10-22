package com.datn.mcq.generation;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.repository.QuestionTemplateRepository;
import com.datn.mcq.catalog.repository.SubjectRepository;
import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.SourceType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Solver trả JSON có hai khóa "judge_answer": không có kết quả hợp lệ nên answer_check = ERROR,
 * câu giữ generated, không ghi dòng answer_check, và generate-sync vẫn trả 200.
 * Solver là LlmClient giả (không gọi mạng), phản hồi đi qua AnswerSolverChecker.parseAndCompare thật.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class SolverDuplicateJudgeAnswerTest {

    private static final String DUPLICATE_JUDGE_ANSWER =
            "{\"reasoning\": \"Giải được $x = 3$.\", \"judge_answer\": \"A\", \"judge_answer\": \"B\"}";

    /** Solver giả: chỉ trả lời qua callJsonModeWithSchema; mọi đường gọi khác bị chặn. */
    static final class DuplicateKeySolverClient extends LlmClient {
        int calls;

        DuplicateKeySolverClient() {
            super("FAKE_KEY_FOR_TESTS", "fake-solver-model");
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public LlmCallResult generateMcq(String prompt, String modelName, Strategy strategy, Double temperature) {
            throw new UnsupportedOperationException("Không được sinh câu trong test này");
        }

        @Override
        public LlmCallResult callJsonMode(String prompt, String modelName, Double temperature) {
            throw new UnsupportedOperationException("Solver phải dùng callJsonModeWithSchema");
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            calls++;
            return new LlmCallResult(DUPLICATE_JUDGE_ANSWER, 10, 1.0, 0.0, modelName);
        }
    }

    private static final DuplicateKeySolverClient SOLVER_CLIENT = new DuplicateKeySolverClient();

    @TestBean
    private AnswerSolverChecker answerSolverChecker;

    static AnswerSolverChecker answerSolverChecker() {
        return new AnswerSolverChecker(SOLVER_CLIENT, new PromptBuilder(), "fake-solver-model");
    }

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
    private GeneratedQuestionRepository questionRepository;
    @Autowired
    private QualityCheckResultRepository qcrRepository;

    private MockMvc mockMvc;
    private QuestionTemplate template;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbcTemplate, "SolverDuplicateJudgeAnswerTest.setUp");
        SOLVER_CLIENT.calls = 0;
        Subject subject = subjectRepository.save(new Subject("Toán Solver Khóa Trùng"));
        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(subject.getId());
        tpl.setGradeLevel(8);
        tpl.setTopic("Phương trình bậc nhất");
        tpl.setSourceType(SourceType.TEXT);
        tpl.setSourceQuestion("Câu mẫu?");
        tpl.setIsActive(true);
        template = templateRepository.save(tpl);
    }

    @Test
    void solverAnswerWithDuplicateJudgeAnswerIsErrorAndQuestionStaysGenerated() throws Exception {
        String generated = "{\"question_text\": \"Nghiệm của phương trình $2x = 6$ là:\","
                + " \"option_a\": \"$x = 2$\", \"option_b\": \"$x = 3$\", \"option_c\": \"$x = 4$\","
                + " \"option_d\": \"$x = 12$\", \"correct_option\": \"B\","
                + " \"explanation\": \"Chia hai vế cho $2$ được $x = 3$.\"}";
        String body = "{\"templateId\": " + template.getId() + ", \"runAnswerCheck\": true,"
                + " \"rawLlmOutputOverride\": " + SimpleJson.quote(generated) + "}";

        mockMvc.perform(post("/api/generation/generate-sync")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.status").value("generated"))
                .andExpect(jsonPath("$.qcReport.formatPassed").value(true))
                .andExpect(jsonPath("$.qcReport.dedupStatus").value("NO_DUPLICATE"))
                .andExpect(jsonPath("$.qcReport.answerCheckStatus").value("ERROR"))
                .andExpect(jsonPath("$.qcReport.answerCheckPassed").doesNotExist())
                .andExpect(jsonPath("$.qcReport.judgeAnswer").doesNotExist())
                .andExpect(jsonPath("$.qcReport.solverReasoning").value(containsString("\"judge_answer\"")));

        assertEquals(1, SOLVER_CLIENT.calls, "Solver phải được gọi đúng một lần");
        GeneratedQuestion created = questionRepository.findAllByOrderByIdDesc().get(0);
        assertEquals(QuestionStatus.GENERATED, created.getStatus());
        assertTrue(qcrRepository.findByQuestionIdAndCheckType(created.getId(), CheckType.ANSWER_CHECK).isEmpty(),
                "Không được ghi dòng answer_check khi Solver trả JSON khóa trùng");
        assertTrue(qcrRepository.findByQuestionIdAndCheckType(created.getId(), CheckType.FORMAT)
                .orElseThrow().getPassed());
        assertTrue(qcrRepository.findByQuestionIdAndCheckType(created.getId(), CheckType.DEDUP)
                .orElseThrow().getPassed());
    }

    @Test
    void parseAndCompareRejectsDuplicateJudgeAnswerInsteadOfTakingEitherValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> answerSolverChecker.parseAndCompare(DUPLICATE_JUDGE_ANSWER, "fake-solver-model", Option.B));
        assertTrue(ex.getMessage().contains("\"judge_answer\""), ex.getMessage());
    }
}
