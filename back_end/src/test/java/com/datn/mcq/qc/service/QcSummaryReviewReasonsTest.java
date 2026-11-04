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

    /** Lời giải có "tính lại"/"làm lại"/"sửa lại": mã riêng, không phải "warning: ..." (cảnh báo vẫn lưu ở warnings). */
    @Test
    void loiGiaiCoTheConNhapBatMaRiengKhongPhaiWarning() {
        assertEquals(List.of("explanation_draft_suspect"),
                reasons("explanation: lời giải có thể còn phần nháp (tính lại, làm lại, sửa lại)"));
        assertEquals(List.of("explanation_draft_suspect", "warning: question_text: Lệnh \\foo không hỗ trợ"),
                reasons("explanation: lời giải có thể còn phần nháp (tính lại, làm lại, sửa lại)",
                        "question_text: Lệnh \\foo không hỗ trợ"));
        assertEquals("explanation_draft_suspect", QcSummaryService.EXPLANATION_DRAFT_SUSPECT);
    }

    @Test
    void canhBaoNhomCanNguoiXemVanBatCo() {
        assertEquals(List.of("warning: label: 4 ý cùng nhãn T"), reasons("label: 4 ý cùng nhãn T"));
    }

    /**
     * 2/11 (sau đề 11): sau khi lưu đủ cảnh báo, chỉ nhóm "hệ thống đã tự sửa chắc chắn" không bật cờ; nhóm còn lại, kể cả cảnh
     * báo chưa biết, bật cờ (mặc định an toàn). Cảnh báo luôn được lưu và hiện ở vết QC, chỉ lý do "cần xem kỹ" khác nhau.
     */
    @Test
    void nhomHeThongDaTuSuaChacChanKhongBatCo() {
        for (String fixed : List.of(
                "json: đã sửa 1 escape LaTeX (\\circ)",
                "json: đã sửa 2 \\text{} ngoài $ (đã gỡ vỏ) ở question_text",
                "json: đã sửa 1 ký hiệu độ (^\\bullet, ^\\triangle thành ^{\\circ}) ở option_b",
                "statement_b: đã tự đóng $",
                "option_a: đã tự đóng $",
                "explanation: đã tự bọc $ quanh công thức LaTeX nằm ngoài $",
                "statement_c: đã tự bọc $ quanh 2 công thức LaTeX nằm ngoài $",
                "statement_a: đã bỏ tiền tố \"a)\" ở đầu ý",
                "label: khác mẫu mục tiêu T,F,F,T (nhận T,T,F,F)")) {
            assertEquals(List.of(), reasons(fixed), fixed);
        }
        assertEquals(List.of(), reasons("json: đã sửa 1 escape LaTeX (\\circ)", "option_a: đã tự bọc $ quanh công thức LaTeX nằm ngoài $"),
                "chỉ có việc tự sửa thì câu không có lý do review");
    }

    @Test
    void nhomCanNguoiXemVaCanhBaoLaBatCo() {
        for (String needsLook : List.of(
                "question_text: Lệnh \\toRadians không hỗ trợ hoặc có thể sai chính tả",
                "option_b: môi trường foo không hỗ trợ",
                "explanation: Có lệnh LaTeX nằm ngoài cặp $...$ (thiếu dấu $ bao quanh công thức)",
                "explanation: ngoặc { } không cân bằng",
                "Lời giải còn chứa phần nháp/tự sửa; chỉ ghi lời giải cuối cùng.",
                "question_text: có từ tiếng Anh ngoài công thức (and)",
                "label: 4 ý cùng nhãn F",
                "answer: phân số chưa tối giản",
                "một cảnh báo chưa ai biết",
                "statement_b: đã tự sửa thứ gì đó mới")) {
            assertEquals(List.of("warning: " + needsLook), reasons(needsLook), needsLook);
        }
        assertEquals(List.of("warning: question_text: Lệnh \\toRadians không hỗ trợ"),
                reasons("json: đã sửa 1 escape LaTeX (\\toRadians)", "option_a: đã tự bọc $ quanh công thức LaTeX nằm ngoài $",
                        "question_text: Lệnh \\toRadians không hỗ trợ"), "câu 190 của đề 11: chỉ lý do thật còn lại");
    }

    @Test
    void danhSachNhomKhongBatCoCoTen() {
        assertEquals(true, QcSummaryService.isAutoFixedWarning("statement_x: đã tự đóng $"));
        assertEquals(false, QcSummaryService.isAutoFixedWarning("statement_x: đã tự đóng ngoặc"));
        assertEquals(false, QcSummaryService.isAutoFixedWarning(null));
    }

    /**
     * Từ 26/10 mẫu nhãn tf là mục tiêu: nhãn lệch mẫu vì tính đúng là hợp lệ, nên không bật "cần xem kỹ" (cảnh báo vẫn lưu ở
     * warnings và hiện trong vết QC để thống kê mức tuân thủ). Dòng cũ ghi "bắt buộc" cũng vậy.
     */
    /** 3/11: chữ đúng của câu mcq khác chữ đã giao chỉ ghi vào QC để thống kê, không bật "cần xem kỹ" (chưa sinh lại). */
    @Test
    void chuDungMcqKhacChuDaGiaoKhongBatCanXemKy() {
        assertEquals(List.of(), reasons("correct_option: khác chữ đã giao C (nhận A)"));
        assertEquals(List.of("warning: question_text: có từ tiếng Anh ngoài công thức (and)"),
                reasons("correct_option: khác chữ đã giao C (nhận A)", "question_text: có từ tiếng Anh ngoài công thức (and)"));
    }

    @Test
    void canhBaoLechMauMucTieuKhongBatCanXemKy() {
        assertEquals(List.of(), reasons("label: khác mẫu mục tiêu T,F,F,T (nhận T,T,F,F)"));
        assertEquals(List.of(), reasons("label: khác mẫu bắt buộc T,F,F,T (nhận T,T,F,F)"), "dữ liệu trước 26/10");
        assertEquals(List.of("warning: label: 4 ý cùng nhãn T"),
                reasons("label: khác mẫu mục tiêu T,T,T,T (nhận T,T,T,F)", "label: 4 ý cùng nhãn T",
                        "statement_b: đã tự đóng $"), "các cảnh báo cần người xem cùng lúc vẫn bật cờ");
    }
}
