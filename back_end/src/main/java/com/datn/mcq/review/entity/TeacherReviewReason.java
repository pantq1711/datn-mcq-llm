
package com.datn.mcq.review.entity;

import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.ReasonCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "teacher_review_reason")
@IdClass(TeacherReviewReason.TeacherReviewReasonId.class)
public class TeacherReviewReason {

    public static class TeacherReviewReasonId implements Serializable {

        private Integer reviewId;
        private String reasonCodeDbValue;

        public TeacherReviewReasonId() {}

        public TeacherReviewReasonId(
                Integer reviewId, ReasonCode reasonCode) {
            this.reviewId = reviewId;
            this.reasonCodeDbValue =
                    reasonCode == null ? null : reasonCode.getDbValue();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TeacherReviewReasonId that)) return false;
            return Objects.equals(reviewId, that.reviewId)
                    && Objects.equals(
                            reasonCodeDbValue, that.reasonCodeDbValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reviewId, reasonCodeDbValue);
        }
    }

    @Id
    @Column(name = "review_id", nullable = false)
    private Integer reviewId;

    // Khóa chính lưu trực tiếp giá trị chuỗi trong DB.
    // Không áp dụng AttributeConverter lên thuộc tính @Id.
    @Id
    @Column(name = "reason_code", nullable = false, length = 50)
    private String reasonCodeDbValue;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    public TeacherReviewReason() {}

    public TeacherReviewReason(
            Integer reviewId, ReasonCode reasonCode, String detail) {
        this.reviewId = reviewId;
        setReasonCode(reasonCode);
        this.detail = detail;
    }

    public Integer getReviewId() {
        return reviewId;
    }

    public void setReviewId(Integer reviewId) {
        this.reviewId = reviewId;
    }

    // Giữ API Java hiện tại sử dụng enum ReasonCode.
    public ReasonCode getReasonCode() {
        return reasonCodeDbValue == null
                ? null
                : DbEnum.fromDbValue(
                        ReasonCode.class, reasonCodeDbValue);
    }

    public void setReasonCode(ReasonCode reasonCode) {
        this.reasonCodeDbValue =
                reasonCode == null ? null : reasonCode.getDbValue();
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
