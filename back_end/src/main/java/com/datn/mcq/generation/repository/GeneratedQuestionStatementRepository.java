package com.datn.mcq.generation.repository;

import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GeneratedQuestionStatementRepository
        extends JpaRepository<GeneratedQuestionStatement, GeneratedQuestionStatement.Key> {
    List<GeneratedQuestionStatement> findByQuestionIdOrderByPositionAsc(Integer questionId);

    List<GeneratedQuestionStatement> findByQuestionIdInOrderByQuestionIdAscPositionAsc(Collection<Integer> questionIds);
}
