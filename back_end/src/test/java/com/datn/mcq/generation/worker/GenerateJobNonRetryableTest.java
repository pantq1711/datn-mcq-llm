package com.datn.mcq.generation.worker;

import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.llm.client.GeminiRateLimiter;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import com.datn.mcq.qc.service.AnswerCheckRecorder;
import com.datn.mcq.qc.service.DedupService;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-04 và C-10: job sinh câu gặp lỗi KHÔNG tạm thời (thiếu API key, HTTP 4xx khác 429, phản hồi bị chặn an toàn SAFETY /
 * RECITATION) thì hỏng ngay ({@code failed}, như đường answer_check), chỉ tốn đúng MỘT lượt hạn mức ngày; lỗi tạm thời (429,
 * 503, 5xx, mạng, hết token) vẫn thử lại như cũ. Thông điệp lỗi ghi {@code finishReason} / {@code promptFeedback.blockReason}.
 * Gemini giả qua HTTP cục bộ, không gọi mạng thật.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class GenerateJobNonRetryableTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private GenerationJobProcessor jobProcessor;
    @Autowired
    private DedupScopeResolver dedupScopeResolver;
    @Autowired
    private FormatChecker formatChecker;
    @Autowired
    private PromptBuilder promptBuilder;
    @Autowired
    private DedupService dedupService;
    @Autowired
    private QualityCheckResultRepository qualityCheckResultRepository;
    @Autowired
    private AnswerCheckRecorder answerCheckRecorder;

    private HttpServer server;
    private final AtomicInteger httpStatus = new AtomicInteger(200);
    private final AtomicReference<String> httpBody = new AtomicReference<>("{}");
    private final AtomicInteger httpCalls = new AtomicInteger();
    private int template;
    private int run;
    private GeminiRateLimiter limiter;

    @BeforeEach
    void setUp() throws IOException {
        TestDataCleaner.cleanAll(dataSource, jdbc, "GenerateJobNonRetryableTest.setUp");
        int subject = insert("INSERT INTO subject (name) VALUES ('Toán 8')");
        template = insert("INSERT INTO question_template (subject_id, source_question, is_active, grade_level, topic,"
                + " learning_outcome) VALUES (?, 'Mẫu', TRUE, 8, 'Đa thức', 'Nhận biết bậc')", subject);
        run = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose)"
                + " VALUES (?, 'zero-shot', 'gemini-3.5-flash-lite', 0.7, 'p', 'production')", subject);
        limiter = new GeminiRateLimiter(0L, 100, 60_000L, Clock.systemDefaultZone(), d -> { }, s -> 0);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            httpCalls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = httpBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(httpStatus.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private String fakeBaseUrl() {
        return "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + server.getAddress().getPort()
                + "/v1beta/models/";
    }

    private GenerationJobPoller poller(LlmClient client) {
        QcOrchestrator qc = new QcOrchestrator(formatChecker,
                new AnswerSolverChecker(client, promptBuilder, "gemini-3.5-flash-lite"),
                qualityCheckResultRepository, dedupService, answerCheckRecorder);
        return new GenerationJobPoller(jobProcessor, client, qc, dedupScopeResolver, 3, limiter);
    }

    private int newJob() {
        return insert("INSERT INTO generation_job (run_id, template_id, target_difficulty, status, attempt, next_run_at)"
                + " VALUES (?, ?, 'easy', 'pending', 0, now() - interval '1 minute')", run, template);
    }

    /** Chạy đúng một lượt xử lý job với Gemini giả trả {@code status}/{@code body}; trả dòng job sau lượt đó. */
    private Map<String, Object> runOnce(int status, String body) {
        httpStatus.set(status);
        httpBody.set(body);
        int job = newJob();
        GenerationJobPoller.ProcessedJobSummary summary = poller(
                new LlmClient("fake-key", "gemini-3.5-flash-lite", fakeBaseUrl(), limiter))
                .processNextJob(true);
        assertTrue(summary.processed());
        return jdbc.queryForMap("SELECT * FROM generation_job WHERE id = ?", job);
    }

    private static String error(Map<String, Object> job) {
        return String.valueOf(job.get("last_error"));
    }

    // ---------------------------------------------------------------- HTTP 4xx khác 429: hỏng ngay

    @Test
    void http400HongNgayKhongThuLaiVaChiTonMotLuotHanMuc() {
        Map<String, Object> job = runOnce(400, "{\"error\":{\"code\":400,\"message\":\"Invalid JSON payload\"}}");

        assertEquals("failed", job.get("status"));
        assertEquals(1, job.get("attempt"));
        assertTrue(error(job).contains("HTTP 400"), error(job));
        assertEquals(1, httpCalls.get(), "không gọi lại Gemini");
        assertEquals(1, limiter.getDailyUsed(), "chỉ tiêu một lượt hạn mức ngày");
    }

    @Test
    void http404SaiTenModelHongNgay() {
        Map<String, Object> job = runOnce(404, "{\"error\":{\"code\":404,\"message\":\"model not found\"}}");

        assertEquals("failed", job.get("status"));
        assertTrue(error(job).contains("HTTP 404"), error(job));
        assertEquals(1, limiter.getDailyUsed());
    }

    @Test
    void http403SaiKeyHongNgay() {
        Map<String, Object> job = runOnce(403, "{\"error\":{\"code\":403,\"message\":\"API key not valid\"}}");

        assertEquals("failed", job.get("status"));
        assertEquals(1, limiter.getDailyUsed());
    }

    @Test
    void jobDaHongKhongDuocNhanLai() {
        runOnce(400, "{\"error\":{\"code\":400}}");
        jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute'");

        GenerationJobPoller.ProcessedJobSummary again = poller(
                new LlmClient("fake-key", "gemini-3.5-flash-lite", fakeBaseUrl(), limiter)).processNextJob(true);

        assertFalse(again.processed());
        assertEquals(1, httpCalls.get());
    }

    // ---------------------------------------------------------------- thiếu API key: hỏng ngay

    @Test
    void thieuApiKeyHongNgayKhongTieuHanMuc() {
        LlmClient noKey = new LlmClient("fake-key", "gemini-3.5-flash-lite", fakeBaseUrl(), limiter) {
            @Override
            public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature,
                                                         String schema) {
                throw new com.datn.mcq.llm.client.LlmNotConfiguredException(
                        "Chưa cấu hình LLM_API_KEY (hoặc GEMINI_API_KEY) cho LlmClient");
            }
        };
        int job = newJob();

        GenerationJobPoller.ProcessedJobSummary summary = poller(noKey).processNextJob(true);

        assertTrue(summary.processed());
        assertEquals("failed", summary.jobStatus());
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM generation_job WHERE id = ?", job);
        assertEquals("failed", row.get("status"));
        assertTrue(error(row).contains("LLM_API_KEY"), error(row));
        assertEquals(0, httpCalls.get());
        assertEquals(0, limiter.getDailyUsed());
    }

    // ---------------------------------------------------------------- lỗi tạm thời: vẫn thử lại như cũ

    @Test
    void http503Http500Http502VanThuLaiNhuCu() {
        for (int status : new int[] {503, 500, 502}) {
            jdbc.update("DELETE FROM generation_job");
            Map<String, Object> job = runOnce(status, "{\"error\":{\"code\":" + status + "}}");

            assertEquals("pending", job.get("status"), "HTTP " + status + " là lỗi tạm thời, phải thử lại");
            assertEquals(1, job.get("attempt"), "HTTP " + status);
            assertTrue(error(job).contains(String.valueOf(status)), error(job));
        }
    }

    /** Riêng một lượt: sau 429 bộ điều tiết tạm dừng 60 s nên không gọi tiếp trong cùng test. */
    @Test
    void http429VanThuLaiNhuCu() {
        Map<String, Object> job = runOnce(429, "{\"error\":{\"code\":429,\"message\":\"slow down\"}}");

        assertEquals("pending", job.get("status"));
        assertEquals(1, job.get("attempt"));
        assertTrue(error(job).contains("429"), error(job));
        assertEquals(1, limiter.getDailyUsed());
    }

    @Test
    void loiMangVanThuLai() throws IOException {
        server.stop(0);
        int job = newJob();

        GenerationJobPoller.ProcessedJobSummary summary = poller(
                new LlmClient("fake-key", "gemini-3.5-flash-lite", fakeBaseUrl(), limiter)).processNextJob(true);

        assertTrue(summary.processed());
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM generation_job WHERE id = ?", job);
        assertEquals("pending", row.get("status"));
        assertTrue(error(row).contains("Lỗi kết nối"), error(row));
        server = null;
    }

    @Test
    void loiTamThoiTonMotLuotMoiLanGoi() {
        runOnce(503, "{\"error\":{\"code\":503}}");
        runOnce(503, "{\"error\":{\"code\":503}}");

        assertEquals(2, limiter.getDailyUsed(), "mỗi lần gọi một lượt: lỗi tạm thời vẫn tốn lượt như cũ");
    }

    // ---------------------------------------------------------------- C-10: finishReason / blockReason

    @Test
    void finishReasonSafetyGhiVaoLoiVaHongNgay() {
        Map<String, Object> job = runOnce(200, "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}");

        assertEquals("failed", job.get("status"));
        assertTrue(error(job).contains("content.parts") && error(job).contains("finishReason=SAFETY"), error(job));
        assertEquals(1, limiter.getDailyUsed());
    }

    @Test
    void finishReasonRecitationHongNgay() {
        Map<String, Object> job = runOnce(200,
                "{\"candidates\":[{\"finishReason\":\"RECITATION\",\"content\":{\"role\":\"model\"}}]}");

        assertEquals("failed", job.get("status"));
        assertTrue(error(job).contains("finishReason=RECITATION"), error(job));
    }

    @Test
    void finishReasonMaxTokensVanThuLaiNhungGhiLyDo() {
        Map<String, Object> job = runOnce(200, "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}");

        assertEquals("pending", job.get("status"));
        assertEquals(1, job.get("attempt"));
        assertTrue(error(job).contains("finishReason=MAX_TOKENS"), error(job));
    }

    @Test
    void blockReasonSafetyKhongCoCandidatesHongNgayVaGhiLyDo() {
        Map<String, Object> job = runOnce(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");

        assertEquals("failed", job.get("status"));
        assertTrue(error(job).contains("candidates") && error(job).contains("blockReason=SAFETY"), error(job));
        assertEquals(1, limiter.getDailyUsed());
    }

    @Test
    void blockReasonKhacVanThuLaiNhungGhiLyDo() {
        Map<String, Object> job = runOnce(200, "{\"promptFeedback\":{\"blockReason\":\"OTHER\"}}");

        assertEquals("pending", job.get("status"));
        assertTrue(error(job).contains("blockReason=OTHER"), error(job));
    }

    @Test
    void phanHoiKhongCoLyDoVanThuLaiNhuCu() {
        Map<String, Object> job = runOnce(200, "{\"candidates\":[{\"content\":{}}]}");

        assertEquals("pending", job.get("status"));
        assertTrue(error(job).contains("content.parts"), error(job));
        assertFalse(error(job).contains("finishReason"), error(job));
    }
}
