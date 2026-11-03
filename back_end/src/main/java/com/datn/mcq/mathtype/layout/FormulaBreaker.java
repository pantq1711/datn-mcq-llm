package com.datn.mcq.mathtype.layout;

import static com.datn.mcq.mathtype.mtef.OmmlToMtef.M;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Tìm chỗ ngắt dòng cho công thức quá rộng: chỉ ở CẤP NGOÀI CÙNG, không bao giờ trong ngoặc, phân số, căn, chỉ số,
 * {@code \left…\right}, {@code \begin…\end}. Hai tầng: {@link Level#RELATION} (=, ≠, ≤, ≥, <, >, ⇔, ⇒, ≈…) rồi
 * {@link Level#ADDITIVE} (+ và − hai ngôi; dấu đứng đầu hoặc đứng sau một toán tử là dấu âm/dương, không ngắt).
 * Dòng sau bắt đầu bằng chính dấu đã ngắt.
 *
 * <p>Chỗ ngắt được tìm song song trên LaTeX (để dựng ảnh xem trước từng đoạn) và trên các con trực tiếp của
 * {@code m:oMath} (để dựng MTEF từng đoạn), bằng cùng một máy trạng thái. Hai bên ra số chỗ ngắt khác nhau (ký hiệu
 * LaTeX mà pandoc đổi khác cấu trúc) thì KHÔNG ngắt ở tầng đó — bên gọi chuyển sang thu nhỏ — chứ không đoán.
 */
public final class FormulaBreaker {

    public enum Level { RELATION, ADDITIVE }

    /** Một đoạn công thức: LaTeX (cho ảnh xem trước) và các con của m:oMath (cho MTEF). */
    public record Piece(String latex, List<Element> children) { }

    private static final Set<String> RELATION_COMMANDS = Set.of("le", "leq", "leqslant", "ge", "geq", "geqslant", "ne",
            "neq", "approx", "Leftrightarrow", "iff", "Rightarrow", "implies", "Longrightarrow", "Longleftrightarrow",
            "Leftarrow", "Longleftarrow");
    private static final String RELATION_CHARS = "=≠≤≥<>⇔⇒⇐≈⩽⩾⟹⟺⟸";
    /** Toán tử đứng trước số hạng (sau chúng, +/− là dấu âm/dương). */
    private static final Set<String> PREFIX_COMMANDS = Set.of("times", "cdot", "pm", "mp", "div", "ast", "not");
    private static final String PREFIX_CHARS = "×·⋅÷±∓∗*/";
    private static final Set<String> TRANSPARENT_COMMANDS = Set.of(",", ";", ":", "!", " ", "quad", "qquad",
            "displaystyle", "textstyle", "limits", "nolimits");

    private FormulaBreaker() {
    }

    /** Cắt {@code whole} ở mọi chỗ ngắt của tầng này; không có chỗ ngắt (hoặc hai bên lệch nhau) thì trả {@code [whole]}. */
    public static List<Piece> split(Piece whole, Level level) {
        List<Integer> latexBreaks = latexBreaks(whole.latex(), level);
        List<Integer> childBreaks = ommlBreaks(whole.children(), level);
        if (latexBreaks.isEmpty() || latexBreaks.size() != childBreaks.size()) {
            return List.of(whole);
        }
        List<Piece> out = new ArrayList<>();
        int latexFrom = 0;
        int childFrom = 0;
        for (int k = 0; k <= latexBreaks.size(); k++) {
            int latexTo = k < latexBreaks.size() ? latexBreaks.get(k) : whole.latex().length();
            int childTo = k < childBreaks.size() ? childBreaks.get(k) : whole.children().size();
            out.add(new Piece(whole.latex().substring(latexFrom, latexTo).strip(),
                    List.copyOf(whole.children().subList(childFrom, childTo))));
            latexFrom = latexTo;
            childFrom = childTo;
        }
        return out;
    }

    // ---------------------------------------------------------------- máy trạng thái dùng chung

    private static final class Scanner {
        private final Level level;
        private int depth;
        private boolean operand;

        Scanner(Level level) {
            this.level = level;
        }

        /** Gặp dấu quan hệ; true nếu ngắt dòng trước nó. */
        boolean relation() {
            boolean brk = level == Level.RELATION && depth == 0 && operand;
            operand = false;
            return brk;
        }

        /** Gặp + hoặc −; true nếu là dấu hai ngôi cần ngắt trước nó. */
        boolean additive() {
            boolean brk = level == Level.ADDITIVE && depth == 0 && operand;
            operand = false;
            return brk;
        }

        void atom() {
            operand = true;
        }

        void prefixOperator() {
            operand = false;
        }

        void enter() {
            depth++;
            operand = false;
        }

        void exit() {
            depth = Math.max(0, depth - 1);
            if (depth == 0) {
                operand = true;
            }
        }
    }

    // ---------------------------------------------------------------- LaTeX

    /** Vị trí (chỉ số ký tự) bắt đầu mỗi dấu cần ngắt trước nó. */
    static List<Integer> latexBreaks(String s, Level level) {
        List<Integer> out = new ArrayList<>();
        Scanner sc = new Scanner(level);
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int start = i;
            if (c == '\\') {
                int j = i + 1;
                if (j < n && Character.isLetter(s.charAt(j))) {
                    while (j < n && Character.isLetter(s.charAt(j))) {
                        j++;
                    }
                } else if (j < n) {
                    j++;
                }
                String cmd = s.substring(i + 1, j);
                i = j;
                switch (cmd) {
                    case "left" -> {
                        sc.enter();
                        i = skipDelimiter(s, i);
                    }
                    case "begin" -> sc.enter();
                    case "right" -> {
                        i = skipDelimiter(s, i);
                        sc.exit();
                    }
                    case "end" -> sc.exit();
                    default -> {
                        if (RELATION_COMMANDS.contains(cmd)) {
                            if (sc.relation()) {
                                out.add(start);
                            }
                        } else if (PREFIX_COMMANDS.contains(cmd)) {
                            sc.prefixOperator();
                        } else if (!TRANSPARENT_COMMANDS.contains(cmd)) {
                            sc.atom();
                        }
                    }
                }
                continue;
            }
            i++;
            switch (c) {
                case '{' -> sc.enter();
                case '}' -> sc.exit();
                case '(', '[' -> sc.enter();
                case ')', ']' -> sc.exit();
                case '^', '_', '&' -> sc.prefixOperator();
                case '+', '-' -> {
                    if (sc.additive()) {
                        out.add(start);
                    }
                }
                default -> {
                    if (RELATION_CHARS.indexOf(c) >= 0) {
                        if (sc.relation()) {
                            out.add(start);
                        }
                    } else if (PREFIX_CHARS.indexOf(c) >= 0) {
                        sc.prefixOperator();
                    } else {
                        sc.atom();
                    }
                }
            }
        }
        return out;
    }

    /** Sau {@code \left} / {@code \right} là một dấu ngoặc (ký tự hoặc lệnh): bỏ qua để không tính nó là toán tử. */
    private static int skipDelimiter(String s, int i) {
        int n = s.length();
        while (i < n && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        if (i >= n) {
            return i;
        }
        if (s.charAt(i) != '\\') {
            return i + 1;
        }
        int j = i + 1;
        if (j < n && Character.isLetter(s.charAt(j))) {
            while (j < n && Character.isLetter(s.charAt(j))) {
                j++;
            }
            return j;
        }
        return Math.min(n, j + 1);
    }

    // ---------------------------------------------------------------- OMML

    /** Chỉ số (trong {@code children}) của con cần ngắt trước nó. */
    static List<Integer> ommlBreaks(List<Element> children, Level level) {
        List<Integer> out = new ArrayList<>();
        Scanner sc = new Scanner(level);
        for (int idx = 0; idx < children.size(); idx++) {
            Element e = children.get(idx);
            String name = e.getLocalName();
            if (!M.equals(e.getNamespaceURI())) {
                sc.atom();
                continue;
            }
            if (name.equals("ctrlPr") || name.equals("rPr")) {
                continue;
            }
            if (!name.equals("r")) {
                // pandoc gắn chỉ số vào dấu ngoặc đóng: "(a+b)^2" thành "(", a, +, b, sSup[")" mũ 2]. Ngoặc đóng nằm
                // trong thân chỉ số vẫn đóng ngoặc mở ở cấp ngoài (LaTeX thấy ")" riêng), nếu không dấu "(" không đóng.
                if (isScript(name) && closesParenthesis(e)) {
                    sc.exit();
                }
                sc.atom();
                continue;
            }
            String text = runText(e);
            if (isBlank(text)) {
                continue;
            }
            if (text.codePointCount(0, text.length()) != 1) {
                sc.atom();
                continue;
            }
            int cp = text.codePointAt(0);
            String ch = new String(Character.toChars(cp));
            if (cp < 0x10000 && RELATION_CHARS.indexOf((char) cp) >= 0) {
                if (sc.relation()) {
                    out.add(idx);
                }
            } else if (cp == '+' || cp == '-' || cp == 0x2212) {
                if (sc.additive()) {
                    out.add(idx);
                }
            } else if (cp < 0x10000 && PREFIX_CHARS.indexOf((char) cp) >= 0) {
                sc.prefixOperator();
            } else if (ch.equals("(") || ch.equals("[")) {
                sc.enter();
            } else if (ch.equals(")") || ch.equals("]")) {
                sc.exit();
            } else {
                sc.atom();
            }
        }
        return out;
    }

    private static boolean isScript(String name) {
        return name.equals("sSup") || name.equals("sSub") || name.equals("sSubSup");
    }

    /** Thân (m:e) của chỉ số chỉ là một dấu ngoặc đóng ")" hoặc "]". */
    private static boolean closesParenthesis(Element script) {
        for (Node c = script.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element base && M.equals(base.getNamespaceURI()) && "e".equals(base.getLocalName())) {
                String text = null;
                for (Node k = base.getFirstChild(); k != null; k = k.getNextSibling()) {
                    if (!(k instanceof Element kid) || !M.equals(kid.getNamespaceURI())) {
                        continue;
                    }
                    if (!"r".equals(kid.getLocalName()) || text != null) {
                        return false;
                    }
                    text = runText(kid);
                }
                return ")".equals(text) || "]".equals(text);
            }
        }
        return false;
    }

    private static String runText(Element r) {
        StringBuilder sb = new StringBuilder();
        for (Node c = r.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && M.equals(e.getNamespaceURI()) && "t".equals(e.getLocalName())) {
                sb.append(e.getTextContent());
            }
        }
        return sb.toString().strip();
    }

    private static boolean isBlank(String s) {
        return s.chars().allMatch(ch -> Character.isWhitespace(ch) || ch == 0x200B || ch == 0xA0);
    }
}
