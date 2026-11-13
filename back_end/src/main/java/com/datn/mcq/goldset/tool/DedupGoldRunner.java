package com.datn.mcq.goldset.tool;

import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.SamplingStratum;
import com.datn.mcq.goldset.tool.DedupGoldPairSampler.Batch2Quotas;
import com.datn.mcq.goldset.tool.DedupGoldPairSampler.BlockedPair;
import com.datn.mcq.goldset.tool.DedupGoldPairSampler.Item;
import com.datn.mcq.goldset.tool.DedupGoldPairSampler.Pair;
import com.datn.mcq.goldset.tool.DedupGoldPairSampler.Quotas;
import com.datn.mcq.goldset.tool.DedupGoldPairSampler.SampledPair;
import com.datn.mcq.qc.checker.DedupChecker;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Công cụ D6 CHẠY THỦ CÔNG (chương 4): chọn tập cặp câu để gán nhãn trùng và ghi dự đoán của {@link DedupChecker} ở các
 * ngưỡng 0,50–0,95. Chỉ tồn tại khi có {@code app.dedup-gold.out-dir}; test và chạy app bình thường không có bean này.
 *
 * <ul>
 *   <li>CHỈ ĐỌC DB (generated_question, generated_question_statement, quality_check_result); không ghi DB, không gọi LLM.
 *       Bảng {@code dedup_gold_pair}/{@code dedup_gold_label} KHÔNG được ghi: tập cặp và nhãn nằm trong file.</li>
 *   <li>{@code app.dedup-gold.seed}: bắt buộc khi lấy mẫu mới. {@code app.dedup-gold.pairs-file}: thay vì lấy mẫu, đọc lại
 *       danh sách cặp đã có (cap-vang.csv cũ) và tính lại dự đoán (ví dụ sau khi DedupChecker đổi).</li>
 *   <li>Ghi vào {@code out-dir} (ngoài repo, hoặc trong {@code data/private/} của repo, đã gitignore):
 *       {@code cap-vang.csv} (FILE KHÓA: tầng, cosine, chốt chặn, dự đoán; không đưa người gán nhãn),
 *       {@code cap-vang-noi-dung.jsonl} (nội dung hai câu cho phiếu gán nhãn, không điểm) và {@code cap-vang-log.txt}.</li>
 * </ul>
 * Chạy kèm {@code --server.port=0 --app.worker.enabled=false} như SolverProbeRunner.
 */
@Component
@ConditionalOnProperty(prefix = "app.dedup-gold", name = "out-dir")
public class DedupGoldRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DedupGoldRunner.class);
    static final String KEY_FILE = "cap-vang.csv";
    static final String CONTENT_FILE = "cap-vang-noi-dung.jsonl";
    static final String LOG_FILE = "cap-vang-log.txt";

    static final String POOL_SQL = """
            SELECT g.id, g.question_type, g.question_text, g.option_a, g.option_b, g.option_c, g.option_d, g.sa_answer,
                   g.run_id, g.template_id
            FROM generated_question g
            WHERE g.question_text IS NOT NULL AND g.parse_success IS NOT FALSE
              AND NOT EXISTS (SELECT 1 FROM quality_check_result f
                              WHERE f.question_id = g.id AND f.check_type = 'format' AND f.passed = FALSE)
            ORDER BY g.id""";
    static final String STATEMENT_SQL = """
            SELECT question_id, position, statement_text FROM generated_question_statement
            WHERE statement_text IS NOT NULL ORDER BY question_id, position""";

    /** Các cặp dedup từng chặn: câu bị loại vì trùng và câu nó khớp (đã lưu bởi DedupService). */
    static final String BLOCKED_SQL = """
            SELECT question_id, compared_to_question_id FROM quality_check_result
            WHERE check_type = 'dedup' AND passed = FALSE AND compared_to_question_id IS NOT NULL
            ORDER BY question_id""";

    private final JdbcTemplate jdbc;
    private final ConfigurableApplicationContext context;
    private final String outDir;
    private final String seed;
    private final String pairsFile;
    private final String batch2Seed;

    public DedupGoldRunner(JdbcTemplate jdbc, ConfigurableApplicationContext context,
                           @Value("${app.dedup-gold.out-dir}") String outDir,
                           @Value("${app.dedup-gold.seed:}") String seed,
                           @Value("${app.dedup-gold.pairs-file:}") String pairsFile,
                           @Value("${app.dedup-gold.batch2-seed:}") String batch2Seed) {
        this.jdbc = jdbc;
        this.context = context;
        this.outDir = outDir;
        this.seed = seed == null ? "" : seed.trim();
        this.pairsFile = pairsFile == null ? "" : pairsFile.trim();
        this.batch2Seed = batch2Seed == null ? "" : batch2Seed.trim();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path out = resolveOutDir(outDir, Path.of("").toAbsolutePath());
        if (pairsFile.isEmpty() && seed.isEmpty()) {
            throw new IllegalStateException("Thiếu app.dedup-gold.seed (hoặc app.dedup-gold.pairs-file)");
        }
        Map<Integer, Item> pool = loadPool();
        log.info("Công cụ D6: {} câu trong kho", pool.size());
        List<SampledPair> pairs;
        List<String> notes = new ArrayList<>();
        if (pairsFile.isEmpty()) {
            DedupGoldPairSampler.Result r = DedupGoldPairSampler.sample(new ArrayList<>(pool.values()),
                    Quotas.defaults(), Long.parseLong(seed));
            pairs = r.pairs();
            notes.addAll(r.log());
            notes.add("seed=" + seed + ", quotas=" + Quotas.defaults());
        } else {
            pairs = readPairs(Files.readString(Path.of(pairsFile), StandardCharsets.UTF_8), pool);
            notes.add("Tính lại dự đoán cho tập cặp có sẵn: " + pairsFile);
        }
        List<BlockedPair> blocked = loadBlocked();
        if (!batch2Seed.isEmpty()) {
            if (pairsFile.isEmpty()) {
                throw new IllegalStateException("app.dedup-gold.batch2-seed cần app.dedup-gold.pairs-file (cặp lô 1)");
            }
            if (pairs.stream().anyMatch(p -> p.batch() == 2)) {
                throw new IllegalStateException("pairs-file đã có cặp lô 2; không thêm lô 2 lần nữa");
            }
            Set<String> existing = new HashSet<>();
            pairs.forEach(p -> existing.add(p.pair().key()));
            DedupGoldPairSampler.Result b2 = DedupGoldPairSampler.sampleBatch2(new ArrayList<>(pool.values()), existing,
                    blocked, Batch2Quotas.defaults(), Long.parseLong(batch2Seed));
            notes.addAll(b2.log());
            notes.add("batch2-seed=" + batch2Seed + ", " + Batch2Quotas.defaults() + ", lô 2: " + b2.pairs().size()
                    + " cặp; theo nhóm: " + countByGroup(b2.pairs()));
            pairs = DedupGoldPairSampler.merge(pairs, b2.pairs(), Long.parseLong(batch2Seed));
        }
        Set<String> blockedKeys = new HashSet<>();
        blocked.forEach(b -> blockedKeys.add(DedupGoldPairSampler.keyOf(b.subjectId(), b.comparedId())));
        Files.createDirectories(out);
        Files.writeString(out.resolve(KEY_FILE), "﻿" + String.join("\n", keyCsv(pairs, blockedKeys)) + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(out.resolve(CONTENT_FILE), String.join("\n", contentJsonl(pairs)) + "\n",
                StandardCharsets.UTF_8);
        notes.add(pairs.size() + " cặp; theo tầng: " + countByStratum(pairs) + "; theo nhóm: " + countByGroup(pairs)
                + "; cặp dedup từng chặn trong tập: "
                + pairs.stream().filter(p -> blockedKeys.contains(p.pair().key())).count());
        Files.writeString(out.resolve(LOG_FILE), String.join("\n", notes) + "\n", StandardCharsets.UTF_8);
        log.info("Công cụ D6 xong: {} cặp -> {}. {}", pairs.size(), out, notes);
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private Map<Integer, Item> loadPool() {
        Map<Integer, List<String>> statements = new HashMap<>();
        jdbc.query(STATEMENT_SQL, rs -> {
            statements.computeIfAbsent(rs.getInt("question_id"), k -> new ArrayList<>()).add(rs.getString("statement_text"));
        });
        Map<Integer, Item> out = new LinkedHashMap<>();
        jdbc.query(POOL_SQL, rs -> {
            int id = rs.getInt("id");
            QuestionType type = QuestionType.valueOf(rs.getString("question_type").toUpperCase(Locale.ROOT));
            DedupCandidate c = new DedupCandidate(id, rs.getString("question_text"), rs.getString("option_a"),
                    rs.getString("option_b"), rs.getString("option_c"), rs.getString("option_d"), type,
                    rs.getString("sa_answer"), statements.getOrDefault(id, List.of()));
            if (c.hasContent()) {
                out.put(id, new Item(c, rs.getInt("run_id"), rs.getInt("template_id")));
            }
        });
        return out;
    }

    private List<BlockedPair> loadBlocked() {
        List<BlockedPair> out = new ArrayList<>();
        jdbc.query(BLOCKED_SQL, rs -> {
            out.add(new BlockedPair(rs.getInt("question_id"), rs.getInt("compared_to_question_id")));
        });
        return out;
    }

    /**
     * cap-vang.csv: một dòng mỗi cặp; cột du_doan_XXX = 1 khi DedupChecker báo trùng ở ngưỡng XXX/100; cuối dòng: lo
     * (1 hoặc 2), nhom (nhóm lấy mẫu trong lô), da_chan (1 khi cặp từng bị dedup chặn trong DB).
     */
    static List<String> keyCsv(List<SampledPair> pairs) {
        return keyCsv(pairs, Set.of());
    }

    static List<String> keyCsv(List<SampledPair> pairs, Set<String> blockedKeys) {
        List<String> lines = new ArrayList<>();
        StringBuilder header = new StringBuilder("cap,q1,q2,dang,tang,cosine,cung_so_lieu,cung_phu_dinh,cung_van_tay");
        for (double t : DedupGoldPairSampler.THRESHOLDS) {
            header.append(",du_doan_").append(String.format(Locale.ROOT, "%03d", Math.round(t * 100)));
        }
        header.append(",lo,nhom,da_chan");
        lines.add(header.toString());
        for (SampledPair sp : pairs) {
            Pair p = sp.pair();
            StringBuilder line = new StringBuilder();
            line.append(sp.pairNo()).append(',').append(p.first().id()).append(',').append(p.second().id()).append(',')
                    .append(p.first().candidate().questionType().getDbValue()).append(',')
                    .append(p.stratum().getDbValue()).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", p.diagnostics().cosine())).append(',')
                    .append(bit(p.diagnostics().sameSignature())).append(',')
                    .append(bit(p.diagnostics().sameNegation())).append(',')
                    .append(bit(p.diagnostics().sameFingerprint()));
            for (double t : DedupGoldPairSampler.THRESHOLDS) {
                line.append(',').append(bit(p.predictedDuplicate(t)));
            }
            line.append(',').append(sp.batch()).append(',').append(sp.group()).append(',')
                    .append(bit(blockedKeys.contains(p.key())));
            lines.add(line.toString());
        }
        return lines;
    }

    /** cap-vang-noi-dung.jsonl: chỉ nội dung hai câu (định dạng câu của tools/measurement), không điểm hay tầng. */
    static List<String> contentJsonl(List<SampledPair> pairs) {
        ObjectMapper mapper = new ObjectMapper();
        List<String> lines = new ArrayList<>();
        for (SampledPair sp : pairs) {
            ObjectNode node = mapper.createObjectNode();
            node.put("cap", sp.pairNo());
            node.put("dang", sp.pair().first().candidate().questionType().getDbValue());
            node.set("cau_1", question(mapper, sp.pair().first().candidate()));
            node.set("cau_2", question(mapper, sp.pair().second().candidate()));
            lines.add(node.toString());
        }
        return lines;
    }

    private static ObjectNode question(ObjectMapper mapper, DedupCandidate c) {
        ObjectNode q = mapper.createObjectNode();
        q.put("dang", c.questionType().getDbValue());
        q.put("de", c.questionText());
        if (c.questionType() == QuestionType.MCQ) {
            ObjectNode pa = q.putObject("phuong_an");
            pa.put("A", c.optionA());
            pa.put("B", c.optionB());
            pa.put("C", c.optionC());
            pa.put("D", c.optionD());
        } else if (c.questionType() == QuestionType.TF) {
            ArrayNode ys = q.putArray("y");
            for (int i = 0; i < c.statements().size(); i++) {
                ys.addObject().put("vi_tri", String.valueOf((char) ('a' + i))).put("noi_dung", c.statements().get(i));
            }
        } else {
            q.put("dap_an", c.saAnswer());
        }
        return q;
    }

    /** Đọc lại cap-vang.csv cũ (cột cap, q1, q2, tang); id không còn trong kho thì dừng, không bỏ im lặng. */
    static List<SampledPair> readPairs(String csv, Map<Integer, Item> pool) {
        List<SampledPair> out = new ArrayList<>();
        String[] lines = csv.replace("﻿", "").split("\\R");
        if (lines.length == 0 || !lines[0].startsWith("cap,q1,q2,dang,tang")) {
            throw new IllegalStateException("pairs-file không đúng định dạng cap-vang.csv");
        }
        List<String> header = List.of(lines[0].split(","));
        int loCol = header.indexOf("lo");
        int nhomCol = header.indexOf("nhom");
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] f = lines[i].split(",");
            Item a = pool.get(Integer.parseInt(f[1]));
            Item b = pool.get(Integer.parseInt(f[2]));
            if (a == null || b == null) {
                throw new IllegalStateException("Cặp " + f[0] + ": câu " + (a == null ? f[1] : f[2]) + " không còn trong kho");
            }
            SamplingStratum stratum = SamplingStratum.valueOf(f[4].toUpperCase(Locale.ROOT));
            Pair pair = new Pair(a, b, stratum, DedupChecker.diagnose(a.candidate(), b.candidate()));
            if (loCol >= 0 && nhomCol >= 0 && f.length > Math.max(loCol, nhomCol)) {
                out.add(new SampledPair(Integer.parseInt(f[0]), pair, Integer.parseInt(f[loCol]), f[nhomCol]));
            } else {
                out.add(new SampledPair(Integer.parseInt(f[0]), pair));
            }
        }
        return out;
    }

    private static Map<String, Integer> countByGroup(List<SampledPair> pairs) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (SampledPair p : pairs) {
            out.merge(p.group(), 1, Integer::sum);
        }
        return out;
    }

    private static Map<String, Integer> countByStratum(List<SampledPair> pairs) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (SamplingStratum s : SamplingStratum.values()) {
            out.put(s.getDbValue(), (int) pairs.stream().filter(p -> p.pair().stratum() == s).count());
        }
        return out;
    }

    private static String bit(boolean b) {
        return b ? "1" : "0";
    }

    /**
     * Thư mục ghi: ngoài repo, hoặc trong {@code <repo>/data/private/} (đã gitignore). Repo là thư mục cha gần nhất của
     * {@code workingDir} có ".git"; không thấy thì coi {@code workingDir} là repo.
     */
    static Path resolveOutDir(String outDir, Path workingDir) {
        if (outDir == null || outDir.isBlank()) {
            throw new IllegalStateException("Thiếu app.dedup-gold.out-dir");
        }
        Path out = Path.of(outDir.trim()).toAbsolutePath().normalize();
        Path wd = workingDir.toAbsolutePath().normalize();
        Path repo = wd;
        for (Path p = wd; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve(".git"))) {
                repo = p;
                break;
            }
        }
        if (out.startsWith(repo) && !out.startsWith(repo.resolve("data").resolve("private"))) {
            throw new IllegalStateException("app.dedup-gold.out-dir phải nằm ngoài repo hoặc trong data/private/ ("
                    + repo + "): " + out);
        }
        return out;
    }
}
