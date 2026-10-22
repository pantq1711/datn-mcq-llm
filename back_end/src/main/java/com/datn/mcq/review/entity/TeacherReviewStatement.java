package com.datn.mcq.review.entity;

import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.enums.TruthValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Nhãn chấm mù của giáo viên cho một ý a-d của câu Đúng/Sai (V4).
 * reviewer_label nhập trước khi thấy nhãn LLM; label_correct do server tính (reviewer_label = llm_label).
 */
@Entity
@Table(name = "teacher_review_statement")
@IdClass(TeacherReviewStatement.Key.class)
public class TeacherReviewStatement {

    public static class Key implements Serializable {
        private Integer reviewId;
        private String position;

        public Key() {}

        public Key(Integer reviewId, String position) {
            this.reviewId = reviewId;
            this.position = position;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key that)) return false;
            return Objects.equals(reviewId, that.reviewId) && Objects.equals(position, that.position);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reviewId, position);
        }
    }

    @Id
    @Column(name = "review_id", nullable = false)
    private Integer reviewId;

    @Id
    @Column(name = "position", nullable = false, length = 1)
    private String position;

    /** Hằng 'tf', chỉ dùng cho khóa ngoại kép tới teacher_review(id, question_type). */
    @Column(name = "question_type", nullable = false, length = 10, updatable = false)
    private QuestionType questionType = QuestionType.TF;

    @Column(name = "reviewer_label", nullable = false, length = 20)
    private TruthChoice reviewerLabel;

    @Column(name = "difficulty_label", nullable = false, length = 10)
    private Difficulty difficultyLabel;

    @Column(name = "label_correct", nullable = false)
    private Boolean labelCorrect;

    @Column(name = "final_label", length = 1)
    private TruthValue finalLabel;

    @Column(name = "edited_statement_text", columnDefinition = "TEXT")
    private String editedStatementText;

    public TeacherReviewStatement() {}

    public TeacherReviewStatement(Integer reviewId, StatementKey key) {
        this.reviewId = reviewId;
        setStatementKey(key);
    }

    public Integer getReviewId() {
        return reviewId;
    }

    public void setReviewId(Integer reviewId) {
        this.reviewId = reviewId;
    }

    public StatementKey getStatementKey() {
        return DbEnum.fromDbValue(StatementKey.class, position);
    }

    public void setStatementKey(StatementKey key) {
        this.position = key != null ? key.getDbValue() : null;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public TruthChoice getReviewerLabel() {
        return reviewerLabel;
    }

    public void setReviewerLabel(TruthChoice reviewerLabel) {
        this.reviewerLabel = reviewerLabel;
    }

    public Difficulty getDifficultyLabel() {
        return difficultyLabel;
    }

    public void setDifficultyLabel(Difficulty difficultyLabel) {
        this.difficultyLabel = difficultyLabel;
    }

    public Boolean getLabelCorrect() {
        return labelCorrect;
    }

    public void setLabelCorrect(Boolean labelCorrect) {
        this.labelCorrect = labelCorrect;
    }

    public TruthValue getFinalLabel() {
        return finalLabel;
    }

    public void setFinalLabel(TruthValue finalLabel) {
        this.finalLabel = finalLabel;
    }

    public String getEditedStatementText() {
        return editedStatementText;
    }

    public void setEditedStatementText(String editedStatementText) {
        this.editedStatementText = editedStatementText;
    }
}
