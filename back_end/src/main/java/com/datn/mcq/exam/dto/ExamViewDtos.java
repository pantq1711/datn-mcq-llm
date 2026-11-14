package com.datn.mcq.exam.dto;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.DistractorCheck;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
     *                      solver_unparseable, statement_undecidable, dedup_not_run, explanation_draft_suspect (lời giải có
     *                      "tính lại", "làm lại" hoặc "sửa lại", thẩm định 31/10), "warning: ..." (cảnh báo format);
     *                      W1-2 thêm solver_pending (Solver đang kiểm lại bản sửa), solver_failed (job kiểm lại thất
     *                      bại), "duplicate: &lt;id&gt;" (bản sửa trùng câu khác); "similar_in_exam: &lt;Phần&gt;.&lt;số&gt;" (câu hệ
     *                      thống sinh cùng khuôn với câu đứng trước nó, Phần I/II/III và số "Câu k" trong Phần như trang đề;
     *                      mỗi cặp chỉ câu sau bị gắn; tính lúc xem đề, thẩm định 31/10, đổi dạng 2/11)
     * @param qcState       (W1-2) chỉ với câu hệ thống sinh chưa Giữ: "checking" (Solver đang kiểm lại), "passed" (không
     *                      còn lý do cần xem kỹ), "needs_review" (có lý do); bỏ khỏi JSON với chỗ khác
     * @param edited        (W1-2) câu hiện tại của chỗ là bản giáo viên sửa (kể cả khi đã Giữ)
     * @param originalQuestionId (W1-2) bản gốc LLM của bản sửa; bỏ khỏi JSON khi câu chưa sửa
     * @param infoReasons   (12/11, PR B) cờ THÔNG TIN, luôn có ({@code []} khi không có): {@code request_partial: <lý do>},
     *                      {@code request_not_met: <lý do>}; về sau thêm cờ khác cùng dạng {@code <mã>} hoặc
     *                      {@code <mã>: <chi tiết>}. Tách hẳn khỏi {@code reviewReasons}: không bật {@code needsReview},
     *                      không tính vào số câu cần xem kỹ, không đổi {@code qcState} hay ngân hàng riêng/chung khi Giữ
     * @param requests      (PR B) yêu cầu mà câu đang ở chỗ được sinh theo; vắng khi câu không có yêu cầu (câu ngân hàng,
     *                      câu đã Giữ hiện bản ngân hàng, chỗ chưa có câu)
     * @param inheritedRequests (PR B) yêu cầu thừa hưởng của chỗ (của ô, không thì của đề; mã đã lọc theo dạng câu), để hộp
     *                      Đổi câu điền sẵn; vắng khi ô và đề không có yêu cầu áp cho chỗ này
     * @param distractorErrors (13/11, PA1) lỗi sai của học sinh dẫn tới từng phương án sai, theo chữ A-D: {@code text} và
     *                      {@code check} ({@code match | mismatch | unknown | unchecked}). Chỉ câu mcq có ít nhất một lỗi; chỉ
     *                      khóa của phương án sai có chữ lỗi; vắng khi không có
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
            @JsonInclude(JsonInclude.Include.NON_NULL) String failureReason,
            List<String> infoReasons,
            @JsonInclude(JsonInclude.Include.NON_NULL) RequestsDto requests,
            @JsonInclude(JsonInclude.Include.NON_NULL) RequestsDto inheritedRequests,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, DistractorErrorDto> distractorErrors
    ) {
        public static final String QC_CHECKING = "checking";
        public static final String QC_PASSED = "passed";
        public static final String QC_NEEDS_REVIEW = "needs_review";

        public ExamItemDto {
            infoReasons = infoReasons == null ? List.of() : List.copyOf(infoReasons);
            distractorErrors = distractorErrors == null || distractorErrors.isEmpty() ? null
                    : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(distractorErrors));
        }

        /** Chữ ký trước 13/11 (không có lỗi sai kèm phương án). */
        public ExamItemDto(Integer slotId, Integer cellId, Integer slotNo, String part, String topic,
                           QuestionType questionType, String state, String source, Integer bankItemId,
                           Integer questionId, String questionText, String optionA, String optionB, String optionC,
                           String optionD, Option correctOption, String saAnswer, List<ItemStatementDto> statements,
                           String explanation, Difficulty difficulty, boolean needsReview, List<String> reviewReasons,
                           String qcState, boolean edited, Integer originalQuestionId, String failureReason,
                           List<String> infoReasons, RequestsDto requests, RequestsDto inheritedRequests) {
            this(slotId, cellId, slotNo, part, topic, questionType, state, source, bankItemId, questionId, questionText,
                    optionA, optionB, optionC, optionD, correctOption, saAnswer, statements, explanation, difficulty,
                    needsReview, reviewReasons, qcState, edited, originalQuestionId, failureReason, infoReasons, requests,
                    inheritedRequests, null);
        }

        /** Bản sao kèm lỗi sai của phương án nhiễu (PA1); null hoặc rỗng là không có. */
        public ExamItemDto withDistractorErrors(Map<String, DistractorErrorDto> errors) {
            return new ExamItemDto(slotId, cellId, slotNo, part, topic, questionType, state, source, bankItemId, questionId,
                    questionText, optionA, optionB, optionC, optionD, correctOption, saAnswer, statements, explanation,
                    difficulty, needsReview, reviewReasons, qcState, edited, originalQuestionId, failureReason, infoReasons,
                    requests, inheritedRequests, errors);
        }

        /** Chữ ký trước PR B (không có cờ thông tin và yêu cầu). */
        public ExamItemDto(Integer slotId, Integer cellId, Integer slotNo, String part, String topic,
                           QuestionType questionType, String state, String source, Integer bankItemId,
                           Integer questionId, String questionText, String optionA, String optionB, String optionC,
                           String optionD, Option correctOption, String saAnswer, List<ItemStatementDto> statements,
                           String explanation, Difficulty difficulty, boolean needsReview, List<String> reviewReasons,
                           String qcState, boolean edited, Integer originalQuestionId, String failureReason) {
            this(slotId, cellId, slotNo, part, topic, questionType, state, source, bankItemId, questionId, questionText,
                    optionA, optionB, optionC, optionD, correctOption, saAnswer, statements, explanation, difficulty,
                    needsReview, reviewReasons, qcState, edited, originalQuestionId, failureReason, null, null, null);
        }

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
                    originalQuestionId, failureReason, infoReasons, requests, inheritedRequests, distractorErrors);
        }

        /** Bản sao kèm cờ thông tin và yêu cầu (PR B). */
        public ExamItemDto withRequests(List<String> info, RequestsDto own, RequestsDto inherited) {
            return new ExamItemDto(slotId, cellId, slotNo, part, topic, questionType, state, source, bankItemId, questionId,
                    questionText, optionA, optionB, optionC, optionD, correctOption, saAnswer, statements, explanation,
                    difficulty, needsReview, reviewReasons, qcState, edited, originalQuestionId, failureReason, info, own,
                    inherited, distractorErrors);
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

    /**
     * Lỗi sai của học sinh dẫn tới một phương án sai (13/11, PA1).
     *
     * @param text  câu mô tả lỗi (LaTeX trong $...$ như các trường khác)
     * @param check kết quả Solver ghép mù: {@code match}, {@code mismatch}, {@code unknown} hoặc {@code unchecked}
     */
    public record DistractorErrorDto(String text, DistractorCheck check) {}

    /**
     * Yêu cầu của giáo viên (12/11, PR B).
     *
     * @param codes mã chip (đã lọc theo dạng câu); {@code []} khi chỉ có văn bản
     * @param text  văn bản tự do (nguyên văn đã lưu, chưa qua neutralize); null khi chỉ có mã
     * @param scope mức lấy yêu cầu: "replace" (lượt Đổi câu tự đặt), "cell" hoặc "exam"; với {@code inheritedRequests} chỉ
     *              là "cell" hoặc "exam"
     */
    public record RequestsDto(List<String> codes, String text, String scope) {
        public RequestsDto {
            codes = codes == null ? List.of() : List.copyOf(codes);
        }
    }

    /** @param required số câu ma trận yêu cầu; {@code ready} số câu đã có */
    public record PartDto(String part, int required, int ready, List<ExamItemDto> items) {}

    /**
     * @param buildStatus như {@link ExamBuildDtos.ProgressDto#status()}
     * @param matrixId    null với đề sinh theo bài (ma trận ẩn)
     * @param kind        (W1-4) "matrix" (tạo từ ma trận), "lesson" (sinh theo bài) hoặc "manual" (đề cũ không có ma trận)
     * @param challenge   (12/11) mức thử thách của đề: "standard" hoặc "advanced"
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
            String kind,
            String challenge
    ) {
        /** Chữ ký trước 12/11 (đề chuẩn). */
        public ExamViewDto(Integer examId, String examCode, String title, String mode, ExamStatus status,
                           Integer matrixId, LocalDateTime createdAt, String buildStatus, int needsReviewCount,
                           List<PartDto> parts, String kind) {
            this(examId, examCode, title, mode, status, matrixId, createdAt, buildStatus, needsReviewCount, parts, kind,
                    "standard");
        }

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
