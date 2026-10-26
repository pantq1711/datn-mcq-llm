package com.datn.mcq.qc.entity;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.SaJudgeKind;
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

    /** Dạng câu (V4); khóa ngoại kép tới generated_question(id, question_type). */
    @Column(name = "question_type", nullable = false, length = 10, updatable = false)
    private QuestionType questionType = QuestionType.MCQ;

    /** Chỉ với answer_check của câu sa. */
    @Column(name = "judge_sa_kind", length = 20)
    private SaJudgeKind judgeSaKind;

    /** Chuỗi gốc Solver trả với câu sa (kind number hoặc unparseable). */
    @Column(name = "judge_sa_answer", columnDefinition = "TEXT")
    private String judgeSaAnswer;

    /** V14 (W1-3): lời giải thích của Solver (chỉ dòng answer_check, tối đa 4000 ký tự); NULL với dòng cũ. */
    @Column(name = "solver_reasoning", columnDefinition = "TEXT")
    private String solverReasoning;

    /** V21 (đo chi phí): token của lượt gọi Solver cho ra dòng answer_check này; NULL với dòng khác, dòng cũ, kết quả chép. */
    @Column(name = "token_count")
    private Integer tokenCount;

    /** V21: độ trễ (mili giây) của lượt gọi Solver đó; cùng quy tắc NULL với {@link #tokenCount}. */
    @Column(name = "latency_ms")
    private Double latencyMs;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    /** V5: cảnh báo không chặn của bước format, dạng mảng JSON; chỉ dòng format; NULL khi không có. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", columnDefinition = "jsonb")
    private String warnings;

    /** V8: lý do bước format không qua, dạng mảng JSON; chỉ dòng format có passed = FALSE; NULL khi passed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", columnDefinition = "jsonb")
    private String errors;

    public QualityCheckResult() {}

    public String getErrors() {
        return errors;
    }

    public void setErrors(String errors) {
        this.errors = errors;
    }

    public String getWarnings() {
        return warnings;
    }

    public void setWarnings(String warnings) {
        this.warnings = warnings;
    }

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

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public SaJudgeKind getJudgeSaKind() {
        return judgeSaKind;
    }

    public void setJudgeSaKind(SaJudgeKind judgeSaKind) {
        this.judgeSaKind = judgeSaKind;
    }

    public String getJudgeSaAnswer() {
        return judgeSaAnswer;
    }

    public void setJudgeSaAnswer(String judgeSaAnswer) {
        this.judgeSaAnswer = judgeSaAnswer;
    }

    public String getSolverReasoning() {
        return solverReasoning;
    }

    public void setSolverReasoning(String solverReasoning) {
        this.solverReasoning = solverReasoning;
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
}

