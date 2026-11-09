package com.datn.mcq.bankimport.mtef;

import com.datn.mcq.bankimport.mtef.MtefNode.Chr;
import com.datn.mcq.bankimport.mtef.MtefNode.Line;
import com.datn.mcq.bankimport.mtef.MtefNode.Matrix;
import com.datn.mcq.bankimport.mtef.MtefNode.Pile;
import com.datn.mcq.bankimport.mtef.MtefNode.Tmpl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Cây MTEF → LaTeX. Nguyên tắc: gặp thứ chưa có trong bảng thì ném {@link Unsupported} (kết quả UNSUPPORTED, không có
 * LaTeX), tuyệt đối không đoán. {@code PARTIAL} chỉ dành cho phần bị bỏ có chủ đích mà ghi được lý do (mã khoảng trắng lạ).
 */
final class MtefToLatex {

    /** Công thức không dịch được. */
    static final class Unsupported extends RuntimeException {
        Unsupported(String reason) {
            super(reason, null, false, false);
        }
    }

    // selector → tên (theo đặc tả MTEF5)
    private static final String[] TMPL_NAMES = {"tmANGLE", "tmPAREN", "tmBRACE", "tmBRACK", "tmBAR", "tmDBAR",
            "tmFLOOR", "tmCEILING", "tmOBRACK", "tmINTERVAL", "tmROOT", "tmFRACT", "tmUBAR", "tmOBAR", "tmARROW",
            "tmINTEG", "tmSUM", "tmPROD", "tmCOPROD", "tmUNION", "tmINTER", "tmINTOP", "tmSUMOP", "tmLIM", "tmHBRACE",
            "tmHBRACK", "tmLDIV", "tmSUB", "tmSUP", "tmSUBSUP", "tmDIRAC", "tmVEC", "tmTILDE", "tmHAT", "tmARC",
            "tmJSTATUS", "tmSTRIKE", "tmBOX"};

    private static final int FACE_TEXT = 1, FACE_FUNC = 2, FACE_VAR = 3, FACE_LCGREEK = 4, FACE_UCGREEK = 5,
            FACE_SYM = 6, FACE_NUM = 8, FACE_MTEXTRA = 11, FACE_TEXT_FE = 12, FACE_EXPAND = 22, FACE_SPACE = 24;

    private static final Set<String> FUNCTIONS = Set.of("sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "lg",
            "lim", "max", "min", "sup", "inf", "exp", "det", "gcd", "arg", "deg", "dim", "ker", "sinh", "cosh", "tanh");

    private static final Map<Integer, String> SYMBOLS = Map.ofEntries(
            Map.entry(0x2212, "-"), Map.entry(0x2013, "-"), Map.entry(0x2014, "-"),
            Map.entry(0x00B1, "\\pm "), Map.entry(0x00D7, "\\times "), Map.entry(0x00F7, "\\div "),
            Map.entry(0x22C5, "\\cdot "), Map.entry(0x00B7, "\\cdot "), Map.entry(0x2260, "\\neq "),
            Map.entry(0x2264, "\\leq "), Map.entry(0x2265, "\\geq "), Map.entry(0x2248, "\\approx "),
            Map.entry(0x2261, "\\equiv "), Map.entry(0x221E, "\\infty "), Map.entry(0x2208, "\\in "),
            Map.entry(0x2209, "\\notin "), Map.entry(0x2282, "\\subset "), Map.entry(0x2283, "\\supset "),
            Map.entry(0x2286, "\\subseteq "), Map.entry(0x2287, "\\supseteq "), Map.entry(0x222A, "\\cup "),
            Map.entry(0x2229, "\\cap "), Map.entry(0x2205, "\\emptyset "), Map.entry(0x21D2, "\\Rightarrow "),
            Map.entry(0x21D4, "\\Leftrightarrow "), Map.entry(0x2192, "\\to "), Map.entry(0x2190, "\\leftarrow "),
            Map.entry(0x2194, "\\leftrightarrow "), Map.entry(0x22A5, "\\perp "), Map.entry(0x2225, "\\parallel "),
            Map.entry(0x2220, "\\angle "), Map.entry(0x00B0, "^{\\circ}"), Map.entry(0x2218, "\\circ "),
            Map.entry(0x25B3, "\\triangle "), Map.entry(0x0394, "\\Delta "), Map.entry(0x2032, "'"),
            Map.entry(0x2033, "''"), Map.entry(0x00B2, "^{2}"), Map.entry(0x00B3, "^{3}"), Map.entry(0x2234, "\\therefore "),
            Map.entry(0x2235, "\\because "), Map.entry(0x2200, "\\forall "), Map.entry(0x2203, "\\exists "),
            Map.entry(0x2026, "\\ldots "), Map.entry(0x00A0, "\\ "), Map.entry(0x2223, "|"), Map.entry(0x2236, ":"));

    private static final String[] LC_GREEK = {"alpha", "beta", "gamma", "delta", "varepsilon", "zeta", "eta", "theta",
            "iota", "kappa", "lambda", "mu", "nu", "xi", "o", "pi", "rho", "varsigma", "sigma", "tau", "upsilon",
            "varphi", "chi", "psi", "omega"};          // U+03B1 … U+03C9
    private static final Map<Integer, String> UC_GREEK = Map.of(0x0393, "\\Gamma ", 0x0394, "\\Delta ",
            0x0398, "\\Theta ", 0x039B, "\\Lambda ", 0x039E, "\\Xi ", 0x03A0, "\\Pi ", 0x03A3, "\\Sigma ",
            0x03A6, "\\Phi ", 0x03A8, "\\Psi ", 0x03A9, "\\Omega ");

    private final Set<String> partialReasons = new LinkedHashSet<>();
    private int scriptDepth;

    FormulaResult translate(MtefNode body) {
        String latex;
        try {
            latex = clean(node(body));
        } catch (Unsupported u) {
            return FormulaResult.unsupported(u.getMessage());
        } catch (StackOverflowError e) {
            return FormulaResult.unsupported("cay_qua_sau");
        }
        if (latex.isBlank()) {
            return FormulaResult.unsupported("cong_thuc_rong");
        }
        if (!balanced(latex)) {
            return FormulaResult.unsupported("ngoac_khong_can_bang");
        }
        return partialReasons.isEmpty() ? FormulaResult.ok(latex)
                : FormulaResult.partial(latex, String.join("; ", partialReasons));
    }

    // ---------- nút ----------

    private String node(MtefNode n) {
        if (n instanceof Line l) {
            return items(l.items());
        }
        if (n instanceof Pile p) {
            return pile(p);
        }
        throw new Unsupported("nut_la");
    }

    /** PILE: các dòng xếp chồng → mảng một cột; một dòng thì chỉ là dòng đó. */
    private String pile(Pile p) {
        List<String> rows = new ArrayList<>();
        for (MtefNode n : p.children()) {
            if (!(n instanceof Line l)) {
                throw new Unsupported("PILE_phan_tu_la");
            }
            rows.add(l.isNull() ? "" : items(l.items()));
        }
        if (rows.isEmpty()) {
            throw new Unsupported("PILE_rong");
        }
        if (rows.size() == 1) {
            return rows.get(0);
        }
        String align = switch (p.halign()) {
            case 2 -> "c";
            case 3 -> "r";
            default -> "l";
        };
        return "\\begin{array}{" + align + "}" + String.join("\\\\", rows) + "\\end{array}";
    }

    private String items(List<MtefNode> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            MtefNode n = items.get(i);
            if (n instanceof Chr c) {
                if (c.unresolved() != null) {
                    throw new Unsupported(c.unresolved());
                }
                int face = c.face();
                if (isTextFace(face)) {
                    i = textRun(items, i, sb);
                } else if (face == FACE_FUNC && isAsciiLetter(c.code())) {
                    i = functionRun(items, i, sb);
                } else if (face == FACE_FUNC && c.code() == ',' && decimalComma(items, i)) {
                    sb.append("{,}");
                } else {
                    sb.append(withEmbellishments(c, character(c)));
                }
            } else if (n instanceof Tmpl t) {
                sb.append(template(t));
            } else if (n instanceof Line l) {
                sb.append(items(l.items()));
            } else if (n instanceof Matrix) {
                throw new Unsupported("MATRIX_ngoai_he_phuong_trinh");
            } else if (n instanceof Pile p) {
                sb.append(pile(p));
            } else {
                throw new Unsupported("nut_la");
            }
        }
        return sb.toString();
    }

    private static boolean decimalComma(List<MtefNode> items, int i) {
        return i > 0 && i + 1 < items.size()
                && items.get(i - 1) instanceof Chr a && a.face() == FACE_NUM && Character.isDigit(a.code())
                && items.get(i + 1) instanceof Chr b && b.face() == FACE_NUM && Character.isDigit(b.code());
    }

    private static boolean isAsciiLetter(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** Chuỗi ký tự face FUNC liên tiếp là tên hàm: sin → \sin, tên lạ → \mathrm{...}. */
    private int functionRun(List<MtefNode> items, int start, StringBuilder sb) {
        StringBuilder name = new StringBuilder();
        int i = start;
        while (i < items.size() && items.get(i) instanceof Chr c && c.face() == FACE_FUNC && isAsciiLetter(c.code())) {
            name.append((char) c.code());
            i++;
        }
        String s = name.toString();
        sb.append(FUNCTIONS.contains(s) ? "\\" + s + " " : "\\mathrm{" + s + "}");
        return i - 1;
    }

    /** Chuỗi ký tự face TEXT liên tiếp → \text{...}; ký tự độ (°) tách ra thành ^{\circ}. */
    private int textRun(List<MtefNode> items, int start, StringBuilder sb) {
        StringBuilder text = new StringBuilder();
        int face = normalizeTextFace(((Chr) items.get(start)).face());
        int i = start;
        while (i < items.size() && items.get(i) instanceof Chr c && c.unresolved() == null && isTextFace(c.face())
                && normalizeTextFace(c.face()) == face) {
            if (c.code() == 0xB0) {
                while (text.length() > 0 && text.charAt(text.length() - 1) == ' ') {
                    text.setLength(text.length() - 1);
                }
                flushText(text, sb, face);
                sb.append(scriptDepth > 0 ? "\\circ " : "^{\\circ}");
            } else {
                text.append(escapeText(c.code()));
            }
            i++;
        }
        flushText(text, sb, face);
        return i - 1;
    }

    /** Mặt chữ văn bản: TEXT (1), TEXT_FE (12), và các kiểu người dùng đã quy đổi: nghiêng 101, đậm 102, đậm nghiêng 103, đơn cách 104. */
    static boolean isTextFace(int face) {
        return face == FACE_TEXT || face == FACE_TEXT_FE || (face >= 101 && face <= 104);
    }

    private static int normalizeTextFace(int face) {
        return face == FACE_TEXT_FE ? FACE_TEXT : face;
    }

    private static void flushText(StringBuilder text, StringBuilder sb, int face) {
        if (text.length() == 0) {
            return;
        }
        String body = text.toString();
        if (body.isBlank()) {       // chỉ có dấu cách: viết \  thay cho \text{ } (chế độ toán bỏ qua dấu cách thường)
            sb.append("\\ ");
            text.setLength(0);
            return;
        }
        if (face == 101) {
            formatItalic(body, sb);
            text.setLength(0);
            return;
        }
        sb.append(switch (face) {
            case 101 -> "\\textit{" + body + "}";
            case 102 -> "\\textbf{" + body + "}";
            case 103 -> "\\textbf{\\textit{" + body + "}}";
            case 104 -> "\\texttt{" + body + "}";
            default -> "\\text{" + body + "}";
        });
        text.setLength(0);
    }

    private static void formatItalic(String body, StringBuilder sb) {
        if (containsNonAscii(body) || hasSpaceBetweenWords(body)) {
            sb.append("\\text{").append(body).append("}");
            return;
        }
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == ' ') {
                sb.append("\\ ");
            } else {
                sb.append(c);
            }
        }
    }

    private static boolean hasSpaceBetweenWords(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                int j = i + 1;
                while (j < s.length() && Character.isWhitespace(s.charAt(j))) {
                    j++;
                }
                if (j > i + 1 && j < s.length() && Character.isLetter(s.charAt(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsNonAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) {
                return true;
            }
        }
        return false;
    }

    private static String escapeText(int code) {
        return switch (code) {
            case '%', '$', '&', '#', '_', '{', '}' -> "\\" + (char) code;
            case 0xA0 -> " ";
            case '\\' -> "\\textbackslash{}";
            case '~', '^' -> "\\" + (char) code + "{}";
            default -> {
                if (code < 0x20 || code > 0xFFFF) {
                    throw new Unsupported("ky_tu_text_U+" + hex(code));
                }
                yield String.valueOf((char) code);
            }
        };
    }

    // ---------- ký tự ----------

    private String character(Chr c) {
        int code = c.code();
        switch (c.face()) {
            case FACE_SPACE:
                return space(code);
            case FACE_EXPAND:
                throw new Unsupported("ky_tu_EXPAND_ngoai_template_U+" + hex(code));
            case FACE_LCGREEK:
                if (code >= 0x03B1 && code <= 0x03C9 && !LC_GREEK[code - 0x03B1].equals("o")) {
                    return "\\" + LC_GREEK[code - 0x03B1] + " ";
                }
                throw new Unsupported("chu_Hy_Lap_thuong_U+" + hex(code));
            case FACE_UCGREEK:
                if (UC_GREEK.containsKey(code)) {
                    return UC_GREEK.get(code);
                }
                throw new Unsupported("chu_Hy_Lap_hoa_U+" + hex(code));
            case FACE_NUM: case FACE_VAR: case FACE_FUNC: case FACE_SYM: case FACE_MTEXTRA:
                return plain(c);
            default:
                throw new Unsupported("typeface_" + c.face());
        }
    }

    /** Ký tự thường: ASCII (thoát ký tự đặc biệt của LaTeX) hoặc tra bảng ký hiệu theo mã Unicode. */
    private String plain(Chr c) {
        int code = c.code();
        if (code >= 0x20 && code < 0x7F) {
            return switch (code) {
                case '%', '$', '&', '#', '_', '{', '}' -> "\\" + (char) code;
                case '\\' -> "\\backslash ";
                case '~' -> "\\sim ";
                case '^' -> "\\hat{}";
                case '<', '>', '/', '|' -> String.valueOf((char) code);
                default -> String.valueOf((char) code);
            };
        }
        String s = SYMBOLS.get(code);
        if (s != null) {
            if (scriptDepth > 0) {      // đã ở trong ô chỉ số: không bọc ^{} lần nữa
                if (code == 0x00B0) {
                    return "\\circ ";
                }
                if (code == 0x00B2 || code == 0x00B3) {
                    return String.valueOf(code - 0x00B0);
                }
            }
            return s;
        }
        if (c.face() == FACE_VAR || c.face() == FACE_NUM) {
            if (code >= 0x03B1 && code <= 0x03C9 && !LC_GREEK[code - 0x03B1].equals("o")) {
                return "\\" + LC_GREEK[code - 0x03B1] + " ";
            }
            if (UC_GREEK.containsKey(code)) {
                return UC_GREEK.get(code);
            }
        }
        throw new Unsupported("ky_tu_U+" + hex(code) + "_typeface_" + c.face());
    }

    /** Mã khoảng trắng MathType (vùng riêng U+EFxx), theo bảng của bộ đọc MTEF tham chiếu (MIT). */
    private String space(int code) {
        return switch (code) {
            case 0xEF00, 0xEF01 -> "";            // dấu căn cột, khoảng trắng độ rộng 0: không hiển thị
            case 0xEF02, 0xEF07 -> "\\,";
            case 0xEF03 -> "\\:";
            case 0xEF04 -> "\\;";
            case 0xEF05 -> "\\quad ";
            case 0xEF06 -> "\\qquad ";
            default -> {
                partialReasons.add("ma_khoang_trang_U+" + hex(code) + "_bi_bo");
                yield "";
            }
        };
    }

    private String withEmbellishments(Chr c, String base) {
        String out = base;
        for (int type : c.embells()) {
            out = switch (type) {
                case 2 -> "\\dot{" + out + "}";
                case 3 -> "\\ddot{" + out + "}";
                case 5 -> out + "'";
                case 6 -> out + "''";
                case 8 -> "\\tilde{" + out + "}";
                case 9 -> "\\hat{" + out + "}";
                case 11 -> "\\vec{" + out + "}";
                case 17 -> "\\bar{" + out + "}";
                default -> throw new Unsupported("embellishment_" + type);
            };
        }
        return out;
    }

    // ---------- template ----------

    private static String name(int selector) {
        return selector >= 0 && selector < TMPL_NAMES.length ? TMPL_NAMES[selector] : "tm#" + selector;
    }

    private static List<Line> slots(Tmpl t) {
        List<Line> out = new ArrayList<>();
        for (MtefNode n : t.children()) {
            if (n instanceof Line l) {
                out.add(l);
            }
        }
        return out;
    }

    private static List<Chr> extras(Tmpl t) {
        List<Chr> out = new ArrayList<>();
        for (MtefNode n : t.children()) {
            if (n instanceof Chr c) {
                out.add(c);
            }
        }
        return out;
    }

    private String slot(List<Line> slots, int i) {
        return i < slots.size() && !slots.get(i).isNull() ? items(slots.get(i).items()) : "";
    }

    /** Nội dung ô chỉ số: ký hiệu độ và lũy thừa dạng ký tự được viết trần vì đã nằm trong ^{} hoặc _{}. */
    private String scriptSlot(List<Line> slots, int i) {
        scriptDepth++;
        try {
            return slot(slots, i);
        } finally {
            scriptDepth--;
        }
    }

    private String template(Tmpl t) {
        String nm = name(t.selector());
        List<Line> s = slots(t);
        switch (t.selector()) {
            case 11: {      // tmFRACT
                if (t.variation() != 0 || s.size() != 2) {
                    throw new Unsupported(nm + "_bien_the_" + t.variation());
                }
                return "\\frac{" + slot(s, 0) + "}{" + slot(s, 1) + "}";
            }
            case 10: {      // tmROOT
                String index = slot(s, 1);
                return index.isEmpty() ? "\\sqrt{" + slot(s, 0) + "}" : "\\sqrt[" + index + "]{" + slot(s, 0) + "}";
            }
            case 27: case 28: case 29: {   // tmSUB, tmSUP, tmSUBSUP: ô 0 = chỉ số dưới, ô 1 = chỉ số trên
                String sub = scriptSlot(s, 0);
                String sup = scriptSlot(s, 1);
                if (sub.isEmpty() && sup.isEmpty()) {
                    throw new Unsupported(nm + "_rong");
                }
                return (sub.isEmpty() ? "" : "_{" + sub + "}") + (sup.isEmpty() ? "" : "^{" + sup + "}");
            }
            case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7:
                return delimiters(t, nm, s);
            case 12:
                return "\\underline{" + slot(s, 0) + "}";
            case 13:
                return "\\overline{" + slot(s, 0) + "}";
            case 31: case 32: case 33: case 34:
                return accent(t, nm, s);
            default:
                throw new Unsupported(nm + "_chua_ho_tro");
        }
    }

    /** Ngoặc: biến thể bit 0 = có ngoặc trái, bit 1 = có ngoặc phải; ký tự ngoặc nằm ở các CHAR sau ô nội dung. */
    private String delimiters(Tmpl t, String nm, List<Line> s) {
        boolean left = (t.variation() & 1) != 0;
        boolean right = (t.variation() & 2) != 0;
        List<Chr> ex = extras(t);
        int need = (left ? 1 : 0) + (right ? 1 : 0);
        if (ex.size() < need || s.isEmpty()) {
            throw new Unsupported(nm + "_thieu_ky_tu_ngoac");
        }
        // tmBAR/tmDBAR: MathType vẽ thanh bằng glyph MT Extra (U+EC07, U+EC08...), nên dựa vào loại template, không vào mã
        String l = !left ? "." : t.selector() == 4 ? "|" : t.selector() == 5 ? "\\|" : delimiter(ex.get(0).code(), nm);
        String r = !right ? "." : t.selector() == 4 ? "|" : t.selector() == 5 ? "\\|"
                : delimiter(ex.get(left ? 1 : 0).code(), nm);
        // Hệ phương trình: ngoặc nhọn trái bao một MATRIX một cột
        List<MtefNode> content = s.get(0).items();
        if (t.selector() == 2 && left && !right && content.size() == 1 && content.get(0) instanceof Matrix m) {
            return cases(m);
        }
        return "\\left" + l + slot(s, 0) + "\\right" + r;
    }

    private String cases(Matrix m) {
        if (m.cols() != 1) {
            throw new Unsupported("he_phuong_trinh_nhieu_cot");
        }
        List<String> rows = new ArrayList<>();
        for (MtefNode n : m.children()) {
            if (n instanceof Line l && !l.isNull()) {
                rows.add(items(l.items()));
            } else {
                throw new Unsupported("he_phuong_trinh_dong_la");
            }
        }
        if (rows.size() != m.rows()) {
            throw new Unsupported("he_phuong_trinh_so_dong_khong_khop");
        }
        return "\\begin{cases}" + String.join("\\\\", rows) + "\\end{cases}";
    }

    private static String delimiter(int code, String nm) {
        return switch (code) {
            case '(', ')', '[', ']', '|' -> String.valueOf((char) code);
            case '{' -> "\\{";
            case '}' -> "\\}";
            case 0x2016 -> "\\|";
            case 0x27E8, 0x2329 -> "\\langle ";
            case 0x27E9, 0x232A -> "\\rangle ";
            case 0x230A -> "\\lfloor ";
            case 0x230B -> "\\rfloor ";
            case 0x2308 -> "\\lceil ";
            case 0x2309 -> "\\rceil ";
            default -> throw new Unsupported(nm + "_ngoac_U+" + hex(code));
        };
    }

    /** tmHAT, tmTILDE, tmVEC, tmARC: dấu nằm ở CHAR (EXPAND) sau ô nội dung. */
    private String accent(Tmpl t, String nm, List<Line> s) {
        List<Chr> ex = extras(t);
        String body = slot(s, 0);
        if (ex.isEmpty()) {
            throw new Unsupported(nm + "_thieu_dau");
        }
        int code = ex.get(0).code();
        return switch (t.selector()) {
            case 33 -> {
                if (code != 0x0302 && code != 0x02C6 && code != 0x005E) {
                    throw new Unsupported(nm + "_dau_U+" + hex(code));
                }
                yield "\\widehat{" + body + "}";
            }
            case 32 -> "\\widetilde{" + body + "}";
            case 31 -> switch (code) {
                case 0x20D7, 0x2192 -> "\\overrightarrow{" + body + "}";
                case 0x20D6, 0x2190 -> "\\overleftarrow{" + body + "}";
                case 0x20E1, 0x2194 -> "\\overleftrightarrow{" + body + "}";
                default -> throw new Unsupported(nm + "_dau_U+" + hex(code));
            };
            default -> "\\overset{\\frown}{" + body + "}";
        };
    }

    // ---------- hoàn thiện ----------

    private static final Pattern SPACE_AFTER_WORD = Pattern.compile("(\\\\[A-Za-z]+) +(?![A-Za-z0-9])");
    private static final Pattern MULTI_SPACE = Pattern.compile(" {2,}");


    /** Bỏ khoảng trắng thừa sau lệnh khi ký tự kế tiếp không phải chữ/số. */
    static String clean(String s) {
        String out = SPACE_AFTER_WORD.matcher(s).replaceAll("$1");
        out = MULTI_SPACE.matcher(out).replaceAll(" ");
        out = stripTrailingMathWhitespace(out);
        return out;
    }

    static String stripTrailingMathWhitespace(String s) {
        String out = s;
        while (true) {
            String prev = out;
            int end = out.length();
            while (end > 0 && Character.isWhitespace(out.charAt(end - 1))) {
                end--;
            }
            out = out.substring(0, end);

            String[] spaceCmds = {"\\qquad", "\\quad", "\\,", "\\;", "\\:", "\\!"};
            for (String cmd : spaceCmds) {
                if (out.endsWith(cmd)) {
                    int cmdStart = out.length() - cmd.length();
                    int bs = 0;
                    while (cmdStart - 1 - bs >= 0 && out.charAt(cmdStart - 1 - bs) == '\\') {
                        bs++;
                    }
                    if (bs % 2 == 0) {
                        out = out.substring(0, cmdStart);
                        break;
                    }
                }
            }

            int trailingBs = 0;
            while (trailingBs < out.length() && out.charAt(out.length() - 1 - trailingBs) == '\\') {
                trailingBs++;
            }
            if (trailingBs % 2 != 0) {
                out = out.substring(0, out.length() - 1);
            }

            if (out.equals(prev)) {
                break;
            }
        }
        return out;
    }

    private static boolean balanced(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;       // bỏ qua ký tự bị thoát (\{ \} \\)
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth < 0) {
                return false;
            }
        }
        return depth == 0 && count(s, "\\left") == count(s, "\\right");
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + sub.length())) {
            n++;
        }
        return n;
    }

    private static String hex(int code) {
        return String.format("%04X", code);
    }
}
