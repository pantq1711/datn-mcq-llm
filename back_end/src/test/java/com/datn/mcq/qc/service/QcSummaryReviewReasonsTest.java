package com.datn.mcq.qc.service;

import com.datn.mcq.qc.dto.GroundingVerdict;
import com.datn.mcq.qc.service.QcSummaryService.QcSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Lý do "cần xem kỹ" từ cảnh báo format (ngày 23/10, việc 2). */
class QcSummaryReviewReasonsTest {

    private static List<String> reasons(String... warnings) {
        return new QcSummary(true, true, null, false, List.of(warnings)).reviewReasons();
    }

    @Test
    void canhBaoDaSuaEscapeLatexKhongBatCanXemKy() {
        assertEquals(List.of(), reasons("json: đã sửa 1 escape LaTeX (\\circ)"));
        assertEquals(List.of(), reasons("json: đã sửa 3 escape LaTeX (\\frac, \\times)"));
    }

    private static List<String> reasonsWith(GroundingVerdict grounding) {
        return new QcSummary(true, true, null, false, List.of(), null, grounding).reviewReasons();
    }

    @Test
    void ketLuanBamTaiLieuChiCanhBaoKhiKhongKhopHoacKhongKetLuanDuoc() {
        assertEquals(List.of(), reasonsWith(null), "câu không dùng tài liệu hoặc chưa chấm");
        assertEquals(List.of(), reasonsWith(GroundingVerdict.SUPPORTED));
        assertEquals(List.of("grounding_not_supported"), reasonsWith(GroundingVerdict.NOT_SUPPORTED));
        assertEquals(List.of("grounding_unknown"), reasonsWith(GroundingVerdict.UNKNOWN));
    }

    @Test
    void canhBaoTuDongDongDoLaVanBatCanXemKy() {
        assertEquals(List.of("warning: statement_b: đã tự đóng $"),
                reasons("json: đã sửa 1 escape LaTeX (\\circ)", "statement_b: đã tự đóng $"));
        assertEquals(List.of("warning: label: khác mẫu bắt buộc T,F,F,T (nhận T,T,F,F)"),
                reasons("label: khác mẫu bắt buộc T,F,F,T (nhận T,T,F,F)"));
    }
}
