package com.datn.mcq.goldset.tool;

import com.datn.mcq.enums.SamplingStratum;
import com.datn.mcq.qc.checker.DedupChecker;
import com.datn.mcq.qc.checker.DedupChecker.PairDiagnostics;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * D6 (chương 4): chọn tập cặp câu để gán nhãn "trùng / không trùng" và tính dự đoán của {@link DedupChecker} ở từng ngưỡng.
 * Hàm thuần (không Spring, DB hay LLM); {@link DedupGoldRunner} nạp câu từ DB rồi gọi vào đây.
 *
 * <p>Tầng (cột {@code dedup_gold_pair.sampling_stratum}): {@code same_run} cùng run (cùng đề, cùng dạng),
 * {@code cross_run} khác run nhưng cùng template (cùng ô ma trận ở hai đề: nơi có cặp "cùng dạng, đổi số liệu"),
 * {@code cross_template} khác template, {@code random} rút đều trên mọi cặp còn lại. Chỉ ghép câu cùng dạng (như pipeline).
 *
 * <p>Thứ tự chọn: (1) cặp "đổi số liệu" (chốt chặn số liệu khác, cosine {@code >=} {@value #CHANGED_NUMBERS_MIN_COSINE})
 * và cặp lệch phủ định, đủ số tối thiểu; (2) mỗi tầng cấu trúc lấy phần lớn ({@code bandShare}) trong vùng cosine
 * [{@value #BAND_LOW}, {@value #BAND_HIGH}), phần còn lại chia đôi trên/dưới vùng; (3) tầng random. Mọi lần rút dùng
 * {@code new Random(seed)} trên danh sách đã sắp theo id, nên cùng dữ liệu và seed thì ra đúng tập cặp. Cuối cùng xáo trộn
 * và đánh số cặp 1..N để thứ tự trên phiếu không lộ tầng hay điểm.
 */
public final class DedupGoldPairSampler {

    public static final double BAND_LOW = 0.60;
    public static final double BAND_HIGH = 0.95;
    public static final double CHANGED_NUMBERS_MIN_COSINE = 0.80;
    /** Nhóm của lô 1 (lấy mẫu phân tầng). */
    public static final String GROUP_BATCH1 = "lo1";
    /** Lô 2: cặp dedup từng chặn trong DB (câu bị loại vì trùng, câu nó khớp). */
    public static final String GROUP_BLOCKED = "da_chan";
    /** Lô 2: cùng số liệu/công thức, cosine [0,60; 0,90): ứng viên diễn đạt lại mà dedup có thể bỏ sót (đo recall). */
    public static final String GROUP_SAME_NUMBERS_MID = "cung_so_lieu_0.60-0.90";
    /** Lô 2: cùng số liệu/công thức, cosine >= 0,90 (đo báo nhầm khi số liệu giống nhưng hỏi khác ý). */
    public static final String GROUP_SAME_NUMBERS_HIGH = "cung_so_lieu_tu_0.90";
    /** Ngưỡng quét: 0,50 đến 0,95, bước 0,05. */
    public static final List<Double> THRESHOLDS = thresholds();

    private DedupGoldPairSampler() {}

    /** Một câu trong kho: nội dung để so trùng, run và template để xếp tầng. */
    public record Item(DedupCandidate candidate, int runId, int templateId) {
        public int id() {
            return candidate.questionId();
        }
    }

    /**
     * @param minChangedNumbers số cặp "cùng dạng, đổi số liệu" tối thiểu
     * @param minNegation       số cặp lệch phủ định tối thiểu
     * @param bandShare         tỉ lệ cặp trong vùng [BAND_LOW, BAND_HIGH) của mỗi tầng cấu trúc
     */
    public record Quotas(int sameRun, int crossRun, int crossTemplate, int random, int minChangedNumbers,
                         int minNegation, double bandShare) {
        public static Quotas defaults() {
            return new Quotas(40, 45, 35, 30, 15, 10, 0.70);
        }

        int of(SamplingStratum s) {
            return switch (s) {
                case SAME_RUN -> sameRun;
                case CROSS_RUN -> crossRun;
                case CROSS_TEMPLATE -> crossTemplate;
                case RANDOM -> random;
            };
        }

        public int total() {
            return sameRun + crossRun + crossTemplate + random;
        }
    }

    /** Một cặp (first.id < second.id) kèm tầng và chẩn đoán. */
    public record Pair(Item first, Item second, SamplingStratum stratum, PairDiagnostics diagnostics) {
        String key() {
            return first.id() + "-" + second.id();
        }

        boolean inBand() {
            return diagnostics.cosine() >= BAND_LOW && diagnostics.cosine() < BAND_HIGH;
        }

        boolean changedNumbers() {
            return !diagnostics.sameSignature() && diagnostics.cosine() >= CHANGED_NUMBERS_MIN_COSINE;
        }

        boolean negationDiffers() {
            return !diagnostics.sameNegation();
        }

        /** Dự đoán của pipeline ở ngưỡng t: câu mới hơn (id lớn) so với câu cũ hơn, như lúc QC. */
        public boolean predictedDuplicate(double threshold) {
            return DedupChecker.check(second.candidate(), List.of(first.candidate()), threshold).status()
                    == DedupStatus.DUPLICATE;
        }
    }

    /**
     * Cặp đã chọn kèm số cặp trên phiếu, lô (1 = lấy mẫu phân tầng, 2 = lô bù) và nhóm trong lô. Nhóm lô 1 luôn là
     * {@value #GROUP_BATCH1}; nhóm lô 2 là một trong {@link #GROUP_BLOCKED}, {@link #GROUP_SAME_NUMBERS_MID},
     * {@link #GROUP_SAME_NUMBERS_HIGH}.
     */
    public record SampledPair(int pairNo, Pair pair, int batch, String group) {
        public SampledPair(int pairNo, Pair pair) {
            this(pairNo, pair, 1, GROUP_BATCH1);
        }

        SampledPair withPairNo(int no) {
            return new SampledPair(no, pair, batch, group);
        }
    }

    public record Result(List<SampledPair> pairs, List<String> log) {}

    /** Cặp dedup từng chặn trong DB: câu bị loại vì trùng và câu nó khớp (thứ tự tùy ý). */
    public record BlockedPair(int subjectId, int comparedId) {}

    /**
     * @param total          tổng số cặp tối đa của lô 2
     * @param maxSameHigh    số cặp tối đa của nhóm cùng số liệu, cosine >= 0,90
     */
    public record Batch2Quotas(int total, int maxSameHigh) {
        public static Batch2Quotas defaults() {
            return new Batch2Quotas(60, 15);
        }
    }

    /** Khóa không phụ thuộc thứ tự của một cặp id. */
    public static String keyOf(int idA, int idB) {
        return Math.min(idA, idB) + "-" + Math.max(idA, idB);
    }

    /**
     * Lô 2 (bù cho lô 1, vốn dồn vào cặp "đổi số liệu"): (1) mọi cặp dedup từng chặn trong DB còn trong kho và chưa có ở
     * lô 1; (2) cặp cùng số liệu, cosine {@code >=} 0,90 (tối đa {@code maxSameHigh}); (3) cặp cùng số liệu cosine
     * [0,60; 0,90) cho tới đủ {@code total}. Không trùng {@code existingKeys}; tầng cấu trúc của cặp tính như lô 1.
     * Số cặp trả về có {@code pairNo = 0}: {@link #merge} xáo và đánh số.
     */
    public static Result sampleBatch2(List<Item> items, Set<String> existingKeys, List<BlockedPair> blocked,
                                      Batch2Quotas quotas, long seed) {
        Random rng = new Random(seed);
        List<String> log = new ArrayList<>();
        Set<String> taken = new HashSet<>(existingKeys);
        List<SampledPair> out = new ArrayList<>();
        Map<Integer, Item> byId = new java.util.HashMap<>();
        items.forEach(i -> byId.put(i.id(), i));

        int missing = 0;
        int alreadyInBatch1 = 0;
        List<Pair> blockedPairs = new ArrayList<>();
        Set<String> seenBlocked = new HashSet<>();
        for (BlockedPair b : blocked) {
            Item x = byId.get(b.subjectId());
            Item y = byId.get(b.comparedId());
            String key = keyOf(b.subjectId(), b.comparedId());
            if (x == null || y == null || x.candidate().questionType() != y.candidate().questionType()
                    || x.id() == y.id()) {
                missing++;
                continue;
            }
            if (!seenBlocked.add(key)) {
                continue;
            }
            if (existingKeys.contains(key)) {
                alreadyInBatch1++;
                continue;
            }
            Item a = x.id() < y.id() ? x : y;
            Item c = x.id() < y.id() ? y : x;
            blockedPairs.add(new Pair(a, c, stratumOf(a, c), DedupChecker.diagnose(a.candidate(), c.candidate())));
        }
        log.add("Cặp dedup từng chặn: " + blocked.size() + " dòng; không còn trong kho/không cùng dạng " + missing
                + "; đã có ở lô 1 " + alreadyInBatch1 + "; thêm vào lô 2 " + blockedPairs.size());
        for (Pair p : blockedPairs) {
            out.add(new SampledPair(0, p, 2, GROUP_BLOCKED));
            taken.add(p.key());
        }

        List<Pair> all = allPairs(items).stream().filter(p -> p.diagnostics().sameSignature()).toList();
        int roomHigh = Math.max(0, Math.min(quotas.maxSameHigh(), quotas.total() - out.size()));
        int high = takeBatch2(rng, all, p -> p.diagnostics().cosine() >= PROVISIONAL_HIGH, roomHigh, taken, out,
                GROUP_SAME_NUMBERS_HIGH);
        if (high < quotas.maxSameHigh()) {
            log.add("Cùng số liệu, cosine >= 0,90: tối đa " + quotas.maxSameHigh() + ", chỉ có " + high + " cặp mới");
        }
        int roomMid = Math.max(0, quotas.total() - out.size());
        int mid = takeBatch2(rng, all, p -> p.diagnostics().cosine() >= BAND_LOW
                && p.diagnostics().cosine() < PROVISIONAL_HIGH, roomMid, taken, out, GROUP_SAME_NUMBERS_MID);
        if (mid < roomMid) {
            log.add("Cùng số liệu, cosine 0,60-0,90: cần " + roomMid + ", chỉ có " + mid);
        }
        if (out.size() > quotas.total()) {
            log.add("Lô 2 vượt " + quotas.total() + " cặp vì số cặp dedup từng chặn nhiều hơn hạn mức: " + out.size());
        }
        return new Result(out, log);
    }

    private static final double PROVISIONAL_HIGH = 0.90;

    private static int takeBatch2(Random rng, List<Pair> pool, Predicate<Pair> filter, int k, Set<String> taken,
                                  List<SampledPair> out, String group) {
        if (k <= 0) {
            return 0;
        }
        List<Pair> candidates = new ArrayList<>(pool.stream().filter(filter).filter(p -> !taken.contains(p.key())).toList());
        Collections.shuffle(candidates, rng);
        int n = 0;
        for (Pair p : candidates) {
            if (n >= k) {
                break;
            }
            out.add(new SampledPair(0, p, 2, group));
            taken.add(p.key());
            n++;
        }
        return n;
    }

    /** Gộp hai lô thành một danh sách: xáo bằng {@code seed} rồi đánh số 1..N liên tục. */
    public static List<SampledPair> merge(List<SampledPair> batch1, List<SampledPair> batch2, long seed) {
        List<SampledPair> all = new ArrayList<>(batch1);
        all.addAll(batch2);
        Collections.shuffle(all, new Random(seed));
        List<SampledPair> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            out.add(all.get(i).withPairNo(i + 1));
        }
        return out;
    }

    /** Tầng cấu trúc của một cặp (không bao giờ là random). */
    static SamplingStratum stratumOf(Item a, Item b) {
        if (a.runId() == b.runId()) {
            return SamplingStratum.SAME_RUN;
        }
        return a.templateId() == b.templateId() ? SamplingStratum.CROSS_RUN : SamplingStratum.CROSS_TEMPLATE;
    }

    /** Mọi cặp cùng dạng, sắp theo (id nhỏ, id lớn). */
    static List<Pair> allPairs(List<Item> items) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(Item::id));
        List<Pair> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                Item a = sorted.get(i);
                Item b = sorted.get(j);
                if (a.candidate().questionType() != b.candidate().questionType() || a.id() == b.id()) {
                    continue;
                }
                out.add(new Pair(a, b, stratumOf(a, b), DedupChecker.diagnose(a.candidate(), b.candidate())));
            }
        }
        return out;
    }

    public static Result sample(List<Item> items, Quotas quotas, long seed) {
        Random rng = new Random(seed);
        List<Pair> all = allPairs(items);
        List<String> log = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        List<Pair> chosen = new ArrayList<>();
        Map<SamplingStratum, Integer> used = new EnumMap<>(SamplingStratum.class);
        for (SamplingStratum s : SamplingStratum.values()) {
            used.put(s, 0);
        }

        // (1) cặp đặc biệt, tính vào tầng cấu trúc của chúng (không vượt hạn mức tầng)
        int changed = pick(rng, all, Pair::changedNumbers, quotas.minChangedNumbers(), quotas, taken, chosen, used);
        if (changed < quotas.minChangedNumbers()) {
            log.add("Cặp đổi số liệu: cần " + quotas.minChangedNumbers() + ", có " + changed);
        }
        long negationAlready = chosen.stream().filter(Pair::negationDiffers).count();
        int needNegation = (int) Math.max(0, quotas.minNegation() - negationAlready);
        int negation = pick(rng, all, Pair::negationDiffers, needNegation, quotas, taken, chosen, used);
        if (negation < needNegation) {
            log.add("Cặp lệch phủ định: cần thêm " + needNegation + ", có " + negation);
        }

        // (2) tầng cấu trúc: phần lớn trong vùng, còn lại chia trên/dưới vùng, thiếu thì bù từ bất kỳ cặp nào của tầng
        for (SamplingStratum s : List.of(SamplingStratum.SAME_RUN, SamplingStratum.CROSS_RUN,
                SamplingStratum.CROSS_TEMPLATE)) {
            int quota = quotas.of(s);
            List<Pair> ofStratum = all.stream().filter(p -> p.stratum() == s).toList();
            long inBandAlready = chosen.stream().filter(p -> p.stratum() == s && p.inBand()).count();
            int bandTarget = (int) Math.max(0, Math.round(quota * quotas.bandShare()) - inBandAlready);
            pick(rng, ofStratum, Pair::inBand, Math.min(bandTarget, quota - used.get(s)), quotas, taken, chosen, used);
            int rest = quota - used.get(s);
            pick(rng, ofStratum, p -> p.diagnostics().cosine() >= BAND_HIGH, rest - rest / 2, quotas, taken, chosen, used);
            pick(rng, ofStratum, p -> p.diagnostics().cosine() < BAND_LOW, quota - used.get(s), quotas, taken, chosen,
                    used);
            pick(rng, ofStratum, p -> true, quota - used.get(s), quotas, taken, chosen, used);
            if (used.get(s) < quota) {
                log.add("Tầng " + s.getDbValue() + ": cần " + quota + ", chỉ có " + used.get(s));
            }
        }

        // (3) random: rút đều trên mọi cặp chưa chọn
        List<Pair> rest = new ArrayList<>(all.stream().filter(p -> !taken.contains(p.key())).toList());
        Collections.shuffle(rest, rng);
        for (Pair p : rest.subList(0, Math.min(quotas.random(), rest.size()))) {
            chosen.add(new Pair(p.first(), p.second(), SamplingStratum.RANDOM, p.diagnostics()));
            taken.add(p.key());
        }
        if (Math.min(quotas.random(), rest.size()) < quotas.random()) {
            log.add("Tầng random: cần " + quotas.random() + ", chỉ có " + rest.size());
        }

        Collections.shuffle(chosen, rng);
        List<SampledPair> out = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            out.add(new SampledPair(i + 1, chosen.get(i)));
        }
        return new Result(out, log);
    }

    /** Rút tối đa k cặp thỏa điều kiện, chưa chọn, tầng còn hạn mức. Trả số cặp đã rút. */
    private static int pick(Random rng, List<Pair> pool, Predicate<Pair> filter, int k, Quotas quotas, Set<String> taken,
                            List<Pair> chosen, Map<SamplingStratum, Integer> used) {
        if (k <= 0) {
            return 0;
        }
        List<Pair> candidates = new ArrayList<>(pool.stream().filter(filter).toList());
        Collections.shuffle(candidates, rng);
        int n = 0;
        for (Pair p : candidates) {
            if (n >= k) {
                break;
            }
            if (taken.contains(p.key()) || used.get(p.stratum()) >= quotas.of(p.stratum())) {
                continue;
            }
            chosen.add(p);
            taken.add(p.key());
            used.merge(p.stratum(), 1, Integer::sum);
            n++;
        }
        return n;
    }

    private static List<Double> thresholds() {
        List<Double> out = new ArrayList<>();
        for (int pct = 50; pct <= 95; pct += 5) {
            out.add(pct / 100.0);
        }
        return List.copyOf(out);
    }
}
