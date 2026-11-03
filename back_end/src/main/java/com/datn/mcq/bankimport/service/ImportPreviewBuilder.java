package com.datn.mcq.bankimport.service;

import com.datn.mcq.bankimport.dto.BankImportDtos.ImportItem;
import com.datn.mcq.bankimport.dto.BankImportDtos.ItemOption;
import com.datn.mcq.bankimport.dto.BankImportDtos.ItemStatement;
import com.datn.mcq.bankimport.dto.BankImportDtos.PreviewData;
import com.datn.mcq.bankimport.dto.BankImportDtos.SkippedItem;
import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.parse.ParsedDoc;
import com.datn.mcq.common.util.LatexValidationRules;
import com.datn.mcq.common.util.ShortAnswerValue;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.qc.checker.DedupChecker;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupOutcome;
import com.datn.mcq.qc.dto.DedupStatus;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Câu đã tách từ file Word → bản xem trước (không ghi DB, không gọi mạng). Quyết định ngày 3/11:
 * <ul>
 *   <li>bỏ (và đếm theo lý do) câu có ảnh, câu có công thức chưa chuyển được, câu có bảng dữ liệu;</li>
 *   <li>mức NB → easy, TH → medium, VD/VDC → hard; không có nhãn mức là cờ chặn {@code level_missing};</li>
 *   <li>đáp số sa qua {@link ShortAnswerValue} chế độ STRICT; không đạt là cờ chặn {@code sa_answer_format}, giữ nguyên
 *       văn (không âm thầm bỏ);</li>
 *   <li>LaTeX kiểm bằng {@link LatexValidationRules}: lỗi ở đề, phương án, ý, đáp số là cờ chặn; ở lời giải và cảnh báo là
 *       cờ thông tin;</li>
 *   <li>không có lời giải: cờ thông tin {@code no_solution}, explanation NULL;</li>
 *   <li>trùng (dedup 3-gram trong bộ nhớ, như QC) với câu ngân hàng người tải được thấy hoặc với câu trước trong cùng lô:
 *       cờ thông tin, không chặn.</li>
 * </ul>
 */
public final class ImportPreviewBuilder {

    private static final List<String> AD_MARKERS = List.of("tài liệu được chia sẻ", "http://", "https://", "www.",
            ".edu.vn");
    private static final Pattern FORMULA_TOKEN = Pattern.compile("⟦f\\d+⟧");

    private ImportPreviewBuilder() {}

    /**
     * @param bankCandidates câu ngân hàng người tải được thấy (cùng môn); id là id câu nguồn, chỉ dùng để so
     */
    public static PreviewData build(ParsedDoc doc, List<DedupCandidate> bankCandidates) {
        List<ImportItem> items = new ArrayList<>();
        List<SkippedItem> skipped = new ArrayList<>();
        List<DedupCandidate> inBatch = new ArrayList<>();
        List<String> leakNeedles = leakNeedles(doc.teacher());
        for (Question q : doc.questions()) {
            String reason = skipReason(q);
            if (reason != null) {
                skipped.add(new SkippedItem(q.locator, q.type, reason));
                continue;
            }
            ImportItem item = item(q, leakNeedles);
            DedupCandidate self = candidate(item, -(items.size() + 1));
            List<String> flags = new ArrayList<>(item.flags());
            if (self != null) {
                if (isDuplicate(self, bankCandidates)) {
                    flags.add(ImportFlags.DUPLICATE_IN_BANK);
                }
                if (isDuplicate(self, inBatch)) {
                    flags.add(ImportFlags.DUPLICATE_IN_BATCH);
                }
                inBatch.add(self);
            }
            items.add(withFlags(item, flags));
        }
        return new PreviewData(List.copyOf(items), List.copyOf(skipped));
    }

    private static String skipReason(Question q) {
        if (q.hasImage || !q.images.isEmpty()) {
            return ImportFlags.SKIP_HAS_IMAGE;
        }
        if (q.flags.contains("formula_unsupported") || containsFormulaToken(q)) {
            return ImportFlags.SKIP_FORMULA_UNSUPPORTED;
        }
        if (q.tables != null && !q.tables.isEmpty()) {
            return ImportFlags.SKIP_HAS_TABLE;
        }
        return null;
    }

    private static boolean containsFormulaToken(Question q) {
        for (String s : texts(q)) {
            if (s != null && FORMULA_TOKEN.matcher(s).find()) {
                return true;
            }
        }
        return q.solution != null && FORMULA_TOKEN.matcher(q.solution).find();
    }

    /** Các trường văn bản của câu sẽ vào ngân hàng (không gồm lời giải). */
    private static List<String> texts(Question q) {
        List<String> out = new ArrayList<>();
        out.add(q.stem);
        if (q.options != null) {
            q.options.forEach(o -> out.add(o.text));
        }
        if (q.statements != null) {
            q.statements.forEach(s -> out.add(s.text));
        }
        if (q.answer != null) {
            out.add(q.answer.text);
        }
        return out;
    }

    private static ImportItem item(Question q, List<String> leakNeedles) {
        List<String> flags = new ArrayList<>(q.flags);
        QuestionType type = switch (q.type) {
            case "mcq" -> QuestionType.MCQ;
            case "tf" -> QuestionType.TF;
            default -> QuestionType.SA;
        };
        String stem = blankToNull(q.stem);
        List<ItemOption> options = null;
        String correct = null;
        List<ItemStatement> statements = null;
        String saAnswer = null;
        List<String> fieldTexts = new ArrayList<>();
        fieldTexts.add(stem);
        boolean empty = stem == null;
        switch (type) {
            case MCQ -> {
                if (q.options != null) {
                    options = new ArrayList<>();
                    for (Question.Option o : q.options) {
                        String text = blankToNull(o.text);
                        empty |= text == null;
                        options.add(new ItemOption(o.key, text));
                        fieldTexts.add(text);
                    }
                }
                correct = q.answer == null ? null : q.answer.value;
            }
            case TF -> {
                statements = new ArrayList<>();
                if (q.statements != null) {
                    for (Question.Statement s : q.statements) {
                        String text = blankToNull(s.text);
                        empty |= text == null;
                        statements.add(new ItemStatement(s.key, text, s.truth));
                        fieldTexts.add(text);
                    }
                }
            }
            case SA -> {
                String raw = q.answer == null ? null : blankToNull(q.answer.text);
                if (raw != null) {
                    String normalized = ShortAnswerValue.normalizedStrictAnswer(raw);
                    if (normalized == null || !ShortAnswerValue.parse(raw, ShortAnswerValue.Mode.STRICT).ok()) {
                        flags.add(ImportFlags.SA_ANSWER_FORMAT);
                        saAnswer = raw;
                    } else {
                        saAnswer = normalized;
                        fieldTexts.add(normalized);
                    }
                }
            }
        }
        if (empty) {
            flags.add(ImportFlags.EMPTY_FIELD);
        }
        boolean latexError = false;
        boolean latexWarning = false;
        for (String text : fieldTexts) {
            if (text == null) {
                continue;
            }
            LatexValidationRules.Result r = LatexValidationRules.check(text);
            latexError |= r.hasErrors();
            latexWarning |= !r.warnings().isEmpty();
        }
        String explanation = blankToNull(q.solution);
        if (explanation == null) {
            flags.add(ImportFlags.NO_SOLUTION);
        } else {
            LatexValidationRules.Result r = LatexValidationRules.check(explanation);
            if (r.hasErrors()) {
                flags.add(ImportFlags.SOLUTION_LATEX_ERROR);
            }
            latexWarning |= !r.warnings().isEmpty();
        }
        if (latexError) {
            flags.add(ImportFlags.LATEX_ERROR);
        }
        if (latexWarning) {
            flags.add(ImportFlags.LATEX_WARNING);
        }
        List<String> all = new ArrayList<>(fieldTexts);
        all.add(explanation);
        if (containsAny(all, leakNeedles)) {
            flags.add(ImportFlags.LEAK_SUSPECT);
        }
        return new ImportItem(q.id, type.getDbValue(), q.locator, q.levelRaw, q.level, stem,
                options == null ? null : List.copyOf(options), correct,
                statements == null ? null : List.copyOf(statements), saAnswer, explanation, List.copyOf(flags),
                false);
    }

    private static ImportItem withFlags(ImportItem i, List<String> flags) {
        List<String> distinct = flags.stream().distinct().toList();
        boolean blocking = distinct.stream().anyMatch(ImportFlags::isBlocking);
        return new ImportItem(i.key(), i.type(), i.locator(), i.levelRaw(), i.level(), i.stem(), i.options(),
                i.correctOption(), i.statements(), i.saAnswer(), i.explanation(), distinct, blocking);
    }

    /** Nội dung để so trùng; null khi câu thiếu nội dung (đã có cờ chặn, không so). */
    static DedupCandidate candidate(ImportItem i, int syntheticId) {
        DedupCandidate c;
        switch (i.type()) {
            case "mcq" -> {
                if (i.options() == null || i.options().size() != 4) {
                    return null;
                }
                c = new DedupCandidate(syntheticId, i.stem(), i.options().get(0).text(), i.options().get(1).text(),
                        i.options().get(2).text(), i.options().get(3).text());
            }
            case "tf" -> {
                if (i.statements() == null || i.statements().size() != 4) {
                    return null;
                }
                c = DedupCandidate.trueFalse(syntheticId, i.stem(),
                        i.statements().stream().map(ItemStatement::text).toList());
            }
            default -> c = DedupCandidate.shortAnswer(syntheticId, i.stem(), i.saAnswer());
        }
        return c.hasContent() ? c : null;
    }

    private static boolean isDuplicate(DedupCandidate subject, List<DedupCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        try {
            DedupOutcome o = DedupChecker.check(subject, candidates, DedupChecker.PROVISIONAL_THRESHOLD);
            return o.status() == DedupStatus.DUPLICATE;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static List<String> leakNeedles(String teacher) {
        List<String> out = new ArrayList<>(AD_MARKERS);
        if (teacher != null && teacher.strip().length() >= 4) {
            out.add(normalize(teacher.strip()));
        }
        return out;
    }

    private static boolean containsAny(List<String> texts, List<String> needles) {
        for (String t : texts) {
            if (t == null) {
                continue;
            }
            String n = normalize(t);
            for (String needle : needles) {
                if (n.contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
