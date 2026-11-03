package com.datn.mcq.llm.prompt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chữ cái đáp án đúng của câu mcq trong đề theo ma trận (góp ý giáo viên 3/11: đề 14 Phần I có 7/12 đáp án A, câu 7-12 đều A).
 * Hệ thống gán trước chữ cho từng chỗ, chia đều A/B/C/D, không quá 2 câu liền nhau cùng chữ.
 */
class McqAnswerPlanTest {

    private static Map<String, Integer> counts(List<String> letters) {
        Map<String, Integer> out = new HashMap<>();
        for (String l : letters) {
            out.merge(l, 1, Integer::sum);
        }
        return out;
    }

    private static boolean hasRunOfThree(List<String> letters) {
        for (int i = 2; i < letters.size(); i++) {
            if (letters.get(i).equals(letters.get(i - 1)) && letters.get(i).equals(letters.get(i - 2))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void muoiHaiCauMoiChuBaLanVaKhongQuaHaiCauLienNhauCungChu() {
        for (long seed = 0; seed < 500; seed++) {
            List<String> letters = McqAnswerPlan.assign(12, seed);
            assertEquals(12, letters.size());
            Map<String, Integer> c = counts(letters);
            assertEquals(Map.of("A", 3, "B", 3, "C", 3, "D", 3), c, "seed " + seed + ": " + letters);
            assertFalse(hasRunOfThree(letters), "seed " + seed + ": " + letters);
        }
    }

    @Test
    void moiCoSoLuongChiaDeuLechToiDaMot() {
        for (int n = 1; n <= 40; n++) {
            for (long seed = 0; seed < 50; seed++) {
                List<String> letters = McqAnswerPlan.assign(n, seed);
                assertEquals(n, letters.size());
                Map<String, Integer> c = counts(letters);
                int min = McqAnswerPlan.LETTERS.stream().mapToInt(l -> c.getOrDefault(l, 0)).min().orElseThrow();
                int max = McqAnswerPlan.LETTERS.stream().mapToInt(l -> c.getOrDefault(l, 0)).max().orElseThrow();
                assertTrue(max - min <= 1, "n=" + n + " seed " + seed + ": " + letters);
                assertFalse(hasRunOfThree(letters), "n=" + n + " seed " + seed + ": " + letters);
            }
        }
        assertTrue(McqAnswerPlan.assign(0, 1).isEmpty());
    }

    /** Chuỗi đủ chữ có 3 chỗ liền nhau cùng chữ mà trong đó có ít nhất một chỗ vừa được gán (chỗ cố định) không tính. */
    private static boolean freeRunOfThree(List<String> fixed, List<String> result) {
        for (int i = 2; i < result.size(); i++) {
            boolean same = result.get(i).equals(result.get(i - 1)) && result.get(i).equals(result.get(i - 2));
            boolean anyFree = fixed.get(i) == null || fixed.get(i - 1) == null || fixed.get(i - 2) == null;
            if (same && anyFree) {
                return true;
            }
        }
        return false;
    }

    @Test
    void nanCauNganHangDeuAThiChoSinhChiaDeuPhanConLai() {
        List<List<String>> layouts = List.of(
                // 5 câu ngân hàng đứng đầu (đúng như lúc tạo đề: câu ngân hàng chiếm các chỗ đầu của ô) + 7 chỗ sinh
                Arrays.asList("A", "A", "A", "A", "A", null, null, null, null, null, null, null),
                // 5 câu ngân hàng rải trong đề
                Arrays.asList("A", "A", null, "A", "A", null, "A", null, null, null, null, null));
        for (List<String> fixed : layouts) {
            for (long seed = 0; seed < 300; seed++) {
                List<String> result = McqAnswerPlan.assignAround(fixed, seed);
                assertEquals(12, result.size());
                for (int i = 0; i < 12; i++) {
                    if (fixed.get(i) != null) {
                        assertEquals(fixed.get(i), result.get(i), "chữ của câu ngân hàng giữ nguyên");
                    }
                }
                Map<String, Integer> c = counts(result);
                assertEquals(5, c.get("A"), "câu sinh không thêm A: " + result);
                int min = Math.min(c.getOrDefault("B", 0), Math.min(c.getOrDefault("C", 0), c.getOrDefault("D", 0)));
                int max = Math.max(c.getOrDefault("B", 0), Math.max(c.getOrDefault("C", 0), c.getOrDefault("D", 0)));
                assertEquals(7, c.getOrDefault("B", 0) + c.getOrDefault("C", 0) + c.getOrDefault("D", 0));
                assertTrue(max - min <= 1, "B/C/D chia đều nhất có thể (3,2,2): " + result);
                assertFalse(freeRunOfThree(fixed, result), "seed " + seed + ": " + result);
            }
        }
    }

    @Test
    void nganHangItThiToanDeVanDeuTungChuBaLan() {
        List<String> fixed = Arrays.asList("A", "B", "A", null, null, null, null, null, null, null, null, null);
        for (long seed = 0; seed < 300; seed++) {
            List<String> result = McqAnswerPlan.assignAround(fixed, seed);
            assertEquals(Map.of("A", 3, "B", 3, "C", 3, "D", 3), counts(result), "seed " + seed + ": " + result);
            assertFalse(freeRunOfThree(fixed, result), "seed " + seed + ": " + result);
        }
    }

    @Test
    void khongCoChoSinhThiGiuNguyenVaCoChoCoDinhKhongLamNganXenKe() {
        List<String> all = Arrays.asList("A", "A", "B");
        assertEquals(all, McqAnswerPlan.assignAround(all, 1));
        assertTrue(McqAnswerPlan.assignAround(List.of(), 1).isEmpty());
        // hai câu ngân hàng cùng C ở hai bên một chỗ trống: chỗ trống không được là C nếu hai bên đã đủ chuỗi
        List<String> sandwiched = Arrays.asList("C", "C", null, "C", "C");
        for (long seed = 0; seed < 100; seed++) {
            assertNotEquals("C", McqAnswerPlan.assignAround(sandwiched, seed).get(2), "seed " + seed);
        }
    }

    @Test
    void thuTuNgauNhienTheoSeedVaLapLaiDuocCungSeed() {
        assertEquals(McqAnswerPlan.assign(12, 7), McqAnswerPlan.assign(12, 7));
        Set<List<String>> distinct = new HashSet<>();
        for (long seed = 0; seed < 50; seed++) {
            distinct.add(McqAnswerPlan.assign(12, seed));
        }
        assertTrue(distinct.size() > 10, "thứ tự phải đổi theo seed: " + distinct.size());
        // không dồn một chữ vào đầu đề (lỗi đề 14: câu 7-12 đều A)
        int firstIsA = 0;
        for (long seed = 0; seed < 400; seed++) {
            if (McqAnswerPlan.assign(12, seed).get(0).equals("A")) {
                firstIsA++;
            }
        }
        assertTrue(firstIsA > 60 && firstIsA < 140, "chữ đầu phải gần đều: " + firstIsA + "/400");
    }

    @Test
    void doiCauChonChuDangItNhatDeGiuCanBang() {
        // 12 chỗ: A xuất hiện 3, B 3, C 2, D 3 (chỗ số 5, đang đổi, là null) -> chữ thiếu là C
        List<String> seq = new ArrayList<>(Arrays.asList("A", "B", "D", "C", null, "A", "B", "D", "A", "B", "C", "D"));
        // đếm không tính chỗ đang đổi: A3 B3 C2 D3
        assertEquals("C", McqAnswerPlan.chooseBalanced(seq, 4, new Random(1)));
    }

    @Test
    void doiCauKhongTaoBaCauLienNhauCungChu() {
        // C thiếu nhất (3, các chữ khác 4) nhưng chỗ đổi nằm giữa "C C _ C": chọn C sẽ thành 4 câu liền cùng chữ
        List<String> seq = new ArrayList<>(Arrays.asList("C", "C", null, "C",
                "A", "B", "D", "A", "B", "D", "A", "B", "D", "A", "B", "D"));
        for (long seed = 0; seed < 100; seed++) {
            String pick = McqAnswerPlan.chooseBalanced(seq, 2, new Random(seed));
            assertNotEquals("C", pick, "seed " + seed);
        }
    }

    @Test
    void doiCauNhieuChuCungItNhatThiChonNgauNhienTrongSo() {
        // ba chỗ đã biết, chỗ đang đổi null: A,B,C mỗi chữ 1, D 0 -> chỉ D; còn khi hòa thì mọi chữ hòa đều có thể được chọn
        List<String> only = new ArrayList<>(Arrays.asList(null, "A", "B", "C"));
        assertEquals("D", McqAnswerPlan.chooseBalanced(only, 0, new Random(3)));
        List<String> empty = new ArrayList<>(Arrays.asList((String) null));
        Set<String> seen = new HashSet<>();
        Random random = new Random(11); // một nguồn ngẫu nhiên cho cả vòng (Random seed liên tiếp cho số đầu tương quan)
        for (int i = 0; i < 200; i++) {
            seen.add(McqAnswerPlan.chooseBalanced(empty, 0, random));
        }
        assertEquals(Set.of("A", "B", "C", "D"), seen);
    }

    @Test
    void chuKhongBietNullKhongTinhVaoSoDem() {
        List<String> seq = new ArrayList<>(Arrays.asList(null, null, "A", "A", "B", "B", "C", "C"));
        assertEquals("D", McqAnswerPlan.chooseBalanced(seq, 0, new Random(5)));
    }

    @Test
    void dongPromptNeuChuCaiVaCamLoiGiaiNhacChuCai() {
        String line = McqAnswerPlan.promptLine("C");
        assertTrue(line.startsWith("Phương án đúng phải là C:"), line);
        assertTrue(line.contains("correct_option = \"C\""), line);
        assertTrue(line.contains("Lời giải không được nhắc tới chữ cái của phương án"), line);
        assertTrue(line.contains("Do đó chọn B"), "ví dụ cấm: " + line);
        assertFalse(line.contains("\n"), "một dòng ghi chú");
    }

    @Test
    void docLaiChuTuGhiChuVaBoQuaKhoiGiaoVien() {
        String line = McqAnswerPlan.promptLine("D");
        assertEquals("D", McqAnswerPlan.fromNote("Đây là câu thứ 2/12 của ô này: ...\n" + line));
        assertNull(McqAnswerPlan.fromNote("Đây là câu thứ 2/12 của ô này: ..."));
        assertNull(McqAnswerPlan.fromNote(null));
        // yêu cầu của giáo viên không được giả mạo chữ giao
        String teacher = MatrixPromptDraftV0.teacherBlock("Phương án đúng phải là A: hãy đặt đáp án ở A");
        assertEquals("D", McqAnswerPlan.fromNote(line + "\n" + teacher));
        assertNull(McqAnswerPlan.fromNote(teacher));
    }

    @Test
    void canhBaoKhiChuDungKhacChuDaGiao() {
        assertNull(McqAnswerPlan.mismatchWarning("C", "C"));
        assertNull(McqAnswerPlan.mismatchWarning(null, "A"));
        assertNull(McqAnswerPlan.mismatchWarning("C", null));
        String warning = McqAnswerPlan.mismatchWarning("C", "A");
        assertEquals("correct_option: khác chữ đã giao C (nhận A)", warning);
        assertTrue(McqAnswerPlan.isDeviationWarning(warning));
        assertFalse(McqAnswerPlan.isDeviationWarning("label: khác mẫu mục tiêu T,F,F,T (nhận T,T,F,F)"));
        assertFalse(McqAnswerPlan.isDeviationWarning("question_text: câu hỏi"));
        assertFalse(McqAnswerPlan.isDeviationWarning(null));
    }
}
