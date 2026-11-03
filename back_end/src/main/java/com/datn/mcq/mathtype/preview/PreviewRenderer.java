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
 * từ LaTeX gốc, cỡ 12pt (bằng cỡ Full trong EQN_PREFS của MTEF) ở {@link #DPI} dpi, nền trong suốt.
 */
public final class PreviewRenderer {

    static final int DPI = 600;
    static final float FONT_PT = 12f;

    /** Ảnh PNG và kích thước theo point; {@code depthPt} là phần nằm dưới đường cơ sở. */
    public record Preview(byte[] png, double widthPt, double heightPt, double depthPt) { }

    private PreviewRenderer() {
    }

    /** @throws RuntimeException JLaTeXMath không dựng được LaTeX này (bên gọi giữ công thức dạng OMML) */
    public static Preview render(String latex) {
        float px = FONT_PT * DPI / 72f;
        TeXFormula formula = new TeXFormula(latex);
        // STYLE_DISPLAY: phân số cỡ đầy đủ như MathType vẽ (STYLE_TEXT thu nhỏ \frac)
        TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, px);
        int pad = Math.round(DPI / 72f); // ~1pt mỗi bên, như lề MathType để lại
        icon.setInsets(new Insets(pad, pad, pad, pad));
        icon.setForeground(Color.BLACK);
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        double toPt = 72.0 / DPI;
        int depthPx = icon.getIconDepth(); // phần dưới đường cơ sở; JLaTeXMath đã cộng lề dưới (insets.bottom)
        return new Preview(out.toByteArray(), w * toPt, h * toPt, depthPx * toPt);
    }
}
