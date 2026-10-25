package com.datn.mcq.exam.repository;

import com.datn.mcq.exam.entity.ExamSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ExamSlotRepository extends JpaRepository<ExamSlot, Integer> {

    List<ExamSlot> findByExamIdOrderByCellIdAscSlotNoAsc(Integer examId);

    /** Các chỗ của nhiều đề (danh sách đề của tôi, chỉ đọc). */
    List<ExamSlot> findByExamIdIn(Collection<Integer> examIds);

    /** Khóa các chỗ của một đề khi đối soát, để hai lần đối soát đồng thời không tạo job trùng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSlot s WHERE s.examId = :examId ORDER BY s.cellId, s.slotNo")
    List<ExamSlot> lockByExamId(@Param("examId") Integer examId);

    /**
     * Câu ngân hàng khớp một ô (phần C mục 2): cùng môn, cùng dạng, cùng mức (tf: cùng 4 mức theo thứ tự a-d), cùng chủ
     * đề (so sau trim, không phân biệt hoa thường, theo topic của template gốc); nếu câu được sinh từ một ô ma trận thì
     * còn phải cùng nội dung. Bỏ câu đã nằm trong đề này; mỗi câu chỉ ra một lần dù template được nhiều ô dùng chung.
     *
     * <p>W1-3, xếp hạng MỀM giữa các đề (không cấm cứng): ít đề khác đã dùng câu trước, hòa thì câu dùng lần cuối cũ
     * hơn trước (câu chưa dùng lên đầu), hòa nữa thì theo id. Đếm số ĐỀ khác nhau (đề của mọi giáo viên, quyết định
     * D3) có chỗ {@code exam_slot.bank_item_id} hoặc {@code exam_question.bank_item_id} là câu đó, trừ đề đang tạo;
     * lần dùng gần nhất là {@code exam.created_at} lớn nhất của các đề đó.
     *
     * <p>Truy vấn native chỉ đọc qua bank_item, generated_question, question_template, exam_matrix_cell, exam_slot,
     * exam_question, exam (ngoại lệ ghi ở docs/architecture/backend-structure.md). {@code difficulty} và
     * {@code levels} truyền chuỗi rỗng khi không dùng.
     *
     * <p>W1-4 (D5-B, Đổi bằng câu ngân hàng): thêm ba điều kiện loại trừ trong phạm vi đề: câu ngân hàng từng bị Đổi ra
     * khỏi MỘT chỗ nào của đề ({@code exam_slot_action.bank_item_id}, action replace); bản sao ngân hàng của câu sinh ra
     * đang nằm trong đề hoặc đã bị Đổi ra ({@code bank_item.source_question_id} thuộc {@code out_of_reach}, gồm cả bản gốc
     * theo chuỗi {@code edited_from_question_id} của bản giáo viên sửa; câu có thể vào ngân hàng qua duyệt chấm mù, không chỉ
     * qua Giữ). Đề đang tạo chưa có chỗ hay nhật ký nên không đổi kết quả khi tạo đề.
     *
     * <p>W1-4: {@code anyContent} = true (chỉ ô của ma trận ẩn sinh theo bài không nhập nội dung) bỏ điều kiện về nội dung
     * của ô nguồn; false giữ nguyên hành vi cũ.
     */
    @Query(value = """
            WITH RECURSIVE out_of_reach(id) AS (
                SELECT es.generated_question_id FROM exam_slot es
                WHERE es.exam_id = :examId AND es.generated_question_id IS NOT NULL
                UNION
                SELECT a.generated_question_id FROM exam_slot_action a JOIN exam_slot es ON es.id = a.slot_id
                WHERE es.exam_id = :examId AND a.action = 'replace' AND a.generated_question_id IS NOT NULL
                UNION
                SELECT q.edited_from_question_id FROM generated_question q JOIN out_of_reach r ON q.id = r.id
                WHERE q.edited_from_question_id IS NOT NULL)
            SELECT b.id
            FROM bank_item b
            JOIN generated_question g ON g.id = b.source_question_id
            JOIN question_template t ON t.id = g.template_id
            LEFT JOIN (SELECT u.bank_item_id, count(*) AS uses, max(e.created_at) AS last_used
                       FROM (SELECT exam_id, bank_item_id FROM exam_slot WHERE bank_item_id IS NOT NULL
                             UNION
                             SELECT exam_id, bank_item_id FROM exam_question) u
                       JOIN exam e ON e.id = u.exam_id
                       WHERE u.exam_id <> :examId
                       GROUP BY u.bank_item_id) us ON us.bank_item_id = b.id
            WHERE b.question_type = :type
              AND t.subject_id = :subjectId
              AND lower(btrim(coalesce(t.topic, ''))) = lower(btrim(:topic))
              AND (CAST(:anyContent AS boolean)
                   OR NOT EXISTS (SELECT 1 FROM exam_matrix_cell c WHERE c.template_id = t.id)
                   OR EXISTS (SELECT 1 FROM exam_matrix_cell c WHERE c.template_id = t.id
                              AND lower(btrim(coalesce(c.content, ''))) = lower(btrim(:content))))
              AND ((:type <> 'tf' AND b.difficulty = :difficulty)
                   OR (:type = 'tf' AND (SELECT string_agg(s.difficulty, ',' ORDER BY s.position)
                                         FROM bank_item_statement s WHERE s.bank_item_id = b.id) = :levels))
              AND b.id NOT IN (SELECT es.bank_item_id FROM exam_slot es
                               WHERE es.exam_id = :examId AND es.bank_item_id IS NOT NULL)
              AND b.id NOT IN (SELECT a.bank_item_id FROM exam_slot_action a JOIN exam_slot es ON es.id = a.slot_id
                               WHERE es.exam_id = :examId AND a.action = 'replace' AND a.bank_item_id IS NOT NULL)
              AND b.source_question_id NOT IN (SELECT id FROM out_of_reach)
            ORDER BY coalesce(us.uses, 0), us.last_used ASC NULLS FIRST, b.id
            LIMIT :limit
            """, nativeQuery = true)
    List<Integer> findBankCandidates(@Param("examId") Integer examId,
                                     @Param("subjectId") Integer subjectId,
                                     @Param("type") String type,
                                     @Param("topic") String topic,
                                     @Param("content") String content,
                                     @Param("difficulty") String difficulty,
                                     @Param("levels") String levels,
                                     @Param("anyContent") boolean anyContent,
                                     @Param("limit") int limit);
}
