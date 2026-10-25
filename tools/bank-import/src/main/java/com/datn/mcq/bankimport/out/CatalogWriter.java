package com.datn.mcq.bankimport.out;

import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.parse.ParsedDoc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Danh mục CSV (lọc tập dùng cho thí nghiệm) và bảng review (danh sách câu cần người kiểm). */
public final class CatalogWriter {

    private static final List<String> CATALOG_HEADER = List.of("id", "doc_id", "grade", "chapter", "lesson", "part", "type",
            "ordinal", "level_raw", "level", "answer", "has_image", "figure_count", "formula_image_count", "ole_drawing_count",
            "formula_count", "formula_ok", "formula_partial", "formula_unsupported", "formula_status", "usable_text_only",
            "structure_ok", "flags", "locator");

    private static final List<String> REVIEW_HEADER = List.of("id", "locator", "type", "level_raw", "answer", "evidence", "flags",
            "sample_reason", "checked", "verdict", "note");

    /** Tỉ lệ câu trắc nghiệm không bị cờ được lấy mẫu để kiểm tay. */
    static final double MCQ_SAMPLE_RATE = 0.20;

    public static void writeCatalog(Path file, List<ParsedDoc> docs) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(Csv.line(CATALOG_HEADER));
        for (ParsedDoc d : docs) {
            for (Question q : d.questions()) {
                Summary s = new Summary(q);
                lines.add(Csv.line(List.of(q.id, d.source().docId(), nz(d.source().grade()), nz(d.source().chapter()),
                        nz(d.source().lesson()), q.part, q.type, q.ordinal, nz(q.levelRaw), nz(q.level), Summary.answerText(q),
                        s.hasImage(), s.figure, s.formulaImage, s.oleDrawing, q.formulas.size(), s.formulaOk, s.formulaPartial,
                        s.formulaUnsupported, s.formulaStatus(), s.usableTextOnly(), s.structureOk,
                        String.join(";", q.flags), q.locator)));
            }
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /**
     * Review: 100% câu đúng/sai và trả lời ngắn (đọc nhanh một dòng), mọi câu có cờ, và mẫu 20% trắc nghiệm không cờ
     * (hạt giống cố định để lần chạy lại ra cùng danh sách).
     */
    public static void writeReview(Path file, List<ParsedDoc> docs) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(Csv.line(REVIEW_HEADER));
        for (ParsedDoc d : docs) {
            List<Question> cleanMcq = new ArrayList<>();
            for (Question q : d.questions()) {
                if (q.type.equals("mcq") && q.flags.isEmpty()) {
                    cleanMcq.add(q);
                }
            }
            Collections.shuffle(cleanMcq, new Random(42));
            int take = (int) Math.ceil(cleanMcq.size() * MCQ_SAMPLE_RATE);
            List<String> sampled = cleanMcq.subList(0, take).stream().map(q -> q.id).toList();
            for (Question q : d.questions()) {
                String reason = !q.flags.isEmpty() ? "flagged" : q.type.equals("tf") ? "tf_100" : q.type.equals("sa") ? "sa_100"
                        : sampled.contains(q.id) ? "mcq_sample20" : null;
                if (reason == null) {
                    continue;
                }
                String evidence = q.answer != null && q.answer.evidence != null ? q.answer.evidence
                        : q.statements != null && !q.statements.isEmpty() && q.statements.get(0).evidence != null
                        ? q.statements.get(0).evidence : "";
                lines.add(Csv.line(List.of(q.id, q.locator, q.type, nz(q.levelRaw), Summary.answerText(q), evidence,
                        String.join(";", q.flags), reason, "", "", "")));
            }
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static String nz(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private CatalogWriter() {}
}
