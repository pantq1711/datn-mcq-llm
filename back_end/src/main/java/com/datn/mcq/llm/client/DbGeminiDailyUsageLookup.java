package com.datn.mcq.llm.client;

import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import com.datn.mcq.qc.repository.QuestionGroundingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * W2-4b (C): cài đặt {@link GeminiDailyUsageLookup} đọc trực tiếp từ DB qua 3 repository:
 * câu LLM sinh ({@link GeneratedQuestionRepository}), kết quả Solver ({@link QualityCheckResultRepository}),
 * và kết quả judge grounding ({@link QuestionGroundingRepository}).
 * Tách riêng khỏi {@code GenerationJobProcessor} để cắt đứt vòng phụ thuộc bean (LlmClient -> GeminiRateLimiter ->
 * GenerationJobProcessor -> QcOrchestrator -> AnswerSolverChecker -> LlmClient).
 */
@Component
public class DbGeminiDailyUsageLookup implements GeminiDailyUsageLookup {

    private final GeneratedQuestionRepository generatedQuestionRepository;
    private final QualityCheckResultRepository qualityCheckResultRepository;
    private final QuestionGroundingRepository questionGroundingRepository;

    public DbGeminiDailyUsageLookup(
            GeneratedQuestionRepository generatedQuestionRepository,
            QualityCheckResultRepository qualityCheckResultRepository,
            QuestionGroundingRepository questionGroundingRepository) {
        this.generatedQuestionRepository = generatedQuestionRepository;
        this.qualityCheckResultRepository = qualityCheckResultRepository;
        this.questionGroundingRepository = questionGroundingRepository;
    }

    /**
     * W2-4b (C): đếm tổng số lượt gọi Gemini đã lưu kết quả trong DB kể từ mốc {@code since} (00:00 giờ Thái Bình Dương
     * đã đổi sang giờ địa phương của JVM): câu LLM sinh ({@code edited_from_question_id IS NULL}) + kết quả Solver có
     * {@code token_count} + kết quả judge "bám tài liệu" có {@code token_count}.
     */
    @Override
    @Transactional(readOnly = true)
    public int countCallsSince(LocalDateTime since) {
        int questions = generatedQuestionRepository.countLlmGeneratedSince(since);
        int solver = qualityCheckResultRepository.countSolverCallsSince(since);
        int grounding = questionGroundingRepository.countGroundingCallsSince(since);
        return questions + solver + grounding;
    }
}

