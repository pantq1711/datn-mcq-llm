package com.datn.mcq.questionimage;

import com.datn.mcq.questionimage.service.QuestionImageToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionImageTokenTest {

    @Test
    @DisplayName("format: tạo đúng định dạng ⟦img:<id>⟧")
    void formatDungDinhDang() {
        assertEquals("⟦img:812⟧", QuestionImageToken.format(812L));
        assertEquals("⟦img:1⟧", QuestionImageToken.format(1L));
        assertThrows(IllegalArgumentException.class, () -> QuestionImageToken.format(0L));
        assertThrows(IllegalArgumentException.class, () -> QuestionImageToken.format(-5L));
    }

    @Test
    @DisplayName("parseId: bóc tách id chính xác từ token")
    void parseIdChinhXac() {
        assertEquals(812L, QuestionImageToken.parseId("⟦img:812⟧"));
        assertEquals(123456L, QuestionImageToken.parseId("  ⟦img:123456⟧  "));
        assertThrows(IllegalArgumentException.class, () -> QuestionImageToken.parseId("⟦img:abc⟧"));
        assertThrows(IllegalArgumentException.class, () -> QuestionImageToken.parseId("⟦img:-1⟧"));
        assertThrows(IllegalArgumentException.class, () -> QuestionImageToken.parseId("img:812"));
        assertThrows(IllegalArgumentException.class, () -> QuestionImageToken.parseId(null));
    }

    @Test
    @DisplayName("containsToken: phát hiện sự tồn tại của token")
    void containsTokenPhatHienDung() {
        assertTrue(QuestionImageToken.containsToken("Cho tam giác ABC như hình vẽ:\n⟦img:812⟧"));
        assertTrue(QuestionImageToken.containsToken("⟦img:1⟧"));
        assertFalse(QuestionImageToken.containsToken("Không có hình ở đây"));
        assertFalse(QuestionImageToken.containsToken("⟦img:abc⟧"));
        assertFalse(QuestionImageToken.containsToken(null));
        assertFalse(QuestionImageToken.containsToken(""));
    }

    @Test
    @DisplayName("extractIds: trích xuất danh sách toàn bộ ID theo thứ tự")
    void extractIdsTraVeDanhSachDungThuTu() {
        String text = "Đề bài có hình 1: ⟦img:101⟧, hình 2: ⟦img:202⟧ và hình 3: ⟦img:303⟧";
        assertEquals(List.of(101L, 202L, 303L), QuestionImageToken.extractIds(text));
        assertTrue(QuestionImageToken.extractIds("Không có hình").isEmpty());
    }

    @Test
    @DisplayName("strip: xóa toàn bộ token khỏi văn bản")
    void stripXoaHetToken() {
        String text = "Cho hình vẽ ⟦img:812⟧. Tính độ dài cạnh AB.";
        assertEquals("Cho hình vẽ . Tính độ dài cạnh AB.", QuestionImageToken.strip(text));
        assertEquals("Câu không có hình", QuestionImageToken.strip("Câu không có hình"));
    }

    @Test
    @DisplayName("sanitizeWordText: thoát ký tự ⟦ và ⟧ thành [ và ]")
    void sanitizeWordTextThoatKyTuDacBiet() {
        String raw = "Ký hiệu tập hợp ⟦1, 2, 3⟧ trong Word";
        assertEquals("Ký hiệu tập hợp [1, 2, 3] trong Word", QuestionImageToken.sanitizeWordText(raw));
    }

    @Test
    @DisplayName("containsDisallowedPrefix: phát hiện chuỗi ⟦img:")
    void containsDisallowedPrefixKiemTraTienTo() {
        assertTrue(QuestionImageToken.containsDisallowedPrefix("Người dùng tự gõ ⟦img:999⟧"));
        assertTrue(QuestionImageToken.containsDisallowedPrefix("Người dùng gõ dở ⟦img:"));
        assertFalse(QuestionImageToken.containsDisallowedPrefix("Đoạn văn bình thường [img:123]"));
    }
}
