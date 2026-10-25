package com.datn.mcq.common.util;

import java.util.ArrayList;
import java.util.List;

/** Đọc/ghi mảng JSON các chuỗi cho cột jsonb (ví dụ quality_check_result.warnings, exam_matrix.notes). */
public final class JsonStringArray {

    private JsonStringArray() {}

    /** Mảng JSON các chuỗi; null khi danh sách null hoặc rỗng. */
    public static String write(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append(i > 0 ? "," : "").append(SimpleJson.quote(items.get(i)));
        }
        return sb.append("]").toString();
    }

    /** Danh sách chuỗi từ mảng JSON; null hoặc rỗng thì danh sách rỗng. */
    public static List<String> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Object parsed = SimpleJson.parseObject("{\"items\":" + json + "}").get("items");
        List<String> out = new ArrayList<>();
        if (parsed instanceof List<?> items) {
            for (Object item : items) {
                out.add(String.valueOf(item));
            }
        }
        return List.copyOf(out);
    }
}
