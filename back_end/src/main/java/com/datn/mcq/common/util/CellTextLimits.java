package com.datn.mcq.common.util;

/**
 * Giới hạn độ dài các trường của ô ma trận đi vào prompt sinh câu (D2). Đây là MỨC TRẦN CỨNG: kiểm tra đầu vào dùng giá trị
 * cấu hình ({@code app.generation-limits.cell-*}, mặc định bằng các hằng này và không được vượt chúng), còn khi dựng prompt
 * ({@code MatrixPromptDraftV0}) mọi trường vẫn bị cắt theo đúng các hằng này, để ô lưu từ trước khi có giới hạn không đưa
 * văn bản dài tùy ý vào prompt. Độ dài tính theo ký tự (điểm mã Unicode), không theo đơn vị UTF-16.
 */
public final class CellTextLimits {

    /** Nội dung (phạm vi) của ô. */
    public static final int CONTENT_MAX_CHARS = 1000;
    /** Mỗi yêu cầu cần đạt của ô. */
    public static final int OUTCOME_MAX_CHARS = 500;
    /** Số mục yêu cầu cần đạt tối đa của một ô. */
    public static final int OUTCOMES_MAX_ITEMS = 10;
    /** "Yêu cầu thêm cho AI" của ô hoặc của cả đề (khớp {@code ExamContextService.MAX_INSTRUCTION_CHARS}). */
    public static final int EXTRA_INSTRUCTION_MAX_CHARS = 1000;

    private CellTextLimits() {}

    /** Số ký tự (điểm mã Unicode) của {@code s}; null là 0. */
    public static int length(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    /** Cắt {@code s} còn tối đa {@code maxChars} ký tự (không cắt đôi cặp surrogate); null giữ nguyên null. */
    public static String clip(String s, int maxChars) {
        if (s == null || length(s) <= maxChars) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, maxChars));
    }
}
