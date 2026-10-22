package com.datn.mcq.qc.checker;

import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupOutcome;
import com.datn.mcq.qc.dto.DedupStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Dedup câu Trả lời ngắn (bản sửa 1 mục 6): văn bản chuẩn = thân câu + khóa chuẩn của đáp số; chốt chặn số liệu
 * và phủ định xét thân câu; chỉ so với câu cùng dạng.
 */
class DedupCheckerShortAnswerTest {

    private static final double TAU = DedupChecker.PROVISIONAL_THRESHOLD;
    private static final String STEM = "Nghiệm của phương trình $4x + 3 = 0$ là bao nhiêu?";

    @Test
    void cungDeCungGiaTriKhacCachVietLaTrungDauVanTay() {
        DedupOutcome out = DedupChecker.check(DedupCandidate.shortAnswer(2, STEM, "-0,75"),
                List.of(DedupCandidate.shortAnswer(1, STEM, "$-\\frac{3}{4}$")), TAU);
        assertEquals(DedupStatus.DUPLICATE, out.status());
        assertEquals(DedupOutcome.Layer.FINGERPRINT, out.layer());
        assertEquals(1, out.comparedToQuestionId());
    }

    @Test
    void cungDeKhacGiaTriKhongTrungDauVanTay() {
        DedupOutcome out = DedupChecker.check(DedupCandidate.shortAnswer(2, STEM, "0,33"),
                List.of(DedupCandidate.shortAnswer(1, STEM, "1/3")), TAU);
        assertNotEquals(DedupOutcome.Layer.FINGERPRINT, out.layer());
    }

    @Test
    void khacSoLieuTrongDeKhongTrung() {
        DedupOutcome out = DedupChecker.check(
                DedupCandidate.shortAnswer(2, "Nghiệm của phương trình $4x + 5 = 0$ là bao nhiêu?", "-1,25"),
                List.of(DedupCandidate.shortAnswer(1, STEM, "-0,75")), TAU);
        assertEquals(DedupStatus.NO_DUPLICATE, out.status());
    }

    @Test
    void lechPhuDinhKhongTrung() {
        DedupOutcome out = DedupChecker.check(
                DedupCandidate.shortAnswer(2, "Số nghiệm không âm của phương trình $x^2 = 4$ là bao nhiêu?", "1"),
                List.of(DedupCandidate.shortAnswer(1, "Số nghiệm âm của phương trình $x^2 = 4$ là bao nhiêu?", "1")),
                TAU);
        assertEquals(DedupStatus.NO_DUPLICATE, out.status());
    }

    @Test
    void khongSoVoiCauKhacDang() {
        DedupCandidate mcq = new DedupCandidate(1, STEM, "$-\\frac{3}{4}$", "$\\frac{3}{4}$", "$-3$", "$3$");
        DedupOutcome out = DedupChecker.check(DedupCandidate.shortAnswer(2, STEM, "-0,75"), List.of(mcq), TAU);
        assertEquals(DedupStatus.NO_DUPLICATE, out.status());
        assertEquals(null, out.comparedToQuestionId());
        assertEquals("Không trùng với 0 ứng viên", out.detail());
    }

    @Test
    void ungVienCoDapSoKhongPhanTichDuocKhongGayLoi() {
        DedupOutcome out = DedupChecker.check(DedupCandidate.shortAnswer(2, STEM, "-0,75"),
                List.of(DedupCandidate.shortAnswer(1, STEM, "x = 2")), TAU);
        assertNotEquals(DedupOutcome.Layer.FINGERPRINT, out.layer());
        DedupOutcome same = DedupChecker.check(DedupCandidate.shortAnswer(2, STEM, "x  =  2"),
                List.of(DedupCandidate.shortAnswer(1, STEM, "x = 2")), TAU);
        assertEquals(DedupOutcome.Layer.FINGERPRINT, same.layer(), "chuỗi thô so sau khi bỏ khoảng trắng");
    }

    @Test
    void cauSaThieuDapSoKhongSoDuoc() {
        assertThrows(IllegalArgumentException.class,
                () -> DedupChecker.check(DedupCandidate.shortAnswer(2, STEM, " "), List.of(), TAU));
    }
}
