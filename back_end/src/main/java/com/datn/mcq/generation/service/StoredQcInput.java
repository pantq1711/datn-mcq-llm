package com.datn.mcq.generation.service;

import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import com.datn.mcq.qc.dto.QuestionQcInput;

import java.util.ArrayList;
import java.util.List;

/**
 * Đầu vào pipeline QC dựng từ một câu ĐÃ LƯU (không phải từ phản hồi LLM vừa giải mã): dùng cho chạy lại QC
 * (QcRerunService) và job answer_check của bản giáo viên sửa (W1-2). Hàm thuần, không đọc DB.
 *
 * <p>Không có cảnh báo từ bước giải mã (đã không còn tái tạo được từ câu đã lưu).
 */
public final class StoredQcInput {

    private StoredQcInput() {}

    /**
     * @param statements 4 ý a-d của câu tf đã lưu (rỗng với mcq/sa)
     */
    public static QuestionQcInput of(GeneratedQuestion gq, List<GeneratedQuestionStatement> statements,
                                     boolean validateLatex, boolean runAnswerCheck) {
        boolean parseSuccess = Boolean.TRUE.equals(gq.getParseSuccess());
        if (gq.getQuestionType() == QuestionType.TF) {
            List<String> texts = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (GeneratedQuestionStatement s : statements) {
                texts.add(s.getStatementText());
                labels.add(s.getLlmLabel() != null ? s.getLlmLabel().getDbValue() : null);
            }
            return QuestionQcInput.trueFalse(gq.getId(), parseSuccess, gq.getQuestionText(), texts, labels,
                    gq.getExplanation(), validateLatex, runAnswerCheck, null);
        }
        if (gq.getQuestionType() == QuestionType.SA) {
            // sa_answer lưu dạng TEXT là chuỗi JSON đã giải mã, nên coi là chuỗi.
            return QuestionQcInput.shortAnswer(gq.getId(), parseSuccess, gq.getQuestionText(), gq.getExplanation(),
                    gq.getSaAnswer(), true, validateLatex, runAnswerCheck, null);
        }
        return new QuestionQcInput(gq.getId(), parseSuccess, gq.getQuestionText(), gq.getOptionA(), gq.getOptionB(),
                gq.getOptionC(), gq.getOptionD(), gq.getCorrectOption(), gq.getExplanation(), validateLatex,
                runAnswerCheck);
    }
}
