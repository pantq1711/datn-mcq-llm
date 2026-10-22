package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Difficulty;

import java.util.List;

/**
 * BẢN NHÁP v0 – CHỜ THÀNH VIÊN PHỤ TRÁCH PROMPT DUYỆT – KHÔNG DÙNG CHO THỰC NGHIỆM C1/C2/C3.
 *
 * <p>Prompt sinh câu Đúng/Sai và prompt Solver cho câu Đúng/Sai (thiết kế bản sửa 1, mục 10; GĐ3). Không sửa
 * {@link PromptBuilder}; chỉ dùng lại {@link PromptBuilder#ESCAPE_HINT}. Nhãn bản nháp nằm ở Javadoc và ở
 * {@link #PROMPT_VERSION} (ghi vào experiment_run.prompt_version), không gửi cho model.
 *
 * <p>Schema Gemini khai propertyOrdering: đề dẫn, 4 ý, lời giải, rồi 4 nhãn, để nhãn dựa vào lời giải vừa viết.
 * Schema Solver: reasoning_x rồi judge_x, lần lượt a đến d, để ý sau dùng được kết quả ý trước.
 */
public final class TfPromptDraftV0 {

    public static final String PROMPT_VERSION = "tf-v0-draft";

    public static final String GENERATION_RESPONSE_SCHEMA_JSON = """
            {"type":"OBJECT","properties":{
              "question_text":{"type":"STRING"},
              "statement_a":{"type":"STRING"},
              "statement_b":{"type":"STRING"},
              "statement_c":{"type":"STRING"},
              "statement_d":{"type":"STRING"},
              "explanation":{"type":"STRING"},
              "label_a":{"type":"STRING","enum":["T","F"]},
              "label_b":{"type":"STRING","enum":["T","F"]},
              "label_c":{"type":"STRING","enum":["T","F"]},
              "label_d":{"type":"STRING","enum":["T","F"]}
            },"required":["question_text","statement_a","statement_b","statement_c","statement_d","explanation",
                          "label_a","label_b","label_c","label_d"],
            "propertyOrdering":["question_text","statement_a","statement_b","statement_c","statement_d","explanation",
                                "label_a","label_b","label_c","label_d"]}
            """;

    public static final String SOLVER_RESPONSE_SCHEMA_JSON = """
            {"type":"OBJECT","properties":{
              "reasoning_a":{"type":"STRING"},
              "judge_a":{"type":"STRING","enum":["T","F","undecidable"]},
              "reasoning_b":{"type":"STRING"},
              "judge_b":{"type":"STRING","enum":["T","F","undecidable"]},
              "reasoning_c":{"type":"STRING"},
              "judge_c":{"type":"STRING","enum":["T","F","undecidable"]},
              "reasoning_d":{"type":"STRING"},
              "judge_d":{"type":"STRING","enum":["T","F","undecidable"]}
            },"required":["reasoning_a","judge_a","reasoning_b","judge_b","reasoning_c","judge_c","reasoning_d",
                          "judge_d"],
            "propertyOrdering":["reasoning_a","judge_a","reasoning_b","judge_b","reasoning_c","judge_c","reasoning_d",
                                "judge_d"]}
            """;

    private static final List<String> KEYS = List.of("a", "b", "c", "d");

    private TfPromptDraftV0() {}

    /** @param statementDifficulties mức mục tiêu của 4 ý theo thứ tự a-d */
    public static String buildGenerationPrompt(
            String subjectName,
            Integer gradeLevel,
            String topic,
            String learningOutcome,
            String sourceQuestion,
            List<Difficulty> statementDifficulties) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là giáo viên Toán THCS biên soạn 1 câu hỏi ĐÚNG/SAI: một đề dẫn chung và đúng 4 ý a, b, c, d.\n");
        sb.append("Môn học: ").append(subjectName != null ? subjectName : "Toán").append("\n");
        if (gradeLevel != null) {
            sb.append("Khối lớp: ").append(gradeLevel).append("\n");
        }
        if (topic != null && !topic.isBlank()) {
            sb.append("Chủ đề: ").append(topic).append("\n");
        }
        if (learningOutcome != null && !learningOutcome.isBlank()) {
            sb.append("Yêu cầu cần đạt: ").append(learningOutcome).append("\n");
        }
        if (sourceQuestion != null && !sourceQuestion.isBlank()) {
            sb.append("Câu hỏi nguồn / ngữ liệu tham khảo: ").append(sourceQuestion).append("\n");
        }
        if (statementDifficulties != null && statementDifficulties.size() == 4) {
            StringBuilder levels = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                levels.append(i > 0 ? "; " : "").append("ý ").append(KEYS.get(i)).append(": ")
                        .append(levelLabel(statementDifficulties.get(i)));
            }
            sb.append("Mức độ mục tiêu: ").append(levels).append(".\n");
        }
        sb.append("""

                Yêu cầu:
                - Mỗi ý là một khẳng định (không phải câu hỏi), xác định được chắc chắn là Đúng hoặc Sai.
                - Các ý có thể nối tiếp nhau (điều kiện xác định, rút gọn, tính giá trị...), nhưng mỗi ý phải tự kết \
                luận được khi đã có đề dẫn. Không ghi "a)", "b)" ở đầu nội dung ý.
                - Không bắt buộc số ý Đúng hay số ý Sai.
                """);
        sb.append("\n").append(PromptBuilder.ESCAPE_HINT).append("\n");
        sb.append("""
                Viết lời giải cho lần lượt từng ý trong "explanation" TRƯỚC, sau đó mới ghi nhãn.
                Trả về DUY NHẤT một JSON theo thứ tự: "question_text", "statement_a", "statement_b", "statement_c", \
                "statement_d", "explanation", "label_a", "label_b", "label_c", "label_d" ("T" = Đúng, "F" = Sai).
                """);
        return sb.toString();
    }

    /** Solver chỉ thấy đề dẫn và 4 ý, không thấy nhãn và lời giải của người soạn. */
    public static String buildSolverPrompt(String questionText, List<String> statements) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < KEYS.size(); i++) {
            String s = statements != null && i < statements.size() ? statements.get(i) : "";
            items.append(KEYS.get(i)).append(") ").append(s).append("\n");
        }
        return """
                Bạn là giáo viên Toán THCS thẩm định độc lập một câu hỏi Đúng/Sai.
                Dưới đây là đề dẫn và 4 ý (không kèm nhãn của người soạn):

                Đề dẫn: %s
                %s
                Yêu cầu: xét lần lượt các ý a, b, c, d. Với mỗi ý, tự giải ở "reasoning_x" rồi ghi "judge_x":
                - "T" nếu ý đó đúng, "F" nếu ý đó sai;
                - "undecidable" nếu không thể kết luận chắc chắn (ý mơ hồ, thiếu dữ kiện, có nhiều cách hiểu).
                Được dùng kết quả của ý trước khi xét ý sau.
                %s
                Trả về DUY NHẤT một JSON có 8 trường theo thứ tự: "reasoning_a", "judge_a", "reasoning_b", "judge_b", \
                "reasoning_c", "judge_c", "reasoning_d", "judge_d".
                """.formatted(questionText, items, PromptBuilder.ESCAPE_HINT);
    }

    /** Nhãn mức độ theo Ma trận đề kiểm tra cấp 2 (docs/contract/enums.md, enum Difficulty). */
    private static String levelLabel(Difficulty d) {
        if (d == null) {
            return "?";
        }
        return switch (d) {
            case EASY -> "Biết";
            case MEDIUM -> "Hiểu";
            case HARD -> "Vận dụng";
        };
    }
}
