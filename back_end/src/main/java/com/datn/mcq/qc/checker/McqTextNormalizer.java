package com.datn.mcq.qc.checker;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa nội dung câu trắc nghiệm cho bước dedup (hàm thuần, không Spring, DB hay LLM).
 *
 * <p>Quy tắc (quyết định 1a, 2026-10-21):
 * <ul>
 *   <li>Unicode NFC.</li>
 *   <li>Ngoài công thức: gộp mọi khoảng trắng liên tiếp thành một dấu cách. Trong công thức: bỏ khoảng trắng,
 *       vì khoảng trắng không có nghĩa trong math mode.</li>
 *   <li>Lệnh đồng nghĩa: \leq = \le, \geq = \ge, \neq = \ne. Cặp $...$ và $$...$$ coi như nhau.</li>
 *   <li>Không đổi chữ hoa/thường: biến toán học phân biệt hoa thường.</li>
 *   <li>Tập phương án được sắp xếp, nên thứ tự A-D không ảnh hưởng. Đáp án đúng và lời giải không tham gia.</li>
 * </ul>
 */
public final class McqTextNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LEQ = Pattern.compile("\\\\leq(?![A-Za-z])");
    private static final Pattern GEQ = Pattern.compile("\\\\geq(?![A-Za-z])");
    private static final Pattern NEQ = Pattern.compile("\\\\neq(?![A-Za-z])");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    /**
     * Cụm phủ định tường minh (quyết định 5 ngày 21/10, quyết định 3 ngày 22/10): không, chưa, ngoại trừ,
     * không phải, không đúng, không thuộc, không là, không thỏa mãn, không thể, phát biểu sai, nhận định sai,
     * khẳng định sai. Có ranh giới từ ở hai đầu, không phân biệt hoa thường, chấp nhận cách bỏ dấu cũ "thoả".
     * Cụm dài đứng trước trong phép chọn để khớp dài nhất. Chỉ xét phần văn bản ngoài công thức của thân câu.
     */
    private static final Pattern NEGATION = Pattern.compile(
            "(?<!\\p{L})(không thỏa mãn|không thoả mãn|không phải|không đúng|không thuộc|không là|không thể"
                    + "|phát biểu sai|nhận định sai|khẳng định sai|ngoại trừ|không|chưa)(?!\\p{L})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String OPTION_JOINER = " | ";
    private static final String STEM_SEPARATOR = " || ";

    private record Segment(boolean math, String content) {}

    private McqTextNormalizer() {}

    /** Chuẩn hóa một trường văn bản (thân câu hoặc một phương án). */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Segment seg : segments(text)) {
            if (seg.math()) {
                sb.append('$').append(normalizeMath(seg.content())).append('$');
            } else {
                sb.append(WHITESPACE.matcher(seg.content()).replaceAll(" "));
            }
        }
        return WHITESPACE.matcher(sb.toString()).replaceAll(" ").trim();
    }

    /** Văn bản chuẩn của cả câu: thân câu và tập phương án đã sắp xếp. Dùng làm dấu vân tay và đầu vào cosine. */
    public static String canonicalText(String stem, String a, String b, String c, String d) {
        List<String> options = new ArrayList<>(List.of(normalize(a), normalize(b), normalize(c), normalize(d)));
        options.sort(null);
        return normalize(stem) + STEM_SEPARATOR + String.join(OPTION_JOINER, options);
    }

    /** Dấu vân tay sau chuẩn hóa (tầng a). Hai câu cùng dấu vân tay là trùng. */
    public static String fingerprint(String stem, String a, String b, String c, String d) {
        return canonicalText(stem, a, b, c, d);
    }

    /**
     * Số liệu và công thức của thân câu: các công thức đã chuẩn hóa và các con số ngoài công thức, đã sắp xếp.
     * Hai câu chỉ được coi là trùng ở tầng cosine khi chữ ký này bằng nhau.
     */
    public static List<String> stemSignature(String stem) {
        List<String> signature = new ArrayList<>();
        if (stem == null) {
            return signature;
        }
        for (Segment seg : segments(stem)) {
            if (seg.math()) {
                String formula = normalizeMath(seg.content());
                if (!formula.isEmpty()) {
                    signature.add("$" + formula);
                }
            } else {
                Matcher m = NUMBER.matcher(seg.content());
                while (m.find()) {
                    signature.add("#" + m.group());
                }
            }
        }
        signature.sort(null);
        return signature;
    }

    /** Thân câu có cụm từ phủ định ở phần văn bản ngoài công thức hay không. */
    public static boolean hasNegation(String stem) {
        return !negationMarkers(stem).isEmpty();
    }

    /**
     * Các cụm phủ định tìm thấy trong phần văn bản ngoài công thức của thân câu, theo thứ tự xuất hiện,
     * ở dạng chữ thường chuẩn ("không thoả mãn" được quy về "không thỏa mãn").
     */
    public static List<String> negationMarkers(String stem) {
        List<String> markers = new ArrayList<>();
        if (stem == null) {
            return markers;
        }
        StringBuilder text = new StringBuilder();
        for (Segment seg : segments(stem)) {
            // Công thức được thay bằng khoảng trắng để chữ hai bên không dính vào nhau.
            text.append(seg.math() ? " " : seg.content()).append(' ');
        }
        Matcher m = NEGATION.matcher(WHITESPACE.matcher(text).replaceAll(" "));
        while (m.find()) {
            String marker = m.group(1).toLowerCase(Locale.ROOT);
            markers.add(marker.equals("không thoả mãn") ? "không thỏa mãn" : marker);
        }
        return markers;
    }

    /** Cosine giữa hai vectơ đếm 3-gram ký tự. Đối xứng, nằm trong [0, 1]. */
    public static double trigramCosine(String a, String b) {
        Map<String, Integer> va = trigrams(a);
        Map<String, Integer> vb = trigrams(b);
        if (va.isEmpty() || vb.isEmpty()) {
            return (va.isEmpty() && vb.isEmpty() && a != null && a.equals(b)) ? 1.0 : 0.0;
        }
        double dot = 0.0;
        for (Map.Entry<String, Integer> e : va.entrySet()) {
            Integer other = vb.get(e.getKey());
            if (other != null) {
                dot += (double) e.getValue() * other;
            }
        }
        double cos = dot / (norm(va) * norm(vb));
        return Math.min(1.0, cos);
    }

    private static Map<String, Integer> trigrams(String s) {
        Map<String, Integer> counts = new HashMap<>();
        if (s == null || s.isEmpty()) {
            return counts;
        }
        if (s.length() < 3) {
            counts.put(s, 1);
            return counts;
        }
        for (int i = 0; i + 3 <= s.length(); i++) {
            counts.merge(s.substring(i, i + 3), 1, Integer::sum);
        }
        return counts;
    }

    private static double norm(Map<String, Integer> v) {
        double sum = 0.0;
        for (int x : v.values()) {
            sum += (double) x * x;
        }
        return Math.sqrt(sum);
    }

    private static String normalizeMath(String math) {
        String s = LEQ.matcher(math).replaceAll("\\\\le");
        s = GEQ.matcher(s).replaceAll("\\\\ge");
        s = NEQ.matcher(s).replaceAll("\\\\ne");
        return WHITESPACE.matcher(s).replaceAll("");
    }

    /**
     * Tách văn bản (đã NFC) thành đoạn thường và đoạn công thức theo dấu $ hoặc $$.
     * Ký tự ngay sau dấu \ được giữ nguyên, nên \$ không phải dấu mở công thức.
     */
    private static List<Segment> segments(String raw) {
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC);
        List<Segment> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inMath = false;
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) {
                cur.append(c).append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '$') {
                result.add(new Segment(inMath, cur.toString()));
                cur.setLength(0);
                inMath = !inMath;
                i += (i + 1 < n && s.charAt(i + 1) == '$') ? 2 : 1;
                continue;
            }
            cur.append(c);
            i++;
        }
        result.add(new Segment(inMath, cur.toString()));
        return result;
    }
}
