package com.datn.mcq.qc.checker;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Phát hiện lời giải câu Đúng/Sai của LLM "tự thú nhận" (không tốn lượt gọi LLM nào). Đo baseline 26/10 (27 lần sinh tf): 6 lần
 * lời giải nhắc tới mẫu hay yêu cầu gán nhãn ("đúng nhưng theo mẫu yêu cầu gán nhãn F") hoặc tự sửa đề giữa lời giải ("Xin lỗi,
 * ta làm lại biểu thức"), tức model gán nhãn trái toán học để theo mẫu. Câu như vậy bị loại ở bước kiểm định dạng và sinh lại.
 *
 * <p>Hai nhóm cụm, so không phân biệt dấu và hoa thường (judge từng trả tiếng Việt không dấu); các cụm lấy từ 6 ca thật ở
 * baseline. Không dùng chữ "mẫu" trơn vì "mẫu số" là từ toán thật. Chỉ bắt được trường hợp tự thú nhận; ca sai nhãn mà lời giải
 * im lặng (5 trong 27 lần sinh ở baseline) vẫn chỉ Solver bắt được.
 */
public final class TfExplanationLeak {

    public enum Kind { PATTERN_MENTION, SELF_REVISION }

    static final String PATTERN_MESSAGE = "explanation: lời giải nhắc tới mẫu nhãn hoặc việc gán nhãn theo yêu cầu; nhãn phải "
            + "theo đúng/sai thật của từng ý";
    static final String REVISION_MESSAGE = "explanation: lời giải tự sửa lại đề hoặc ý (xin lỗi, làm lại, chỉnh lại); hãy viết "
            + "lời giải cuối cùng nhất quán với các ý";

    /** Nhắc tới mẫu nhãn hoặc yêu cầu gán nhãn (đã bỏ dấu, chữ thường). */
    private static final Pattern PATTERN_MENTION = Pattern.compile(String.join("|",
            "\\bmau\\s+nhan\\b",
            "\\bnhan\\s+(?:yeu\\s+cau|muc\\s+tieu|bat\\s+buoc)\\b",
            "\\byeu\\s+cau\\s+(?:mau|gan\\s+nhan|nhan)\\b",
            "\\bmau\\s+(?:bat\\s+buoc|yeu\\s+cau|muc\\s+tieu)\\b",
            "\\bgan\\s+nhan\\b",
            "\\bmau\\s*[(]?\\s*[tf]\\s*,\\s*[tf]\\b"));

    /** Tự sửa đề hoặc ý ngay trong lời giải (đã bỏ dấu, chữ thường). */
    private static final Pattern SELF_REVISION = Pattern.compile(String.join("|",
            "\\bxin\\s+loi\\b",
            "\\bta\\s+(?:lam|chinh|sua|doi)\\s+lai\\b",
            "\\b(?:chinh|sua)\\s+lai\\s+(?:phat\\s+bieu|khang\\s+dinh|de\\s+bai|bieu\\s+thuc|y\\s+[a-d]\\b)",
            "\\bxet\\s+lai\\s*:"));

    private TfExplanationLeak() {}

    /** Các nhóm cụm xuất hiện trong lời giải; rỗng khi lời giải null, trống hoặc sạch. */
    public static Set<Kind> detect(String explanation) {
        Set<Kind> found = EnumSet.noneOf(Kind.class);
        if (explanation == null || explanation.isBlank()) {
            return found;
        }
        String folded = fold(explanation);
        if (PATTERN_MENTION.matcher(folded).find()) {
            found.add(Kind.PATTERN_MENTION);
        }
        if (SELF_REVISION.matcher(folded).find()) {
            found.add(Kind.SELF_REVISION);
        }
        return found;
    }

    /** Lỗi định dạng (mỗi nhóm một dòng, bắt đầu bằng "explanation: ") cho FormatChecker; rỗng khi lời giải sạch. */
    public static List<String> errors(String explanation) {
        Set<Kind> kinds = detect(explanation);
        List<String> errors = new ArrayList<>(2);
        if (kinds.contains(Kind.PATTERN_MENTION)) {
            errors.add(PATTERN_MESSAGE);
        }
        if (kinds.contains(Kind.SELF_REVISION)) {
            errors.add(REVISION_MESSAGE);
        }
        return errors;
    }

    /** Chữ thường, bỏ dấu tiếng Việt (kể cả chuỗi dấu rời NFD), đ thành d. */
    static String fold(String s) {
        String decomposed = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replace('đ', 'd');
    }
}
