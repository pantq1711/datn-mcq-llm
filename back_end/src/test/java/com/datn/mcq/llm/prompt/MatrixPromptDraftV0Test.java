package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prompt sinh câu theo ô ma trận (phần C mục 2): sinh từ chủ đề, nội dung, yêu cầu cần đạt (không có câu mẫu); quy tắc
 * đa dạng; chủ đề hình học dặn không cần hình vẽ (quyết định 5); đầu ra dùng lại hợp đồng JSON mcq/tf/sa.
 */
class MatrixPromptDraftV0Test {

    private static MatrixCellSpec spec(QuestionType type, String topic, String content) {
        Map<Difficulty, String> outcomes = new LinkedHashMap<>();
        outcomes.put(Difficulty.EASY, "Nhận biết được đơn thức.");
        outcomes.put(Difficulty.MEDIUM, "Thu gọn được đa thức.");
        outcomes.put(Difficulty.HARD, "Chứng minh biểu thức không phụ thuộc biến.");
        return switch (type) {
            case MCQ -> new MatrixCellSpec("Toán", 8, topic, content, Map.of(Difficulty.EASY, "Nhận biết được đơn thức."),
                    QuestionType.MCQ, Difficulty.EASY, null);
            case SA -> new MatrixCellSpec("Toán", 8, topic, content, Map.of(Difficulty.MEDIUM, "Tính được góc."),
                    QuestionType.SA, Difficulty.MEDIUM, null);
            case TF -> new MatrixCellSpec("Toán", 8, topic, content, outcomes, QuestionType.TF, null,
                    List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD));
        };
    }

    @Test
    void promptMcqTuYeuCauCanDatKhongCoCauMau() {
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec(QuestionType.MCQ, "Đa thức", "Đơn thức, đa thức"));
        for (String part : List.of("TRẮC NGHIỆM 4 LỰA CHỌN", "Khối lớp: 8", "Chủ đề: Đa thức",
                "Nội dung: Đơn thức, đa thức", "- Biết: Nhận biết được đơn thức.", "Mức độ nhận thức mục tiêu: Biết",
                MatrixPromptDraftV0.NEW_PROBLEM_RULE, "đủ 7 trường", "\"correct_option\" (A/B/C/D)",
                PromptBuilder.ESCAPE_HINT.strip())) {
            assertTrue(p.contains(part), "Prompt thiếu: " + part);
        }
        assertFalse(p.contains("Câu hỏi nguồn"), "sinh từ yêu cầu cần đạt, không có câu mẫu");
        assertFalse(p.contains(MatrixPromptDraftV0.NO_FIGURE_RULE));
        assertFalse(p.contains("BẢN NHÁP"));
    }

    @Test
    void promptTfNeuMucTungYVaDungLaiQuyTacTf() {
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec(QuestionType.TF, "Đa thức", "Các phép tính đa thức"));
        for (String part : List.of("ĐÚNG/SAI", "ý a: Biết; ý b: Biết; ý c: Hiểu; ý d: Vận dụng",
                "- Hiểu: Thu gọn được đa thức.", "- Vận dụng: Chứng minh biểu thức không phụ thuộc biến.",
                TfPromptDraftV0.STATEMENT_RULES.strip(), TfPromptDraftV0.OUTPUT_INSTRUCTION.strip())) {
            assertTrue(p.contains(part), "Prompt thiếu: " + part);
        }
    }

    @Test
    void promptSaHinhHocDanKhongCanHinhVe() {
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec(QuestionType.SA, "Tứ giác", "Tứ giác, hình thang cân"));
        for (String part : List.of("TRẢ LỜI NGẮN", "Mức độ nhận thức mục tiêu: Hiểu", MatrixPromptDraftV0.NO_FIGURE_RULE,
                SaPromptDraftV0.ANSWER_RULES.strip(), SaPromptDraftV0.OUTPUT_INSTRUCTION.strip())) {
            assertTrue(p.contains(part), "Prompt thiếu: " + part);
        }
        assertTrue(MatrixPromptDraftV0.isGeometry(spec(QuestionType.MCQ, "TỨ GIÁC", null)), "không phân biệt hoa thường");
        assertFalse(MatrixPromptDraftV0.isGeometry(spec(QuestionType.MCQ, "Hằng đẳng thức", "Phân tích đa thức")));
    }

    @Test
    void danhSachCauDaSinhCuaO() {
        List<PreviousQuestion> prev = List.of(new PreviousQuestion("Đơn thức nào sau đây có bậc 3?", List.of()));
        MatrixCellSpec s = spec(QuestionType.MCQ, "Đa thức", null);
        String p = MatrixPromptDraftV0.buildGenerationPrompt(s, prev);
        assertTrue(p.contains(PreviousQuestion.section(prev, false)), p);
        assertFalse(MatrixPromptDraftV0.buildGenerationPrompt(s).contains(PreviousQuestion.HEADER));
    }

    @Test
    void phienBanVaThongSoOLuuDuoc() {
        assertEquals("matrix-mcq-v1-draft", MatrixPromptDraftV0.promptVersion(QuestionType.MCQ));
        assertEquals("matrix-tf-v1-draft", MatrixPromptDraftV0.promptVersion(QuestionType.TF));
        assertTrue(MatrixPromptDraftV0.isMatrixVersion("matrix-sa-v0-draft"));
        assertFalse(MatrixPromptDraftV0.isMatrixVersion("sa-v1-draft"));
        assertFalse(MatrixPromptDraftV0.isMatrixVersion(null));
        for (QuestionType type : QuestionType.values()) {
            MatrixCellSpec s = spec(type, "Tứ giác \"có ngoặc\"", "Nội dung\ncó xuống dòng");
            assertEquals(s, MatrixCellSpec.fromJson(s.toJson()), type.name());
        }
    }

    /** Ngày 23/10 (mục 9): phương án nhiễu phải sai về giá trị, không tương đương đáp án. */
    @Test
    void mcqPhuongAnNhieuPhaiSaiVeGiaTri() {
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec(QuestionType.MCQ, "Hằng đẳng thức", "Phân tích"));
        assertTrue(p.contains("Mỗi phương án nhiễu phải SAI về giá trị, không được là biểu thức tương đương với đáp án"
                + " đúng"), p);
        assertTrue(p.contains("$3(x^2 - 4x)$ khi đáp án là $3x(x - 4)$"), p);
        assertFalse(MatrixPromptDraftV0.buildGenerationPrompt(spec(QuestionType.SA, "Tứ giác", null))
                .contains("phương án nhiễu"));
    }

    /** Ngày 23/10 (mục 6): phần TF của prompt ma trận dùng chung quy tắc ý với TfPromptDraftV0. */
    @Test
    void tfMaTranCoQuyTacYBamDeDan() {
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec(QuestionType.TF, "Đa thức", null));
        assertTrue(p.contains("phải nói về chính đối tượng trong đề dẫn"), p);
        assertTrue(p.contains("phải đóng bằng $ trước dấu câu"), p);
    }

    /** Ngày 23/10 (mục 5, 8): ghi chú riêng của job (câu thứ k/n, lý do lần sinh trước bị loại) chèn vào prompt. */
    @Test
    void ghiChuCuaJobDuocChenVaoPrompt() {
        MatrixCellSpec s = spec(QuestionType.MCQ, "Đa thức", null);
        String note = "Đây là câu thứ 2/4 của ô này.";
        String p = MatrixPromptDraftV0.buildGenerationPrompt(s, List.of(), note);
        assertTrue(p.contains("\n" + note + "\n"), p);
        assertTrue(p.indexOf(note) < p.indexOf("đủ 7 trường"), "ghi chú đứng trước yêu cầu đầu ra");
        assertEquals(MatrixPromptDraftV0.buildGenerationPrompt(s, List.of()),
                MatrixPromptDraftV0.buildGenerationPrompt(s, List.of(), null));
        assertEquals("Đây là câu thứ 2/4 của ô này: dạng hỏi phải khác các câu còn lại của ô (không chỉ đổi số liệu);"
                + " nếu yêu cầu cần đạt có nhiều ý, mỗi câu kiểm tra một ý khác.", MatrixPromptDraftV0.slotLine(2, 4));
        assertNull(MatrixPromptDraftV0.slotLine(1, 1));
    }

    @Test
    void lyDoLanSinhTruocToiDa300KyTu() {
        assertEquals("Lần sinh trước bị loại vì: question_text: số dấu $ lẻ. Tránh lặp lại lỗi này.",
                MatrixPromptDraftV0.retryHint(List.of("question_text: số dấu $ lẻ")));
        String longHint = MatrixPromptDraftV0.retryHint(List.of("x".repeat(500), "y"));
        assertEquals(300, longHint.length());
        assertTrue(longHint.endsWith("…. Tránh lặp lại lỗi này."), longHint);
        assertNull(MatrixPromptDraftV0.retryHint(List.of()));
    }
}
