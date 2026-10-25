package com.datn.mcq.exam.repository;

import com.datn.mcq.exam.entity.ExamGenerationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamGenerationContextRepository extends JpaRepository<ExamGenerationContext, Integer> {

    /** Các phạm vi ngữ cảnh của một đề (cả đề và từng ô). */
    List<ExamGenerationContext> findByExamId(Integer examId);

    /** Số đề khác nhau đang dùng một tài liệu (chặn xóa tài liệu đang dùng). */
    @Query("SELECT count(DISTINCT c.examId) FROM ExamGenerationContext c JOIN c.materialIds m WHERE m = :materialId")
    long countExamsUsingMaterial(@Param("materialId") Integer materialId);
}
