package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Strategy;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public static final String ESCAPE_HINT = """
            Quy tắc định dạng công thức và JSON:
            - Mọi công thức Toán viết bằng LaTeX và đặt trong cặp dấu $...$ (ví dụ $\\frac{a}{b}$, $\\sqrt{x}$, $x \\neq 0$).
            - Không dùng \\( ... \\) hoặc \\[ ... \\]. Không để lệnh LaTeX nằm ngoài cặp $...$.
            - Vì đầu ra là JSON, mọi dấu gạch chéo ngược \\ trong LaTeX PHẢI viết thành hai dấu \\\\ (ví dụ "\\\\frac{1}{2}", "\\\\sqrt{9}", "\\\\neq", "\\\\ge", "\\\\le", "\\\\cdot").
            """;

    private static final String FEW_SHOT_MATH_EXAMPLES = """
            Ví dụ mẫu:
            {"question_text": "Rút gọn phân thức $\\\\frac{4x^2y}{8xy^3}$ với $x \\\\neq 0, y \\\\neq 0$ ta được:", "option_a": "$\\\\frac{x}{2y^2}$", "option_b": "$\\\\frac{2x}{y^2}$", "option_c": "$\\\\frac{x}{4y^2}$", "option_d": "$\\\\frac{x}{2y}$", "correct_option": "A", "explanation": "Chia cả tử và mẫu cho nhân tử chung $4xy$: $\\\\frac{4x^2y}{8xy^3} = \\\\frac{x}{2y^2}$."}
            """;

    public String buildGenerationPrompt(
            String subjectName,
            Integer gradeLevel,
            String topic,
            String learningOutcome,
            String sourceQuestion,
            Strategy strategy,
            Difficulty targetDifficulty) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là giáo viên THCS biên soạn câu hỏi trắc nghiệm 4 lựa chọn.\n");
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
        if (targetDifficulty != null) {
            sb.append("Mức độ nhận thức mục tiêu: ").append(targetDifficulty.getDbValue()).append("\n");
        }
        if (sourceQuestion != null && !sourceQuestion.isBlank()) {
            sb.append("Câu hỏi nguồn / ngữ liệu tham khảo: ").append(sourceQuestion).append("\n");
        }
        sb.append("\n").append(ESCAPE_HINT).append("\n");
        if (strategy == Strategy.FEW_SHOT || strategy == Strategy.FEW_SHOT_STRUCTURED) {
            sb.append(FEW_SHOT_MATH_EXAMPLES).append("\n");
        }
        sb.append("""
                Hãy biên soạn 1 câu hỏi trắc nghiệm mới có đúng 1 đáp án đúng.
                Kiểm tra kỹ tính toán ở bước giải thích để đảm bảo trường "correct_option" khớp chính xác với phương án đúng.
                Trả về DUY NHẤT một đối tượng JSON có đủ 7 trường:
                "question_text", "option_a", "option_b", "option_c", "option_d", "correct_option" (A/B/C/D), "explanation".
                """);
        return sb.toString();
    }

    public String buildSolverPrompt(
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD) {
        return """
                Bạn là giáo viên THCS chấm thẩm định độc lập một câu hỏi trắc nghiệm.
                Dưới đây là đề bài và 4 phương án A, B, C, D (không kèm đáp án của người soạn):

                Câu hỏi: %s
                A. %s
                B. %s
                C. %s
                D. %s

                Yêu cầu:
                1. Tự giải bài toán từng bước từ đề bài ở trường "reasoning" (không đoán mò theo phương án).
                2. Đối chiếu kết quả vừa giải được với cả 4 phương án A, B, C, D để điền trường "judge_answer":
                   - Ghi "A", "B", "C", hoặc "D" nếu có ĐÚNG MỘT phương án đúng hoàn toàn.
                   - Ghi "multiple" nếu có từ 2 phương án trở lên cùng đúng.
                   - Ghi "none" nếu KHÔNG có phương án nào trong A, B, C, D khớp với kết quả đúng của đề bài.
                %s
                Trả về DUY NHẤT một đối tượng JSON có 2 trường: "reasoning" và "judge_answer".
                """.formatted(questionText, optionA, optionB, optionC, optionD, ESCAPE_HINT);
    }
}

