package com.datn.mcq.exam.export;

import com.datn.mcq.mathtype.preview.PreviewRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.util.Arrays;
import java.util.List;

/**
 * Cách xếp bốn phương án A–D của câu trắc nghiệm (khuôn đề kiểm tra THCS, 4/11). Đo bề rộng hiển thị từng phương án rồi
 * chọn theo W = bề rộng dòng sau mốc tab đầu 283 ({@link ExamPage#optionLineWidthPt()}, 496,1 pt):
 * <ul>
 *   <li>mọi phương án ≤ W/4 − đệm: một dòng bốn cột (tab dừng tại 283 + W/4, + W/2, + 3W/4);</li>
 *   <li>mọi phương án ≤ W/2 − đệm: hai dòng, "A, B" rồi "C, D" (tab dừng tại W/2);</li>
 *   <li>còn lại: mỗi phương án một dòng.</li>
 * </ul>
 *
 * <p>Đo chữ bằng số đo phông Times New Roman 12 (26/11: hoặc 14, theo cỡ chữ của đề; trên máy chủ Linux là Liberation
 * Serif, cùng metric); đo công thức bằng
 * đúng phép đo ảnh xem trước MathType ({@link PreviewRenderer#widthPt}) cho cả hai dạng công thức, để bố cục Equation và
 * MathType giống nhau. Công thức không đo được coi là rất rộng (xuống mức ít cột hơn), không báo lỗi.
 */
public enum OptionLayout {

    FOUR_COLUMNS(4),
    TWO_COLUMNS(2),
    ONE_PER_LINE(1);

    private static final Logger log = LoggerFactory.getLogger(OptionLayout.class);

    /**
     * Khoảng đệm (pt) giữa chữ phương án và tab dừng kế tiếp (chữ không được chạm cột sau). Đo trên Word (4/11, công thức
     * Equation cỡ 13): đa thức rộng hơn số đo của ảnh xem trước khoảng 8% (200pt đo được thành 216pt), phân thức hẹp
     * hơn nhiều; 20pt đủ cho chênh lệch 8% ở mức W/2 (223pt) và vẫn để ≥ 12pt trống ở mức W/4.
     */
    public static final double PADDING_PT = 20;

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final Font PLAIN = font(Font.PLAIN);
    private static final Font BOLD = font(Font.BOLD);

    public final int columns;

    OptionLayout(int columns) {
        this.columns = columns;
    }

    /**
     * Tab dừng (twips, tính từ lề trái chữ, như w:tab w:pos) giữa các cột: mốc đầu 283 của mẫu + k × W/cột (cột cuối kết
     * thúc đúng ở lề phải). Rỗng khi mỗi phương án một dòng (chỉ còn tab đầu dòng tới mốc 283).
     */
    public int[] stopsTwips() {
        if (columns == 1) {
            return new int[0];
        }
        double line = ExamPage.TEXT_WIDTH_TWIPS - ExamPage.OPTION_LABEL_START_TWIPS;
        int[] stops = new int[columns - 1];
        for (int k = 1; k < columns; k++) {
            stops[k - 1] = (int) Math.round(ExamPage.OPTION_LABEL_START_TWIPS + line * k / columns);
        }
        return stops;
    }

    /** Như {@link #choose(List, int)} ở cỡ chữ mặc định 12. */
    public static OptionLayout choose(List<String> options) {
        return choose(options, ExamPage.FONT_SIZE_PT);
    }

    /**
     * @param options    bốn phương án ở dạng nguồn (có $...$), đã chuẩn hóa dấu chấm cuối
     * @param fontSizePt (26/11) cỡ chữ của đề: chữ và công thức đo ở cỡ này ({@link #widthPt(char, String, int)}); mốc tab
     *                   và đệm không đổi (theo bề rộng trang)
     */
    public static OptionLayout choose(List<String> options, int fontSizePt) {
        double max = 0;
        for (int i = 0; i < options.size(); i++) {
            max = Math.max(max, widthPt((char) ('A' + i), options.get(i), fontSizePt));
        }
        double line = ExamPage.optionLineWidthPt();
        if (max <= line / 4 - PADDING_PT) {
            return FOUR_COLUMNS;
        }
        if (max <= line / 2 - PADDING_PT) {
            return TWO_COLUMNS;
        }
        return ONE_PER_LINE;
    }

    /** Như {@link #widthPt(char, String, int)} ở cỡ chữ mặc định 12. */
    public static double widthPt(char label, String option) {
        return widthPt(label, option, ExamPage.FONT_SIZE_PT);
    }

    /**
     * Bề rộng hiển thị (pt) của nhãn "A." đậm, một dấu cách rồi nội dung phương án (chữ và công thức $...$), ở cỡ chữ
     * {@code fontSizePt} (26/11; công thức đo bằng ảnh xem trước cùng cỡ, như MathType và Equation của file).
     */
    public static double widthPt(char label, String option, int fontSizePt) {
        Font plain = fontSizePt == ExamPage.FONT_SIZE_PT ? PLAIN : PLAIN.deriveFont((float) fontSizePt);
        Font bold = fontSizePt == ExamPage.FONT_SIZE_PT ? BOLD : BOLD.deriveFont((float) fontSizePt);
        double width = textWidth(label + ".", bold) + textWidth(" ", plain);
        for (ExamMarkdownBuilder.Piece piece : ExamMarkdownBuilder.pieces(option == null ? "" : option)) {
            if (piece.math()) {
                try {
                    width += PreviewRenderer.widthPt(piece.text(), fontSizePt);
                } catch (RuntimeException e) {
                    return Double.POSITIVE_INFINITY;
                }
            } else {
                width += textWidth(piece.text(), plain);
            }
        }
        return width;
    }

    private static double textWidth(String s, Font font) {
        return s.isEmpty() ? 0 : font.getStringBounds(s, FRC).getWidth();
    }

    private static Font font(int style) {
        String family = pickFamily();
        return new Font(family, style, ExamPage.FONT_SIZE_PT);
    }

    /** Times New Roman nếu có; trên Linux là Liberation Serif (cùng metric), Tinos; không có gì thì phông serif mặc định. */
    private static String pickFamily() {
        try {
            List<String> available = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames());
            for (String candidate : new String[]{ExamPage.FONT, "Liberation Serif", "Tinos"}) {
                if (available.contains(candidate)) {
                    return candidate;
                }
            }
        } catch (RuntimeException | Error e) {
            log.warn("Không liệt kê được phông của máy chủ: {}", e.toString());
        }
        log.warn("Máy chủ không có Times New Roman/Liberation Serif: đo phương án bằng phông serif mặc định");
        return Font.SERIF;
    }
}
