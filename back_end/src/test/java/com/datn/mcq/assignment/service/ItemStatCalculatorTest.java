package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.repository.AssignmentRepository.AnswerRow;
import com.datn.mcq.assignment.service.ItemStatCalculator.ItemResult;
import com.datn.mcq.assignment.service.ItemStatCalculator.ItemSpec;
import com.datn.mcq.assignment.service.ItemStatCalculator.Result;
import com.datn.mcq.assignment.service.ItemStatCalculator.Sheet;
import com.datn.mcq.enums.QuestionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Công thức phân tích câu (docs/design/lam-bai-truc-tuyen.md mục 5.2–5.4) trên bộ dữ liệu tính tay. Hàm thuần, không cần
 * Spring. Điểm lượt cho thẳng (không chấm lại); số câu/ý đúng của lượt lấy từ {@code correct_count} như ở DB.
 *
 * <p><b>Bộ 20 lượt (id 1..20), k = round(0,27 × 20) = round(5,4) = 5.</b> Xếp hạng theo điểm giảm dần, hòa thì theo tổng
 * số câu/ý đúng giảm dần, rồi id tăng dần:
 * <ul>
 *   <li>id 5 và 6 cùng 1,5 điểm; id 6 đúng 4 (câu M đúng), id 5 đúng 3 ⇒ id 6 hạng 5 (nhóm cao), id 5 hạng 6.</li>
 *   <li>id 15 và 16 cùng 0,5 điểm, cùng đúng 3 ⇒ theo id: id 15 hạng 15, id 16 hạng 16 (nhóm thấp).</li>
 *   <li>H = {1, 2, 3, 4, 6}; L = {16, 17, 18, 19, 20}.</li>
 * </ul>
 *
 * <p><b>M</b> (trắc nghiệm, đáp án B), lựa chọn theo id: {@code BBBBABBBBCB-BDBCCA-C}. c = 11, c_H = 5, c_L = 0 ⇒
 * p = 11/20 = 0,55; D = (5 − 0)/5 = 1,0. Đếm (tất cả/cao/thấp): A 2/0/1, B 11/5/0, C 4/0/3, D 1/0/0, bỏ trống 2/0/1.
 * Không cờ (D có 1 lượt = đúng 5% nên không phải "dưới 5%").
 *
 * <p><b>T</b> (Đúng/Sai, nhãn T F F T). Từng ý (đúng tất cả, đúng H, đúng L): a 15/5/2 ⇒ p 0,75, D 0,6;
 * b 10/5/0 ⇒ 0,5, 1,0; c 13/2/4 ⇒ 0,65, −0,4 (thấp hơn cao 2 lượt ⇒ {@code key_suspect: c}); d 11/4/1 ⇒ 0,55, 0,6.
 * Cấp câu: p = 49/(4 × 20) = 0,6125; D = (16 − 7)/(4 × 5) = 0,45.
 *
 * <p><b>S</b> (Trả lời ngắn, đáp số 2,5): c = 9, c_H = 4, c_L = 1 ⇒ p 0,45, D 0,6; bỏ trống 3; sai hay gặp: giá trị 25
 * ("25" × 4, "25,0" × 1) = 5 lượt, rồi "-2,5", "2,4", "abc" mỗi đáp số 1 lượt.
 */
class ItemStatCalculatorTest {

    private static final int M = 101;
    private static final int T = 102;
    private static final int S = 103;

    private static final String M_CHOICES = "BBBBABBBBCB-BDBCCA-C";
    private static final String[] T_CHOICES = {"TFFT", "TFFT", "TFTT", "TFTT", "TFT-", "TFT-", "TFFT", "TTFT", "TFFF",
            "TTFT", "FFFT", "----", "TFFT", "TTTT", "TTFF", "TTFF", "FTFF", "FTFT", "----", "TTFF"};
    private static final String[] S_TEXTS = {"2.5", "2,5", "2,50", "25", "2,5", "2,5", "25", "2,5", null, "2.5", "-2,5",
            null, "2,5", "25", "2,4", "2,5", "25", "abc", null, "25,0"};
    private static final boolean[] S_CORRECT = {true, true, true, false, true, true, false, true, false, true, false,
            false, true, false, false, true, false, false, false, false};
    private static final String[] SCORES = {"2.0", "1.9", "1.8", "1.7", "1.5", "1.5", "1.4", "1.3", "1.2", "1.1", "1.0",
            "0.9", "0.8", "0.7", "0.5", "0.5", "0.4", "0.3", "0.2", "0.1"};

    private static final List<ItemSpec> ITEMS = List.of(
            new ItemSpec(M, QuestionType.MCQ, "B", null, null),
            new ItemSpec(T, QuestionType.TF, null, "TFFT", null),
            new ItemSpec(S, QuestionType.SA, null, null, "2,5"));

    // ---------- dựng dữ liệu ----------

    private static int tfCorrect(String labels, String choices) {
        int c = 0;
        for (int i = 0; i < 4; i++) {
            if (choices.charAt(i) == labels.charAt(i)) {
                c++;
            }
        }
        return c;
    }

    private static AnswerRow mcq(int attempt, int slot, char choice, char key) {
        return new AnswerRow(attempt, slot, choice == '-' ? null : String.valueOf(choice), null, null,
                choice == key ? 1 : 0, BigDecimal.ZERO);
    }

    private static AnswerRow tf(int attempt, int slot, String labels, String choices) {
        return new AnswerRow(attempt, slot, null, choices, null, tfCorrect(labels, choices), BigDecimal.ZERO);
    }

    private static AnswerRow sa(int attempt, int slot, String text, boolean correct) {
        return new AnswerRow(attempt, slot, null, null, text, correct ? 1 : 0, BigDecimal.ZERO);
    }

    private static List<Sheet> handData() {
        List<Sheet> sheets = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int id = i + 1;
            Map<Integer, AnswerRow> answers = new HashMap<>();
            answers.put(M, mcq(id, M, M_CHOICES.charAt(i), 'B'));
            answers.put(T, tf(id, T, "TFFT", T_CHOICES[i]));
            answers.put(S, sa(id, S, S_TEXTS[i], S_CORRECT[i]));
            sheets.add(new Sheet(id, new BigDecimal(SCORES[i]), answers));
        }
        // Thứ tự đưa vào không ảnh hưởng kết quả.
        java.util.Collections.reverse(sheets);
        return sheets;
    }

    // ---------- kiểm ----------

    private static void assertDec(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())),
                "mong đợi " + expected + ", được " + actual);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    private static void assertCounts(Map<String, Object> counts, String key, int all, int high, int low) {
        Map<String, Object> c = map(counts.get(key));
        assertEquals(List.of(all, high, low), List.of(((Number) c.get("all")).intValue(),
                ((Number) c.get("high")).intValue(), ((Number) c.get("low")).intValue()), key);
    }

    private static void assertStatement(Map<String, Object> counts, String key, int t, int f, int blank, int correct,
                                        String p, String d) {
        Map<String, Object> st = map(counts.get(key));
        assertEquals(List.of(t, f, blank, correct), List.of(((Number) st.get("T")).intValue(),
                ((Number) st.get("F")).intValue(), ((Number) st.get("blank")).intValue(),
                ((Number) st.get("correct")).intValue()), "ý " + key);
        assertDec(p, st.get("p"));
        assertDec(d, st.get("d"));
    }

    private static ItemResult item(Result r, int slot) {
        return r.items().stream().filter(i -> i.slotId() == slot).findFirst().orElseThrow();
    }

    @Test
    void boDuLieuTinhTayHaiMuoiLuot() {
        Result r = ItemStatCalculator.compute(ITEMS, handData());
        assertEquals(20, r.n());
        assertEquals(5, r.groupSize());
        assertEquals(List.of(M, T, S), r.items().stream().map(ItemResult::slotId).toList(), "giữ thứ tự câu");

        ItemResult m = item(r, M);
        assertDec("0.5500", m.p());
        assertDec("1.0000", m.d());
        assertEquals(List.of(), m.flags());
        assertCounts(m.choiceCounts(), "A", 2, 0, 1);
        assertCounts(m.choiceCounts(), "B", 11, 5, 0);
        assertCounts(m.choiceCounts(), "C", 4, 0, 3);
        assertCounts(m.choiceCounts(), "D", 1, 0, 0);
        assertCounts(m.choiceCounts(), "blank", 2, 0, 1);

        ItemResult t = item(r, T);
        assertDec("0.6125", t.p());
        assertDec("0.4500", t.d());
        assertEquals(List.of("key_suspect: c"), t.flags());
        assertStatement(t.choiceCounts(), "a", 15, 3, 2, 15, "0.75", "0.6");
        assertStatement(t.choiceCounts(), "b", 8, 10, 2, 10, "0.5", "1.0");
        assertStatement(t.choiceCounts(), "c", 5, 13, 2, 13, "0.65", "-0.4");
        assertStatement(t.choiceCounts(), "d", 11, 5, 4, 11, "0.55", "0.6");

        ItemResult s = item(r, S);
        assertDec("0.4500", s.p());
        assertDec("0.6000", s.d());
        assertEquals(List.of(), s.flags());
        assertEquals(9, ((Number) s.choiceCounts().get("correct")).intValue());
        assertEquals(3, ((Number) s.choiceCounts().get("blank")).intValue());
        assertEquals(List.of(Map.of("value", "25", "count", 5), Map.of("value", "-2,5", "count", 1),
                Map.of("value", "2,4", "count", 1), Map.of("value", "abc", "count", 1)),
                s.choiceCounts().get("topWrong"));
    }

    /** k = round(0,27 × n), làm tròn nửa lên, không lệch do số thực (n = 50: 13,5 → 14). */
    @ParameterizedTest
    @CsvSource({"0,0", "1,0", "2,1", "3,1", "19,5", "20,5", "37,10", "38,10", "50,14", "76,21", "150,41"})
    void kLaHaiMuoiBayPhanTramLamTronNuaLen(int n, int k) {
        assertEquals(k, ItemStatCalculator.groupSize(n));
    }

    @Test
    void khongCoLuotNaoThiKhongCoDong() {
        Result r = ItemStatCalculator.compute(ITEMS, List.of());
        assertEquals(0, r.n());
        assertEquals(0, r.groupSize());
        assertEquals(List.of(), r.items());
    }

    /** n < 20: chỉ cờ insufficient_sample (dù D âm), D vẫn tính; n = 1 thì k = 0 và D = NULL. */
    @Test
    void mauNhoChiCoCoThieuMauVaDVanCo() {
        List<Sheet> three = handData().subList(0, 3); // id 20, 19, 18 (đã đảo)
        Result r = ItemStatCalculator.compute(ITEMS, three);
        assertEquals(3, r.n());
        assertEquals(1, r.groupSize());
        for (ItemResult i : r.items()) {
            assertEquals(List.of("insufficient_sample"), i.flags(), "câu " + i.slotId());
        }
        // M: id 18 (0,3 điểm) chọn A, id 20 (0,1) chọn C, id 19 bỏ trống ⇒ c = 0, D = 0.
        assertDec("0", item(r, M).p());
        assertDec("0", item(r, M).d());

        Result one = ItemStatCalculator.compute(ITEMS, handData().subList(0, 1));
        assertEquals(0, one.groupSize());
        for (ItemResult i : one.items()) {
            assertNull(i.d(), "k = 0 thì D = NULL");
            assertEquals(List.of("insufficient_sample"), i.flags());
        }
        assertNull(map(item(one, T).choiceCounts().get("a")).get("d"));
    }

    // ---------- cờ ----------

    private static final int X = 201;
    private static final int Y = 202;
    private static final int Z = 203;
    private static final int TT = 204;
    private static final int SS = 205;

    /** 20 lượt, điểm giảm theo id (H = 1..5, L = 16..20). Chuỗi lựa chọn theo id, '-' là bỏ trống. */
    private static List<Sheet> flagData() {
        String x = "AACCA" + "AAAAAAAAAA" + "AABBA";
        String y = "AAAAC" + "AAAAAAAAAA" + "ABBDD";
        String z = "AAAAB" + "AAAAAAAAAD" + "AAABC";
        String[] tt = new String[20];
        String[] ss = new String[20];
        for (int i = 0; i < 20; i++) {
            tt[i] = "TTTT";
            ss[i] = "1";
        }
        tt[0] = "FTTT";
        tt[1] = "FTTT";
        tt[19] = "FTTT";
        ss[1] = "0";
        ss[2] = "0";
        ss[3] = "0";
        List<Sheet> sheets = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int id = i + 1;
            Map<Integer, AnswerRow> answers = new HashMap<>();
            answers.put(X, mcq(id, X, x.charAt(i), 'A'));
            answers.put(Y, mcq(id, Y, y.charAt(i), 'A'));
            answers.put(Z, mcq(id, Z, z.charAt(i), 'A'));
            answers.put(TT, tf(id, TT, "TTTT", tt[i]));
            answers.put(SS, sa(id, SS, ss[i], "1".equals(ss[i])));
            sheets.add(new Sheet(id, BigDecimal.valueOf(100 - id), answers));
        }
        return sheets;
    }

    private static final List<ItemSpec> FLAG_ITEMS = List.of(
            new ItemSpec(X, QuestionType.MCQ, "A", null, null),
            new ItemSpec(Y, QuestionType.MCQ, "A", null, null),
            new ItemSpec(Z, QuestionType.MCQ, "A", null, null),
            new ItemSpec(TT, QuestionType.TF, null, "TTTT", null),
            new ItemSpec(SS, QuestionType.SA, null, null, "1"));

    /**
     * X: c_H = 3, c_L = 3 ⇒ D = 0 (low_discrimination); D không ai chọn (unused_option); C: cao 2 > thấp 0 và 2 ≥ max(2, 1)
     * (key_suspect). Y: D = 0,6; C chỉ 1 lượt cao (dưới ngưỡng 2); mỗi phương án sai ≥ 1 lượt = 5% (không phải dưới 5%).
     * Z: D = (4 − 3)/5 = 0,2 đúng ngưỡng (không bật). TT: ý a cao đúng 3, thấp đúng 4 (chênh 1, không key_suspect); cấp câu
     * D = (18 − 19)/20 &lt; 0,2. SS: c_H = 2, c_L = 5 ⇒ D = −0,6, thấp hơn cao 3 lượt ⇒ key_suspect.
     */
    @Test
    void moiCoCoCaBatVaCaKhongBat() {
        Result r = ItemStatCalculator.compute(FLAG_ITEMS, flagData());
        assertEquals(5, r.groupSize());
        assertEquals(List.of("low_discrimination", "unused_option: D", "key_suspect: C"), item(r, X).flags());
        assertDec("0", item(r, X).d());
        assertEquals(List.of(), item(r, Y).flags());
        assertDec("0.6", item(r, Y).d());
        assertEquals(List.of(), item(r, Z).flags());
        assertDec("0.2", item(r, Z).d());
        assertEquals(List.of("low_discrimination"), item(r, TT).flags());
        assertDec("-0.05", item(r, TT).d());
        assertEquals(List.of("low_discrimination", "key_suspect"), item(r, SS).flags());
        assertDec("-0.6", item(r, SS).d());
    }

    /**
     * Ngưỡng key_suspect của trắc nghiệm là max(2, 20% × k): n = 40 ⇒ k = 11, cần ≥ 2,2 tức 3 lượt nhóm cao. H = 1..11:
     * id 1–6 chọn A (đúng), 7–8 chọn C, 9–11 chọn B; còn lại chọn A.
     */
    @Test
    void nguongKeySuspectTangTheoK() {
        String choices = "AAAAAACCBBB" + "A".repeat(29);
        List<Sheet> sheets = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            int id = i + 1;
            sheets.add(new Sheet(id, BigDecimal.valueOf(100 - id), Map.of(X, mcq(id, X, choices.charAt(i), 'A'))));
        }
        Result r = ItemStatCalculator.compute(List.of(new ItemSpec(X, QuestionType.MCQ, "A", null, null)), sheets);
        assertEquals(11, r.groupSize());
        // c_H = 6, c_L = 11 ⇒ D âm; D không ai chọn; C (cao 2) dưới ngưỡng 3; B (cao 3) đạt.
        assertEquals(List.of("low_discrimination", "unused_option: D", "key_suspect: B"), item(r, X).flags());
        assertDec("-0.4545", item(r, X).d());
    }
}
