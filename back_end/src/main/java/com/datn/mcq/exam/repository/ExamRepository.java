package com.datn.mcq.exam.repository;

import com.datn.mcq.exam.entity.Exam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Integer> {
    boolean existsByExamCode(String examCode);

    /** W1-1: đề chỉ khi thuộc người gọi (không thì coi như không tồn tại). */
    Optional<Exam> findByIdAndCreatedBy(Integer id, Integer createdBy);

    /** W1-1: đề của một người (GET /api/exams), thứ tự theo {@code pageable}. */
    Page<Exam> findByCreatedBy(Integer createdBy, Pageable pageable);

    /** W1-4: số đề đã tạo từ một ma trận (sửa/xóa ma trận đã có đề bị khóa). */
    long countByMatrixId(Integer matrixId);

    /** W1-4: số đề theo từng ma trận: các dòng {@code [matrixId, số đề]}; ma trận không có đề thì không có dòng. */
    @Query("SELECT e.matrixId, COUNT(e) FROM Exam e WHERE e.matrixId IN :matrixIds GROUP BY e.matrixId")
    List<Object[]> countByMatrixIds(@Param("matrixIds") Collection<Integer> matrixIds);

    /** W1-4: đề của một người theo loại ma trận gốc ('matrix' hoặc 'lesson'); đề không có ma trận không thuộc loại nào. */
    @Query("SELECT e FROM Exam e WHERE e.createdBy = :createdBy AND EXISTS "
            + "(SELECT 1 FROM ExamMatrix m WHERE m.id = e.matrixId AND m.kind = :kind)")
    Page<Exam> findByCreatedByAndMatrixKind(@Param("createdBy") Integer createdBy, @Param("kind") String kind,
                                            Pageable pageable);
}
