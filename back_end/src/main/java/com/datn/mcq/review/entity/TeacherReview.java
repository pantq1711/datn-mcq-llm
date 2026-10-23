package com.datn.mcq.review.entity;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.ReviewDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "teacher_review")
public class TeacherReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "question_id", nullable = false)
    private Integer questionId;

    @Column(name = "reviewer_id", nullable = false)
    private Integer reviewerId;

    @Column(name = "review_purpose", nullable = false, length = 20)
    private Purpose reviewPurpose;

    @Column(name = "reviewer_answer", nullable = false, length = 10)
    private AnswerChoice reviewerAnswer;

    @Column(name = "answer_correct", nullable = false)
    private Boolean answerCorrect;

    @Column(name = "format_ok", nullable = false)
    private Boolean formatOk;

    @Column(name = "difficulty_label", nullable = false, length = 10)
    private Difficulty difficultyLabel;

    @Column(name = "decision", nullable = false, length = 20)
    private ReviewDecision decision;

    @Column(name = "final_option", length = 1)
    private Option finalOption;

    @Column(name = "edited", nullable = false)
    private Boolean edited = Boolean.FALSE;

    @Column(name = "edited_question_text", columnDefinition = "TEXT")
    private String editedQuestionText;

    @Column(name = "edited_option_a", columnDefinition = "TEXT")
    private String editedOptionA;

    @Column(name = "edited_option_b", columnDefinition = "TEXT")
    private String editedOptionB;

    @Column(name = "edited_option_c", columnDefinition = "TEXT")
    private String editedOptionC;

    @Column(name = "edited_option_d", columnDefinition = "TEXT")
    private String editedOptionD;

    @Column(name = "edited_explanation", columnDefinition = "TEXT")
    private String editedExplanation;

    @Column(name = "edit_distance")
    private Integer editDistance;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt = LocalDateTime.now();

    public TeacherReview() {}

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

    public Integer getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Integer reviewerId) {
        this.reviewerId = reviewerId;
    }

    public Purpose getReviewPurpose() {
        return reviewPurpose;
    }

    public void setReviewPurpose(Purpose reviewPurpose) {
        this.reviewPurpose = reviewPurpose;
    }

    public AnswerChoice getReviewerAnswer() {
        return reviewerAnswer;
    }

    public void setReviewerAnswer(AnswerChoice reviewerAnswer) {
        this.reviewerAnswer = reviewerAnswer;
    }

    public Boolean getAnswerCorrect() {
        return answerCorrect;
    }

    public void setAnswerCorrect(Boolean answerCorrect) {
        this.answerCorrect = answerCorrect;
    }

    public Boolean getFormatOk() {
        return formatOk;
    }

    public void setFormatOk(Boolean formatOk) {
        this.formatOk = formatOk;
    }

    public Difficulty getDifficultyLabel() {
        return difficultyLabel;
    }

    public void setDifficultyLabel(Difficulty difficultyLabel) {
        this.difficultyLabel = difficultyLabel;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public void setDecision(ReviewDecision decision) {
        this.decision = decision;
    }

    public Option getFinalOption() {
        return finalOption;
    }

    public void setFinalOption(Option finalOption) {
        this.finalOption = finalOption;
    }

    public Boolean getEdited() {
        return edited;
    }

    public void setEdited(Boolean edited) {
        this.edited = edited;
    }

    public String getEditedQuestionText() {
        return editedQuestionText;
    }

    public void setEditedQuestionText(String editedQuestionText) {
        this.editedQuestionText = editedQuestionText;
    }

    public String getEditedOptionA() {
        return editedOptionA;
    }

    public void setEditedOptionA(String editedOptionA) {
        this.editedOptionA = editedOptionA;
    }

    public String getEditedOptionB() {
        return editedOptionB;
    }

    public void setEditedOptionB(String editedOptionB) {
        this.editedOptionB = editedOptionB;
    }

    public String getEditedOptionC() {
        return editedOptionC;
    }

    public void setEditedOptionC(String editedOptionC) {
        this.editedOptionC = editedOptionC;
    }

    public String getEditedOptionD() {
        return editedOptionD;
    }

    public void setEditedOptionD(String editedOptionD) {
        this.editedOptionD = editedOptionD;
    }

    public String getEditedExplanation() {
        return editedExplanation;
    }

    public void setEditedExplanation(String editedExplanation) {
        this.editedExplanation = editedExplanation;
    }

    public Integer getEditDistance() {
        return editDistance;
    }

    public void setEditDistance(Integer editDistance) {
        this.editDistance = editDistance;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}

