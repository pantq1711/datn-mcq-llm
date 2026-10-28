package com.datn.mcq.generation.repository;

import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GeneratedQuestionRepository extends JpaRepository<GeneratedQuestion, Integer> {
    List<GeneratedQuestion> findAllByOrderByIdDesc();
    List<GeneratedQuestion> findByStatusOrderByIdDesc(QuestionStatus status);
    List<GeneratedQuestion> findByRunIdOrderByIdAsc(Integer runId);

    /** Độ đa dạng: 10 câu giải mã được gần nhất cùng template và dạng câu, ở mọi run và mọi trạng thái. */
    List<GeneratedQuestion> findTop10ByTemplateIdAndQuestionTypeAndParseSuccessTrueOrderByCreatedAtDescIdDesc(
            Integer templateId, QuestionType questionType);

    /** V13 (W1-2): câu đã có bản giáo viên sửa tiếp (đã bị thay). */
    boolean existsByEditedFromQuestionId(Integer questionId);

    /** W1-3: bản giáo viên sửa tiếp theo của một câu (tối đa một, UNIQUE ở DB). */
    java.util.Optional<GeneratedQuestion> findByEditedFromQuestionId(Integer questionId);

    /**
     * W2-4b (A): câu của một claim đã bị loại có còn được nơi nào dùng không (khi đó KHÔNG được xóa). Truy vấn native
     * CHỈ ĐỌC, ngoại lệ đọc chéo bảng (xem backend-structure.md mục 24): chỗ trong đề ({@code exam_slot}, gồm câu
     * fallback), nhật ký thao tác, ngân hàng, đánh giá giáo viên, gán nhãn, gold set, dòng QC của câu khác trỏ tới câu này,
     * kết quả grounding, bản sửa tiếp, và job khác ({@code jobId} là job của chính claim bị loại, không tính).
     */
    @Query(value = """
            SELECT (EXISTS (SELECT 1 FROM exam_slot WHERE generated_question_id = :questionId
                                                         OR fallback_question_id = :questionId)
                 OR EXISTS (SELECT 1 FROM exam_slot_action WHERE generated_question_id = :questionId)
                 OR EXISTS (SELECT 1 FROM bank_item WHERE source_question_id = :questionId)
                 OR EXISTS (SELECT 1 FROM teacher_review WHERE question_id = :questionId)
                 OR EXISTS (SELECT 1 FROM label_assignment WHERE question_id = :questionId)
                 OR EXISTS (SELECT 1 FROM dedup_gold_pair WHERE question_id_1 = :questionId
                                                              OR question_id_2 = :questionId)
                 OR EXISTS (SELECT 1 FROM quality_check_result WHERE compared_to_question_id = :questionId
                                                                   AND question_id <> :questionId)
                 OR EXISTS (SELECT 1 FROM question_grounding WHERE question_id = :questionId)
                 OR EXISTS (SELECT 1 FROM question_embedding WHERE question_id = :questionId)
                 OR EXISTS (SELECT 1 FROM generated_question WHERE edited_from_question_id = :questionId)
                 OR EXISTS (SELECT 1 FROM generation_job WHERE question_id = :questionId AND id <> :jobId))
            """, nativeQuery = true)
    boolean isReferencedBesidesJob(@Param("questionId") Integer questionId, @Param("jobId") Integer jobId);

    /** W2-4b (A): xóa các ý của câu tf (con của {@code generated_question_statement}) trước khi xóa câu mồ côi. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM generated_question_statement WHERE question_id = :questionId", nativeQuery = true)
    int deleteStatementsOf(@Param("questionId") Integer questionId);

    /** W2-4b (A): xóa một câu mồ côi; nơi gọi đã xóa mọi dòng con và kiểm tra không còn tham chiếu. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM generated_question WHERE id = :questionId", nativeQuery = true)
    int deleteOrphan(@Param("questionId") Integer questionId);

    /**
     * W2-4b (C): đếm số câu do LLM sinh ({@code edited_from_question_id IS NULL}) từ mốc {@code since} trở đi để khởi tạo
     * bộ đếm hạn mức ngày của Gemini khi ứng dụng khởi động.
     */
    @Query(value = """
            SELECT count(*) FROM generated_question
            WHERE edited_from_question_id IS NULL AND created_at >= :since
            """, nativeQuery = true)
    int countLlmGeneratedSince(@Param("since") LocalDateTime since);
}
