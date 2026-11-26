package com.datn.mcq.mathtype.preview;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

/**
 * Ảnh xem trước PNG cho đối tượng OLE (Word hiển thị/in ảnh này cho tới khi MathType vẽ lại). Dựng bằng JLaTeXMath
 * từ LaTeX gốc, cỡ 12pt (bằng cỡ Full trong EQN_PREFS của MTEF; 26/11: hoặc cỡ chữ của đề, 14pt) ở {@link #DPI} dpi, nền
 * trong suốt.
 *
 * <p>Bề ngang ảnh cắt sát nét chữ, chừa {@link #SIDE_MARGIN_PT} mỗi bên: hộp của JLaTeXMath tự để trống ~2,5-3pt mỗi
 * bên (đo 22/11 trên "AB", "IJKL", "x", "\frac{1}{2}"), cộng dấu cách thường trong câu thành khoảng hở cỡ hai dấu cách
 * trước và sau công thức; giáo viên góp ý 21/11 là không đúng thể thức văn bản. Chiều dọc giữ lề ~1pt như cũ.
 */
public final class PreviewRenderer {

    static final int DPI = 600;
    static final float FONT_PT = 12f;
    /** Khoảng chừa mỗi bên sau khi cắt sát nét chữ (pt), để nét nghiêng/dấu phẩy không chạm chữ bên cạnh. */
    static final double SIDE_MARGIN_PT = 0.5;

    /** Ảnh PNG và kích thước theo point; {@code depthPt} là phần nằm dưới đường cơ sở. */
    public record Preview(byte[] png, double widthPt, double heightPt, double depthPt) { }

    private PreviewRenderer() {
    }

    /**
     * Bề rộng (pt) của ảnh xem trước công thức này, đúng bằng {@link #render}{@code .widthPt()} (cùng phép dựng và cắt,
     * không mã hóa PNG). Dùng để quyết định ngắt dòng/thu nhỏ khi công thức rộng hơn lề chữ, và xếp cột phương án.
     *
     * @throws RuntimeException JLaTeXMath không dựng được LaTeX này
     */
    public static double widthPt(String latex) {
        return widthPt(latex, FONT_PT);
    }

    /** Như {@link #widthPt(String)} ở cỡ {@code fontPt} (26/11: đề cỡ 14). */
    public static double widthPt(String latex, float fontPt) {
        return draw(latex, fontPt).getWidth() * (72.0 / DPI);
    }

    /** @throws RuntimeException JLaTeXMath không dựng được LaTeX này (bên gọi giữ công thức dạng OMML) */
    public static Preview render(String latex) {
        return render(latex, FONT_PT);
    }

    /** Như {@link #render(String)} ở cỡ {@code fontPt} (26/11: đề cỡ 14; cỡ Full trong EQN_PREFS của MTEF cùng cỡ). */
    public static Preview render(String latex, float fontPt) {
        TeXIcon icon = icon(latex, fontPt);
        BufferedImage img = cropSides(paint(icon));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        double toPt = 72.0 / DPI;
        int depthPx = icon.getIconDepth(); // phần dưới đường cơ sở; JLaTeXMath đã cộng lề dưới (insets.bottom)
        return new Preview(out.toByteArray(), img.getWidth() * toPt, img.getHeight() * toPt, depthPx * toPt);
    }

    private static BufferedImage draw(String latex, float fontPt) {
        return cropSides(paint(icon(latex, fontPt)));
    }

    private static TeXIcon icon(String latex, float fontPt) {
        float px = fontPt * DPI / 72f;
        TeXFormula formula = new TeXFormula(latex);
        // STYLE_DISPLAY: phân số cỡ đầy đủ như MathType vẽ (STYLE_TEXT thu nhỏ \frac)
        TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, px);
        int pad = Math.round(DPI / 72f); // ~1pt trên/dưới; hai bên cắt sát nét chữ ở cropSides
        icon.setInsets(new Insets(pad, 0, pad, 0));
        icon.setForeground(Color.BLACK);
        return icon;
    }

    private static BufferedImage paint(TeXIcon icon) {
        BufferedImage img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return img;
    }

    /**
     * Cắt phần trong suốt hai bên, chừa {@link #SIDE_MARGIN_PT} mỗi bên (không vượt khung gốc). Giữ nguyên chiều cao nên
     * đường cơ sở và {@code depthPt} không đổi. Ảnh không có nét nào (công thức chỉ gồm khoảng trắng) giữ nguyên.
     */
    static BufferedImage cropSides(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int left = -1;
        int right = -1;
        for (int x = 0; x < w && left < 0; x++) {
            if (columnHasInk(img, x, h)) {
                left = x;
            }
        }
        if (left < 0) {
            return img;
        }
        for (int x = w - 1; x >= left && right < 0; x--) {
            if (columnHasInk(img, x, h)) {
                right = x;
            }
        }
        int margin = (int) Math.round(SIDE_MARGIN_PT * DPI / 72.0);
        int from = Math.max(0, left - margin);
        int to = Math.min(w - 1, right + margin);
        if (from == 0 && to == w - 1) {
            return img;
        }
        BufferedImage out = new BufferedImage(to - from + 1, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, -from, 0, null);
        g.dispose();
        return out;
    }

    private static boolean columnHasInk(BufferedImage img, int x, int h) {
        for (int y = 0; y < h; y++) {
            if ((img.getRGB(x, y) >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }
}
