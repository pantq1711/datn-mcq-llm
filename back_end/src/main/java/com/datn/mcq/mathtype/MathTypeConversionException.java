package com.datn.mcq.mathtype;

/**
 * Không chuyển được cả file sang MathType (docx hỏng, hoặc số công thức trong docx khác số LaTeX nên không biết ảnh
 * xem trước nào thuộc công thức nào). Bên gọi nên trả file Equation (OMML) gốc.
 */
public class MathTypeConversionException extends RuntimeException {

    public MathTypeConversionException(String message) {
        super(message);
    }

    public MathTypeConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
