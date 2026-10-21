package com.datn.mcq.qc.orchestrator;

import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bảng quyết định đầy đủ cho Q-2: chỉ (format đạt, dedup NO_DUPLICATE, answer_check CONSISTENT) mới QC_PASSED.
 */
class QcStatusDeciderTest {

    @Test
    void onlyFormatPassedNoDuplicateAndConsistentAnswerReachesQcPassed() {
        for (DedupStatus dedup : DedupStatus.values()) {
            for (AnswerCheckStatus answer : AnswerCheckStatus.values()) {
                QuestionStatus expected =
                        (dedup == DedupStatus.NO_DUPLICATE && answer == AnswerCheckStatus.CONSISTENT)
                                ? QuestionStatus.QC_PASSED
                                : QuestionStatus.GENERATED;
                assertEquals(expected, QcStatusDecider.decide(true, dedup, answer),
                        "format=đạt, dedup=" + dedup + ", answer_check=" + answer);
            }
        }
    }

    @Test
    void formatFailureAlwaysWinsRegardlessOfOtherSteps() {
        for (DedupStatus dedup : DedupStatus.values()) {
            for (AnswerCheckStatus answer : AnswerCheckStatus.values()) {
                assertEquals(QuestionStatus.FORMAT_FAILED, QcStatusDecider.decide(false, dedup, answer),
                        "format=lỗi, dedup=" + dedup + ", answer_check=" + answer);
            }
        }
    }

    @Test
    void nullInputsAreTreatedAsNotRunAndNeverPass() {
        assertEquals(QuestionStatus.GENERATED, QcStatusDecider.decide(true, null, AnswerCheckStatus.CONSISTENT));
        assertEquals(QuestionStatus.GENERATED, QcStatusDecider.decide(true, DedupStatus.NO_DUPLICATE, null));
        assertEquals(QuestionStatus.GENERATED, QcStatusDecider.decide(true, null, null));
    }
}
