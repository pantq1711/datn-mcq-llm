package spike.mathtype;

import java.io.ByteArrayOutputStream;
import java.util.HexFormat;

/**
 * Ghi luồng MTEF v5 (đặc tả MTEF5 của Design Science/Wiris). Bố cục record bám theo đúng những gì MathType 7.12 sinh
 * ra khi Toggle TeX cùng 10 biểu thức mẫu (xem out/ref_mathtype_textoggle.mtef.txt), bỏ các record COLOR.
 *
 * <p>Kích cỡ chữ (FULL/SUB/SUB2) là trạng thái "dính" trong luồng: chỉ ghi record typesize khi kích cỡ đổi.
 */
final class MtefWriter {

    static final int FULL = 0, SUB = 1, SUB2 = 2, SYM = 3;

    // Typeface (MTEF "styles")
    static final int FN_TEXT = 1, FN_FUNCTION = 2, FN_VARIABLE = 3, FN_LCGREEK = 4, FN_UCGREEK = 5, FN_SYMBOL = 6,
            FN_VECTOR = 7, FN_NUMBER = 8, FN_MTEXTRA = 11, FN_EXPAND = 22, FN_SPACE = 24;

    // Template selectors
    static final int TM_ANGLE = 0, TM_PAREN = 1, TM_BRACE = 2, TM_BRACK = 3, TM_BAR = 4, TM_DBAR = 5, TM_FLOOR = 6,
            TM_CEILING = 7, TM_ROOT = 10, TM_FRACT = 11, TM_UBAR = 12, TM_OBAR = 13, TM_INTEG = 15, TM_SUM = 16,
            TM_PROD = 17, TM_LIM = 23, TM_SUB = 27, TM_SUP = 28, TM_SUBSUP = 29, TM_VEC = 31, TM_TILDE = 32,
            TM_HAT = 33, TM_ARC = 34;

    // Embellishment (EMBELL record)
    static final int EMB_1DOT = 2, EMB_2DOT = 3, EMB_1PRIME = 5, EMB_2PRIME = 6, EMB_TILDE = 8, EMB_HAT = 9,
            EMB_RARROW = 11, EMB_OBAR = 17, EMB_3PRIME = 18;

    /**
     * Phần định nghĩa (ENCODING_DEF "WinAllBasicCodePages", FONT_DEF Times New Roman / Symbol / Courier New / MT Extra,
     * EQN_PREFS: cỡ chữ 12pt, khoảng cách, bảng style) chép nguyên từ đối tượng do MathType 7.12 sinh trên máy dev.
     */
    private static final byte[] PREAMBLE = HexFormat.of().parseHex(
            "1357696e416c6c4261736963436f6465506167657300110554696d6573204e657720526f6d616e00110353796d626f6c00"
            + "1105436f7572696572204e65770011044d5420457874726100120008212f27f25f218f212f475f4150f21f1e4150f4150f"
            + "4100f445f425f48f425f4100f4100f435f4100f21f20a5f20a25f48f21f4100f4100f40f48f417f48f4100f21a5f445f45"
            + "f45f45f45f410f0c0100010001020202020002000101010003000100040000");

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private int curSize = -1;

    /** Header MTEF: v5, Windows, MathType 6.9, key "DSMT6", eqn_options = 1 (inline). */
    MtefWriter(boolean inline) {
        out.writeBytes(new byte[]{5, 1, 0, 6, 9});
        out.writeBytes("DSMT6".getBytes());
        out.write(0);
        out.write(inline ? 1 : 0);
        out.writeBytes(PREAMBLE);
    }

    void size(int s) {
        if (s != curSize) {
            out.write(10 + s);
            curSize = s;
        }
    }

    void line() {
        out.write(1);
        out.write(0);
    }

    void nullLine() {
        out.write(1);
        out.write(1);
    }

    void end() {
        out.write(0);
    }

    /** CHAR record. fontPos &lt; 0: không ghi vị trí trong font (chỉ MTCode). */
    void ch(int typeface, int mtCode, int fontPos, boolean funcStart) {
        ch(typeface, mtCode, fontPos, funcStart, null);
    }

    /** CHAR record kèm danh sách EMBELL (dấu mũ, phẩy, chấm... gắn trên một ký tự), như MathType ghi. */
    void ch(int typeface, int mtCode, int fontPos, boolean funcStart, int[] embells) {
        boolean hasEmbell = embells != null && embells.length > 0;
        int opts = (fontPos >= 0 ? 0x04 : 0) | (funcStart ? 0x02 : 0) | (hasEmbell ? 0x01 : 0);
        out.write(2);
        out.write(opts);
        out.write(typeface + 128);
        out.write(mtCode & 0xFF);
        out.write((mtCode >> 8) & 0xFF);
        if (fontPos >= 0) {
            out.write(fontPos);
        }
        if (hasEmbell) {
            for (int type : embells) {
                out.write(6);
                out.write(0);
                out.write(type);
            }
            out.write(0);
        }
    }

    void tmpl(int selector, int variation, int tmplOpts) {
        out.write(3);
        out.write(0);
        out.write(selector);
        if (variation >= 0x80) {
            out.write((variation & 0x7F) | 0x80);
            out.write(variation >> 8);
        } else {
            out.write(variation);
        }
        out.write(tmplOpts);
    }

    /** MATRIX record: valign, h_just, v_just như MathType ghi cho cases (1, 0, 1); không kẻ đường phân cách. */
    void matrix(int rows, int cols, int hJust) {
        out.write(5);
        out.write(0);
        out.write(1);
        out.write(hJust);
        out.write(1);
        out.write(rows);
        out.write(cols);
        out.writeBytes(new byte[(2 * (rows + 1) + 7) / 8]);
        out.writeBytes(new byte[(2 * (cols + 1) + 7) / 8]);
    }

    void pile(int hAlign, int vAlign) {
        out.write(4);
        out.write(0);
        out.write(hAlign);
        out.write(vAlign);
    }

    byte[] toByteArray() {
        return out.toByteArray();
    }
}
