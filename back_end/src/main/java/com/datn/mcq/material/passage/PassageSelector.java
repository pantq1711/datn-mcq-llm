package com.datn.mcq.material.passage;

import com.datn.mcq.common.util.PromptData;
import com.datn.mcq.common.util.SimpleJson;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chọn đoạn tài liệu để đưa vào prompt sinh câu (W2-3 bước 5): KHÔNG nhồi cả tài liệu, mỗi job chỉ nhận tối đa
 * {@value #MAX_CHARS_PER_JOB} ký tự trong tối đa {@value #MAX_PASSAGES} đoạn.
 *
 * <ol>
 *   <li><b>Tài liệu nhỏ</b> (tổng ≤ {@value #MAX_CHARS_PER_JOB} ký tự): đưa cả (mode {@code whole}).</li>
 *   <li><b>Chia đoạn:</b> theo dòng trống, gộp các đoạn văn liền nhau tới {@value #CHUNK_TARGET} ký tự; đoạn văn dài hơn thì
 *       cắt cửa sổ có gối nhau {@value #OVERLAP} ký tự. Mỗi chunk là một khoảng liền {@code [from, to)} của văn bản đã lưu
 *       nên truy lại được chính xác.</li>
 *   <li><b>Xếp hạng</b> bằng từ khóa (BM25) giữa chunk và tên bài + mục tiêu: đơn vị là âm tiết và cặp âm tiết liền nhau
 *       (cặp có trọng số gấp đôi), bỏ dấu tiếng Việt để người dùng viết không dấu vẫn khớp; chunk có tiêu đề khớp tên bài
 *       được nhân {@value #HEADING_BOOST}. Không có chunk nào trùng từ nào thì lấy chunk rải đều (mode {@code fallback}).</li>
 *   <li><b>Cửa sổ theo chỗ:</b> luôn giữ đoạn hạng 0 (đoạn liên quan nhất) trong mọi job; các đoạn còn lại lấy từ các chunk
 *       hạng 1 trở đi, xoay vòng theo {@code (slotIndex × WINDOW_STEP + attempt) mod (TOP_K - 1)}, nên các câu cùng ô vừa
 *       bám sát nội dung cốt lõi của bài vừa phủ các phần bổ trợ khác nhau, và lần sinh lại dịch sang các đoạn khác.</li>
 * </ol>
 *
 * <p>Hàm thuần, xác định: cùng đầu vào cho cùng kết quả. Văn bản đoạn ({@link Passage#text()}) là bản gốc để offset khớp;
 * {@link Selection#context()} là bản đã bỏ ranh giới nguy hiểm ({@link PromptData#neutralize}), chính là thứ đưa vào prompt
 * và lưu làm ảnh chụp ở generation_job.
 */
public final class PassageSelector {

    public static final int MAX_CHARS_PER_JOB = 6000;
    public static final int CHUNK_TARGET = 1500;
    public static final int MAX_PASSAGES = 4;
    public static final int TOP_K = 8;
    public static final int WINDOW_STEP = 2;
    static final int OVERLAP = 150;
    static final double HEADING_BOOST = 1.5;

    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final Pattern BLOCK_BREAK = Pattern.compile("\\n[ \\t]*\\n+");
    private static final Pattern HEADING_NAME = Pattern.compile("^(?i)(bài|chương|mục|phần|tiết|lesson|chapter)\\s*\\d");
    private static final Pattern NON_TOKEN = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOP = Set.of("va", "cua", "la", "co", "mot", "cac", "nhung", "duoc", "trong", "cho",
            "voi", "de", "nay", "khi", "thi", "ma", "bai", "nhu", "cung", "hay", "hoac", "tu", "den", "ve", "se", "da",
            "khong", "nhieu", "moi", "bang", "theo", "tren", "duoi", "sau", "truoc", "do", "no", "vi", "nen");

    /** Một tài liệu đầu vào: id, sha256 của file gốc và văn bản đã trích (đã lưu trong DB). */
    public record Source(Integer materialId, String sha256, String text) {}

    /**
     * Một đoạn được chọn: khoảng {@code [from, to)} của văn bản đã lưu của tài liệu.
     *
     * @param rank      hạng theo điểm (0 là liên quan nhất); với {@code whole} và {@code fallback} là thứ tự chọn
     * @param selection {@code ranked}, {@code fallback} hoặc {@code whole}
     */
    public record Passage(Integer materialId, String sha256, int from, int to, int rank, double score,
                          String selection, String text) {}

    /**
     * Kết quả chọn đoạn.
     *
     * @param passages các đoạn theo thứ tự trong tài liệu
     * @param context  khối văn bản đưa vào prompt (đã bỏ ranh giới nguy hiểm, mỗi đoạn có tiêu đề "[Tài liệu n, đoạn k]",
     *                 không có tên file); null khi không có đoạn nào
     * @param refsJson mảng JSON truy vết (materialId, sha256, from, to, rank, score, selection) cho cột material_refs; null
     *                 khi không có đoạn nào
     * @param mode     {@code whole}, {@code ranked}, {@code fallback} hoặc {@code none}
     */
    public record Selection(List<Passage> passages, String context, String refsJson, String mode) {
        public boolean isEmpty() {
            return passages.isEmpty();
        }
    }

    private record Chunk(int source, int from, int to, String heading, Map<String, Integer> features, int length) {}

    private PassageSelector() {}

    /**
     * @param topic     tên bài hoặc chủ đề của ô (dùng để khớp tiêu đề)
     * @param details   nội dung, yêu cầu cần đạt... (tùy chọn)
     * @param slotIndex chỗ trong ô, từ 0
     * @param attempt   số lần đã sinh cho chỗ này trước đó (sinh lại, Đổi câu), từ 0
     */
    public static Selection select(List<Source> sources, String topic, String details, int slotIndex, int attempt) {
        List<Source> live = new ArrayList<>();
        for (Source s : sources) {
            if (s.text() != null && !s.text().isBlank()) {
                live.add(s);
            }
        }
        if (live.isEmpty()) {
            return new Selection(List.of(), null, null, "none");
        }
        int total = live.stream().mapToInt(s -> s.text().length()).sum();
        if (total <= MAX_CHARS_PER_JOB) {
            List<Passage> all = new ArrayList<>();
            for (int i = 0; i < live.size(); i++) {
                Source s = live.get(i);
                all.add(new Passage(s.materialId(), s.sha256(), 0, s.text().length(), i, 0, "whole", s.text()));
            }
            return assemble(live, all, "whole");
        }

        List<Chunk> chunks = new ArrayList<>();
        for (int si = 0; si < live.size(); si++) {
            chunks.addAll(chunk(si, live.get(si).text()));
        }
        Map<String, Double> query = queryFeatures(topic, details);
        double[] scores = score(chunks, query, headingTokens(topic));
        int n = chunks.size();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer i) -> scores[i]).reversed()
                .thenComparingInt(i -> chunks.get(i).source()).thenComparingInt(i -> chunks.get(i).from()));

        List<Passage> picked = new ArrayList<>();
        if (scores[order.get(0)] <= 0) {
            int m = Math.min(MAX_PASSAGES, n);
            int shift = (slotIndex + attempt) % Math.max(1, n / m);
            for (int i = 0; i < m; i++) {
                Chunk c = chunks.get((i * n / m + shift) % n);
                picked.add(passage(live, c, i, 0, "fallback"));
            }
            return assemble(live, picked, "fallback");
        }
        int k = Math.min(TOP_K, n);
        // W2-4a (Mục 3): luôn giữ đoạn hạng 0 trong mọi job, chỉ xoay các chỗ còn lại
        Chunk c0 = chunks.get(order.get(0));
        int chars = c0.to() - c0.from();
        picked.add(passage(live, c0, 0, scores[order.get(0)], "ranked"));

        int poolSize = k - 1;
        if (poolSize > 0) {
            int shift = (slotIndex * WINDOW_STEP + attempt) % poolSize;
            for (int j = 0; j < poolSize && picked.size() < MAX_PASSAGES; j++) {
                int rank = 1 + ((shift + j) % poolSize);
                Chunk c = chunks.get(order.get(rank));
                if (chars + (c.to() - c.from()) > MAX_CHARS_PER_JOB) {
                    break;
                }
                chars += c.to() - c.from();
                picked.add(passage(live, c, rank, scores[order.get(rank)], "ranked"));
            }
        }
        return assemble(live, picked, "ranked");
    }

    private static Passage passage(List<Source> live, Chunk c, int rank, double score, String selection) {
        Source s = live.get(c.source());
        return new Passage(s.materialId(), s.sha256(), c.from(), c.to(), rank, score, selection,
                s.text().substring(c.from(), c.to()));
    }

    /** Xếp đoạn theo thứ tự trong tài liệu, dựng khối đưa vào prompt và danh sách truy vết. */
    private static Selection assemble(List<Source> live, List<Passage> picked, String mode) {
        List<Passage> ordered = new ArrayList<>(picked);
        ordered.sort(Comparator.comparingInt((Passage p) -> indexOf(live, p.materialId())).thenComparingInt(Passage::from));
        StringBuilder context = new StringBuilder();
        StringBuilder refs = new StringBuilder("[");
        for (int i = 0; i < ordered.size(); i++) {
            Passage p = ordered.get(i);
            context.append(i > 0 ? "\n\n" : "").append("[Tài liệu ").append(indexOf(live, p.materialId()) + 1)
                    .append(", đoạn ").append(i + 1).append("]\n").append(PromptData.neutralize(p.text()));
            refs.append(i > 0 ? "," : "").append("{\"materialId\":").append(p.materialId()).append(",\"sha256\":")
                    .append(SimpleJson.quote(p.sha256())).append(",\"from\":").append(p.from()).append(",\"to\":")
                    .append(p.to()).append(",\"rank\":").append(p.rank()).append(",\"score\":")
                    .append(String.format(Locale.ROOT, "%.4f", p.score())).append(",\"selection\":")
                    .append(SimpleJson.quote(p.selection())).append("}");
        }
        return new Selection(ordered, context.toString(), refs.append("]").toString(), mode);
    }

    private static int indexOf(List<Source> live, Integer materialId) {
        for (int i = 0; i < live.size(); i++) {
            if (live.get(i).materialId().equals(materialId)) {
                return i;
            }
        }
        return live.size();
    }

    // ---------- chia đoạn ----------

    private static List<Chunk> chunk(int source, String text) {
        // Các mảnh [start, end) theo dòng trống; mảnh dài hơn CHUNK_TARGET bị cắt cửa sổ có gối nhau.
        List<int[]> pieces = new ArrayList<>(); // {start, end, windowed, heading}
        Matcher m = BLOCK_BREAK.matcher(text);
        int cursor = 0;
        while (m.find()) {
            addBlock(pieces, text, cursor, m.start());
            cursor = m.end();
        }
        addBlock(pieces, text, cursor, text.length());

        List<Chunk> chunks = new ArrayList<>();
        int from = -1;
        int to = -1;
        for (int[] piece : pieces) {
            boolean windowed = piece[2] == 1;
            // Dòng tiêu đề luôn mở chunk mới để đi cùng phần nội dung sau nó, không dính vào đoạn đứng trước.
            if (from >= 0 && !windowed && piece[3] == 0 && piece[1] - from <= CHUNK_TARGET) {
                to = piece[1];
                continue;
            }
            if (from >= 0) {
                chunks.add(newChunk(source, text, from, to));
            }
            from = piece[0];
            to = piece[1];
            if (windowed) {
                chunks.add(newChunk(source, text, from, to));
                from = -1;
            }
        }
        if (from >= 0) {
            chunks.add(newChunk(source, text, from, to));
        }
        return chunks;
    }

    private static void addBlock(List<int[]> pieces, String text, int start, int end) {
        int s = start;
        int e = end;
        while (s < e && Character.isWhitespace(text.charAt(s))) {
            s++;
        }
        while (e > s && Character.isWhitespace(text.charAt(e - 1))) {
            e--;
        }
        if (s >= e) {
            return;
        }
        if (e - s <= CHUNK_TARGET) {
            String block = text.substring(s, e);
            pieces.add(new int[] {s, e, 0, block.indexOf('\n') < 0 && looksLikeHeading(block) ? 1 : 0});
            return;
        }
        int ws = s;
        while (ws < e) {
            int we = Math.min(ws + CHUNK_TARGET, e);
            if (we < e) {
                int cut = we;
                int minCut = ws + CHUNK_TARGET * 6 / 10;
                while (cut > minCut && !Character.isWhitespace(text.charAt(cut - 1))) {
                    cut--;
                }
                if (cut > minCut) {
                    we = cut;
                }
            }
            pieces.add(new int[] {ws, we, 1, 0});
            if (we >= e) {
                break;
            }
            int next = Math.max(ws + 1, we - OVERLAP);
            while (next < we && !Character.isWhitespace(text.charAt(next - 1))) {
                next++;
            }
            ws = next;
        }
    }

    private static Chunk newChunk(int source, String text, int from, int to) {
        String body = text.substring(from, to);
        int nl = body.indexOf('\n');
        String first = nl < 0 ? body : body.substring(0, nl);
        String heading = looksLikeHeading(first) ? first : null;
        Map<String, Integer> features = new TreeMap<>();
        List<String> tokens = tokens(body);
        for (String f : featuresOf(tokens)) {
            features.merge(f, 1, Integer::sum);
        }
        return new Chunk(source, from, to, heading, features, Math.max(1, tokens.size()));
    }

    private static boolean looksLikeHeading(String line) {
        String t = line.strip();
        if (t.isEmpty() || t.length() > 100) {
            return false;
        }
        boolean named = HEADING_NAME.matcher(t).find();
        char last = t.charAt(t.length() - 1);
        boolean endsLikeSentence = ".?!;,:".indexOf(last) >= 0;
        // Một dòng ngắn không kết câu cũng là tiêu đề, dù đứng riêng (khối một dòng) hay đứng đầu một khối nhiều dòng.
        return named || !endsLikeSentence;
    }

    // ---------- xếp hạng ----------

    /** Chữ thường, bỏ dấu tiếng Việt (đ thành d). */
    static String fold(String s) {
        String d = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return d.replace('đ', 'd');
    }

    /** Âm tiết (từ ≥ 2 ký tự) đã bỏ dấu, giữ cả từ dừng (cặp liền nhau cần chúng). */
    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        for (String t : NON_TOKEN.split(fold(s))) {
            if (t.length() >= 2) {
                out.add(t);
            }
        }
        return out;
    }

    /** Đặc trưng: âm tiết (không phải từ dừng) và cặp âm tiết liền nhau (không phải cặp hai từ dừng). */
    private static List<String> featuresOf(List<String> tokens) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (!STOP.contains(t)) {
                out.add(t);
            }
            if (i + 1 < tokens.size()) {
                String u = tokens.get(i + 1);
                if (!(STOP.contains(t) && STOP.contains(u))) {
                    out.add(t + "_" + u);
                }
            }
        }
        return out;
    }

    private static Map<String, Double> queryFeatures(String topic, String details) {
        Map<String, Double> q = new TreeMap<>();
        String text = (topic == null ? "" : topic) + " . " + (details == null ? "" : details);
        for (String f : featuresOf(tokens(text))) {
            q.put(f, f.contains("_") ? 2.0 : 1.0);
        }
        return q;
    }

    /** Âm tiết (không phải từ dừng) của tên bài, để so với tiêu đề của chunk. */
    private static Set<String> headingTokens(String topic) {
        Set<String> out = new TreeSet<>();
        for (String t : tokens(topic == null ? "" : topic)) {
            if (!STOP.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private static double[] score(List<Chunk> chunks, Map<String, Double> query, Set<String> topicTokens) {
        int n = chunks.size();
        double avg = chunks.stream().mapToInt(Chunk::length).average().orElse(1);
        Map<String, Integer> df = new TreeMap<>();
        for (Chunk c : chunks) {
            for (String f : c.features().keySet()) {
                if (query.containsKey(f)) {
                    df.merge(f, 1, Integer::sum);
                }
            }
        }
        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            Chunk c = chunks.get(i);
            double s = 0;
            for (Map.Entry<String, Double> e : query.entrySet()) {
                Integer tf = c.features().get(e.getKey());
                if (tf == null) {
                    continue;
                }
                double idf = Math.log(1 + (n - df.get(e.getKey()) + 0.5) / (df.get(e.getKey()) + 0.5));
                s += e.getValue() * idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * c.length() / avg));
            }
            if (s > 0 && c.heading() != null && headingMatches(c.heading(), topicTokens)) {
                s *= HEADING_BOOST;
            }
            scores[i] = s;
        }
        return scores;
    }

    /** Tiêu đề khớp tên bài khi chứa ít nhất 60% âm tiết (không phải từ dừng) của tên bài. */
    private static boolean headingMatches(String heading, Set<String> topicTokens) {
        if (topicTokens.isEmpty()) {
            return false;
        }
        Set<String> h = new TreeSet<>(tokens(heading));
        long hit = topicTokens.stream().filter(h::contains).count();
        return hit >= Math.ceil(topicTokens.size() * 0.6);
    }
}
