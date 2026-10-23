package com.datn.mcq.generation.entity;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.enums.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "generation_job")
public class GenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "run_id", nullable = false)
    private Integer runId;

    @Column(name = "template_id", nullable = false)
    private Integer templateId;

    @Column(name = "question_id")
    private Integer questionId;

    @Column(name = "target_difficulty", length = 10)
    private Difficulty targetDifficulty;

    /** Dạng câu (V4); khóa ngoại kép tới experiment_run(id, question_type). Xuất ra JSON từ GĐ2. */
    @Column(name = "question_type", nullable = false, length = 10, updatable = false)
    private QuestionType questionType = QuestionType.MCQ;

    /**
     * Chỉ với tf: mức mục tiêu của 4 ý a,b,c,d, ví dụ "easy,easy,medium,hard" (CHECK chk_gj_target_statements).
     * Bỏ khỏi JSON khi null, nên job mcq/sa không có key này.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Column(name = "target_statement_difficulties", length = 40)
    private String targetStatementDifficulties;

    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "attempt", nullable = false)
    private Integer attempt = 0;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "latency_ms")
    private Double latencyMs;

    @Column(name = "cost_usd")
    private Double costUsd;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** V7: chỗ trong đề (exam_slot) mà job sinh câu cho; null với job thường (khi đó không có trong JSON). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Column(name = "exam_slot_id")
    private Integer examSlotId;

    /** V8: ghi chú thêm vào prompt của job (ô ma trận: câu thứ k/n, lý do lần sinh trước bị loại); null với job thường. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Column(name = "prompt_note", columnDefinition = "TEXT")
    private String promptNote;

    public GenerationJob() {}

    public String getPromptNote() {
        return promptNote;
    }

    public void setPromptNote(String promptNote) {
        this.promptNote = promptNote;
    }

    public Integer getExamSlotId() {
        return examSlotId;
    }

    public void setExamSlotId(Integer examSlotId) {
        this.examSlotId = examSlotId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getRunId() {
        return runId;
    }

    public void setRunId(Integer runId) {
        this.runId = runId;
    }

    public Integer getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Integer templateId) {
        this.templateId = templateId;
    }

    public Integer getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Integer questionId) {
        this.questionId = questionId;
    }

    public Difficulty getTargetDifficulty() {
        return targetDifficulty;
    }

    public void setTargetDifficulty(Difficulty targetDifficulty) {
        this.targetDifficulty = targetDifficulty;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }

    public LocalDateTime getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(LocalDateTime nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public Double getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Double latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Double getCostUsd() {
        return costUsd;
    }

    public void setCostUsd(Double costUsd) {
        this.costUsd = costUsd;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public String getTargetStatementDifficulties() {
        return targetStatementDifficulties;
    }

    public void setTargetStatementDifficulties(String targetStatementDifficulties) {
        this.targetStatementDifficulties = targetStatementDifficulties;
    }
}

