package com.datn.mcq.exam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * N-10: đọc file SQL thống kê viết cho psql để chạy qua JDBC trong test. Làm đúng phần psql làm với các file này: bỏ dòng
 * lệnh của psql (bắt đầu bằng "\", ví dụ {@code \if}, {@code \set}) và thay biến {@code :'ten'} bằng chuỗi SQL. Giá trị
 * lấy từ {@code vars} (như {@code psql -v ten=...}), không có thì lấy mặc định ở dòng {@code \set ten '...'} của file.
 */
final class PsqlFile {

    private static final Pattern SET = Pattern.compile("^\\s*\\\\set\\s+(\\w+)\\s+'([^']*)'\\s*$");
    private static final Pattern VAR = Pattern.compile(":'(\\w+)'");

    private PsqlFile() {
    }

    static String load(Path file) throws IOException {
        return load(file, Map.of());
    }

    static String load(Path file, Map<String, String> vars) throws IOException {
        Map<String, String> values = new HashMap<>();
        StringBuilder sql = new StringBuilder();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            Matcher set = SET.matcher(line);
            if (set.matches()) {
                values.putIfAbsent(set.group(1), set.group(2));
            }
            if (!line.stripLeading().startsWith("\\")) {
                sql.append(line).append('\n');
            }
        }
        values.putAll(vars);
        Matcher m = VAR.matcher(sql);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            if (value == null) {
                throw new IllegalStateException(file + ": biến psql :" + m.group(1) + " chưa có \\set mặc định");
            }
            m.appendReplacement(out, Matcher.quoteReplacement("'" + value.replace("'", "''") + "'"));
        }
        m.appendTail(out);
        return out.toString();
    }
}
