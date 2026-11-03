package com.datn.mcq.exam.export;

import com.datn.mcq.mathtype.preview.PreviewRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chọn cách xếp phương án A–D (khuôn đề kiểm tra THCS, 4/11): đo bề rộng hiển thị từng phương án (chữ: số đo phông
 * Times New Roman 12; công thức: phép đo của ảnh xem trước MathType), W = bề rộng dòng sau thụt lề phương án.
 * Mọi phương án ≤ W/4 − đệm → 1 dòng 4 cột; ≤ W/2 − đệm → 2 dòng 2 cột; còn lại mỗi phương án một dòng.
 */
class OptionLayoutTest {

    /** W = (10205 − 283) / 20 = 496,1 pt (bề rộng chữ A4 lề trái 2 cm, phải 1 cm, trừ mốc tab đầu 283 của mẫu). */
    private static final double W = 496.1;
    private static final double PAD = OptionLayout.PADDING_PT;

    // Ba bộ mẫu. Phương án đã được chuẩn hóa dấu chấm cuối (bước chuẩn hóa chạy trước phép đo).
    private static final List<String> SHORT_NUMBERS = List.of("$1$.", "$3$.", "$0$.", "$2$.");
    private static final List<String> MEDIUM_EXPRESSIONS = List.of(
            "$\\frac{x+1}{x-1} + \\frac{2x}{x+3} - \\frac{5}{x}$.",
            "$\\frac{x-1}{x+1} + \\frac{2x}{x-3} - \\frac{5}{x}$.",
            "$(x + 1)(x - 2)(x + 3) + 5x$.",
            "$x^3 - 3x^2 + 3x - 1 = (x - 1)^3$.");
    private static final List<String> LONG_SENTENCES = List.of(
            "Hai đường thẳng song song thì không có điểm chung nào cả.",
            "Hai đường thẳng cắt nhau thì có đúng một điểm chung duy nhất.",
            "Hai đường thẳng trùng nhau thì có vô số điểm chung với nhau.",
            "Hai đường thẳng chéo nhau thì không nằm trong cùng một mặt phẳng.");

    private static double max(List<String> options) {
        double max = 0;
        for (int i = 0; i < options.size(); i++) {
            max = Math.max(max, OptionLayout.widthPt((char) ('A' + i), options.get(i)));
        }
        return max;
    }

    private static double min(List<String> options) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < options.size(); i++) {
            min = Math.min(min, OptionLayout.widthPt((char) ('A' + i), options.get(i)));
        }
        return min;
    }

    @Test
    void soNganThiXepMotDongBonCot() {
        assertTrue(max(SHORT_NUMBERS) > 0 && max(SHORT_NUMBERS) <= W / 4 - PAD,
                "tiền điều kiện: mọi phương án ≤ W/4 − đệm, đo được " + max(SHORT_NUMBERS));
        assertEquals(OptionLayout.FOUR_COLUMNS, OptionLayout.choose(SHORT_NUMBERS));
    }

    @Test
    void bieuThucVuaThiXepHaiDongHaiCot() {
        assertTrue(min(MEDIUM_EXPRESSIONS) > W / 4 - PAD && max(MEDIUM_EXPRESSIONS) <= W / 2 - PAD,
                "tiền điều kiện: có phương án > W/4 − đệm và mọi phương án ≤ W/2 − đệm, đo được "
                        + min(MEDIUM_EXPRESSIONS) + ".." + max(MEDIUM_EXPRESSIONS));
        assertEquals(OptionLayout.TWO_COLUMNS, OptionLayout.choose(MEDIUM_EXPRESSIONS));
    }

    @Test
    void cauChuDaiThiMoiPhuongAnMotDong() {
        assertTrue(min(LONG_SENTENCES) > W / 2 - PAD,
                "tiền điều kiện: mọi phương án > W/2 − đệm, đo được " + min(LONG_SENTENCES));
        assertEquals(OptionLayout.ONE_PER_LINE, OptionLayout.choose(LONG_SENTENCES));
    }

    /** Quy tắc là "mọi phương án": một phương án dài kéo cả nhóm xuống mức ít cột hơn. */
    @Test
    void motPhuongAnDaiKeoCaNhomXuongMucItCotHon() {
        List<String> oneMedium = List.of("$1$.", "$3$.", "$0$.", MEDIUM_EXPRESSIONS.get(0));
        assertEquals(OptionLayout.TWO_COLUMNS, OptionLayout.choose(oneMedium));
        List<String> oneLong = List.of("$1$.", "$3$.", "$0$.", LONG_SENTENCES.get(3));
        assertEquals(OptionLayout.ONE_PER_LINE, OptionLayout.choose(oneLong));
    }

    /** Công thức đo bằng đúng phép đo ảnh xem trước MathType; chữ đo bằng số đo phông (rộng hơn khi dài hơn). */
    @Test
    void congThucDoBangPhepDoAnhXemTruoc() {
        double label = OptionLayout.widthPt('A', "");
        double withFormula = OptionLayout.widthPt('A', "$\\frac{1}{2}$");
        assertEquals(PreviewRenderer.widthPt("\\frac{1}{2}"), withFormula - label, 0.01);
        assertTrue(label > 8 && label < 30, "nhãn \"A. \" đậm cỡ 12 rộng khoảng 15pt: " + label);
        assertTrue(OptionLayout.widthPt('B', "abc") < OptionLayout.widthPt('B', "abcdef"));
        assertEquals(OptionLayout.widthPt('B', "abc") + OptionLayout.widthPt('B', "abc") - OptionLayout.widthPt('B', ""),
                OptionLayout.widthPt('B', "abcabc"), 0.5, "chữ cộng dồn theo bề rộng ký tự");
    }

    /** Công thức không dựng được ảnh: coi như rất rộng (xuống mức ít cột hơn), không ném lỗi. */
    @Test
    void congThucKhongDoDuocThiCoiLaRatRong() {
        assertEquals(OptionLayout.ONE_PER_LINE,
                OptionLayout.choose(List.of("$1$.", "$\\notacommand{x$.", "$0$.", "$2$.")));
    }

    /**
     * Tab dừng tính từ lề trái chữ: mốc đầu của mẫu (283) rồi cộng k × W/4 hoặc W/2 (W = 9922 twips); cột cuối kết thúc
     * đúng ở lề phải. Mẫu của trường có mốc 283, 2906, 5528, 8150 (bước 2622) cho bề rộng chữ cũ 10489.
     */
    @Test
    void tabDungTaiMocDau283CongMotPhanTuHaiPhanTuBaPhanTuW() {
        assertEquals(283, ExamPage.OPTION_LABEL_START_TWIPS);
        assertArrayEquals(new int[]{2764, 5244, 7725}, OptionLayout.FOUR_COLUMNS.stopsTwips());
        assertArrayEquals(new int[]{5244}, OptionLayout.TWO_COLUMNS.stopsTwips());
        assertEquals(0, OptionLayout.ONE_PER_LINE.stopsTwips().length);
        assertEquals(496.1, ExamPage.optionLineWidthPt(), 0.001);
    }
}
