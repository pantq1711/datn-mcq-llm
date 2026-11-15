package com.datn.mcq.common.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc/ghi đối tượng JSON theo chữ cái phương án ({@code {"A": "...", "B": null, ...}}) cho các cột jsonb của phương án nhiễu
 * (V29: {@code distractor_errors}, {@code distractor_checks}). Khóa luôn theo thứ tự A-D; khóa khác bị bỏ.
 */
public final class LetterJson {

    /** Bốn chữ cái phương án, theo thứ tự. */
    public static final List<String> LETTERS = List.of("A", "B", "C", "D");

    private LetterJson() {}

    /**
     * Đối tượng JSON theo thứ tự A-D, chỉ gồm khóa có trong {@code map} (giá trị null ghi là {@code null}); null khi map null
     * hoặc không có khóa A-D nào.
     */
    public static String write(Map<String, String> map) {
        if (map == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        int n = 0;
        for (String letter : LETTERS) {
            if (map.containsKey(letter)) {
                sb.append(n++ > 0 ? "," : "").append(SimpleJson.quote(letter)).append(':')
                        .append(SimpleJson.quote(map.get(letter)));
            }
        }
        return n == 0 ? null : sb.append('}').toString();
    }

    /**
     * Map theo thứ tự A-D từ đối tượng JSON đã lưu; giá trị không phải chuỗi thành null. Null hoặc trống thì null; JSON hỏng
     * cũng null (dữ liệu chỉ để hiển thị, không làm hỏng trang).
     */
    public static Map<String, String> read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<String, Object> parsed;
        try {
            parsed = SimpleJson.parseObject(json);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String letter : LETTERS) {
            if (parsed.containsKey(letter)) {
                Object v = parsed.get(letter);
                out.put(letter, v instanceof String s ? s : null);
            }
        }
        return out;
    }
}
