package com.datn.mcq.assignment.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Họ tên học sinh ở chế độ {@code name} của bài giao (quyết định 25/11/2026, V36). Hàm thuần.
 *
 * <ul>
 *   <li>{@link #normalize}: dạng LƯU. Ghép Unicode NFC (bàn phím điện thoại có thể gửi dấu tách rời), bỏ khoảng trắng
 *       đầu cuối, gộp mọi chuỗi khoảng trắng (kể cả khoảng trắng không ngắt, tab, xuống dòng) thành một dấu cách; giữ hoa
 *       thường như học sinh gõ. Nhận 2–{@value #MAX_LENGTH} ký tự (đếm theo ký tự Unicode, như {@code VARCHAR(60)} của
 *       PostgreSQL). Từ chối nếu còn ký tự điều khiển ({@code Cc}) hoặc ký tự định dạng vô hình ({@code Cf}, ví dụ
 *       zero-width space, ký tự đảo chiều chữ): hai loại này không nhìn thấy nên làm hai tên trông giống hệt nhau mà không
 *       bị nhận là trùng.</li>
 *   <li>{@link #key}: khóa SO TRÙNG trong cùng bài giao + cùng lớp: chữ thường, khoảng trắng đã gộp, GIỮ dấu ("Nguyễn
 *       Văn An" và "nguyễn  VĂN an" trùng; "Nguyen Van An" là tên khác).</li>
 * </ul>
 */
final class StudentNames {

    static final int MIN_LENGTH = 2;
    static final int MAX_LENGTH = 60;

    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");

    private StudentNames() {}

    /** Dạng lưu của họ tên, hoặc null khi không hợp lệ (thiếu, quá ngắn, quá dài, có ký tự điều khiển). */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = SPACES.matcher(Normalizer.normalize(raw, Normalizer.Form.NFC)).replaceAll(" ").strip();
        int length = s.codePointCount(0, s.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return null;
        }
        boolean hidden = s.codePoints().anyMatch(cp -> {
            int type = Character.getType(cp);
            return type == Character.CONTROL || type == Character.FORMAT;
        });
        return hidden ? null : s;
    }

    /** Khóa so trùng của một họ tên ĐÃ chuẩn hóa: chữ thường (giữ dấu), NFC. */
    static String key(String normalizedName) {
        return Normalizer.normalize(normalizedName.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }

    /**
     * Tên (chữ cuối) để sắp bảng điểm như sổ điểm: theo tên rồi đến họ và tên đệm. Họ tên một chữ thì là chính nó.
     */
    static String givenName(String normalizedName) {
        int space = normalizedName.lastIndexOf(' ');
        return space < 0 ? normalizedName : normalizedName.substring(space + 1);
    }
}
