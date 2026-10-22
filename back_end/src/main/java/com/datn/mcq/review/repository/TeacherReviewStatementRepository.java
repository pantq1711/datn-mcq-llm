package com.datn.mcq.review.repository;

import com.datn.mcq.review.entity.TeacherReviewStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherReviewStatementRepository
        extends JpaRepository<TeacherReviewStatement, TeacherReviewStatement.Key> {
    List<TeacherReviewStatement> findByReviewIdOrderByPositionAsc(Integer reviewId);
}
