package com.datn.mcq.generation.worker;

import com.datn.mcq.common.exception.UpstreamServiceException;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.exam.dto.ExamBuildDtos.BuildExamRequest;
import com.datn.mcq.exam.service.ExamBuildService;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.llm.client.GeminiRateLimiter;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.client.QuotaExhaustedException;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import com.datn.mcq.qc.service.AnswerCheckRecorder;
import com.datn.mcq.qc.service.DedupService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W2-4b (C): bộ điều tiết lượt gọi Gemini dùng chung ({@link GeminiRateLimiter}) và worker chạy liên tục
 * ({@link GenerationJobWorkerLifecycle}).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class GeminiRateLimiterAndWorkerTest {

    static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;
        final List<Long> sleepsMs = new ArrayList<>();

        MutableClock(Instant initial, ZoneId zone) {
            this.instant = initial;
            this.zone = zone;
        }

        synchronized void advance(Duration d) {
            long ms = Math.max(0L, d.toMillis());
            sleepsMs.add(ms);
            this.instant = this.instant.plusMillis(ms);
        }

        synchronized void advanceWithoutSleepRecord(Duration d) {
            this.instant = this.instant.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public synchronized Instant instant() {
            return instant;
        }
    }

    private static final String VALID_MCQ_CANDIDATE = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"question_text\\":\\"Bậc của $3x^2$ là:\\",\\"option_a\\":\\"$1$\\",\\"option_b\\":\\"$2$\\",\\"option_c\\":\\"$3$\\",\\"option_d\\":\\"$0$\\",\\"correct_option\\":\\"B\\",\\"explanation\\":\\"Số mũ của x là 2.\\"}"}]}}],"usageMetadata":{"totalTokenCount":25}}
            """;

    private static final String VALID_SOLVER_CANDIDATE = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"reasoning\\":\\"Số mũ là 2\\",\\"judge_answer\\":\\"B\\"}"}]}}],"usageMetadata":{"totalTokenCount":12}}
            """;

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private GenerationJobProcessor jobProcessor;
    @Autowired
    private GenerationService generationService;
    @Autowired
    private ExamBuildService examBuildService;
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
    private final AtomicReference<String> httpBody = new AtomicReference<>(VALID_MCQ_CANDIDATE);
    private int subject;
    private int template;
    private int run;
    private int teacher;

    @BeforeEach
    void setUp() throws IOException {
        TestDataCleaner.cleanAll(dataSource, jdbc, "GeminiRateLimiterAndWorkerTest.setUp");
        teacher = insert("INSERT INTO users (name, email, role) VALUES ('GV C', 'gvc@test.local', 'teacher')");
        subject = insert("INSERT INTO subject (name) VALUES ('Toán 8')");
        template = insert("INSERT INTO question_template (subject_id, source_question, is_active, grade_level, topic,"
                + " learning_outcome) VALUES (?, 'Mẫu', TRUE, 8, 'Đa thức', 'Nhận biết bậc')", subject);
        run = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose)"
                + " VALUES (?, 'zero-shot', 'gemini-3.5-flash-lite', 0.7, 'p', 'production')", subject);

        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
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

    @Test
    void boDieuTietCachDeu4200MsVaDungToanBoKhiGap429RetryDelay() {
        Instant t0 = Instant.parse("2026-10-27T18:00:00Z");
        MutableClock clock = new MutableClock(t0, ZoneId.of("Asia/Ho_Chi_Minh"));
        GeminiRateLimiter limiter = new GeminiRateLimiter(4200L, 480, 60_000L, clock, clock::advance, s -> 0);
        LlmClient client = new LlmClient("fake-key", "gemini-3.5-flash-lite", fakeBaseUrl(), limiter);

        // Lượt 1 ở t = 0: không phải chờ.
        client.callJsonMode("p1", "gemini-3.5-flash-lite", 0.0);
        assertEquals(t0, clock.instant());
        assertEquals(List.of(), clock.sleepsMs);
        assertEquals(1, limiter.getDailyUsed());

        // Lượt 2 gọi ngay sau lượt 1: bộ điều tiết bắt chờ đúng 4200 ms.
        client.callJsonMode("p2", "gemini-3.5-flash-lite", 0.0);
        assertEquals(t0.plusMillis(4200), clock.instant());
        assertEquals(List.of(4200L), clock.sleepsMs);
        assertEquals(2, limiter.getDailyUsed());

        // Lượt 3 gặp HTTP 429 kèm retryDelay = "42s": tạm dừng toàn bộ bộ điều tiết 42 giây VÀ vẫn tính vào bộ đếm ngày.
        httpStatus.set(429);
        httpBody.set("""
                {"error":{"code":429,"message":"Resource exhausted","status":"RESOURCE_EXHAUSTED","details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"42s"}]}}
                """);
        assertEquals(Duration.ofSeconds(42), LlmClient.parseRetryDelay(httpBody.get()).orElse(null));
        UpstreamServiceException ex429 = assertThrows(UpstreamServiceException.class,
                () -> client.callJsonMode("p3", "gemini-3.5-flash-lite", 0.0));
        assertEquals(429, ex429.getHttpStatus());
        assertEquals(t0.plusMillis(8400), clock.instant(), "trước khi gọi lượt 3 đã chờ đủ 4200 ms kể từ lượt 2");
        assertEquals(3, limiter.getDailyUsed(), "lượt gọi bị 429 vẫn tính vào hạn mức ngày");

        // Lượt 4 (luồng khác hoặc lần thử lại gọi ngay ở t = 8400 ms): phải chờ đủ 42000 ms (tới t = 50400 ms).
        httpStatus.set(200);
        httpBody.set(VALID_MCQ_CANDIDATE);
        client.callJsonMode("p4", "gemini-3.5-flash-lite", 0.0);
        assertEquals(t0.plusMillis(8400 + 42_000), clock.instant(),
                "429 có retryDelay=42s phải tạm dừng toàn bộ bộ điều tiết 42 giây");
        assertEquals(4, limiter.getDailyUsed());

        // 429 không có retryDelay thì dùng khoảng lùi mặc định (60s).
        httpStatus.set(429);
        httpBody.set("{\"error\":{\"code\":429,\"message\":\"Quota exceeded\"}}");
        assertThrows(UpstreamServiceException.class,
                () -> client.callJsonMode("p5", "gemini-3.5-flash-lite", 0.0));
        Instant after429NoRetryDelay = clock.instant();
        httpStatus.set(200);
        httpBody.set(VALID_MCQ_CANDIDATE);
        client.callJsonMode("p6", "gemini-3.5-flash-lite", 0.0);
        assertEquals(after429NoRetryDelay.plusSeconds(60), clock.instant(),
                "429 thiếu retryDelay thì tạm dừng theo mặc định 60 giây");
    }

    @Test
    void hanMucNgayKhoiTaoTuDbDemMoiLuotGoiVaDatLaiLuc0GioThaiBinhDuong() {
        ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");
        // 23:50 ngày 27/10/2026 theo giờ Thái Bình Dương (PDT, UTC-7) = 06:50 ngày 28/10 UTC = 13:50 ngày 28/10 giờ VN.
        ZonedDateTime pacificLateNight = ZonedDateTime.of(2026, 10, 27, 23, 50, 0, 0, GeminiRateLimiter.PACIFIC_ZONE);
        ZonedDateTime pacificStartOfDay = pacificLateNight.toLocalDate().atStartOfDay(GeminiRateLimiter.PACIFIC_ZONE);
        LocalDateTime startLocal = LocalDateTime.ofInstant(pacificStartOfDay.toInstant(), vnZone);

        // 1 câu hôm qua (trước 0h PT) -> KHÔNG tính.
        insertQuestionAt(startLocal.minusHours(2), null);
        // 2 câu LLM sinh hôm nay (edited_from_question_id IS NULL) -> tính 2.
        int q1 = insertQuestionAt(startLocal.plusHours(1), null);
        int q2 = insertQuestionAt(startLocal.plusHours(2), null);
        // 1 bản giáo viên sửa (edited_from_question_id IS NOT NULL) -> KHÔNG tính.
        int qEdited = insertQuestionAt(startLocal.plusHours(3), q1);

        // 1 dòng answer_check có token_count (gọi Solver thật) -> tính 1; 1 dòng chép từ bản trước (token_count NULL) -> KHÔNG tính.
        jdbc.update("INSERT INTO quality_check_result (question_id, question_type, check_type, passed, judge_answer,"
                + " judge_model, token_count, latency_ms, checked_at) VALUES (?, 'mcq', 'answer_check', TRUE, 'B',"
                + " 'm', 15, 100.0, ?)", q1, Timestamp.valueOf(startLocal.plusHours(1)));
        jdbc.update("INSERT INTO quality_check_result (question_id, question_type, check_type, passed, judge_answer,"
                + " judge_model, token_count, latency_ms, checked_at) VALUES (?, 'mcq', 'answer_check', TRUE, 'B',"
                + " 'm', NULL, NULL, ?)", qEdited, Timestamp.valueOf(startLocal.plusHours(3)));

        // 1 dòng question_grounding có token_count -> tính 1.
        jdbc.update("INSERT INTO question_grounding (question_id, verdict, reason, judge_model, material_refs,"
                + " token_count, latency_ms, checked_at) VALUES (?, 'unknown', 'r', 'm', '[]'::jsonb, 20, 150.0, ?)",
                q2, Timestamp.valueOf(startLocal.plusHours(2)));

        assertEquals(4, jobProcessor.countCallsSince(startLocal),
                "DB có 2 câu LLM + 1 Solver có token + 1 grounding có token = 4 lượt gọi trong ngày");

        MutableClock clock = new MutableClock(pacificLateNight.toInstant(), vnZone);
        GeminiRateLimiter limiter = new GeminiRateLimiter(4200L, 6, 60_000L, clock, clock::advance, jobProcessor);
        LlmClient client = new LlmClient("fake-key", "gemini-3.5-flash-lite", fakeBaseUrl(), limiter);

        assertTrue(limiter.hasDailyQuota());
        assertEquals(4, limiter.getDailyUsed(), "khởi tạo từ số lượt đã ghi trong DB kể từ 0h giờ Thái Bình Dương");

        // Lượt thứ 5 (thành công) và lượt thứ 6 (HTTP 500 thất bại) đều tính vào hạn mức ngày (đạt 6/6).
        client.callJsonMode("call-5", "gemini-3.5-flash-lite", 0.0);
        assertEquals(5, limiter.getDailyUsed());
        httpStatus.set(500);
        httpBody.set("boom");
        assertThrows(RuntimeException.class, () -> client.callJsonMode("call-6-fail", "gemini-3.5-flash-lite", 0.0));
        assertEquals(6, limiter.getDailyUsed());
        assertFalse(limiter.hasDailyQuota(), "đã chạm hạn mức 6 lượt/ngày");

        // Lượt thứ 7 bị chặn ngay bằng QuotaExhaustedException, KHÔNG ngủ chờ tới 0h PT.
        Instant before7 = clock.instant();
        QuotaExhaustedException quotaEx = assertThrows(QuotaExhaustedException.class,
                () -> client.callJsonMode("call-7", "gemini-3.5-flash-lite", 0.0));
        assertEquals(before7, clock.instant(), "chạm hạn mức ngày thì ném lỗi ngay, không sleep");
        ZonedDateTime expectedResetPacific = pacificLateNight.toLocalDate().plusDays(1)
                .atStartOfDay(GeminiRateLimiter.PACIFIC_ZONE);
        assertEquals(LocalDateTime.ofInstant(expectedResetPacific.toInstant(), vnZone), quotaEx.getResetAt());

        // Qua mốc 00:00 giờ Thái Bình Dương sang ngày mới (tiến 15 phút tới 00:05 PT): hạn mức tự đặt lại về 0.
        httpStatus.set(200);
        httpBody.set(VALID_MCQ_CANDIDATE);
        clock.advanceWithoutSleepRecord(Duration.ofMinutes(15));
        assertTrue(limiter.hasDailyQuota(), "qua 0h giờ Thái Bình Dương phải đặt lại hạn mức ngày");
        assertEquals(0, limiter.getDailyUsed());
        client.callJsonMode("call-new-day", "gemini-3.5-flash-lite", 0.0);
        assertEquals(1, limiter.getDailyUsed());
    }

    @Test
    void chamHanMucNgayKhongClaimJobMoiVaGiuaChungJobKhongChoToiGioDatLai() {
        ZoneId vnZone = ZoneId.systemDefault();
        ZonedDateTime pacificNow = ZonedDateTime.of(2026, 10, 27, 22, 0, 0, 0, GeminiRateLimiter.PACIFIC_ZONE);
        LocalDateTime expectedResetLocal = LocalDateTime.ofInstant(
                pacificNow.toLocalDate().plusDays(1).atStartOfDay(GeminiRateLimiter.PACIFIC_ZONE).toInstant(), vnZone);
        MutableClock clock = new MutableClock(pacificNow.toInstant(), vnZone);

        // 1) Hạn mức đã hết từ trước: worker KHÔNG claim job (job vẫn pending, attempt = 0).
        GeminiRateLimiter exhaustedLimiter = new GeminiRateLimiter(4200L, 2, 60_000L, clock, clock::advance, s -> 2);
        LlmClient client = new LlmClient("fake-key", "gemini-3.5-flash-lite", fakeBaseUrl(), exhaustedLimiter);
        QcOrchestrator qc = new QcOrchestrator(formatChecker,
                new AnswerSolverChecker(client, promptBuilder, "gemini-3.5-flash-lite"),
                qualityCheckResultRepository, dedupService, answerCheckRecorder);
        GenerationJobPoller poller = new GenerationJobPoller(jobProcessor, client, qc, dedupScopeResolver, 3,
                exhaustedLimiter);

        int job1 = insert("INSERT INTO generation_job (run_id, template_id, target_difficulty, status, attempt,"
                + " next_run_at) VALUES (?, ?, 'easy', 'pending', 0, now() - interval '1 minute')", run, template);
        GenerationJobPoller.ProcessedJobSummary s1 = poller.processNextJob(true);
        assertFalse(s1.processed(), "hết hạn mức ngày thì không được nhận job mới");
        Map<String, Object> row1 = jdbc.queryForMap("SELECT * FROM generation_job WHERE id = ?", job1);
        assertEquals("pending", row1.get("status"));
        assertEquals(0, row1.get("attempt"));

        // 2) Còn đúng 1 lượt trong hạn mức ngày: job generate dùng lượt cuối để sinh câu (thành công), đến bước Solver
        // nội tuyến thì chạm hạn mức ngày -> QuotaExhaustedException ném ngay (không sleep tới 0h), câu được giữ ở
        // generated kèm solver_error và xếp job answer_check riêng (pending).
        GeminiRateLimiter oneLeftLimiter = new GeminiRateLimiter(4200L, 5, 60_000L, clock, clock::advance, s -> 4);
        LlmClient clientOneLeft = new LlmClient("fake-key", "gemini-3.5-flash-lite", fakeBaseUrl(), oneLeftLimiter);
        QcOrchestrator qcOneLeft = new QcOrchestrator(formatChecker,
                new AnswerSolverChecker(clientOneLeft, promptBuilder, "gemini-3.5-flash-lite"),
                qualityCheckResultRepository, dedupService, answerCheckRecorder);
        GenerationJobPoller pollerOneLeft = new GenerationJobPoller(jobProcessor, clientOneLeft, qcOneLeft,
                dedupScopeResolver, 3, oneLeftLimiter);

        httpStatus.set(200);
        httpBody.set(VALID_MCQ_CANDIDATE);
        GenerationJobPoller.ProcessedJobSummary s2 = pollerOneLeft.processNextJob(true);
        assertTrue(s2.processed());
        assertEquals("done", s2.jobStatus());
        assertEquals("generated", s2.questionStatus());
        String solverErr = jdbc.queryForObject("SELECT solver_error FROM generated_question WHERE id = ?",
                String.class, s2.questionId());
        assertNotNull(solverErr);
        assertTrue(solverErr.contains("hạn mức"), solverErr);
        Map<String, Object> acJob = jdbc.queryForMap(
                "SELECT * FROM generation_job WHERE question_id = ? AND job_type = 'answer_check'", s2.questionId());
        assertEquals("pending", acJob.get("status"));

        // 3) Job generate đã được claim nhưng lúc gọi LLM sinh câu thì hạn mức vừa bị worker khác dùng hết:
        // job generate được trả về pending, next_run_at = mốc đặt lại 0h PT, KHÔNG tính vào attempt (attempt về 0).
        GeminiRateLimiter raceLimiter = new GeminiRateLimiter(4200L, 5, 60_000L, clock, clock::advance, s -> 4);
        LlmClient raceClient = new LlmClient("fake-key", "gemini-3.5-flash-lite", fakeBaseUrl(), raceLimiter) {
            @Override
            public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
                // Giả lập worker khác vừa lấy mất lượt cuối ngay sau khi job này được claim.
                raceLimiter.acquire();
                return super.callJsonModeWithSchema(prompt, modelName, temperature, schema);
            }
        };
        GenerationJobPoller racePoller = new GenerationJobPoller(jobProcessor, raceClient, qcOneLeft,
                dedupScopeResolver, 3, raceLimiter);
        jdbc.update("DELETE FROM generation_job WHERE job_type = 'answer_check'");
        int job2 = insert("INSERT INTO generation_job (run_id, template_id, target_difficulty, status, attempt,"
                + " next_run_at) VALUES (?, ?, 'easy', 'pending', 0, now() - interval '1 minute')", run, template);

        GenerationJobPoller.ProcessedJobSummary s3 = racePoller.processNextJob(true);
        assertTrue(s3.processed());
        assertEquals("pending", s3.jobStatus());
        Map<String, Object> row2 = jdbc.queryForMap("SELECT * FROM generation_job WHERE id = ?", job2);
        assertEquals("pending", row2.get("status"));
        assertEquals(0, row2.get("attempt"), "hoãn vì hết hạn mức ngày không tính vào attempt");
        assertNull(row2.get("started_at"));
        assertEquals(expectedResetLocal, ((Timestamp) row2.get("next_run_at")).toLocalDateTime());
        assertTrue(String.valueOf(row2.get("last_error")).startsWith("quota:"), String.valueOf(row2.get("last_error")));
    }

    @Test
    void boJobSpacingVaWorkerLienTucXuLyNhieuJobDungSachKhiTat() throws Exception {
        // 1) GenerationJobPoller không còn dùng @Scheduled.
        for (Method m : GenerationJobPoller.class.getDeclaredMethods()) {
            assertNull(m.getAnnotation(Scheduled.class),
                    "GenerationJobPoller không được dùng @Scheduled nữa: " + m.getName());
        }

        // 2) GenerationService.enqueueJobs và ExamBuildService.build không còn giãn cách next_run_at 13 giây.
        LocalDateTime afterEnqueueCutoff = LocalDateTime.now().plusSeconds(3);
        List<GenerationJob> enqueued = generationService.enqueueJobs(
                template, Strategy.ZERO_SHOT, Difficulty.EASY, "gemini-3.5-flash-lite", 3, QuestionType.MCQ, null);
        assertEquals(3, enqueued.size());
        for (GenerationJob j : enqueued) {
            assertTrue(!j.getNextRunAt().isAfter(afterEnqueueCutoff),
                    "enqueueJobs không được cộng 13s * i vào next_run_at: " + j.getNextRunAt());
        }
        jdbc.update("DELETE FROM generation_job");

        int matrixId = insert("INSERT INTO exam_matrix (name, subject_id, grade_level, created_by)"
                + " VALUES ('Ma trận không giãn cách', ?, 8, ?)", subject, teacher);
        insert("INSERT INTO exam_matrix_cell (matrix_id, part, question_type, difficulty, question_count, position,"
                + " topic, content, learning_outcome_easy, template_id)"
                + " VALUES (?, 'I', 'mcq', 'easy', 3, 1, 'Đa thức', 'Đơn thức', 'NB', ?)",
                matrixId, template);
        var built = examBuildService.build(new BuildExamRequest(matrixId, "Đề C", null, null), teacher);
        assertEquals(3, built.jobsQueued());
        List<Timestamp> nextRuns = jdbc.queryForList(
                "SELECT next_run_at FROM generation_job ORDER BY id", Timestamp.class);
        assertEquals(3, nextRuns.size());
        LocalDateTime maxAllowed = LocalDateTime.now().plusSeconds(3);
        for (Timestamp ts : nextRuns) {
            assertTrue(!ts.toLocalDateTime().isAfter(maxAllowed),
                    "buildFromMatrix không được cộng JOB_SPACING 13s * jobs vào next_run_at: " + ts);
        }

        // 3) Worker liên tục (SmartLifecycle) xử lý hết 3 job liên tiếp ngay lập tức mà không chờ idle-delay (5000 ms),
        // rồi dừng sạch khi gọi stop().
        MutableClock clock = new MutableClock(Instant.now(), ZoneId.systemDefault());
        GeminiRateLimiter fastLimiter = new GeminiRateLimiter(0L, 480, 60_000L, clock, clock::advance, s -> 0);
        AtomicInteger seq = new AtomicInteger();
        LlmClient fastClient = new LlmClient("fake-key", "gemini-3.5-flash-lite", fakeBaseUrl(), fastLimiter) {
            @Override
            public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
                int k = seq.incrementAndGet();
                String body = "{\"question_text\":\"Đơn thức $" + k + "x^" + k + "$ có bậc mấy?\","
                        + "\"option_a\":\"$" + k + "$\",\"option_b\":\"$" + (k + 1) + "$\","
                        + "\"option_c\":\"$0$\",\"option_d\":\"$" + (k + 2) + "$\","
                        + "\"correct_option\":\"A\",\"explanation\":\"Số mũ là " + k + ".\"}";
                return new LlmCallResult(body, 10, 1.0, 0.0, modelName);
            }
        };
        AnswerSolverChecker fastSolver = new AnswerSolverChecker(fastClient, promptBuilder, "gemini-3.5-flash-lite") {
            @Override
            public boolean canRunOnline() {
                return true;
            }

            @Override
            public SolverCheckOutcome check(String q, String a, String b, String c, String d, com.datn.mcq.enums.Option opt) {
                return new SolverCheckOutcome(true, com.datn.mcq.enums.AnswerChoice.A, "gemini-3.5-flash-lite", "ok");
            }
        };
        QcOrchestrator fastQc = new QcOrchestrator(formatChecker, fastSolver,
                qualityCheckResultRepository, dedupService, answerCheckRecorder);
        GenerationJobPoller fastPoller = new GenerationJobPoller(jobProcessor, fastClient, fastQc,
                dedupScopeResolver, 3, fastLimiter);

        GenerationJobWorkerLifecycle workerLifecycle = new GenerationJobWorkerLifecycle(fastPoller, 2, 5000L, 2000L);
        long startWall = System.currentTimeMillis();
        workerLifecycle.start();
        try {
            assertTrue(workerLifecycle.isRunning());
            long deadline = System.currentTimeMillis() + 3000L;
            while (System.currentTimeMillis() < deadline) {
                int done = jdbc.queryForObject(
                        "SELECT count(*) FROM generation_job WHERE status = 'done'", Integer.class);
                if (done == 3) {
                    break;
                }
                Thread.sleep(25);
            }
            long elapsed = System.currentTimeMillis() - startWall;
            assertEquals(3, jdbc.queryForObject(
                    "SELECT count(*) FROM generation_job WHERE status = 'done'", Integer.class),
                    "worker liên tục phải xử lý hết cả 3 job");
            assertTrue(elapsed < 3000L, "worker xử lý liên tiếp không chờ idleDelayMs=5000ms: tốn " + elapsed + "ms");
        } finally {
            workerLifecycle.stop();
        }
        assertFalse(workerLifecycle.isRunning(), "worker phải dừng sạch sau stop()");
    }

    private int insertQuestionAt(LocalDateTime createdAt, Integer editedFromId) {
        return insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, created_at,"
                + " edited_from_question_id) VALUES (?, ?, 'Câu hỏi $x$', '$1$', '$2$', '$3$', '$4$', 'B', '{}',"
                + " TRUE, 'qc_passed', ?, ?)", template, run, Timestamp.valueOf(createdAt), editedFromId);
    }
}

