package com.datn.mcq.llm.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mẫu nhãn MỤC TIÊU cho câu Đúng/Sai trong đề theo ma trận: hệ thống chọn ngẫu nhiên trong các mẫu có 1–3 ý Đúng, tránh
 * trùng mẫu của câu tf gần nhất cùng đề, ghi vào prompt bằng {@link #promptLine(String)}. Mẫu được lưu trong
 * generation_job.prompt_note (đọc lại bằng {@link #fromNote(String)}). Nhãn trả về khác mẫu chỉ là cảnh báo
 * ({@link #mismatchWarning(String, List)}), không loại câu.
 *
 * <p>Ngày 23/10 mẫu là "bắt buộc" để hệ thống quyết định phân bố đáp án (model tự chọn thì thiên về TTFF). Đo ngày 26/10
 * (20 chỗ tf): lệnh "phải" làm model gán nhãn theo mẫu bất chấp toán học (11/27 lần sinh có nhãn sai thật, 6 lần lời giải tự
 * thú nhận gán nhãn theo yêu cầu). Nên mẫu giờ là mục tiêu: nhãn phải theo đúng/sai THẬT của ý, được lệch mẫu khi không viết
 * được ý hợp lý (chỉ cảnh báo).
 *
 * <p>Mẫu viết bằng 4 ký tự T/F theo thứ tự a-d, ví dụ "TFFT".
 */
public final class TfLabelPattern {

    /** 14 mẫu có từ 1 đến 3 ý Đúng (bỏ TTTT và FFFF). */
    public static final List<String> ALL = allPatterns();

    private static final List<String> KEYS = List.of("a", "b", "c", "d");
    private static final String LINE_PREFIX = "Mẫu nhãn mục tiêu (";
    /** Nhận cả tiền tố cũ "bắt buộc": job đã xếp hàng và dữ liệu trước khi đổi chữ vẫn đọc được mẫu. */
    private static final Pattern IN_NOTE = Pattern.compile(
            "Mẫu nhãn (?:mục tiêu|bắt buộc) \\(([TF]),([TF]),([TF]),([TF])\\)");

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

    /**
     * Dòng ghi chú đưa vào prompt, ví dụ "Mẫu nhãn mục tiêu (T,F,F,T): cố gắng viết ý a Đúng, ý b Sai, ...". Mẫu chỉ là mục
     * tiêu: nhãn phải khớp đúng/sai thật, muốn ý Sai thì viết khẳng định sai, được lệch mẫu khi không viết được ý hợp lý.
     */
    public static String promptLine(String pattern) {
        StringBuilder sb = new StringBuilder(LINE_PREFIX).append(String.join(",", pattern.split(""))).append("): ")
                .append("cố gắng viết ");
        for (int i = 0; i < 4; i++) {
            sb.append(i > 0 ? ", " : "").append("ý ").append(KEYS.get(i)).append(' ')
                    .append(pattern.charAt(i) == 'T' ? "Đúng" : "Sai");
        }
        return sb.append(". Nhãn phải khớp tính đúng/sai THẬT của khẳng định: muốn một ý Sai thì viết khẳng định sai (đổi hệ số, "
                        + "dấu hoặc kết luận so với kết quả đúng), muốn một ý Đúng thì chỉ nêu giá trị đã tính đúng. "
                        + "Nếu không viết được một ý theo đúng nhãn mục tiêu một cách hợp lý, hãy ưu tiên tính đúng và gán "
                        + "nhãn thật cho ý đó (được lệch mẫu). Không gán nhãn trái với lời giải; lời giải không được nhắc tới "
                        + "mẫu nhãn hay việc gán nhãn theo yêu cầu.")
                .toString();
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

    private static final String DEVIATION_PREFIX = "label: khác mẫu mục tiêu ";
    /** Tiền tố trước 26/10, còn trong dữ liệu cũ. */
    private static final String OLD_DEVIATION_PREFIX = "label: khác mẫu bắt buộc ";

    /**
     * Cảnh báo là "nhãn khác mẫu mục tiêu" (kể cả dòng cũ ghi "bắt buộc"). Lệch mẫu vì tính đúng là hợp lệ nên cảnh báo này chỉ
     * để thống kê mức tuân thủ, không bật "cần xem kỹ".
     */
    public static boolean isDeviationWarning(String warning) {
        return warning != null && (warning.startsWith(DEVIATION_PREFIX) || warning.startsWith(OLD_DEVIATION_PREFIX));
    }

    /**
     * Cảnh báo khi 4 nhãn LLM trả về khác mẫu mục tiêu, ví dụ "label: khác mẫu mục tiêu T,F,F,T (nhận T,T,F,F)"; null khi
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
        return DEVIATION_PREFIX + String.join(",", pattern.split("")) + " (nhận "
                + String.join(",", got.toString().split("")) + ")";
    }
}
