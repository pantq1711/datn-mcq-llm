package com.datn.mcq.generation.entity;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_question")
public class GeneratedQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "template_id", nullable = false)
    private Integer templateId;

    @Column(name = "run_id", nullable = false)
    private Integer runId;

    /** Dạng câu (V4). Không đổi sau khi tạo: khóa ngoại kép của bảng con trỏ tới (id, question_type). */
    @Column(name = "question_type", nullable = false, length = 10, updatable = false)
    private QuestionType questionType = QuestionType.MCQ;

    /** Đáp số câu Trả lời ngắn đúng như LLM viết (đã trim); NULL với mcq và tf (V4). */
    @Column(name = "sa_answer", columnDefinition = "TEXT")
    private String saAnswer;

    @Column(name = "question_text", columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "option_a", columnDefinition = "TEXT")
    private String optionA;

    @Column(name = "option_b", columnDefinition = "TEXT")
    private String optionB;

    @Column(name = "option_c", columnDefinition = "TEXT")
    private String optionC;

    @Column(name = "option_d", columnDefinition = "TEXT")
    private String optionD;

    @Column(name = "correct_option", length = 1)
    private Option correctOption;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "raw_llm_output", nullable = false, columnDefinition = "TEXT")
    private String rawLlmOutput;

    @Column(name = "llm_difficulty", length = 10)
    private Difficulty llmDifficulty;

    @Column(name = "difficulty_judge_model", length = 100)
    private String difficultyJudgeModel;

    @Column(name = "difficulty_prompt_version", length = 50)
    private String difficultyPromptVersion;

    @Column(name = "final_difficulty", length = 10)
    private Difficulty finalDifficulty;

    @Column(name = "parse_success")
    private Boolean parseSuccess;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "latency_ms")
    private Double latencyMs;

    @Column(name = "cost_usd")
    private Double costUsd;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @Column(name = "status", nullable = false, length = 50)
    private QuestionStatus status = QuestionStatus.GENERATED;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public GeneratedQuestion() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Integer templateId) {
        this.templateId = templateId;
    }

    public Integer getRunId() {
        return runId;
    }

    public void setRunId(Integer runId) {
        this.runId = runId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getOptionA() {
        return optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }

    public Option getCorrectOption() {
        return correctOption;
    }

    public void setCorrectOption(Option correctOption) {
        this.correctOption = correctOption;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getRawLlmOutput() {
        return rawLlmOutput;
    }

    public void setRawLlmOutput(String rawLlmOutput) {
        this.rawLlmOutput = rawLlmOutput;
    }

    public Difficulty getLlmDifficulty() {
        return llmDifficulty;
    }

    public void setLlmDifficulty(Difficulty llmDifficulty) {
        this.llmDifficulty = llmDifficulty;
    }

    public String getDifficultyJudgeModel() {
        return difficultyJudgeModel;
    }

    public void setDifficultyJudgeModel(String difficultyJudgeModel) {
        this.difficultyJudgeModel = difficultyJudgeModel;
    }

    public String getDifficultyPromptVersion() {
        return difficultyPromptVersion;
    }

    public void setDifficultyPromptVersion(String difficultyPromptVersion) {
        this.difficultyPromptVersion = difficultyPromptVersion;
    }

    public Difficulty getFinalDifficulty() {
        return finalDifficulty;
    }

    public void setFinalDifficulty(Difficulty finalDifficulty) {
        this.finalDifficulty = finalDifficulty;
    }

    public Boolean getParseSuccess() {
        return parseSuccess;
    }

    public void setParseSuccess(Boolean parseSuccess) {
        this.parseSuccess = parseSuccess;
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

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public void setStatus(QuestionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public String getSaAnswer() {
        return saAnswer;
    }

    public void setSaAnswer(String saAnswer) {
        this.saAnswer = saAnswer;
    }
}

