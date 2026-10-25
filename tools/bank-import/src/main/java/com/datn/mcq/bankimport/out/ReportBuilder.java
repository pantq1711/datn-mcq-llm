package com.datn.mcq.bankimport.out;

import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.parse.ParsedDoc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Báo cáo chất lượng chuyển đổi (report.json). Chỉ có số liệu, id câu và mã lỗi; không có nội dung câu hỏi.
 */
public final class ReportBuilder {

    private static final List<String> TYPES = List.of("mcq", "tf", "sa");

    public static Map<String, Object> build(List<ParsedDoc> docs, int[] expected) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generated_at", java.time.LocalDateTime.now().withNano(0).toString());
        List<Map<String, Object>> docReports = new ArrayList<>();
        Tally total = new Tally();
        for (ParsedDoc d : docs) {
            Tally t = new Tally();
            for (Question q : d.questions()) {
                t.add(q);
            }
            total.addAll(t);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("doc_id", d.source().docId());
            m.put("grade", d.source().grade());
            m.put("chapter", d.source().chapter());
            m.put("lesson", d.source().lesson());
            m.putAll(t.toMap());
            List<Map<String, Object>> mismatch = new ArrayList<>();
            if (expected != null) {
                for (int i = 0; i < TYPES.size(); i++) {
                    int actual = t.byType.getOrDefault(TYPES.get(i), 0);
                    if (actual != expected[i]) {
                        mismatch.add(Map.of("type", TYPES.get(i), "expected", expected[i], "actual", actual));
                    }
                }
            }
            m.put("count_mismatch", mismatch);
            List<Map<String, String>> problems = new ArrayList<>();
            for (ParsedDoc.Problem p : d.problems()) {
                problems.add(Map.of("where", p.where(), "code", p.code()));
            }
            m.put("structure_problems", problems);
            docReports.add(m);
        }
        root.put("docs", docReports);
        root.put("totals", total.toMap());
        return root;
    }

    public static void write(Path file, Map<String, Object> report) throws IOException {
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(file.toFile(), report);
    }

    /** Cộng dồn số liệu. */
    private static final class Tally {
        int questions;
        final Map<String, Integer> byType = new TreeMap<>();
        final Map<String, Integer> levelRaw = new TreeMap<>();
        final Map<String, Integer> level3 = new TreeMap<>();
        final Map<String, Integer> flags = new TreeMap<>();
        final Map<String, Integer> unsupportedReasons = new TreeMap<>();
        int ok;
        int partial;
        int unsupported;
        int empty;
        int figure;
        int formulaImage;
        int oleDrawing;
        int withImage;
        int usable;
        int usableAndStructureOk;
        int structureOk;

        void add(Question q) {
            Summary s = new Summary(q);
            questions++;
            byType.merge(q.type, 1, Integer::sum);
            levelRaw.merge(String.valueOf(q.levelRaw), 1, Integer::sum);
            level3.merge(String.valueOf(q.level), 1, Integer::sum);
            for (String f : q.flags) {
                flags.merge(f, 1, Integer::sum);
                if (f.equals("empty_formula")) {
                    empty++;
                }
            }
            ok += s.formulaOk;
            partial += s.formulaPartial;
            unsupported += s.formulaUnsupported;
            for (Question.Formula f : q.formulas) {
                if (f.status.equals("unsupported")) {
                    unsupportedReasons.merge(f.reason == null ? "?" : f.reason, 1, Integer::sum);
                }
            }
            figure += s.figure;
            formulaImage += s.formulaImage;
            oleDrawing += s.oleDrawing;
            if (s.hasImage()) {
                withImage++;
            }
            if (s.usableTextOnly()) {
                usable++;
                if (s.structureOk) {
                    usableAndStructureOk++;
                }
            }
            if (s.structureOk) {
                structureOk++;
            }
        }

        void addAll(Tally o) {
            questions += o.questions;
            o.byType.forEach((k, v) -> byType.merge(k, v, Integer::sum));
            o.levelRaw.forEach((k, v) -> levelRaw.merge(k, v, Integer::sum));
            o.level3.forEach((k, v) -> level3.merge(k, v, Integer::sum));
            o.flags.forEach((k, v) -> flags.merge(k, v, Integer::sum));
            o.unsupportedReasons.forEach((k, v) -> unsupportedReasons.merge(k, v, Integer::sum));
            ok += o.ok;
            partial += o.partial;
            unsupported += o.unsupported;
            empty += o.empty;
            figure += o.figure;
            formulaImage += o.formulaImage;
            oleDrawing += o.oleDrawing;
            withImage += o.withImage;
            usable += o.usable;
            usableAndStructureOk += o.usableAndStructureOk;
            structureOk += o.structureOk;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("questions", questions);
            m.put("by_type", byType);
            m.put("level_raw", levelRaw);
            m.put("level", level3);
            Map<String, Object> images = new LinkedHashMap<>();
            images.put("figure", figure);
            images.put("formula_image", formulaImage);
            images.put("ole_drawing", oleDrawing);
            images.put("questions_with_image", withImage);
            m.put("images", images);
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("ok", ok);
            fm.put("partial", partial);
            fm.put("unsupported", unsupported);
            fm.put("empty", empty);
            int used = ok + partial + unsupported;
            fm.put("used_in_questions", used);
            fm.put("ok_rate", used == 0 ? null : Math.round(1000.0 * ok / used) / 10.0);
            fm.put("unsupported_reasons", unsupportedReasons);
            m.put("formulas", fm);
            m.put("structure_ok", structureOk);
            m.put("usable_text_only", usable);
            m.put("usable_text_only_and_structure_ok", usableAndStructureOk);
            m.put("flags", flags);
            return m;
        }
    }

    private ReportBuilder() {}
}
