package com.datn.mcq.questionimage.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiện ích xử lý token hình ảnh câu hỏi ⟦img:<id>⟧ theo hợp đồng H2 (thiết kế G0).
 */
public final class QuestionImageToken {

    public static final String PREFIX = "⟦img:";
    public static final String SUFFIX = "⟧";
    public static final Pattern TOKEN_PATTERN = Pattern.compile("⟦img:(\\d+)⟧");

    private QuestionImageToken() {}

    /**
     * Dựng chuỗi token từ id hình ảnh. Ví dụ id = 812 -> "⟦img:812⟧".
     */
    public static String format(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Image ID phải là số nguyên dương: " + id);
        }
        return PREFIX + id + SUFFIX;
    }

    /**
     * Trích xuất id từ chuỗi token chính xác "⟦img:<id>⟧".
     */
    public static long parseId(String token) {
        if (token == null) {
            throw new IllegalArgumentException("Token không được để trống");
        }
        Matcher matcher = TOKEN_PATTERN.matcher(token.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Token hình ảnh không đúng định dạng: " + token);
        }
        return Long.parseLong(matcher.group(1));
    }

    /**
     * Kiểm tra văn bản có chứa ít nhất một token ⟦img:<id>⟧ hay không.
     */
    public static boolean containsToken(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return TOKEN_PATTERN.matcher(text).find();
    }

    /**
     * Lấy danh sách toàn bộ ID ảnh xuất hiện trong văn bản theo thứ tự xuất hiện.
     */
    public static List<Long> extractIds(String text) {
        List<Long> ids = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return ids;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids;
    }

    /**
     * Xóa toàn bộ token ⟦img:<id>⟧ khỏi văn bản (dùng cho dedup và so sánh văn bản thuần túy).
     */
    public static String strip(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return TOKEN_PATTERN.matcher(text).replaceAll("").trim();
    }

    /**
     * Thoát ký tự ⟦ và ⟧ có sẵn trong văn bản Word thành [ và ] (H2: tránh người dùng gõ giả token).
     */
    public static String sanitizeWordText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replace('⟦', '[').replace('⟧', ']');
    }

    /**
     * Kiểm tra văn bản có chứa chuỗi "⟦img:" hay không (dùng để chặn giáo viên tự gõ token khi sửa câu).
     */
    public static boolean containsDisallowedPrefix(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains(PREFIX);
    }
}
