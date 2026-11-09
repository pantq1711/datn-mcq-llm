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

    private ImportFlags() {}
}
