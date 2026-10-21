package com.datn.mcq.qc.repository;

import com.datn.mcq.enums.CheckType;
import com.datn.mcq.qc.entity.QualityCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QualityCheckResultRepository extends JpaRepository<QualityCheckResult, Integer> {
    List<QualityCheckResult> findByQuestionIdOrderByIdAsc(Integer questionId);
    Optional<QualityCheckResult> findByQuestionIdAndCheckType(Integer questionId, CheckType checkType);

    /**
     * Ứng viên so trùng (quyết định 2, 8 và 9, 2026-10-21). Truy vấn native CHỈ ĐỌC trên generated_question:
     * <ul>
     *   <li>câu qc_passed hoặc in_bank thuộc một run được so ({@code comparableRunIds});</li>
     *   <li>câu cùng run ({@code runId}) đã có dòng dedup passed, trừ rejected, format_failed, duplicate.</li>
     * </ul>
     * {@code comparableRunIds} do nơi gọi tính theo purpose (experiment: chỉ run đó; production: run production
     * cùng môn), không được rỗng. Mỗi dòng: id, question_text, option_a, option_b, option_c, option_d.
     */
    @Query(value = """
            SELECT g.id, g.question_text, g.option_a, g.option_b, g.option_c, g.option_d
            FROM generated_question g
            WHERE g.id <> :questionId
              AND (
                    (g.status IN ('qc_passed', 'in_bank') AND g.run_id IN (:comparableRunIds))
                 OR (g.run_id = :runId
                     AND g.status NOT IN ('rejected', 'format_failed', 'duplicate')
                     AND EXISTS (SELECT 1 FROM quality_check_result d
                                 WHERE d.question_id = g.id
                                   AND d.check_type = 'dedup'
                                   AND d.passed = TRUE))
                  )
            ORDER BY g.id
            """, nativeQuery = true)
    List<Object[]> findDedupCandidateRows(
            @Param("questionId") Integer questionId,
            @Param("runId") Integer runId,
            @Param("comparableRunIds") Collection<Integer> comparableRunIds);
}
