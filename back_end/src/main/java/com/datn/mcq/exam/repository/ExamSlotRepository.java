package com.datn.mcq.exam.repository;

import com.datn.mcq.exam.entity.ExamSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamSlotRepository extends JpaRepository<ExamSlot, Integer> {

    List<ExamSlot> findByExamIdOrderByCellIdAscSlotNoAsc(Integer examId);

    /** Khóa các chỗ của một đề khi đối soát, để hai lần đối soát đồng thời không tạo job trùng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSlot s WHERE s.examId = :examId ORDER BY s.cellId, s.slotNo")
    List<ExamSlot> lockByExamId(@Param("examId") Integer examId);

    /**
     * Câu ngân hàng khớp một ô (phần C mục 2): cùng môn, cùng dạng, cùng mức (tf: cùng 4 mức theo thứ tự a-d), cùng chủ
     * đề (so sau trim, không phân biệt hoa thường, theo topic của template gốc); nếu câu được sinh từ một ô ma trận thì
     * còn phải cùng nội dung. Bỏ câu đã nằm trong đề này. Câu chưa nằm trong đề nào được ưu tiên.
     *
     * <p>Truy vấn native chỉ đọc qua bank_item, generated_question, question_template, exam_matrix_cell, exam_question
     * (ngoại lệ ghi ở docs/architecture/backend-structure.md). {@code difficulty} và {@code levels} truyền chuỗi rỗng khi
     * không dùng.
     */
    @Query(value = """
            SELECT b.id
            FROM bank_item b
            JOIN generated_question g ON g.id = b.source_question_id
            JOIN question_template t ON t.id = g.template_id
            LEFT JOIN exam_matrix_cell c ON c.template_id = t.id
            WHERE b.question_type = :type
              AND t.subject_id = :subjectId
              AND lower(btrim(coalesce(t.topic, ''))) = lower(btrim(:topic))
              AND (c.id IS NULL OR lower(btrim(coalesce(c.content, ''))) = lower(btrim(:content)))
              AND ((:type <> 'tf' AND b.difficulty = :difficulty)
                   OR (:type = 'tf' AND (SELECT string_agg(s.difficulty, ',' ORDER BY s.position)
                                         FROM bank_item_statement s WHERE s.bank_item_id = b.id) = :levels))
              AND b.id NOT IN (SELECT es.bank_item_id FROM exam_slot es
                               WHERE es.exam_id = :examId AND es.bank_item_id IS NOT NULL)
            ORDER BY (EXISTS (SELECT 1 FROM exam_slot o WHERE o.bank_item_id = b.id)
                      OR EXISTS (SELECT 1 FROM exam_question q WHERE q.bank_item_id = b.id)), b.id
            LIMIT :limit
            """, nativeQuery = true)
    List<Integer> findBankCandidates(@Param("examId") Integer examId,
                                     @Param("subjectId") Integer subjectId,
                                     @Param("type") String type,
                                     @Param("topic") String topic,
                                     @Param("content") String content,
                                     @Param("difficulty") String difficulty,
                                     @Param("levels") String levels,
                                     @Param("limit") int limit);
}
