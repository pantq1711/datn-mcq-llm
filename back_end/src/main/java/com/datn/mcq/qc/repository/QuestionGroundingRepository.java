package com.datn.mcq.qc.repository;

import com.datn.mcq.qc.entity.QuestionGrounding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionGroundingRepository extends JpaRepository<QuestionGrounding, Integer> {

    /** Kết quả bám tài liệu của một câu (một phiên bản); rỗng khi chưa chấm hoặc câu không có tài liệu. */
    Optional<QuestionGrounding> findByQuestionId(Integer questionId);
}
