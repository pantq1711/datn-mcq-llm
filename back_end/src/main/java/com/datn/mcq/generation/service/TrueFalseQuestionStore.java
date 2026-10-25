package com.datn.mcq.generation.service;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.generation.dto.ParsedTfDto;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.generation.repository.GeneratedQuestionStatementRepository;
import com.datn.mcq.llm.dto.LlmCallResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lưu câu Đúng/Sai vừa sinh (GĐ3): generated_question và 4 dòng generated_question_statement trong cùng một
 * transaction. Tách thành bean riêng để @Transactional có hiệu lực cả khi gọi từ GenerationService lẫn từ worker
 * (M-24). Giải mã lỗi thì không lưu ý nào. Không gọi mạng.
 */
@Service
public class TrueFalseQuestionStore {

    private final GeneratedQuestionRepository generatedQuestionRepository;
    private final GeneratedQuestionStatementRepository statementRepository;

    public TrueFalseQuestionStore(
            GeneratedQuestionRepository generatedQuestionRepository,
            GeneratedQuestionStatementRepository statementRepository) {
        this.generatedQuestionRepository = generatedQuestionRepository;
        this.statementRepository = statementRepository;
    }

    /**
     * @param statementDifficulties mức mục tiêu của 4 ý (a-d), ghi vào llm_difficulty của từng ý; mức độ cấp câu để
     *                              NULL (CHECK chk_gq_type_columns)
     */
    @Transactional
    public GeneratedQuestion save(
            Integer templateId,
            Integer runId,
            List<Difficulty> statementDifficulties,
            LlmCallResult llmResult,
            ParsedTfDto parsed,
            int attemptCount) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setQuestionType(QuestionType.TF);
        gq.setTemplateId(templateId);
        gq.setRunId(runId);
        gq.setQuestionText(parsed.questionText());
        gq.setExplanation(parsed.explanation());
        gq.setRawLlmOutput(llmResult.rawText() != null ? llmResult.rawText() : "");
        gq.setLlmDifficulty(null);
        gq.setParseSuccess(parsed.parseSuccess());
        gq.setTokenCount(llmResult.tokenCount());
        gq.setLatencyMs(llmResult.latencyMs());
        gq.setCostUsd(llmResult.costUsd());
        gq.setAttemptCount(Math.max(1, attemptCount));
        gq.setStatus(QuestionStatus.GENERATED);
        gq.setCreatedAt(LocalDateTime.now());
        GeneratedQuestion saved = generatedQuestionRepository.save(gq);

        if (parsed.parseSuccess()) {
            StatementKey[] keys = StatementKey.values();
            for (int i = 0; i < keys.length; i++) {
                GeneratedQuestionStatement s = new GeneratedQuestionStatement(saved.getId(), keys[i]);
                s.setStatementText(i < parsed.statements().size() ? parsed.statements().get(i) : null);
                // Nhãn không hợp lệ (đã báo lỗi format) lưu NULL; raw_llm_output giữ bản gốc.
                s.setLlmLabel(i < parsed.labels().size() ? TruthValue.fromLabel(parsed.labels().get(i)) : null);
                s.setLlmDifficulty(statementDifficulties != null && i < statementDifficulties.size()
                        ? statementDifficulties.get(i) : null);
                statementRepository.save(s);
            }
        }
        return saved;
    }
}
