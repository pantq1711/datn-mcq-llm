package com.datn.mcq.catalog.repository;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionTemplateRepository extends JpaRepository<QuestionTemplate, Integer> {
    List<QuestionTemplate> findBySubjectIdOrderByIdAsc(Integer subjectId);
    Optional<QuestionTemplate> findFirstBySubjectIdAndTopicOrderByIdAsc(Integer subjectId, String topic);

    /**
     * W1-4: xóa các template (trong {@code ids}) không còn ô ma trận, câu sinh ra hay job nào tham chiếu; template còn
     * được tham chiếu giữ nguyên (vết nguồn gốc câu). Truy vấn native chỉ đọc generated_question, generation_job,
     * exam_matrix_cell để kiểm tra tham chiếu (ngoại lệ ghi ở docs/architecture/backend-structure.md). Trả số template đã
     * xóa.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM question_template t
            WHERE t.id IN (:ids)
              AND NOT EXISTS (SELECT 1 FROM exam_matrix_cell c WHERE c.template_id = t.id)
              AND NOT EXISTS (SELECT 1 FROM generated_question g WHERE g.template_id = t.id)
              AND NOT EXISTS (SELECT 1 FROM generation_job j WHERE j.template_id = t.id)
            """, nativeQuery = true)
    int deleteUnreferenced(@Param("ids") Collection<Integer> ids);
}
