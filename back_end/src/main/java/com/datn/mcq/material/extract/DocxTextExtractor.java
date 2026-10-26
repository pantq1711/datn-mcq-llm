package com.datn.mcq.material.extract;

import com.datn.mcq.material.entity.LessonMaterial;
import com.datn.mcq.material.extract.DocxFormulaCounter.FormulaStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Trích văn bản từ docx bằng pandoc ("-f docx -t markdown+tex_math_dollars"): Equation gốc của Word (OMML) thành
 * {@code $...$}. Thứ tự: ghi file vào thư mục tạm riêng (tên cố định, không dùng tên client gửi), quét zip, kiểm cấu trúc
 * docx, đếm công thức, rồi mới chạy pandoc ({@code --sandbox}, trần heap, giới hạn thời gian). Đối tượng MathType nhúng
 * (OLE) bị pandoc bỏ qua: được đếm và báo trong warnings, không im lặng. Dùng chung đường dẫn pandoc với xuất Word
 * ({@code app.export.pandoc-path}); không gọi sang module exam.
 */
@Component
public class DocxTextExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(DocxTextExtractor.class);

    static final String TEMP_PREFIX = "material-docx-";
    /** Markdown giữ công thức $...$; tắt HTML thô, thuộc tính và các khối mở rộng để văn bản gọn. */
    static final String PANDOC_FORMAT = "markdown+tex_math_dollars-raw_html-link_attributes-header_attributes"
            + "-fenced_divs-bracketed_spans-native_divs-native_spans-raw_attribute";
    static final String NO_TEXT_MESSAGE = "Không trích được văn bản: file Word không có chữ (có thể chỉ gồm ảnh hoặc "
            + "công thức MathType). Hãy dán nội dung vào file .txt hoặc lưu công thức dạng Equation của Word.";

    private static final java.util.regex.Pattern IMAGE = java.util.regex.Pattern.compile(
            "!\\[[^\\]]*\\]\\([^)]*\\)(\\{[^}]*\\})?");
    private static final java.util.regex.Pattern EMPTY_COMMENT_LINE = java.util.regex.Pattern.compile(
            "(?m)^[ \\t]*<!--\\s*-->[ \\t]*$\\n?");

    private final MaterialProperties props;
    private final String pandocPath;
    private final ZipSafetyScanner scanner;
    private final DocxFormulaCounter counter;

    @Autowired
    public DocxTextExtractor(MaterialProperties props, @Value("${app.export.pandoc-path:pandoc}") String pandocPath,
                             ZipSafetyScanner scanner, DocxFormulaCounter counter) {
        this.props = props;
        this.pandocPath = pandocPath;
        this.scanner = scanner;
        this.counter = counter;
    }

    public ExtractedText extract(byte[] content) {
        Path dir;
        try {
            dir = Files.createTempDirectory(TEMP_PREFIX);
        } catch (IOException e) {
            throw MaterialErrors.failed("Không tạo được thư mục tạm để đọc file Word.");
        }
        try {
            Path in = dir.resolve("in.docx");
            Files.write(in, content);
            List<String> entries = scanner.scan(in);
            requireDocxEntries(entries);
            FormulaStats stats = counter.count(in);
            String markdown = runPandoc(dir);
            String text = TextNormalizer.checked(props, cleanMarkdown(markdown), NO_TEXT_MESSAGE);
            return new ExtractedText(text, LessonMaterial.MIME_DOCX, LessonMaterial.METHOD_PANDOC, null,
                    warnings(stats));
        } catch (IOException e) {
            throw MaterialErrors.failed("Lỗi ghi hoặc đọc file tạm khi đọc file Word.");
        } finally {
            deleteQuietly(dir);
        }
    }

    /** File zip phải có cấu trúc docx (không phải xlsx, pptx hay zip khác); không thì UNSUPPORTED_FILE_TYPE. */
    static void requireDocxEntries(List<String> entries) {
        if (!entries.contains("[Content_Types].xml") || !entries.contains(DocxFormulaCounter.DOCUMENT_XML)) {
            throw MaterialErrors.unsupported("File nén này không phải tài liệu Word (.docx). Chỉ hỗ trợ .docx, .pdf và "
                    + ".txt (UTF-8).");
        }
    }

    private String runPandoc(Path dir) throws IOException {
        Path log = dir.resolve("pandoc.log");
        Path out = dir.resolve("out.md");
        Process process;
        try {
            process = new ProcessBuilder(pandocPath, "+RTS", "-M" + props.getPandocHeapMb() + "m", "-RTS", "--sandbox",
                    "-f", "docx", "-t", PANDOC_FORMAT, "--wrap=none", "in.docx", "-o", "out.md")
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
        } catch (IOException e) {
            throw MaterialErrors.unavailable("Máy chủ chưa cài pandoc nên chưa đọc được file Word (.docx). File .pdf và "
                    + ".txt vẫn dùng được.");
        }
        try {
            process.getOutputStream().close();
            if (!process.waitFor(props.getExtractTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw MaterialErrors.timeout();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw MaterialErrors.timeout();
        }
        if (process.exitValue() != 0) {
            String detail = Files.exists(log) ? Files.readString(log, StandardCharsets.UTF_8).strip() : "";
            LOG.warn("pandoc đọc docx lỗi (mã {}): {}", process.exitValue(),
                    detail.length() > 300 ? detail.substring(0, 300) : detail);
            throw MaterialErrors.failed("Không đọc được file Word (file có thể bị hỏng hoặc quá phức tạp).");
        }
        if (Files.size(out) > props.getMaxChars() * 8L) {
            throw MaterialErrors.tooLong(props.getMaxChars());
        }
        return Files.readString(out, StandardCharsets.UTF_8);
    }

    /**
     * Bỏ tham chiếu ảnh (kèm thuộc tính kích thước) và dòng chú thích rỗng pandoc chèn giữa các danh sách, rồi làm sạch
     * định dạng markdown thành văn bản thường (giữ nguyên công thức $...$), xem {@link MarkdownCleaner}. Văn bản lưu trong
     * DB là kết quả này.
     */
    static String cleanMarkdown(String markdown) {
        String s = IMAGE.matcher(markdown).replaceAll("");
        s = EMPTY_COMMENT_LINE.matcher(s).replaceAll("");
        return MarkdownCleaner.clean(s);
    }

    private List<ExtractionWarning> warnings(FormulaStats stats) {
        List<ExtractionWarning> out = new ArrayList<>();
        if (stats.mathType() > 0) {
            int total = stats.formulas();
            String ratio = stats.mathType() + "/" + total;
            out.add(new ExtractionWarning("MATHTYPE_FORMULAS_DROPPED", stats.mathType(), total,
                    "Có " + ratio + " công thức MathType (đối tượng nhúng) chưa đọc được nên bị bỏ khỏi văn bản trích."));
            if ((double) stats.mathType() / total >= props.getFormulaLossWarnRatio()) {
                out.add(new ExtractionWarning("FORMULA_LOSS_HIGH", stats.mathType(), total,
                        "Phần lớn công thức (" + ratio + ") bị mất: câu hỏi sinh ra có thể thiếu hoặc sai công thức. "
                                + "Hãy lưu công thức dạng Equation của Word hoặc dán nội dung vào file .txt."));
            }
        }
        if (stats.images() > 0) {
            out.add(new ExtractionWarning("FORMULA_IMAGES_NOT_READ", stats.images(), null,
                    "Có " + stats.images() + " ảnh trong tài liệu (có thể là công thức dạng ảnh); hệ thống không đọc "
                            + "nội dung ảnh (không OCR)."));
        }
        return out;
    }

    /**
     * Xóa thư mục tạm. Trên Windows, tệp (pandoc.log, in.docx) có thể còn bị giữ thêm chốc lát sau khi tiến trình pandoc
     * vừa bị dừng, nên thử lại vài lần ngắn trước khi bỏ cuộc (khi đó để hệ điều hành dọn thư mục tạm).
     */
    private static void deleteQuietly(Path dir) {
        for (int attempt = 0; attempt < 10; attempt++) {
            if (tryDelete(dir)) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** @return true khi thư mục không còn tồn tại */
    private static boolean tryDelete(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // còn bị giữ: vòng thử lại sau xử lý
                }
            });
        } catch (IOException ignored) {
            // thư mục đã bị xóa hoặc không đọc được
        }
        return !Files.exists(dir);
    }
}
