package com.datn.mcq.generation.worker;

import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hàng đợi generation_job (đợt A, ngày 23/10): nhận job bằng FOR UPDATE SKIP LOCKED, trả job RUNNING quá hạn về
 * PENDING (V10 started_at), lỗi sau lời gọi LLM không để job kẹt RUNNING. Chạy trên datn_mcq_test, LLM giả.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class GenerationJobQueueTest {

    /** LLM giả: trả {@code body} cho mọi lời gọi sinh câu. */
    static final class FixedLlm extends LlmClient {
        final String body;

        FixedLlm(String body) {
            super("FAKE_KEY_FOR_TESTS", "fake-model");
            this.body = body;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public LlmCallResult generateMcq(String prompt, String modelName, Strategy strategy, Double temperature) {
            return new LlmCallResult(body, 10, 1.0, 0.0, modelName);
        }

        @Override
        public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
            return new LlmCallResult(body, 10, 1.0, 0.0, modelName);
        }
    }

    private static final String VALID_MCQ = "{\"question_text\": \"Bậc của đơn thức $2x^3$ là:\", \"option_a\": \"$1$\","
            + " \"option_b\": \"$3$\", \"option_c\": \"$0$\", \"option_d\": \"$2$\", \"correct_option\": \"B\","
            + " \"explanation\": \"Số mũ của x.\"}";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private GenerationJobProcessor processor;
    @Autowired
    private StaleJobRecovery staleJobRecovery;
    @Autowired
    private QcOrchestrator qcOrchestrator;
    @Autowired
    private DedupScopeResolver dedupScopeResolver;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private int template;
    private int run;

    @BeforeEach
    void setUp() {
        TestDataCleaner.cleanAll(dataSource, jdbc, "GenerationJobQueueTest.setUp");
        int subject = insert("INSERT INTO subject (name) VALUES ('Toán')");
        template = insert("INSERT INTO question_template (subject_id, source_question, is_active, topic)"
                + " VALUES (?, 'Mẫu', TRUE, 'Đa thức')", subject);
        run = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose)"
                + " VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    /** Job mcq; {@code startedAt}/{@code updatedAt} là biểu thức SQL. */
    private int job(String status, int attempt, String startedAt, String updatedAt) {
        return insert("INSERT INTO generation_job (run_id, template_id, target_difficulty, status, attempt, next_run_at,"
                + " started_at, updated_at) VALUES (?, ?, 'easy', ?, ?, now() - interval '1 minute', " + startedAt + ", "
                + updatedAt + ")", run, template, status, attempt);
    }

    private int pendingJob() {
        return job("pending", 0, "NULL", "now()");
    }

    private Map<String, Object> row(int id) {
        return jdbc.queryForMap("SELECT status, attempt, last_error, started_at, abs(extract(epoch FROM"
                + " (next_run_at - now()))) AS due_in FROM generation_job WHERE id = ?", id);
    }

    // ---------- 1. job kẹt RUNNING ----------

    @Test
    void jobRunningQuaHanTraVePendingKhongTinhAttemptJobMoiKhongBiDung() {
        int stale = job("running", 2, "now() - interval '400 seconds'", "now() - interval '400 seconds'");
        int legacy = job("running", 1, "NULL", "now() - interval '1 hour'");
        int fresh = job("running", 1, "now() - interval '10 seconds'", "now() - interval '10 seconds'");

        assertEquals(2, staleJobRecovery.resetStaleJobs());

        Map<String, Object> s = row(stale);
        assertEquals("pending", s.get("status"));
        assertEquals(1, s.get("attempt"), "hoàn lại lượt đã tăng lúc nhận job");
        assertEquals("stale: reset", s.get("last_error"));
        assertNull(s.get("started_at"));
        assertTrue(((Number) s.get("due_in")).doubleValue() < 5, "next_run_at = now()");
        assertEquals("pending", row(legacy).get("status"), "job RUNNING trước V10 (started_at NULL) xét theo updated_at");
        Map<String, Object> f = row(fresh);
        assertEquals("running", f.get("status"), "job còn mới không bị đụng");
        assertEquals(1, f.get("attempt"));
        assertNull(f.get("last_error"));

        GenerationJobProcessor.ClaimedJobContext again = processor.claimNextPendingJob().orElseThrow();
        assertEquals(stale, again.jobId());
        assertEquals(2, again.attempt(), "lần reset không tính vào attempt");
        assertNotNull(row(stale).get("started_at"), "nhận job ghi started_at");
        assertEquals(0, staleJobRecovery.resetStaleJobs(), "job vừa nhận lại có started_at mới");
    }

    // ---------- 1. lỗi sau lời gọi LLM ----------

    @Test
    void loiKhiLuuCauSauLoiGoiLlmKhongDeJobKetRunning() {
        int id = pendingJob();
        // Ký tự NUL trong phản hồi: PostgreSQL từ chối lưu raw_llm_output, lỗi xảy ra sau lời gọi LLM.
        GenerationJobPoller poller = new GenerationJobPoller(processor, new FixedLlm("{\"question_text\": \"x\u0000\"}"),
                qcOrchestrator, dedupScopeResolver, 3);
        for (int attempt = 1; attempt <= 3; attempt++) {
            GenerationJobPoller.ProcessedJobSummary summary = poller.processNextJob(false);
            assertTrue(summary.processed());
            assertEquals(id, summary.jobId());
            assertNotNull(summary.error());
            Map<String, Object> r = row(id);
            assertEquals(attempt < 3 ? "pending" : "failed", r.get("status"), "lần " + attempt);
            assertEquals(attempt, r.get("attempt"));
            assertNotNull(r.get("last_error"));
            jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE id = ?", id);
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM generation_job WHERE status = 'running'",
                Integer.class));
    }

    @Test
    void loiKhiChayQcSauLoiGoiLlmKhongDeJobKetRunning() {
        int id = pendingJob();
        QcOrchestrator broken = mock(QcOrchestrator.class);
        when(broken.evaluateAndSave(any(), any())).thenThrow(new IllegalStateException("QC hỏng"));
        GenerationJobPoller poller = new GenerationJobPoller(processor, new FixedLlm(VALID_MCQ), broken,
                dedupScopeResolver, 3);
        GenerationJobPoller.ProcessedJobSummary summary = poller.processNextJob(false);
        assertEquals("pending", summary.jobStatus());
        assertTrue(summary.error().contains("QC hỏng"), summary.error());
        Map<String, Object> r = row(id);
        assertEquals("pending", r.get("status"), "thử lại theo đường hiện có");
        assertTrue(((String) r.get("last_error")).contains("QC hỏng"));
    }

    // ---------- 2. nhận job có khóa ----------

    @Test
    void haiLuongNhanJobDongThoiKhongNhanTrung() throws Exception {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add(pendingJob());
        }
        List<Integer> claimed = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                workers.add(pool.submit(() -> {
                    start.await();
                    for (Optional<GenerationJobProcessor.ClaimedJobContext> c = processor.claimNextPendingJob();
                         c.isPresent(); c = processor.claimNextPendingJob()) {
                        claimed.add(c.get().jobId());
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> w : workers) {
                w.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(20, claimed.size(), "không job nào bị nhận hai lần: " + claimed);
        assertEquals(new HashSet<>(ids), new HashSet<>(claimed));
        assertEquals(20, jdbc.queryForObject("SELECT count(*) FROM generation_job WHERE status = 'running'"
                + " AND attempt = 1", Integer.class));
    }

    @Test
    void jobDangDuocLuongKhacNhanThiBoQuaKhongCho() throws Exception {
        int first = pendingJob();
        int second = pendingJob();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Integer[] other = new Integer[1];
            Integer mine = new TransactionTemplate(transactionManager).execute(tx -> {
                int id = processor.claimNextPendingJob().orElseThrow().jobId();
                // Transaction của luồng này chưa commit (job vẫn bị khóa); luồng kia phải lấy job khác ngay.
                Future<Integer> f = pool.submit(() -> processor.claimNextPendingJob().orElseThrow().jobId());
                try {
                    other[0] = f.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new AssertionError("luồng thứ hai bị chặn hoặc lỗi khi nhận job", e);
                }
                return id;
            });
            assertEquals(first, mine);
            assertEquals(second, other[0]);
            assertNotEquals(mine, other[0]);
        } finally {
            pool.shutdownNow();
        }
        assertEquals("running", row(first).get("status"));
        assertEquals("running", row(second).get("status"));
    }
}
