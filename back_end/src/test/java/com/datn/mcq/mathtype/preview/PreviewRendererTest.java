package com.datn.mcq.mathtype.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** Góp ý giáo viên 21/11: công thức MathType trong file Word cách chữ bên cạnh thừa khoảng một dấu cách. */
class PreviewRendererTest {

    private static final double PX_TO_PT = 72.0 / PreviewRenderer.DPI;
    /** Chừa tối đa mỗi bên (pt): lề cố ý cộng 1px sai số làm tròn. */
    private static final double MAX_SIDE_PT = PreviewRenderer.SIDE_MARGIN_PT + PX_TO_PT;

    @Test
    void anhCatSatNetChuHaiBen() throws IOException {
        for (String latex : new String[] {"AB", "IJKL", "O", "x", "\\frac{1}{2}", "2{,}5", "x^2+1"}) {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(PreviewRenderer.render(latex).png()));
            assertTrue(blankLeftPx(img) * PX_TO_PT <= MAX_SIDE_PT, latex + ": trống bên trái " + blankLeftPx(img) + "px");
            assertTrue(blankRightPx(img) * PX_TO_PT <= MAX_SIDE_PT, latex + ": trống bên phải " + blankRightPx(img) + "px");
            assertTrue(blankLeftPx(img) > 0 && blankRightPx(img) > 0, latex + ": vẫn chừa một chút mỗi bên");
        }
    }

    @Test
    void beRongDoTruocBangBeRongAnh() {
        for (String latex : new String[] {"AB", "\\frac{a+b}{c}", "x^2 - 18x + 20"}) {
            PreviewRenderer.Preview p = PreviewRenderer.render(latex);
            assertEquals(p.widthPt(), PreviewRenderer.widthPt(latex), 1e-9, latex);
        }
    }

    /** Trước 22/11 "AB" rộng 24,96pt (lề 1pt + khoảng trống ~2,5pt mỗi bên của JLaTeXMath); nay hẹp hơn ít nhất 5pt. */
    @Test
    void congThucNganHepHonTruoc() {
        assertTrue(PreviewRenderer.widthPt("AB") < 24.96 - 5, "AB rộng " + PreviewRenderer.widthPt("AB") + "pt");
    }

    @Test
    void chieuCaoVaDuongCoSoKhongDoi() throws IOException {
        PreviewRenderer.Preview p = PreviewRenderer.render("\\frac{1}{2}");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(p.png()));
        assertEquals(img.getHeight() * PX_TO_PT, p.heightPt(), 1e-9);
        assertTrue(p.depthPt() > 0 && p.depthPt() < p.heightPt(), "phân số có phần dưới đường cơ sở");
    }

    @Test
    void anhKhongCoNetGiuNguyen() {
        BufferedImage blank = new BufferedImage(40, 10, BufferedImage.TYPE_INT_ARGB);
        assertSame(blank, PreviewRenderer.cropSides(blank));
    }

    private static int blankLeftPx(BufferedImage img) {
        for (int x = 0; x < img.getWidth(); x++) {
            if (hasInk(img, x)) {
                return x;
            }
        }
        return img.getWidth();
    }

    private static int blankRightPx(BufferedImage img) {
        for (int x = img.getWidth() - 1; x >= 0; x--) {
            if (hasInk(img, x)) {
                return img.getWidth() - 1 - x;
            }
        }
        return img.getWidth();
    }

    private static boolean hasInk(BufferedImage img, int x) {
        for (int y = 0; y < img.getHeight(); y++) {
            if ((img.getRGB(x, y) >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }
}
