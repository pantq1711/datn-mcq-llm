package com.datn.mcq.qc.service;

import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.qc.entity.AnswerCheckStatement;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.AnswerCheckStatementRepository;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import com.datn.mcq.qc.repository.QuestionGroundingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-07: {@link QcSummaryService#summarizeAll} nạp QC của cả tập câu bằng số truy vấn cố định, và chỉ truy vấn
 * {@code answer_check_statement} khi trong tập có câu Đúng/Sai đã có answer_check (mcq và sa không có dòng ý).
 * {@link QcSummaryService#summarize} dùng chung logic đó.
 */
class QcSummaryTfOnlyStatementQueryTest {

    private QualityCheckResultRepository results;
    private AnswerCheckStatementRepository statements;
    private QuestionGroundingRepository grounding;
    private QcSummaryService service;
    private final List<QualityCheckResult> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        results = mock(QualityCheckResultRepository.class);
        statements = mock(AnswerCheckStatementRepository.class);
        grounding = mock(QuestionGroundingRepository.class);
        when(results.findByQuestionIdIn(anyCollection())).thenAnswer(i -> List.copyOf(rows));
        when(grounding.findByQuestionIdIn(anyCollection())).thenReturn(List.of());
        service = new QcSummaryService(results, statements, grounding);
    }

    private QualityCheckResult row(int questionId, CheckType type, QuestionType questionType, int rowId) {
        QualityCheckResult r = mock(QualityCheckResult.class);
        when(r.getId()).thenReturn(rowId);
        when(r.getQuestionId()).thenReturn(questionId);
        when(r.getCheckType()).thenReturn(type);
        when(r.getQuestionType()).thenReturn(questionType);
        when(r.getPassed()).thenReturn(true);
        rows.add(r);
        return r;
    }

    private AnswerCheckStatement label(int qcResultId, TruthChoice judge) {
        AnswerCheckStatement s = mock(AnswerCheckStatement.class);
        when(s.getQcResultId()).thenReturn(qcResultId);
        when(s.getJudgeLabel()).thenReturn(judge);
        return s;
    }

    @Test
    void cauMcqVaSaKhongTruyVanYCuaCauDungSai() {
        row(1, CheckType.ANSWER_CHECK, QuestionType.MCQ, 50);
        row(2, CheckType.ANSWER_CHECK, QuestionType.SA, 51);
        Map<Integer, QcSummaryService.QcSummary> all = service.summarizeAll(List.of(1, 2));
        assertFalse(all.get(1).hasUndecidable());
        assertFalse(all.get(2).hasUndecidable());
        verify(statements, never()).findByQcResultIdIn(anyCollection());
    }

    @Test
    void cauDungSaVanTruyVanMotLanVaNhanRaYKhongKetLuanDuoc() {
        row(1, CheckType.ANSWER_CHECK, QuestionType.TF, 50);
        row(2, CheckType.ANSWER_CHECK, QuestionType.TF, 51);
        row(3, CheckType.ANSWER_CHECK, QuestionType.MCQ, 52);
        List<AnswerCheckStatement> labels = List.of(label(50, TruthChoice.UNDECIDABLE), label(51, TruthChoice.TRUE));
        when(statements.findByQcResultIdIn(anyCollection())).thenReturn(labels);

        Map<Integer, QcSummaryService.QcSummary> all = service.summarizeAll(List.of(1, 2, 3));

        assertTrue(all.get(1).hasUndecidable());
        assertFalse(all.get(2).hasUndecidable());
        assertFalse(all.get(3).hasUndecidable());
        verify(statements, times(1)).findByQcResultIdIn(anyCollection());
    }

    @Test
    void soTruyVanCoDinhKhongPhuThuocSoCau() {
        List<Integer> ids = new ArrayList<>();
        for (int q = 1; q <= 40; q++) {
            ids.add(q);
            row(q, CheckType.FORMAT, QuestionType.TF, 1000 + q);
            row(q, CheckType.DEDUP, QuestionType.TF, 2000 + q);
            row(q, CheckType.ANSWER_CHECK, QuestionType.TF, 3000 + q);
        }
        Map<Integer, QcSummaryService.QcSummary> all = service.summarizeAll(ids);

        assertEquals(40, all.size());
        assertTrue(all.values().stream().allMatch(QcSummaryService.QcSummary::dedupRan));
        verify(results, times(1)).findByQuestionIdIn(anyCollection());
        verify(statements, times(1)).findByQcResultIdIn(anyCollection());
        verify(grounding, times(1)).findByQuestionIdIn(anyCollection());
    }

    @Test
    void cauChuaCoDongQcVanCoTomTatSolverChuaChayVaDedupChuaChay() {
        QcSummaryService.QcSummary s = service.summarizeAll(List.of(7)).get(7);

        assertFalse(s.dedupRan());
        assertEquals(null, s.answerCheckPassed());
        assertEquals(List.of("solver_not_run", "dedup_not_run"), s.reviewReasons());
        assertEquals(Map.of(), service.summarizeAll(List.of()));
    }

    @Test
    void summarizeMotCauBangDungKetQuaCuaSummarizeAll() {
        row(1, CheckType.ANSWER_CHECK, QuestionType.TF, 50);
        List<AnswerCheckStatement> labels = List.of(label(50, TruthChoice.UNDECIDABLE));
        when(statements.findByQcResultIdIn(anyCollection())).thenReturn(labels);

        assertEquals(service.summarizeAll(List.of(1)).get(1), service.summarize(1));
        assertTrue(service.summarize(1).hasUndecidable());
    }
}
