package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.QuestionStatus;

import java.util.List;

/**
 * Báo cáo một lần chạy QC. Chỉ dùng cho luồng sinh câu (người nghiên cứu), không trả về form chấm.
 *
 * @param dedup chi tiết kết quả dedup; null khi format lỗi nên dedup không chạy
 */
public record QcPipelineReport(
        QuestionStatus resultingStatus,
        boolean formatPassed,
        List<String> formatErrors,
        Boolean answerCheckPassed,
        AnswerChoice judgeAnswer,
        String judgeModel,
        String solverReasoning,
        AnswerCheckStatus answerCheckStatus,
        DedupStatus dedupStatus,
        DedupOutcome dedup
) {
}
