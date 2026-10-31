package com.datn.mcq.exam.dto;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Xem đề ở chế độ giáo viên (V7, phần C mục 3). Đây là API production KHÔNG chấm mù: có đáp án, đáp số, nhãn từng ý,
 * lời giải và cờ "cần xem kỹ" (thay đổi so với quyết định 21/10 và V1, xem docs/ai-development-rules.md). Form chấm mù
 * (/api/reviews/**) và chế độ experiment không đổi.
 */
public final class ExamViewDtos {

    private ExamViewDtos() {}

    public record ItemStatementDto(StatementKey key, String text, TruthValue label, Difficulty difficulty) {}

    /**
     * Một câu của đề. Chỗ chưa có câu (đang sinh, hỏng) chỉ có phần định danh và {@code state}.
     *
     * @param source        "bank" (lấy từ ngân hàng) hoặc "generated" (hệ thống sinh)
     * @param difficulty    mức của câu mcq/sa; tf ghi mức ở từng ý
     * @param reviewReasons lý do "cần xem kỹ": solver_inconsistent, solver_multiple_correct, solver_no_correct (mcq,
     *                      Solver xét từng phương án, thẩm định 31/10), solver_not_run, solver_ambiguous,
     *                      solver_unparseable, statement_undecidable, dedup_not_run, "warning: ..." (cảnh báo format);
     *                      W1-2 thêm solver_pending (Solver đang kiểm lại bản sửa), solver_failed (job kiểm lại thất
     *                      bại), "duplicate: &lt;id&gt;" (bản sửa trùng câu khác); "similar_in_exam: &lt;n&gt;" (câu hệ
     *                      thống sinh cùng khuôn với câu thứ n trong đề, tính lúc xem đề, thẩm định 31/10)
     * @param qcState       (W1-2) chỉ với câu hệ thống sinh chưa Giữ: "checking" (Solver đang kiểm lại), "passed" (không
     *                      còn lý do cần xem kỹ), "needs_review" (có lý do); bỏ khỏi JSON với chỗ khác
     * @param edited        (W1-2) câu hiện tại của chỗ là bản giáo viên sửa (kể cả khi đã Giữ)
     * @param originalQuestionId (W1-2) bản gốc LLM của bản sửa; bỏ khỏi JSON khi câu chưa sửa
     */
    public record ExamItemDto(
            Integer slotId,
            Integer cellId,
            Integer slotNo,
            String part,
            String topic,
            QuestionType questionType,
            String state,
            @JsonInclude(JsonInclude.Include.NON_NULL) String source,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer bankItemId,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer questionId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String questionText,
            @JsonInclude(JsonInclude.Include.NON_NULL) String optionA,
            @JsonInclude(JsonInclude.Include.NON_NULL) String optionB,
            @JsonInclude(JsonInclude.Include.NON_NULL) String optionC,
            @JsonInclude(JsonInclude.Include.NON_NULL) String optionD,
            @JsonInclude(JsonInclude.Include.NON_NULL) Option correctOption,
            @JsonInclude(JsonInclude.Include.NON_NULL) String saAnswer,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<ItemStatementDto> statements,
            @JsonInclude(JsonInclude.Include.NON_NULL) String explanation,
            @JsonInclude(JsonInclude.Include.NON_NULL) Difficulty difficulty,
            boolean needsReview,
            List<String> reviewReasons,
            @JsonInclude(JsonInclude.Include.NON_NULL) String qcState,
            boolean edited,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer originalQuestionId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String failureReason
    ) {
        public static final String QC_CHECKING = "checking";
        public static final String QC_PASSED = "passed";
        public static final String QC_NEEDS_REVIEW = "needs_review";

        /**
         * Bản sao có thêm lý do "cần xem kỹ" {@code extra} (tính lúc xem đề, không lưu): {@code needsReview} thành true,
         * {@code qcState} thành "needs_review" trừ khi Solver đang kiểm lại ("checking" giữ nguyên).
         */
        public ExamItemDto withExtraReasons(List<String> extra) {
            List<String> all = new java.util.ArrayList<>(reviewReasons);
            all.addAll(extra);
            return new ExamItemDto(slotId, cellId, slotNo, part, topic, questionType, state, source, bankItemId, questionId,
                    questionText, optionA, optionB, optionC, optionD, correctOption, saAnswer, statements, explanation,
                    difficulty, true, all, QC_CHECKING.equals(qcState) ? qcState : QC_NEEDS_REVIEW, edited,
                    originalQuestionId, failureReason);
        }

        /** Chữ ký trước W2-4b (chỗ đã có câu, không có failureReason). */
        public ExamItemDto(Integer slotId, Integer cellId, Integer slotNo, String part, String topic,
                           QuestionType questionType, String state, String source, Integer bankItemId,
                           Integer questionId, String questionText, String optionA, String optionB, String optionC,
                           String optionD, Option correctOption, String saAnswer, List<ItemStatementDto> statements,
                           String explanation, Difficulty difficulty, boolean needsReview, List<String> reviewReasons,
                           String qcState, boolean edited, Integer originalQuestionId) {
            this(slotId, cellId, slotNo, part, topic, questionType, state, source, bankItemId, questionId, questionText,
                    optionA, optionB, optionC, optionD, correctOption, saAnswer, statements, explanation, difficulty,
                    needsReview, reviewReasons, qcState, edited, originalQuestionId, null);
        }

        /** Chữ ký trước W1-2 (câu chưa sửa, không có trạng thái QC). */
        public ExamItemDto(Integer slotId, Integer cellId, Integer slotNo, String part, String topic,
                           QuestionType questionType, String state, String source, Integer bankItemId,
                           Integer questionId, String questionText, String optionA, String optionB, String optionC,
                           String optionD, Option correctOption, String saAnswer, List<ItemStatementDto> statements,
                           String explanation, Difficulty difficulty, boolean needsReview, List<String> reviewReasons) {
            this(slotId, cellId, slotNo, part, topic, questionType, state, source, bankItemId, questionId, questionText,
                    optionA, optionB, optionC, optionD, correctOption, saAnswer, statements, explanation, difficulty,
                    needsReview, reviewReasons, null, false, null, null);
        }
    }

    /** @param required số câu ma trận yêu cầu; {@code ready} số câu đã có */
    public record PartDto(String part, int required, int ready, List<ExamItemDto> items) {}

    /**
     * @param buildStatus như {@link ExamBuildDtos.ProgressDto#status()}
     * @param matrixId    null với đề sinh theo bài (ma trận ẩn)
     * @param kind        (W1-4) "matrix" (tạo từ ma trận), "lesson" (sinh theo bài) hoặc "manual" (đề cũ không có ma trận)
     */
    public record ExamViewDto(
            Integer examId,
            String examCode,
            String title,
            String mode,
            ExamStatus status,
            Integer matrixId,
            LocalDateTime createdAt,
            String buildStatus,
            int needsReviewCount,
            List<PartDto> parts,
            String kind
    ) {
        /** Chữ ký trước W1-4 (đề tạo từ ma trận). */
        public ExamViewDto(Integer examId, String examCode, String title, String mode, ExamStatus status,
                           Integer matrixId, LocalDateTime createdAt, String buildStatus, int needsReviewCount,
                           List<PartDto> parts) {
            this(examId, examCode, title, mode, status, matrixId, createdAt, buildStatus, needsReviewCount, parts,
                    "matrix");
        }
    }

    /**
     * Một đề trong danh sách "đề của tôi" (W1-1, GET /api/exams). Số chỗ theo trạng thái tại lần đối soát gần nhất
     * (danh sách không đối soát).
     *
     * @param readySlots       chỗ đã có câu, chưa Giữ (state ready)
     * @param keptSlots        chỗ đã Giữ (kept, edited)
     * @param needsReviewSlots trong số readySlots, câu hệ thống sinh có cờ "cần xem kỹ" (như needsReview khi xem đề)
     * @param generatingSlots  pending, generating
     * @param failedSlots      failed
     * @param kind             (W1-4) "matrix", "lesson" hoặc "manual" (như {@link ExamViewDto#kind()}); matrixId null với "lesson"
     */
    public record ExamSummaryDto(
            Integer examId,
            String examCode,
            String title,
            String mode,
            String kind,
            ExamStatus status,
            Integer matrixId,
            LocalDateTime createdAt,
            int totalSlots,
            int readySlots,
            int keptSlots,
            int needsReviewSlots,
            int generatingSlots,
            int failedSlots
    ) {}

    /** @param page trang hiện tại (từ 0) */
    public record ExamPageDto(List<ExamSummaryDto> items, int page, int size, long totalItems, int totalPages) {}
}
