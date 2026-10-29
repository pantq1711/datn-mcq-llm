package com.datn.mcq.exam.export;

import com.datn.mcq.config.PandocLimiter;
import com.datn.mcq.mathtype.MathTypeDocxConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Chuyển Markdown sang docx bằng pandoc ("-f markdown+tex_math_dollars -t docx --reference-doc"), để công thức $...$
 * thành Equation gốc của Word (OMML). Chạy trong một thư mục tạm riêng, giới hạn {@value #TIMEOUT_SECONDS} giây, xóa thư mục khi xong.
 * Giới hạn bộ nhớ pandoc qua cờ +RTS -M<heap>m -RTS và dùng chung Semaphore với trích tài liệu để chống OOM (W2-4a Mục 1).
 * Không cài pandoc; máy chủ không có pandoc thì ném {@link DocumentExportException} 503.
 *
 * <p>Tuỳ chọn công thức MathType ({@link EquationFormat#MATHTYPE}): hậu xử lý docx của pandoc bằng module
 * {@code mathtype}, không sửa lệnh pandoc.
 *
 * <p>Đường dẫn pandoc: {@code app.export.pandoc-path} (mặc định "pandoc", tìm trên PATH).
 */
@Component
public class PandocRunner {

    private static final Logger log = LoggerFactory.getLogger(PandocRunner.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MARKDOWN_FORMAT = "markdown+tex_math_dollars";

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

    /**
     * File docx và kết quả đổi công thức.
     *
     * @param mathTypeObjects   số công thức thành đối tượng MathType (0 khi xuất Equation)
     * @param equationsKeptOmml số công thức giữ dạng Equation của Word khi xuất MathType; null khi xuất Equation
     */
    public record Docx(byte[] content, int mathTypeObjects, Integer equationsKeptOmml) {}

    /** Công thức là Equation gốc của Word (OMML). */
    public byte[] markdownToDocx(String markdown) {
        return markdownToDocx(markdown, EquationFormat.OMML).content();
    }

    /**
     * Với {@link EquationFormat#MATHTYPE}: chạy thêm "pandoc -t json" trên cùng file Markdown để lấy LaTeX từng công thức
     * đúng thứ tự, rồi đổi Equation thành đối tượng MathType ({@link MathTypeDocxConverter}), vẫn trong permit của
     * {@link PandocLimiter} (dựng ảnh xem trước tốn bộ nhớ). Không đổi được thì trả file Equation, không báo lỗi.
     */
    public Docx markdownToDocx(String markdown, EquationFormat format) {
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
            return doMarkdownToDocx(markdown, format);
        } finally {
            if (limiter != null && acquired) {
                limiter.release();
            }
        }
    }

    private Docx doMarkdownToDocx(String markdown, EquationFormat format) {
        Path dir;
        try {
            dir = Files.createTempDirectory(TEMP_PREFIX);
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được thư mục tạm để xuất đề", e);
        }
        try {
            Files.writeString(dir.resolve("exam.md"), markdown, StandardCharsets.UTF_8);
            copyReferenceDoc(dir.resolve("reference.docx"));
            runPandoc(dir, "exam.md", "-f", MARKDOWN_FORMAT, "-t", "docx",
                    "--reference-doc=reference.docx", "-o", "exam.docx");
            byte[] docx = Files.readAllBytes(dir.resolve("exam.docx"));
            if (format != EquationFormat.MATHTYPE) {
                return new Docx(docx, 0, null);
            }
            List<String> latex;
            try {
                runPandoc(dir, "exam.md", "-f", MARKDOWN_FORMAT, "-t", "json", "-o", "exam.json");
                latex = latexFromPandocJson(Files.readAllBytes(dir.resolve("exam.json")));
            } catch (DocumentExportException | IOException | UncheckedIOException e) {
                log.warn("Xuất MathType: không lấy được LaTeX bằng pandoc -t json ({}), trả file Equation",
                        e.getMessage());
                return new Docx(docx, 0, MathTypeDocxConverter.countEquations(docx));
            }
            return toMathType(docx, latex);
        } catch (IOException e) {
            throw new UncheckedIOException("Lỗi đọc/ghi file khi xuất đề", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentExportException(503, "Xuất đề bị ngắt, hãy thử lại.");
        } finally {
            deleteQuietly(dir);
        }
    }

    /** Chạy pandoc trong {@code dir} với giới hạn bộ nhớ và thời gian; lỗi thì ném {@link DocumentExportException}. */
    private void runPandoc(Path dir, String... args) throws IOException, InterruptedException {
        Path logFile = dir.resolve("pandoc.log");
        int heapMb = limiter != null ? limiter.getPandocHeapMb() : 256;
        List<String> command = new ArrayList<>(List.of(pandocPath, "+RTS", "-M" + heapMb + "m", "-RTS"));
        command.addAll(List.of(args));
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
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
            String detail = Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8).strip() : "";
            throw new DocumentExportException(500, "pandoc báo lỗi (mã " + process.exitValue() + ")"
                    + (detail.isEmpty() ? "" : ": " + truncate(detail, 500)));
        }
    }

    /**
     * Đổi Equation của docx sang MathType. Bộ chuyển không đổi được cả file (số công thức khác số LaTeX, docx hỏng, lỗi
     * bất ngờ) thì trả nguyên file Equation và ghi WARN, để giáo viên vẫn nhận được file.
     */
    static Docx toMathType(byte[] docx, List<String> latex) {
        try {
            MathTypeDocxConverter.Result r = MathTypeDocxConverter.convert(docx, latex);
            if (r.keptOmml() > 0) {
                log.info("Xuất MathType: {} công thức giữ Equation của Word", r.keptOmml());
                log.debug("Công thức giữ Equation: {}", r.notes());
            }
            return new Docx(r.docx(), r.converted(), r.keptOmml());
        } catch (RuntimeException e) {
            log.warn("Xuất MathType không thành, trả file Equation của Word: {}", e.toString());
            return new Docx(docx, 0, MathTypeDocxConverter.countEquations(docx));
        }
    }

    /** LaTeX của mọi nút Math trong AST "pandoc -t json", đúng thứ tự xuất hiện, khoảng trắng gộp thành một dấu cách. */
    static List<String> latexFromPandocJson(byte[] json) {
        JsonNode ast;
        try {
            ast = JSON.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException("JSON của pandoc không đọc được", e);
        }
        List<String> out = new ArrayList<>();
        collectMath(ast.path("blocks"), out);
        return out;
    }

    private static void collectMath(JsonNode node, List<String> out) {
        if (node.isObject()) {
            if ("Math".equals(node.path("t").asText()) && node.path("c").size() == 2) {
                out.add(String.join(" ", node.path("c").get(1).asText().strip().split("\\s+")));
                return;
            }
            node.forEach(child -> collectMath(child, out));
        } else if (node.isArray()) {
            node.forEach(child -> collectMath(child, out));
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
