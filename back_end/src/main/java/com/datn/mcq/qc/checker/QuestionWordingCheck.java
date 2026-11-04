package com.datn.mcq.qc.checker;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Văn phong câu hỏi do LLM sinh (góp ý giáo viên Toán 3/11): thuật ngữ sai dễ gặp và hướng dẫn nhập đáp án trong câu dẫn của
 * câu Trả lời ngắn. Không tốn lượt gọi LLM nào; FormatChecker chỉ ghi cảnh báo (bật cờ "cần xem kỹ"), không loại câu.
 *
 * <p>Danh sách thuật ngữ sai là {@link #WRONG_TERMS}: thêm một dòng là đủ để bắt thêm một từ (viết chữ thường, các từ cách nhau
 * một dấu cách; khoảng trắng và xuống dòng giữa các từ đều khớp).
 */
public final class QuestionWordingCheck {

    /** Một thuật ngữ sai và cách viết đúng theo SGK, cả hai chữ thường. */
    public record WrongTerm(String wrong, String correct) {}

    /** Thuật ngữ sai thường gặp và thuật ngữ SGK tương ứng. */
    public static final List<WrongTerm> WRONG_TERMS = List.of(
            new WrongTerm("cạnh biên", "cạnh bên"));

    private static final String LETTER_BOUNDARY_BEFORE = "(?<![\\p{L}\\p{N}_])";
    private static final String LETTER_BOUNDARY_AFTER = "(?![\\p{L}\\p{N}_])";

    private static final List<Pattern> WRONG_PATTERNS = WRONG_TERMS.stream()
            .map(t -> Pattern.compile(LETTER_BOUNDARY_BEFORE + String.join("\\s+", t.wrong().split(" "))
                    + LETTER_BOUNDARY_AFTER, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .toList();

    /** "Nhập đáp án", "nhập đáp số", "nhập kết quả" đứng riêng (không phân biệt hoa thường). */
    private static final Pattern ANSWER_INPUT = Pattern.compile(
            LETTER_BOUNDARY_BEFORE + "nhập\\s+(?:đáp\\s+án|đáp\\s+số|kết\\s+quả)" + LETTER_BOUNDARY_AFTER,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Cụm chữ chỉ quan hệ giữa hai đối tượng đã đặt tên mà góp ý giáo viên 4/11 muốn viết bằng ký hiệu ($AD \perp BC$,
     * $AD \parallel BC$, $AD > BC$, $AD < BC$). Mỗi cụm đúng chữ thường, các từ cách nhau một dấu cách.
     */
    public static final List<String> SYMBOL_SUGGESTED_PHRASES = List.of("vuông góc với", "song song với", "lớn hơn", "nhỏ hơn");

    /** Tên đối tượng: 1 đến 3 chữ in hoa ASCII, mỗi chữ có thể kèm dấu phẩy trên ({@code A'B'}). */
    private static final String OBJECT_NAME = "(?:[A-Z]'?){1,3}";

    /** Mẫu "tên + cụm + tên" cho từng cụm của {@link #SYMBOL_SUGGESTED_PHRASES}; chỉ phần cụm không phân biệt hoa thường. */
    private static final List<Pattern> SYMBOL_PATTERNS = SYMBOL_SUGGESTED_PHRASES.stream()
            .map(p -> Pattern.compile(LETTER_BOUNDARY_BEFORE + OBJECT_NAME + "\\s+(?iu:" + String.join("\\s+", p.split(" "))
                    + ")\\s+" + OBJECT_NAME + LETTER_BOUNDARY_AFTER))
            .toList();

    private QuestionWordingCheck() {}

    /**
     * Cụm chữ chỉ quan hệ ({@link #SYMBOL_SUGGESTED_PHRASES}) đứng giữa hai tên đối tượng in hoa trong {@code text}, mỗi cụm một
     * lần, theo thứ tự danh sách; dấu "$" bị bỏ trước khi so nên "$AD$ vuông góc với $BC$" cũng khớp. Rỗng khi quan hệ đã viết
     * bằng ký hiệu hoặc không đứng giữa hai tên đối tượng.
     */
    public static List<String> symbolSuggestedPhrases(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String plain = Normalizer.normalize(text, Normalizer.Form.NFC).replace("$", "");
        List<String> found = new ArrayList<>();
        for (int i = 0; i < SYMBOL_SUGGESTED_PHRASES.size(); i++) {
            if (SYMBOL_PATTERNS.get(i).matcher(plain).find()) {
                found.add(SYMBOL_SUGGESTED_PHRASES.get(i));
            }
        }
        return found;
    }

    private static final String SYMBOL_SUGGESTED_TAG = "wording_symbol_suggested";

    /**
     * Cảnh báo gợi ý dùng ký hiệu cho trường {@code fieldName}, ví dụ
     * {@code option_a: wording_symbol_suggested (dùng chữ "vuông góc với" thay vì ký hiệu)}. Chỉ để đo: không bật cờ "cần xem
     * kỹ" ({@code QcSummaryService}).
     */
    public static String symbolSuggestedWarning(String fieldName, String phrase) {
        return fieldName + ": " + SYMBOL_SUGGESTED_TAG + " (dùng chữ \"" + phrase + "\" thay vì ký hiệu)";
    }

    /** True khi {@code warning} do {@link #symbolSuggestedWarning} tạo ra. */
    public static boolean isSymbolSuggestedWarning(String warning) {
        return warning != null && warning.matches("^[a-z_]+: " + SYMBOL_SUGGESTED_TAG + " \\(.*\\)$");
    }

    /** Các thuật ngữ sai có trong {@code text}, mỗi thuật ngữ một lần, theo thứ tự trong {@link #WRONG_TERMS}. */
    public static List<WrongTerm> wrongTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
        List<WrongTerm> found = new ArrayList<>();
        for (int i = 0; i < WRONG_TERMS.size(); i++) {
            if (WRONG_PATTERNS.get(i).matcher(nfc).find()) {
                found.add(WRONG_TERMS.get(i));
            }
        }
        return found;
    }

    /** True khi {@code text} có hướng dẫn nhập đáp án như "(Nhập đáp án dưới dạng…)". */
    public static boolean hasAnswerInputHint(String text) {
        return text != null && ANSWER_INPUT.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).find();
    }

    /** Cảnh báo cho một trường, ví dụ {@code question_text: dùng sai thuật ngữ "cạnh biên" (SGK: "cạnh bên")}. */
    public static String wrongTermWarning(String fieldName, WrongTerm term) {
        return fieldName + ": dùng sai thuật ngữ \"" + term.wrong() + "\" (SGK: \"" + term.correct() + "\")";
    }

    /** Cảnh báo hướng dẫn nhập đáp án ở trường {@code fieldName}. */
    public static String answerInputWarning(String fieldName) {
        return fieldName + ": có hướng dẫn nhập đáp án trong câu dẫn";
    }
}
