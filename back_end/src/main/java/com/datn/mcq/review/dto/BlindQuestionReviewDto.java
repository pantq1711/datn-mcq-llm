package com.datn.mcq.review.dto;

import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO trả về cho form chấm của giáo viên (Blind Review Contract).
 * Tuân thủ Quy tắc 9 trong docs/architecture/backend-structure.md, mục 3 docs/contract/enums.md
 * và quyết định 21/10 mục 2 trong docs/ai-development-rules.md. Áp dụng cho CẢ production và experiment:
 * KHÔNG chứa correct_option, explanation, llm_difficulty, status, latexValid, latexErrors
 * hay bất kỳ kết quả QC nào (format, dedup, answer_check).
 *
 * <p>V4: có questionType để frontend dựng đúng form. Câu sa không có optionA..optionD (null nên bị bỏ khỏi JSON)
 * và KHÔNG có đáp số LLM (sa_answer) hay cảnh báo về đáp số.
 *
 * <p>GĐ3: câu tf có statements gồm 4 ý (chỉ key và text), KHÔNG có nhãn, mức độ của LLM hay kết quả Solver;
 * câu mcq/sa không có key statements.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlindQuestionReviewDto(
        Integer id,
        Integer templateId,
        Purpose reviewPurpose,
        String subjectName,
        Integer gradeLevel,
        String topic,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        LocalDateTime createdAt,
        QuestionType questionType,
        List<BlindStatementDto> statements
) {
}
