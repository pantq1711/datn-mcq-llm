package com.datn.mcq.bankimport;

import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.out.CatalogWriter;
import com.datn.mcq.bankimport.out.FormulaSheet;
import com.datn.mcq.bankimport.out.ImageConverter;
import com.datn.mcq.bankimport.out.ReportBuilder;
import com.datn.mcq.bankimport.parse.DocParser;
import com.datn.mcq.bankimport.parse.ParsedDoc;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Điều phối: đọc mọi .docx trong thư mục vào, ghi JSON từng bộ, ảnh, danh mục CSV, review.csv, báo cáo và formulas.html
 * vào thư mục ra, rồi chạy chốt chặn rò rỉ. Phát hiện rò rỉ thì xóa mọi tệp vừa ghi và ném {@link LeakException}.
 */
public final class BankImporter {

    /** {@code expected}: số câu kỳ vọng mỗi bộ theo thứ tự mcq, tf, sa (null = không kiểm). */
    public record Options(Path in, Path out, int[] expected, boolean convertImages, boolean validateLatex) {}

    public record Result(List<ParsedDoc> docs, Map<String, Object> report) {}

    /** Output chứa chuỗi không được phép (tên giáo viên, dòng quảng cáo). Thông báo không chứa chính chuỗi đó. */
    public static final class LeakException extends RuntimeException {
        public LeakException(String message) {
            super(message);
        }
    }

    private static final List<String> GENERIC_FORBIDDEN = List.of("tài liệu được chia sẻ", "http://", "https://", "www.", ".edu.vn");

    private static final ObjectMapper JSON = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static Result run(Options o) throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(o.in())) {
            files = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx"))
                    .filter(p -> !p.getFileName().toString().startsWith("~$")).sorted().toList();
        }
        Files.createDirectories(o.out());
        List<Path> created = new ArrayList<>();
        try {
            List<ParsedDoc> docs = new ArrayList<>();
            for (Path f : files) {
                docs.add(DocParser.parse(f, o.validateLatex()));
            }
            for (ParsedDoc d : docs) {
                writeDoc(o.out(), d, o.convertImages(), created);
            }
            Path catalog = o.out().resolve("catalog.csv");
            CatalogWriter.writeCatalog(catalog, docs);
            created.add(catalog);
            Path review = o.out().resolve("review.csv");
            CatalogWriter.writeReview(review, docs);
            created.add(review);
            Map<String, Object> report = ReportBuilder.build(docs, o.expected());
            Path reportFile = o.out().resolve("report.json");
            ReportBuilder.write(reportFile, report);
            created.add(reportFile);
            FormulaSheet.write(o.out(), docs, o.convertImages(), created::add);
            checkLeak(o.out(), docs, created);
            return new Result(docs, report);
        } catch (IOException | RuntimeException e) {
            if (e instanceof LeakException) {
                cleanup(created);
            }
            throw e;
        }
    }

    // ---------- ghi từng bộ ----------

    private static void writeDoc(Path out, ParsedDoc d, boolean convertImages, List<Path> created) throws IOException {
        // ảnh trước (và bản PNG nếu --convert-images) để JSON ghi được đường dẫn file_png
        List<Path[]> toConvert = new ArrayList<>();
        Map<Question.ImageRef, Path> pngOf = new java.util.IdentityHashMap<>();
        for (Question q : d.questions()) {
            for (Question.ImageRef im : q.images) {
                byte[] bytes = d.binaries().get(im.sourceRef);
                if (bytes == null) {
                    q.addFlag("image_missing_in_package");
                    continue;
                }
                Path target = out.resolve(im.file);
                Files.createDirectories(target.getParent());
                created.add(target.getParent().getParent());
                created.add(target.getParent());
                Files.write(target, bytes);
                created.add(target);
                if (convertImages && (im.sourceExt.equals("wmf") || im.sourceExt.equals("emf"))) {
                    Path png = target.resolveSibling(target.getFileName().toString().replaceFirst("\\.[^.]+$", "") + ".png");
                    toConvert.add(new Path[]{target, png});
                    pngOf.put(im, png);
                }
            }
        }
        for (Path png : ImageConverter.convert(toConvert, out)) {
            created.add(png);
        }
        for (Map.Entry<Question.ImageRef, Path> e : pngOf.entrySet()) {
            if (Files.isRegularFile(e.getValue())) {
                e.getKey().filePng = out.relativize(e.getValue()).toString().replace('\\', '/');
            }
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", "1.0");
        root.put("source", d.source());
        root.put("extracted_at", LocalDateTime.now().withNano(0).toString());
        root.put("questions", d.questions());
        Path json = out.resolve(d.source().docId() + ".json");
        JSON.writeValue(json.toFile(), root);
        created.add(json);
    }

    // ---------- chốt chặn rò rỉ ----------

    private static void checkLeak(Path out, List<ParsedDoc> docs, List<Path> created) throws IOException {
        List<String> forbidden = new ArrayList<>(GENERIC_FORBIDDEN);
        for (ParsedDoc d : docs) {
            if (d.teacher() != null && !d.teacher().isBlank()) {
                forbidden.add(Normalizer.normalize(d.teacher(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT));
            }
        }
        try (Stream<Path> walk = Files.walk(out)) {
            for (Path f : walk.filter(Files::isRegularFile).sorted().toList()) {
                String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!(name.endsWith(".json") || name.endsWith(".csv") || name.endsWith(".html"))) {
                    continue;
                }
                String text = Normalizer.normalize(Files.readString(f, StandardCharsets.UTF_8), Normalizer.Form.NFC)
                        .toLowerCase(Locale.ROOT);
                for (String bad : forbidden) {
                    if (text.contains(bad)) {
                        throw new LeakException("Phát hiện chuỗi cấm trong " + out.relativize(f)
                                + " (tên giáo viên hoặc dòng quảng cáo); toàn bộ output đã bị xóa.");
                    }
                }
            }
        }
    }

    private static void cleanup(List<Path> created) {
        for (int i = created.size() - 1; i >= 0; i--) {
            try {
                Files.deleteIfExists(created.get(i));
            } catch (IOException ignored) {
                // thư mục chưa rỗng sẽ được xóa ở lần sau; không để lỗi dọn dẹp che lỗi gốc
            }
        }
    }

    private BankImporter() {}
}
