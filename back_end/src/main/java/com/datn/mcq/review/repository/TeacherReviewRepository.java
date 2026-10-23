package com.datn.mcq.review.repository;

import com.datn.mcq.review.entity.TeacherReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherReviewRepository extends JpaRepository<TeacherReview, Integer> {
    Optional<TeacherReview> findByQuestionIdAndReviewerId(Integer questionId, Integer reviewerId);
    List<TeacherReview> findByQuestionIdOrderByIdDesc(Integer questionId);
}

