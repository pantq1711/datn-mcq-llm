package com.datn.mcq.bank.dto;

import com.datn.mcq.bank.dto.BankItemDto.StatementDto;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.questionimage.dto.QuestionImageDto;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ngân hàng có lọc (W1-3): danh sách phân trang, chi tiết một câu kèm nguồn gốc và số đề đã dùng. Giữ nguyên các key
 * của {@link BankItemDto} (id ... createdAt, questionType, saAnswer, statements), thêm môn, khối, chủ đề, nguồn và mức
 * dùng. Không có raw_llm_output.
 */
public final class BankSearchDtos {

    private BankSearchDtos() {}

    /**
     * @param type       nguồn câu: {@code ai} (câu AI đã được giáo viên duyệt) hoặc {@code import} (câu nhập từ Word, V26;
     *                   reviewMode là {@code assisted}, người duyệt là người tải)
     * @param reviewMode {@code assisted} (Giữ trong đề ở chế độ giáo viên) hoặc {@code blind} (duyệt ở form chấm mù)
     * @param edited     câu đã qua tay giáo viên sửa (bản sửa trong đề hoặc sửa lúc duyệt)
     */
    public record OriginDto(String type, String reviewMode, boolean edited) {}

    /** Câu dùng chung (mọi giáo viên thấy và dùng lại) — giá trị của {@code visibility}. */
    public static final String VISIBILITY_SHARED = "shared";
    /** Câu riêng của người xem ({@code bank_item.visible_to_user_id}) — giá trị của {@code visibility}. */
    public static final String VISIBILITY_PRIVATE = "private";

    /**
     * Một dòng của danh sách ngân hàng.
     *
     * @param visibility (16/11, NV-30) {@code shared} (câu dùng chung) hoặc {@code private} (câu riêng của người xem; câu
     *                   riêng của người khác không bao giờ hiện)
     * @param images     (22/11, G0 H3) ảnh của câu nhập từ Word có hình, mỗi ảnh có URL ký ngắn hạn cho đúng người xem; giao
     *                   diện đặt ảnh theo token {@code ⟦img:<id>⟧} trong chữ. Vắng khỏi JSON khi câu không có hình
     */
    public record BankListItemDto(
            Integer id,
            Integer sourceQuestionId,
            Integer reviewId,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            Option correctOption,
            String explanation,
            Difficulty difficulty,
            LocalDateTime createdAt,
            QuestionType questionType,
            @JsonInclude(JsonInclude.Include.NON_NULL) String saAnswer,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<StatementDto> statements,
            Integer subjectId,
            String subjectName,
            Integer gradeLevel,
            String topic,
            String content,
            OriginDto origin,
            long usageCount,
            LocalDateTime lastUsedAt,
            String visibility,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<QuestionImageDto> images
    ) {
        /** Bản sao có {@code images} (nạp theo lô sau khi dựng dòng). */
        public BankListItemDto withImages(List<QuestionImageDto> images) {
            return new BankListItemDto(id, sourceQuestionId, reviewId, questionText, optionA, optionB, optionC, optionD,
                    correctOption, explanation, difficulty, createdAt, questionType, saAnswer, statements, subjectId,
                    subjectName, gradeLevel, topic, content, origin, usageCount, lastUsedAt, visibility, images);
        }
    }

    public record BankPageDto(List<BankListItemDto> items, int page, int size, long totalItems, int totalPages) {}

    public record UserRef(Integer id, String name) {}

    /**
     * @param kind {@code llm} (đầu ra LLM), {@code teacher_edit} (giáo viên sửa trong đề) hoặc {@code import} (câu nhập từ
     *             Word, V26)
     */
    public record VersionDto(Integer questionId, String kind, LocalDateTime createdAt, UserRef editedBy,
                             List<String> changedFields, Integer editDistance) {}

    public record ReviewDto(Integer reviewId, String mode, String decision, UserRef reviewer,
                            LocalDateTime reviewedAt) {}

    public record ProvenanceDto(Integer llmQuestionId, Integer runId, String promptVersion, String model,
                                List<VersionDto> versions, ReviewDto review) {}

    public record ExamRef(Integer examId, String examCode, String title, LocalDateTime createdAt) {}

    /**
     * @param usageCount số đề khác nhau (của mọi giáo viên) đang dùng câu này
     * @param myExams    các đề của chính người gọi dùng câu này (tối đa 50, mới nhất trước); đề của người khác chỉ
     *                   được tính vào {@code usageCount}
     */
    public record UsageDto(long usageCount, LocalDateTime lastUsedAt, List<ExamRef> myExams) {}

    /**
     * Chi tiết một câu ngân hàng: các trường của danh sách cộng nguồn gốc và mức dùng.
     *
     * @param canRemove (16/11, NV-21) người xem gỡ được câu khỏi ngân hàng ({@code DELETE /api/bank-items/{id}}): người Giữ
     *                  câu (teacher_review.reviewer_id; câu nhập từ Word là người tải) hoặc admin
     */
    public record BankItemDetailDto(
            Integer id,
            Integer sourceQuestionId,
            Integer reviewId,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            Option correctOption,
            String explanation,
            Difficulty difficulty,
            LocalDateTime createdAt,
            QuestionType questionType,
            @JsonInclude(JsonInclude.Include.NON_NULL) String saAnswer,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<StatementDto> statements,
            Integer subjectId,
            String subjectName,
            Integer gradeLevel,
            String topic,
            String content,
            OriginDto origin,
            long usageCount,
            LocalDateTime lastUsedAt,
            ProvenanceDto provenance,
            UsageDto usage,
            String visibility,
            boolean canRemove,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<QuestionImageDto> images
    ) {
        public static BankItemDetailDto of(BankListItemDto i, ProvenanceDto provenance, UsageDto usage,
                                           boolean canRemove) {
            return new BankItemDetailDto(i.id(), i.sourceQuestionId(), i.reviewId(), i.questionText(), i.optionA(),
                    i.optionB(), i.optionC(), i.optionD(), i.correctOption(), i.explanation(), i.difficulty(),
                    i.createdAt(), i.questionType(), i.saAnswer(), i.statements(), i.subjectId(), i.subjectName(),
                    i.gradeLevel(), i.topic(), i.content(), i.origin(), i.usageCount(), i.lastUsedAt(), provenance,
                    usage, i.visibility(), canRemove, i.images());
        }
    }

    /** Một chủ đề có câu trong ngân hàng (để dựng ô chọn bộ lọc). */
    public record TopicDto(Integer subjectId, String subjectName, Integer gradeLevel, String topic, long count) {}
}
