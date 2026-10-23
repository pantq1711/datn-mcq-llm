package com.datn.mcq.matrix.repository;

import com.datn.mcq.matrix.entity.ExamMatrixCell;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamMatrixCellRepository extends JpaRepository<ExamMatrixCell, Integer> {
    List<ExamMatrixCell> findByMatrixIdOrderByPositionAsc(Integer matrixId);
}
