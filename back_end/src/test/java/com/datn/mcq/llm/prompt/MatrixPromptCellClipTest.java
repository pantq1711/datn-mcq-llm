package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2: dữ liệu cũ (ô ma trận lưu trước khi có giới hạn độ dài) vẫn bị cắt an toàn khi dựng prompt: content tối đa 1000 ký
 * tự, mỗi yêu cầu cần đạt tối đa 500, yêu cầu thêm của ô tối đa 1000. Phần trong giới hạn giữ nguyên.
 */
class MatrixPromptCellClipTest {

    private static MatrixCellSpec spec(String content, String outcome, String extra) {
        return new MatrixCellSpec("Toán", 8, "Đa thức", content, Map.of(Difficulty.EASY, outcome), QuestionType.MCQ,
                Difficulty.EASY, null, extra);
    }

    @Test
    void contentDaiHon1000KyTuBiCatKhiDungPrompt() {
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec("a".repeat(1000) + "ZZZ", "Nhận biết.", null));
        assertTrue(p.contains("Nội dung: " + "a".repeat(1000) + "\n"), "giữ đủ 1000 ký tự đầu");
        assertFalse(p.contains("ZZZ"), "phần vượt 1000 ký tự bị cắt");
    }

    @Test
    void yeuCauCanDatDaiHon500KyTuBiCat() {
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec("Nội dung", "b".repeat(500) + "YYY", null));
        assertTrue(p.contains(": " + "b".repeat(500) + "\n"), "giữ đủ 500 ký tự đầu");
        assertFalse(p.contains("YYY"), "phần vượt 500 ký tự bị cắt");
    }

    @Test
    void yeuCauThemCuaODaiHon1000KyTuBiCat() {
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec("Nội dung", "Nhận biết.", "c".repeat(1000) + "WWW"));
        assertTrue(p.contains("c".repeat(1000)));
        assertFalse(p.contains("WWW"));
    }

    @Test
    void khongCatCapSurrogateGiuaChung() {
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec("😀".repeat(1001), "Nhận biết.", null));
        assertTrue(p.contains("😀".repeat(1000) + "\n"), "đếm theo ký tự người dùng thấy, không cắt đôi emoji");
        assertFalse(p.contains("😀".repeat(1001)));
    }

    @Test
    void dulieuTrongGioiHanGiuNguyen() {
        String content = "d".repeat(1000);
        String outcome = "e".repeat(500);
        String p = MatrixPromptDraftV0.buildGenerationPrompt(spec(content, outcome, null));
        assertTrue(p.contains("Nội dung: " + content + "\n"));
        assertTrue(p.contains(outcome));
    }
}
