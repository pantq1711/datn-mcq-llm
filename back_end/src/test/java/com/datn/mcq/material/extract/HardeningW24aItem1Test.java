package com.datn.mcq.material.extract;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.config.PandocLimiter;
import com.datn.mcq.exam.export.DocumentExportException;
import com.datn.mcq.exam.export.PandocRunner;
import com.datn.mcq.material.entity.LessonMaterial;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.datn.mcq.material.extract.MaterialTestFiles.pdf;
import static com.datn.mcq.material.extract.MaterialTestFiles.utf8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test cho W2-4a Mục 1: Gia cố bộ nhớ và đồng thời cho trích tài liệu và xuất Word.
 */
class HardeningW24aItem1Test {

    @Test
    void semaphoreChung1PermitChanXuatWordKhiDangTrichTaiLieu() throws Exception {
        MaterialProperties props = new MaterialProperties();
        props.setMaxConcurrentExtractions(1);
        props.setQueueWait(Duration.ofMillis(100));

        PandocLimiter limiter = new PandocLimiter(1, Duration.ofMillis(100), 256);

        CountDownLatch extractStarted = new CountDownLatch(1);
        CountDownLatch finishExtract = new CountDownLatch(1);

        PdfTextExtractor slowPdf = new PdfTextExtractor(props) {
            @Override
            public ExtractedText extract(byte[] content, Runnable permitReleaser) {
                extractStarted.countDown();
                try {
                    finishExtract.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (permitReleaser != null) {
                    permitReleaser.run();
                }
                return new ExtractedText("dummy", LessonMaterial.MIME_PDF, "pdfbox", 1, List.of());
            }
        };

        MaterialTextExtractor extractor = new MaterialTextExtractor(props, new PlainTextExtractor(props),
                new DocxTextExtractor(props, "pandoc", new ZipSafetyScanner(props), new DocxFormulaCounter()),
                slowPdf, limiter);

        PandocRunner runner = new PandocRunner("fake-pandoc", limiter);

        byte[] pdfBytes = pdf("Noi dung dai");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ExtractedText> future = pool.submit(() -> extractor.extract(pdfBytes));
            assertTrue(extractStarted.await(5, TimeUnit.SECONDS));

            // Trong lúc trích tài liệu đang giữ semaphore, xuất Word phải bị chặn sau timeout và trả 503 EXPORT_BUSY
            DocumentExportException ex = assertThrows(DocumentExportException.class,
                    () -> runner.markdownToDocx("# Đề kiểm tra"));
            assertEquals(503, ex.getHttpStatus());
            assertEquals("EXPORT_BUSY", ex.getCode());
            assertNotNull(ex.getRetryAfterSeconds());
            assertTrue(ex.getMessage().contains("bận"));

            finishExtract.countDown();
            assertNotNull(future.get(5, TimeUnit.SECONDS));
            assertEquals(1, limiter.getAvailablePermits(), "sau khi xong phải trả lại đúng 1 permit");
        } finally {
            finishExtract.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void semaphoreChungChanTrichTaiLieuKhiDangXuatWord() throws Exception {
        MaterialProperties props = new MaterialProperties();
        props.setMaxConcurrentExtractions(1);
        props.setQueueWait(Duration.ofMillis(100));

        PandocLimiter limiter = new PandocLimiter(1, Duration.ofMillis(100), 256);

        // Giả lập export đang giữ permit
        assertTrue(limiter.tryAcquire());
        try {
            MaterialTextExtractor extractor = new MaterialTextExtractor(props, new PlainTextExtractor(props),
                    new DocxTextExtractor(props, "pandoc", new ZipSafetyScanner(props), new DocxFormulaCounter()),
                    new PdfTextExtractor(props), limiter);

            ApiErrorException ex = assertThrows(ApiErrorException.class, () -> extractor.extract(utf8("Văn bản")));
            assertEquals("EXTRACTOR_BUSY", ex.getCode());
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        } finally {
            limiter.release();
            assertEquals(1, limiter.getAvailablePermits());
        }
    }

    @Test
    void pdfTimeoutKhongNhaPermitKhiLuongCuConTreo() throws Exception {
        MaterialProperties props = new MaterialProperties();
        props.setMaxConcurrentExtractions(1);
        props.setQueueWait(Duration.ofMillis(100));
        props.setExtractTimeout(Duration.ofMillis(200));

        PandocLimiter limiter = new PandocLimiter(1, Duration.ofMillis(100), 256);

        CountDownLatch hangStarted = new CountDownLatch(1);
        CountDownLatch hangFinish = new CountDownLatch(1);

        // Subclass PdfTextExtractor để giả lập luồng background bị treo (không dừng khi bị interrupt)
        PdfTextExtractor hangingPdf = new PdfTextExtractor(props) {
            @Override
            public ExtractedText extract(byte[] content, Runnable permitReleaser) {
                // Tạo một luồng background treo
                AtomicBoolean released = new AtomicBoolean(false);
                Runnable releaseOnce = () -> {
                    if (released.compareAndSet(false, true) && permitReleaser != null) {
                        permitReleaser.run();
                    }
                };
                ExecutorService single = Executors.newSingleThreadExecutor();
                try {
                    Future<?> f = single.submit(() -> {
                        hangStarted.countDown();
                        try {
                            // Cố tình ngủ và nuốt interrupt để giả lập luồng treo
                            while (hangFinish.getCount() > 0) {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException ignored) {
                                    // nuốt interrupt
                                }
                            }
                        } finally {
                            releaseOnce.run();
                        }
                    });
                    try {
                        f.get(props.getExtractTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        f.cancel(true);
                        throw MaterialErrors.timeout();
                    }
                } finally {
                    single.shutdown();
                }
                return null;
            }
        };

        MaterialTextExtractor extractor = new MaterialTextExtractor(props, new PlainTextExtractor(props),
                new DocxTextExtractor(props, "pandoc", new ZipSafetyScanner(props), new DocxFormulaCounter()),
                hangingPdf, limiter);

        // Request 1: bị timeout
        ApiErrorException ex1 = assertThrows(ApiErrorException.class, () -> extractor.extract(pdf("PDF treo khong dau")));
        assertEquals("EXTRACTION_TIMEOUT", ex1.getCode());

        // Vì luồng cũ vẫn đang treo (hangFinish chưa countDown), permit KHÔNG được trả lại!
        assertEquals(0, limiter.getAvailablePermits(), "Permit chưa được nhả khi luồng trích cũ vẫn còn sống");

        // Request 2: vào tiếp phải bị từ chối với EXTRACTOR_BUSY (503) vì không lấy được permit
        ApiErrorException ex2 = assertThrows(ApiErrorException.class, () -> extractor.extract(utf8("Text mới")));
        assertEquals("EXTRACTOR_BUSY", ex2.getCode());

        // Cho luồng cũ kết thúc
        hangFinish.countDown();
        // Chờ permit được nhả
        boolean reacquired = limiter.tryAcquire(Duration.ofSeconds(2));
        assertTrue(reacquired, "Sau khi luồng cũ kết thúc, permit phải được giải phóng");
        limiter.release();
    }
}

