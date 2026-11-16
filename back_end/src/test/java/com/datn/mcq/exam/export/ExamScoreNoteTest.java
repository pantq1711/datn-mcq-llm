package com.datn.mcq.exam.export;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import com.datn.mcq.exam.service.ExamScore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NV-41 (quyết định 16/11, mục 5): file Word ghi chú thang điểm ở phần đáp án: "Tổng điểm phần trắc nghiệm: X điểm."; khi
 * X &lt; 10 thêm dòng "Phần tự luận (nếu có) do giáo viên bổ sung." Không đổi thang điểm (vẫn theo app.exam.scoring). Tổng
 * điểm tính như tiêu đề từng phần: điểm một câu × số chỗ của phần (kể cả chỗ chưa có câu).
 */
class ExamScoreNoteTest {

    private static final ExamScoringProperties SCORING = ExamMarkdownBuilder.DEFAULT_SCORING;
    private static final ExamHeader HEADER = ExamHeader.of("Giữa kì I – Toán 8", "Toán", 8, null);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private static ExamItemDto mcq(int slotNo) {
        return new ExamItemDto(slotNo, 1, slotNo, "I", "Đa thức", QuestionType.MCQ, "ready", "generated", null,
                100 + slotNo, "Câu hỏi " + slotNo + "?", "$1$", "$2$", "$3$", "$4$", Option.B, null, null, "Lời giải.",
                Difficulty.EASY, false, List.of());
    }

    private static ExamItemDto tf(int slotNo) {
        List<ItemStatementDto> statements = List.of(
                new ItemStatementDto(StatementKey.A, "Ý a.", TruthValue.TRUE, Difficulty.EASY),
                new ItemStatementDto(StatementKey.B, "Ý b.", TruthValue.FALSE, Difficulty.EASY),
                new ItemStatementDto(StatementKey.C, "Ý c.", TruthValue.TRUE, Difficulty.EASY),
                new ItemStatementDto(StatementKey.D, "Ý d.", TruthValue.FALSE, Difficulty.EASY));
        return new ExamItemDto(slotNo, 2, slotNo, "II", "Hằng đẳng thức", QuestionType.TF, "ready", "generated", null,
                200 + slotNo, "Cho biểu thức " + slotNo + ".", null, null, null, null, null, null, statements,
                "Lời giải.", null, false, List.of());
    }

    private static ExamItemDto sa(int slotNo) {
        return new ExamItemDto(slotNo, 3, slotNo, "III", "Tứ giác", QuestionType.SA, "ready", "generated", null,
                300 + slotNo, "Tính " + slotNo + ".", null, null, null, null, null, "5", null, "Lời giải.",
                Difficulty.MEDIUM, false, List.of());
    }

    private static ExamItemDto failedMcq(int slotNo) {
        return new ExamItemDto(slotNo, 1, slotNo, "I", "Đa thức", QuestionType.MCQ, "failed", null, null, null, null,
                null, null, null, null, null, null, null, null, null, false, List.of(), null, false, null,
                "qc_exhausted");
    }

    private static ExamViewDto exam(int mcq, int tf, int sa, int failedMcq) {
        List<ExamItemDto> p1 = new ArrayList<>();
        for (int i = 1; i <= mcq; i++) {
            p1.add(mcq(i));
        }
        for (int i = 1; i <= failedMcq; i++) {
            p1.add(failedMcq(mcq + i));
        }
        List<ExamItemDto> p2 = new ArrayList<>();
        for (int i = 1; i <= tf; i++) {
            p2.add(tf(i));
        }
        List<ExamItemDto> p3 = new ArrayList<>();
        for (int i = 1; i <= sa; i++) {
            p3.add(sa(i));
        }
        return new ExamViewDto(5, "MT1", "Đề thử", "teacher", ExamStatus.DRAFT, 3, LocalDateTime.of(2026, 11, 16, 8, 0),
                "ready", 0, List.of(new PartDto("I", p1.size(), mcq, p1), new PartDto("II", tf, tf, p2),
                new PartDto("III", sa, sa, p3)), "matrix");
    }

    @Test
    void tongDiemTinhTheoSoChoCuaTungPhanKeCaChoChuaCoCau() {
        // mẫu Giữa kì I Toán 8: 12 mcq + 2 tf + 4 sa = 3 + 2 + 2 = 7 điểm
        assertEquals(0, new BigDecimal("7").compareTo(ExamScore.total(exam(12, 2, 4, 0).parts(), SCORING)));
        // 20 mcq + 4 tf + 6 sa = 5 + 4 + 3 = 12 điểm
        assertEquals(0, new BigDecimal("12").compareTo(ExamScore.total(exam(20, 4, 6, 0).parts(), SCORING)));
        // chỗ hỏng vẫn tính (tiêu đề phần cũng tính)
        assertEquals(0, new BigDecimal("3").compareTo(ExamScore.total(exam(11, 0, 0, 1).parts(), SCORING)));
    }

    @Test
    void tongDuoiMuoiGhiTongVaDongTuLuan() {
        String md = ExamMarkdownBuilder.build(exam(12, 2, 4, 0), HEADER, SCORING, TODAY);
        assertTrue(md.contains("Tổng điểm phần trắc nghiệm: 7,0 điểm."), md);
        assertTrue(md.contains("Phần tự luận (nếu có) do giáo viên bổ sung."), md);
        int answers = md.indexOf(ExamMarkdownBuilder.ANSWER_TITLE);
        assertTrue(answers > 0 && md.indexOf("Tổng điểm phần trắc nghiệm") > answers,
                "ghi chú nằm ở phần đáp án, không in trên đề của học sinh");
    }

    @Test
    void tongBangMuoiKhongCoDongTuLuan() {
        // 16 mcq + 4 tf + 4 sa = 4 + 4 + 2 = 10 điểm
        String md = ExamMarkdownBuilder.build(exam(16, 4, 4, 0), HEADER, SCORING, TODAY);
        assertTrue(md.contains("Tổng điểm phần trắc nghiệm: 10,0 điểm."), md);
        assertFalse(md.contains("Phần tự luận"), md);
    }

    @Test
    void tongTrenMuoiChiGhiTongKhongDoiThangDiem() {
        String md = ExamMarkdownBuilder.build(exam(20, 4, 6, 0), HEADER, SCORING, TODAY);
        assertTrue(md.contains("Tổng điểm phần trắc nghiệm: 12,0 điểm."), md);
        assertFalse(md.contains("Phần tự luận"), md);
        assertTrue(md.contains("(5,0 điểm)"), "thang điểm từng phần giữ nguyên");
    }
}
