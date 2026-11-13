package com.datn.mcq.exam.repository;

import com.datn.mcq.exam.entity.ExamGenerationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamGenerationContextRepository extends JpaRepository<ExamGenerationContext, Integer> {

    /** Các phạm vi ngữ cảnh của một đề (cả đề và từng ô). */
    List<ExamGenerationContext> findByExamId(Integer examId);

    /**
     * PR B (12/11): yêu cầu (văn bản, mã) của các phạm vi thuộc nhiều đề trong MỘT truy vấn, không nạp tài liệu: dùng khi
     * xem đề và danh sách đề (yêu cầu thừa hưởng của từng chỗ). Mỗi dòng: exam_id, cell_id, extra_instruction, mã nối bằng
     * dấu phẩy (mã không chứa dấu phẩy, CHECK của V28).
     */
    @Query(value = """
            SELECT exam_id, cell_id, extra_instruction, array_to_string(request_codes, ',') AS codes
            FROM exam_generation_context
            WHERE exam_id IN (:examIds) AND (extra_instruction IS NOT NULL OR request_codes IS NOT NULL)
            """, nativeQuery = true)
    List<Object[]> findRequestsByExamIds(@Param("examIds") java.util.Collection<Integer> examIds);

    /** Số đề khác nhau đang dùng một tài liệu (chặn xóa tài liệu đang dùng). */
    @Query("SELECT count(DISTINCT c.examId) FROM ExamGenerationContext c JOIN c.materialIds m WHERE m = :materialId")
    long countExamsUsingMaterial(@Param("materialId") Integer materialId);
}
