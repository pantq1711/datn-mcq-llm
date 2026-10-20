package com.datn.mcq.catalog.repository;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionTemplateRepository extends JpaRepository<QuestionTemplate, Integer> {
    List<QuestionTemplate> findBySubjectIdOrderByIdAsc(Integer subjectId);
    Optional<QuestionTemplate> findFirstBySubjectIdAndTopicOrderByIdAsc(Integer subjectId, String topic);
}

