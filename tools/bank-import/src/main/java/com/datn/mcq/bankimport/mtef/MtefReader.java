package com.datn.mcq.bankimport.mtef;

import com.datn.mcq.bankimport.mtef.MtefNode.Chr;
import com.datn.mcq.bankimport.mtef.MtefNode.Line;
import com.datn.mcq.bankimport.mtef.MtefNode.Matrix;
import com.datn.mcq.bankimport.mtef.MtefNode.Pile;
import com.datn.mcq.bankimport.mtef.MtefNode.Tmpl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc MTEF v5 (đặc tả MTEF5 của Design Science; khuôn theo spikes/mathtype-ole/tools/mtef_dump.py).
 *
 * <p>Khác {@code mtef_dump.py}: RULER nằm trong LINE/PILE (cờ mtefOPT_LP_RULER) KHÔNG có byte thẻ 7 đứng trước, đúng
 * như bộ đọc Ruby tham chiếu (RecordRuler chỉ có n_stops). Dữ liệu MathType 7.4 (khóa DSMT7) có loại này; bộ cũ báo
 * "expected RULER got 1" ở 14/891 đối tượng.
 */
final class MtefReader {

    /** Lỗi cấu trúc dữ liệu (bị cắt cụt, record lạ). */
    static final class MtefException extends RuntimeException {
        MtefException(String message) {
            super(message);
        }
    }

    private static final Object END = new Object();

    /** Lý do của công thức không có nội dung nào (đối tượng OLE trống). */
    static final String EMPTY = "cong_thuc_rong";

    private final byte[] d;
    private int p;
    private final List<String> fontDefs = new ArrayList<>();       // tên font theo thứ tự FONT_DEF (chỉ số từ 1)
    private final List<int[]> styleDefs = new ArrayList<>();       // FONT_STYLE_DEF: {chỉ số font, kiểu chữ}
    private final List<int[]> prefStyles = new ArrayList<>();      // EQN_PREFS: mục thứ i ↔ typeface i+1

    private MtefReader(byte[] data) {
        this.d = data;
    }

    /** Trả LINE (hoặc PILE) đầu tiên ở mức cao nhất, tức thân công thức. */
    static MtefNode parse(byte[] mtef) {
        MtefReader r = new MtefReader(mtef);
        int version = r.u8();
        if (version != 5) {
            throw new MtefException("mtef_version_" + version + "_khong_ho_tro");
        }
        r.u8();              // platform
        r.u8();              // product
        r.u8();              // product version
        r.u8();              // product subversion
        r.cstr();            // application key
        r.u8();              // equation options
        MtefNode body = null;
        while (!r.eof()) {
            Object rec = r.record();
            if (rec == END) {
                break;
            }
            if (body == null && (rec instanceof Line || rec instanceof Pile)) {
                body = (MtefNode) rec;
            }
        }
        if (body == null) {
            throw new MtefException(EMPTY);
        }
        return body;
    }

    // ---------- byte-level ----------

    private boolean eof() {
        return p >= d.length;
    }

    private int u8() {
        if (p >= d.length) {
            throw new MtefException("du_lieu_bi_cat_cut_o_byte_" + p);
        }
        return d[p++] & 0xFF;
    }

    private int u16() {
        int lo = u8();
        return lo | (u8() << 8);
    }

    private int uint() {
        int v = u8();
        return v == 255 ? u16() : v;
    }

    private String cstr() {
        int e = p;
        while (e < d.length && d[e] != 0) {
            e++;
        }
        if (e >= d.length) {
            throw new MtefException("chuoi_khong_ket_thuc_o_byte_" + p);
        }
        String s = new String(d, p, e - p, StandardCharsets.ISO_8859_1);
        p = e + 1;
        return s;
    }

    private void skip(int n) {
        if (n < 0 || p + n > d.length) {
            throw new MtefException("du_lieu_bi_cat_cut_o_byte_" + p);
        }
        p += n;
    }

    private void nudge(int opts) {
        if ((opts & 0x08) != 0) {
            int dx = u8();
            int dy = u8();
            if (dx == 128 && dy == 128) {
                skip(4);
            }
        }
    }

    /** Ruler trong LINE/PILE: n_stops rồi mỗi tab-stop gồm kiểu (1 byte) và vị trí (2 byte); không có byte thẻ. */
    private void rulerBody() {
        int n = u8();
        for (int i = 0; i < n; i++) {
            u8();
            u16();
        }
    }

    private int dimArray() {
        int count = u8();
        int done = 0;
        while (done < count) {
            int b = u8();
            for (int n : new int[]{b >> 4, b & 0xF}) {
                if (n == 0xF) {
                    done++;
                    if (done == count) {
                        break;
                    }
                }
            }
        }
        return count;
    }

    // ---------- records ----------

    private List<MtefNode> list() {
        List<MtefNode> out = new ArrayList<>();
        for (;;) {
            if (eof()) {
                throw new MtefException("danh_sach_khong_co_END_o_byte_" + p);
            }
            Object rec = record();
            if (rec == END) {
                return out;
            }
            if (rec instanceof MtefNode n) {
                out.add(n);
            }
        }
    }

    /** Trả {@link #END}, một nút, hoặc null cho record không ảnh hưởng nội dung (kích thước, màu, định nghĩa font). */
    private Object record() {
        int t = u8();
        switch (t) {
            case 0:
                return END;
            case 1: {
                int o = u8();
                nudge(o);
                if ((o & 0x04) != 0) {
                    u16();
                }
                if ((o & 0x02) != 0) {
                    rulerBody();
                }
                if ((o & 0x01) != 0) {
                    return new Line(List.of(), true);
                }
                return new Line(list(), false);
            }
            case 2: {
                int o = u8();
                nudge(o);
                int face = u8() - 128;
                int code = -1;
                if ((o & 0x20) == 0) {
                    code = u16();
                }
                int fp = -1;
                if ((o & 0x04) != 0) {
                    fp = u8();
                } else if ((o & 0x10) != 0) {
                    fp = u16();
                }
                if (code < 0) {
                    code = fp;     // không có mã MathType: dùng ký tự 8/16 bit nếu có
                }
                List<Integer> emb = new ArrayList<>();
                if ((o & 0x01) != 0) {
                    embellishments(emb);
                }
                if (code < 0) {
                    throw new MtefException("CHAR_khong_co_ma_ky_tu");
                }
                return resolveTypeface(face, code, emb);
            }
            case 3: {
                int o = u8();
                nudge(o);
                int sel = u8();
                int v = u8();
                if ((v & 0x80) != 0) {
                    v = (v & 0x7F) | (u8() << 8);
                }
                u8();      // template options
                return new Tmpl(sel, v, list());
            }
            case 4: {
                int o = u8();
                nudge(o);
                int halign = u8();
                u8();
                if ((o & 0x02) != 0) {
                    rulerBody();
                }
                return new Pile(halign, list());
            }
            case 5: {
                int o = u8();
                nudge(o);
                u8();
                u8();
                u8();
                int rows = u8();
                int cols = u8();
                skip(((rows + 1) * 2 + 7) / 8);
                skip(((cols + 1) * 2 + 7) / 8);
                return new Matrix(rows, cols, list());
            }
            case 6: {
                int o = u8();
                nudge(o);
                u8();
                return null;
            }
            case 7:
                rulerBody();      // RULER đứng riêng (có thẻ 7)
                return null;
            case 8: {
                int fontIndex = uint();
                styleDefs.add(new int[]{fontIndex, u8()});
                return null;
            }
            case 9: {
                int a = u8();
                if (a == 101) {
                    skip(2);
                } else if (a == 100) {
                    u8();
                    u16();
                } else {
                    u8();
                }
                return null;
            }
            case 10: case 11: case 12: case 13: case 14:
                return null;
            case 15:
                uint();
                return null;
            case 16: {
                int o = u8();
                for (int i = 0; i < ((o & 1) != 0 ? 4 : 3); i++) {
                    u16();
                }
                if ((o & 4) != 0) {
                    cstr();
                }
                return null;
            }
            case 17:
                uint();
                fontDefs.add(cstr());
                return null;
            case 18: {
                u8();
                dimArray();
                dimArray();
                int n = u8();
                for (int i = 0; i < n; i++) {
                    int font = uint();
                    prefStyles.add(new int[]{font, font != 0 ? u8() : 0});
                }
                return null;
            }
            case 19:
                cstr();
                return null;
            default:
                if (t >= 100) {
                    int n = uint();
                    skip(n);
                    return null;
                }
                throw new MtefException("record_la_" + t + "_o_byte_" + (p - 1));
        }
    }

    /** Danh sách EMBELL của một CHAR (có thể xen record COLOR...); ghi loại embellishment vào {@code types}. */
    private void embellishments(List<Integer> types) {
        for (;;) {
            if (eof()) {
                throw new MtefException("embellishment_khong_co_END");
            }
            int t = u8();
            if (t == 0) {
                return;
            }
            if (t == 6) {
                int o = u8();
                nudge(o);
                types.add(u8());
            } else {
                p--;
                record();        // COLOR, SIZE... xen giữa: đọc và bỏ qua
            }
        }
    }

    // ---------- typeface kiểu người dùng ----------

    /**
     * Typeface âm là kiểu người dùng (FONT_STYLE_DEF thứ -face); 9 và 10 là User 1/User 2 (mục tương ứng của EQN_PREFS).
     * Chỉ chấp nhận font chữ thường dùng, kiểu thường, ký tự ASCII → quy về TEXT; còn lại để nguyên và ghi lý do,
     * bộ dịch sẽ báo UNSUPPORTED (không đoán).
     */
    private Chr resolveTypeface(int face, int code, List<Integer> emb) {
        if (face >= 0 && face != 9 && face != 10) {
            return new Chr(face, code, emb, null);
        }
        int fontIdx = 0;
        int style = 0;
        if (face < 0) {
            int k = -face - 1;
            if (k < styleDefs.size()) {
                fontIdx = styleDefs.get(k)[0];
                style = styleDefs.get(k)[1];
            }
        } else if (face - 1 < prefStyles.size()) {
            fontIdx = prefStyles.get(face - 1)[0];
            style = prefStyles.get(face - 1)[1];
        }
        String font = fontIdx >= 1 && fontIdx <= fontDefs.size() ? fontDefs.get(fontIdx - 1) : null;
        String why;
        if (font == null) {
            why = "typeface_" + face;
        } else if (!isTextFont(font)) {
            why = "typeface_" + face + "_font_" + font.replaceAll("\\W+", "_");
        } else if (code > 0x7F) {
            why = "typeface_" + face + "_ky_tu_ngoai_ASCII";
        } else {
            // quy đổi: font đơn cách → 104; kiểu chữ (bit 0 đậm, bit 1 nghiêng) → 1 thường, 101 nghiêng, 102 đậm, 103 đậm nghiêng
            int mapped = font.toLowerCase(java.util.Locale.ROOT).contains("courier") ? 104
                    : switch (style) { case 0 -> 1; case 2 -> 101; case 1 -> 102; case 3 -> 103; default -> -1; };
            if (mapped > 0) {
                return new Chr(mapped, code, emb, null);
            }
            why = "typeface_" + face + "_kieu_chu_" + style;
        }
        return new Chr(face, code, emb, why);
    }

    private static boolean isTextFont(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return (n.contains("times") || n.contains("arial") || n.contains("calibri") || n.contains("tahoma")
                || n.contains("verdana") || n.contains("courier")) && !n.contains("symbol") && !n.contains("extra");
    }
}
