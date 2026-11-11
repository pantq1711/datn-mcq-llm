package com.datn.mcq.exam.export;

import com.datn.mcq.config.PandocLimiter;
import com.datn.mcq.mathtype.MathTypeDocxConverter;
import com.datn.mcq.mathtype.docx.DocxPackage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Chuyển Markdown sang docx bằng pandoc ("--sandbox -f markdown+tex_math_dollars-raw_html-raw_tex -t docx --reference-doc"), để công thức $...$
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
    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String M_NS = "http://schemas.openxmlformats.org/officeDocument/2006/math";

    static final String TEMP_PREFIX = "exam-export-";
    static final String REFERENCE_DOC_RESOURCE = "/export/reference.docx";
    static final long TIMEOUT_SECONDS = 30;
    /**
     * Định dạng đọc Markdown (F-05): công thức {@code $...$}; tắt {@code raw_html} và {@code raw_tex} để chữ trong đề không
     * thành HTML/TeX thô. KHÔNG tắt {@code raw_attribute}: ExamMarkdownBuilder tự dùng khối {@code ```{=openxml}} cho bảng
     * đầu đề và ngắt trang (không có cách khác trong Markdown). Nội dung đề không thể tạo khối đó vì ký tự backtick ngoài
     * công thức bị thoát và xuống dòng trong công thức bị thay bằng khoảng trắng (xem ExamMarkdownBuilder).
     */
    static final String MARKDOWN_FORMAT = "markdown+tex_math_dollars-raw_html-raw_tex";
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
     * @param equationsKeptOmml số công thức giữ dạng Equation của Word khi xuất MathType (chỉ khi chuyển MTEF thất bại
     *                          thật); null khi xuất Equation
     * @param equationsScaled   số công thức dài không ngắt được nên bị thu nhỏ cho vừa lề khi xuất MathType; null khi
     *                          xuất Equation
     * @param equationsFailed   số công thức pandoc (texmath) không đọc được nên nằm ở dạng chữ "$...$" khi xuất MathType;
     *                          null khi xuất Equation
     * @param failedLatex       LaTeX của các công thức đó theo thứ tự xuất hiện (rỗng khi không biết công thức nào), để nơi
     *                          gọi tìm câu chứa; KHÔNG ghi log (là nội dung câu)
     */
    public record Docx(byte[] content, int mathTypeObjects, Integer equationsKeptOmml, Integer equationsScaled,
                       Integer equationsFailed, List<String> failedLatex) {
        public Docx {
            failedLatex = failedLatex == null ? List.of() : List.copyOf(failedLatex);
        }

        /** Không có công thức hỏng: 0 khi xuất MathType, null khi xuất Equation ({@code equationsKeptOmml} null). */
        public Docx(byte[] content, int mathTypeObjects, Integer equationsKeptOmml, Integer equationsScaled) {
            this(content, mathTypeObjects, equationsKeptOmml, equationsScaled, equationsKeptOmml == null ? null : 0,
                    List.of());
        }
    }

    /** Một nút Math trong AST "pandoc -t json": phần tử thứ {@code index} của mảng inline {@code parent}. */
    record MathRef(ArrayNode parent, int index, ObjectNode math) {
        String latex() {
            return math.path("c").get(1).asText();
        }

        boolean display() {
            return "DisplayMath".equals(math.path("c").get(0).path("t").asText());
        }

        /** Thay công thức bằng chữ "$...$" ("$$...$$" với công thức hiển thị), giống cách pandoc ghi công thức không đọc được. */
        void replaceWithText() {
            if (parent == null) {
                throw new IllegalStateException("nút Math không nằm trong mảng inline");
            }
            String dollars = display() ? "$$" : "$";
            ObjectNode str = JSON.createObjectNode().put("t", "Str").put("c", dollars + latex() + dollars);
            parent.set(index, str);
        }
    }

    /** Docx đã xuất lại với công thức hỏng ở dạng chữ; {@code latex} là LaTeX các công thức còn lại (đều có m:oMath). */
    private record Rewritten(byte[] docx, List<String> latex, List<String> failedLatex) {}

    /** Công thức là Equation gốc của Word (OMML). */
    public byte[] markdownToDocx(String markdown) {
        return markdownToDocx(markdown, EquationFormat.OMML).content();
    }

    /**
     * Với {@link EquationFormat#MATHTYPE}: chạy thêm "pandoc -t json" trên cùng file Markdown để lấy LaTeX từng công thức
     * đúng thứ tự, rồi đổi Equation thành đối tượng MathType ({@link MathTypeDocxConverter}), vẫn trong permit của
     * {@link PandocLimiter} (dựng ảnh xem trước tốn bộ nhớ). Không đổi được thì trả file Equation, không báo lỗi.
     *
     * <p>11/11: pandoc không đọc được công thức nào (texmath báo lỗi) thì công thức đó nằm ở dạng chữ và số Equation ít hơn số
     * LaTeX. Khi đó (chỉ khi đó, đề bình thường không tốn thêm lượt pandoc nào) kiểm từng công thức trong một lượt pandoc phụ,
     * ghi công thức hỏng dạng chữ và xuất lại, để các công thức khác vẫn thành MathType thay vì cả file rơi về Equation
     * ({@link Docx#equationsFailed()}).
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
                return new Docx(finish(docx), 0, null, null);
            }
            JsonNode ast;
            List<MathRef> refs;
            try {
                runPandoc(dir, "exam.md", "-f", MARKDOWN_FORMAT, "-t", "json", "-o", "exam.json");
                ast = readJson(Files.readAllBytes(dir.resolve("exam.json")));
                refs = mathRefs(ast);
            } catch (DocumentExportException | IOException | UncheckedIOException e) {
                log.warn("Xuất MathType: không lấy được LaTeX bằng pandoc -t json ({}), trả file Equation",
                        e.getMessage());
                return new Docx(finish(docx), 0, MathTypeDocxConverter.countEquations(docx), 0);
            }
            List<String> latex = refs.stream().map(r -> squash(r.latex())).toList();
            List<String> failedLatex = List.of();
            int failed = 0;
            int equations = MathTypeDocxConverter.countEquations(docx);
            if (equations != latex.size()) {
                // pandoc ghi công thức không đọc được thành chữ nên số Equation ít hơn số công thức: tìm từng công thức hỏng
                // (một lượt pandoc phụ), ghi chúng dạng chữ rồi xuất lại; các công thức còn lại vẫn đổi sang MathType.
                Rewritten rewritten = rewriteUnreadable(dir, ast, refs, equations);
                if (rewritten != null) {
                    docx = rewritten.docx();
                    latex = rewritten.latex();
                    failedLatex = rewritten.failedLatex();
                    failed = failedLatex.size();
                } else {
                    failed = Math.max(0, latex.size() - equations); // cả file rơi về Equation ở toMathType (có log lý do)
                }
            }
            // Tab (mã giữ chỗ của ExamMarkdownBuilder) thay SAU bước MathType: bước đó có thể tách đoạn quanh công thức rộng.
            Docx converted = toMathType(docx, latex);
            return new Docx(finish(converted.content()), converted.mathTypeObjects(),
                    converted.equationsKeptOmml(), converted.equationsScaled(), failed, failedLatex);
        } catch (IOException e) {
            throw new UncheckedIOException("Lỗi đọc/ghi file khi xuất đề", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentExportException(503, "Xuất đề bị ngắt, hãy thử lại.");
        } finally {
            deleteQuietly(dir);
        }
    }

    /** Bước hậu xử lý riêng của đề, sau pandoc và MathType: thay mã giữ chỗ tab, thêm chân trang section đề. */
    private static byte[] finish(byte[] docx) {
        return ExamSections.ensureExamFooter(ExamTabs.apply(docx));
    }

    /** Chạy pandoc trong {@code dir} với giới hạn bộ nhớ và thời gian; lỗi thì ném {@link DocumentExportException}. */
    private void runPandoc(Path dir, String... args) throws IOException, InterruptedException {
        Path logFile = dir.resolve("pandoc.log");
        int heapMb = limiter != null ? limiter.getPandocHeapMb() : 256;
        // D4: --sandbox, pandoc chỉ đọc các file nêu trong dòng lệnh (không lấy được file máy chủ qua ảnh, include...).
        List<String> command = new ArrayList<>(List.of(pandocPath, "+RTS", "-M" + heapMb + "m", "-RTS", "--sandbox"));
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
     * bất ngờ) thì trả nguyên file Equation và ghi WARN kèm lý do, để giáo viên vẫn nhận được file.
     */
    static Docx toMathType(byte[] docx, List<String> latex) {
        try {
            MathTypeDocxConverter.Result r = MathTypeDocxConverter.convert(docx, latex);
            if (r.keptOmml() > 0) {
                log.info("Xuất MathType: {} công thức giữ Equation của Word", r.keptOmml());
                log.debug("Công thức giữ Equation: {}", r.notes());
            }
            if (r.scaled() > 0) {
                log.info("Xuất MathType: {} công thức dài không ngắt được, đã thu nhỏ cho vừa lề", r.scaled());
            }
            return new Docx(r.docx(), r.converted(), r.keptOmml(), r.scaled());
        } catch (RuntimeException e) {
            log.warn("Xuất MathType không thành, trả cả file Equation của Word. Lý do: {}", e.toString());
            return new Docx(docx, 0, MathTypeDocxConverter.countEquations(docx), 0);
        }
    }

    /**
     * Số Equation trong docx khác số công thức trong AST: kiểm từng công thức trong MỘT lượt pandoc phụ
     * ({@link #probeUnreadable}), thay công thức hỏng bằng chữ "$...$" ngay trong AST (không đổi DB, không đổi Markdown) rồi
     * xuất lại docx từ AST đó ("pandoc -f json"), để số Equation khớp số LaTeX còn lại. Ghi WARN số công thức hỏng và thứ tự
     * của chúng (không ghi nội dung). Trả null (để cả file rơi về Equation ở {@link #toMathType}, có ghi lý do) khi lượt kiểm
     * lỗi hoặc không tìm ra công thức hỏng nào.
     */
    private Rewritten rewriteUnreadable(Path dir, JsonNode ast, List<MathRef> refs, int equations)
            throws InterruptedException {
        try {
            SortedSet<Integer> bad = probeUnreadable(dir, ast, refs);
            if (bad.isEmpty()) {
                log.warn("Xuất MathType: pandoc sinh {} Equation cho {} công thức nhưng lượt kiểm từng công thức không thấy "
                        + "công thức hỏng nào", equations, refs.size());
                return null;
            }
            List<String> failedLatex = new ArrayList<>();
            List<String> kept = new ArrayList<>();
            for (int i = 0; i < refs.size(); i++) {
                (bad.contains(i) ? failedLatex : kept).add(squash(refs.get(i).latex()));
            }
            for (int i : bad) {
                refs.get(i).replaceWithText();
            }
            Files.write(dir.resolve("exam-text.json"), JSON.writeValueAsBytes(ast));
            runPandoc(dir, "exam-text.json", "-f", "json", "-t", "docx", "--reference-doc=reference.docx",
                    "-o", "exam-text.docx");
            log.warn("Xuất MathType: {} trên {} công thức pandoc không đọc được (thứ tự {}), ghi dạng chữ; các công thức "
                    + "khác vẫn đổi sang MathType", bad.size(), refs.size(), bad.stream().map(i -> i + 1).toList());
            return new Rewritten(Files.readAllBytes(dir.resolve("exam-text.docx")), kept, failedLatex);
        } catch (IOException | RuntimeException e) { // gồm DocumentExportException (pandoc lỗi/quá giờ)
            log.warn("Xuất MathType: không kiểm được từng công thức ({}: {})", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /** Mã đánh dấu đứng trước công thức thứ i trong docx kiểm: "MTPROBE" + i + "Q". */
    static final String PROBE_MARK = "MTPROBE";
    private static final Pattern PROBE_MARK_PATTERN = Pattern.compile(PROBE_MARK + "(\\d+)Q");

    /**
     * Một lượt pandoc phụ: mỗi công thức một đoạn "MTPROBE&lt;i&gt;Q &lt;công thức&gt;" → docx; đoạn nào không có m:oMath
     * là công thức pandoc không đọc được. Trả thứ tự (từ 0) các công thức đó.
     */
    private SortedSet<Integer> probeUnreadable(Path dir, JsonNode ast, List<MathRef> refs)
            throws IOException, InterruptedException {
        ObjectNode probe = JSON.createObjectNode();
        probe.set("pandoc-api-version", ast.path("pandoc-api-version"));
        probe.putObject("meta");
        ArrayNode blocks = probe.putArray("blocks");
        for (int i = 0; i < refs.size(); i++) {
            ArrayNode inlines = JSON.createArrayNode();
            inlines.addObject().put("t", "Str").put("c", PROBE_MARK + i + "Q");
            inlines.addObject().put("t", "Space");
            inlines.add(refs.get(i).math().deepCopy());
            blocks.addObject().put("t", "Para").set("c", inlines);
        }
        Files.write(dir.resolve("probe.json"), JSON.writeValueAsBytes(probe));
        runPandoc(dir, "probe.json", "-f", "json", "-t", "docx", "-o", "probe.docx");
        return unreadableInProbe(Files.readAllBytes(dir.resolve("probe.docx")), refs.size());
    }

    /**
     * Đọc docx kiểm theo thứ tự tài liệu: m:oMath gặp sau mã đánh dấu thứ i (trước mã kế tiếp) thuộc công thức i. Thiếu mã
     * đánh dấu nào thì ném lỗi (không đoán).
     */
    static SortedSet<Integer> unreadableInProbe(byte[] probeDocx, int total) throws IOException {
        Document doc = DocxPackage.read(probeDocx).document();
        NodeList all = doc.getElementsByTagName("*");
        Set<Integer> seen = new HashSet<>();
        Set<Integer> ok = new HashSet<>();
        int current = -1;
        for (int k = 0; k < all.getLength(); k++) {
            Element e = (Element) all.item(k);
            if (W_NS.equals(e.getNamespaceURI()) && "t".equals(e.getLocalName())) {
                Matcher m = PROBE_MARK_PATTERN.matcher(e.getTextContent());
                if (m.find()) {
                    current = Integer.parseInt(m.group(1));
                    seen.add(current);
                }
            } else if (M_NS.equals(e.getNamespaceURI()) && "oMath".equals(e.getLocalName()) && current >= 0) {
                ok.add(current);
            }
        }
        if (seen.size() != total) {
            throw new IllegalStateException("docx kiểm công thức thiếu mã đánh dấu (" + seen.size() + "/" + total + ")");
        }
        SortedSet<Integer> bad = new TreeSet<>();
        for (int i = 0; i < total; i++) {
            if (!ok.contains(i)) {
                bad.add(i);
            }
        }
        return bad;
    }

    /** LaTeX của mọi nút Math trong AST "pandoc -t json", đúng thứ tự xuất hiện, khoảng trắng gộp thành một dấu cách. */
    static List<String> latexFromPandocJson(byte[] json) {
        return mathRefs(readJson(json)).stream().map(r -> squash(r.latex())).toList();
    }

    private static JsonNode readJson(byte[] json) {
        try {
            return JSON.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException("JSON của pandoc không đọc được", e);
        }
    }

    private static String squash(String latex) {
        return String.join(" ", latex.strip().split("\\s+"));
    }

    /** Mọi nút Math (đủ hai thành phần) trong {@code blocks} của AST, đúng thứ tự xuất hiện. */
    static List<MathRef> mathRefs(JsonNode ast) {
        List<MathRef> out = new ArrayList<>();
        collectMath(ast.path("blocks"), null, -1, out);
        return out;
    }

    private static void collectMath(JsonNode node, ArrayNode parent, int index, List<MathRef> out) {
        if (node.isObject()) {
            if ("Math".equals(node.path("t").asText()) && node.path("c").size() == 2) {
                out.add(new MathRef(parent, index, (ObjectNode) node));
                return;
            }
            node.forEach(child -> collectMath(child, null, -1, out));
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                collectMath(array.get(i), array, i, out);
            }
        }
    }

    /**
     * File mẫu --reference-doc ({@value #REFERENCE_DOC_RESOURCE}): reference.docx mặc định của pandoc, thêm
     * compatibilityMode = 15 trong settings.xml (Word không mở ở Compatibility Mode, lệnh Convert Equations của MathType
     * chạy được) và font Normal Times New Roman 13. Cách tạo ghi ở docs/api/README.md mục 7.6. Mỗi lần xuất, file này được
     * vá thêm khổ A4, lề, kiểu của đề và chân trang ({@link ExamReferenceDoc}).
     */
    private static void copyReferenceDoc(Path target) throws IOException {
        Files.write(target, referenceDocBytes());
    }

    /** Nội dung file mẫu đưa cho pandoc: {@value #REFERENCE_DOC_RESOURCE} đã vá khổ trang, kiểu, chân trang. */
    static byte[] referenceDocBytes() throws IOException {
        try (var in = PandocRunner.class.getResourceAsStream(REFERENCE_DOC_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Thiếu file mẫu " + REFERENCE_DOC_RESOURCE + " trong resources");
            }
            return ExamReferenceDoc.build(in.readAllBytes());
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
