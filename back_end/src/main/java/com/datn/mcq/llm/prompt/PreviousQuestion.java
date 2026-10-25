package com.datn.mcq.llm.prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * Một câu đã sinh trước đó, đưa vào prompt sinh câu sa/tf để model không lặp lại bài toán hay số liệu (độ đa dạng,
 * quyết định Q1-Q3 ngày 22/10). Không kèm đáp số hay nhãn.
 *
 * @param questionText đề (sa) hoặc đề dẫn (tf)
 * @param statements   4 ý theo thứ tự a-d (tf); rỗng với sa
 */
public record PreviousQuestion(String questionText, List<String> statements) {

    /** Số câu tối đa đưa vào prompt. */
    public static final int MAX_ITEMS = 10;
    /** Mỗi mục (đề, đề dẫn hoặc một ý) cắt tối đa chừng này ký tự. */
    public static final int MAX_CHARS = 200;
    /** Dòng mở đầu danh sách trong prompt. */
    public static final String HEADER = "Các câu đã sinh trước đó từ cùng câu hỏi nguồn (mới nhất trước). KHÔNG được "
            + "lặp lại bài toán, phương trình, biểu thức hay số liệu của các câu này:";

    public PreviousQuestion {
        statements = statements == null ? List.of() : new ArrayList<>(statements).stream()
                .map(s -> s == null ? "" : s).toList();
    }

    /** Thu khoảng trắng (kể cả xuống dòng) về một dấu cách, rồi cắt tối đa {@link #MAX_CHARS} ký tự kèm "…". */
    static String clip(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        if (flat.codePointCount(0, flat.length()) <= MAX_CHARS) {
            return flat;
        }
        return flat.substring(0, flat.offsetByCodePoints(0, MAX_CHARS)) + "…";
    }

    /**
     * Đoạn prompt liệt kê các câu đã sinh; rỗng khi danh sách rỗng. Chỉ dùng {@link #MAX_ITEMS} câu đầu.
     *
     * @param trueFalse true thì liệt kê đề dẫn kèm 4 ý a-d
     */
    static String section(List<PreviousQuestion> previous, boolean trueFalse) {
        if (previous == null || previous.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(HEADER).append("\n");
        List<String> keys = List.of("a", "b", "c", "d");
        int n = Math.min(previous.size(), MAX_ITEMS);
        for (int i = 0; i < n; i++) {
            PreviousQuestion q = previous.get(i);
            sb.append(i + 1).append(". ");
            if (!trueFalse) {
                sb.append(clip(q.questionText())).append("\n");
                continue;
            }
            sb.append("Đề dẫn: ").append(clip(q.questionText())).append("\n");
            for (int k = 0; k < Math.min(q.statements().size(), keys.size()); k++) {
                sb.append("   ").append(keys.get(k)).append(") ").append(clip(q.statements().get(k))).append("\n");
            }
        }
        return sb.toString();
    }
}
