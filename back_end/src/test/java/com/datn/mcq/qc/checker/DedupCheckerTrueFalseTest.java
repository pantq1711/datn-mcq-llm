package com.datn.mcq.qc.checker;

import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupOutcome;
import com.datn.mcq.qc.dto.DedupStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Dedup câu Đúng/Sai (GĐ3): văn bản chuẩn = đề dẫn + 4 ý đã chuẩn hóa và sắp xếp (không gồm nhãn); chốt chặn số liệu
 * gộp đề dẫn và 4 ý; chốt chặn phủ định so phủ định của đề dẫn và danh sách phủ định của 4 ý đã sắp xếp;
 * chỉ so với câu cùng dạng.
 */
class DedupCheckerTrueFalseTest {

    private static final double TAU = DedupChecker.PROVISIONAL_THRESHOLD;
    private static final String STEM = "Cho biểu thức $P = \\frac{x^2 - 1}{x^2 - x}$ với $x \\neq 0$ và $x \\neq 1$.";
    private static final List<String> STATEMENTS = List.of(
            "Rút gọn được $P = \\frac{x+1}{x}$.",
            "Tại $x = 2$ thì $P = 2$.",
            "Có đúng hai giá trị nguyên của $x$ để $P$ nguyên.",
            "Giá trị của $P$ tại $x = -1$ bằng $0$.");

    private static DedupOutcome check(DedupCandidate subject, DedupCandidate... candidates) {
        return DedupChecker.check(subject, List.of(candidates), TAU);
    }

    @Test
    void daoThuTuYVanTrungDauVanTay() {
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM,
                        List.of(STATEMENTS.get(3), STATEMENTS.get(1), STATEMENTS.get(0), STATEMENTS.get(2))),
                DedupCandidate.trueFalse(1, STEM, STATEMENTS));
        assertEquals(DedupStatus.DUPLICATE, out.status());
        assertEquals(DedupOutcome.Layer.FINGERPRINT, out.layer());
    }

    @Test
    void khacSoLieuTrongMotYKhongTrung() {
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM,
                        List.of(STATEMENTS.get(0), "Tại $x = 3$ thì $P = 2$.", STATEMENTS.get(2), STATEMENTS.get(3))),
                DedupCandidate.trueFalse(1, STEM, STATEMENTS));
        assertEquals(DedupStatus.NO_DUPLICATE, out.status());
    }

    @Test
    void motYBiPhuDinhKhongTrung() {
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM,
                        List.of(STATEMENTS.get(0), "Tại $x = 2$ thì $P$ không bằng $2$.", STATEMENTS.get(2),
                                STATEMENTS.get(3))),
                DedupCandidate.trueFalse(1, STEM, STATEMENTS));
        assertEquals(DedupStatus.NO_DUPLICATE, out.status());
    }

    @Test
    void cungMotYPhuDinhVanSoBangCosine() {
        // Hai câu đều có đúng một ý phủ định và cùng số liệu, nên qua hai chốt chặn và so bằng cosine.
        List<String> first = List.of(STATEMENTS.get(0), STATEMENTS.get(1), STATEMENTS.get(2),
                "Giá trị của $P$ tại $x = -1$ không bằng $0$.");
        List<String> second = List.of(STATEMENTS.get(0), STATEMENTS.get(1), STATEMENTS.get(2),
                "Giá trị của $P$ tại $x = -1$ không bằng $0$ đâu.");
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM, second), DedupCandidate.trueFalse(1, STEM, first));
        assertEquals(DedupStatus.DUPLICATE, out.status());
        assertEquals(DedupOutcome.Layer.TRIGRAM, out.layer());
    }

    @Test
    void khongSoVoiCauKhacDang() {
        DedupCandidate mcq = new DedupCandidate(1, STEM, STATEMENTS.get(0), STATEMENTS.get(1), STATEMENTS.get(2),
                STATEMENTS.get(3));
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM, STATEMENTS), mcq);
        assertEquals(DedupStatus.NO_DUPLICATE, out.status());
        assertEquals("Không trùng với 0 ứng viên", out.detail());
    }

    @Test
    void cauTfThieuYKhongSoDuoc() {
        assertThrows(IllegalArgumentException.class, () -> check(DedupCandidate.trueFalse(2, STEM,
                List.of("a", "b", "c"))));
    }
}
