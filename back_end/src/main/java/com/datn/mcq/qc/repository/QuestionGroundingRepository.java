package com.datn.mcq.qc.repository;

import com.datn.mcq.qc.entity.QuestionGrounding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface QuestionGroundingRepository extends JpaRepository<QuestionGrounding, Integer> {

    /** Kết quả bám tài liệu của một câu (một phiên bản); rỗng khi chưa chấm hoặc câu không có tài liệu. */
    Optional<QuestionGrounding> findByQuestionId(Integer questionId);

    /** W2-4b (C): đếm số lượt gọi judge "bám tài liệu" thành công (V21: có {@code token_count}) từ mốc {@code since}. */
    @Query(value = """
            SELECT count(*) FROM question_grounding
            WHERE token_count IS NOT NULL AND checked_at >= :since
            """, nativeQuery = true)
    int countGroundingCallsSince(@Param("since") LocalDateTime since);
}
