package spike.mathtype;

import static spike.mathtype.MtefWriter.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * OMML (m:oMath do pandoc sinh) -> MTEF v5, cho tập con Toán THCS. Cách mã hóa từng cấu trúc/ký tự chép theo đối
 * tượng do chính MathType 7.12 sinh (Toggle TeX) cho cùng biểu thức — xem samples/*.tex.txt và tools/compare_mtef.py.
 * Gặp cấu trúc chưa hỗ trợ thì ném {@link Unsupported}: bên gọi giữ nguyên công thức đó dạng Equation (OMML).
 */
final class OmmlToMtef {

    static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";

    static final class Unsupported extends RuntimeException {
        Unsupported(String what) {
            super(what);
        }
    }

    private final MtefWriter w;

    private OmmlToMtef(boolean inline) {
        this.w = new MtefWriter(inline);
    }

    static byte[] convert(Element oMath, boolean inline) {
        OmmlToMtef c = new OmmlToMtef(inline);
        c.w.size(FULL);
        c.w.line();
        c.children(oMath, FULL);
        c.w.end(); // hết LINE chính
        c.w.end(); // hết luồng
        return c.w.toByteArray();
    }

    // ---------------------------------------------------------------- duyệt cây

    private void children(Element container, int size) {
        for (Element e : elems(container)) {
            item(e, size);
        }
    }

    private void item(Element e, int size) {
        if (!M.equals(e.getNamespaceURI())) {
            String n = e.getLocalName();
            if (n.equals("bookmarkStart") || n.equals("bookmarkEnd") || n.equals("proofErr")) {
                return;
            }
            throw new Unsupported("w:" + n);
        }
        switch (e.getLocalName()) {
            case "r" -> run(e, size);
            case "f" -> fraction(e, size);
            case "rad" -> radical(e, size);
            case "sSub" -> script(e, size, TM_SUB);
            case "sSup" -> script(e, size, TM_SUP);
            case "sSubSup" -> script(e, size, TM_SUBSUP);
            case "d" -> delimiter(e, size);
            case "acc" -> accent(e, size);
            case "bar" -> bar(e, size);
            case "m" -> matrix(e, size);
            case "func" -> func(e, size);
            case "nary" -> nary(e, size);
            case "limLow" -> limit(e, size, true);
            case "limUpp" -> limit(e, size, false);
            case "box" -> children(child(e, "e"), size);
            case "ctrlPr", "rPr", "oMathParaPr" -> { }
            default -> throw new Unsupported("m:" + e.getLocalName());
        }
    }

    /** Một ô (slot) của template: LINE có nội dung, hoặc LINE null nếu trống. */
    private void slot(Element arg, int size) {
        w.size(size);
        if (isEmpty(arg)) {
            w.nullLine();
            return;
        }
        w.line();
        children(arg, size);
        w.end();
    }

    private static boolean isEmpty(Element arg) {
        return arg == null || elems(arg).stream().allMatch(x -> x.getLocalName().equals("ctrlPr"));
    }

    private static int scriptSize(int size) {
        return size == FULL ? SUB : SUB2;
    }

    // ---------------------------------------------------------------- cấu trúc

    private void fraction(Element f, int size) {
        String type = val(child(child(f, "fPr"), "type"));
        if ("noBar".equals(type)) { // \binom: MathType dùng PILE canh giữa 2 dòng
            pile(f, size);
            return;
        }
        int variation = switch (type == null ? "bar" : type) {
            case "bar" -> 0;
            case "skw", "lin" -> 0x0002; // tvFR_SLASH
            default -> throw new Unsupported("m:f type=" + type);
        };
        w.size(size);
        w.tmpl(TM_FRACT, variation, 0);
        slot(child(f, "num"), size);
        slot(child(f, "den"), size);
        w.end();
    }

    private void pile(Element noBarFraction, int size) {
        w.size(size);
        w.pile(2, 1);
        slot(child(noBarFraction, "num"), size);
        slot(child(noBarFraction, "den"), size);
        w.end();
    }

    private void radical(Element rad, int size) {
        Element deg = child(rad, "deg");
        boolean hide = isOn(child(child(rad, "radPr"), "degHide"));
        boolean square = hide || isEmpty(deg);
        w.size(size);
        w.tmpl(TM_ROOT, square ? 0 : 1, 0);
        slot(child(rad, "e"), size);                 // radicand
        slot(square ? null : deg, scriptSize(size)); // chỉ số căn
        w.end();
    }

    private static final Set<String> LIMIT_FUNCS = Set.of("lim", "max", "min", "sup", "inf");

    private void script(Element s, int size, int selector) {
        Element base = child(s, "e");
        Element sub = child(s, "sub");
        Element sup = child(s, "sup");
        int ss = scriptSize(size);

        // f' f'': MathType gắn dấu phẩy làm embellishment của ký tự, không dùng chỉ số trên
        if (selector == TM_SUP) {
            int prime = switch (text(sup)) {
                case "′" -> EMB_1PRIME;
                case "″", "′′" -> EMB_2PRIME;
                case "‴", "′′′" -> EMB_3PRIME;
                default -> 0;
            };
            Element single = singleCharRun(base);
            if (prime != 0 && single != null) {
                run(single, size, new int[]{prime});
                return;
            }
        }
        // \lim_{x\to 0}: pandoc ra sSub với "lim" đứng thẳng; MathType dùng template tmLIM (giới hạn dưới)
        if (selector == TM_SUB && elems(base).size() == 1 && LIMIT_FUNCS.contains(text(base))) {
            w.size(size);
            w.tmpl(TM_LIM, 0x10, 0);
            slot(base, size);
            slot(sub, ss);
            slot(null, ss);
            w.end();
            return;
        }

        children(base, size); // cơ sở đứng trước template (MTEF ScrBoxClass)
        w.size(size);
        w.tmpl(selector, 0, 0);
        if (selector == TM_SUB) {
            slot(sub, ss);
            slot(null, ss);
        } else if (selector == TM_SUP) {
            slot(null, ss);
            if ("∘".equals(text(sup))) { // ^\circ: MathType đổi thành dấu độ
                w.size(ss);
                w.line();
                w.ch(FN_SYMBOL, 0x00B0, 0xB0, false);
                w.end();
            } else {
                slot(sup, ss);
            }
        } else {
            slot(sub, ss);
            slot(sup, ss);
        }
        w.end();
    }

    private record Fence(int selector, int code) { }

    /** Ký tự rào giãn được (fnEXPAND) theo mã MathType dùng (|, ‖, ⌊⌋, ⌈⌉ là mã riêng của MathType). */
    private static final Map<String, Fence> OPEN = Map.of(
            "(", new Fence(TM_PAREN, 0x28), "[", new Fence(TM_BRACK, 0x5B), "{", new Fence(TM_BRACE, 0x7B),
            "|", new Fence(TM_BAR, 0xEC07), "‖", new Fence(TM_DBAR, 0xEC09),
            "⟨", new Fence(TM_ANGLE, 0x2329), "〈", new Fence(TM_ANGLE, 0x2329),
            "⌊", new Fence(TM_FLOOR, 0xF8F0), "⌈", new Fence(TM_CEILING, 0xF8EE));
    private static final Map<String, Fence> CLOSE = Map.of(
            ")", new Fence(TM_PAREN, 0x29), "]", new Fence(TM_BRACK, 0x5D), "}", new Fence(TM_BRACE, 0x7D),
            "|", new Fence(TM_BAR, 0xEC08), "‖", new Fence(TM_DBAR, 0xEC0A),
            "⟩", new Fence(TM_ANGLE, 0x232A), "〉", new Fence(TM_ANGLE, 0x232A),
            "⌋", new Fence(TM_FLOOR, 0xF8FB), "⌉", new Fence(TM_CEILING, 0xF8F9));

    private void delimiter(Element d, int size) {
        Element pr = child(d, "dPr");
        String beg = attrOr(child(pr, "begChr"), "(");
        String end = attrOr(child(pr, "endChr"), ")");
        String sep = attrOr(child(pr, "sepChr"), "|");
        List<Element> args = elems(d).stream().filter(x -> x.getLocalName().equals("e")).toList();
        Fence fb = beg.isEmpty() ? null : OPEN.get(beg);
        Fence fe = end.isEmpty() ? null : CLOSE.get(end);
        if ((!beg.isEmpty() && fb == null) || (!end.isEmpty() && fe == null)) {
            throw new Unsupported("m:d fence '" + beg + "' '" + end + "'");
        }
        if (fb != null && fe != null && fb.selector() != fe.selector()) {
            throw new Unsupported("m:d unmatched fences '" + beg + "' '" + end + "' (tmINTERVAL)");
        }
        if (fb == null && fe == null) {
            joined(args, sep, size);
            return;
        }
        int selector = fb != null ? fb.selector() : fe.selector();
        int variation = (fb != null ? 1 : 0) | (fe != null ? 2 : 0);
        w.size(size);
        w.tmpl(selector, variation, 0);
        Element only = args.size() == 1 && elems(args.get(0)).size() == 1 ? elems(args.get(0)).get(0) : null;
        if (only != null && only.getLocalName().equals("f")
                && "noBar".equals(val(child(child(only, "fPr"), "type")))) {
            pile(only, size); // \binom: PILE là ô chính của template, như MathType
        } else {
            w.size(size);
            w.line();
            joined(args, sep, size);
            w.end();
        }
        w.size(size);
        if (fb != null) {
            w.ch(FN_EXPAND, fb.code(), -1, false);
        }
        if (fe != null) {
            w.ch(FN_EXPAND, fe.code(), -1, false);
        }
        w.end();
    }

    private void joined(List<Element> args, String sep, int size) {
        for (int i = 0; i < args.size(); i++) {
            if (i > 0 && !sep.isEmpty()) {
                sep.codePoints().forEach(cp -> otherChar(cp, size, null));
            }
            children(args.get(i), size);
        }
    }

    private void accent(Element acc, int size) {
        String chr = attrOr(child(child(acc, "accPr"), "chr"), "̂");
        Element e = child(acc, "e");
        // một ký tự: MathType gắn embellishment lên ký tự (\hat{A}, \vec{a}, \bar{x}, \dot{x})
        int embell = switch (chr) {
            case "̂", "^" -> EMB_HAT;
            case "̃", "~" -> EMB_TILDE;
            case "⃗", "→" -> EMB_RARROW;
            case "̄", "̅", "¯", "‾" -> EMB_OBAR;
            case "̇" -> EMB_1DOT;
            case "̈" -> EMB_2DOT;
            default -> 0;
        };
        Element single = singleCharRun(e);
        if (embell != 0 && single != null) {
            run(single, size, new int[]{embell});
            return;
        }
        int selector;
        int variation = 0;
        int code;
        switch (chr) {
            case "̂", "^" -> { selector = TM_HAT; code = 0x0302; }
            case "̃", "~" -> { selector = TM_TILDE; code = 0x0303; }
            case "⃗", "→" -> { selector = TM_VEC; variation = 0x0002; code = 0x20D7; }
            case "̑", "⌢", "⏜", "⌒" -> { selector = TM_ARC; code = 0x2322; }
            case "̄", "̅", "¯", "‾" -> { selector = TM_OBAR; code = -1; }
            default -> throw new Unsupported(String.format("m:acc chr U+%04X trên nhiều ký tự", (int) chr.charAt(0)));
        }
        w.size(size);
        w.tmpl(selector, variation, 0);
        slot(e, size);
        if (code >= 0) {
            w.size(size);
            w.ch(FN_EXPAND, code, -1, false);
        }
        w.end();
    }

    private void bar(Element bar, int size) {
        String pos = val(child(child(bar, "barPr"), "pos"));
        w.size(size);
        w.tmpl("top".equals(pos) ? TM_OBAR : TM_UBAR, 0, 0);
        slot(child(bar, "e"), size);
        w.end();
    }

    private void matrix(Element m, int size) {
        List<List<Element>> rows = new ArrayList<>();
        for (Element mr : elems(m)) {
            if (mr.getLocalName().equals("mr")) {
                rows.add(elems(mr).stream().filter(x -> x.getLocalName().equals("e")).toList());
            }
        }
        int cols = rows.stream().mapToInt(List::size).max().orElse(0);
        if (rows.isEmpty() || cols == 0) {
            throw new Unsupported("m:m empty");
        }
        String jc = null;
        Element mcs = child(child(m, "mPr"), "mcs");
        if (mcs != null) {
            jc = val(child(child(child(mcs, "mc"), "mcPr"), "mcJc"));
        }
        // left = 0 như MathType ghi cho cases/array{l}; center/right theo bảng halign của đặc tả (suy luận)
        int hJust = jc == null || jc.equals("center") ? 2 : jc.equals("left") ? 0 : 3;
        w.size(size);
        w.matrix(rows.size(), cols, hJust);
        for (List<Element> row : rows) {
            for (int c = 0; c < cols; c++) {
                slot(c < row.size() ? row.get(c) : null, size);
            }
        }
        w.end();
    }

    private void func(Element f, int size) {
        Element name = child(f, "fName");
        for (Element r : elems(name)) {
            if (r.getLocalName().equals("r")) {
                String t = text(r);
                for (int i = 0; i < t.length(); i++) {
                    w.size(size);
                    w.ch(FN_FUNCTION, t.charAt(i), -1, i == 0);
                }
            } else {
                item(r, size);
            }
        }
        children(child(f, "e"), size);
    }

    /** Tích phân/tổng/tích. Thứ tự ô theo MathType: thân, (SUB) cận dưới, cận trên, (SYM) ký hiệu. */
    private void nary(Element n, int size) {
        Element pr = child(n, "naryPr");
        String chr = attrOr(child(pr, "chr"), "∫");
        int selector;
        int variation;
        int fontPos;
        switch (chr) {
            case "∫" -> { selector = TM_INTEG; variation = 0x01; fontPos = 0xF2; }
            case "∑" -> { selector = TM_SUM; variation = 0x40; fontPos = 0xE5; }
            case "∏" -> { selector = TM_PROD; variation = 0x40; fontPos = 0xD5; }
            default -> throw new Unsupported(String.format("m:nary chr U+%04X", (int) chr.charAt(0)));
        }
        Element sub = isOn(child(pr, "subHide")) ? null : child(n, "sub");
        Element sup = isOn(child(pr, "supHide")) ? null : child(n, "sup");
        boolean hasSub = !isEmpty(sub);
        boolean hasSup = !isEmpty(sup);
        variation |= (hasSub ? 0x10 : 0) | (hasSup ? 0x20 : 0);
        int ss = scriptSize(size);
        w.size(size);
        w.tmpl(selector, variation, 0);
        slot(child(n, "e"), size);
        slot(hasSub ? sub : null, ss);
        slot(hasSup ? sup : null, ss);
        w.size(size == FULL ? SYM : ss);
        w.ch(FN_SYMBOL, chr.charAt(0), fontPos, false);
        w.end();
    }

    /** m:limLow / m:limUpp -> tmLIM (vd. \overset\frown{AB}: cung AB). */
    private void limit(Element l, int size, boolean lower) {
        Element lim = child(l, "lim");
        int ss = scriptSize(size);
        w.size(size);
        w.tmpl(TM_LIM, lower ? 0x10 : 0x20, 0);
        slot(child(l, "e"), size);
        slot(lower ? lim : null, ss);
        slot(lower ? null : lim, ss);
        w.end();
    }

    // ---------------------------------------------------------------- ký tự

    /** m:e chỉ chứa đúng một m:r có đúng một ký tự (để gắn embellishment). */
    private static Element singleCharRun(Element e) {
        List<Element> items = elems(e).stream().filter(x -> !x.getLocalName().equals("ctrlPr")).toList();
        if (items.size() != 1 || !items.get(0).getLocalName().equals("r")) {
            return null;
        }
        String t = text(items.get(0));
        return t.codePointCount(0, t.length()) == 1 ? items.get(0) : null;
    }

    private void run(Element r, int size) {
        run(r, size, null);
    }

    private void run(Element r, int size, int[] embells) {
        Element rPr = child(r, "rPr");
        String sty = val(child(rPr, "sty"));
        String scr = val(child(rPr, "scr"));
        boolean normalText = child(rPr, "nor") != null;
        String t = text(r);
        boolean prevFunc = false;
        for (int i = 0; i < t.length(); ) {
            int cp = t.codePointAt(i);
            i += Character.charCount(cp);
            int[] emb = i >= t.length() ? embells : null; // embellishment gắn vào ký tự cuối
            if (normalText) { // \text{...}: mọi ký tự, kể cả dấu cách, ở style Text
                w.size(size);
                w.ch(FN_TEXT, bmp(cp), -1, false, emb);
                continue;
            }
            int space = switch (cp) { // khoảng trắng tường minh -> ký tự khoảng của MathType
                case 0x2009, 0x2006, 0x202F, 0x200A -> 0xEF07; // \,
                case 0x2004, 0x2005, 0x00A0, 0x2002 -> 0xEF04; // \; \:  "\ "
                case 0x2001, 0x2003 -> 0xEF05;                 // \quad
                default -> -1;
            };
            if (space >= 0) {
                w.size(size);
                w.ch(FN_SPACE, space, -1, false);
                prevFunc = false;
                continue;
            }
            if (Character.isWhitespace(cp) || cp == 0x2061 || cp == 0x2062 || cp == 0x200B) {
                continue;
            }
            boolean letter = (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z');
            if (letter && "double-struck".equals(scr)) {
                Integer ds = DOUBLE_STRUCK.get(cp);
                if (ds == null) {
                    throw new Unsupported("\\mathbb{" + (char) cp + "}");
                }
                otherChar(ds, size, emb);
                prevFunc = false;
                continue;
            }
            if (scr != null && !"roman".equals(scr) && letter) {
                throw new Unsupported("m:scr=" + scr);
            }
            w.size(size);
            if (cp >= '0' && cp <= '9') {
                w.ch(FN_NUMBER, cp, -1, false, emb);
                prevFunc = false;
            } else if (letter) {
                int face = "b".equals(sty) || "bi".equals(sty) ? FN_VECTOR
                        : "p".equals(sty) ? (t.length() > 1 ? FN_FUNCTION : FN_TEXT)
                        : FN_VARIABLE;
                w.ch(face, cp, -1, face == FN_FUNCTION && !prevFunc, emb);
                prevFunc = face == FN_FUNCTION;
            } else if (GREEK_LC.containsKey(cp)) {
                w.ch(FN_LCGREEK, cp, GREEK_LC.get(cp), false, emb);
                prevFunc = false;
            } else if (GREEK_UC.containsKey(cp) || cp == 0x2206) {
                int g = cp == 0x2206 ? 0x0394 : cp;
                w.ch(FN_UCGREEK, g, GREEK_UC.get(g), false, emb);
                prevFunc = false;
            } else if (Character.isLetter(cp)) {
                w.ch(FN_TEXT, bmp(cp), -1, false, emb);
                prevFunc = false;
            } else {
                otherChar(cp, size, emb);
                prevFunc = false;
            }
        }
    }

    /** Dấu câu ASCII: MathType ghi ở style Function (Times New Roman đứng), không kèm vị trí font. */
    private static final Set<Integer> FUNC_PUNCT = Set.of((int) '(', (int) ')', (int) '[', (int) ']', (int) '{',
            (int) '}', (int) '|', (int) ',', (int) ';', (int) ':', (int) '.', (int) '/', (int) '%', (int) '*',
            (int) '!', (int) '~', (int) '\'', (int) '?');

    private void otherChar(int cp, int size, int[] emb) {
        cp = switch (cp) {
            case '-' -> 0x2212;
            case 0x27C2 -> 0x22A5; // \perp: pandoc ⟂ -> MathType ⊥
            case 0x2300 -> 0x2205; // \varnothing: pandoc ⌀ -> MathType ∅
            case 0x223C -> '~';    // \sim: MathType ghi '~' style Function
            default -> cp;
        };
        w.size(size);
        if (FUNC_PUNCT.contains(cp)) {
            w.ch(FN_FUNCTION, cp, -1, false, emb);
        } else if (MTEXTRA_POS.containsKey(cp)) {
            w.ch(FN_MTEXTRA, cp, MTEXTRA_POS.get(cp), false, emb);
        } else if (SYMBOL_POS.containsKey(cp)) {
            w.ch(FN_SYMBOL, cp, SYMBOL_POS.get(cp), false, emb);
        } else {
            // Ký hiệu chưa có mã hóa đã kiểm chứng (vd. ⌊ ⌋: MathType đọc ra rỗng) -> giữ cả công thức dạng OMML
            throw new Unsupported(String.format("ký hiệu chưa kiểm chứng U+%04X", cp));
        }
    }

    private static int bmp(int cp) {
        if (cp > 0xFFFF) {
            throw new Unsupported(String.format("ký tự ngoài BMP U+%X", cp));
        }
        return cp;
    }

    private static final Map<Integer, Integer> DOUBLE_STRUCK = Map.of(
            (int) 'N', 0x2115, (int) 'Z', 0x2124, (int) 'Q', 0x211A, (int) 'R', 0x211D);

    /** Ký tự lấy từ font MT Extra (fnMTEXTRA) và vị trí, theo đối tượng MathType 7.12 sinh. */
    private static final Map<Integer, Integer> MTEXTRA_POS = Map.ofEntries(
            Map.entry(0x2213, 0x6D), Map.entry(0x22EF, 0x4C), Map.entry(0x22EE, 0x4D), Map.entry(0x25B3, 0x56),
            Map.entry(0x2225, 0x50), Map.entry(0x2115, 0xA5), Map.entry(0x2124, 0xA2), Map.entry(0x211A, 0xA4),
            Map.entry(0x211D, 0xA1), Map.entry(0x2322, 0x29));

    /** Vị trí ký tự trong font Symbol (bảng mã Adobe Symbol), MathType ghi kèm MTCode cho style Symbol. */
    private static final Map<Integer, Integer> SYMBOL_POS = Map.ofEntries(
            Map.entry(0x23, 0x23), Map.entry(0x26, 0x26), Map.entry(0x2B, 0x2B), Map.entry(0x3C, 0x3C),
            Map.entry(0x3D, 0x3D), Map.entry(0x3E, 0x3E), Map.entry(0x2212, 0x2D), Map.entry(0x2200, 0x22),
            Map.entry(0x2203, 0x24), Map.entry(0x2217, 0x2A), Map.entry(0x2245, 0x40), Map.entry(0x22A5, 0x5E),
            Map.entry(0x2032, 0xA2), Map.entry(0x2264, 0xA3), Map.entry(0x221E, 0xA5), Map.entry(0x2194, 0xAB),
            Map.entry(0x2190, 0xAC), Map.entry(0x2191, 0xAD), Map.entry(0x2192, 0xAE), Map.entry(0x2193, 0xAF),
            Map.entry(0x00B0, 0xB0), Map.entry(0x00B1, 0xB1), Map.entry(0x2033, 0xB2), Map.entry(0x2265, 0xB3),
            Map.entry(0x00D7, 0xB4), Map.entry(0x221D, 0xB5), Map.entry(0x2202, 0xB6), Map.entry(0x2022, 0xB7),
            Map.entry(0x00F7, 0xB8), Map.entry(0x2260, 0xB9), Map.entry(0x2261, 0xBA), Map.entry(0x2248, 0xBB),
            Map.entry(0x2026, 0xBC), Map.entry(0x2205, 0xC6), Map.entry(0x2229, 0xC7), Map.entry(0x222A, 0xC8),
            Map.entry(0x2283, 0xC9), Map.entry(0x2287, 0xCA), Map.entry(0x2284, 0xCB), Map.entry(0x2282, 0xCC),
            Map.entry(0x2286, 0xCD), Map.entry(0x2208, 0xCE), Map.entry(0x2209, 0xCF), Map.entry(0x2220, 0xD0),
            Map.entry(0x2207, 0xD1), Map.entry(0x221A, 0xD6), Map.entry(0x22C5, 0xD7), Map.entry(0x00AC, 0xD8),
            Map.entry(0x2227, 0xD9), Map.entry(0x2228, 0xDA), Map.entry(0x21D4, 0xDB), Map.entry(0x21D0, 0xDC),
            Map.entry(0x21D2, 0xDE), Map.entry(0x2211, 0xE5), Map.entry(0x222B, 0xF2), Map.entry(0x220F, 0xD5));

    private static final Map<Integer, Integer> GREEK_LC = Map.ofEntries(
            Map.entry(0x3B1, 0x61), Map.entry(0x3B2, 0x62), Map.entry(0x3C7, 0x63), Map.entry(0x3B4, 0x64),
            Map.entry(0x3B5, 0x65), Map.entry(0x3C6, 0x6A), Map.entry(0x3B3, 0x67), Map.entry(0x3B7, 0x68),
            Map.entry(0x3B9, 0x69), Map.entry(0x3D5, 0x66), Map.entry(0x3BA, 0x6B), Map.entry(0x3BB, 0x6C),
            Map.entry(0x3BC, 0x6D), Map.entry(0x3BD, 0x6E), Map.entry(0x3BF, 0x6F), Map.entry(0x3C0, 0x70),
            Map.entry(0x3B8, 0x71), Map.entry(0x3C1, 0x72), Map.entry(0x3C3, 0x73), Map.entry(0x3C4, 0x74),
            Map.entry(0x3C5, 0x75), Map.entry(0x3D6, 0x76), Map.entry(0x3C9, 0x77), Map.entry(0x3BE, 0x78),
            Map.entry(0x3C8, 0x79), Map.entry(0x3B6, 0x7A), Map.entry(0x3C2, 0x56), Map.entry(0x3D1, 0x4A),
            Map.entry(0x3F5, 0x65));

    private static final Map<Integer, Integer> GREEK_UC = Map.ofEntries(
            Map.entry(0x391, 0x41), Map.entry(0x392, 0x42), Map.entry(0x3A7, 0x43), Map.entry(0x394, 0x44),
            Map.entry(0x395, 0x45), Map.entry(0x3A6, 0x46), Map.entry(0x393, 0x47), Map.entry(0x397, 0x48),
            Map.entry(0x399, 0x49), Map.entry(0x39A, 0x4B), Map.entry(0x39B, 0x4C), Map.entry(0x39C, 0x4D),
            Map.entry(0x39D, 0x4E), Map.entry(0x39F, 0x4F), Map.entry(0x3A0, 0x50), Map.entry(0x398, 0x51),
            Map.entry(0x3A1, 0x52), Map.entry(0x3A3, 0x53), Map.entry(0x3A4, 0x54), Map.entry(0x3A5, 0x55),
            Map.entry(0x3A9, 0x57), Map.entry(0x39E, 0x58), Map.entry(0x3A8, 0x59), Map.entry(0x396, 0x5A));

    // ---------------------------------------------------------------- DOM

    static List<Element> elems(Element parent) {
        List<Element> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                out.add(e);
            }
        }
        return out;
    }

    static Element child(Element parent, String localName) {
        for (Element e : elems(parent)) {
            if (M.equals(e.getNamespaceURI()) && e.getLocalName().equals(localName)) {
                return e;
            }
        }
        return null;
    }

    private static String val(Element e) {
        if (e == null) {
            return null;
        }
        String v = e.getAttributeNS(M, "val");
        return v.isEmpty() && !e.hasAttributeNS(M, "val") ? null : v;
    }

    /** Giá trị m:val; phần tử vắng mặt -> mặc định; có mặt nhưng val="" -> "" (không có ký tự). */
    private static String attrOr(Element e, String dflt) {
        if (e == null) {
            return dflt;
        }
        String v = val(e);
        return v == null ? dflt : v;
    }

    private static boolean isOn(Element e) {
        if (e == null) {
            return false;
        }
        String v = val(e);
        return v == null || v.equals("on") || v.equals("1") || v.equals("true");
    }

    private static String text(Element e) {
        if (e == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c) {
                if (M.equals(c.getNamespaceURI()) && c.getLocalName().equals("t")) {
                    sb.append(c.getTextContent());
                } else if (M.equals(c.getNamespaceURI()) && c.getLocalName().equals("r")) {
                    sb.append(text(c));
                }
            }
        }
        return sb.toString();
    }
}
