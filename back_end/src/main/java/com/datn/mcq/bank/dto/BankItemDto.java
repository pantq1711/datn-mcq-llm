package com.datn.mcq.bank.dto;

import com.datn.mcq.bank.entity.BankItem;
import com.datn.mcq.bank.entity.BankItemStatement;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Một câu trong ngân hàng, trả về cho GET /api/bank-items (quyết định 7 ngày 22/10).
 *
 * <p>12 key cũ (trước V4 endpoint trả thẳng entity BankItem) giữ nguyên tên và luôn có mặt, kể cả khi null:
 * id, sourceQuestionId, reviewId, questionText, optionA..optionD, correctOption, explanation, difficulty, createdAt.
 * Key mới: questionType luôn có; saAnswer (chỉ sa) và statements (chỉ tf) bị bỏ khỏi JSON khi null.
 */
public record BankItemDto(
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
        // PA1 (13/11, V29): chỉ cho xem đề (chế độ giáo viên); /api/bank-items CHƯA trả hai trường này.
        @JsonIgnore String distractorErrors,
        @JsonIgnore String distractorChecks
) {
    /** Không có lỗi sai kèm phương án (chữ ký trước 13/11). */
    public BankItemDto(Integer id, Integer sourceQuestionId, Integer reviewId, String questionText, String optionA,
                       String optionB, String optionC, String optionD, Option correctOption, String explanation,
                       Difficulty difficulty, LocalDateTime createdAt, QuestionType questionType, String saAnswer,
                       List<StatementDto> statements) {
        this(id, sourceQuestionId, reviewId, questionText, optionA, optionB, optionC, optionD, correctOption,
                explanation, difficulty, createdAt, questionType, saAnswer, statements, null, null);
    }

    /** Một ý a-d của câu Đúng/Sai trong ngân hàng. */
    public record StatementDto(StatementKey key, String text, TruthValue label, Difficulty difficulty) {
        public static StatementDto fromEntity(BankItemStatement s) {
            return new StatementDto(s.getStatementKey(), s.getStatementText(), s.getLabel(), s.getDifficulty());
        }
    }

    /**
     * @param statements các ý của câu (chỉ dùng khi câu là tf; với mcq và sa bị bỏ qua để key không xuất hiện)
     */
    public static BankItemDto fromEntity(BankItem item, List<BankItemStatement> statements) {
        List<StatementDto> statementDtos = item.getQuestionType() == QuestionType.TF
                ? statements.stream().map(StatementDto::fromEntity).toList()
                : null;
        return new BankItemDto(
                item.getId(),
                item.getSourceQuestionId(),
                item.getReviewId(),
                item.getQuestionText(),
                item.getOptionA(),
                item.getOptionB(),
                item.getOptionC(),
                item.getOptionD(),
                item.getCorrectOption(),
                item.getExplanation(),
                item.getDifficulty(),
                item.getCreatedAt(),
                item.getQuestionType(),
                item.getQuestionType() == QuestionType.SA ? item.getSaAnswer() : null,
                statementDtos,
                item.getDistractorErrors(),
                item.getDistractorChecks()
        );
    }
}
