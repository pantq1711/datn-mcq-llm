package com.datn.mcq.qc.repository;

import com.datn.mcq.qc.entity.QuestionEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionEmbeddingRepository
        extends JpaRepository<QuestionEmbedding, QuestionEmbedding.QuestionEmbeddingId> {
}

