package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.SaJudgeKind;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthChoice;

import java.util.ArrayList;
import java.util.List;

/**
 * Một kết quả Solver hợp lệ (W1-2: tách khỏi QcOrchestrator#evaluateAndSave để gọi Solver và ghi kết quả ở hai bước,
 * cho job answer_check gọi LLM ngoài transaction rồi ghi kết quả cùng transaction với trạng thái câu và job).
 *
 * @param judgeAnswer  mcq: đáp án Solver chọn; null với dạng khác
 * @param saJudgeKind  sa: loại đáp số Solver trả; null với dạng khác
 * @param saJudgeAnswer sa: chuỗi gốc Solver trả (null khi ambiguous)
 * @param tfLabels     tf: nhãn Solver cho 4 ý theo thứ tự a-d; null với dạng khác
 */
public record SolverVerdict(
        QuestionType questionType,
        boolean passed,
        String judgeModel,
        String reasoning,
        AnswerChoice judgeAnswer,
        SaJudgeKind saJudgeKind,
        String saJudgeAnswer,
        List<TruthChoice> tfLabels
) {
    public SolverVerdict {
        tfLabels = tfLabels == null ? null : List.copyOf(tfLabels);
    }

    public AnswerCheckStatus status() {
        return passed ? AnswerCheckStatus.CONSISTENT : AnswerCheckStatus.INCONSISTENT;
    }

    /** Nhãn Solver từng ý dạng báo cáo; null khi không phải câu tf. */
    public List<TfJudgement> tfJudgements() {
        if (tfLabels == null) {
            return null;
        }
        List<TfJudgement> out = new ArrayList<>(4);
        StatementKey[] keys = StatementKey.values();
        for (int i = 0; i < keys.length; i++) {
            out.add(new TfJudgement(keys[i], tfLabels.get(i)));
        }
        return out;
    }
}
