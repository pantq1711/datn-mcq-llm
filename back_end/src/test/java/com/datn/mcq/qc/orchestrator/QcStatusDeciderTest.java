package com.datn.mcq.qc.orchestrator;

import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bảng quyết định đầy đủ cho Q-2: chỉ (format đạt, dedup NO_DUPLICATE, answer_check CONSISTENT) mới QC_PASSED.
 * Sửa 2026-10-21 theo quyết định 7: thêm DedupStatus.DUPLICATE (trạng thái cuối DUPLICATE) và DedupStatus.ERROR
 * (giữ GENERATED). Các tổ hợp còn lại giữ nguyên kỳ vọng cũ.
 */
class QcStatusDeciderTest {

    @Test
    void onlyFormatPassedNoDuplicateAndConsistentAnswerReachesQcPassed() {
        for (DedupStatus dedup : DedupStatus.values()) {
            for (AnswerCheckStatus answer : AnswerCheckStatus.values()) {
                QuestionStatus expected;
                if (dedup == DedupStatus.DUPLICATE) {
                    expected = QuestionStatus.DUPLICATE;
                } else if (dedup == DedupStatus.NO_DUPLICATE && answer == AnswerCheckStatus.CONSISTENT) {
                    expected = QuestionStatus.QC_PASSED;
                } else {
                    expected = QuestionStatus.GENERATED;
                }
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
    void duplicateIsFinalEvenWhenAnswerCheckWouldBeConsistent() {
        assertEquals(QuestionStatus.DUPLICATE,
                QcStatusDecider.decide(true, DedupStatus.DUPLICATE, AnswerCheckStatus.CONSISTENT));
        assertEquals(QuestionStatus.DUPLICATE,
                QcStatusDecider.decide(true, DedupStatus.DUPLICATE, AnswerCheckStatus.NOT_RUN));
    }

    @Test
    void dedupErrorKeepsGeneratedEvenWhenAnswerCheckIsConsistent() {
        assertEquals(QuestionStatus.GENERATED,
                QcStatusDecider.decide(true, DedupStatus.ERROR, AnswerCheckStatus.CONSISTENT));
    }

    @Test
    void nullInputsAreTreatedAsNotRunAndNeverPass() {
        assertEquals(QuestionStatus.GENERATED, QcStatusDecider.decide(true, null, AnswerCheckStatus.CONSISTENT));
        assertEquals(QuestionStatus.GENERATED, QcStatusDecider.decide(true, DedupStatus.NO_DUPLICATE, null));
        assertEquals(QuestionStatus.GENERATED, QcStatusDecider.decide(true, null, null));
    }
}
