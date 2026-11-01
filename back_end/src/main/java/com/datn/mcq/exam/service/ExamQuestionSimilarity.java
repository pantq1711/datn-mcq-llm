package com.datn.mcq.exam.service;

import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.qc.checker.McqTextNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Lớp 2 chống câu cùng khuôn trong MỘT đề (thẩm định 31/10): tìm các cặp câu của cùng một đề giống nhau về khuôn, chỉ khác
 * số liệu hoặc tên điểm/hình. Hàm thuần trên danh sách câu theo thứ tự hiển thị: không Spring, DB hay LLM.
 *
 * <p>Khác dedup của ngân hàng và của câu sinh (quy tắc "câu chỉ khác số liệu không tính là trùng" giữ nguyên ở
 * {@code DedupChecker}): ở đây số liệu và tên điểm bị che đi nên câu cùng khuôn mới giống nhau. Kết quả chỉ để gắn cờ
 * "cần xem kỹ" khi xem đề, không loại câu, không đổi trạng thái, không lưu DB.
 *
 * <p>Chuẩn hóa ({@link #pattern}): bỏ ký hiệu {@code $} của công thức (khoảng trắng trong công thức đã bỏ bởi
 * {@link McqTextNormalizer#normalize}), thay mọi số bằng {@code #}, thay cụm 2-5 chữ in hoa (tên điểm, tên hình) bằng
 * {@code @}, chữ thường, gộp khoảng trắng. Độ giống là cosine 3-gram ký tự ({@link McqTextNormalizer#trigramCosine}).
 *
 * <p>Phạm vi so: câu trắc nghiệm với câu trắc nghiệm (thân câu và tập phương án), và từng câu trắc nghiệm với từng ý của câu
 * Đúng/Sai (bắt trường hợp câu trắc nghiệm làm lộ đáp án của một ý). Ý đúng/sai được so với thân câu trắc nghiệm và với
 * thân câu ghép từng phương án, vì ý thường đã chứa sẵn biểu thức kết quả.
 */
public final class ExamQuestionSimilarity {

    /**
     * Ngưỡng mặc định (cấu hình {@code app.exam.similar-threshold}). 0,82 chọn trên 4 đề của datn_mcq_e2e (31/10), ưu tiên
     * recall vì lớp này chỉ gắn cờ; xem dev-log L-55.
     */
    public static final double DEFAULT_THRESHOLD = 0.82;
    /** Tiền tố lý do "cần xem kỹ", theo sau là số thứ tự (1, 2, ...) của câu kia trong đề. */
    public static final String REASON_PREFIX = "similar_in_exam: ";

    /** Cụm 2-5 chữ in hoa đứng riêng (không dính chữ cái khác): tên điểm, tên hình như WXYZ, PQRS, AB. */
    private static final Pattern POINT_NAME = Pattern.compile("(?<!\\p{L})\\p{Lu}{2,5}(?!\\p{L})");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\{,\\}\\d+|[.,]\\d+)?");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ExamQuestionSimilarity() {}

    /** Văn bản đã chuẩn hóa về khuôn; xem mô tả lớp. */
    public static String pattern(String text) {
        if (text == null) {
            return "";
        }
        String s = McqTextNormalizer.normalize(text).replace('$', ' ');
        s = POINT_NAME.matcher(s).replaceAll("@");
        s = NUMBER.matcher(s).replaceAll("#");
        return WHITESPACE.matcher(s.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /**
     * @param items     các chỗ của đề theo thứ tự hiển thị (phần I, II, III); chỗ chưa có câu vẫn chiếm một vị trí
     * @param threshold độ giống 3-gram tối thiểu để coi là cùng khuôn
     * @return vị trí (từ 1) của câu → vị trí (từ 1, tăng dần) của các câu khác cùng khuôn với nó; cả hai phía của một cặp
     *         đều có mặt; câu không có cặp nào không có trong kết quả
     */
    public static Map<Integer, List<Integer>> findSimilar(List<ExamItemDto> items, double threshold) {
        Map<Integer, List<Integer>> out = new TreeMap<>();
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                if (similar(items.get(i), items.get(j), threshold)) {
                    out.computeIfAbsent(i + 1, k -> new ArrayList<>()).add(j + 1);
                    out.computeIfAbsent(j + 1, k -> new ArrayList<>()).add(i + 1);
                }
            }
        }
        return out;
    }

    private static boolean similar(ExamItemDto a, ExamItemDto b, double threshold) {
        return score(a, b) >= threshold;
    }

    /** Độ giống của một cặp trong phạm vi so (mcq-mcq, mcq-ý đúng/sai); -1 khi cặp không thuộc phạm vi. */
    static double score(ExamItemDto a, ExamItemDto b) {
        if (isMcq(a) && isMcq(b)) {
            return McqTextNormalizer.trigramCosine(mcqPattern(a), mcqPattern(b));
        }
        if (isMcq(a) && isTf(b)) {
            return leakScore(a, b);
        }
        if (isTf(a) && isMcq(b)) {
            return leakScore(b, a);
        }
        return -1.0;
    }


    private static boolean isMcq(ExamItemDto item) {
        return item.questionType() == QuestionType.MCQ && item.questionText() != null && item.optionA() != null
                && item.optionB() != null && item.optionC() != null && item.optionD() != null;
    }

    private static boolean isTf(ExamItemDto item) {
        return item.questionType() == QuestionType.TF && item.statements() != null && !item.statements().isEmpty();
    }

    /** Thân câu và tập phương án đã sắp xếp, như văn bản chuẩn của dedup nhưng đã che số và tên điểm. */
    private static String mcqPattern(ExamItemDto item) {
        List<String> options = new ArrayList<>(options(item));
        options.sort(null);
        return pattern(item.questionText()) + " || " + String.join(" | ", options);
    }

    private static List<String> options(ExamItemDto item) {
        return List.of(pattern(item.optionA()), pattern(item.optionB()), pattern(item.optionC()), pattern(item.optionD()));
    }

    /**
     * Độ giống cao nhất giữa một ý của câu Đúng/Sai và thân câu trắc nghiệm (hoặc thân câu ghép một phương án).
     */
    private static double leakScore(ExamItemDto mcq, ExamItemDto tf) {
        String stem = pattern(mcq.questionText());
        List<String> withOption = new ArrayList<>();
        for (String option : options(mcq)) {
            withOption.add(stem + " " + option);
        }
        double best = 0.0;
        for (ItemStatementDto statement : tf.statements()) {
            String s = pattern(statement.text());
            best = Math.max(best, McqTextNormalizer.trigramCosine(stem, s));
            for (String candidate : withOption) {
                best = Math.max(best, McqTextNormalizer.trigramCosine(candidate, s));
            }
        }
        return best;
    }
}
