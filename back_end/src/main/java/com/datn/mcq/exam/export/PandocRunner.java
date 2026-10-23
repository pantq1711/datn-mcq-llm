package com.datn.mcq.exam.export;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Chuyển Markdown sang docx bằng pandoc ("-f markdown+tex_math_dollars -t docx"), để công thức $...$ thành Equation
 * gốc của Word (OMML). Chạy trong một thư mục tạm riêng, giới hạn {@value #TIMEOUT_SECONDS} giây, xóa thư mục khi xong.
 * Không cài pandoc; máy chủ không có pandoc thì ném {@link DocumentExportException} 503.
 *
 * <p>Đường dẫn pandoc: {@code app.export.pandoc-path} (mặc định "pandoc", tìm trên PATH).
 */
@Component
public class PandocRunner {

    static final String TEMP_PREFIX = "exam-export-";
    static final long TIMEOUT_SECONDS = 30;
    static final String NOT_INSTALLED_MESSAGE = "Máy chủ chưa cài pandoc nên chưa xuất được file Word. Cài pandoc theo "
            + "hướng dẫn trong docs/api/README.md (mục 7.6 Xuất đề ra Word) rồi khởi động lại backend, hoặc đặt PANDOC_PATH.";

    private final String pandocPath;

    public PandocRunner(@Value("${app.export.pandoc-path:pandoc}") String pandocPath) {
        this.pandocPath = pandocPath;
    }

    public byte[] markdownToDocx(String markdown) {
        Path dir;
        try {
            dir = Files.createTempDirectory(TEMP_PREFIX);
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được thư mục tạm để xuất đề", e);
        }
        try {
            Files.writeString(dir.resolve("exam.md"), markdown, StandardCharsets.UTF_8);
            Path log = dir.resolve("pandoc.log");
            Process process;
            try {
                process = new ProcessBuilder(pandocPath, "exam.md", "-f", "markdown+tex_math_dollars", "-t", "docx",
                        "-o", "exam.docx")
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
