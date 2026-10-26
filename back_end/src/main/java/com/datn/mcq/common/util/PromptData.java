package com.datn.mcq.common.util;

/**
 * Dữ liệu do người dùng cung cấp (tài liệu bài học, yêu cầu thêm cho AI) đưa vào prompt phải được coi là DỮ LIỆU nằm
 * trong một khối có ranh giới {@code <<<} ... {@code >>>}. Dữ liệu không được chứa chính các ranh giới đó, nếu không có thể
 * "đóng khối" sớm rồi chèn chỉ dẫn giả ngay sau.
 */
public final class PromptData {

    private PromptData() {}

    /**
     * Bỏ mọi {@code <<<} và {@code >>>}, lặp tới khi không còn (bỏ một cặp có thể làm các ký tự hai bên ghép lại thành
     * ranh giới mới, ví dụ {@code <<<<>>>>}). Công thức như {@code $a<b$} và {@code <<}, {@code >>} giữ nguyên. Null thì rỗng.
     */
    public static String neutralize(String s) {
        if (s == null) {
            return "";
        }
        String out = s;
        while (out.contains("<<<") || out.contains(">>>")) {
            out = out.replace("<<<", "").replace(">>>", "");
        }
        return out;
    }
}
