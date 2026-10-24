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
     * @param reviewReasons lý do "cần xem kỹ": solver_inconsistent, solver_not_run, solver_ambiguous,
     *                      solver_unparseable, statement_undecidable, dedup_not_run, "warning: ..." (cảnh báo format)
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
            List<String> reviewReasons
    ) {}

    /** @param required số câu ma trận yêu cầu; {@code ready} số câu đã có */
    public record PartDto(String part, int required, int ready, List<ExamItemDto> items) {}

    /** @param buildStatus như {@link ExamBuildDtos.ProgressDto#status()} */
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
            List<PartDto> parts
    ) {}

    /**
     * Một đề trong danh sách "đề của tôi" (W1-1, GET /api/exams). Số chỗ theo trạng thái tại lần đối soát gần nhất
     * (danh sách không đối soát).
     *
     * @param readySlots       chỗ đã có câu, chưa Giữ (state ready)
     * @param keptSlots        chỗ đã Giữ (kept, edited)
     * @param needsReviewSlots trong số readySlots, câu hệ thống sinh có cờ "cần xem kỹ" (như needsReview khi xem đề)
     * @param generatingSlots  pending, generating
     * @param failedSlots      failed
     */
    public record ExamSummaryDto(
            Integer examId,
            String examCode,
            String title,
            String mode,
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
