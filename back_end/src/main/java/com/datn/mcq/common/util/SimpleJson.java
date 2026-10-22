package com.datn.mcq.common.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bộ đọc/ghi JSON tuân thủ RFC 8259 dùng cho phản hồi LLM và payload gọi Gemini.
 * Bắt chặt các chuỗi thoát không hợp lệ (ví dụ \g, \l, \c, \s chưa nhân đôi dấu \)
 * và không phụ thuộc vào khác biệt package giữa Jackson 2 và Jackson 3.
 * Khóa trùng trong cùng một đối tượng (ở mọi độ sâu) bị từ chối với thông báo nêu tên khóa.
 */
public final class SimpleJson {

    private SimpleJson() {}

    public static String stripMarkdownFence(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            } else {
                s = s.substring(3);
            }
            s = s.trim();
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3).trim();
            }
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        if (json == null) {
            throw new IllegalArgumentException("Chuỗi JSON trống (null)");
        }
        Parser p = new Parser(json);
        Object value = p.parseValue();
        p.skipWhitespace();
        if (!p.isAtEnd()) {
            throw new IllegalArgumentException("Dư ký tự sau đối tượng JSON tại vị trí " + p.pos);
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Gốc JSON không phải là đối tượng {...}");
        }
        return (Map<String, Object>) map;
    }

    public static String quote(String raw) {
        if (raw == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static final class Parser {
        private final String s;
        private final int n;
        private int pos;

        Parser(String s) {
            this.s = s;
            this.n = s.length();
            this.pos = 0;
        }

        boolean isAtEnd() {
            return pos >= n;
        }

        void skipWhitespace() {
            while (pos < n) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= n) {
                throw new IllegalArgumentException("JSON kết thúc đột ngột tại vị trí " + pos);
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> parseObjectInternal();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw new IllegalArgumentException("Ký tự JSON không hợp lệ '" + c + "' tại vị trí " + pos);
                }
            };
        }

        Map<String, Object> parseObjectInternal() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw new IllegalArgumentException("Tên trường JSON phải đặt trong dấu ngoặc kép tại vị trí " + pos);
                }
                int keyPos = pos;
                String key = parseString();
                // Khóa trùng ở bất kỳ đối tượng nào là lỗi: không lấy giá trị đầu hay giá trị sau
                // (ví dụ model trả hai khóa "option_c", khóa sau đáng lẽ là "correct_option").
                if (map.containsKey(key)) {
                    throw new IllegalArgumentException(
                            "Khóa JSON bị trùng: " + quote(key) + " tại vị trí " + keyPos);
                }
                skipWhitespace();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Thiếu dấu ',' hoặc '}' trong đối tượng JSON tại vị trí " + pos);
                }
            }
            return map;
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Thiếu dấu ',' hoặc ']' trong mảng JSON tại vị trí " + pos);
                }
            }
            return list;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < n) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw new IllegalArgumentException(
                            "Ký tự điều khiển chưa thoát trong chuỗi JSON tại vị trí " + (pos - 1));
                }
                if (c == '\\') {
                    if (pos >= n) {
                        throw new IllegalArgumentException("Dấu \\ ở cuối chuỗi JSON tại vị trí " + (pos - 1));
                    }
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > n) {
                                throw new IllegalArgumentException("Chuỗi \\uXXXX không đủ 4 ký tự hex tại vị trí " + pos);
                            }
                            String hex = s.substring(pos, pos + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw new IllegalArgumentException("Mã \\u" + hex + " không hợp lệ tại vị trí " + pos);
                            }
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException(
                                "Invalid \\escape: '\\" + esc + "' tại vị trí " + (pos - 2));
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Chuỗi JSON chưa đóng dấu ngoặc kép");
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Giá trị boolean không hợp lệ tại vị trí " + pos);
        }

        Object parseNull() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Giá trị null không hợp lệ tại vị trí " + pos);
        }

        Number parseNumber() {
            int start = pos;
            if (peek('-')) {
                pos++;
            }
            while (pos < n && Character.isDigit(s.charAt(pos))) {
                pos++;
            }
            boolean isFloat = false;
            if (pos < n && s.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < n && Character.isDigit(s.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < n && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < n && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < n && Character.isDigit(s.charAt(pos))) {
                    pos++;
                }
            }
            String numStr = s.substring(start, pos);
            try {
                if (isFloat) {
                    return Double.parseDouble(numStr);
                }
                long val = Long.parseLong(numStr);
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                    return (int) val;
                }
                return val;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Số JSON không hợp lệ '" + numStr + "' tại vị trí " + start);
            }
        }

        boolean peek(char expected) {
            return pos < n && s.charAt(pos) == expected;
        }

        void expect(char expected) {
            if (pos >= n || s.charAt(pos) != expected) {
                throw new IllegalArgumentException("Kỳ vọng '" + expected + "' tại vị trí " + pos);
            }
            pos++;
        }
    }
}
