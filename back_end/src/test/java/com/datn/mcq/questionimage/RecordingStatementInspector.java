package com.datn.mcq.questionimage;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Ghi lại mọi câu SQL Hibernate gửi đi, để test kiểm hai điều về ảnh câu hỏi: (1) số truy vấn bảng question_image không
 * tăng theo số câu, (2) truy vấn xem/danh sách không bao giờ nạp cột bytea {@code data}. Chỉ bật khi test đặt
 * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector} trỏ tới lớp này. Không phải test.
 */
public class RecordingStatementInspector implements StatementInspector {

    public static final String PROPERTY = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
            + "com.datn.mcq.questionimage.RecordingStatementInspector";

    private static final List<String> SQL = new CopyOnWriteArrayList<>();

    public static void clear() {
        SQL.clear();
    }

    /** Các câu SQL đã ghi từ lần {@link #clear()} gần nhất, chữ thường. */
    public static List<String> recorded() {
        return SQL.stream().map(String::toLowerCase).toList();
    }

    /** Các câu SQL đã ghi có đọc bảng question_image. */
    public static List<String> imageQueries() {
        return recorded().stream().filter(s -> s.contains("question_image")).toList();
    }

    /** Câu SQL nào nhắc tới cột {@code data} (từ nguyên vẹn, không phải {@code data_...}). */
    public static boolean selectsDataColumn(String sql) {
        return java.util.regex.Pattern.compile("\\bdata\\b").matcher(sql).find();
    }

    @Override
    public String inspect(String sql) {
        SQL.add(sql);
        return sql;
    }
}
