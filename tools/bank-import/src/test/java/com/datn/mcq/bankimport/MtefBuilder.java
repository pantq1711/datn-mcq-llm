package com.datn.mcq.bankimport;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Dựng MTEF v5 (và đối tượng OLE "Equation Native") cho test, để không phụ thuộc file thương mại. Chỉ ghi các record
 * mà bộ đọc cần; khuôn theo đặc tả MTEF5 và cây thực tế của MathType 6.9/7.x.
 */
public final class MtefBuilder {

    // typeface (số ghi trong CHAR là giá trị + 128)
    public static final int TEXT = 1, FUNC = 2, VAR = 3, LCGREEK = 4, UCGREEK = 5, SYM = 6, NUM = 8, MTEXTRA = 11,
            EXPAND = 22, SPACE = 24;
    // selector của template
    public static final int T_ANGLE = 0, T_PAREN = 1, T_BRACE = 2, T_BRACK = 3, T_BAR = 4, T_ROOT = 10, T_FRACT = 11,
            T_SUB = 27, T_SUP = 28, T_SUBSUP = 29, T_HAT = 33, T_SUM = 16, T_INTEG = 15;

    /** Một đối tượng con của danh sách (mảng byte đã mã hóa). */
    public record Obj(byte[] bytes) {}

    private static Obj obj(ByteArrayOutputStream b) {
        return new Obj(b.toByteArray());
    }

    private static void write(ByteArrayOutputStream out, Obj... items) {
        for (Obj o : items) {
            out.writeBytes(o.bytes());
        }
    }

    private static void u16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    /** LINE có danh sách đối tượng, kết thúc END. */
    public static Obj line(Obj... items) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(1);
        b.write(0);
        write(b, items);
        b.write(0);
        return obj(b);
    }

    /** LINE(null): ô trống (không có danh sách đối tượng, không có END). */
    public static Obj nullLine() {
        return new Obj(new byte[]{1, 1});
    }

    /**
     * LINE có RULER (mtefOPT_LP_RULER). Như dữ liệu MathType 7.4 thực tế: ruler nằm ngay sau byte options, KHÔNG có
     * byte thẻ 7 đứng trước; n_stops rồi mỗi tab-stop gồm kiểu (1 byte) và vị trí (2 byte).
     */
    public static Obj lineWithRuler(int[] tabStopOffsets, Obj... items) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(1);
        b.write(0x02);
        b.write(tabStopOffsets.length);
        for (int off : tabStopOffsets) {
            b.write(0);
            u16(b, off);
        }
        write(b, items);
        b.write(0);
        return obj(b);
    }

    /** CHAR với mã Unicode 16 bit (options = 0). */
    public static Obj ch(int face, int code) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(2);
        b.write(0);
        b.write(face + 128);
        u16(b, code);
        return obj(b);
    }

    /** CHAR kèm ký tự 8 bit (options 0x04), như phần lớn ký tự MathType thực tế. */
    public static Obj ch8(int face, int code) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(2);
        b.write(0x04);
        b.write(face + 128);
        u16(b, code);
        b.write(code & 0xFF);
        return obj(b);
    }

    public static Obj str(int face, String s) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        s.codePoints().forEach(cp -> write(b, ch(face, cp)));
        return obj(b);
    }

    public static Obj num(String digits) {
        return str(NUM, digits);
    }

    public static Obj var(String letters) {
        return str(VAR, letters);
    }

    public static Obj tmpl(int selector, int variation, Obj... children) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(3);
        b.write(0);
        b.write(selector);
        b.write(variation);
        b.write(0);
        write(b, children);
        b.write(0);
        return obj(b);
    }

    public static Obj full() {
        return new Obj(new byte[]{10});
    }

    public static Obj sub() {
        return new Obj(new byte[]{11});
    }

    /** CHAR kèm một embellishment (options 0x01 + danh sách EMBELL). */
    public static Obj charWithEmbell(int face, int code, int embellType) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(2);
        b.write(0x01);
        b.write(face + 128);
        u16(b, code);
        b.write(6);
        b.write(0);
        b.write(embellType);
        b.write(0);
        return obj(b);
    }

    public static Obj matrix(int rows, int cols, Obj... cells) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(5);
        b.write(0);
        b.write(1);
        b.write(0);
        b.write(1);
        b.write(rows);
        b.write(cols);
        b.write(new byte[((rows + 1) * 2 + 7) / 8], 0, ((rows + 1) * 2 + 7) / 8);
        b.write(new byte[((cols + 1) * 2 + 7) / 8], 0, ((cols + 1) * 2 + 7) / 8);
        write(b, cells);
        b.write(0);
        return obj(b);
    }

    /** x^2 */
    public static Obj sup(Obj content) {
        return tmpl(T_SUP, 0, sub(), nullLine(), line(content));
    }

    /** x_1 */
    public static Obj subscript(Obj content) {
        return tmpl(T_SUB, 0, sub(), line(content), nullLine());
    }

    public static Obj frac(Obj numerator, Obj denominator) {
        return tmpl(T_FRACT, 0, line(numerator), line(denominator));
    }

    /** Toàn bộ luồng MTEF: header (MathType 7.4, khóa DSMT7) + FULL + LINE + END. */
    public static byte[] mtef(Obj... lineContent) {
        return mtefOfLine(line(lineContent));
    }

    public static byte[] mtefOfLine(Obj topLine) {
        return mtefWithDefs(topLine);
    }

    /** Như {@link #mtefOfLine} nhưng có các record định nghĩa (FONT_DEF, EQN_PREFS...) đứng trước FULL. */
    public static byte[] mtefWithDefs(Obj topLine, Obj... defs) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(new byte[]{5, 1, 0, 7, 4}, 0, 5);
        b.writeBytes("DSMT7".getBytes(StandardCharsets.US_ASCII));
        b.write(0);
        b.write(0);
        write(b, defs);
        write(b, full(), topLine);
        b.write(0);
        return b.toByteArray();
    }

    /** Công thức rỗng: chỉ có FULL và END (gặp ở vài đối tượng OLE trống trong bộ thật). */
    public static byte[] mtefEmpty() {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(new byte[]{5, 1, 0, 7, 4}, 0, 5);
        b.writeBytes("DSMT7".getBytes(StandardCharsets.US_ASCII));
        b.write(0);
        b.write(0);
        b.write(10);
        b.write(0);
        return b.toByteArray();
    }

    /** FONT_DEF (record 17): chỉ số bảng mã + tên font. Chỉ số font tính từ 1 theo thứ tự xuất hiện. */
    public static Obj fontDef(String name) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(17);
        b.write(5);
        b.writeBytes(name.getBytes(StandardCharsets.ISO_8859_1));
        b.write(0);
        return obj(b);
    }

    /** FONT_STYLE_DEF (record 8): kiểu người dùng, tham chiếu bởi CHAR có typeface âm (-1 là kiểu đầu tiên). */
    public static Obj fontStyleDef(int fontIndex, int charStyle) {
        return new Obj(new byte[]{8, (byte) fontIndex, (byte) charStyle});
    }

    /** CHAR kèm embellishment cùng các record COLOR xen trong danh sách (như MathType 7.4 ghi). */
    public static Obj charWithEmbellAndColor(int face, int code, int embellType) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(2);
        b.write(0x01);
        b.write(face + 128);
        u16(b, code);
        b.write(15);
        b.write(0);
        b.write(6);
        b.write(0);
        b.write(embellType);
        b.write(15);
        b.write(1);
        b.write(0);
        return obj(b);
    }

    /** EQN_PREFS có 12 mục kiểu; mục của typeface 9 (User 1) dùng font {@code user1Font} (0 = không dùng). */
    public static Obj eqnPrefsWithUser1(int user1Font) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(18);
        b.write(0);
        b.write(0);        // mảng kích thước rỗng
        b.write(0);        // mảng khoảng cách rỗng
        b.write(12);
        for (int i = 1; i <= 12; i++) {
            if (i == 9 && user1Font != 0) {
                b.write(user1Font);
                b.write(0);
            } else {
                b.write(0);
            }
        }
        return obj(b);
    }

    /** PILE: các dòng xếp chồng; halign 1 = trái, 2 = giữa, 3 = phải. */
    public static Obj pile(int halign, Obj... lines) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(4);
        b.write(0);
        b.write(halign);
        b.write(1);
        write(b, lines);
        b.write(0);
        return obj(b);
    }

    /** Dòng cao nhất là PILE (biểu thức nhiều dòng). */
    public static byte[] mtefOfPile(Obj pile) {
        return mtefOfLine(pile);
    }

    /** Bọc MTEF trong compound file có luồng "Equation Native" (header 28 byte) như đối tượng Equation.DSMT4. */
    public static byte[] ole(byte[] mtef) {
        ByteBuffer hdr = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
        hdr.putShort((short) 28).putInt(0x00020000).putShort((short) 0xC1BE).putInt(mtef.length);
        byte[] stream = new byte[28 + mtef.length];
        System.arraycopy(hdr.array(), 0, stream, 0, 28);
        System.arraycopy(mtef, 0, stream, 28, mtef.length);
        try (POIFSFileSystem fs = new POIFSFileSystem(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fs.createDocument(new ByteArrayInputStream(stream), "Equation Native");
            fs.writeFilesystem(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Compound file không có "Equation Native" (ví dụ đối tượng vẽ FXDraw/PBrush). */
    public static byte[] oleWithoutEquation() {
        try (POIFSFileSystem fs = new POIFSFileSystem(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fs.createDocument(new ByteArrayInputStream(new byte[]{1, 2, 3}), "Ole10Native");
            fs.writeFilesystem(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private MtefBuilder() {}
}
