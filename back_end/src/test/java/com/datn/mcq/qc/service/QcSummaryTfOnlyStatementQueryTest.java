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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-07 (phần rẻ): {@link QcSummaryService#summarize} chỉ truy vấn {@code answer_check_statement} khi câu là Đúng/Sai; mcq và sa
 * không có dòng ý nên truy vấn đó là thừa (xem đề N câu gọi summarize N lần).
 */
class QcSummaryTfOnlyStatementQueryTest {

    private QualityCheckResultRepository results;
    private AnswerCheckStatementRepository statements;
    private QcSummaryService service;

    @BeforeEach
    void setUp() {
        results = mock(QualityCheckResultRepository.class);
        statements = mock(AnswerCheckStatementRepository.class);
        QuestionGroundingRepository grounding = mock(QuestionGroundingRepository.class);
        when(results.findByQuestionIdAndCheckType(anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(grounding.findByQuestionId(anyInt())).thenReturn(Optional.empty());
        service = new QcSummaryService(results, statements, grounding);
    }

    private QualityCheckResult answerCheck(QuestionType type) {
        QualityCheckResult r = mock(QualityCheckResult.class);
        when(r.getId()).thenReturn(50);
        when(r.getQuestionType()).thenReturn(type);
        when(r.getPassed()).thenReturn(true);
        when(results.findByQuestionIdAndCheckType(1, CheckType.ANSWER_CHECK)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void cauMcqVaSaKhongTruyVanYCuaCauDungSai() {
        for (QuestionType type : List.of(QuestionType.MCQ, QuestionType.SA)) {
            answerCheck(type);
            assertEquals(false, service.summarize(1).hasUndecidable(), type.name());
        }
        verify(statements, never()).findByQcResultIdOrderByPositionAsc(anyInt());
    }

    @Test
    void cauDungSaVanTruyVanVaNhanRaYKhongKetLuanDuoc() {
        answerCheck(QuestionType.TF);
        AnswerCheckStatement undecidable = mock(AnswerCheckStatement.class);
        when(undecidable.getJudgeLabel()).thenReturn(TruthChoice.UNDECIDABLE);
        when(statements.findByQcResultIdOrderByPositionAsc(50)).thenReturn(List.of(undecidable));

        assertTrue(service.summarize(1).hasUndecidable());
        verify(statements).findByQcResultIdOrderByPositionAsc(50);
    }
}
