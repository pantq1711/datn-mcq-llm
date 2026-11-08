package com.datn.mcq.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thống nhất ký hiệu độ trong công thức khi xuất Word: mọi cách viết độ về "°" (U+00B0) sát số, cỡ thường.
 *
 * <p>Lý do (8/11, giáo viên chỉ ra trong cùng một câu "A. 90°" đúng chuẩn còn "B. 110˚" sai): "^{\circ}", "^\circ" qua pandoc
 * thành chỉ số trên chứa vòng tròn "∘" (Equation: {@code m:sSup}; MathType: ô chỉ số trên cỡ chỉ số), nên vòng tròn nhỏ, nâng
 * cao và tách khỏi số. "°" đứng ngay sau số là một ký tự thường: Equation là run "°", MathType là ký tự ° cỡ thường.
 *
 * <p>Chỉ sửa BÊN TRONG $...$ và $$...$$ (cách xác định như {@link LiteralNewlines}: "\" bỏ qua ký tự đi sau, $ không đóng thì
 * không đụng tới phần còn lại). Các dạng được đổi:
 * <ul>
 *   <li>{@code ^{\circ}}, {@code ^\circ}, {@code ^{\degree}}, {@code ^\degree} và chỉ số trên là ký tự vòng tròn
 *       ({@code ^{°}}, {@code ^°}, {@code ^{˚}}, {@code ^˚}, {@code ^{∘}}, {@code ^∘}; ˚ là U+02DA, ∘ là U+2218) (cho phép
 *       khoảng trắng quanh "^" và trong {...}): đổi thành "°" bất kể đứng sau gì, vì ý định luôn là độ.</li>
 *   <li>"˚" (U+02DA) đứng ngay sau một số ("90˚", "32,5˚"): đổi thành "°". "x˚", "a_1˚" giữ nguyên.</li>
 *   <li>{@code \degree} đứng một mình: pandoc không biết lệnh này (để nguyên chữ thô), đổi thành "°".</li>
 *   <li>{@code ^{o}}, {@code ^o}: CHỈ khi đứng ngay sau một số ("90^{o}", "32,5^o"); "x^{o}", "a_1^o", "(a+b)^o" giữ nguyên
 *       vì có thể là chỉ số trên thật.</li>
 * </ul>
 * Không đổi {@code ^{0}}, {@code ^0} (lũy thừa thật), {@code \circ} không có "^" phía trước và "∘" đứng một mình (hợp hàm
 * {@code f \circ g}, {@code f ∘ g}), và "°" ngoài công thức. Chuỗi không có gì để đổi được trả về đúng đối tượng ban đầu.
 */
public final class DegreeSymbols {

    private static final String DEGREE = "°";

    /**
     * "^" rồi \circ, \degree hoặc một ký tự vòng tròn (U+00B0 °, U+02DA ˚, U+2218 ∘), có hoặc không có {...}; lệnh phải kết thúc
     * (không đứng trước chữ cái: \circledR không phải). "∘" đứng một mình (f ∘ g) không có "^" phía trước nên không khớp.
     */
    private static final Pattern CARET_COMMAND = Pattern.compile(
            "\\s*\\^\\s*(?:\\{\\s*(?:\\\\(?:circ|degree)|[°˚∘])\\s*\\}"
                    + "|\\\\(?:circ|degree)(?![A-Za-z])|[°˚∘])");

    /** Lệnh \degree đứng một mình. */
    private static final Pattern BARE_DEGREE = Pattern.compile("\\\\degree(?![A-Za-z])");

    /**
     * Số (nguyên hoặc thập phân dấu "." hoặc ",") rồi "^" rồi chữ o: {@code 90^{o}}, {@code 32,5^o}. Số không được nằm sau chữ,
     * chữ số, "\", hoặc "_{" (chỉ số dưới như {@code a_{12}^o}); sau chữ o không được là chữ cái ({@code ^or}).
     */
    private static final Pattern NUMBER_CARET_O = Pattern.compile(
            "(?<![A-Za-z0-9_\\\\])(?<!_\\{)(\\d+(?:[.,]\\d+)?)\\s*\\^\\s*(?:\\{\\s*o\\s*\\}|o(?![A-Za-z]))");

    /** Số rồi "˚" (U+02DA): {@code 90˚}, {@code 32,5˚}. Cùng điều kiện về số như {@link #NUMBER_CARET_O}. */
    private static final Pattern NUMBER_RING = Pattern.compile(
            "(?<![A-Za-z0-9_\\\\])(?<!_\\{)(\\d+(?:[.,]\\d+)?)\\s*˚");

    private DegreeSymbols() {
    }

    /** @return văn bản với ký hiệu độ trong công thức đã về "°"; đúng đối tượng {@code text} khi không có gì để đổi */
    public static String normalize(String text) {
        if (text == null || text.indexOf('$') < 0) {
            return text;
        }
        StringBuilder sb = null; // chỉ tạo khi có chỗ đổi
        int copied = 0;          // text[0, copied) đã nằm trong sb
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c != '$') {
                i++;
                continue;
            }
            boolean display = i + 1 < n && text.charAt(i + 1) == '$';
            int open = display ? 2 : 1;
            int close = findMathClose(text, i + open);
            if (close < 0) {
                break;
            }
            String inner = text.substring(i + open, close);
            String fixed = fix(inner);
            if (!fixed.equals(inner)) {
                if (sb == null) {
                    sb = new StringBuilder(n);
                }
                sb.append(text, copied, i + open).append(fixed);
                copied = close;
            }
            boolean two = display && close + 1 < n && text.charAt(close + 1) == '$';
            i = close + (two ? 2 : 1);
        }
        if (sb == null) {
            return text;
        }
        return sb.append(text, copied, n).toString();
    }

    private static String fix(String math) {
        if (math.indexOf('^') < 0 && math.indexOf('\\') < 0 && math.indexOf('˚') < 0) {
            return math;
        }
        String s = CARET_COMMAND.matcher(math).replaceAll(Matcher.quoteReplacement(DEGREE));
        s = BARE_DEGREE.matcher(s).replaceAll(Matcher.quoteReplacement(DEGREE));
        s = NUMBER_CARET_O.matcher(s).replaceAll("$1" + DEGREE);
        return NUMBER_RING.matcher(s).replaceAll("$1" + DEGREE);
    }

    /** Vị trí "$" đóng đầu tiên từ {@code from} (bỏ qua "\" cùng ký tự đi sau nó); -1 khi không có. */
    private static int findMathClose(String text, int from) {
        for (int k = from; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == '\\') {
                k++;
            } else if (c == '$') {
                return k;
            }
        }
        return -1;
    }
}
