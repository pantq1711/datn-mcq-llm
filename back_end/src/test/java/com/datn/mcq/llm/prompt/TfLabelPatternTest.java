package com.datn.mcq.llm.prompt;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mẫu nhãn mục tiêu cho câu Đúng/Sai trong đề theo ma trận (ngày 23/10 là "bắt buộc"; đổi thành mục tiêu sau đo 26/10). */
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

    /**
     * W2-5: mẫu là BẮT BUỘC: model tính toán kết quả trong explanation trước rồi đối chiếu mẫu để viết ý;
     * nhãn theo đúng/sai thật, được lệch mẫu khi không viết được ý hợp lý (chỉ cảnh báo).
     */
    @Test
    void dongPromptNoiMauLaBatBuocVaNhanPhaiTheoDungSaiThat() {
        String line = TfLabelPattern.promptLine("TFFT");
        assertTrue(line.startsWith("Mẫu nhãn bắt buộc (T,F,F,T): cố gắng viết ý a Đúng, ý b Sai, ý c Sai, ý d Đúng."), line);
        for (String phrase : List.of(
                "Trong explanation tính kết quả đúng cho từng ý rồi đối chiếu với mẫu",
                "ý cần Đúng thì giữ nguyên kết quả đúng",
                "ý cần Sai thì đổi hệ số, dấu hoặc kết luận so với kết quả đã tính",
                "Nhãn PHẢI khớp tính đúng/sai THẬT của khẳng định",
                "Nếu không viết được một ý theo đúng nhãn bắt buộc một cách hợp lý, hãy ưu tiên tính đúng",
                "gán nhãn thật cho ý đó (được lệch mẫu)",
                "Không gán nhãn trái với lời giải",
                "lời giải chỉ giải toán và kết luận, TUYỆT ĐỐI KHÔNG nhắc tới mẫu nhãn hay việc gán nhãn")) {
            assertTrue(line.contains(phrase), phrase + " | " + line);
        }
        assertFalse(line.contains("\n"), "một dòng ghi chú");
    }

    @Test
    void docLaiMauTuGhiChuMoiVaCu() {
        String line = TfLabelPattern.promptLine("TFFT");
        assertEquals("TFFT", TfLabelPattern.fromNote("Đây là câu thứ 2/3 của ô này: ...\n" + line));
        // job xếp hàng trước khi đổi chữ (và dữ liệu cũ) vẫn đọc được mẫu
        assertEquals("TFFT", TfLabelPattern.fromNote("Đây là câu thứ 2/3 của ô này: ...\nMẫu nhãn mục tiêu (T,F,F,T): ý a phải"
                + " Đúng, ý b phải Sai, ý c phải Sai, ý d phải Đúng. Soạn nội dung và lời giải từng ý cho khớp mẫu này."));
        assertEquals("TFFT", TfLabelPattern.fromNote("Đây là câu thứ 2/3 của ô này: ...\nMẫu nhãn bắt buộc (T,F,F,T): ý a phải"
                + " Đúng, ý b phải Sai, ý c phải Sai, ý d phải Đúng. Soạn nội dung và lời giải từng ý cho khớp mẫu này."));
        assertNull(TfLabelPattern.fromNote("Đây là câu thứ 2/3 của ô này: ..."));
        assertNull(TfLabelPattern.fromNote(null));
    }

    @Test
    void nhanDienCanhBaoLechMauKeCaDuLieuCu() {
        String warning = TfLabelPattern.mismatchWarning("TFFT", List.of("T", "T", "F", "F"));
        assertTrue(TfLabelPattern.isDeviationWarning(warning), warning);
        assertTrue(TfLabelPattern.isDeviationWarning("label: khác mẫu mục tiêu T,F,F,T (nhận T,T,F,F)"));
        assertTrue(TfLabelPattern.isDeviationWarning("label: khác mẫu bắt buộc T,F,F,T (nhận T,T,F,F)"));
        assertFalse(TfLabelPattern.isDeviationWarning("label: 4 ý cùng nhãn T"));
        assertFalse(TfLabelPattern.isDeviationWarning("statement_b: đã tự đóng $"));
        assertFalse(TfLabelPattern.isDeviationWarning(""));
        assertFalse(TfLabelPattern.isDeviationWarning(null));
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
