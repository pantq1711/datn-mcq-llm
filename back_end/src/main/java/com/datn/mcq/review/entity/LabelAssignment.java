package com.datn.mcq.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "label_assignment")
@IdClass(LabelAssignment.LabelAssignmentId.class)
public class LabelAssignment {

    public static class LabelAssignmentId implements Serializable {
        private Integer questionId;
        private Integer reviewerId;

        public LabelAssignmentId() {}

        public LabelAssignmentId(Integer questionId, Integer reviewerId) {
            this.questionId = questionId;
            this.reviewerId = reviewerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LabelAssignmentId that)) return false;
            return Objects.equals(questionId, that.questionId)
                    && Objects.equals(reviewerId, that.reviewerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(questionId, reviewerId);
        }
    }

    @Id
    @Column(name = "question_id", nullable = false)
    private Integer questionId;

    @Id
    @Column(name = "reviewer_id", nullable = false)
    private Integer reviewerId;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    public LabelAssignment() {}

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

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }
}

