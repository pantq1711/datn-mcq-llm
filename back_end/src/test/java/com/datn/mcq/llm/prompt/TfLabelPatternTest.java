package com.datn.mcq.llm.prompt;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mẫu nhãn bắt buộc cho câu Đúng/Sai trong đề theo ma trận (ngày 23/10, việc 1). */
class TfLabelPatternTest {

    private static long trueCount(String pattern) {
        return pattern.chars().filter(c -> c == 'T').count();
    }

    @Test
    void chi14MauCoTu1Den3YDung() {
        assertEquals(14, TfLabelPattern.ALL.size());
        for (String p : TfLabelPattern.ALL) {
            assertEquals(4, p.length());
            assertTrue(p.matches("[TF]{4}"), p);
            assertTrue(trueCount(p) >= 1 && trueCount(p) <= 3, p);
        }
        assertEquals(14, new HashSet<>(TfLabelPattern.ALL).size());
    }

    @Test
    void chonNgauNhienTranhMauCuaCauGanNhat() {
        Random random = new Random(42);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String p = TfLabelPattern.choose("TTFF", random);
            assertNotEquals("TTFF", p);
            assertTrue(TfLabelPattern.ALL.contains(p), p);
            seen.add(p);
        }
        assertEquals(13, seen.size(), "mọi mẫu khác đều có thể được chọn");
        Set<String> any = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            any.add(TfLabelPattern.choose(null, random));
        }
        assertEquals(14, any.size());
    }

    @Test
    void dongPromptVaDocLaiTuGhiChu() {
        String line = TfLabelPattern.promptLine("TFFT");
        assertEquals("Mẫu nhãn bắt buộc (T,F,F,T): ý a phải Đúng, ý b phải Sai, ý c phải Sai, ý d phải Đúng. "
                + "Soạn nội dung và lời giải từng ý cho khớp mẫu này.", line);
        String note = "Đây là câu thứ 2/3 của ô này: ...\n" + line;
        assertEquals("TFFT", TfLabelPattern.fromNote(note));
        assertNull(TfLabelPattern.fromNote("Đây là câu thứ 2/3 của ô này: ..."));
        assertNull(TfLabelPattern.fromNote(null));
    }

    @Test
    void canhBaoKhiNhanKhacMauBatBuoc() {
        assertNull(TfLabelPattern.mismatchWarning("TFFT", List.of("T", "F", "F", "T")));
        assertEquals("label: khác mẫu bắt buộc T,F,F,T (nhận T,T,F,F)",
                TfLabelPattern.mismatchWarning("TFFT", List.of("T", "T", "F", "F")));
        assertNull(TfLabelPattern.mismatchWarning("TFFT", List.of("T", "x", "F", "T")),
                "nhãn không hợp lệ là lỗi format, không cảnh báo thêm");
        assertNull(TfLabelPattern.mismatchWarning("TFFT", List.of("T", "F")));
        assertNull(TfLabelPattern.mismatchWarning(null, List.of("T", "T", "F", "F")));
    }
}
