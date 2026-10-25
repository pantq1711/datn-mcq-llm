package com.datn.mcq.llm.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mẫu nhãn bắt buộc cho câu Đúng/Sai trong đề theo ma trận (ngày 23/10): hệ thống chọn ngẫu nhiên trong các mẫu có 1–3
 * ý Đúng, tránh trùng mẫu của câu tf gần nhất cùng đề, ghi vào prompt bằng {@link #promptLine(String)}. Mẫu được lưu
 * trong generation_job.prompt_note (đọc lại bằng {@link #fromNote(String)}). Nhãn trả về khác mẫu chỉ là cảnh báo
 * ({@link #mismatchWarning(String, List)}), không loại câu.
 *
 * <p>Mẫu viết bằng 4 ký tự T/F theo thứ tự a-d, ví dụ "TFFT".
 */
public final class TfLabelPattern {

    /** 14 mẫu có từ 1 đến 3 ý Đúng (bỏ TTTT và FFFF). */
    public static final List<String> ALL = allPatterns();

    private static final List<String> KEYS = List.of("a", "b", "c", "d");
    private static final String LINE_PREFIX = "Mẫu nhãn bắt buộc (";
    private static final Pattern IN_NOTE = Pattern.compile("Mẫu nhãn bắt buộc \\(([TF]),([TF]),([TF]),([TF])\\)");

    private TfLabelPattern() {}

    private static List<String> allPatterns() {
        List<String> out = new ArrayList<>();
        for (int mask = 0; mask < 16; mask++) {
            int trues = Integer.bitCount(mask);
            if (trues == 0 || trues == 4) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i >= 0; i--) {
                sb.append((mask >> i & 1) == 1 ? 'T' : 'F');
            }
            out.add(sb.toString());
        }
        return List.copyOf(out);
    }

    /** Một mẫu ngẫu nhiên khác {@code avoid} (null thì chọn trong cả 14 mẫu). */
    public static String choose(String avoid, RandomGenerator random) {
        List<String> pool = ALL.stream().filter(p -> !p.equals(avoid)).toList();
        return pool.get(random.nextInt(pool.size()));
    }

    /** Dòng ghi chú đưa vào prompt, ví dụ "Mẫu nhãn bắt buộc (T,F,F,T): ý a phải Đúng, ý b phải Sai, ...". */
    public static String promptLine(String pattern) {
        StringBuilder sb = new StringBuilder(LINE_PREFIX).append(String.join(",", pattern.split(""))).append("): ");
        for (int i = 0; i < 4; i++) {
            sb.append(i > 0 ? ", " : "").append("ý ").append(KEYS.get(i)).append(" phải ")
                    .append(pattern.charAt(i) == 'T' ? "Đúng" : "Sai");
        }
        return sb.append(". Soạn nội dung và lời giải từng ý cho khớp mẫu này.").toString();
    }

    /**
     * Mẫu ghi trong ghi chú (hoặc prompt) của job; null khi không có. Chỉ đọc phần hệ thống viết, bỏ qua khối yêu cầu
     * bổ sung của giáo viên.
     */
    public static String fromNote(String note) {
        if (note == null) {
            return null;
        }
        Matcher m = IN_NOTE.matcher(MatrixPromptDraftV0.systemPart(note));
        return m.find() ? m.group(1) + m.group(2) + m.group(3) + m.group(4) : null;
    }

    /**
     * Cảnh báo khi 4 nhãn LLM trả về khác mẫu bắt buộc, ví dụ "label: khác mẫu bắt buộc T,F,F,T (nhận T,T,F,F)"; null khi
     * khớp, khi không có mẫu, hoặc khi nhãn thiếu hay không phải T/F (đã là lỗi format).
     */
    public static String mismatchWarning(String pattern, List<String> labels) {
        if (pattern == null || labels == null || labels.size() != 4) {
            return null;
        }
        StringBuilder got = new StringBuilder();
        for (String label : labels) {
            String l = label == null ? "" : label.strip().toUpperCase(java.util.Locale.ROOT);
            if (!l.equals("T") && !l.equals("F")) {
                return null;
            }
            got.append(l);
        }
        if (got.toString().equals(pattern)) {
            return null;
        }
        return "label: khác mẫu bắt buộc " + String.join(",", pattern.split("")) + " (nhận "
                + String.join(",", got.toString().split("")) + ")";
    }
}
