package com.datn.mcq.matrix.repository;

import com.datn.mcq.matrix.entity.ExamMatrix;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamMatrixRepository extends JpaRepository<ExamMatrix, Integer> {
    List<ExamMatrix> findAllByOrderByIdDesc();
}
