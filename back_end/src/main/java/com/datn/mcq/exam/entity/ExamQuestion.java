package com.datn.mcq.exam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "exam_question")
@IdClass(ExamQuestion.ExamQuestionId.class)
public class ExamQuestion {

    public static class ExamQuestionId implements Serializable {
        private Integer examId;
        private Integer bankItemId;

        public ExamQuestionId() {}

        public ExamQuestionId(Integer examId, Integer bankItemId) {
            this.examId = examId;
            this.bankItemId = bankItemId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ExamQuestionId that)) return false;
            return Objects.equals(examId, that.examId)
                    && Objects.equals(bankItemId, that.bankItemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(examId, bankItemId);
        }
    }

    @Id
    @Column(name = "exam_id", nullable = false)
    private Integer examId;

    @Id
    @Column(name = "bank_item_id", nullable = false)
    private Integer bankItemId;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "option_order", nullable = false, length = 4)
    private String optionOrder = "ABCD";

    public ExamQuestion() {}

    public Integer getExamId() {
        return examId;
    }

    public void setExamId(Integer examId) {
        this.examId = examId;
    }

    public Integer getBankItemId() {
        return bankItemId;
    }

    public void setBankItemId(Integer bankItemId) {
        this.bankItemId = bankItemId;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getOptionOrder() {
        return optionOrder;
    }

    public void setOptionOrder(String optionOrder) {
        this.optionOrder = optionOrder;
    }
}

