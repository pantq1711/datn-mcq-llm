package com.datn.mcq.generation.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * W2-4b (C): vòng lặp worker liên tục quản lý qua {@link SmartLifecycle} (thay {@code @Scheduled} 15s trên
 * {@link GenerationJobPoller}, tách khỏi luồng scheduler của {@link StaleJobScheduler}).
 * <ul>
 *   <li>Mặc định {@code app.worker.concurrency = 2} luồng worker để chồng thời gian chờ mạng trong khi
 *       {@link com.datn.mcq.llm.client.GeminiRateLimiter} vẫn giữ nhịp gọi Gemini &le; 15 lượt/phút.</li>
 *   <li>Khi vừa xử lý xong một job ({@code summary.processed() == true}), worker nhận tiếp job kế ngay lập tức mà không
 *       chờ; chỉ khi hàng đợi rỗng (hoặc chạm hạn mức ngày) mới chờ {@code app.worker.idle-delay-ms} (mặc định 2000 ms).</li>
 *   <li>Khi tắt ứng dụng ({@link #stop()}): đánh thức các luồng đang chờ rỗng và dừng sạch executor; job đang chạy dở nếu
 *       không kịp xong trước khi tắt sẽ được {@link StaleJobRecovery} trả về {@code pending}.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true")
public class GenerationJobWorkerLifecycle implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationJobWorkerLifecycle.class);

    private final GenerationJobPoller poller;
    private final int concurrency;
    private final long idleDelayMs;
    private final long shutdownAwaitMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object idleMonitor = new Object();
    private ExecutorService executor;

    public GenerationJobWorkerLifecycle(
            GenerationJobPoller poller,
            @Value("${app.worker.concurrency:2}") int concurrency,
            @Value("${app.worker.idle-delay-ms:2000}") long idleDelayMs,
            @Value("${app.worker.shutdown-await-ms:5000}") long shutdownAwaitMs) {
        this.poller = poller;
        this.concurrency = Math.max(1, concurrency);
        this.idleDelayMs = Math.max(10L, idleDelayMs);
        this.shutdownAwaitMs = Math.max(100L, shutdownAwaitMs);
    }

    @Override
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        AtomicInteger threadNo = new AtomicInteger();
        executor = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r, "generation-job-worker-" + threadNo.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        for (int i = 1; i <= concurrency; i++) {
            final int workerIdx = i;
            executor.submit(() -> runWorkerLoop(workerIdx));
        }
    }

    private void runWorkerLoop(int workerIdx) {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            boolean shouldIdle = true;
            try {
                GenerationJobPoller.ProcessedJobSummary summary = poller.processNextJob(true);
                if (summary.processed()) {
                    shouldIdle = false;
                }
            } catch (Throwable t) {
                if (t instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
                LOG.error("Worker {} gặp lỗi không mong đợi trong vòng lặp: {}", workerIdx, t.getMessage(), t);
            }
            if (!running.get() || Thread.currentThread().isInterrupted()) {
                break;
            }
            if (shouldIdle) {
                synchronized (idleMonitor) {
                    if (!running.get()) {
                        break;
                    }
                    try {
                        idleMonitor.wait(idleDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        synchronized (idleMonitor) {
            idleMonitor.notifyAll();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(shutdownAwaitMs, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    public int getConcurrency() {
        return concurrency;
    }

    public long getIdleDelayMs() {
        return idleDelayMs;
    }
}
