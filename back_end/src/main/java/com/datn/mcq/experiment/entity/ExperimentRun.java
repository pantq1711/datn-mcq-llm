package com.datn.mcq.experiment.entity;

import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.Strategy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "experiment_run")
public class ExperimentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "subject_id", nullable = false)
    private Integer subjectId;

    @Column(name = "purpose", nullable = false, length = 20)
    private Purpose purpose = Purpose.EXPERIMENT;

    @Column(name = "strategy", nullable = false, length = 50)
    private Strategy strategy;

    /**
     * Dạng câu của run (V4). tf/sa chỉ cho production và strategy = zero-shot (CHECK chk_er_tf_sa_production).
     * Phân tích C1/C2/C3 lọc question_type = mcq.
     */
    @Column(name = "question_type", nullable = false, length = 10, updatable = false)
    private QuestionType questionType = QuestionType.MCQ;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "temperature", nullable = false)
    private Double temperature;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "prompt_text", nullable = false, columnDefinition = "TEXT")
    private String promptText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exemplar_ids", columnDefinition = "jsonb")
    private String exemplarIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hyperparams", columnDefinition = "jsonb")
    private String hyperparams;

    @Column(name = "num_questions_requested")
    private Integer numQuestionsRequested;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    public ExperimentRun() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Integer subjectId) {
        this.subjectId = subjectId;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public void setPurpose(Purpose purpose) {
        this.purpose = purpose;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getExemplarIds() {
        return exemplarIds;
    }

    public void setExemplarIds(String exemplarIds) {
        this.exemplarIds = exemplarIds;
    }

    public String getHyperparams() {
        return hyperparams;
    }

    public void setHyperparams(String hyperparams) {
        this.hyperparams = hyperparams;
    }

    public Integer getNumQuestionsRequested() {
        return numQuestionsRequested;
    }

    public void setNumQuestionsRequested(Integer numQuestionsRequested) {
        this.numQuestionsRequested = numQuestionsRequested;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }
}

