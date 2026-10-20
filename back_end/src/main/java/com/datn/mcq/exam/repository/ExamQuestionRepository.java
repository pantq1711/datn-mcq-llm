package com.datn.mcq.exam.repository;

import com.datn.mcq.exam.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamQuestionRepository
        extends JpaRepository<ExamQuestion, ExamQuestion.ExamQuestionId> {
    List<ExamQuestion> findByExamIdOrderByPositionAsc(Integer examId);
}

