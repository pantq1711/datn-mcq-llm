package com.datn.mcq.bank.entity;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_item")
public class BankItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "source_question_id", nullable = false, unique = true)
    private Integer sourceQuestionId;

    @Column(name = "review_id", nullable = false, unique = true)
    private Integer reviewId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "option_a", nullable = false, columnDefinition = "TEXT")
    private String optionA;

    @Column(name = "option_b", nullable = false, columnDefinition = "TEXT")
    private String optionB;

    @Column(name = "option_c", nullable = false, columnDefinition = "TEXT")
    private String optionC;

    @Column(name = "option_d", nullable = false, columnDefinition = "TEXT")
    private String optionD;

    @Column(name = "correct_option", nullable = false, length = 1)
    private Option correctOption;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "difficulty", nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public BankItem() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getSourceQuestionId() {
        return sourceQuestionId;
    }

    public void setSourceQuestionId(Integer sourceQuestionId) {
        this.sourceQuestionId = sourceQuestionId;
    }

    public Integer getReviewId() {
        return reviewId;
    }

    public void setReviewId(Integer reviewId) {
        this.reviewId = reviewId;
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

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

