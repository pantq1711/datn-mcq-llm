package com.datn.mcq.qc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "question_embedding")
@IdClass(QuestionEmbedding.QuestionEmbeddingId.class)
public class QuestionEmbedding {

    public static class QuestionEmbeddingId implements Serializable {
        private Integer questionId;
        private String embeddingModel;

        public QuestionEmbeddingId() {}

        public QuestionEmbeddingId(Integer questionId, String embeddingModel) {
            this.questionId = questionId;
            this.embeddingModel = embeddingModel;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QuestionEmbeddingId that)) return false;
            return Objects.equals(questionId, that.questionId)
                    && Objects.equals(embeddingModel, that.embeddingModel);
        }

        @Override
        public int hashCode() {
            return Objects.hash(questionId, embeddingModel);
        }
    }

    @Id
    @Column(name = "question_id", nullable = false)
    private Integer questionId;

    @Id
    @Column(name = "embedding_model", nullable = false, length = 100)
    private String embeddingModel;

    @Column(name = "embedding")
    private byte[] embedding;

    public QuestionEmbedding() {}

    public Integer getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Integer questionId) {
        this.questionId = questionId;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }
}

