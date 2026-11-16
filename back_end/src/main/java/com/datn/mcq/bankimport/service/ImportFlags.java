package com.datn.mcq.bankimport.service;

import java.util.Set;

/**
 * Cờ của câu trong bản xem trước nhập từ Word và lý do bỏ câu. Cờ CHẶN làm câu không chọn được khi xác nhận (422
 * IMPORT_ITEM_BLOCKED); cờ thông tin chỉ để giáo viên biết. Bản đầu không có sửa câu trong bản xem trước: câu có cờ chặn
 * phải sửa trong file Word rồi tải lại.
 */
public final class ImportFlags {

    // ---------- lý do bỏ câu (không vào bản xem trước) ----------
    /** Câu có ảnh, hình vẽ, ảnh công thức hoặc đối tượng vẽ (bản đầu chỉ nhập câu không cần ảnh). */
    public static final String SKIP_HAS_IMAGE = "has_image";
    /** Câu có công thức MathType chưa chuyển được sang LaTeX. */
    public static final String SKIP_FORMULA_UNSUPPORTED = "formula_unsupported";
    /** Câu có bảng dữ liệu trong đề (ngân hàng chưa lưu được bảng). */
    public static final String SKIP_HAS_TABLE = "has_table";

    // ---------- cờ chặn ----------
    /**
     * Câu không có nhãn mức. 16/11 (NV-51): không còn xuất hiện trong bản xem trước mới, vì file có câu thiếu nhãn thì bắt buộc
     * chọn mức mặc định lúc tải lên (400 IMPORT_LEVEL_DEFAULT_REQUIRED) và câu đó nhận cờ thông tin {@link #LEVEL_DEFAULT}.
     */
    public static final String LEVEL_MISSING = "level_missing";
    /** Đáp số câu sa không đạt STRICT (số nguyên hoặc thập phân tối đa 4 ký tự). */
    public static final String SA_ANSWER_FORMAT = "sa_answer_format";
    /** Lỗi LaTeX (LatexValidationRules) ở đề, phương án, ý hoặc đáp số. */
    public static final String LATEX_ERROR = "latex_error";
    /** Đề, phương án hoặc ý bị trống. */
    public static final String EMPTY_FIELD = "structure_empty_field";
    /** Có chuỗi giống tên tác giả bộ đề (cắt từ tên file) hoặc dòng quảng cáo/liên kết trong nội dung câu. */
    public static final String LEAK_SUSPECT = "leak_suspect";

    // ---------- cờ thông tin ----------
    /** 16/11 (NV-51): câu không ghi nhãn mức trong file, nhận mức mặc định giáo viên chọn lúc tải lên. */
    public static final String LEVEL_DEFAULT = "level_default";
    public static final String NO_SOLUTION = "no_solution";
    public static final String STEM_EXTRA_TEXT = "stem_extra_text";
    public static final String FIGURE_FROM_PREVIOUS_QUESTION = "figure_from_previous_question";
    public static final String MENTIONS_FIGURE_NO_IMAGE = "mentions_figure_no_image";
    public static final String SHARED_CONTEXT = "shared_context";
    public static final String DUPLICATE_IN_BANK = "duplicate_in_bank";
    public static final String DUPLICATE_IN_BATCH = "duplicate_in_batch";
    public static final String LATEX_WARNING = "latex_warning";
    public static final String SOLUTION_LATEX_ERROR = "solution_latex_error";

    private static final Set<String> BLOCKING = Set.of(LEVEL_MISSING, SA_ANSWER_FORMAT, LATEX_ERROR, LEAK_SUSPECT);

    /** Cờ chặn: các cờ trên và mọi cờ cấu trúc/đáp án của bộ phân tích ({@code answer_*}, {@code structure_*}). */
    public static boolean isBlocking(String flag) {
        return BLOCKING.contains(flag) || flag.startsWith("answer_") || flag.startsWith("structure_");
    }

    /**
     * 16/11 (NV-51): lý do bằng lời cho một cờ chặn, để giáo viên biết sửa gì trong file Word. Quy tắc đáp án giữ nguyên: đáp
     * án trắc nghiệm phải tô vàng; câu đúng/sai tô vàng ý đúng hoặc có dòng "Đáp án: Đ S Đ S"; câu trả lời ngắn có dòng
     * "Đáp số: …".
     *
     * @param type dạng câu: mcq, tf hoặc sa
     */
    public static String describe(String flag, String type) {
        switch (flag) {
            case LEVEL_MISSING:
                return "Câu chưa ghi mức [NB], [TH] hoặc [VD].";
            case SA_ANSWER_FORMAT:
                return "Đáp số phải là số nguyên hoặc số thập phân, tối đa 4 ký tự.";
            case LATEX_ERROR:
                return "Công thức trong đề, phương án hoặc ý bị lỗi.";
            case LEAK_SUSPECT:
                return "Nội dung có tên tác giả bộ đề hoặc dòng quảng cáo, liên kết; hãy xóa trong file.";
            case EMPTY_FIELD:
                return "Đề, phương án hoặc ý bị trống.";
            case "answer_missing":
                return switch (type) {
                    case "tf" -> "Chưa thấy đáp án: tô vàng (highlight màu vàng) các ý đúng, hoặc thêm dòng "
                            + "\"Đáp án: Đ S Đ S\" ngay sau các ý.";
                    case "sa" -> "Chưa thấy đáp số: thêm dòng \"Đáp số: …\" sau câu.";
                    default -> "Chưa thấy đáp án: tô vàng (highlight màu vàng) phương án đúng.";
                };
            case "answer_ambiguous":
                return "Có nhiều hơn một phương án được tô vàng; chỉ tô vàng một phương án đúng.";
            case "answer_conflict":
                return "Các ý tô vàng khác với dòng \"Đáp án:\"; hãy sửa cho thống nhất.";
            case "structure_options_not_found":
                return "Không tách được đủ 4 phương án A, B, C, D.";
            default:
                if (flag.startsWith("structure_statements")) {
                    return "Câu đúng/sai phải có đúng 4 ý a), b), c), d).";
                }
                return "Câu chưa đúng cấu trúc của mẫu nhập (" + flag + ").";
        }
    }

    private ImportFlags() {}
}
