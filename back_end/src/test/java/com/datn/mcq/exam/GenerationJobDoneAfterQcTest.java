package com.datn.mcq.exam;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L-39: job sinh câu từng được đánh dấu {@code done} ngay khi lưu câu, TRƯỚC khi dedup và Solver chạy. Nếu giáo viên xem
 * tiến độ đúng lúc đó, đối soát thấy job done mà câu còn {@code generated} (trạng thái trung gian), coi như Solver lệch
 * và xếp hàng sinh lại một câu đang tốt (tốn thêm lời gọi LLM, mất câu tốt). Job chỉ được done cùng transaction với
 * trạng thái QC cuối của câu.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class GenerationJobDoneAfterQcTest {

    /** Solver giả (mcq luôn B) chạy một móc trong lúc "đang gọi Solver" (ngoài transaction của worker). */
    static final class HookSolver extends LlmClient {
        ExamQuestionEditTest.ThrowingRunnable onCall;

        HookSolver() {
            super("FAKE_KEY_FOR_TESTS", "fake-solver-model");
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            if (onCall != null) {
                ExamQuestionEditTest.ThrowingRunnable hook = onCall;
                onCall = null;
                try {
                    hook.run();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }
            return new LlmCallResult("{\"reasoning\": \"r\", \"judge_answer\": \"B\"}", 10, 1.0, 0.0, modelName);
        }
    }

    private static final HookSolver SOLVER = new HookSolver();

    @TestBean
    private AnswerSolverChecker answerSolverChecker;

    static AnswerSolverChecker answerSolverChecker() {
        return new AnswerSolverChecker(SOLVER, new PromptBuilder(), "fake-solver-model");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private GenerationJobProcessor jobProcessor;
    @Autowired
    private QcOrchestrator qcOrchestrator;
    @Autowired
    private DedupScopeResolver dedupScopeResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int teacher;
    private int matrixId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "GenerationJobDoneAfterQcTest.setUp");
        SOLVER.onCall = null;
        int subject = jdbc.queryForObject("INSERT INTO subject (name) VALUES ('Toán') RETURNING id", Integer.class);
        teacher = jdbc.queryForObject("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.race@test', 'teacher')"
                + " RETURNING id", Integer.class);
        matrixId = json(perform(post("/api/exam-matrices").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Đề một câu\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"cells\": ["
                        + "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 1,"
                        + " \"topic\": \"Đa thức\", \"content\": \"Đơn thức\","
                        + " \"learningOutcomes\": {\"easy\": \"Nhận biết được đơn thức.\"}}]}"))
                .andExpect(status().isOk())).get("id").asInt();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(AuthTestSupport.teacher(teacher)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void processAll(GenerationJobPoller poller) {
        for (int i = 0; i < 20; i++) {
            jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'");
            if (!poller.processNextJob(true).processed()) {
                return;
            }
        }
    }

    @Test
    void xemTienDoTrongLucSolverDangChayKhongSinhLaiCauTot() throws Exception {
        int examId = json(perform(post("/api/exams/build").contentType(MediaType.APPLICATION_JSON)
                .content("{\"matrixId\": " + matrixId + "}")).andExpect(status().isOk())).get("examId").asInt();
        int slot = jdbc.queryForObject("SELECT id FROM exam_slot WHERE exam_id = ?", Integer.class, examId);
        List<String> seenJobStatus = new ArrayList<>();
        // Giáo viên mở tiến độ đúng lúc Solver của câu vừa sinh đang chạy.
        SOLVER.onCall = () -> {
            seenJobStatus.add(jdbc.queryForObject("SELECT status FROM generation_job WHERE exam_slot_id = ?",
                    String.class, slot));
            perform(get("/api/exams/" + examId + "/progress")).andExpect(status().isOk());
        };

        processAll(new GenerationJobPoller(jobProcessor, new ExamBuildTest.FakeGenerator(), qcOrchestrator,
                dedupScopeResolver, 3));
        perform(get("/api/exams/" + examId + "/progress")).andExpect(status().isOk());

        Map<String, Object> s = jdbc.queryForMap("SELECT * FROM exam_slot WHERE id = ?", slot);
        assertEquals("ready", s.get("state"));
        assertEquals(1, s.get("attempts"), "không xếp hàng sinh lại");
        assertEquals(0, s.get("solver_retries"));
        assertNull(s.get("fallback_question_id"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM generation_job WHERE exam_slot_id = ?", Integer.class,
                slot), "chỉ một job sinh câu");
        assertEquals("qc_passed", jdbc.queryForObject("SELECT g.status FROM generated_question g JOIN exam_slot s"
                + " ON s.generated_question_id = g.id WHERE s.id = ?", String.class, slot));
        assertEquals("done", jdbc.queryForObject("SELECT status FROM generation_job WHERE exam_slot_id = ?",
                String.class, slot));
        assertEquals(List.of("running"), seenJobStatus, "job chưa done khi QC chưa xong");
    }
}
