package com.datn.mcq.exam.dto;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.SaJudgeKind;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.enums.TruthValue;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vết QC theo ô đề (W1-3, {@code GET /api/exams/{examId}/slots/{slotId}/qc-trace}). Chế độ giáo viên, không chấm mù:
 * có đáp án và kết quả Solver. Không có raw_llm_output, prompt, token hay chi phí.
 */
public final class ExamQcTraceDtos {

    private ExamQcTraceDtos() {}

    public record UserRef(Integer id, String name) {}

    /** Một lần sinh (job) hoặc một câu ngân hàng; {@code job} null với câu lấy từ ngân hàng. */
    public record JobDto(Integer jobId, LocalDateTime createdAt, String status, Integer attempt, String error) {}

    public record LabelDto(StatementKey key, TruthValue label) {}

    public record JudgeLabelDto(StatementKey key, TruthChoice judgeLabel) {}

    /** Đáp án của một phiên bản: mcq {@code correctOption}, sa {@code saAnswer}, tf {@code labels}. */
    public record AnswerDto(Option correctOption, String saAnswer, List<LabelDto> labels) {}

    /** @param autoRepairs các lần hệ thống tự sửa (escape LaTeX, tự đóng $); {@code warnings} là cảnh báo khác */
    public record FormatDto(boolean passed, List<String> errors, List<String> warnings, List<String> autoRepairs,
                            LocalDateTime checkedAt) {}

    public record ComparedToDto(Integer questionId, boolean inThisExam, boolean inBank) {}

    /** @param threshold ngưỡng đã dùng lúc kiểm (đọc từ quality_check_result.threshold_used) */
    public record DedupDto(boolean passed, Double similarity, Double threshold, LocalDateTime checkedAt,
                           ComparedToDto comparedTo) {}

    /**
     * @param state  {@code consistent} (Solver khớp), {@code inconsistent} (lệch), {@code pending} (job đang chờ hoặc
     *               chạy), {@code error} (lỗi, không có kết quả hợp lệ), {@code not_run} (chưa chạy)
     * @param reason với {@code not_run}: format_failed, duplicate, dedup_not_concluded, solver_not_run, superseded,
     *               not_recorded
     * @param error  với {@code error}: lý do (lỗi của job answer_check hoặc generated_question.solver_error)
     * @param job    job answer_check mới nhất của bản (chỉ bản giáo viên sửa có); null nếu không có
     */
    public record AnswerCheckDto(String state, String reason, String judgeModel, AnswerChoice judgeAnswer,
                                 SaJudgeKind judgeSaKind, String judgeSaAnswer, List<JudgeLabelDto> judgeLabels,
                                 String solverReasoning, String error, LocalDateTime checkedAt, JobDto job) {}

    /** Một đoạn tài liệu bài học đã đưa vào prompt (khoảng {@code [from, to)} của văn bản đã trích của tài liệu). */
    public record MaterialRefDto(Integer materialId, String sha256, Integer from, Integer to, Integer rank, Double score,
                                 String selection) {}

    /**
     * Bước QC "bám tài liệu" (W2-3); {@code null} với câu không dùng tài liệu.
     *
     * @param state          {@code supported}, {@code not_supported}, {@code unknown} (đã có kết quả), {@code pending}
     *                       (job đang chờ hoặc chạy), {@code error} (job lỗi, không có kết quả) hoặc {@code not_run} (job
     *                       đã bỏ qua, ví dụ câu đã bị bản sửa thay)
     * @param reason         lý do ngắn của judge; với UNKNOWN do trích dẫn không kiểm chứng được bắt đầu bằng
     *                       {@code evidence_not_found:}
     * @param evidenceQuote  trích dẫn nguyên văn từ tài liệu mà judge đưa ra
     * @param evidenceFound  trích dẫn có thật trong đoạn tài liệu đã đưa vào prompt không; null khi judge không trích dẫn
     * @param materialRefs   các đoạn tài liệu đã dùng (cũng là thứ judge đã thấy)
     * @param error          với {@code error} và {@code not_run}: lý do (generation_job.last_error)
     * @param job            job grounding_check của bản
     */
    public record GroundingDto(String state, String reason, String evidenceQuote, Boolean evidenceFound,
                               String judgeModel, List<MaterialRefDto> materialRefs, LocalDateTime checkedAt, String error,
                               JobDto job) {}

    /** Mỗi bước là null khi chưa có dòng QC tương ứng. */
    public record QcDto(FormatDto format, DedupDto dedup, AnswerCheckDto answerCheck, GroundingDto grounding) {}

    /**
     * Một phiên bản của câu.
     *
     * @param kind          {@code llm} (đầu ra LLM) hoặc {@code teacher_edit} (giáo viên sửa)
     * @param current       là câu hiện đang ở trong chỗ
     * @param editedBy      chỉ {@code teacher_edit}
     * @param changedFields chỉ {@code teacher_edit}: tên trường đã đổi so với bản trước
     * @param editDistance  chỉ {@code teacher_edit}: so với bản trước, không tính lời giải
     * @param reviewReasons lý do "cần xem kỹ" của bản (như API xem đề); rỗng với bản đã vào ngân hàng
     */
    public record VersionDto(Integer questionId, String kind, LocalDateTime createdAt, String status, boolean current,
                             UserRef editedBy, List<String> changedFields, Integer editDistance, AnswerDto answer,
                             QcDto qc, List<String> reviewReasons) {}

    /** @param origin {@code generated} (một lần sinh của hệ thống) hoặc {@code bank} (câu lấy từ ngân hàng) */
    public record LineageDto(String origin, JobDto job, List<VersionDto> versions) {}

    /** Một thao tác của giáo viên trên chỗ: keep, edit, replace. */
    public record ActionDto(Integer id, String action, LocalDateTime at, UserRef actor, Integer questionId,
                            Integer bankItemId, List<String> changedFields, Integer editDistance,
                            String instruction) {}

    public record QcTraceDto(Integer examId, Integer slotId, Integer cellId, Integer slotNo, String part, String topic,
                             QuestionType questionType, String state, String source, Integer currentQuestionId,
                             Integer bankItemId, Integer attempts, Integer solverRetries, List<LineageDto> lineages,
                             List<ActionDto> actions) {}
}
