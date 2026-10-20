package com.datn.mcq.review.repository;

import com.datn.mcq.review.entity.LabelAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabelAssignmentRepository
        extends JpaRepository<LabelAssignment, LabelAssignment.LabelAssignmentId> {
    List<LabelAssignment> findByReviewerId(Integer reviewerId);
}

