package com.datn.mcq.matrix.repository;

import com.datn.mcq.matrix.entity.ExamMatrix;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamMatrixRepository extends JpaRepository<ExamMatrix, Integer> {
    /** W1-1: ma trận của một người, mới nhất trước. */
    List<ExamMatrix> findAllByCreatedByOrderByIdDesc(Integer createdBy);

    /** W1-1: ma trận chỉ khi thuộc người gọi (không thì coi như không tồn tại). */
    Optional<ExamMatrix> findByIdAndCreatedBy(Integer id, Integer createdBy);
}
