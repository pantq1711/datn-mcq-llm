package com.datn.mcq.exam.export;

/**
 * Phần nội dung của file Word xuất ra (tham số {@code content} của GET /api/exams/{examId}/export/docx, 16/11, NV-44).
 */
public enum ExportContent {

    /** Đề, rồi trang đáp án và lời giải (section riêng). Mặc định, như trước 16/11. */
    ALL,

    /** Chỉ đề: file dừng ở dòng "------ HẾT ------", một section, không có đáp án và lời giải. */
    EXAM,

    /**
     * Bản đặc tả đề kiểm tra (18/11) theo mẫu Phụ lục 2 Công văn 7991/BGDĐT-GDTrH: một section A4 ngang, không có đề, đáp án
     * hay mã đề ({@link ExamSpecBuilder}).
     */
    SPEC,

    /**
     * Bản cho phần mềm trộn đề YoungMix (26/11): chỉ nội dung đề (tiêu đề phần, dòng hướng dẫn, câu), dòng thẻ nhóm
     * {@code <gN#P>} dưới tiêu đề mỗi phần, nhãn phương án đúng và nhãn ý Đúng gạch chân, phần III có dòng "A. đáp án" dưới
     * câu hỏi; không có đầu đề, chân trang, dòng HẾT, đáp án, lời giải ({@link ExamMarkdownBuilder}).
     */
    YOUNGMIX;

    /** Không phân biệt hoa thường; null hoặc rỗng là {@link #ALL}; giá trị khác ném IllegalArgumentException (400). */
    public static ExportContent parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        for (ExportContent c : values()) {
            if (c.name().equalsIgnoreCase(value.strip())) {
                return c;
            }
        }
        throw new IllegalArgumentException("Tham số content chỉ nhận all, exam, spec hoặc youngmix");
    }
}
