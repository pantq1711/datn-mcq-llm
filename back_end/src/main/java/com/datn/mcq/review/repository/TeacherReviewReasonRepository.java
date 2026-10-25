package com.datn.mcq.review.repository;

import com.datn.mcq.review.entity.TeacherReviewReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherReviewReasonRepository
        extends JpaRepository<TeacherReviewReason, TeacherReviewReason.TeacherReviewReasonId> {
    List<TeacherReviewReason> findByReviewId(Integer reviewId);
    void deleteByReviewId(Integer reviewId);
}

