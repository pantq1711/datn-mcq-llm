package com.datn.mcq.bankimport.mtef;

/**
 * Kết quả chuyển một công thức MathType sang LaTeX.
 *
 * @param status OK: mọi record đã dịch; PARTIAL: có LaTeX nhưng đã bỏ một phần không hiển thị đáng kể (lý do trong
 *               {@code reason}); UNSUPPORTED: không dịch được, {@code latex} là null (không đoán)
 * @param latex  LaTeX không có dấu $ bao quanh; null khi UNSUPPORTED
 * @param reason lý do khi PARTIAL hoặc UNSUPPORTED; null khi OK
 */
public record FormulaResult(Status status, String latex, String reason) {

    public enum Status { OK, PARTIAL, UNSUPPORTED }

    public static FormulaResult ok(String latex) {
        return new FormulaResult(Status.OK, latex, null);
    }

    public static FormulaResult partial(String latex, String reason) {
        return new FormulaResult(Status.PARTIAL, latex, reason);
    }

    public static FormulaResult unsupported(String reason) {
        return new FormulaResult(Status.UNSUPPORTED, null, reason);
    }

    public boolean hasLatex() {
        return latex != null;
    }
}
