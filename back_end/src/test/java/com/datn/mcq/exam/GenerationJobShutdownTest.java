package com.datn.mcq.exam;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.GenerationJobWorkerLifecycle;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-09 (soát ngoài) và F-27 (nhật ký worker). Khi ứng dụng tắt (cờ running = false của vòng lặp worker, hoặc luồng bị
 * interrupt), job đang gọi Gemini KHÔNG được ghi là lỗi: không {@code recordJobFailure}, không tăng attempt, trả về
 * pending, khôi phục cờ interrupt. Trong lúc xử lý job, MDC có jobId/slotId/examId; khi ghi lỗi job có log lý do.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class GenerationJobShutdownTest extends ExamFlowTestBase {

    private int examId;
    private int slot;
    private ListAppender<ILoggingEvent> logs;
    private Logger pollerLogger;

    @BeforeEach
    void setUp() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 1));
        examId = build(matrixId).get("examId").asInt();
        slot = slotId(examId, cellId(matrixId, 1), 1);
        jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'");
        logs = new ListAppender<>();
        logs.start();
        pollerLogger = (Logger) LoggerFactory.getLogger(GenerationJobPoller.class);
        pollerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        pollerLogger.detachAppender(logs);
        Thread.interrupted();
    }

    private Map<String, Object> job() {
        return jdbc.queryForMap("SELECT * FROM generation_job WHERE exam_slot_id = ? AND job_type = 'generate'", slot);
    }

    private abstract static class FailingClient extends LlmClient {
        FailingClient() {
            super("FAKE_KEY_FOR_TESTS", "fake-generator-model");
        }

        @Override
        public boolean isConfigured() {
            return true;
        }
    }

    @Test
    void luongBiInterruptKhiGoiGeminiThiTraJobVePendingKhongTinhLoi() {
        LlmClient interrupted = new FailingClient() {
            @Override
            public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
                // Giống LlmClient khi httpClient.send bị ngắt lúc tắt app: đặt lại cờ interrupt rồi ném RuntimeException.
                Thread.currentThread().interrupt();
                throw new RuntimeException("Lỗi kết nối Gemini API: sleep interrupted");
            }
        };
        GenerationJobPoller poller = new GenerationJobPoller(jobProcessor, interrupted, qcOrchestrator,
                dedupScopeResolver, 3);

        GenerationJobPoller.ProcessedJobSummary summary = poller.processNextJob(true);

        assertTrue(summary.processed());
        Map<String, Object> row = job();
        assertEquals("pending", row.get("status"), "tắt app giữa chừng: job về pending, không failed");
        assertEquals(0, row.get("attempt"), "không tăng attempt vì lần dở dang do tắt app");
        assertNull(row.get("started_at"));
        assertFalse(String.valueOf(row.get("last_error")).startsWith("Lỗi kết nối"),
                "không ghi lỗi kết nối: " + row.get("last_error"));
        assertTrue(Thread.currentThread().isInterrupted(), "phải khôi phục cờ interrupt cho vòng lặp worker thoát");
    }

    @Test
    void workerDangDungThiLoiMangKhiGoiGeminiKhongTinhLoi() throws Exception {
        CountDownLatch inCall = new CountDownLatch(1);
        AtomicReference<GenerationJobWorkerLifecycle> lifecycle = new AtomicReference<>();
        LlmClient closing = new FailingClient() {
            @Override
            public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
                inCall.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (lifecycle.get().isRunning() && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                // Kết nối bị đóng khi tắt app, nhưng KHÔNG có cờ interrupt: chỉ running = false cho biết đang tắt.
                throw new RuntimeException("Lỗi kết nối Gemini API: Connection reset");
            }
        };
        GenerationJobPoller poller = new GenerationJobPoller(jobProcessor, closing, qcOrchestrator, dedupScopeResolver, 3);
        GenerationJobWorkerLifecycle worker = new GenerationJobWorkerLifecycle(poller, 1, 20, 4000);
        lifecycle.set(worker);

        worker.start();
        assertTrue(inCall.await(10, TimeUnit.SECONDS), "worker phải nhận job và vào lời gọi Gemini");
        worker.stop();

        Map<String, Object> row = job();
        assertEquals("pending", row.get("status"), "running = false: job về pending, không failed");
        assertEquals(0, row.get("attempt"));
        assertFalse(String.valueOf(row.get("last_error")).startsWith("Lỗi kết nối"), String.valueOf(row.get("last_error")));
    }

    @Test
    void mdcCoJobSlotExamTrongLucXuLyVaLogLyDoKhiGhiLoiJob() {
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();
        LlmClient boom = new FailingClient() {
            @Override
            public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
                seen.set(Map.of("jobId", String.valueOf(MDC.get("jobId")), "slotId", String.valueOf(MDC.get("slotId")),
                        "examId", String.valueOf(MDC.get("examId"))));
                throw new RuntimeException("Gemini hỏng giả lập lý do-xyz");
            }
        };
        GenerationJobPoller poller = new GenerationJobPoller(jobProcessor, boom, qcOrchestrator, dedupScopeResolver, 3);

        poller.processNextJob(true);

        Map<String, Object> row = job();
        assertEquals(Map.of("jobId", String.valueOf(row.get("id")), "slotId", String.valueOf(slot),
                "examId", String.valueOf(examId)), seen.get());
        assertNull(MDC.get("jobId"), "MDC được dọn sau khi xử lý xong");
        assertNull(MDC.get("slotId"));
        assertNull(MDC.get("examId"));
        assertEquals("pending", row.get("status"), "lỗi thường vẫn đi đường thử lại như cũ");
        assertEquals(1, row.get("attempt"));
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN)
                        && e.getFormattedMessage().contains("lý do-xyz")
                        && e.getFormattedMessage().contains(String.valueOf(row.get("id")))),
                "log lý do khi ghi lỗi job: " + logs.list);
    }
}
