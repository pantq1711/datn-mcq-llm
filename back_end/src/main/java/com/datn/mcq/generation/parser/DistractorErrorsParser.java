package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.LatexValidationRules;
import com.datn.mcq.common.util.LetterJson;
import com.datn.mcq.enums.Option;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Giải mã trường {@code distractor_errors} của câu {@code mcq} (13/11, PA1, docs/design/phuong-an-nhieu-loi-sai.md mục 3, H-5):
 * mỗi phương án sai một câu mô tả lỗi sai của học sinh, phương án đúng {@code null}. Không bao giờ làm câu hỏng: thiếu hay
 * hỏng chỉ ghi cảnh báo có tiền tố {@link LlmOutputRepair#ESCAPE_WARNING_PREFIX} (nhóm tự sửa, không bật "cần xem kỹ").
 */
public final class DistractorErrorsParser {

    /** Tên trường trong JSON sinh câu. */
    public static final String FIELD = "distractor_errors";

    /** Quá số từ này thì cảnh báo (vẫn giữ chữ). */
    public static final int SOFT_MAX_WORDS = 15;

    /** Quá số từ này thì bỏ lỗi (không cắt chữ). */
    public static final int HARD_MAX_WORDS = 30;

    static final String W = LlmOutputRepair.ESCAPE_WARNING_PREFIX;

    /** Cảnh báo khi thiếu hẳn trường hoặc trường không phải đối tượng. */
    public static final String MISSING_WARNING = W + "thiếu " + FIELD + " (không có phân tích phương án nhiễu)";

    private DistractorErrorsParser() {}

    /**
     * @param raw      giá trị của trường trong JSON đã giải mã (có thể null)
     * @param correct  đáp án đúng đã giải mã; null thì không xét phương án đúng
     * @param repair   chế độ sửa (run production): sửa escape như trường văn bản khác và ghi cảnh báo; false thì chỉ đọc
     * @param expected prompt có yêu cầu trường này; false thì thiếu trường không ghi cảnh báo
     * @param warnings nơi ghi cảnh báo
     * @return map A-D (đủ 4 khóa, phương án không có lỗi là null); null khi thiếu hẳn trường hoặc không có lỗi nào dùng được
     */
    public static Map<String, String> parse(Object raw, Option correct, boolean repair, boolean expected,
                                            List<String> warnings) {
        if (!(raw instanceof Map<?, ?> obj)) {
            if (repair && expected) {
                warnings.add(MISSING_WARNING);
            }
            return null;
        }
        String correctLetter = correct == null ? null : correct.getDbValue();
        Map<String, String> out = new LinkedHashMap<>();
        boolean any = false;
        for (String letter : LetterJson.LETTERS) {
            String text = clean(letter, obj.get(letter), repair);
            if (letter.equals(correctLetter)) {
                if (text != null && repair) {
                    warnings.add(W + FIELD + " bỏ lỗi ghi ở phương án đúng " + letter);
                }
                out.put(letter, null);
                continue;
            }
            if (!repair) {
                out.put(letter, text);
                any |= text != null;
                continue;
            }
            if (text == null) {
                warnings.add(W + FIELD + " thiếu lỗi của phương án " + letter);
                out.put(letter, null);
                continue;
            }
            if (brokenLatex(text)) {
                warnings.add(W + FIELD + " bỏ lỗi của phương án " + letter + " vì công thức hỏng");
                out.put(letter, null);
                continue;
            }
            int words = wordCount(text);
            if (words > HARD_MAX_WORDS) {
                warnings.add(W + FIELD + " bỏ lỗi của phương án " + letter + " vì dài " + words + " từ (quá "
                        + HARD_MAX_WORDS + ")");
                out.put(letter, null);
                continue;
            }
            if (words > SOFT_MAX_WORDS) {
                warnings.add(W + FIELD + " phương án " + letter + " dài " + words + " từ (quá " + SOFT_MAX_WORDS + ")");
            }
            out.put(letter, text);
            any = true;
        }
        return any ? out : null;
    }

    /** Chuỗi đã trim (chế độ sửa: qua cùng bước sửa escape và "$" của trường văn bản); rỗng hoặc không phải chuỗi thì null. */
    private static String clean(String letter, Object value, boolean repair) {
        if (!(value instanceof String s)) {
            return null;
        }
        // Cảnh báo của bước sửa chung không ghi lại: lỗi chỉ là phần phụ của câu, công thức còn hỏng thì bỏ ở bước sau.
        String text = LlmOutputRepair.textField("distractor_" + letter.toLowerCase(java.util.Locale.ROOT), s, repair,
                false, new ArrayList<>());
        text = text == null ? null : text.replaceAll("\\s+", " ").strip();
        return text == null || text.isEmpty() ? null : text;
    }

    /** Công thức hỏng (lẻ "$", lệnh không hỗ trợ...): chữ lỗi được in ra Word nên không để làm hỏng pandoc. */
    static boolean brokenLatex(String text) {
        LatexValidationRules.Result r = LatexValidationRules.check(text);
        return !r.errors().isEmpty() || !r.warnings().isEmpty();
    }

    /** Số từ theo khoảng trắng; một công thức {@code $...$} liền nhau tính là một từ. */
    public static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        boolean inWord = false;
        boolean inMath = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && text.charAt(i + 1) == '$') {
                if (!inWord) {
                    count++;
                    inWord = true;
                }
                i++;
                continue;
            }
            if (c == '$') {
                if (!inMath && !inWord) {
                    count++;
                }
                inMath = !inMath;
                inWord = true;
                continue;
            }
            if (Character.isWhitespace(c) && !inMath) {
                inWord = false;
                continue;
            }
            if (!inWord) {
                count++;
                inWord = true;
            }
        }
        return count;
    }
}
