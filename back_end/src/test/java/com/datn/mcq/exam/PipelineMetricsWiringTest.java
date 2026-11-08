package com.datn.mcq.exam;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.generation.repository.GenerationJobRepository;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.generation.worker.QueueMetrics;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Giám sát (08/11): đi hết luồng thật (tạo đề một câu → worker → QC) với Gemini giả và kiểm số liệu Micrometer được ghi
 * ở worker ({@link QueueMetrics}) và ở QC ({@code QcMetrics}, nạp vào {@link QcOrchestrator} bean). So sánh trước/sau vì
 * registry dùng chung trong cả ngữ cảnh test.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class PipelineMetricsWiringTest {

    @TestBean
    private AnswerSolverChecker answerSolverChecker;

    static AnswerSolverChecker answerSolverChecker() {
        return new AnswerSolverChecker(new ExamBuildTest.FakeSolver(), new PromptBuilder(), "fake-solver-model");
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
    @Autowired
    private MeterRegistry registry;
    @Autowired
    private QueueMetrics queueMetrics;
    @Autowired
    private GenerationJobRepository jobRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int teacher;
    private int matrixId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "PipelineMetricsWiringTest.setUp");
        int subject = jdbc.queryForObject("INSERT INTO subject (name) VALUES ('Toán') RETURNING id", Integer.class);
        teacher = jdbc.queryForObject("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.metrics@test', 'teacher')"
                + " RETURNING id", Integer.class);
        matrixId = objectMapper.readTree(mockMvc.perform(post("/api/exam-matrices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Đề một câu\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"cells\": ["
                                + "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\","
                                + " \"questionCount\": 1, \"topic\": \"Đa thức\", \"content\": \"Đơn thức\","
                                + " \"learningOutcomes\": {\"easy\": \"Nhận biết được đơn thức.\"}}]}")
                        .with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asInt();
    }

    private long jobTimerCount() {
        return registry.find("datn.job").timers().stream().mapToLong(Timer::count).sum();
    }

    private double qcPipelineCount() {
        return registry.find("datn.qc.pipeline").counters().stream().mapToDouble(c -> c.count()).sum();
    }

    @Test
    void luongThatGhiSoLieuWorkerVaQc() throws Exception {
        long jobsBefore = jobTimerCount();
        double qcBefore = qcPipelineCount();
        mockMvc.perform(post("/api/exams/build").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matrixId\": " + matrixId + "}").with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isOk());

        GenerationJobPoller poller = new GenerationJobPoller(jobProcessor, new ExamBuildTest.FakeGenerator(),
                qcOrchestrator, dedupScopeResolver, 3);
        poller.setQueueMetrics(queueMetrics);
        for (int i = 0; i < 20; i++) {
            jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'");
            if (!poller.processNextJob(true).processed()) {
                break;
            }
        }

        assertTrue(jobTimerCount() > jobsBefore, "worker phải ghi thời gian xử lý job");
        assertTrue(registry.find("datn.job").tag("outcome", "done").timers().stream().mapToLong(Timer::count).sum() >= 1,
                "có ít nhất một job kết thúc done");
        assertTrue(qcPipelineCount() > qcBefore, "QC chạy cho câu vừa sinh phải được đếm");

        // Truy vấn GROUP BY của gauge datn_jobs chạy được trên PostgreSQL thật (gauge nuốt lỗi nên kiểm trực tiếp ở đây).
        long done = jobRepository.countByStatus().stream().filter(r -> "done".equals(r.getStatus()))
                .mapToLong(GenerationJobRepository.StatusCount::getTotal).sum();
        assertTrue(done >= 1, "phải đếm được job done: " + done);
    }
}
