package com.datn.mcq.exam.export;

/** Dạng công thức trong file Word xuất ra (tham số {@code equation} của GET /api/exams/{examId}/export/docx). */
public enum EquationFormat {

    /** Equation gốc của Word (OMML) do pandoc sinh. Mặc định. */
    OMML,

    /** Đối tượng MathType OLE (Equation.DSMT4); công thức chưa hỗ trợ giữ dạng Equation của Word. */
    MATHTYPE;

    /** Không phân biệt hoa thường; null hoặc rỗng là {@link #OMML}; giá trị khác ném IllegalArgumentException (400). */
    public static EquationFormat parse(String value) {
        if (value == null || value.isBlank()) {
            return OMML;
        }
        for (EquationFormat f : values()) {
            if (f.name().equalsIgnoreCase(value.strip())) {
                return f;
            }
        }
        throw new IllegalArgumentException("Tham số equation chỉ nhận omml hoặc mathtype");
    }
}
