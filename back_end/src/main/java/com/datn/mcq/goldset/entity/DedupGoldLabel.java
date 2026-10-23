package com.datn.mcq.goldset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "dedup_gold_label")
@IdClass(DedupGoldLabel.DedupGoldLabelId.class)
public class DedupGoldLabel {

    public static class DedupGoldLabelId implements Serializable {
        private Integer pairId;
        private Integer annotatorId;

        public DedupGoldLabelId() {}

        public DedupGoldLabelId(Integer pairId, Integer annotatorId) {
            this.pairId = pairId;
            this.annotatorId = annotatorId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DedupGoldLabelId that)) return false;
            return Objects.equals(pairId, that.pairId)
                    && Objects.equals(annotatorId, that.annotatorId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pairId, annotatorId);
        }
    }

    @Id
    @Column(name = "pair_id", nullable = false)
    private Integer pairId;

    @Id
    @Column(name = "annotator_id", nullable = false)
    private Integer annotatorId;

    @Column(name = "is_duplicate", nullable = false)
    private Boolean isDuplicate;

    @Column(name = "labeled_at")
    private LocalDateTime labeledAt;

    public DedupGoldLabel() {}

    public Integer getPairId() {
        return pairId;
    }

    public void setPairId(Integer pairId) {
        this.pairId = pairId;
    }

    public Integer getAnnotatorId() {
        return annotatorId;
    }

    public void setAnnotatorId(Integer annotatorId) {
        this.annotatorId = annotatorId;
    }

    public Boolean getIsDuplicate() {
        return isDuplicate;
    }

    public void setIsDuplicate(Boolean isDuplicate) {
        this.isDuplicate = isDuplicate;
    }

    public LocalDateTime getLabeledAt() {
        return labeledAt;
    }

    public void setLabeledAt(LocalDateTime labeledAt) {
        this.labeledAt = labeledAt;
    }
}

