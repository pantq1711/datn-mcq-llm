package com.datn.mcq.assignment.service;

import com.datn.mcq.common.util.ShortAnswerValue;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.TruthValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Chấm một câu của bài làm trực tuyến (docs/design/lam-bai-truc-tuyen.md mục 4). Hàm thuần: nơi gọi đã kiểm dạng dữ liệu
 * (lựa chọn thuộc A-D, khóa ý thuộc a-d, giá trị ý là T/F).
 *
 * <ul>
 *   <li>Trắc nghiệm: đúng khi lựa chọn trùng đáp án; bỏ trống là sai.</li>
 *   <li>Đúng/Sai: mỗi ý đúng khi trùng nhãn, bỏ trống là sai; điểm câu theo SỐ Ý ĐÚNG ({@link Scoring#tfScore}).</li>
 *   <li>Trả lời ngắn: so theo giá trị bằng {@link ShortAnswerValue} chế độ rộng (dấu phẩy và dấu chấm thập phân, dấu trừ
 *       U+2212, khoảng trắng); không phân tích được thành một số là sai.</li>
 * </ul>
 */
public final class AttemptGrader {

    static final List<String> STATEMENT_KEYS = List.of("a", "b", "c", "d");
    static final char BLANK = '-';

    private AttemptGrader() {}

    /**
     * Kết quả chấm một câu, đúng dạng lưu ở {@code attempt_answer}.
     *
     * @param mcqChoice    lựa chọn A-D (chỉ mcq); null khi bỏ trống
     * @param tfChoices    4 ký tự theo ý a-d, mỗi ký tự T, F hoặc '-' (chỉ tf)
     * @param saText       nguyên văn học sinh gõ, đã trim (chỉ sa); null khi bỏ trống
     * @param correctCount mcq, sa: 0 hoặc 1; tf: số ý đúng 0..4
     */
    public record Graded(String mcqChoice, String tfChoices, String saText, int correctCount, BigDecimal score) {}

    public static Graded mcq(Option key, String choice, Scoring scoring) {
        boolean correct = choice != null && key != null && key.getDbValue().equals(choice);
        return new Graded(choice, null, null, correct ? 1 : 0, correct ? scoring.mcq() : BigDecimal.ZERO);
    }

    /**
     * @param labels  nhãn của bốn ý theo thứ tự a, b, c, d
     * @param answers trả lời của học sinh theo khóa ý ("a".."d" → "T"/"F"); thiếu khóa là bỏ trống; null là bỏ cả câu
     */
    public static Graded tf(List<TruthValue> labels, Map<String, String> answers, Scoring scoring) {
        StringBuilder choices = new StringBuilder(4);
        int correct = 0;
        for (int i = 0; i < STATEMENT_KEYS.size(); i++) {
            String value = answers == null ? null : answers.get(STATEMENT_KEYS.get(i));
            if (value == null) {
                choices.append(BLANK);
                continue;
            }
            choices.append(value);
            if (labels.get(i).getDbValue().equals(value)) {
                correct++;
            }
        }
        return new Graded(null, choices.toString(), null, correct, scoring.tfScore(correct));
    }

    public static Graded sa(String key, String text, Scoring scoring) {
        String typed = text == null || text.isBlank() ? null : text.strip();
        boolean correct = typed != null && sameValue(key, typed);
        return new Graded(null, null, typed, correct ? 1 : 0, correct ? scoring.sa() : BigDecimal.ZERO);
    }

    private static boolean sameValue(String key, String typed) {
        ShortAnswerValue.ParseResult expected = ShortAnswerValue.parse(key, ShortAnswerValue.Mode.LENIENT);
        ShortAnswerValue.ParseResult actual = ShortAnswerValue.parse(typed, ShortAnswerValue.Mode.LENIENT);
        return expected.ok() && actual.ok() && expected.value().sameValue(actual.value());
    }
}
