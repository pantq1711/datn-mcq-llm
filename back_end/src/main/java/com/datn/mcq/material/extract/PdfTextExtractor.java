package com.datn.mcq.material.extract;

import com.datn.mcq.material.entity.LessonMaterial;
import jakarta.annotation.PreDestroy;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Trích văn bản PDF bằng PDFBox, KHÔNG OCR: PDF scan (không có lớp chữ) bị từ chối với thông báo rõ. Giới hạn: số trang,
 * độ dài văn bản, thời gian (trang xử lý quá hạn thì dừng; quá hạn tổng thì đóng tài liệu và bỏ kết quả). PDF có mật khẩu
 * hoặc cấm sao chép nội dung bị từ chối. Công thức trong PDF thường bị méo khi trích: nhiều ký tự lỗi thì có cảnh báo.
 *
 * <p>Quản lý luồng (W2-4a Mục 1): dùng ThreadPoolExecutor có trần (SynchronousQueue), không dùng newCachedThreadPool;
 * chỉ giải phóng permit khi luồng trích thật sự kết thúc trong background, ngăn tình trạng luồng cũ treo mà permit đã nhả.
 */
@Component
public class PdfTextExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(PdfTextExtractor.class);

    static final String NO_TEXT_MESSAGE = "Không trích được văn bản: PDF có vẻ là bản scan (ảnh) hoặc không có lớp chữ. "
            + "Hệ thống chưa hỗ trợ OCR; hãy dùng file có chữ hoặc dán nội dung vào file .txt.";

    private final MaterialProperties props;
    private final ExecutorService pool;

    public PdfTextExtractor(MaterialProperties props) {
        this.props = props;
        int maxThreads = Math.max(1, props.getMaxConcurrentExtractions());
        this.pool = new ThreadPoolExecutor(
                1, maxThreads, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread t = new Thread(runnable, "pdf-extract");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }

    public final ExtractedText extract(byte[] content) {
        return extract(content, null);
    }

    public ExtractedText extract(byte[] content, Runnable permitReleaser) {
        AtomicBoolean released = new AtomicBoolean(false);
        Runnable releaseOnce = () -> {
            if (released.compareAndSet(false, true) && permitReleaser != null) {
                permitReleaser.run();
            }
        };
        AtomicReference<PDDocument> opened = new AtomicReference<>();
        long deadline = System.nanoTime() + props.getExtractTimeout().toNanos();
        Future<ExtractedText> task;
        try {
            task = pool.submit(() -> {
                try {
                    return doExtract(content, deadline, opened);
                } finally {
                    releaseOnce.run();
                }
            });
        } catch (RejectedExecutionException e) {
            releaseOnce.run();
            throw MaterialErrors.busy();
        }
        try {
            return task.get(props.getExtractTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            closeQuietly(opened.get());
            // Không nhả permit ở đây nếu luồng trích thật sự chưa kết thúc; releaseOnce trong finally của task sẽ nhả
            throw MaterialErrors.timeout();
        } catch (InterruptedException e) {
            task.cancel(true);
            closeQuietly(opened.get());
            Thread.currentThread().interrupt();
            throw MaterialErrors.timeout();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            LOG.warn("Trích PDF lỗi: {}", String.valueOf(e.getCause()));
            throw MaterialErrors.invalid("Không đọc được file PDF (file hỏng hoặc không phải PDF hợp lệ).");
        }
    }

    private ExtractedText doExtract(byte[] content, long deadline, AtomicReference<PDDocument> opened) {
        // Luồng PDF (đã nén) chỉ đệm bằng file tạm, không giữ trong heap; file tạm xóa khi đóng tài liệu.
        try (PDDocument doc = Loader.loadPDF(content, "", null, null, IOUtils.createTempFileOnlyStreamCache())) {
            opened.set(doc);
            if (doc.isEncrypted() && !doc.getCurrentAccessPermission().canExtractContent()) {
                throw MaterialErrors.encrypted("PDF không cho phép sao chép nội dung nên không dùng được. Hãy dùng "
                        + "bản không khóa hoặc dán nội dung vào file .txt.");
            }
            int pages = doc.getNumberOfPages();
            if (pages == 0) {
                throw MaterialErrors.invalid("File PDF không có trang nào.");
            }
            if (pages > props.getMaxPages()) {
                throw MaterialErrors.tooManyPages(props.getMaxPages());
            }
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                public void processPage(PDPage page) throws IOException {
                    if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) {
                        throw new ExtractionTimeoutException();
                    }
                    super.processPage(page);
                }
            };
            String raw = stripper.getText(doc);
            long visible = raw.codePoints().filter(cp -> !Character.isWhitespace(cp)).count();
            if (visible < (long) props.getMinPdfCharsPerPage() * pages) {
                throw MaterialErrors.noText(NO_TEXT_MESSAGE);
            }
            List<ExtractionWarning> warnings = isLowQuality(raw)
                    ? List.of(new ExtractionWarning("PDF_TEXT_QUALITY_LOW", null, null,
                            "Văn bản PDF có nhiều ký tự lỗi (thường do công thức hoặc font đặc biệt): nội dung trích "
                                    + "có thể sai hoặc thiếu công thức."))
                    : List.of();
            return new ExtractedText(TextNormalizer.checked(props, raw, NO_TEXT_MESSAGE), LessonMaterial.MIME_PDF,
                    LessonMaterial.METHOD_PDFBOX, pages, warnings);
        } catch (InvalidPasswordException e) {
            throw MaterialErrors.encrypted("PDF có mật khẩu nên không đọc được. Hãy dùng bản không đặt mật khẩu.");
        } catch (ExtractionTimeoutException e) {
            throw MaterialErrors.timeout();
        } catch (com.datn.mcq.common.exception.ApiErrorException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            LOG.warn("Trích PDF lỗi: {}", e.toString());
            throw MaterialErrors.invalid("Không đọc được file PDF (file hỏng hoặc không phải PDF hợp lệ).");
        }
    }

    /** Hơn 1% ký tự là ký tự thay thế (U+FFFD) hoặc vùng riêng: dấu hiệu công thức hay font không giải mã được. */
    static boolean isLowQuality(String raw) {
        long total = raw.codePoints().count();
        long suspect = raw.codePoints()
                .filter(cp -> cp == 0xFFFD || Character.getType(cp) == Character.PRIVATE_USE).count();
        return suspect >= 5 && suspect * 100 > total;
    }

    private static void closeQuietly(PDDocument doc) {
        if (doc != null) {
            try {
                doc.close();
            } catch (IOException ignored) {
                // đang hủy việc: bỏ qua
            }
        }
    }

    private static final class ExtractionTimeoutException extends IOException {
        ExtractionTimeoutException() {
            super("quá hạn trích PDF");
        }
    }
}
