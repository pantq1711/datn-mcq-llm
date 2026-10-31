package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lớp 1 chống câu cùng khuôn trong một đề (thẩm định 31/10): prompt sinh câu cho một chỗ có khối "câu đã có trong ô này"
 * (tối đa 6 câu, mỗi câu dẫn cắt 200 ký tự) và dòng "câu thứ k/N" giao khía cạnh thứ ((k-1) mod m)+1 trong m yêu cầu cần
 * đạt. Hàm thuần: không DB, không LLM.
 */
class MatrixPromptSameCellTest {

    private static MatrixCellSpec spec() {
        return new MatrixCellSpec("Toán", 8, "Tứ giác", "Tính góc", Map.of(Difficulty.EASY, "Nhận biết tổng các góc."),
                QuestionType.MCQ, Difficulty.EASY, List.of());
    }

    private static String build(List<String> same) {
        return MatrixPromptDraftV0.buildGenerationPrompt(spec(), List.of(), null, null, same);
    }

    // ---------- khối câu đã có trong ô ----------

    @Test
    void khoiLietKeCauDaCoVaYeuCauChonKhuonKhac() {
        String p = build(List.of("Cho hình thang cân $WXYZ$ có góc $W$ bằng $75^\\circ$.", "Tổng các góc của tứ giác là:"));
        assertTrue(p.contains(MatrixPromptDraftV0.SAME_CELL_HEADER), p);
        assertTrue(p.contains("1. Cho hình thang cân $WXYZ$ có góc $W$ bằng $75^\\circ$.\n"), p);
        assertTrue(p.contains("2. Tổng các góc của tứ giác là:\n"), p);
        assertTrue(p.contains(MatrixPromptDraftV0.SAME_CELL_RULE), p);
        assertTrue(p.indexOf(MatrixPromptDraftV0.SAME_CELL_HEADER) < p.indexOf(MatrixPromptDraftV0.SAME_CELL_RULE));
        assertEquals("Chọn dạng bài hoặc khía cạnh khác các câu trên; không lặp lại khuôn câu chỉ đổi số liệu hay tên điểm.",
                MatrixPromptDraftV0.SAME_CELL_RULE);
    }

    @Test
    void toiDaSauCauMoiCauCatHaiTramKyTu() {
        List<String> texts = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            texts.add("Câu số " + i + " " + "x".repeat(300));
        }
        String p = build(texts);
        String block = p.substring(p.indexOf(MatrixPromptDraftV0.SAME_CELL_HEADER),
                p.indexOf(MatrixPromptDraftV0.SAME_CELL_RULE));
        List<String> lines = Arrays.stream(block.split("\n")).filter(l -> l.matches("^\\d+\\. .*")).toList();
        assertEquals(6, lines.size(), block);
        assertTrue(block.contains("6. Câu số 6 "), block);
        assertFalse(block.contains("7. "), block);
        for (String line : lines) {
            // "k. " + tối đa 200 ký tự + "…"
            assertTrue(line.length() <= 3 + 200 + 1, line.length() + ": " + line);
            assertTrue(line.endsWith("…"), line);
        }
    }

    @Test
    void khoiThuGonXuongDongVaBoQuaCauTrong() {
        String p = build(java.util.Arrays.asList("Dòng một\n\n  dòng hai", null, "   ", "Câu ba"));
        assertTrue(p.contains("1. Dòng một dòng hai\n"), p);
        assertTrue(p.contains("2. Câu ba\n"), p);
        assertFalse(p.contains("3. "), p);
    }

    @Test
    void khongCoCauNaoThiKhongCoKhoi() {
        String plain = MatrixPromptDraftV0.buildGenerationPrompt(spec(), List.of(), null, null);
        assertEquals(plain, build(List.of()));
        assertEquals(plain, build(null));
        assertEquals(plain, build(java.util.Arrays.asList(" ", null)));
        assertFalse(plain.contains(MatrixPromptDraftV0.SAME_CELL_HEADER));
    }

    @Test
    void khoiDatSauDanhSachCauDaSinhVaTruocGhiChuCuaJob() {
        String note = "Đây là câu thứ 2/3 của ô này.";
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec(),
                List.of(new PreviousQuestion("Câu cũ của ô", List.of())), note, null, List.of("Câu cùng đề"));
        int previous = p.indexOf(PreviousQuestion.HEADER);
        int same = p.indexOf(MatrixPromptDraftV0.SAME_CELL_HEADER);
        int noteAt = p.indexOf(note);
        assertTrue(previous >= 0 && previous < same, p);
        assertTrue(same < noteAt, p);
        assertTrue(noteAt < p.indexOf("Yêu cầu:"), "ghi chú vẫn nằm trước phần yêu cầu đầu ra");
    }

    // ---------- phân công khía cạnh theo thứ tự chỗ ----------

    private static final List<Difficulty> THREE = List.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD);

    @Test
    void khiaCanhThuJBangKTruMotModMCongMot() {
        // m = 3 khía cạnh, n = 3 chỗ: mỗi chỗ một khía cạnh riêng, không cần dặn đổi hình thức.
        for (int k = 1; k <= 3; k++) {
            String line = MatrixPromptDraftV0.slotLine(k, 3, THREE);
            assertTrue(line.startsWith(MatrixPromptDraftV0.slotLine(k, 3)), line);
            assertTrue(line.contains("khía cạnh thứ " + k + "/3"), line);
            assertFalse(line.contains(MatrixPromptDraftV0.SAME_FORM_HINT), line);
        }
    }

    @Test
    void nhieuChoHonKhiaCanhThiXoayVongVaDanDoiHinhThuc() {
        // m = 3, n = 4: chỗ 4 quay lại khía cạnh 1 và cả ô được dặn đổi hình thức hỏi.
        String fourth = MatrixPromptDraftV0.slotLine(4, 4, THREE);
        assertTrue(fourth.contains("khía cạnh thứ 1/3"), fourth);
        assertTrue(fourth.contains(MatrixPromptDraftV0.SAME_FORM_HINT), fourth);
        String second = MatrixPromptDraftV0.slotLine(2, 4, THREE);
        assertTrue(second.contains("khía cạnh thứ 2/3"), second);
        assertTrue(second.contains(MatrixPromptDraftV0.SAME_FORM_HINT), second);
    }

    @Test
    void motKhiaCanhHaiChoCungKhiaCanhMotVaDanDoiHinhThuc() {
        for (int k = 1; k <= 2; k++) {
            String line = MatrixPromptDraftV0.slotLine(k, 2, List.of(Difficulty.EASY));
            assertTrue(line.contains("khía cạnh thứ 1/1"), line);
            assertTrue(line.contains(MatrixPromptDraftV0.SAME_FORM_HINT), line);
        }
    }

    @Test
    void khongCoYeuCauCanDatChiDanDoiHinhThuc() {
        String line = MatrixPromptDraftV0.slotLine(2, 3, List.of());
        assertTrue(line.startsWith(MatrixPromptDraftV0.slotLine(2, 3)), line);
        assertFalse(line.contains("khía cạnh thứ"), line);
        assertTrue(line.contains(MatrixPromptDraftV0.SAME_FORM_HINT), line);
    }

    @Test
    void oMotCauKhongCoDongKN() {
        assertNull(MatrixPromptDraftV0.slotLine(1, 1, THREE));
        assertNull(MatrixPromptDraftV0.slotLine(1, 1, List.of()));
    }
}
