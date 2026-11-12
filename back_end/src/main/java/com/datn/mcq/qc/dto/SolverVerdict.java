package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.RequestVerdict;
import com.datn.mcq.enums.SaJudgeKind;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.llm.dto.LlmUsage;

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
 * @param usage        token và độ trễ của lượt gọi Solver cho ra kết quả này; null khi kết quả được CHÉP từ bản trước
 *                     (không có lượt gọi nào, nên không được tính chi phí lần nữa)
 * @param mcqOptionCorrect mcq (Solver v3): kết luận từng phương án A-D; null với dạng khác, Solver cũ, phản hồi thiếu
 *                     {@code options_verdict} và kết quả chép. Không lưu cột riêng: đã nằm trong ghi chú Solver (V14).
 * @param requestVerdict (12/11, PR B) kết luận về yêu cầu của giáo viên ({@code request_check}, cùng lượt gọi); null khi câu
 *                     không có yêu cầu hoặc Solver không trả kết luận hợp lệ. Không ảnh hưởng {@code passed}
 * @param requestReason (PR B) lý do ngắn của kết luận trên; null khi không có
 */
public record SolverVerdict(
        QuestionType questionType,
        boolean passed,
        String judgeModel,
        String reasoning,
        AnswerChoice judgeAnswer,
        SaJudgeKind saJudgeKind,
        String saJudgeAnswer,
        List<TruthChoice> tfLabels,
        LlmUsage usage,
        List<Boolean> mcqOptionCorrect,
        RequestVerdict requestVerdict,
        String requestReason
) {
    public SolverVerdict {
        tfLabels = tfLabels == null ? null : List.copyOf(tfLabels);
        mcqOptionCorrect = mcqOptionCorrect == null ? null : List.copyOf(mcqOptionCorrect);
        requestReason = requestVerdict == null ? null : requestReason;
    }

    public SolverVerdict(QuestionType questionType, boolean passed, String judgeModel, String reasoning,
                         AnswerChoice judgeAnswer, SaJudgeKind saJudgeKind, String saJudgeAnswer,
                         List<TruthChoice> tfLabels, LlmUsage usage, List<Boolean> mcqOptionCorrect) {
        this(questionType, passed, judgeModel, reasoning, judgeAnswer, saJudgeKind, saJudgeAnswer, tfLabels, usage,
                mcqOptionCorrect, null, null);
    }

    /** Bản sao kèm kết luận về yêu cầu của giáo viên (PR B). */
    public SolverVerdict withRequestCheck(RequestVerdict verdict, String reason) {
        return new SolverVerdict(questionType, passed, judgeModel, reasoning, judgeAnswer, saJudgeKind, saJudgeAnswer,
                tfLabels, usage, mcqOptionCorrect, verdict, reason);
    }

    public SolverVerdict(QuestionType questionType, boolean passed, String judgeModel, String reasoning,
                         AnswerChoice judgeAnswer, SaJudgeKind saJudgeKind, String saJudgeAnswer,
                         List<TruthChoice> tfLabels, LlmUsage usage) {
        this(questionType, passed, judgeModel, reasoning, judgeAnswer, saJudgeKind, saJudgeAnswer, tfLabels, usage,
                null);
    }

    public SolverVerdict(QuestionType questionType, boolean passed, String judgeModel, String reasoning,
                         AnswerChoice judgeAnswer, SaJudgeKind saJudgeKind, String saJudgeAnswer,
                         List<TruthChoice> tfLabels) {
        this(questionType, passed, judgeModel, reasoning, judgeAnswer, saJudgeKind, saJudgeAnswer, tfLabels, null,
                null);
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
