package com.datn.mcq.qc.orchestrator;

import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupStatus;

/**
 * Quyết định trạng thái câu hỏi sau QC (hàm thuần, không gọi DB hay mạng).
 *
 * <p>Theo Q-2 (chốt 2026-10-21): QC_PASSED chỉ khi format hợp lệ, dedup đã chạy và không trùng,
 * answer_check chạy thành công và nhất quán. Format lỗi -> FORMAT_FAILED. Mọi trường hợp còn lại
 * (bước bị bỏ qua, lỗi, không nhất quán, thiếu thông tin) -> GENERATED, tức là chưa qua QC.
 */
public final class QcStatusDecider {

    private QcStatusDecider() {}

    public static QuestionStatus decide(boolean formatPassed, DedupStatus dedup, AnswerCheckStatus answerCheck) {
        if (!formatPassed) {
            return QuestionStatus.FORMAT_FAILED;
        }
        if (dedup == DedupStatus.NO_DUPLICATE && answerCheck == AnswerCheckStatus.CONSISTENT) {
            return QuestionStatus.QC_PASSED;
        }
        return QuestionStatus.GENERATED;
    }
}
