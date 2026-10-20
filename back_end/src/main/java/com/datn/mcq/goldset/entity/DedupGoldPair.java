package com.datn.mcq.goldset.entity;

import com.datn.mcq.enums.GoldSplit;
import com.datn.mcq.enums.SamplingStratum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dedup_gold_pair")
public class DedupGoldPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "question_id_1", nullable = false)
    private Integer questionId1;

    @Column(name = "question_id_2", nullable = false)
    private Integer questionId2;

    @Column(name = "sampling_stratum", nullable = false, length = 20)
    private SamplingStratum samplingStratum;

    @Column(name = "final_is_duplicate")
    private Boolean finalIsDuplicate;

    @Column(name = "adjudicator_id")
    private Integer adjudicatorId;

    @Column(name = "split", length = 10)
    private GoldSplit split;

    @Column(name = "predicted_similarity")
    private Double predictedSimilarity;

    public DedupGoldPair() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getQuestionId1() {
        return questionId1;
    }

    public void setQuestionId1(Integer questionId1) {
        this.questionId1 = questionId1;
    }

    public Integer getQuestionId2() {
        return questionId2;
    }

    public void setQuestionId2(Integer questionId2) {
        this.questionId2 = questionId2;
    }

    public SamplingStratum getSamplingStratum() {
        return samplingStratum;
    }

    public void setSamplingStratum(SamplingStratum samplingStratum) {
        this.samplingStratum = samplingStratum;
    }

    public Boolean getFinalIsDuplicate() {
        return finalIsDuplicate;
    }

    public void setFinalIsDuplicate(Boolean finalIsDuplicate) {
        this.finalIsDuplicate = finalIsDuplicate;
    }

    public Integer getAdjudicatorId() {
        return adjudicatorId;
    }

    public void setAdjudicatorId(Integer adjudicatorId) {
        this.adjudicatorId = adjudicatorId;
    }

    public GoldSplit getSplit() {
        return split;
    }

    public void setSplit(GoldSplit split) {
        this.split = split;
    }

    public Double getPredictedSimilarity() {
        return predictedSimilarity;
    }

    public void setPredictedSimilarity(Double predictedSimilarity) {
        this.predictedSimilarity = predictedSimilarity;
    }
}

