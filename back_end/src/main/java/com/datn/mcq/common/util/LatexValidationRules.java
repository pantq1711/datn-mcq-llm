package com.datn.mcq.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Quy tắc kiểm tra định dạng công thức LaTeX dùng chung cho parser và bước format check (QC).
 * Không tự sửa (sanitize) công thức hỏng; chỉ trả về danh sách lỗi phát hiện được.
 */
public final class LatexValidationRules {

    private LatexValidationRules() {}

    public static List<String> validate(String text) {
        List<String> errors = new ArrayList<>();
        if (text == null || text.isBlank()) {
            errors.add("Nội dung trống");
            return errors;
        }
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\f' || c == '\b' || c == '\t' || c == '\r' || (c < 0x20 && c != '\n')) {
                errors.add("ký tự điều khiển tại vị trí " + i + " (thường do \\ chưa thoát trong JSON)");
                break;
            }
        }
        boolean inMath = false;
        boolean commandOutsideReported = false;
        int depth = 0;
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\') {
                if (!inMath && !commandOutsideReported && i + 1 < n && Character.isLetter(text.charAt(i + 1))) {
                    errors.add("Có lệnh LaTeX nằm ngoài cặp $...$ (thiếu dấu $ bao quanh công thức)");
                    commandOutsideReported = true;
                }
                i += 2;
                continue;
            }
            if (c == '$') {
                if (i + 1 < n && text.charAt(i + 1) == '$') {
                    i += 1;
                }
                if (inMath && depth != 0) {
                    errors.add("ngoặc { } không cân bằng");
                    depth = 0;
                }
                inMath = !inMath;
                i += 1;
                continue;
            }
            if (inMath) {
                if (c == '\n') {
                    errors.add("ký tự điều khiển xuống dòng bên trong $...$ tại vị trí " + i
                            + " (thường do \\n như \\neq chưa thoát trong JSON)");
                } else if (c == '{') {
                    depth += 1;
                } else if (c == '}') {
                    depth -= 1;
                    if (depth < 0) {
                        errors.add("dấu } thừa");
                        depth = 0;
                    }
                }
            }
            i += 1;
        }
        if (inMath) {
            errors.add("số dấu $ bị lẻ");
        }
        return errors;
    }
}

