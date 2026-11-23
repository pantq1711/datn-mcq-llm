package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.QuestionType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D4 (docs/design/cau-co-hinh.md mục 4.3, 4.6 "chưa xác minh"): Đổi bằng sinh mới từ chỗ có hình gọi
 * {@link MatrixPromptDraftV0#replaceReasonBlock} với {@code oldQuestion = null}: khối vẫn có lý do, không có đoạn "Câu bị đổi"
 * hay đáp số cũ, không có token ảnh. Văn bản prompt khi có câu cũ không đổi (các test sẵn có của khối lý do).
 */
class ReplaceReasonWithoutOldQuestionTest {

    @ParameterizedTest
    @EnumSource(QuestionType.class)
    void khongCoCauCuThiChiCoLyDo(QuestionType type) {
        for (String reason : new String[] {"too_easy", "too_hard"}) {
            String block = MatrixPromptDraftV0.replaceReasonBlock(reason, null, type, null);
            assertNotNull(block, type + " " + reason);
            assertTrue(block.startsWith(MatrixPromptDraftV0.REASON_PREFIX), block);
            assertTrue(block.contains("too_easy".equals(reason) ? "QUÁ DỄ" : "QUÁ KHÓ"), block);
            assertFalse(block.contains("Câu bị đổi (DỮ LIỆU"), block);
            assertFalse(block.contains("Đáp số câu bị đổi"), block);
            assertFalse(block.contains("<<<"), block);
            assertFalse(block.contains("⟦img:"), block);
            assertFalse(block.contains("\n"), "một dòng: " + block);
        }
    }
}
