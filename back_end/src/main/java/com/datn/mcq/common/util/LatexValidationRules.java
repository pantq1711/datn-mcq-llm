package com.datn.mcq.common.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Quy tắc kiểm tra định dạng công thức LaTeX dùng chung cho parser và bước format check (QC).
 * Không tự sửa (sanitize) công thức hỏng; chỉ trả về danh sách lỗi phát hiện được.
 *
 * <p>Đây chỉ là kiểm tra cú pháp bề mặt trong phạm vi toán THCS (tập lệnh cho phép cố định,
 * đối số của \frac và \sqrt, cặp \left/\right, cặp \begin/\end, ngoặc nhọn, dấu $).
 * Nó không phải parser LaTeX và không bảo đảm KaTeX render được công thức.
 */
public final class LatexValidationRules {

    /** Lệnh được phép trong công thức; lệnh khác bị báo lỗi (có thể do sai chính tả). */
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "frac", "sqrt", "cdot", "times", "div", "pm",
            "neq", "ne", "le", "leq", "ge", "geq",
            "left", "right", "begin", "end",
            "Rightarrow", "Leftrightarrow", "rightarrow", "leftrightarrow",
            "dots", "ldots", "cdots",
            "pi", "angle", "triangle", "circ", "infty", "in", "notin", "cup", "cap",
            "text", "sin", "cos", "tan", "cot", "quad");

    private static final Set<String> ALLOWED_ENVIRONMENTS = Set.of("matrix", "cases", "aligned");

    /** Dấu phân cách hợp lệ sau \left / \right (ngoài ra còn \{ \} \|); '.' là dấu vô hình. */
    private static final String PLAIN_DELIMITERS = "()[]|.";

    private static final String LEFT_FRAME = "L";
    private static final String ENV_FRAME_PREFIX = "E:";

    private LatexValidationRules() {}

    public static List<String> validate(String text) {
        List<String> errors = new ArrayList<>();
        if (text == null || text.isBlank()) {
            errors.add("Nội dung trống");
            return errors;
        }
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\f' || c == '\b' || c == '\t' || c == '\r' || (c < 0x20 && c != '\n')) {
                errors.add("ký tự điều khiển tại vị trí " + i + " (thường do \\ chưa thoát trong JSON)");
                break;
            }
        }
        boolean inMath = false;
        boolean display = false;
        boolean commandOutsideReported = false;
        int depth = 0;
        int mathStart = 0;
        Deque<String> frames = new ArrayDeque<>();
        Set<String> reportedCommands = new HashSet<>();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\') {
                if (!inMath) {
                    if (!commandOutsideReported && i + 1 < n && Character.isLetter(text.charAt(i + 1))) {
                        errors.add("Có lệnh LaTeX nằm ngoài cặp $...$ (thiếu dấu $ bao quanh công thức)");
                        commandOutsideReported = true;
                    }
                    i += 2;
                } else {
                    i = scanCommand(text, i, frames, errors, reportedCommands);
                }
                continue;
            }
            if (c == '$') {
                boolean doubleDollar = i + 1 < n && text.charAt(i + 1) == '$';
                if (!inMath) {
                    display = doubleDollar;
                    inMath = true;
                    i += display ? 2 : 1;
                    mathStart = i;
                    depth = 0;
                    frames.clear();
                    continue;
                }
                if (display && !doubleDollar) {
                    errors.add("số dấu $ bị lẻ");
                }
                if (text.substring(mathStart, i).isBlank()) {
                    errors.add("công thức rỗng giữa cặp $...$");
                }
                if (depth != 0) {
                    errors.add("ngoặc { } không cân bằng");
                }
                reportUnclosedFrames(frames, errors);
                inMath = false;
                i += (display && doubleDollar) ? 2 : 1;
                continue;
            }
            if (inMath) {
                if (c == '\n') {
                    if (!display && !hasOpenEnvironment(frames)) {
                        errors.add("ký tự điều khiển xuống dòng bên trong $...$ tại vị trí " + i
                                + " (thường do \\n như \\neq chưa thoát trong JSON)");
                    } else {
                        String lost = decodedNewlineCommand(text, i);
                        if (lost != null) {
                            errors.add("ký tự xuống dòng tại vị trí " + i + " nằm ngay trước \""
                                    + text.substring(i + 1, i + 1 + lost.length() - 1)
                                    + "\" (có thể do \\" + lost + " chưa thoát trong JSON)");
                        }
                    }
                } else if (c == '{') {
                    depth += 1;
                } else if (c == '}') {
                    depth -= 1;
                    if (depth < 0) {
                        errors.add("dấu } thừa");
                        depth = 0;
                    }
                }
            }
            i += 1;
        }
        if (inMath) {
            errors.add("số dấu $ bị lẻ");
        }
        return errors;
    }

    /**
     * Xử lý một dấu '\' bên trong công thức, trả về vị trí tiếp theo cần quét.
     * Ký hiệu một ký tự như \{ \} \$ \\ được bỏ qua nguyên khối nên không ảnh hưởng đếm ngoặc.
     */
    private static int scanCommand(String text, int start, Deque<String> frames,
                                   List<String> errors, Set<String> reportedCommands) {
        int n = text.length();
        if (start + 1 >= n) {
            return n;
        }
        if (!Character.isLetter(text.charAt(start + 1))) {
            return start + 2;
        }
        int end = start + 1;
        while (end < n && Character.isLetter(text.charAt(end))) {
            end++;
        }
        String name = text.substring(start + 1, end);
        if (!ALLOWED_COMMANDS.contains(name)) {
            if (reportedCommands.add(name)) {
                errors.add(unknownCommandMessage(name));
            }
            return end;
        }
        switch (name) {
            case "frac" -> checkArguments("\\frac", 2, text, end, errors);
            case "sqrt" -> checkSqrt(text, end, errors);
            case "left" -> {
                checkDelimiter("\\left", text, end, errors);
                frames.push(LEFT_FRAME);
            }
            case "right" -> {
                checkDelimiter("\\right", text, end, errors);
                if (LEFT_FRAME.equals(frames.peek())) {
                    frames.pop();
                } else {
                    errors.add("\\right không có \\left tương ứng");
                }
            }
            case "begin" -> {
                String env = readEnvironmentName(text, end);
                if (env == null) {
                    errors.add("\\begin thiếu tên môi trường dạng {tên}");
                } else {
                    if (!ALLOWED_ENVIRONMENTS.contains(env)) {
                        errors.add("môi trường " + env + " không hỗ trợ");
                    }
                    frames.push(ENV_FRAME_PREFIX + env);
                }
            }
            case "end" -> {
                String env = readEnvironmentName(text, end);
                if (env == null) {
                    errors.add("\\end thiếu tên môi trường dạng {tên}");
                } else if ((ENV_FRAME_PREFIX + env).equals(frames.peek())) {
                    frames.pop();
                } else {
                    errors.add("\\end{" + env + "} không có \\begin tương ứng");
                }
            }
            default -> {
                // Lệnh còn lại trong allowlist không có yêu cầu đối số
            }
        }
        return end;
    }

    private static String unknownCommandMessage(String name) {
        Set<String> suggestions = new TreeSet<>();
        for (String candidate : ALLOWED_COMMANDS) {
            if (withinOneEdit(name, candidate)) {
                suggestions.add("\\" + candidate);
            }
        }
        String message = "Lệnh \\" + name + " không hỗ trợ hoặc có thể sai chính tả";
        if (!suggestions.isEmpty()) {
            message += " (gợi ý: " + String.join(", ", suggestions) + ")";
        }
        return message;
    }

    /** Khoảng cách chỉnh sửa (thêm/xóa/thay một ký tự) không quá 1 và hai chuỗi khác nhau. */
    private static boolean withinOneEdit(String a, String b) {
        int la = a.length();
        int lb = b.length();
        if (a.equals(b) || Math.abs(la - lb) > 1) {
            return false;
        }
        int i = 0;
        while (i < la && i < lb && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        if (la == lb) {
            return a.substring(i + 1).equals(b.substring(i + 1));
        }
        return la < lb ? a.substring(i).equals(b.substring(i + 1)) : a.substring(i + 1).equals(b.substring(i));
    }

    private static void checkSqrt(String text, int pos, List<String> errors) {
        int k = skipWhitespace(text, pos);
        if (k < text.length() && text.charAt(k) == '[') {
            int close = text.indexOf(']', k);
            if (close < 0) {
                errors.add("\\sqrt có chỉ số căn [ chưa đóng");
                return;
            }
            k = close + 1;
        }
        checkArguments("\\sqrt", 1, text, k, errors);
    }

    /**
     * Kiểm tra lệnh có đủ {@code count} đối số. Đối số là nhóm {...} không rỗng hoặc một token đơn
     * (một ký tự, hoặc một lệnh) theo cú pháp TeX, nên \frac12 hợp lệ. Mỗi lệnh chỉ báo một lỗi.
     */
    private static void checkArguments(String label, int count, String text, int pos, List<String> errors) {
        int n = text.length();
        int k = pos;
        for (int a = 0; a < count; a++) {
            k = skipWhitespace(text, k);
            if (k >= n || text.charAt(k) == '}' || text.charAt(k) == '$') {
                errors.add(label + " thiếu đối số");
                return;
            }
            char c = text.charAt(k);
            if (c == '{') {
                int close = findMatchingBrace(text, k);
                if (close < 0) {
                    return; // ngoặc lệch đã được báo riêng
                }
                if (text.substring(k + 1, close).isBlank()) {
                    errors.add(label + " có đối số rỗng");
                    return;
                }
                k = close + 1;
            } else if (c == '\\') {
                k++;
                if (k < n && Character.isLetter(text.charAt(k))) {
                    while (k < n && Character.isLetter(text.charAt(k))) {
                        k++;
                    }
                } else {
                    k++;
                }
            } else {
                k++;
            }
        }
    }

    private static void checkDelimiter(String label, String text, int pos, List<String> errors) {
        int n = text.length();
        int k = skipWhitespace(text, pos);
        boolean valid = false;
        if (k < n) {
            char c = text.charAt(k);
            if (PLAIN_DELIMITERS.indexOf(c) >= 0) {
                valid = true;
            } else if (c == '\\' && k + 1 < n) {
                char next = text.charAt(k + 1);
                valid = next == '{' || next == '}' || next == '|';
            }
        }
        if (!valid) {
            errors.add(label + " thiếu dấu phân cách hợp lệ ngay sau lệnh");
        }
    }

    /** Đọc tên môi trường trong {tên} ngay sau \begin / \end; null nếu không có dạng đó. */
    private static String readEnvironmentName(String text, int pos) {
        int k = skipWhitespace(text, pos);
        if (k >= text.length() || text.charAt(k) != '{') {
            return null;
        }
        int close = text.indexOf('}', k);
        if (close < 0) {
            return null;
        }
        String name = text.substring(k + 1, close).trim();
        return name.isEmpty() ? null : name;
    }

    /** Vị trí '}' khớp với '{' tại {@code open}; -1 nếu không khớp trước khi hết công thức. */
    private static int findMatchingBrace(String text, int open) {
        int depth = 0;
        int n = text.length();
        for (int k = open; k < n; k++) {
            char c = text.charAt(k);
            if (c == '\\') {
                k++;
            } else if (c == '$') {
                return -1;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return k;
                }
            }
        }
        return -1;
    }

    private static int skipWhitespace(String text, int pos) {
        int k = pos;
        while (k < text.length() && Character.isWhitespace(text.charAt(k))) {
            k++;
        }
        return k;
    }

    /**
     * Dấu xuống dòng thật chỉ do JSON giải mã "\n" mà ra. Nếu ngay sau nó (không có khoảng trắng) là
     * chuỗi chữ cái w sao cho "n" + w là một lệnh trong allowlist (neq, notin), nhiều khả năng đó là
     * lệnh bị mất dấu '\' thoát. Trả về tên lệnh ("neq"), hoặc null nếu không nghi ngờ.
     * Chỉ xét w từ 2 chữ cái trở lên để không báo nhầm dòng bắt đầu bằng một biến đơn như "e".
     */
    private static String decodedNewlineCommand(String text, int newlineIndex) {
        int end = newlineIndex + 1;
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++;
        }
        if (end - (newlineIndex + 1) < 2) {
            return null;
        }
        String candidate = "n" + text.substring(newlineIndex + 1, end);
        return ALLOWED_COMMANDS.contains(candidate) ? candidate : null;
    }

    private static boolean hasOpenEnvironment(Deque<String> frames) {
        for (String frame : frames) {
            if (frame.startsWith(ENV_FRAME_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static void reportUnclosedFrames(Deque<String> frames, List<String> errors) {
        for (String frame : frames) {
            if (LEFT_FRAME.equals(frame)) {
                errors.add("\\left không có \\right tương ứng");
            } else {
                String env = frame.substring(ENV_FRAME_PREFIX.length());
                errors.add("\\begin{" + env + "} thiếu \\end{" + env + "}");
            }
        }
        frames.clear();
    }
}
