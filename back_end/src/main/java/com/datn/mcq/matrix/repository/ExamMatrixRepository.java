package com.datn.mcq.matrix.repository;

import com.datn.mcq.matrix.entity.ExamMatrix;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamMatrixRepository extends JpaRepository<ExamMatrix, Integer> {
    /** W1-1: ma trận của một người, mới nhất trước. W1-4: chỉ loại {@code kind} (ma trận ẩn của sinh theo bài bị bỏ). */
    List<ExamMatrix> findAllByCreatedByAndKindOrderByIdDesc(Integer createdBy, String kind);

    /** W1-1: ma trận chỉ khi thuộc người gọi (không thì coi như không tồn tại). W1-4: và đúng loại {@code kind}. */
    Optional<ExamMatrix> findByIdAndCreatedByAndKind(Integer id, Integer createdBy, String kind);
}
