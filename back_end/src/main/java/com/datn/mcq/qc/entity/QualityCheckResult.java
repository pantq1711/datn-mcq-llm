package com.datn.mcq.qc.entity;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.CheckType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "quality_check_result")
public class QualityCheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "question_id", nullable = false)
    private Integer questionId;

    @Column(name = "check_type", nullable = false, length = 50)
    private CheckType checkType;

    @Column(name = "passed", nullable = false)
    private Boolean passed;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "compared_to_question_id")
    private Integer comparedToQuestionId;

    @Column(name = "threshold_used")
    private Double thresholdUsed;

    @Column(name = "judge_answer", length = 10)
    private AnswerChoice judgeAnswer;

    @Column(name = "judge_model", length = 100)
    private String judgeModel;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    public QualityCheckResult() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Integer questionId) {
        this.questionId = questionId;
    }

    public CheckType getCheckType() {
        return checkType;
    }

    public void setCheckType(CheckType checkType) {
        this.checkType = checkType;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public Integer getComparedToQuestionId() {
        return comparedToQuestionId;
    }

    public void setComparedToQuestionId(Integer comparedToQuestionId) {
        this.comparedToQuestionId = comparedToQuestionId;
    }

    public Double getThresholdUsed() {
        return thresholdUsed;
    }

    public void setThresholdUsed(Double thresholdUsed) {
        this.thresholdUsed = thresholdUsed;
    }

    public AnswerChoice getJudgeAnswer() {
        return judgeAnswer;
    }

    public void setJudgeAnswer(AnswerChoice judgeAnswer) {
        this.judgeAnswer = judgeAnswer;
    }

    public String getJudgeModel() {
        return judgeModel;
    }

    public void setJudgeModel(String judgeModel) {
        this.judgeModel = judgeModel;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }
}

