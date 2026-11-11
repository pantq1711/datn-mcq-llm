package com.datn.mcq.common.util;

/**
 * Đổi công thức {@code $$...$$} thành {@code $...$} (C-02, rà soát 11/11). Model đôi khi viết công thức riêng dòng bằng
 * "$$" (prompt chỉ nói "$...$" nhưng không cấm); web (KaTeX) hiện đúng, nhưng bước xuất Word coi mỗi "$$" là một "$" rỗng
 * rồi một "$" lẻ nên Word hiện hai dấu "$" thừa quanh công thức. Đề chỉ dùng công thức trong dòng nên đổi về "$" không
 * mất gì.
 *
 * <p>Dùng chung cho bước định dạng khi lưu câu (LlmOutputRepair, nhóm tự sửa, không bật cờ "cần xem kỹ") và khi xuất Word
 * (câu đã lưu từ trước cũng được sửa), cạnh {@link LiteralNewlines} và {@link AlignmentAmpersand}.
 *
 * <p>Cách xác định như {@link AlignmentAmpersand}: "\" bỏ qua ký tự đi sau (nên {@code \$} là dấu $ thật), công thức một
 * "$" được nhảy qua nguyên khối (nên "$a$$b$" là hai công thức liền nhau, không phải "$$"). Giữ nguyên: "$$" không có cặp
 * đóng (và mọi thứ sau nó), "$$$$" (công thức rỗng), "$$...$$" mà bên trong còn "$". Khoảng trắng và xuống dòng sát hai đầu
 * công thức được bỏ (công thức trong dòng của pandoc không được bắt đầu hoặc kết thúc bằng khoảng trắng). Không có gì để sửa
 * thì trả về đúng đối tượng chuỗi ban đầu.
 */
public final class DisplayMathDollars {

    /**
     * @param text  văn bản sau khi sửa
     * @param fixed số công thức "$$...$$" đã đổi
     */
    public record Result(String text, int fixed) { }

    private DisplayMathDollars() {
    }

    /** Cảnh báo tự sửa (cùng tiền tố "json: đã sửa" nên không bật "cần xem kỹ"); null khi không sửa gì. */
    public static String warning(String field, Result r) {
        if (r.fixed() == 0) {
            return null;
        }
        return "json: đã sửa " + r.fixed() + " công thức $$...$$ thành $...$ ở " + field;
    }

    public static Result repair(String text) {
        if (text == null || !text.contains("$$")) {
            return new Result(text, 0);
        }
        StringBuilder sb = null; // chỉ tạo khi có chỗ đổi
        int copied = 0;          // text[0, copied) đã nằm trong sb
        int fixed = 0;
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c != '$') {
                i++;
                continue;
            }
            boolean display = i + 1 < n && text.charAt(i + 1) == '$';
            if (!display) {
                int close = nextDollar(text, i + 1);
                if (close < 0) {
                    break;
                }
                i = close + 1;
                continue;
            }
            int close = nextDollar(text, i + 2);
            if (close < 0 || close + 1 >= n || text.charAt(close + 1) != '$') {
                break; // "$$" không có cặp "$$" đóng: giữ nguyên phần còn lại
            }
            String inner = text.substring(i + 2, close).strip();
            if (!inner.isEmpty()) {
                if (sb == null) {
                    sb = new StringBuilder(n);
                }
                sb.append(text, copied, i).append('$').append(inner).append('$');
                copied = close + 2;
                fixed++;
            }
            i = close + 2;
        }
        if (sb == null) {
            return new Result(text, 0);
        }
        return new Result(sb.append(text, copied, n).toString(), fixed);
    }

    /** Vị trí "$" chưa thoát đầu tiên từ {@code from}; -1 khi không có. */
    private static int nextDollar(String text, int from) {
        int n = text.length();
        for (int k = from; k < n; k++) {
            char c = text.charAt(k);
            if (c == '\\') {
                k++;
            } else if (c == '$') {
                return k;
            }
        }
        return -1;
    }
}
