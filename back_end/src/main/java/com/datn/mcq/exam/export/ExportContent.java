package com.datn.mcq.exam.export;

/**
 * Phần nội dung của file Word xuất ra (tham số {@code content} của GET /api/exams/{examId}/export/docx, 16/11, NV-44).
 */
public enum ExportContent {

    /** Đề, rồi trang đáp án và lời giải (section riêng). Mặc định, như trước 16/11. */
    ALL,

    /** Chỉ đề: file dừng ở dòng "------ HẾT ------", một section, không có đáp án và lời giải. */
    EXAM;

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
        throw new IllegalArgumentException("Tham số content chỉ nhận all hoặc exam");
    }
}
