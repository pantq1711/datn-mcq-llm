package com.datn.mcq.generation.dto;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.generation.entity.GenerationJob;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Job sinh câu cho công cụ thí nghiệm (/api/generation/*), thay cho việc trả thẳng entity (W2-2). Giữ các key cũ của
 * entity; không trả {@code prompt_note} (ghi chú prompt, có thể chứa yêu cầu tự do của giáo viên). Key {@code null}
 * bị bỏ khỏi JSON ở các trường mà entity cũng bỏ (tf, started_at, exam_slot_id).
 */
public record GenerationJobDto(
        Integer id,
        Integer runId,
        Integer templateId,
        Integer questionId,
        QuestionType questionType,
        String jobType,
        Difficulty targetDifficulty,
        @JsonInclude(JsonInclude.Include.NON_NULL) String targetStatementDifficulties,
        JobStatus status,
        Integer attempt,
        LocalDateTime nextRunAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime startedAt,
        String lastError,
        Integer tokenCount,
        Double latencyMs,
        Double costUsd,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer examSlotId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static GenerationJobDto fromEntity(GenerationJob job) {
        return new GenerationJobDto(job.getId(), job.getRunId(), job.getTemplateId(), job.getQuestionId(),
                job.getQuestionType(), job.getJobType(), job.getTargetDifficulty(),
                job.getTargetStatementDifficulties(), job.getStatus(), job.getAttempt(), job.getNextRunAt(),
                job.getStartedAt(), job.getLastError(), job.getTokenCount(), job.getLatencyMs(), job.getCostUsd(),
                job.getExamSlotId(), job.getCreatedAt(), job.getUpdatedAt());
    }
}
