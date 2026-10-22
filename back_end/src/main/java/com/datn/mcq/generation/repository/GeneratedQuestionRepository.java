package com.datn.mcq.generation.repository;

import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedQuestionRepository extends JpaRepository<GeneratedQuestion, Integer> {
    List<GeneratedQuestion> findAllByOrderByIdDesc();
    List<GeneratedQuestion> findByStatusOrderByIdDesc(QuestionStatus status);
    List<GeneratedQuestion> findByRunIdOrderByIdAsc(Integer runId);

    /** Độ đa dạng: 10 câu giải mã được gần nhất cùng template và dạng câu, ở mọi run và mọi trạng thái. */
    List<GeneratedQuestion> findTop10ByTemplateIdAndQuestionTypeAndParseSuccessTrueOrderByCreatedAtDescIdDesc(
            Integer templateId, QuestionType questionType);
}

