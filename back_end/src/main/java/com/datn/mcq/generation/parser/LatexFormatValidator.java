package com.datn.mcq.generation.parser;

import com.datn.mcq.common.util.LatexValidationRules;

import java.util.List;

/**
 * Kiểm tra định dạng công thức LaTeX trong văn bản câu hỏi đã giải mã từ JSON.
 * Không tự sửa (sanitize) công thức hỏng; chỉ trả về danh sách lỗi phát hiện được.
 */
public final class LatexFormatValidator {

    private LatexFormatValidator() {}

    /** Liệt kê mọi phát hiện, kể cả cảnh báo. Không dùng để quyết định chặn; dùng {@link #check(String)}. */
    public static List<String> validate(String text) {
        return LatexValidationRules.validate(text);
    }

    /** Kết quả tách lỗi fatal và cảnh báo. */
    public static LatexValidationRules.Result check(String text) {
        return LatexValidationRules.check(text);
    }
}
