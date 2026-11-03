package com.datn.mcq.bankimport.service;

import com.datn.mcq.common.exception.ApiErrorException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * Lỗi ràng buộc DB khi lưu lô nhập hoặc khi xác nhận câu nhập. Thông báo gốc của PostgreSQL có dòng "Failing row contains
 * (…)" kèm NỘI DUNG câu (bộ đề thương mại), nên KHÔNG bao giờ được log hay trả về: chỉ log tên ràng buộc, SQLState và mã lô.
 * Vi phạm CHECK / NOT NULL (dữ liệu câu không hợp lệ) là 422 IMPORT_DATA_INVALID; trùng khóa / khóa ngoại (thao tác đồng
 * thời) là 409 IMPORT_CONFLICT. Lỗi không phải vi phạm ràng buộc thì trả null để nơi gọi ném lại nguyên trạng.
 */
final class ImportDbErrors {

    private static final Logger LOG = LoggerFactory.getLogger(ImportDbErrors.class);
    /** Tên ràng buộc chỉ gồm ký tự an toàn (tên do migration đặt); khác thì không log. */
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_]{1,63}$");

    private ImportDbErrors() {}

    /**
     * @param where  bước đang làm ("lưu lô", "xác nhận")
     * @param batch  mã lô (hoặc "mới" khi chưa có id)
     * @return lỗi API thay thế, hoặc null nếu {@code e} không phải vi phạm ràng buộc
     */
    static ApiErrorException translate(Throwable e, String where, Object batch) {
        String constraint = null;
        String sqlState = null;
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ConstraintViolationException cv) {
                constraint = constraint == null ? cv.getConstraintName() : constraint;
                sqlState = sqlState == null ? cv.getSQLState() : sqlState;
            }
            if (t instanceof SQLException sql && sqlState == null) {
                sqlState = sql.getSQLState();
            }
        }
        if (sqlState == null || !sqlState.startsWith("23")) {
            return null;
        }
        String name = constraint != null && SAFE_NAME.matcher(constraint).matches() ? constraint : "(không rõ)";
        LOG.warn("Nhập từ Word: vi phạm ràng buộc {} (SQLState {}) khi {} lô {}", name, sqlState, where, batch);
        if ("23505".equals(sqlState) || "23503".equals(sqlState)) {
            return new ApiErrorException(HttpStatus.CONFLICT, "IMPORT_CONFLICT",
                    "Dữ liệu vừa thay đổi do một thao tác khác. Hãy tải lại trang rồi thử lại.");
        }
        return new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "IMPORT_DATA_INVALID",
                "Có câu không lưu được vì dữ liệu không hợp lệ (" + name + "). Hãy kiểm tra lại file Word.");
    }
}
