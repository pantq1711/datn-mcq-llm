package com.datn.mcq.common.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Giá trị chính xác của một đáp số câu Trả lời ngắn (V4, bản sửa 1 thiết kế và quyết định A-E ngày 22/10).
 * Hàm thuần, không dùng thư viện ngoài.
 *
 * <p>Giá trị có dạng k·√n: k là phân số tối giản p/q (BigInteger, q > 0), n nguyên dương không chứa thừa số
 * chính phương (n = 1 là số hữu tỉ). Với n như vậy cách viết k·√n là duy nhất, nên hai giá trị bằng nhau khi và chỉ
 * khi khóa chuẩn bằng nhau.
 *
 * <p>Tiền xử lý: trim; tối đa {@value #MAX_LENGTH} ký tự; bỏ cặp $...$ hoặc $$...$$ bao cả chuỗi (còn $ khác là lỗi);
 * đổi "−" (U+2212) thành "-", "{,}" thành ",", \cdot thành "·", \dfrac và \tfrac thành \frac; bỏ mọi khoảng trắng.
 *
 * <p>Ngữ pháp:
 * <pre>
 * value  := '-'? body
 * body   := '\frac{' value '}{' value '}' ( '·'? sqrt )?  |  term ( '/' plain )?
 * term   := number ( '·'? sqrt )?  |  sqrt
 * plain  := number | sqrt                      (mẫu số dạng "a/b" không có hệ số trước căn, tránh "1/2√3" mơ hồ)
 * number := DIGITS ( (',' | '.') DIGITS )?     ('.' theo sau đúng 3 chữ số: có thể là phân cách hàng nghìn, lỗi;
 *                                               miễn khi phần nguyên là 0, ví dụ 0.125 và -0.125)
 * sqrt   := '\sqrt{' DIGITS '}' | '√{' DIGITS '}' | '√' DIGITS   (1 &lt;= số dưới căn &lt;= 10^12)
 * </pre>
 *
 * <p>STRICT (đáp số LLM sinh và đáp số cuối vào ngân hàng) chặn thêm: chữ số 0 thừa ở đầu (05, \frac{05}{2}) và
 * dấu "-" trước một giá trị bằng 0 (-0, -0,0, -\frac{0}{3}). LENIENT (đáp số giáo viên nhập mù, đáp số Solver)
 * chỉ cần phân tích được. Phân số mà tử và mẫu viết ra có ước chung (2/4, \frac{6}{8}) vẫn hợp lệ, kèm cảnh báo.
 */
public final class ShortAnswerValue {

    public enum Mode { STRICT, LENIENT }

    public static final int MAX_LENGTH = 100;
    private static final BigInteger MAX_RADICAND = BigInteger.TEN.pow(12);

    public static final String MSG_UNPARSEABLE = "không phân tích được thành một số";
    public static final String MSG_THOUSANDS = "dấu chấm có thể là phân cách hàng nghìn";
    public static final String MSG_LEADING_ZERO = "có chữ số 0 thừa ở đầu";
    public static final String MSG_NEGATIVE_ZERO = "số 0 mang dấu âm";
    public static final String MSG_TOO_LONG = "chuỗi dài quá 100 ký tự";
    public static final String MSG_RADICAND_TOO_LARGE = "số dưới dấu căn lớn hơn 10^12";
    public static final String MSG_ZERO_DENOMINATOR = "mẫu số bằng 0";
    public static final String WARN_UNREDUCED_FRACTION = "phân số chưa tối giản";

    /**
     * @param value    giá trị; null khi không phân tích được
     * @param error    thông báo lỗi đầu tiên; null khi phân tích được
     * @param warnings cảnh báo (chỉ khi phân tích được), hiện chỉ có {@link #WARN_UNREDUCED_FRACTION}
     */
    public record ParseResult(ShortAnswerValue value, String error, List<String> warnings) {
        public ParseResult {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public boolean ok() {
            return value != null;
        }
    }

    private final BigInteger num;
    private final BigInteger den;
    private final BigInteger radicand;

    private ShortAnswerValue(BigInteger num, BigInteger den, BigInteger radicand) {
        if (den.signum() == 0) {
            throw new ParseFailure(MSG_ZERO_DENOMINATOR);
        }
        if (den.signum() < 0) {
            num = num.negate();
            den = den.negate();
        }
        BigInteger g = num.gcd(den);
        if (g.signum() != 0 && !g.equals(BigInteger.ONE)) {
            num = num.divide(g);
            den = den.divide(g);
        }
        if (num.signum() == 0) {
            den = BigInteger.ONE;
            radicand = BigInteger.ONE;
        }
        this.num = num;
        this.den = den;
        this.radicand = radicand;
    }

    private static ShortAnswerValue rational(BigInteger num, BigInteger den) {
        return new ShortAnswerValue(num, den, BigInteger.ONE);
    }

    public static ParseResult parse(String raw, Mode mode) {
        if (raw == null) {
            return failure(MSG_UNPARSEABLE);
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return failure(MSG_UNPARSEABLE);
        }
        if (trimmed.length() > MAX_LENGTH) {
            return failure(MSG_TOO_LONG);
        }
        try {
            Parser parser = new Parser(preprocess(trimmed), mode == Mode.STRICT);
            ShortAnswerValue value = parser.parseAll();
            return new ParseResult(value, null, parser.warnings);
        } catch (ParseFailure ex) {
            return failure(ex.getMessage());
        }
    }

    private static final java.util.regex.Pattern LEADING_ZEROS =
            java.util.regex.Pattern.compile("(?<![0-9.,])0+(?=[0-9])");

    /**
     * Gợi ý cách viết lại khi có chữ số 0 thừa ở đầu (quyết định 3 sau GĐ2): bỏ các số 0 đứng đầu phần nguyên của
     * mỗi số, giữ nguyên phần còn lại. Chỉ biến đổi chuỗi người dùng nhập, không dùng dữ liệu nào khác.
     * Ví dụ "05" thành "5", "-007,5" thành "-7,5", "\frac{05}{2}" thành "\frac{5}{2}".
     */
    public static String withoutLeadingZeros(String raw) {
        return raw == null ? null : LEADING_ZEROS.matcher(raw.strip()).replaceAll("");
    }

    private static ParseResult failure(String message) {
        return new ParseResult(null, message, List.of());
    }

    /**
     * Khóa chuẩn: "p" hoặc "p/q" với số hữu tỉ; "p·√n" hoặc "(p/q)·√n" khi có căn. Ví dụ -3/4, 2·√3, (1/2)·√3.
     */
    public String canonicalKey() {
        String k = den.equals(BigInteger.ONE) ? num.toString() : num + "/" + den;
        if (radicand.equals(BigInteger.ONE)) {
            return k;
        }
        return (den.equals(BigInteger.ONE) ? k : "(" + k + ")") + "·√" + radicand;
    }

    public boolean sameValue(ShortAnswerValue other) {
        return other != null && num.equals(other.num) && den.equals(other.den) && radicand.equals(other.radicand);
    }

    public boolean isZero() {
        return num.signum() == 0;
    }

    @Override
    public String toString() {
        return canonicalKey();
    }

    private boolean isRational() {
        return radicand.equals(BigInteger.ONE);
    }

    private ShortAnswerValue negate() {
        return new ShortAnswerValue(num.negate(), den, radicand);
    }

    /** Hệ số hữu tỉ nhân với căn: (p/q)·(s·√f). */
    private ShortAnswerValue timesRadical(ShortAnswerValue radical) {
        return new ShortAnswerValue(num.multiply(radical.num), den.multiply(radical.den), radical.radicand);
    }

    /**
     * (a·√m) / (b·√n) với m, n không chứa thừa số chính phương. Đặt g = gcd(m, n), m = g·m', n = g·n':
     * √m/√n = √(m'·n')/n', và m'·n' vẫn không chứa thừa số chính phương nên không cần phân tích lại.
     */
    private ShortAnswerValue dividedBy(ShortAnswerValue other) {
        if (other.num.signum() == 0) {
            throw new ParseFailure(MSG_ZERO_DENOMINATOR);
        }
        BigInteger g = radicand.gcd(other.radicand);
        BigInteger mPrime = radicand.divide(g);
        BigInteger nPrime = other.radicand.divide(g);
        return new ShortAnswerValue(
                num.multiply(other.den),
                den.multiply(other.num).multiply(nPrime),
                mPrime.multiply(nPrime));
    }

    private static String preprocess(String s) {
        String inner = s;
        if (inner.length() >= 4 && inner.startsWith("$$") && inner.endsWith("$$")) {
            inner = inner.substring(2, inner.length() - 2);
        } else if (inner.length() >= 2 && inner.startsWith("$") && inner.endsWith("$")) {
            inner = inner.substring(1, inner.length() - 1);
        }
        if (inner.indexOf('$') >= 0) {
            throw new ParseFailure(MSG_UNPARSEABLE);
        }
        inner = inner.replace('−', '-')
                .replace("{,}", ",")
                .replace("\\cdot", "·")
                .replace("\\dfrac", "\\frac")
                .replace("\\tfrac", "\\frac");
        StringBuilder sb = new StringBuilder(inner.length());
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final class ParseFailure extends RuntimeException {
        ParseFailure(String message) {
            super(message, null, false, false);
        }
    }

    /** Bộ phân tích đệ quy xuống theo ngữ pháp ở Javadoc lớp. */
    private static final class Parser {
        private final String s;
        private final boolean strict;
        private int pos;
        private final List<String> warnings = new ArrayList<>();

        Parser(String s, boolean strict) {
            this.s = s;
            this.strict = strict;
        }

        ShortAnswerValue parseAll() {
            ShortAnswerValue v = value();
            if (pos != s.length()) {
                throw new ParseFailure(MSG_UNPARSEABLE);
            }
            return v;
        }

        private ShortAnswerValue value() {
            boolean negative = accept("-");
            ShortAnswerValue v = body();
            if (!negative) {
                return v;
            }
            if (strict && v.isZero()) {
                throw new ParseFailure(MSG_NEGATIVE_ZERO);
            }
            return v.negate();
        }

        private ShortAnswerValue body() {
            if (accept("\\frac{")) {
                ShortAnswerValue a = value();
                expect("}{");
                ShortAnswerValue b = value();
                expect("}");
                noteUnreduced(a, b);
                ShortAnswerValue q = a.dividedBy(b);
                boolean dot = accept("·");
                if (dot || peekSqrt()) {
                    if (!q.isRational()) {
                        throw new ParseFailure(MSG_UNPARSEABLE);
                    }
                    q = q.timesRadical(sqrt());
                }
                return q;
            }
            ShortAnswerValue t = term();
            if (accept("/")) {
                ShortAnswerValue d = peekSqrt() ? sqrt() : number();
                noteUnreduced(t, d);
                return t.dividedBy(d);
            }
            return t;
        }

        private ShortAnswerValue term() {
            if (peekSqrt()) {
                return sqrt();
            }
            ShortAnswerValue n = number();
            boolean dot = accept("·");
            if (dot || peekSqrt()) {
                return n.timesRadical(sqrt());
            }
            return n;
        }

        private ShortAnswerValue number() {
            String intPart = digits();
            if (intPart.isEmpty()) {
                throw new ParseFailure(MSG_UNPARSEABLE);
            }
            checkLeadingZero(intPart);
            if (pos < s.length() && (s.charAt(pos) == ',' || s.charAt(pos) == '.')) {
                char sep = s.charAt(pos++);
                String frac = digits();
                if (frac.isEmpty()) {
                    throw new ParseFailure(MSG_UNPARSEABLE);
                }
                // Phần nguyên là 0 (kể cả -0) thì không thể là phân cách hàng nghìn: 0.125 là thập phân.
                if (sep == '.' && frac.length() == 3 && !intPart.equals("0")) {
                    throw new ParseFailure(MSG_THOUSANDS);
                }
                return rational(new BigInteger(intPart + frac), BigInteger.TEN.pow(frac.length()));
            }
            return rational(new BigInteger(intPart), BigInteger.ONE);
        }

        private boolean peekSqrt() {
            return s.startsWith("\\sqrt", pos) || s.startsWith("√", pos);
        }

        private ShortAnswerValue sqrt() {
            String radicandDigits;
            if (accept("\\sqrt{") || accept("√{")) {
                radicandDigits = digits();
                expect("}");
            } else if (accept("√")) {
                radicandDigits = digits();
            } else {
                throw new ParseFailure(MSG_UNPARSEABLE);
            }
            if (radicandDigits.isEmpty()) {
                throw new ParseFailure(MSG_UNPARSEABLE);
            }
            checkLeadingZero(radicandDigits);
            BigInteger n = new BigInteger(radicandDigits);
            if (n.signum() == 0) {
                throw new ParseFailure(MSG_UNPARSEABLE);
            }
            if (n.compareTo(MAX_RADICAND) > 0) {
                throw new ParseFailure(MSG_RADICAND_TOO_LARGE);
            }
            return squareFree(n.longValueExact());
        }

        /** n = s²·f với f không chứa thừa số chính phương; trả s·√f. Thử chia tới √n &lt;= 10^6. */
        private static ShortAnswerValue squareFree(long n) {
            long outside = 1;
            long rest = n;
            for (long p = 2; p * p <= rest; p++) {
                long pp = p * p;
                while (rest % pp == 0) {
                    rest /= pp;
                    outside *= p;
                }
            }
            return new ShortAnswerValue(BigInteger.valueOf(outside), BigInteger.ONE, BigInteger.valueOf(rest));
        }

        private void noteUnreduced(ShortAnswerValue numerator, ShortAnswerValue denominator) {
            if (numerator.den.equals(BigInteger.ONE) && denominator.den.equals(BigInteger.ONE)
                    && denominator.isRational() && !numerator.isZero()
                    && numerator.num.abs().gcd(denominator.num.abs()).compareTo(BigInteger.ONE) > 0
                    && !warnings.contains(WARN_UNREDUCED_FRACTION)) {
                warnings.add(WARN_UNREDUCED_FRACTION);
            }
        }

        private void checkLeadingZero(String digits) {
            if (strict && digits.length() > 1 && digits.charAt(0) == '0') {
                throw new ParseFailure(MSG_LEADING_ZERO);
            }
        }

        private String digits() {
            int start = pos;
            while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
                pos++;
            }
            return s.substring(start, pos);
        }

        private boolean accept(String token) {
            if (s.startsWith(token, pos)) {
                pos += token.length();
                return true;
            }
            return false;
        }

        private void expect(String token) {
            if (!accept(token)) {
                throw new ParseFailure(MSG_UNPARSEABLE);
            }
        }
    }
}
