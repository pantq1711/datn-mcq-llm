package com.datn.mcq.qc.service;

import com.datn.mcq.enums.AnswerChoice;
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

    private static List<String> solverReasons(AnswerChoice judge, boolean optionCheck) {
        return new QcSummary(true, false, null, false, List.of(), null, null, judge, optionCheck).reviewReasons();
    }

    /** Thẩm định 31/10: Solver v3 xét từng phương án tách lý do "nhiều phương án đúng" và "không có phương án đúng". */
    @Test
    void solverXetTungPhuongAnTachLyDoNhieuPhuongAnDungVaKhongCoPhuongAnDung() {
        assertEquals(List.of("solver_multiple_correct"), solverReasons(AnswerChoice.MULTIPLE, true));
        assertEquals(List.of("solver_no_correct"), solverReasons(AnswerChoice.NONE, true));
        assertEquals(List.of("solver_inconsistent"), solverReasons(AnswerChoice.C, true),
                "một phương án đúng nhưng khác đáp án: như cũ");
        assertEquals(List.of(), new QcSummary(true, true, null, false, List.of(), null, null, AnswerChoice.B, true)
                .reviewReasons());
    }

    /** Kết quả không có khối xét từng phương án (Solver cũ, dòng lưu trước v3): giữ lý do cũ, không gắn cờ mới. */
    @Test
    void solverCuKhongXetTungPhuongAnGiuLyDoSolverInconsistent() {
        assertEquals(List.of("solver_inconsistent"), solverReasons(AnswerChoice.MULTIPLE, false));
        assertEquals(List.of("solver_inconsistent"), solverReasons(AnswerChoice.NONE, false));
        assertEquals(List.of("solver_inconsistent"), new QcSummary(true, false, null, false, List.of()).reviewReasons());
    }

    @Test
    void canhBaoTuDongDongDoLaVanBatCanXemKy() {
        assertEquals(List.of("warning: statement_b: đã tự đóng $"),
                reasons("json: đã sửa 1 escape LaTeX (\\circ)", "statement_b: đã tự đóng $"));
        assertEquals(List.of("warning: label: 4 ý cùng nhãn T"), reasons("label: 4 ý cùng nhãn T"));
    }

    /**
     * Từ 26/10 mẫu nhãn tf là mục tiêu: nhãn lệch mẫu vì tính đúng là hợp lệ, nên không bật "cần xem kỹ" (cảnh báo vẫn lưu ở
     * warnings và hiện trong vết QC để thống kê mức tuân thủ). Dòng cũ ghi "bắt buộc" cũng vậy.
     */
    @Test
    void canhBaoLechMauMucTieuKhongBatCanXemKy() {
        assertEquals(List.of(), reasons("label: khác mẫu mục tiêu T,F,F,T (nhận T,T,F,F)"));
        assertEquals(List.of(), reasons("label: khác mẫu bắt buộc T,F,F,T (nhận T,T,F,F)"), "dữ liệu trước 26/10");
        assertEquals(List.of("warning: label: 4 ý cùng nhãn T", "warning: statement_b: đã tự đóng $"),
                reasons("label: khác mẫu mục tiêu T,T,T,T (nhận T,T,T,F)", "label: 4 ý cùng nhãn T",
                        "statement_b: đã tự đóng $"), "các cảnh báo khác cùng lúc vẫn bật cờ");
    }
}
