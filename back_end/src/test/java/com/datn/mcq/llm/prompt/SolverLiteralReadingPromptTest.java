package com.datn.mcq.llm.prompt;

import com.datn.mcq.qc.checker.AnswerSolverChecker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prompt Solver mcq v4 (chốt 31/10 sau lần đo v3 được 0/9): phương án ĐÚNG nếu học sinh bảo vệ được theo đúng chữ của đề,
 * không thêm yêu cầu đề không nêu.
 */
class SolverLiteralReadingPromptTest {

    private final PromptBuilder builder = new PromptBuilder();

    private String v4Prompt() {
        return builder.buildSolverPrompt("Phân tích $2x^3 - 6x^2$ thành nhân tử", "$2x(x^2 - 3x)$",
                "$2x^2(x - 3)$", "$x^2(2x - 6)$", "$2(x^3 - 3x^2)$");
    }

    @Test
    void v4LaMacDinh() {
        assertEquals("solver-v4-literal-reading", PromptBuilder.SOLVER_PROMPT_VERSION);
        assertEquals(PromptBuilder.SOLVER_PROMPT_VERSION, AnswerSolverChecker.SOLVER_PROMPT_VERSION);
        assertEquals(v4Prompt(), builder.buildSolverPrompt("solver-v4-literal-reading",
                "Phân tích $2x^3 - 6x^2$ thành nhân tử", "$2x(x^2 - 3x)$", "$2x^2(x - 3)$", "$x^2(2x - 6)$",
                "$2(x^3 - 3x^2)$"));
    }

    @Test
    void v4CoQuyTacDocDungChuCuaDe() {
        String p = v4Prompt();
        assertTrue(p.contains("học sinh bảo vệ được nó là đáp án theo ĐÚNG CHỮ CỦA ĐỀ"), p);
        assertTrue(p.contains("không thêm yêu cầu mà đề không nêu"), p);
        // Phân tích nhân tử
        assertTrue(p.contains("mọi phương án là một TÍCH và nhân lại bằng đúng đa thức đã cho đều ĐÚNG"), p);
        assertTrue(p.contains("kể cả khi chưa phân tích triệt để"), p);
        assertTrue(p.contains("\"triệt để\", \"đặt nhân tử chung lớn nhất\""), p);
        // Tính chất, khẳng định hình học
        assertTrue(p.contains("đề không có chữ \"luôn\" (hoặc tương đương)"), p);
        assertTrue(p.contains("đúng trong ít nhất một trường hợp hợp lệ của hình đã cho"), p);
        // Tổng quát: đề mơ hồ
        assertTrue(p.contains("correct_count phải từ 2 trở lên"), p);
        assertTrue(p.contains("phát hiện đề mơ hồ, không phải đoán ý người ra đề"), p);
    }

    @Test
    void v4CoHaiViDu() {
        String p = v4Prompt();
        assertTrue(p.contains("Đề \"Phân tích $2x^3 - 6x^2$ thành nhân tử\" (không ghi \"triệt để\")"), p);
        assertTrue(p.contains("phương án $2x(x^2 - 3x)$ là ĐÚNG"), p);
        assertTrue(p.contains("\"Hai đường chéo vuông góc với nhau\""), p);
        assertTrue(p.contains("là ĐÚNG khi đề không ghi \"luôn\""), p);
        assertTrue(p.contains("có hình thang cân có hai đường chéo vuông góc"), p);
    }

    @Test
    void v4BoQuyTacHepCuaV3VaGiuCacPhanKhac() {
        String p = v4Prompt();
        assertFalse(p.contains("không \"luôn\" đúng, là sai"), "quy tắc hẹp của v3 không còn trong v4");
        assertFalse(p.contains("phương án có đúng dạng đề yêu cầu"), "cụm dễ hiểu thành \"triệt để\" của v3 bỏ ở v4");
        assertTrue(p.contains("Xét TỪNG phương án A, B, C, D"));
        assertTrue(p.contains("KHÔNG dừng lại khi đã thấy một phương án đúng"));
        assertTrue(p.contains(PromptBuilder.SOLVER_INSTRUCTION));
        assertEquals(p.indexOf("<<<"), p.lastIndexOf("<<<"));
        assertEquals(p.indexOf(">>>"), p.lastIndexOf(">>>"));
        assertTrue(p.contains("<<<CÂU_HỎI_CẦN_GIẢI\nCâu hỏi: Phân tích $2x^3 - 6x^2$ thành nhân tử\nA. $2x(x^2 - 3x)$"));
        assertTrue(p.trim().endsWith("Trả về DUY NHẤT một đối tượng JSON có 4 trường theo thứ tự: \"reasoning\", "
                + "\"options_verdict\", \"correct_count\", \"judge_answer\"."), "cùng JSON với v3");
    }

    @Test
    void v3VaV4DungChungSchemaXetTungPhuongAn() {
        // Checker chỉ đổi schema cho v2; v3, v4 cùng options_verdict.
        assertTrue(AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON.contains("options_verdict"));
    }
}
