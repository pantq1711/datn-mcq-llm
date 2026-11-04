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

    private QuestionWordingCheck() {}

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
