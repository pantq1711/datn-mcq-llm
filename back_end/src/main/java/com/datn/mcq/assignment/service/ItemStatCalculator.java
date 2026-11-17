package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.repository.AssignmentRepository.AnswerRow;
import com.datn.mcq.common.util.ShortAnswerValue;
import com.datn.mcq.enums.QuestionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phân tích câu của một bài giao (docs/design/lam-bai-truc-tuyen.md mục 5.2–5.4). Hàm thuần, không chạm DB. Nơi gọi đã lọc
 * lượt: chỉ lượt đã nộp và không trùng lớp + số thứ tự. {@code docs/thong-ke/11-hau-kiem.sql} tính lại đúng các bước này
 * bằng SQL (test {@code HauKiemSqlTest} giữ hai bên khớp); sửa một bên thì sửa cả bên kia.
 *
 * <ul>
 *   <li>Xếp lượt theo điểm giảm dần, hòa thì theo tổng số câu/ý đúng ({@code correct_count}) giảm dần, rồi id tăng dần.</li>
 *   <li>k = round(0,27 × n) làm tròn nửa lên, tính bằng số nguyên. Nhóm cao H = k lượt đầu, nhóm thấp L = k lượt cuối.</li>
 *   <li>mcq, sa: p = c / n, D = (c_H − c_L) / k. tf: từng ý như một câu đúng/sai; cấp câu p = Σ ý đúng / 4n,
 *       D = (Σ ý đúng của H − của L) / 4k. Bỏ trống là sai. k = 0 thì D = null. p, D làm tròn 4 chữ số (nửa ra xa 0).</li>
 *   <li>Cờ chỉ khi n ≥ {@value #MIN_SAMPLE}, dưới ngưỡng chỉ có {@value #INSUFFICIENT}. Ngưỡng so bằng số nguyên (không qua
 *       D đã làm tròn): D &lt; 0,2 ⇔ 5 × (c_H − c_L) &lt; m × k; "dưới 5%" ⇔ 20 × số lượt &lt; n; "≥ 20% × k" ⇔
 *       5 × số lượt ≥ k.</li>
 * </ul>
 */
final class ItemStatCalculator {

    static final int MIN_SAMPLE = 20;
    static final String INSUFFICIENT = "insufficient_sample";
    static final String LOW_DISCRIMINATION = "low_discrimination";
    static final String UNUSED_OPTION = "unused_option: ";
    static final String KEY_SUSPECT = "key_suspect";
    static final int TOP_WRONG = 5;

    private static final List<String> OPTIONS = List.of("A", "B", "C", "D");
    private static final List<String> STATEMENTS = AttemptGrader.STATEMENT_KEYS;

    /**
     * Một câu của bài giao.
     *
     * @param correctOption đáp án A-D (chỉ mcq)
     * @param tfLabels      4 ký tự T/F theo ý a-d (chỉ tf)
     * @param saKey         đáp số (chỉ sa; không dùng để chấm, chỉ để gom đáp số sai)
     */
    record ItemSpec(Integer slotId, QuestionType type, String correctOption, String tfLabels, String saKey) {}

    /** Một lượt hợp lệ: điểm và câu trả lời theo chỗ (thiếu chỗ nào thì chỗ đó là bỏ trống, 0 đúng). */
    record Sheet(Integer attemptId, BigDecimal score, Map<Integer, AnswerRow> answers) {}

    /** {@code d} null khi k = 0. {@code choiceCounts}: mục 5.3 của thiết kế. */
    record ItemResult(Integer slotId, QuestionType type, BigDecimal p, BigDecimal d, Map<String, Object> choiceCounts,
                      List<String> flags) {}

    record Result(int n, int groupSize, List<ItemResult> items) {}

    private enum Group { HIGH, MID, LOW }

    private ItemStatCalculator() {}

    /** k = round(0,27 × n), nửa lên, bằng số nguyên (n = 50 → 14). */
    static int groupSize(int n) {
        return (27 * n + 50) / 100;
    }

    static Result compute(List<ItemSpec> items, List<Sheet> sheets) {
        int n = sheets.size();
        if (n == 0) {
            return new Result(0, 0, List.of());
        }
        int k = groupSize(n);
        List<Sheet> ranked = new ArrayList<>(sheets);
        ranked.sort(Comparator.comparing(Sheet::score, Comparator.reverseOrder())
                .thenComparing(ItemStatCalculator::totalCorrect, Comparator.reverseOrder())
                .thenComparing(Sheet::attemptId));
        List<Group> groups = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            groups.add(i < k ? Group.HIGH : i >= n - k ? Group.LOW : Group.MID);
        }
        List<ItemResult> out = new ArrayList<>(items.size());
        for (ItemSpec item : items) {
            out.add(switch (item.type()) {
                case MCQ -> mcq(item, ranked, groups, n, k);
                case TF -> tf(item, ranked, groups, n, k);
                case SA -> sa(item, ranked, groups, n, k);
            });
        }
        return new Result(n, k, List.copyOf(out));
    }

    private static int totalCorrect(Sheet s) {
        int total = 0;
        for (AnswerRow a : s.answers().values()) {
            total += a.correctCount();
        }
        return total;
    }

    private static AnswerRow answer(Sheet s, Integer slotId) {
        return s.answers().get(slotId);
    }

    private static int correct(AnswerRow a) {
        return a == null ? 0 : a.correctCount();
    }

    static BigDecimal ratio(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal discrimination(long high, long low, long max, int k) {
        return k == 0 ? null : ratio(high - low, max * k);
    }

    /** D &lt; 0,2 so bằng số nguyên; k = 0 không xảy ra khi n ≥ 20. */
    private static boolean lowDiscrimination(long high, long low, long max, int k) {
        return 5 * (high - low) < max * k;
    }

    // ---------- trắc nghiệm ----------

    private static ItemResult mcq(ItemSpec item, List<Sheet> ranked, List<Group> groups, int n, int k) {
        Map<String, int[]> counts = new LinkedHashMap<>(); // all, high, low
        for (String o : OPTIONS) {
            counts.put(o, new int[3]);
        }
        counts.put("blank", new int[3]);
        int c = 0;
        int cHigh = 0;
        int cLow = 0;
        for (int i = 0; i < ranked.size(); i++) {
            AnswerRow a = answer(ranked.get(i), item.slotId());
            String choice = a == null || a.mcqChoice() == null ? "blank" : a.mcqChoice();
            int[] cell = counts.computeIfAbsent(choice, x -> new int[3]);
            int ok = correct(a);
            c += ok;
            cell[0]++;
            if (groups.get(i) == Group.HIGH) {
                cell[1]++;
                cHigh += ok;
            } else if (groups.get(i) == Group.LOW) {
                cell[2]++;
                cLow += ok;
            }
        }
        Map<String, Object> choiceCounts = new LinkedHashMap<>();
        counts.forEach((o, cell) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("all", cell[0]);
            m.put("high", cell[1]);
            m.put("low", cell[2]);
            choiceCounts.put(o, m);
        });
        List<String> flags = new ArrayList<>();
        if (n < MIN_SAMPLE) {
            flags.add(INSUFFICIENT);
        } else {
            if (lowDiscrimination(cHigh, cLow, 1, k)) {
                flags.add(LOW_DISCRIMINATION);
            }
            for (String o : OPTIONS) {
                if (!o.equals(item.correctOption()) && 20L * counts.get(o)[0] < n) {
                    flags.add(UNUSED_OPTION + o);
                }
            }
            for (String o : OPTIONS) {
                int[] cell = counts.get(o);
                if (!o.equals(item.correctOption()) && cell[1] > cell[2] && cell[1] >= 2 && 5 * cell[1] >= k) {
                    flags.add(KEY_SUSPECT + ": " + o);
                }
            }
        }
        return new ItemResult(item.slotId(), item.type(), ratio(c, n), discrimination(cHigh, cLow, 1, k), choiceCounts,
                List.copyOf(flags));
    }

    // ---------- Đúng/Sai ----------

    private static ItemResult tf(ItemSpec item, List<Sheet> ranked, List<Group> groups, int n, int k) {
        String labels = item.tfLabels();
        Map<String, Object> choiceCounts = new LinkedHashMap<>();
        List<String> statementFlags = new ArrayList<>();
        int sumAll = 0;
        int sumHigh = 0;
        int sumLow = 0;
        for (int s = 0; s < STATEMENTS.size(); s++) {
            int t = 0;
            int f = 0;
            int blank = 0;
            int c = 0;
            int cHigh = 0;
            int cLow = 0;
            for (int i = 0; i < ranked.size(); i++) {
                AnswerRow a = answer(ranked.get(i), item.slotId());
                char ch = a == null || a.tfChoices() == null || a.tfChoices().length() <= s ? AttemptGrader.BLANK
                        : a.tfChoices().charAt(s);
                if (ch == 'T') {
                    t++;
                } else if (ch == 'F') {
                    f++;
                } else {
                    blank++;
                }
                int ok = ch != AttemptGrader.BLANK && labels != null && labels.length() > s && ch == labels.charAt(s)
                        ? 1 : 0;
                c += ok;
                if (groups.get(i) == Group.HIGH) {
                    cHigh += ok;
                } else if (groups.get(i) == Group.LOW) {
                    cLow += ok;
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("T", t);
            m.put("F", f);
            m.put("blank", blank);
            m.put("correct", c);
            m.put("p", ratio(c, n));
            m.put("d", discrimination(cHigh, cLow, 1, k));
            choiceCounts.put(STATEMENTS.get(s), m);
            if (cLow - cHigh >= 2) {
                statementFlags.add(KEY_SUSPECT + ": " + STATEMENTS.get(s));
            }
            sumAll += c;
            sumHigh += cHigh;
            sumLow += cLow;
        }
        List<String> flags = new ArrayList<>();
        if (n < MIN_SAMPLE) {
            flags.add(INSUFFICIENT);
        } else {
            if (lowDiscrimination(sumHigh, sumLow, 4, k)) {
                flags.add(LOW_DISCRIMINATION);
            }
            flags.addAll(statementFlags);
        }
        return new ItemResult(item.slotId(), item.type(), ratio(sumAll, 4L * n), discrimination(sumHigh, sumLow, 4, k),
                choiceCounts, List.copyOf(flags));
    }

    // ---------- Trả lời ngắn ----------

    /** Nhóm đáp số sai: theo giá trị khi phân tích được (chế độ rộng như lúc chấm), không thì theo chuỗi. */
    private static final class WrongGroup {
        int count;
        final Map<String, Integer> raw = new HashMap<>();

        String display() {
            return raw.entrySet().stream()
                    .min(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.<String, Integer>comparingByKey()))
                    .orElseThrow().getKey();
        }
    }

    private static ItemResult sa(ItemSpec item, List<Sheet> ranked, List<Group> groups, int n, int k) {
        int c = 0;
        int cHigh = 0;
        int cLow = 0;
        int blank = 0;
        Map<String, WrongGroup> wrong = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            AnswerRow a = answer(ranked.get(i), item.slotId());
            int ok = correct(a);
            c += ok;
            if (groups.get(i) == Group.HIGH) {
                cHigh += ok;
            } else if (groups.get(i) == Group.LOW) {
                cLow += ok;
            }
            String text = a == null ? null : a.saText();
            if (text == null || text.isBlank()) {
                blank++;
            } else if (ok == 0) {
                ShortAnswerValue.ParseResult parsed = ShortAnswerValue.parse(text, ShortAnswerValue.Mode.LENIENT);
                String key = parsed.ok() ? "v:" + parsed.value().canonicalKey() : "s:" + text;
                WrongGroup g = wrong.computeIfAbsent(key, x -> new WrongGroup());
                g.count++;
                g.raw.merge(text, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> topWrong = wrong.values().stream()
                .map(g -> Map.entry(g.display(), g.count))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.<String, Integer>comparingByKey()))
                .limit(TOP_WRONG)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("value", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .toList();
        Map<String, Object> choiceCounts = new LinkedHashMap<>();
        choiceCounts.put("correct", c);
        choiceCounts.put("blank", blank);
        choiceCounts.put("topWrong", topWrong);
        List<String> flags = new ArrayList<>();
        if (n < MIN_SAMPLE) {
            flags.add(INSUFFICIENT);
        } else {
            if (lowDiscrimination(cHigh, cLow, 1, k)) {
                flags.add(LOW_DISCRIMINATION);
            }
            if (cLow - cHigh >= 2) {
                flags.add(KEY_SUSPECT);
            }
        }
        return new ItemResult(item.slotId(), item.type(), ratio(c, n), discrimination(cHigh, cLow, 1, k), choiceCounts,
                List.copyOf(flags));
    }
}
