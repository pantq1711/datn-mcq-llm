package com.datn.mcq.bankimport.parse;

import com.datn.mcq.bankimport.docx.Block;
import com.datn.mcq.bankimport.docx.Block.Cell;
import com.datn.mcq.bankimport.docx.Block.Para;
import com.datn.mcq.bankimport.docx.Block.Tbl;
import com.datn.mcq.bankimport.docx.DocxReader;
import com.datn.mcq.bankimport.docx.RichText;
import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.model.Question.Answer;
import com.datn.mcq.bankimport.model.Question.Option;
import com.datn.mcq.bankimport.model.Question.Statement;
import com.datn.mcq.bankimport.parse.ParsedDoc.Problem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tách một bộ câu hỏi .docx thành {@link Question}: phần I trắc nghiệm (mcq), phần II đúng sai (tf), phần III trả lời
 * ngắn (sa). Mọi điểm không chắc chắn được ghi thành cờ; không đoán đáp án, mức hay công thức.
 */
public final class DocParser {

    private static final Pattern HEADING = Pattern.compile("^\\s*PH[ẦA]N\\s+(I{1,3})\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern AD = Pattern.compile("(tài liệu được chia sẻ|website|https?://|www\\.|\\.edu\\.vn)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SOLUTION = Pattern.compile(
            "^[\\s\\uE001\\uFFFC]*(?:(?:Hướng\\s+dẫn\\s+giải|Lời\\s+giải|Hướng\\s+dẫn|HDG)\\s*[:.]?|Giải\\s*:)\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ANSWER_SA = Pattern.compile("^[\\s\\uE001\\uFFFC]*Đáp\\s+số\\s*[:.]?\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern REPLY_LINE = Pattern.compile("^[\\s\\uE001\\uFFFC]*Trả\\s+lời\\s*[:.]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ANSWER_TF = Pattern.compile("^\\s*Đáp\\s+án\\s*:\\s*((?:[ĐS]\\s*){4})\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STATEMENT_LABEL = Pattern.compile("^[\\s\\uE001]*[a-dA-D]\\s*[).]\\s*");
    private static final Pattern OPTION_LABEL = Pattern.compile("(?<![^\\s\\u00A0\\uE001\\uFFFC])([ABCD])\\s*[.)]");
    private static final Pattern NUMERIC = Pattern.compile("^-?\\d+(?:[.,]\\d+)?$");
    /** Chữ giữ chỗ của mẫu còn sót sau nhãn mức ("[NB] Câu hỏi…"). */
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\s*Câu\\s+hỏi\\s*(?:…|\\.{3,})\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Câu phụ tự luận "a/ ..." còn sót ở cuối đề dẫn câu Đúng/Sai (khác nhãn ý "a)"). */
    private static final Pattern SUB_QUESTION = Pattern.compile("^[\\s\\uE001]*[a-dA-D]\\s*/");

    private static final String LEVEL = "VDC|VẬN\\s*DỤNG\\s*CAO|VD|VẬN\\s*DỤNG|THÔNG\\s*HIỂU|TH|NHẬN\\s*BIẾT|NB";
    /** "Câu N." + nhãn mức ở các dạng: [NB], [[NHẬN BIẾT], NHẬN BIẾT], "Câu 2 [NB].", "Câu 2. [NB]". */
    private static final Pattern STEM_HEAD = Pattern.compile(
            "^(?<lead>[\\s\\uE001]*)(?<cau>Câu\\s*\\d+\\s*[.:]?\\s*)?(?:(?:\\[{1,2}\\s*(?<a>" + LEVEL + ")\\s*\\]?|(?<b>" + LEVEL
                    + ")\\s*\\])\\s*[.:]?\\s*)?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private record Head(String levelRaw, int omitFrom, int omitTo) {}

    /** Đoạn mở đầu một câu (có "Câu N" hoặc nhãn mức) → thông tin nhãn; null nếu không phải. */
    private static Head head(RichText rt) {
        Matcher m = STEM_HEAD.matcher(rt.s);
        if (!m.find() || (m.group("cau") == null && m.group("a") == null && m.group("b") == null)) {
            return null;
        }
        String lv = m.group("a") != null ? m.group("a") : m.group("b");
        if (lv == null) {            // không có nhãn trong chữ: thử đoạn tô cyan
            StringBuilder cy = new StringBuilder();
            for (int i = 0; i < rt.length(); i++) {
                if (rt.cyan[i]) {
                    cy.append(rt.s.charAt(i));
                }
            }
            Matcher cm = Pattern.compile("(" + LEVEL + ")", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(cy);
            lv = cm.find() ? cm.group(1) : null;
        }
        return new Head(normalizeLevel(lv), m.end("lead"), m.end());
    }

    private static String normalizeLevel(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toUpperCase(Locale.ROOT).replaceAll("\\s+", "")) {
            case "NB", "NHẬNBIẾT" -> "NB";
            case "TH", "THÔNGHIỂU" -> "TH";
            case "VD", "VẬNDỤNG" -> "VD";
            case "VDC", "VẬNDỤNGCAO" -> "VDC";
            default -> null;
        };
    }

    private static String easyMediumHard(String raw) {
        return raw == null ? null : switch (raw) {
            case "NB" -> "easy";
            case "TH" -> "medium";
            default -> "hard";
        };
    }

    // ---------- điểm vào ----------

    public static ParsedDoc parse(Path file) throws IOException {
        return parse(file, true);
    }

    public static ParsedDoc parse(Path file, boolean validateLatex) throws IOException {
        DocxReader.Result pkg = DocxReader.read(file);
        String sha = sha256(Files.readAllBytes(file)).substring(0, 16);
        LessonName.Parsed ln = LessonName.parse(file.getFileName().toString(), sha);
        DocParser p = new DocParser(ln, pkg, validateLatex);
        return p.run();
    }

    private final LessonName.Parsed ln;
    private final DocxReader.Result pkg;
    private final FormulaService formulas;
    private final List<Problem> problems = new ArrayList<>();

    private DocParser(LessonName.Parsed ln, DocxReader.Result pkg, boolean validateLatex) {
        this.ln = ln;
        this.pkg = pkg;
        this.formulas = new FormulaService(pkg.binaries(), validateLatex);
    }

    private ParsedDoc run() {
        Map<String, List<Block>> regions = new LinkedHashMap<>();
        for (String part : List.of("I", "II", "III")) {
            regions.put(part, new ArrayList<>());
        }
        String cur = null;
        for (Block b : pkg.blocks()) {
            if (b instanceof Para p) {
                Matcher h = HEADING.matcher(p.text().s);
                if (h.find()) {
                    cur = h.group(1).toUpperCase(Locale.ROOT);
                    continue;
                }
                if (cur != null && AD.matcher(p.text().plain()).find()) {
                    continue;                         // dòng quảng cáo / liên kết
                }
            }
            if (cur != null && regions.containsKey(cur)) {      // trước "PHẦN I" là bảng tiêu đề: bỏ hết
                regions.get(cur).add(b);
            }
        }
        List<Question> out = new ArrayList<>();
        int n = 0;
        for (List<Block> qb : splitByStem(regions.get("I"))) {
            out.add(mcq(qb, ++n));
        }
        n = 0;
        for (Group g : tfGroups(regions.get("II"))) {
            out.add(tf(g, ++n));
        }
        n = 0;
        for (SaPart sp : saParts(regions.get("III"))) {
            out.add(sa(sp.blocks(), ++n, sp.context()));
        }
        for (Question q : out) {
            attachMissingImageTags(q);
            q.hasImage = !q.images.isEmpty();
            q.contentHash = hash(q);
            for (String f : q.flags) {
                if (isStructural(f)) {
                    problems.add(new Problem(q.id, f));
                }
            }
        }
        return new ParsedDoc(ln.source(), ln.teacher(), out, problems, pkg.binaries());
    }

    /**
     * Bổ sung thẻ ⟦imgN⟧ vào trường tương ứng nếu ảnh chưa có thẻ trong bất kỳ trường văn bản nào của câu.
     */
    static void attachMissingImageTags(Question q) {
        if (q.images == null || q.images.isEmpty()) {
            return;
        }
        for (Question.ImageRef im : q.images) {
            String tag = "⟦" + im.id + "⟧";
            if (!containsImageTag(q, tag)) {
                if ("stem".equals(im.role)) {
                    q.stem = appendImageTag(q.stem, tag);
                } else if ("solution".equals(im.role)) {
                    q.solution = appendImageTag(q.solution, tag);
                }
            }
        }
    }

    private static boolean containsImageTag(Question q, String tag) {
        if (q.stem != null && q.stem.contains(tag)) {
            return true;
        }
        if (q.options != null) {
            for (Question.Option o : q.options) {
                if (o.text != null && o.text.contains(tag)) {
                    return true;
                }
            }
        }
        if (q.statements != null) {
            for (Question.Statement s : q.statements) {
                if (s.text != null && s.text.contains(tag)) {
                    return true;
                }
            }
        }
        if (q.solution != null && q.solution.contains(tag)) {
            return true;
        }
        if (q.tables != null) {
            for (List<List<String>> tbl : q.tables) {
                for (List<String> row : tbl) {
                    for (String cell : row) {
                        if (cell != null && cell.contains(tag)) {
                            return true;
                        }
                    }
                }
            }
        }
        if (q.answer != null && q.answer.text != null && q.answer.text.contains(tag)) {
            return true;
        }
        return false;
    }

    private static String appendImageTag(String text, String tag) {
        if (text == null || text.isEmpty()) {
            return tag;
        }
        return text + "\n" + tag;
    }

    /** Cờ cho biết cấu trúc câu chưa đủ tin cậy (khác với cờ thông tin như formula_partial). */
    public static boolean isStructural(String flag) {
        return flag.startsWith("answer_") || flag.startsWith("structure_");
    }

    // ---------- tách theo đầu câu ----------

    private static List<List<Block>> splitByStem(List<Block> region) {
        List<List<Block>> out = new ArrayList<>();
        List<Block> cur = null;
        for (Block b : region) {
            if (b instanceof Para p && head(p.text()) != null) {
                cur = new ArrayList<>();
                out.add(cur);
            }
            if (cur != null) {
                cur.add(b);
            }
        }
        return out;
    }

    /** Đoạn (kể cả đoạn trong bảng) theo thứ tự tài liệu; tbl = chỉ số bảng trong câu, -1 nếu là đoạn ở thân. */
    private record FP(Para para, int tbl) {}

    private static List<FP> flatten(List<Block> blocks, List<Tbl> tables) {
        List<FP> out = new ArrayList<>();
        for (Block b : blocks) {
            if (b instanceof Para p) {
                out.add(new FP(p, -1));
            } else if (b instanceof Tbl t) {
                tables.add(t);
                int idx = tables.size() - 1;
                for (List<Cell> row : t.rows()) {
                    for (Cell c : row) {
                        for (Para p : c.paras()) {
                            out.add(new FP(p, idx));
                        }
                    }
                }
            }
        }
        return out;
    }

    private Question newQuestion(String type, String part, int ordinal) {
        Question q = new Question();
        q.type = type;
        q.part = part;
        q.ordinal = ordinal;
        q.id = ln.source().docId() + "-" + type + "-" + String.format("%03d", ordinal);
        q.locator = "Phần " + part + ", câu thứ " + ordinal;
        return q;
    }

    private void setLevel(Question q, Head h) {
        q.levelRaw = h == null ? null : h.levelRaw();
        q.level = easyMediumHard(q.levelRaw);
        if (q.levelRaw == null) {
            q.addFlag("level_missing");
        }
    }

    private List<List<String>> renderTable(Tbl t, QCtx c, String role) {
        List<List<String>> rows = new ArrayList<>();
        for (List<Cell> row : t.rows()) {
            List<String> cells = new ArrayList<>();
            for (Cell cell : row) {
                List<RichText> parts = new ArrayList<>();
                for (Para p : cell.paras()) {
                    parts.add(p.text());
                }
                cells.add(c.render(RichText.join(parts), role).replace("\n", " "));
            }
            rows.add(cells);
        }
        return rows;
    }

    private static boolean isSolutionStart(Para p) {
        return SOLUTION.matcher(p.text().s).find();
    }

    /** Lời giải từ các đoạn đã cho: bỏ nhãn "Hướng dẫn giải:" ở đoạn đầu, bỏ dòng trống. */
    private String solution(List<Para> paras, QCtx c) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < paras.size(); i++) {
            RichText rt = paras.get(i).text();
            Matcher m = SOLUTION.matcher(rt.s);
            int omit = i == 0 && m.find() ? m.end() : 0;
            String s = c.render(rt, "solution", 0, omit);
            if (!s.isEmpty()) {
                lines.add(s);
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    // ---------- phần I: trắc nghiệm ----------

    private record Labels(int[][] pos) {}

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || c == ' ';
    }

    /**
     * Nhãn "mạnh": đứng đầu đoạn, sau tab, sau công thức/ảnh, hoặc sau từ hai khoảng trắng. Nội dung phương án có thể chứa
     * chuỗi giống nhãn (ví dụ "Điểm C." sau đúng một dấu cách) nên nhãn mạnh được ưu tiên.
     */
    private static boolean strongLabel(String s, int pos) {
        if (pos == 0) {
            return true;
        }
        char p = s.charAt(pos - 1);
        if (p == '\n' || p == '\t' || p == RichText.EQ || p == RichText.IMG || p == '￼') {
            return true;
        }
        return pos >= 2 && isSpace(p) && isSpace(s.charAt(pos - 2));
    }

    /** Tìm nhãn A, B, C, D theo đúng thứ tự: lấy chữ A cuối cùng còn đủ B, C, D phía sau (đề có thể chứa "A." trước đó). */
    private static Labels findLabels(String s) {
        Labels strong = findLabels(s, true);
        return strong != null ? strong : findLabels(s, false);
    }

    private static Labels findLabels(String s, boolean strongOnly) {
        List<List<int[]>> cands = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        Matcher m = OPTION_LABEL.matcher(s);
        while (m.find()) {
            if (!strongOnly || strongLabel(s, m.start())) {
                cands.get(m.group(1).charAt(0) - 'A').add(new int[]{m.start(), m.end()});
            }
        }
        int[][] best = null;
        for (int[] a : cands.get(0)) {
            int[][] seq = new int[4][];
            seq[0] = a;
            boolean ok = true;
            for (int k = 1; k < 4 && ok; k++) {
                int[] prev = seq[k - 1];
                seq[k] = cands.get(k).stream().filter(x -> x[0] >= prev[1]).findFirst().orElse(null);
                ok = seq[k] != null;
            }
            if (ok) {
                best = seq;
            }
        }
        return best == null ? null : new Labels(best);
    }

    private record Whole(RichText rt, int[] start, int[] end, List<FP> fps) {}

    private static Whole whole(List<FP> fps) {
        List<RichText> parts = new ArrayList<>();
        int[] start = new int[fps.size()];
        int[] end = new int[fps.size()];
        int off = 0;
        for (int i = 0; i < fps.size(); i++) {
            RichText rt = fps.get(i).para().text();
            parts.add(rt);
            start[i] = off;
            end[i] = off + rt.length();
            off = end[i] + 1;
        }
        return new Whole(RichText.join(parts), start, end, fps);
    }

    private Question mcq(List<Block> qb, int ordinal) {
        Question q = newQuestion("mcq", "I", ordinal);
        QCtx c = new QCtx(q, ln.source().docId(), formulas);
        List<Tbl> tables = new ArrayList<>();
        List<FP> fps = flatten(qb, tables);
        Head head = head(fps.get(0).para().text());
        setLevel(q, head);

        int solIdx = -1;
        for (int i = 0; i < fps.size(); i++) {
            if (fps.get(i).tbl() < 0 && isSolutionStart(fps.get(i).para())) {
                solIdx = i;
                break;
            }
        }
        List<FP> body = solIdx < 0 ? fps : fps.subList(0, solIdx);
        List<FP> sol = solIdx < 0 ? List.of() : fps.subList(solIdx, fps.size());

        // lượt 1: nhãn A-D chỉ nằm ở đoạn thân; lượt 2: phương án nằm trong bảng bố cục
        List<FP> bodyOnly = body.stream().filter(f -> f.tbl() < 0).toList();
        Whole w = whole(bodyOnly);
        Labels labels = findLabels(w.rt().s);
        java.util.Set<Integer> layoutTables = new java.util.HashSet<>();
        if (labels == null) {
            Whole all = whole(body);
            Labels l2 = findLabels(all.rt().s);
            if (l2 != null) {
                for (int[] lab : l2.pos()) {
                    for (int i = 0; i < all.fps().size(); i++) {
                        if (lab[0] >= all.start()[i] && lab[0] <= all.end()[i] && all.fps().get(i).tbl() >= 0) {
                            layoutTables.add(all.fps().get(i).tbl());
                        }
                    }
                }
                List<FP> used = body.stream().filter(f -> f.tbl() < 0 || layoutTables.contains(f.tbl())).toList();
                w = whole(used);
                labels = findLabels(w.rt().s);
            }
        }

        // bảng dữ liệu trong đề (không phải bảng bố cục)
        q.tables = new ArrayList<>();
        for (int t = 0; t < tables.size(); t++) {
            if (!layoutTables.contains(t)) {
                q.tables.add(renderTable(tables.get(t), c, "stem"));
            }
        }
        if (q.tables.isEmpty()) {
            q.tables = null;
        }

        if (labels == null) {
            q.stem = c.render(w.rt(), "stem", head.omitFrom(), head.omitTo());
            q.addFlag("structure_options_not_found");
            q.answer = answer(null, "none");
        } else {
            int[][] lab = labels.pos();
            q.stem = c.render(w.rt().slice(0, lab[0][0]), "stem", head.omitFrom(), head.omitTo());
            q.options = new ArrayList<>();
            List<String> hit = new ArrayList<>();
            int[] spanEnd = new int[4];
            for (int k = 0; k < 4; k++) {
                int next = k < 3 ? lab[k + 1][0] : w.rt().length();
                int paraEnd = w.rt().length();
                for (int i = 0; i < w.fps().size(); i++) {
                    if (lab[k][0] >= w.start()[i] && lab[k][0] <= w.end()[i]) {
                        paraEnd = w.end()[i];
                    }
                }
                spanEnd[k] = Math.min(next, paraEnd);
                String key = String.valueOf((char) ('A' + k));
                q.options.add(new Option(key, c.render(w.rt().slice(lab[k][1], spanEnd[k]), "option_" + key)));
                if (w.rt().hasYellow(lab[k][0], spanEnd[k])) {
                    hit.add(key);
                }
            }
            // ảnh nằm ngoài mọi phương án (ô ảnh của bảng bố cục, hình đứng giữa các phương án) → ảnh của đề
            for (int i = lab[0][0]; i < w.rt().length(); i++) {
                boolean inSpan = false;
                for (int k = 0; k < 4; k++) {
                    inSpan |= i >= lab[k][0] && i < spanEnd[k];
                }
                if (!inSpan && w.rt().objIdx[i] >= 0) {
                    c.registerImage(w.rt().objs.get(w.rt().objIdx[i]), "stem");
                }
            }
            q.answer = answer(hit.size() == 1 ? hit.get(0) : null, hit.isEmpty() ? "none" : "highlight_yellow");
            if (hit.isEmpty()) {
                q.addFlag("answer_missing");
            } else if (hit.size() > 1) {
                q.addFlag("answer_ambiguous");
                q.answer.evidence = "highlight_yellow_multiple";
            }
        }
        List<Para> solParas = sol.stream().map(FP::para).toList();
        q.solution = solution(solParas, c);
        return q;
    }

    private static Answer answer(String value, String evidence) {
        Answer a = new Answer();
        a.value = value;
        a.evidence = evidence;
        return a;
    }

    // ---------- phần II: đúng sai ----------

    private static final class Group {
        final List<Para> stem = new ArrayList<>();
        final List<Para> hint = new ArrayList<>();        // hàng một ô không nhãn nằm sau các ý ("Gợi ý: ...")
        final List<Cell> statements = new ArrayList<>();
        final List<Para> tail = new ArrayList<>();
    }

    private static boolean isHeaderRow(List<Cell> row) {
        if (row.size() < 3) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (Para p : row.get(1).paras()) {
            sb.append(p.text().plain());
        }
        return sb.toString().replace("￼", "").strip().equalsIgnoreCase("Đúng");
    }

    private List<Group> tfGroups(List<Block> region) {
        List<Group> groups = new ArrayList<>();
        Group cur = null;                  // nhóm đang nhận ý / phần đuôi
        List<Para> pendingStem = new ArrayList<>();
        for (Block b : region) {
            if (b instanceof Para p) {
                if (head(p.text()) != null) {                 // đề dẫn nằm trong đoạn văn đứng trước bảng
                    cur = null;
                    pendingStem = new ArrayList<>(List.of(p));
                } else if (cur == null && !pendingStem.isEmpty()) {
                    pendingStem.add(p);
                } else if (cur != null) {
                    cur.tail.add(p);
                }
                continue;
            }
            for (List<Cell> row : ((Tbl) b).rows()) {
                if (row.size() == 1) {                         // hàng một ô (gộp): đề dẫn, hoặc lời gợi ý sau các ý
                    boolean isStem = row.get(0).paras().stream().anyMatch(p -> head(p.text()) != null);
                    if (cur != null && !cur.statements.isEmpty() && !isStem) {
                        cur.hint.addAll(row.get(0).paras());
                        continue;
                    }
                    cur = new Group();
                    cur.stem.addAll(row.get(0).paras());
                    groups.add(cur);
                    pendingStem = new ArrayList<>();
                } else if (isHeaderRow(row)) {
                    if (cur == null || !cur.statements.isEmpty()) {
                        cur = new Group();
                        cur.stem.addAll(pendingStem);
                        groups.add(cur);
                        pendingStem = new ArrayList<>();
                    }
                } else if (row.size() >= 3) {
                    if (cur == null) {
                        cur = new Group();
                        cur.stem.addAll(pendingStem);
                        groups.add(cur);
                        pendingStem = new ArrayList<>();
                    }
                    cur.statements.add(row.get(0));
                }
            }
        }
        return groups;
    }

    private Question tf(Group g, int ordinal) {
        Question q = newQuestion("tf", "II", ordinal);
        QCtx c = new QCtx(q, ln.source().docId(), formulas);
        List<Para> stem = new ArrayList<>(g.stem.stream().filter(p -> !p.text().isBlank() || !p.text().objs.isEmpty()).toList());
        Head head = stem.isEmpty() ? null : head(stem.get(0).text());
        setLevel(q, head);
        boolean extra = false;
        // câu phụ "a/ b/" liền nhau ở cuối đề dẫn: sót lại từ bản tự luận, các ý a-d đã thay chúng
        while (stem.size() > 1 && SUB_QUESTION.matcher(stem.get(stem.size() - 1).text().s).find()) {
            stem.remove(stem.size() - 1);
            extra = true;
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < stem.size(); i++) {
            RichText rt = stem.get(i).text();
            String s = i == 0 && head != null ? c.render(rt, "stem", head.omitFrom(), head.omitTo()) : c.render(rt, "stem");
            if (i == 0 && PLACEHOLDER.matcher(s).matches()) {
                extra = true;
                continue;
            }
            if (!s.isEmpty()) {
                lines.add(s);
            }
        }
        if (extra) {
            q.addFlag("stem_extra_text");
        }
        q.stem = String.join("\n", lines);
        // hàng "Gợi ý" sau các ý thuộc lời giải, đứng trước lời giải ở đoạn sau bảng
        List<String> hint = new ArrayList<>();
        for (Para p : g.hint) {
            String s = c.render(p.text(), "solution");
            if (!s.isEmpty()) {
                hint.add(s);
                q.addFlag("hint_after_statements");
            }
        }

        q.statements = new ArrayList<>();
        boolean anyYellow = false;
        List<Boolean> yellow = new ArrayList<>();
        for (int k = 0; k < g.statements.size(); k++) {
            List<RichText> parts = new ArrayList<>();
            for (Para p : g.statements.get(k).paras()) {
                parts.add(p.text());
            }
            RichText rt = RichText.join(parts);
            Matcher lm = STATEMENT_LABEL.matcher(rt.s);
            int omit = lm.find() ? lm.end() : 0;
            String key = String.valueOf((char) ('a' + k));
            Statement st = new Statement(key, c.render(rt, "statement_" + key, 0, omit).replace("\n", " "));
            q.statements.add(st);
            boolean y = rt.hasYellow(0, rt.length());
            yellow.add(y);
            anyYellow |= y;
        }
        if (q.statements.size() != 4) {
            q.addFlag("structure_statements_" + q.statements.size());
        }

        // dòng "Đáp án: Đ S S Đ" và lời giải ở phần đuôi (đoạn sau bảng)
        List<Boolean> fromLine = null;
        List<Para> rest = new ArrayList<>();
        for (Para p : g.tail) {
            Matcher dm = ANSWER_TF.matcher(p.text().plain().replace("￼", "").strip());
            if (dm.matches() && fromLine == null) {
                fromLine = new ArrayList<>();
                for (char ch : dm.group(1).replaceAll("\\s", "").toUpperCase(Locale.ROOT).toCharArray()) {
                    fromLine.add(ch == 'Đ');
                }
            } else if (!p.text().isBlank() || !p.text().objs.isEmpty()) {
                rest.add(p);
            }
        }
        boolean sizeOk = q.statements.size() == 4;
        if (anyYellow) {
            for (int k = 0; k < q.statements.size(); k++) {
                q.statements.get(k).truth = yellow.get(k);
                q.statements.get(k).evidence = "highlight_yellow";
            }
            if (fromLine != null && sizeOk && !fromLine.equals(yellow)) {
                q.addFlag("answer_conflict");
            }
        } else if (fromLine != null && fromLine.size() == q.statements.size()) {
            for (int k = 0; k < q.statements.size(); k++) {
                q.statements.get(k).truth = fromLine.get(k);
                q.statements.get(k).evidence = "dap_an_line";
            }
        } else {
            q.addFlag("answer_missing");
        }
        String sol = solution(rest, c);
        if (!hint.isEmpty()) {
            if (sol != null) {
                hint.add(sol);
            }
            sol = String.join("\n", hint);
        }
        q.solution = sol;
        return q;
    }

    // ---------- phần III: trả lời ngắn ----------

    /** Khối của một câu và ngữ cảnh dùng chung (đoạn giới thiệu, hình, bảng) mà câu đó nhận; context rỗng nếu không có. */
    private record SaPart(List<Block> blocks, List<Block> context) {}

    /**
     * Tách Phần III theo đầu câu và gán ngữ cảnh dùng chung: khối đứng trước câu đầu tiên, hoặc khối sau "Đáp số" của một
     * câu có bảng (một đoạn giới thiệu ngay trước bảng + bảng + phần sau). Ngữ cảnh áp cho các câu sau tới khi có ngữ cảnh
     * mới hoặc gặp câu có bảng riêng trong thân câu.
     */
    private static List<SaPart> saParts(List<Block> region) {
        List<Block> preamble = new ArrayList<>();
        List<List<Block>> qbs = new ArrayList<>();
        List<Block> cur = null;
        for (Block b : region) {
            if (b instanceof Para p && head(p.text()) != null) {
                cur = new ArrayList<>();
                qbs.add(cur);
            }
            (cur == null ? preamble : cur).add(b);
        }
        List<Block> ctx = preamble.stream().anyMatch(DocParser::hasContent) ? preamble : List.of();
        List<SaPart> out = new ArrayList<>();
        for (List<Block> qb : qbs) {
            int ans = answerIndex(qb);
            if (qb.subList(0, ans < 0 ? qb.size() : ans).stream().anyMatch(b -> b instanceof Tbl)) {
                ctx = List.of();                              // câu có bảng riêng: ngữ cảnh cũ dừng
            }
            List<Block> trailing = trailingContext(qb, ans);
            out.add(new SaPart(qb.subList(0, qb.size() - trailing.size()), ctx));
            if (!trailing.isEmpty()) {
                ctx = trailing;
            }
        }
        return out;
    }

    private static int answerIndex(List<Block> qb) {
        for (int i = 0; i < qb.size(); i++) {
            if (qb.get(i) instanceof Para p && ANSWER_SA.matcher(p.text().s).find()) {
                return i;
            }
        }
        return -1;
    }

    /** Ngữ cảnh của các câu sau nằm ở đuôi câu này: từ đoạn giới thiệu ngay trước bảng đầu tiên sau "Đáp số" tới hết. */
    private static List<Block> trailingContext(List<Block> qb, int ans) {
        if (ans < 0) {
            return List.of();
        }
        int tbl = -1;
        for (int i = ans + 1; i < qb.size() && tbl < 0; i++) {
            if (qb.get(i) instanceof Tbl) {
                tbl = i;
            }
        }
        if (tbl < 0) {
            return List.of();
        }
        int start = tbl;
        for (int i = tbl - 1; i > ans; i--) {
            if (hasContent(qb.get(i))) {
                start = i;
                break;
            }
        }
        return qb.subList(start, qb.size());
    }

    private static boolean hasContent(Block b) {
        return b instanceof Tbl || b instanceof Para p && (!p.text().isBlank() || !p.text().objs.isEmpty());
    }

    private Question sa(List<Block> qb, int ordinal, List<Block> context) {
        Question q = newQuestion("sa", "III", ordinal);
        QCtx c = new QCtx(q, ln.source().docId(), formulas);
        List<Tbl> tables = new ArrayList<>();
        List<FP> fps = flatten(qb, tables);
        Head head = head(fps.get(0).para().text());
        setLevel(q, head);

        // ngữ cảnh dùng chung đứng trước đề riêng: đoạn (kể cả hình) vào đầu stem, bảng vào đầu tables
        List<String> lines = new ArrayList<>();
        List<List<List<String>>> contextTables = new ArrayList<>();
        for (Block b : context) {
            if (b instanceof Para p) {
                String s = c.render(p.text(), "stem");
                if (!s.isEmpty()) {
                    lines.add(s);
                }
            } else if (b instanceof Tbl t) {
                contextTables.add(renderTable(t, c, "stem"));
            }
        }
        if (!context.isEmpty()) {
            q.addFlag("shared_context");
        }

        int ansIdx = -1;
        int replyIdx = -1;
        for (int i = 0; i < fps.size(); i++) {
            if (fps.get(i).tbl() >= 0) {
                continue;
            }
            RichText rt = fps.get(i).para().text();
            if (ansIdx < 0 && ANSWER_SA.matcher(rt.s).find()) {
                ansIdx = i;
            }
            if (replyIdx < 0 && REPLY_LINE.matcher(rt.s).find()) {
                replyIdx = i;
            }
        }
        int stemEnd = fps.size();
        for (int idx : new int[]{ansIdx, replyIdx}) {
            if (idx >= 0) {
                stemEnd = Math.min(stemEnd, idx);
            }
        }
        // đề: đoạn thân trước "Trả lời"/"Đáp số"; bảng dữ liệu nằm riêng
        for (int i = 0; i < stemEnd; i++) {
            if (fps.get(i).tbl() >= 0) {
                continue;
            }
            RichText rt = fps.get(i).para().text();
            String s = i == 0 && head != null ? c.render(rt, "stem", head.omitFrom(), head.omitTo()) : c.render(rt, "stem");
            if (!s.isEmpty()) {
                lines.add(s);
            }
        }
        q.stem = String.join("\n", lines);
        q.tables = new ArrayList<>(contextTables);
        for (Tbl t : tables) {
            q.tables.add(renderTable(t, c, "stem"));
        }
        if (q.tables.isEmpty()) {
            q.tables = null;
        }

        if (ansIdx < 0) {
            q.answer = answer(null, "none");
            q.addFlag("answer_missing");
            return q;
        }
        RichText ans = fps.get(ansIdx).para().text();
        Matcher am = ANSWER_SA.matcher(ans.s);
        am.find();
        for (int i = 0; i < am.end(); i++) {              // ảnh đứng trước chữ "Đáp số" thuộc về đề
            if (ans.objIdx[i] >= 0) {
                c.registerImage(ans.objs.get(ans.objIdx[i]), "stem");
            }
        }
        String text = c.render(ans.slice(am.end(), ans.length()), "answer").strip();
        while (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1).strip();
        }
        q.answer = new Answer();
        q.answer.text = text;
        q.answer.evidence = "dap_so_line";
        if (text.isEmpty()) {
            q.answer.text = null;
            q.addFlag("answer_missing");
        } else if (NUMERIC.matcher(text).matches()) {
            q.answer.numeric = text.replace(',', '.');
        }
        List<Para> after = new ArrayList<>();
        for (int i = ansIdx + 1; i < fps.size(); i++) {
            if (fps.get(i).tbl() < 0) {
                after.add(fps.get(i).para());
            }
        }
        q.solution = solution(after, c);
        return q;
    }

    // ---------- tiện ích ----------

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hash(Question q) {
        StringBuilder sb = new StringBuilder(q.type).append('|').append(q.stem).append('|');
        if (q.options != null) {
            q.options.forEach(o -> sb.append(o.key).append(o.text).append('|'));
        }
        if (q.statements != null) {
            q.statements.forEach(s -> sb.append(s.key).append(s.text).append(s.truth).append('|'));
        }
        if (q.answer != null) {
            sb.append(q.answer.value).append(q.answer.text);
        }
        return sha256(sb.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 12);
    }
}
