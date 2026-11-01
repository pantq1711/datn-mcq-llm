package com.datn.mcq.qc.checker;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Phát hiện lời giải còn phần nháp hoặc tự sửa (không tốn lượt gọi LLM nào). Thẩm định đề 5-7 (31/10): câu 82 có lời giải
 * tự viết "Xin lỗi, tính lại…" rồi đổi dữ kiện của đề. Áp cho cả 3 dạng câu; {@link TfExplanationLeak} vẫn lo riêng các ca
 * của câu Đúng/Sai (nhắc tới mẫu nhãn).
 *
 * <p>Hai nhánh, đều so không phân biệt hoa thường và dấu (judge từng trả tiếng Việt không dấu):
 * <ul>
 *   <li>{@link #detect}: cụm chắc chắn là nháp, FormatChecker chặn đầu ra LLM: "xin lỗi", "sorry", "wait", "nhầm" dạng hẹp,
 *       "à không".
 *       <ul>
 *         <li>"nhầm": chỉ khớp "tôi nhầm", "em nhầm", "bị nhầm" (không khi theo sau là "lẫn") hoặc "nhầm" ngay trước dấu ","
 *             hay ".". "nhầm lẫn thường gặp" và "nhầm" đứng giữa câu (ví dụ "dễ nhầm với nhau") không khớp.</li>
 *         <li>"à không": so có dấu và đứng riêng một từ, vì bỏ dấu thì trùng "a không âm" (a là biến) và có dấu mà không đứng
 *             riêng thì trùng "mà không", "và không".</li>
 *       </ul></li>
 *   <li>{@link #suspect}: "tính lại", "làm lại", "sửa lại" cũng gặp trong lời giải bình thường (ví dụ "thử tính lại bằng cách
 *       khác"), nên không chặn; FormatChecker chỉ ghi {@link #SUSPECT_WARNING} và QcSummaryService bật lý do
 *       {@code explanation_draft_suspect}. Lời giải đã bị {@link #detect} bắt thì không tính là nghi ngờ.</li>
 * </ul>
 */
public final class ExplanationDraftLeak {

    public static final String MESSAGE = "Lời giải còn chứa phần nháp/tự sửa; chỉ ghi lời giải cuối cùng.";

    /** Cảnh báo (không chặn) khi lời giải có "tính lại", "làm lại" hoặc "sửa lại"; QcSummaryService đổi thành mã riêng. */
    public static final String SUSPECT_WARNING = "explanation: lời giải có thể còn phần nháp (tính lại, làm lại, sửa lại)";

    /** Đã bỏ dấu, chữ thường: cụm chắc chắn là nháp. */
    private static final Pattern FOLDED = Pattern.compile(String.join("|",
            "\\bxin\\s+loi\\b",
            "\\bwait\\b",
            "\\bsorry\\b",
            "\\b(?:toi|em)\\s+nham\\b",
            "\\bbi\\s+nham\\b(?!\\s+lan)",
            "\\bnham[,.]"));

    /** Chữ thường, còn dấu (NFC): cụm chắc chắn là nháp. */
    private static final Pattern ACCENTED = Pattern.compile("(?<![\\p{L}\\p{N}])à\\s+không(?![\\p{L}\\p{N}])");

    /** Đã bỏ dấu, chữ thường: cụm có thể là nháp, có thể là lời giải bình thường. */
    private static final Pattern SUSPECT = Pattern.compile("\\b(?:tinh|lam|sua)\\s+lai\\b");

    private ExplanationDraftLeak() {}

    /** True khi lời giải chứa cụm chắc chắn là nháp; false khi null, trống hoặc sạch. */
    public static boolean detect(String explanation) {
        if (explanation == null || explanation.isBlank()) {
            return false;
        }
        String nfc = Normalizer.normalize(explanation.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        return ACCENTED.matcher(nfc).find() || FOLDED.matcher(TfExplanationLeak.fold(explanation)).find();
    }

    /** True khi lời giải chỉ có cụm đáng nghi ("tính lại", "làm lại", "sửa lại"), không có cụm chắc chắn là nháp. */
    public static boolean suspect(String explanation) {
        if (explanation == null || explanation.isBlank() || detect(explanation)) {
            return false;
        }
        return SUSPECT.matcher(TfExplanationLeak.fold(explanation)).find();
    }
}
