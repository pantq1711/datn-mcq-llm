package com.datn.mcq.common.util;

import java.util.regex.Pattern;

/**
 * Dữ liệu do người dùng cung cấp (tài liệu bài học, yêu cầu thêm cho AI) hoặc đề bài đưa vào Solver phải được coi là
 * DỮ LIỆU nằm trong một khối có ranh giới {@code <<<} ... {@code >>>}. Dữ liệu không được chứa chính các ranh giới đó,
 * hoặc các biến thể ký tự ranh giới hay nhãn kết thúc khối, nếu không có thể "đóng khối" sớm rồi chèn chỉ dẫn giả ngay sau.
 */
public final class PromptData {

    /**
     * Các nhãn kết thúc khối dùng trong prompt của hệ thống: HẾT_TÀI_LIỆU..., HẾT_YÊU_CẦU..., HẾT_CÂU_HỎI...
     * (kể cả có hậu tố sau dấu _).
     */
    private static final Pattern BLOCK_MARKER = Pattern.compile("(?i)HẾT_(?:TÀI_LIỆU|YÊU_CẦU|CÂU_HỎI)(?:_[a-z0-9_\\p{L}]+)?");

    private PromptData() {}

    /**
     * Bỏ mọi ranh giới {@code <<<}, {@code >>>}, các ký tự ngoặc góc tương đương {@code «}, {@code »}, {@code ＜}, {@code ＞},
     * và các nhãn kết thúc khối hệ thống (HẾT_TÀI_LIỆU..., HẾT_YÊU_CẦU..., HẾT_CÂU_HỎI...). Lặp tới khi không còn (bỏ một
     * phần tử có thể làm các ký tự hai bên ghép lại thành ranh giới mới). Công thức Toán như {@code $a<b$}, {@code $x>y$},
     * {@code \le}, {@code \ge} và {@code <<}, {@code >>} giữ nguyên. Null thì rỗng.
     */
    public static String neutralize(String s) {
        if (s == null) {
            return "";
        }
        String out = s;
        while (true) {
            String prev = out;
            out = out.replace("<<<", "")
                    .replace(">>>", "")
                    .replace("«", "")
                    .replace("»", "")
                    .replace("＜", "")
                    .replace("＞", "");
            out = BLOCK_MARKER.matcher(out).replaceAll("");
            if (out.equals(prev)) {
                break;
            }
        }
        return out;
    }
}
