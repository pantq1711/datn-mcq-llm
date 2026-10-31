package com.datn.mcq.exam.export;

import com.datn.mcq.config.PandocLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Chuyển Markdown sang docx bằng pandoc ("--sandbox -f markdown+tex_math_dollars -t docx --reference-doc"), để công thức $...$
 * thành Equation gốc của Word (OMML). Chạy trong một thư mục tạm riêng, giới hạn {@value #TIMEOUT_SECONDS} giây, xóa thư mục khi xong.
 * Giới hạn bộ nhớ pandoc qua cờ +RTS -M<heap>m -RTS và dùng chung Semaphore với trích tài liệu để chống OOM (W2-4a Mục 1).
 * Không cài pandoc; máy chủ không có pandoc thì ném {@link DocumentExportException} 503.
 *
 * <p>Đường dẫn pandoc: {@code app.export.pandoc-path} (mặc định "pandoc", tìm trên PATH).
 */
@Component
public class PandocRunner {

    static final String TEMP_PREFIX = "exam-export-";
    static final String REFERENCE_DOC_RESOURCE = "/export/reference.docx";
    static final long TIMEOUT_SECONDS = 30;
    static final String NOT_INSTALLED_MESSAGE = "Máy chủ chưa cài pandoc nên chưa xuất được file Word. Cài pandoc theo "
            + "hướng dẫn trong docs/api/README.md (mục 7.6 Xuất đề ra Word) rồi khởi động lại backend, hoặc đặt PANDOC_PATH.";

    private final String pandocPath;
    private final PandocLimiter limiter;

    @Autowired
    public PandocRunner(@Value("${app.export.pandoc-path:pandoc}") String pandocPath,
                        PandocLimiter limiter) {
        this.pandocPath = pandocPath;
        this.limiter = limiter;
    }

    public PandocRunner(String pandocPath) {
        this(pandocPath, new PandocLimiter(1, Duration.ofSeconds(30), 256));
    }

    public byte[] markdownToDocx(String markdown) {
        boolean acquired = false;
        if (limiter != null) {
            long waitSeconds = Math.max(1, limiter.getTimeout().toSeconds());
            try {
                acquired = limiter.tryAcquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DocumentExportException(503, "EXPORT_BUSY", "Xuất đề bị ngắt, hãy thử lại.", waitSeconds);
            }
            if (!acquired) {
                throw new DocumentExportException(503, "EXPORT_BUSY",
                        "Máy chủ đang bận xử lý tài liệu khác, hãy thử lại sau " + waitSeconds + " giây.", waitSeconds);
            }
        }
        try {
            return doMarkdownToDocx(markdown);
        } finally {
            if (limiter != null && acquired) {
                limiter.release();
            }
        }
    }

    private byte[] doMarkdownToDocx(String markdown) {
        Path dir;
        try {
            dir = Files.createTempDirectory(TEMP_PREFIX);
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được thư mục tạm để xuất đề", e);
        }
        try {
            Files.writeString(dir.resolve("exam.md"), markdown, StandardCharsets.UTF_8);
            copyReferenceDoc(dir.resolve("reference.docx"));
            Path log = dir.resolve("pandoc.log");
            int heapMb = limiter != null ? limiter.getPandocHeapMb() : 256;
            Process process;
            try {
                // D4: --sandbox, pandoc chỉ đọc các file nêu trong dòng lệnh (không lấy được file máy chủ qua ảnh, include...).
                process = new ProcessBuilder(pandocPath, "+RTS", "-M" + heapMb + "m", "-RTS", "--sandbox",
                        "exam.md", "-f", "markdown+tex_math_dollars", "-t", "docx",
                        "--reference-doc=reference.docx", "-o", "exam.docx")
                        .directory(dir.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
            } catch (IOException e) {
                throw new DocumentExportException(503, NOT_INSTALLED_MESSAGE);
            }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new DocumentExportException(503, "pandoc chạy quá " + TIMEOUT_SECONDS + " giây, hãy thử lại.");
            }
            if (process.exitValue() != 0) {
                String detail = Files.exists(log) ? Files.readString(log, StandardCharsets.UTF_8).strip() : "";
                throw new DocumentExportException(500, "pandoc báo lỗi (mã " + process.exitValue() + ")"
                        + (detail.isEmpty() ? "" : ": " + truncate(detail, 500)));
            }
            return Files.readAllBytes(dir.resolve("exam.docx"));
        } catch (IOException e) {
            throw new UncheckedIOException("Lỗi đọc/ghi file khi xuất đề", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentExportException(503, "Xuất đề bị ngắt, hãy thử lại.");
        } finally {
            deleteQuietly(dir);
        }
    }

    /**
     * File mẫu --reference-doc ({@value #REFERENCE_DOC_RESOURCE}): reference.docx mặc định của pandoc, thêm
     * compatibilityMode = 15 trong settings.xml (Word không mở ở Compatibility Mode, lệnh Convert Equations của MathType
     * chạy được) và font Normal Times New Roman 13. Cách tạo ghi ở docs/api/README.md mục 7.6.
     */
    private static void copyReferenceDoc(Path target) throws IOException {
        try (var in = PandocRunner.class.getResourceAsStream(REFERENCE_DOC_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Thiếu file mẫu " + REFERENCE_DOC_RESOURCE + " trong resources");
            }
            Files.copy(in, target);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // tệp còn bị giữ (Windows): để hệ điều hành dọn thư mục tạm
                }
            });
        } catch (IOException ignored) {
            // thư mục đã bị xóa
        }
    }
}
