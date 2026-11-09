package com.datn.mcq.bankimport.docx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Văn bản của một hoặc nhiều đoạn kèm thuộc tính từng ký tự. Công thức MathType và ảnh là MỘT ký tự giữ chỗ
 * ({@link #EQ} hoặc {@link #IMG}); đối tượng tương ứng là {@code objs.get(objIdx[i])}.
 */
public final class RichText {

    public static final char EQ = '';
    public static final char IMG = '';

    /** Một đối tượng nhúng trong đoạn; floating = hình nổi (wp:anchor), hiển thị lệch khỏi đoạn neo. */
    public record Obj(boolean equation, String ref, String previewRef, String progId, String ext, Double widthCm, Double heightCm,
                      boolean floating) {

        public Obj(boolean equation, String ref, String previewRef, String progId, String ext, Double widthCm, Double heightCm) {
            this(equation, ref, previewRef, progId, ext, widthCm, heightCm, false);
        }
    }

    public final String s;
    public final boolean[] yellow;
    public final boolean[] cyan;
    public final byte[] script;       // 0 thường, 1 chữ mũ, 2 chỉ số dưới (vertAlign)
    public final int[] objIdx;        // chỉ số trong objs nếu ký tự là giữ chỗ, ngược lại -1
    public final List<Obj> objs;

    private RichText(String s, boolean[] yellow, boolean[] cyan, byte[] script, int[] objIdx, List<Obj> objs) {
        this.s = s;
        this.yellow = yellow;
        this.cyan = cyan;
        this.script = script;
        this.objIdx = objIdx;
        this.objs = objs;
    }

    public static final RichText EMPTY = new RichText("", new boolean[0], new boolean[0], new byte[0], new int[0], List.of());

    public int length() {
        return s.length();
    }

    /** Xây từng đoạn. */
    public static final class Builder {
        private final StringBuilder sb = new StringBuilder();
        private final List<Boolean> y = new ArrayList<>();
        private final List<Boolean> c = new ArrayList<>();
        private final List<Byte> sc = new ArrayList<>();
        private final List<Integer> oi = new ArrayList<>();
        private final List<Obj> objs = new ArrayList<>();

        public void text(String text, boolean yellow, boolean cyan, byte script) {
            for (int i = 0; i < text.length(); i++) {
                sb.append(text.charAt(i));
                y.add(yellow);
                c.add(cyan);
                sc.add(script);
                oi.add(-1);
            }
        }

        public void obj(Obj o, boolean yellow, boolean cyan) {
            sb.append(o.equation() ? EQ : IMG);
            y.add(yellow);
            c.add(cyan);
            sc.add((byte) 0);
            oi.add(objs.size());
            objs.add(o);
        }

        public RichText build() {
            int n = sb.length();
            boolean[] ya = new boolean[n];
            boolean[] ca = new boolean[n];
            byte[] sa = new byte[n];
            int[] oa = new int[n];
            for (int i = 0; i < n; i++) {
                ya[i] = y.get(i);
                ca[i] = c.get(i);
                sa[i] = sc.get(i);
                oa[i] = oi.get(i);
            }
            return new RichText(sb.toString(), ya, ca, sa, oa, List.copyOf(objs));
        }
    }

    /** Nối nhiều đoạn, cách nhau bằng ký tự xuống dòng. */
    public static RichText join(List<RichText> parts) {
        Builder b = new Builder();
        for (int k = 0; k < parts.size(); k++) {
            RichText p = parts.get(k);
            if (k > 0) {
                b.text("\n", false, false, (byte) 0);
            }
            for (int i = 0; i < p.length(); i++) {
                if (p.objIdx[i] >= 0) {
                    b.obj(p.objs.get(p.objIdx[i]), p.yellow[i], p.cyan[i]);
                } else {
                    b.text(String.valueOf(p.s.charAt(i)), p.yellow[i], p.cyan[i], p.script[i]);
                }
            }
        }
        return b.build();
    }

    public RichText slice(int from, int to) {
        from = Math.max(0, from);
        to = Math.min(length(), to);
        if (from >= to) {
            return EMPTY;
        }
        Builder b = new Builder();
        for (int i = from; i < to; i++) {
            if (objIdx[i] >= 0) {
                b.obj(objs.get(objIdx[i]), yellow[i], cyan[i]);
            } else {
                b.text(String.valueOf(s.charAt(i)), yellow[i], cyan[i], script[i]);
            }
        }
        return b.build();
    }

    /** Có ký tự không phải khoảng trắng nào được tô vàng trong [from, to) (giữ chỗ công thức/ảnh được tính là có). */
    public boolean hasYellow(int from, int to) {
        for (int i = Math.max(0, from); i < Math.min(length(), to); i++) {
            if (yellow[i] && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != ' ') {
                return true;
            }
        }
        return false;
    }

    /** Văn bản không có giữ chỗ (để so khớp mẫu chữ). */
    public String plain() {
        return s.replace(String.valueOf(EQ), "").replace(String.valueOf(IMG), "");
    }

    public boolean isBlank() {
        for (int i = 0; i < length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c) && c != ' ') {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return Arrays.toString(new Object[]{"RichText", length()});
    }
}
