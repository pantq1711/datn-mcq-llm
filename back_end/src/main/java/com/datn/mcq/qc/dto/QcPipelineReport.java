package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.SaJudgeKind;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Báo cáo một lần chạy QC. Chỉ dùng cho luồng sinh câu (người nghiên cứu), không trả về form chấm.
 *
 * @param dedup          chi tiết kết quả dedup; null khi format lỗi nên dedup không chạy
 * @param answerWarnings (V4) cảnh báo về đáp số, ví dụ phân số chưa tối giản; bỏ khỏi JSON khi rỗng
 * @param saJudgeKind    (V4, câu sa) loại đáp số Solver trả; bỏ khỏi JSON khi null
 * @param saJudgeAnswer  (V4, câu sa) chuỗi gốc Solver trả; bỏ khỏi JSON khi null
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
        DedupOutcome dedup,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> answerWarnings,
        @JsonInclude(JsonInclude.Include.NON_NULL) SaJudgeKind saJudgeKind,
        @JsonInclude(JsonInclude.Include.NON_NULL) String saJudgeAnswer
) {
    /** Báo cáo của câu mcq (chữ ký trước V4): không có trường SA. */
    public QcPipelineReport(
            QuestionStatus resultingStatus,
            boolean formatPassed,
            List<String> formatErrors,
            Boolean answerCheckPassed,
            AnswerChoice judgeAnswer,
            String judgeModel,
            String solverReasoning,
            AnswerCheckStatus answerCheckStatus,
            DedupStatus dedupStatus,
            DedupOutcome dedup) {
        this(resultingStatus, formatPassed, formatErrors, answerCheckPassed, judgeAnswer, judgeModel, solverReasoning,
                answerCheckStatus, dedupStatus, dedup, List.of(), null, null);
    }
}
