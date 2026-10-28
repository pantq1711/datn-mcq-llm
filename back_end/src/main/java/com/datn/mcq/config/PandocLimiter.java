package com.datn.mcq.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Semaphore điều phối tiến trình nặng (pandoc, trích PDF) dùng chung giữa trích tài liệu và xuất Word (W2-4a Mục 1).
 * Giới hạn số tiến trình nặng chạy đồng thời để bảo vệ bộ nhớ container (mem_limit 1 GB).
 */
@Component
public class PandocLimiter {

    private final Semaphore semaphore;
    private final Duration timeout;
    private final int pandocHeapMb;

    public PandocLimiter(
            @Value("${app.materials.max-concurrent-extractions:2}") int maxConcurrent,
            @Value("${app.materials.queue-wait:30s}") Duration timeout,
            @Value("${app.materials.pandoc-heap-mb:768}") int pandocHeapMb) {
        this.semaphore = new Semaphore(Math.max(1, maxConcurrent), true);
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(30);
        this.pandocHeapMb = pandocHeapMb > 0 ? pandocHeapMb : 768;
    }

    public boolean tryAcquire() throws InterruptedException {
        return semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean tryAcquire(Duration customTimeout) throws InterruptedException {
        Duration wait = customTimeout != null ? customTimeout : timeout;
        return semaphore.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void release() {
        semaphore.release();
    }

    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getPandocHeapMb() {
        return pandocHeapMb;
    }
}

